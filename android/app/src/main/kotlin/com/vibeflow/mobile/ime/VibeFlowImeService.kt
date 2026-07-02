package com.vibeflow.mobile.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.vibeflow.mobile.MainActivity
import com.vibeflow.mobile.VibeFlowApp
import com.vibeflow.mobile.asr.SpeechEngine
import com.vibeflow.mobile.asr.SpeechEngines
import com.vibeflow.mobile.core.MathSuggest
import com.vibeflow.mobile.core.OutputRouting
import com.vibeflow.mobile.core.Pipeline
import com.vibeflow.mobile.core.VoiceCommands
import com.vibeflow.mobile.data.Clipboard
import com.vibeflow.mobile.data.DictionaryRepository
import com.vibeflow.mobile.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The VibeFlow keyboard. An everyday keyboard first — full QWERTY with a
 * predictive suggestion strip, learn-as-you-type autocorrect, and inline math —
 * plus one-tap offline voice (Vosk → [Pipeline] → InputConnection). Word input
 * uses a composing region so suggestions/autocorrect can replace the live word.
 */
class VibeFlowImeService : InputMethodService(), KeyboardActions {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val smartFormatter = com.vibeflow.mobile.ai.SmartFormatter()
    private val managedFormatter by lazy { com.vibeflow.mobile.ai.ManagedFormatter(VibeFlowApp.supabaseAuth()) }
    private lateinit var engine: SpeechEngine
    private lateinit var dict: DictionaryRepository
    private var keyboard: KeyboardView? = null

    @Volatile private var settings: Settings = Settings()
    private var listening = false
    private var transcribing = false           // Whisper: recording stopped, transcript still being computed
    private var recordingWithWhisper = false   // this dictation is using the offline Whisper engine
    private var lastPreview = ""
    private var lastCommitLength = 0
    private var builtDark: Boolean? = null   // dark/light mode the current keyboard view was built for
    private var builtTheme: String = "system"   // keyboard theme pref the current view was built for

    // Auto-vocabulary: learn "heard → meant" corrections from the user's edits.
    private val correctionsRepo by lazy { VibeFlowApp.corrections() }
    @Volatile private var learnedCorrections: Map<String, String> = emptyMap()
    private var learnBaseline = ""     // the last dictation we typed (to diff edits against)
    private var lastFieldText = ""     // most recent non-empty field text (sampled, debounced)
    private var revertPolished = ""    // the polished text now in the field (for "↩ Original")
    private var revertOriginal = ""    // the pre-polish text to restore

    // Clipboard paste chip (Gboard-style): show recently-copied text as a one-tap chip.
    private val clipboard by lazy { getSystemService(android.content.ClipboardManager::class.java) }
    private var clipText = ""
    private var clipTime = 0L
    private var clipDismissed = false
    private val CLIP_WINDOW_MS = 6 * 60_000L     // show a fresh copy as a paste chip for 6 min
    private val clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        refreshClip(); refreshSuggestions()
    }

    // typing / prediction state
    private val composing = StringBuilder()
    private var suggestionsEnabled = true

    override val pushToTalk: Boolean get() = settings.pushToTalk && !settings.handsFree

    override fun onCreate() {
        super.onCreate()
        // Prefer the on-device system recognizer (instant + accurate at normal volume,
        // even in noise); fall back to Vosk where it isn't available. Quiet-voice
        // routing to Whisper comes in a later phase.
        engine = SpeechEngines.create(this, online = settings.onlineRecognition, language = settings.resolvedRecognitionLanguage())
        dict = VibeFlowApp.dictionary()
        VibeFlowApp.settings().flow.onEach { settings = it }.launchIn(scope)
        scope.launch { runCatching { VibeFlowApp.history().ensureLoaded() } }
        scope.launch { runCatching { dict.ensureLoaded() } }
        scope.launch { runCatching { correctionsRepo.ensureLoaded(); learnedCorrections = correctionsRepo.confirmed() } }
        runCatching { clipboard?.addPrimaryClipChangedListener(clipListener) }
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this, this, settings.keyboardTheme)
        keyboard = view
        builtDark = isNight()
        builtTheme = settings.keyboardTheme
        return view
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Does the keyboard need rebuilding for a theme/dark-mode change? */
    private fun needsThemeRebuild(): Boolean =
        builtTheme != settings.keyboardTheme ||
            (settings.keyboardTheme == "system" && builtDark != null && builtDark != isNight())

    /** Rebuild the keyboard when the system flips light/dark so the palette follows it. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (needsThemeRebuild()) {
            setInputView(onCreateInputView())
            render(); updateAutoShift()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Catch a theme/dark-mode change that happened while the keyboard was hidden.
        if (needsThemeRebuild()) setInputView(onCreateInputView())
        listening = false
        lastPreview = ""
        lastCommitLength = 0
        composing.setLength(0)
        suggestionsEnabled = info?.let { allowsSuggestions(it) } ?: true
        keyboard?.setSecureField(info?.let { isPasswordField(it) } ?: false)   // no key-preview balloons on passwords
        keyboard?.setAutofillViews(emptyList())   // drop any stale autofill from the previous field
        refreshClip()                 // pick up anything copied before coming to this field
        render()
        refreshSuggestions()          // shows the clipboard paste chip if one is fresh
        updateAutoShift()
        scope.launch { runCatching { engine.warmUp() } }
    }

    override fun onFinishInput() {
        finishComposing(autocorrect = false)
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The cursor moved without an active composing region (e.g. user tapped
        // elsewhere) — drop our stale composing buffer and refresh suggestions.
        if (composing.isNotEmpty() && candidatesStart == -1 && candidatesEnd == -1) {
            composing.setLength(0)
            refreshSuggestions()
            updateAutoShift()
        }
        if (learnBaseline.isNotBlank() || revertPolished.isNotBlank()) onFieldChanged()
    }

    // --- auto-vocabulary learning + revert-chip housekeeping ---------------

    /** On any field change: learn from a sent edit, and retire a stale "↩ Original" chip. */
    private fun onFieldChanged() {
        val field = currentFieldText()
        if (learnBaseline.isNotBlank()) {
            if (field.isBlank()) {
                if (lastFieldText.isNotBlank()) {
                    maybeLearnFromEdit(learnBaseline, lastFieldText)
                    learnBaseline = ""; lastFieldText = ""
                }
            } else {
                lastFieldText = field
            }
        }
        // The "↩ Original" chip is only valid while the polished text is still in the field.
        if (revertPolished.isNotBlank() && !field.contains(revertPolished.trim())) {
            revertPolished = ""; revertOriginal = ""
            refreshSuggestions()
        }
    }

    private fun currentFieldText(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(2000, 0)?.toString().orEmpty()
        return (before + after).trim()
    }

    /** Diff what we typed vs what the user kept; learn high-confidence word corrections. */
    private fun maybeLearnFromEdit(baseline: String, final: String) {
        if (baseline.isBlank() || final.isBlank() || baseline == final) return
        val learned = com.vibeflow.mobile.core.CorrectionLearner.learn(baseline, final) { dict.engine.knows(it) }
        if (learned.isEmpty()) return
        scope.launch {
            runCatching {
                correctionsRepo.learn(learned)
                learnedCorrections = correctionsRepo.confirmed()
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (listening) stopListening(commit = false)
        finishComposing(autocorrect = false)
        // Leaving the field is our last chance to learn from any edits the user made.
        if (learnBaseline.isNotBlank()) {
            val field = currentFieldText()
            if (field.isNotBlank()) maybeLearnFromEdit(learnBaseline, field)
            learnBaseline = ""
            lastFieldText = ""
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        engine.release()
        runCatching { dict.flush() }
        runCatching { clipboard?.removePrimaryClipChangedListener(clipListener) }
        scope.cancel()
        super.onDestroy()
    }

    // --- clipboard paste chip ---------------------------------------------

    private fun refreshClip() {
        val cm = clipboard ?: return
        if (cm.primaryClipDescription?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return
        val t = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() }.getOrNull().orEmpty()
        if (t.isBlank() || t == clipText) return
        clipText = t
        clipTime = System.currentTimeMillis()
        clipDismissed = false
    }

    /** A "📋 …" chip for recently-copied text, when not mid-word and still fresh. */
    private fun pasteChipOrNull(): String? {
        if (composing.isNotEmpty() || clipDismissed || clipText.isBlank()) return null
        if (System.currentTimeMillis() - clipTime > CLIP_WINDOW_MS) return null
        val oneLine = clipText.replace("\n", " ")
        val preview = if (oneLine.length > 24) oneLine.take(24) + "…" else oneLine
        return "📋 $preview"
    }

    // --- typing ---

    override fun onKey(text: String) {
        lastCommitLength = 0
        val ic = currentInputConnection ?: return
        val isWordChar = text.length == 1 && (text[0].isLetter() || text[0] == '\'')
        if (suggestionsEnabled && isWordChar) {
            composing.append(text)
            ic.setComposingText(composing, 1)
            refreshSuggestions()
        } else {
            finishComposing(autocorrect = true)
            ic.commitText(text, 1)
            refreshSuggestions()
            updateAutoShift()
        }
    }

    override fun onSpace() {
        lastCommitLength = 0
        val ic = currentInputConnection ?: return
        finishComposing(autocorrect = true)
        ic.commitText(" ", 1)
        refreshSuggestions()
        updateAutoShift()
    }

    override fun onBackspace() {
        lastCommitLength = 0
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) ic.finishComposingText() else ic.setComposingText(composing, 1)
            refreshSuggestions()
            return
        }
        // If the user has a selection, backspace deletes the SELECTION (commitText("")
        // replaces it); otherwise it deletes the single char before the cursor.
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
        refreshSuggestions()
        updateAutoShift()
    }

    override fun onEnter() {
        lastCommitLength = 0
        finishComposing(autocorrect = true)
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val multiline = ei != null && (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val noEnterAction = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!multiline && !noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) ic.performEditorAction(action) else ic.commitText("\n", 1)
        keyboard?.setSuggestions(emptyList())
        updateAutoShift()
    }

    override fun onPickSuggestion(text: String) {
        val ic = currentInputConnection ?: return
        if (text == "↩ Original") { revertPolish(); return }
        if (text.startsWith("✨ ")) { refreshSuggestions(); return }   // token-readout chip — not tappable
        if (text.startsWith("📋 ")) {                       // clipboard paste chip
            finishComposing(autocorrect = false)
            ic.commitText(clipText, 1)
            clipDismissed = true
            refreshSuggestions(); updateAutoShift()
            return
        }
        if (text.startsWith("= ")) {                       // math result chip
            finishComposing(autocorrect = false)
            ic.commitText(text.substring(2), 1)
        } else {
            composing.setLength(0)
            ic.setComposingText(text, 1)
            ic.finishComposingText()
            ic.commitText(" ", 1)
            dict.learn(text)
        }
        refreshSuggestions()
        updateAutoShift()
    }

    /** Commit the in-progress word, optionally autocorrecting it first. */
    private fun finishComposing(autocorrect: Boolean) {
        val ic = currentInputConnection ?: run { composing.setLength(0); return }
        if (composing.isEmpty()) { ic.finishComposingText(); return }
        val word = composing.toString()
        val finalWord = if (autocorrect && suggestionsEnabled) dict.engine.autocorrect(word) ?: word else word
        if (finalWord != word) ic.setComposingText(finalWord, 1)
        ic.finishComposingText()
        dict.learn(finalWord)
        composing.setLength(0)
    }

    // Suggestions (dictionary lookup + math parse) are too heavy to run on every
    // keystroke — that was the typing lag. Debounce so they compute once the user
    // pauses briefly, keeping key presses instant.
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val suggestRunnable = Runnable { computeSuggestions() }

    private fun refreshSuggestions() {
        uiHandler.removeCallbacks(suggestRunnable)
        uiHandler.postDelayed(suggestRunnable, 60)
    }

    private fun computeSuggestions() {
        keyboard?.setTranscriptBanner(null)   // recomputing normal suggestions → retire any polish banner
        if (!suggestionsEnabled) { keyboard?.setSuggestions(emptyList()); return }
        val ic = currentInputConnection
        val words = if (composing.isNotEmpty()) dict.engine.suggest(composing.toString(), 3) else emptyList()
        val before = ic?.getTextBeforeCursor(48, 0)?.toString() ?: ""
        val math = MathSuggest.compute(before)
        val paste = pasteChipOrNull()
        val list = buildList {
            paste?.let { add(it) }                          // clipboard chip leads when present
            when {
                math != null && composing.isEmpty() -> add("= $math")
                math != null -> { add("= $math"); addAll(words.take(2)) }
                else -> addAll(words)
            }
        }.take(3)
        keyboard?.setSuggestions(list)
    }

    private fun updateAutoShift() {
        if (!suggestionsEnabled) { keyboard?.setAutoShift(false); return }
        val before = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val atStart = before.isEmpty()
        val afterSentence = before.length >= 2 && before[before.length - 2] in charArrayOf('.', '!', '?') && before.last() == ' '
        keyboard?.setAutoShift(atStart || afterSentence)
    }

    private fun allowsSuggestions(info: EditorInfo): Boolean {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        if (cls != InputType.TYPE_CLASS_TEXT) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val passwordish = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI
        val noSuggest = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        return !passwordish && !noSuggest
    }

    /** A true password field (text or numeric) — used to suppress the key-press preview. */
    private fun isPasswordField(info: EditorInfo): Boolean {
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val textPw = cls == InputType.TYPE_CLASS_TEXT && (
            v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val numPw = cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return textPw || numPw
    }

    // --- inline autofill (password / OTP chips, like Gboard) ----------------
    //
    // We never see or store credentials: the system's autofill provider (e.g. Google
    // Password Manager) inflates its own chip Views and hands them to us via
    // onInlineSuggestionsResponse; we only host them in the suggestion strip. Returning
    // a non-null request from onCreateInlineSuggestionsRequest opts us in (API 30+).

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(
        uiExtras: android.os.Bundle,
    ): android.view.inputmethod.InlineSuggestionsRequest? = runCatching {
        val stylesBuilder = androidx.autofill.inline.UiVersions.newStylesBuilder()
        stylesBuilder.addStyle(androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder().build())
        val styles = stylesBuilder.build()

        val h = dpPx(40)
        val min = android.util.Size(dpPx(80), h)
        val max = android.util.Size(Int.MAX_VALUE, h)
        val specs = (0 until 4).map {
            android.widget.inline.InlinePresentationSpec.Builder(min, max).setStyle(styles).build()
        }
        android.view.inputmethod.InlineSuggestionsRequest.Builder(specs)
            .setMaxSuggestionCount(4)
            .build()
    }.getOrNull()

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(
        response: android.view.inputmethod.InlineSuggestionsResponse,
    ): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) { keyboard?.setAutofillViews(emptyList()); return false }
        val count = suggestions.size.coerceAtMost(4)
        val views = arrayOfNulls<View>(count)
        var remaining = count
        val size = android.util.Size(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, dpPx(40),
        )
        fun onOne() { remaining--; if (remaining <= 0) keyboard?.setAutofillViews(views.filterNotNull()) }
        for (i in 0 until count) {
            val handed = runCatching {
                suggestions[i].inflate(this, size, mainExecutor) { view -> views[i] = view; onOne() }
            }.isSuccess
            if (!handed) onOne()
        }
        return true
    }

    private fun dpPx(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // --- system keys ---

    override fun onSwitchIme() {
        finishComposing(autocorrect = false)
        try {
            if (Build.VERSION.SDK_INT >= 28) { if (!switchToNextInputMethod(false)) showImePicker() }
            else showImePicker()
        } catch (_: Throwable) { showImePicker() }
    }

    override fun onOpenSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onHide() {
        if (listening) stopListening(commit = false)
        requestHideSelf(0)
    }

    // --- voice ---

    // Guard against starting a new dictation while the previous one is still finalizing (transcribing).
    override fun onMicTap() { if (listening) stopListening(commit = true) else if (!transcribing) startListening() }
    override fun onMicPressStart() { if (!listening && !transcribing) startListening() }
    override fun onMicPressEnd() { if (listening) stopListening(commit = true) }

    private fun startListening() {
        finishComposing(autocorrect = true)
        val s = settings
        listening = true
        lastPreview = ""
        haptic(start = true)
        render()
        scope.launch {
        engine = SpeechEngines.createForEnvironment(this@VibeFlowImeService, online = s.onlineRecognition, language = s.resolvedRecognitionLanguage(), noiseModel = s.noiseModel)
        recordingWithWhisper = engine is com.vibeflow.mobile.asr.WhisperSpeechEngine
        if (!listening) return@launch
        if (recordingWithWhisper) render()   // surface the "noise handling — needs patience" cue right away
        engine.start(object : SpeechEngine.Listener {
            override fun onListening() { listening = true; transcribing = false; render() }
            override fun onPartial(text: String) { lastPreview = text; render() }
            // Whisper streaming: recording stopped, the last phrase is still transcribing.
            override fun onProcessing() { val was = listening; listening = false; transcribing = true; if (was) haptic(start = false); render() }
            override fun onAmplitude(level: Float) { keyboard?.setAmplitude(level) }
            override fun onFinal(text: String) {
                val was = listening; listening = false; transcribing = false
                if (was) haptic(start = false)
                deliver(text)
            }
            override fun onError(message: String) {
                listening = false; transcribing = false; lastPreview = ""
                keyboard?.render(KeyboardUiState(listening = false, hint = message))
                scope.launch { kotlinx.coroutines.delay(1400); render() }
            }
        }, handsFree = s.handsFree, endSilenceMs = s.endSilenceMs)
        }
    }

    private fun stopListening(commit: Boolean) {
        listening = false
        if (commit) engine.stop() else { engine.cancel(); transcribing = false }
        render()
    }

    private fun deliver(raw: String) {
        val s = settings
        val command = VoiceCommands.parse(raw)
        if (command != null) { executeCommand(command); return }

        val processed = Pipeline.process(raw, s.pipelineConfig().copy(corrections = learnedCorrections))
        if (processed.isBlank()) {
            keyboard?.render(KeyboardUiState(listening = false))
            return
        }
        val ic = currentInputConnection
        val payload = OutputRouting.withTrailingSpace(processed, s.trailingSpace)
        val target = OutputRouting.decideTarget(s.routingMode(), hasEditableTarget = ic != null)

        val typed: Boolean
        if (target == OutputRouting.Target.TYPE && ic != null) {
            ic.commitText(payload, 1)
            lastCommitLength = payload.length
            typed = true
            learnBaseline = processed           // remember what we typed, to learn from your edits
            lastFieldText = ""
        } else {
            Clipboard.copy(this, processed); typed = false
        }
        render()
        updateAutoShift()

        // Raw stage = the recognizer's literal text (before our sentence-splitting/curation).
        val rawStage = engine.lastRawTranscript.ifBlank { raw }
        // Which polish path applies: private = none, byok = needs a key, managed = needs sign-in
        // (handled inside the managed call). Private Mode also forces off.
        val wantPolish = typed && s.autoPolish && !s.privateMode && when (s.smartFormatTier) {
            "managed" -> true
            "byok" -> s.llmApiKey.isNotBlank()
            else -> false   // "private"
        }
        if (s.historyEnabled) {
            val app = appLabel()
            scope.launch {
                val entry = runCatching {
                    VibeFlowApp.history().add(
                        processed, app, if (typed) "typed" else "clipboard", s.historyMax,
                        raw = rawStage, pkg = currentInputEditorInfo?.packageName ?: "",
                    )
                }.getOrNull()
                if (wantPolish) startAutoPolish(processed, payload, entry?.id)
            }
        } else if (wantPolish) {
            startAutoPolish(processed, payload, null)
        }
    }

    /**
     * L3 inline: after a dictation, polish in the background and swap the typed text in
     * place — but ONLY if it's still exactly what we committed (guards against the user
     * editing/sending meanwhile). Otherwise the polished version goes to the clipboard.
     */
    private fun startAutoPolish(clean: String, committed: String, entryId: Long?) {
        val s = settings
        if (clean.length > com.vibeflow.mobile.ai.SmartFormatter.MAX_INPUT_CHARS) return   // cost guard: keep offline text
        // Context-aware: known apps map to a style directly; unknown apps pass their name
        // to the LLM so it can infer the right format itself.
        var style = s.smartFormatStyle
        var appName = ""
        if (s.matchStyleToApp) {
            val recognized = com.vibeflow.mobile.ai.AppStyle.styleFor(currentInputEditorInfo?.packageName)
            if (recognized != null) style = recognized
            else { style = "auto"; appName = appLabel() }
        }
        keyboard?.setTranscriptBanner("✨ Polishing your dictation…")
        scope.launch {
            // Branch on the tier: managed (Supabase proxy) vs BYOK (user's own OpenAI key).
            val polished: String?
            val promptTokens: Int
            val completionTokens: Int
            if (s.smartFormatTier == "managed") {
                when (val r = managedFormatter.format(clean, style, s.userName, s.userTitle, appName)) {
                    is com.vibeflow.mobile.ai.ManagedFormatter.Result.Success -> {
                        polished = r.text; promptTokens = r.promptTokens; completionTokens = r.completionTokens
                    }
                    com.vibeflow.mobile.ai.ManagedFormatter.Result.NeedsSignIn -> {
                        showToast("Sign in (VibeFlow ▸ Settings) to use managed Smart Formatting"); refreshSuggestions(); return@launch
                    }
                    com.vibeflow.mobile.ai.ManagedFormatter.Result.LimitReached -> {
                        showToast("Free Smart Formatting used up — upgrade to Pro to continue"); refreshSuggestions(); return@launch
                    }
                    com.vibeflow.mobile.ai.ManagedFormatter.Result.DeviceSuperseded -> {
                        VibeFlowApp.supabaseAuth().signOut()
                        showToast("Signed out — your VibeFlow account is now active on another phone"); refreshSuggestions(); return@launch
                    }
                    com.vibeflow.mobile.ai.ManagedFormatter.Result.TooLong -> {
                        showToast("Too long to AI-polish — keeping the offline version"); refreshSuggestions(); return@launch
                    }
                    is com.vibeflow.mobile.ai.ManagedFormatter.Result.Maintenance -> {
                        showToast(r.message.ifBlank { "Smart Formatting is paused for maintenance — using offline mode" }); refreshSuggestions(); return@launch
                    }
                    is com.vibeflow.mobile.ai.ManagedFormatter.Result.Error -> { refreshSuggestions(); return@launch }
                }
            } else {
                val result = smartFormatter.format(clean, s.llmApiKey, s.llmModel, style, s.userName, s.userTitle, appName).getOrNull()
                polished = result?.text; promptTokens = result?.promptTokens ?: 0; completionTokens = result?.completionTokens ?: 0
            }
            if (polished.isNullOrBlank()) { refreshSuggestions(); return@launch }
            // Token usage is stored on the capture and shown ONLY in History (not the keyboard).
            entryId?.let {
                runCatching {
                    VibeFlowApp.history().update(it, polished = polished,
                        promptTokens = promptTokens, completionTokens = completionTokens)
                }
            }
            val ic = currentInputConnection
            val before = ic?.getTextBeforeCursor(committed.length, 0)?.toString()
            if (ic != null && before == committed) {
                val polishedPayload = OutputRouting.withTrailingSpace(polished, s.trailingSpace)
                ic.beginBatchEdit()
                ic.deleteSurroundingText(committed.length, 0)
                ic.commitText(polishedPayload, 1)
                ic.endBatchEdit()
                lastCommitLength = 0
                learnBaseline = polished        // learn edits relative to the polished text now on screen
                lastFieldText = ""
                revertPolished = polishedPayload
                revertOriginal = committed
                // Show the actual polished sentence across the full strip (engaging) — tap it to undo.
                keyboard?.setTranscriptBanner("✨ $polished", onTap = { revertPolish() })
            } else {
                Clipboard.copy(this@VibeFlowImeService, polished)
                showToast("✨ Polished version copied — clear & paste to use it")
            }
        }
    }

    private fun showToast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    /** Undo an auto-polish swap, restoring the pre-polish text (if it's still in the field). */
    private fun revertPolish() {
        val ic = currentInputConnection
        if (ic != null && revertPolished.isNotBlank()) {
            val before = ic.getTextBeforeCursor(revertPolished.length, 0)?.toString()
            if (before == revertPolished) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(revertPolished.length, 0)
                ic.commitText(revertOriginal, 1)
                ic.endBatchEdit()
                learnBaseline = revertOriginal.trim()
            }
        }
        revertPolished = ""; revertOriginal = ""
        refreshSuggestions()
    }

    private fun executeCommand(command: VoiceCommands.Command) {
        val ic = currentInputConnection ?: return
        when (command) {
            VoiceCommands.Command.DELETE_LAST ->
                if (lastCommitLength > 0) { ic.deleteSurroundingText(lastCommitLength, 0); lastCommitLength = 0 }
            VoiceCommands.Command.DELETE_WORD -> { deleteLastWord(ic); lastCommitLength = 0 }
            VoiceCommands.Command.NEW_LINE -> { ic.commitText("\n", 1); lastCommitLength = 1 }
            VoiceCommands.Command.NEW_PARAGRAPH -> { ic.commitText("\n\n", 1); lastCommitLength = 2 }
        }
        render()
    }

    private fun deleteLastWord(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(96, 0)?.toString() ?: return
        if (before.isEmpty()) return
        val trimmedEnd = before.trimEnd()
        val lastBreak = trimmedEnd.indexOfLast { it == ' ' || it == '\n' || it == '\t' }
        val deleteCount = before.length - (if (lastBreak >= 0) lastBreak + 1 else 0)
        if (deleteCount > 0) ic.deleteSurroundingText(deleteCount, 0)
    }

    private fun appLabel(): String {
        val pkg = currentInputEditorInfo?.packageName ?: return ""
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun render() {
        // Whisper streams phrases back as live partials; until the first one lands, set expectations.
        val label = when {
            listening && recordingWithWhisper && lastPreview.isBlank() -> "🎧 Noise handling — needs patience"
            else -> lastPreview
        }
        keyboard?.render(KeyboardUiState(listening = listening, processing = transcribing, preview = label))
        if (!listening && !transcribing) refreshSuggestions()
    }

    private fun showImePicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    @Suppress("DEPRECATION")
    private fun haptic(start: Boolean) {
        if (!settings.haptics) return
        val vib = getSystemService(Vibrator::class.java) ?: return
        val ms = if (start) 18L else 12L
        if (Build.VERSION.SDK_INT >= 26)
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else vib.vibrate(ms)
    }
}
