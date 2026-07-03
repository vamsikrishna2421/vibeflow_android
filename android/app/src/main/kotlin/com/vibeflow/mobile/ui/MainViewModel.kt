package com.vibeflow.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibeflow.mobile.VibeFlowApp
import com.vibeflow.mobile.asr.SpeechEngine
import com.vibeflow.mobile.asr.ModelManager
import com.vibeflow.mobile.asr.SpeechEngines
import com.vibeflow.mobile.core.Pipeline
import com.vibeflow.mobile.core.model.HistoryEntry
import com.vibeflow.mobile.data.Clipboard
import com.vibeflow.mobile.data.Settings
import com.vibeflow.mobile.data.SettingsRepository
import com.vibeflow.mobile.data.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RecordState {
    data object Idle : RecordState
    data object Listening : RecordState
    data class Done(val text: String) : RecordState
    data class Error(val message: String) : RecordState
}

/**
 * Drives the in-app "record to clipboard" button and exposes settings + history
 * to the Compose screens. All dictation paths funnel through the shared
 * [Pipeline], so behaviour matches the keyboard exactly.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo: SettingsRepository = VibeFlowApp.settings()
    private val historyRepo: HistoryRepository = VibeFlowApp.history()
    private val correctionsRepo = VibeFlowApp.corrections()
    private val dictionaryRepo = VibeFlowApp.dictionary()
    // Picked per-record from settings: online Google recognizer by default; on-device for Private.
    private var engine: SpeechEngine = SpeechEngines.create(app, online = true)
    private val smartFormatter = com.vibeflow.mobile.ai.SmartFormatter()
    private val managedFormatter by lazy { com.vibeflow.mobile.ai.ManagedFormatter(VibeFlowApp.supabaseAuth()) }

    val settings: StateFlow<Settings> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val history: StateFlow<List<HistoryEntry>> = historyRepo.entries
    val corrections: StateFlow<List<com.vibeflow.mobile.data.CorrectionRecord>> = correctionsRepo.records

    // --- managed tier (Google sign-in via Supabase) ---
    val authState: StateFlow<com.vibeflow.mobile.auth.AuthState> =
        VibeFlowApp.supabaseAuth().state.stateIn(
            viewModelScope, SharingStarted.Eagerly, com.vibeflow.mobile.auth.AuthState(false, ""),
        )

    /** True once the REAL settings + auth values have loaded from disk. On-open dialogs must
     *  gate on this — otherwise they flash with default (pre-load) state and vanish when the
     *  real values arrive (e.g. a stored BYOK key makes the sign-in prompt's condition flip). */
    val ready: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(settingsRepo.flow, VibeFlowApp.supabaseAuth().state) { _, _ -> true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Run native Google sign-in then exchange for a Supabase session. [onDone] gets null on success, else an error. */
    fun signInWithGoogle(activityContext: android.content.Context, onDone: (String?) -> Unit) = viewModelScope.launch {
        val token = com.vibeflow.mobile.auth.GoogleSignInHelper.getIdToken(activityContext)
            .getOrElse { onDone(it.message ?: "Sign-in cancelled"); return@launch }
        VibeFlowApp.supabaseAuth().signInWithGoogle(token).fold(
            onSuccess = { syncProfileAfterSignIn(); onDone(null) },
            onFailure = { onDone(it.message ?: "Sign-in failed") },
        )
    }

    /** Restore the cloud-synced profile (name/title) after sign-in — so reinstall doesn't ask
     *  again. If the cloud is empty but we have local details, back them up instead. */
    private fun syncProfileAfterSignIn() = viewModelScope.launch {
        val auth = VibeFlowApp.supabaseAuth()
        val remote = runCatching { auth.pullProfile() }.getOrNull()
        val s = settings.value
        if (remote != null && (remote.first.isNotBlank() || remote.second.isNotBlank())) {
            if (remote.first.isNotBlank()) settingsRepo.setUserName(remote.first)
            if (remote.second.isNotBlank()) settingsRepo.setUserTitle(remote.second)
        } else if (s.userName.isNotBlank() || s.userTitle.isNotBlank()) {
            runCatching { auth.pushProfile(s.userName, s.userTitle) }
        }
        com.vibeflow.mobile.billing.RevenueCatManager.logIn(auth.userId().orEmpty())
    }

    fun signOutManaged() = viewModelScope.launch { com.vibeflow.mobile.billing.RevenueCatManager.logOut(); VibeFlowApp.supabaseAuth().signOut(); _quota.value = null }

    // Live free-tier quota readout (managed tier).
    private val _quota = MutableStateFlow<com.vibeflow.mobile.auth.SupabaseAuth.Quota?>(null)
    val quota: StateFlow<com.vibeflow.mobile.auth.SupabaseAuth.Quota?> = _quota.asStateFlow()
    fun refreshQuota() = viewModelScope.launch {
        _quota.value = runCatching { VibeFlowApp.supabaseAuth().fetchQuota() }.getOrNull()
    }

    /** Buy Pro via Google Play (RevenueCat). No-op until the goog_ key + Play products exist.
     *  On success, Pro flips server-side via the webhook (is_pro) → we pull the fresh quota. */
    fun buyPro(activity: android.app.Activity, planId: String) = viewModelScope.launch {
        val pkgs = com.vibeflow.mobile.billing.RevenueCatManager.currentPackages()
        val pkg = pkgs.firstOrNull { it.packageType.name.equals(planId, ignoreCase = true) }
            ?: pkgs.firstOrNull { it.identifier.contains(planId, ignoreCase = true) }
            ?: pkgs.firstOrNull()
            ?: return@launch
        if (com.vibeflow.mobile.billing.RevenueCatManager.purchase(activity, pkg)) {
            _quota.value = runCatching { VibeFlowApp.supabaseAuth().fetchQuota() }.getOrNull()
        }
    }

    private val _recordState = MutableStateFlow<RecordState>(RecordState.Idle)
    val recordState: StateFlow<RecordState> = _recordState.asStateFlow()

    private val _preview = MutableStateFlow("")
    val preview: StateFlow<String> = _preview.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    // --- offline speech model (downloadable Vosk; not bundled) ---
    val offlineModelState: StateFlow<ModelManager.State> = ModelManager.state
    fun downloadOfflineModel() = viewModelScope.launch { ModelManager.ensureDownloaded(getApplication()) }
    fun removeOfflineModel() = ModelManager.remove(getApplication())

    // --- offline noise model (downloadable Whisper small; not bundled, ~181 MB) ---
    val whisperModelState: StateFlow<com.vibeflow.mobile.asr.WhisperModelManager.State> = com.vibeflow.mobile.asr.WhisperModelManager.state
    fun downloadWhisperModel() = viewModelScope.launch { com.vibeflow.mobile.asr.WhisperModelManager.ensureDownloaded(getApplication()) }
    /** Wi-Fi-only background fetch: onboarding + every app start (so existing users get it on their
     *  next update). No-op if the user turned the noise model off, or it's already present. */
    fun autoDownloadWhisper() = viewModelScope.launch {
        if (settingsRepo.snapshot().noiseModel == "whisper") {
            com.vibeflow.mobile.asr.WhisperModelManager.autoDownload(getApplication())
        }
    }
    fun removeWhisperModel() {
        com.vibeflow.mobile.asr.WhisperModelManager.remove(getApplication())
        viewModelScope.launch { settingsRepo.setNoiseModel("off") }
    }
    fun setNoiseModel(v: String) = viewModelScope.launch { settingsRepo.setNoiseModel(v) }

    init {
        ModelManager.refreshState(app)
        com.vibeflow.mobile.asr.WhisperModelManager.refreshState(app)
        autoDownloadWhisper()   // Wi-Fi-only; resumes on next update for users who skip onboarding
        viewModelScope.launch { runCatching { historyRepo.ensureLoaded() } }
        viewModelScope.launch { runCatching { engine.warmUp() } }
        viewModelScope.launch { runCatching { dictionaryRepo.ensureLoaded() } }
        viewModelScope.launch { runCatching { correctionsRepo.ensureLoaded() } }
        viewModelScope.launch { if (authState.value.signedIn) com.vibeflow.mobile.billing.RevenueCatManager.logIn(VibeFlowApp.supabaseAuth().userId().orEmpty()) }
    }

    // --- learned corrections ---
    fun removeCorrection(from: String) = viewModelScope.launch { correctionsRepo.remove(from) }
    fun clearCorrections() = viewModelScope.launch { correctionsRepo.clear() }

    // --- dictionary / migration ---
    fun teachFromText(text: String) = viewModelScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { dictionaryRepo.learnFromText(text) }
        }
    }
    fun exportVocabulary(): String = dictionaryRepo.exportLearned()
    fun importVocabulary(text: String) = viewModelScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { dictionaryRepo.importLearned(text) }
        }
    }
    fun learnedCount(): Int = dictionaryRepo.engine.learnedSnapshot().size

    fun toggleRecord() {
        when (_recordState.value) {
            RecordState.Listening -> engine.stop()
            else -> startRecord()
        }
    }

    private fun startRecord() {
        val s = settings.value
        runCatching { engine.release() }
        _preview.value = ""
        _recordState.value = RecordState.Listening
        viewModelScope.launch {
            engine = SpeechEngines.createForEnvironment(getApplication(), online = s.onlineRecognition, language = s.resolvedRecognitionLanguage(), noiseModel = s.noiseModel)
            engine.start(object : SpeechEngine.Listener {
                override fun onListening() { _recordState.value = RecordState.Listening }
                override fun onPartial(text: String) { _preview.value = text }
                override fun onAmplitude(level: Float) { _amplitude.value = level }
                override fun onFinal(text: String) { _amplitude.value = 0f; deliver(text) }
                override fun onError(message: String) { _amplitude.value = 0f; _recordState.value = RecordState.Error(message) }
            }, handsFree = s.handsFree, endSilenceMs = s.endSilenceMs)
        }
    }

    private fun deliver(raw: String) {
        val s = settings.value
        val cleaned = Pipeline.process(raw, s.pipelineConfig())
        if (cleaned.isBlank()) {
            _recordState.value = RecordState.Error("Didn't catch that — try again")
            return
        }
        // Show + copy the CLEANED text instantly (zero wait), then upgrade in place to the
        // AI-polished version when it arrives — the same progressive behavior as the bubble.
        Clipboard.copy(getApplication(), cleaned)
        _recordState.value = RecordState.Done(cleaned)

        val wantPolish = s.autoPolish && !s.privateMode && when (s.smartFormatTier) {
            "managed" -> true
            "byok" -> s.llmApiKey.isNotBlank()
            else -> false
        }
        if (!wantPolish) { saveToHistory(cleaned, raw, null, s); return }

        viewModelScope.launch {
            val polished = (polishAnyTier(cleaned, s.smartFormatStyle) as? PolishOutcome.Success)
                ?.text?.takeIf { it.isNotBlank() }
            val finalText = polished ?: cleaned
            if (polished != null) {
                Clipboard.copy(getApplication(), finalText)
                _recordState.value = RecordState.Done(finalText)
            }
            saveToHistory(cleaned, raw, polished, s)
        }
    }

    /** Persist all three stages — raw → clean (text) → polished (own field) — so History shows
     *  the "✨ AI Polished" badge + Raw/Clean/Polished tabs, identical to the keyboard path. */
    private fun saveToHistory(text: String, raw: String, polished: String?, s: Settings) {
        if (!s.historyEnabled) return
        viewModelScope.launch {
            runCatching {
                val entry = historyRepo.add(text, app = "Clipboard", target = "clipboard", max = s.historyMax, raw = raw)
                if (entry != null && !polished.isNullOrBlank()) historyRepo.update(entry.id, polished = polished)
            }
        }
    }

    fun resetRecord() { _recordState.value = RecordState.Idle; _preview.value = "" }

    // --- L3 Smart Formatting (BYOK) ---
    val smartFormatStyles: List<String> get() = com.vibeflow.mobile.ai.SmartFormatter.STYLES

    /** Polish [text] with the remote model using the user's settings. Off-main. */
    suspend fun smartFormat(text: String, style: String): Result<com.vibeflow.mobile.ai.SmartFormatter.PolishResult> {
        val s = settings.value
        return smartFormatter.format(text, apiKey = s.llmApiKey, model = s.llmModel, style = style,
            userName = s.userName, userTitle = s.userTitle)
    }

    /** Outcome of a tier-aware re-polish (from History). */
    sealed interface PolishOutcome {
        data class Success(val text: String, val promptTokens: Int, val completionTokens: Int) : PolishOutcome
        data class Failure(val message: String) : PolishOutcome
    }

    /** Re-polish [text] in [style] using whichever tier is active (managed proxy or BYOK). */
    suspend fun polishAnyTier(text: String, style: String): PolishOutcome {
        val s = settings.value
        val maxChars = com.vibeflow.mobile.ai.SmartFormatter.MAX_INPUT_CHARS
        if (s.privateMode) return PolishOutcome.Failure("Private Mode is on — turn it off to use cloud AI")
        if (text.length > maxChars) return PolishOutcome.Failure("Too long to polish (max $maxChars chars)")
        return if (s.smartFormatTier == "managed") {
            when (val r = managedFormatter.format(text, style, s.userName, s.userTitle)) {
                is com.vibeflow.mobile.ai.ManagedFormatter.Result.Success -> PolishOutcome.Success(r.text, r.promptTokens, r.completionTokens)
                com.vibeflow.mobile.ai.ManagedFormatter.Result.NeedsSignIn -> PolishOutcome.Failure("Sign in (Settings) to use managed Smart Formatting")
                com.vibeflow.mobile.ai.ManagedFormatter.Result.LimitReached -> PolishOutcome.Failure("Free polishes used up — upgrade to Pro")
                com.vibeflow.mobile.ai.ManagedFormatter.Result.DeviceSuperseded -> PolishOutcome.Failure("Signed out — your account is active on another device")
                com.vibeflow.mobile.ai.ManagedFormatter.Result.TooLong -> PolishOutcome.Failure("Too long to polish")
                is com.vibeflow.mobile.ai.ManagedFormatter.Result.Maintenance -> PolishOutcome.Failure(r.message.ifBlank { "AI is paused for maintenance" })
                is com.vibeflow.mobile.ai.ManagedFormatter.Result.Error -> PolishOutcome.Failure("Polish failed — try again")
            }
        } else {
            if (s.llmApiKey.isBlank()) PolishOutcome.Failure("Add your API key in Settings → Smart Formatting")
            else smartFormatter.format(text, s.llmApiKey, s.llmModel, style, s.userName, s.userTitle).fold(
                onSuccess = { PolishOutcome.Success(it.text, it.promptTokens, it.completionTokens) },
                onFailure = { PolishOutcome.Failure(it.message ?: "Polish failed") },
            )
        }
    }

    fun savePolished(id: Long, polished: String, promptTokens: Int = 0, completionTokens: Int = 0) =
        viewModelScope.launch {
            runCatching { historyRepo.update(id, polished = polished, promptTokens = promptTokens, completionTokens = completionTokens) }
        }

    /** Persist a user edit to a capture stage (0=Raw, 1=Clean, 2=Polished). */
    fun saveCaptureStage(id: Long, stage: Int, text: String) = viewModelScope.launch {
        runCatching {
            when (stage) {
                0 -> historyRepo.update(id, raw = text)
                1 -> historyRepo.update(id, text = text)
                else -> historyRepo.update(id, polished = text)
            }
        }
    }

    // --- history actions ---
    fun copyEntry(entry: HistoryEntry) = Clipboard.copy(getApplication(), entry.text)
    fun deleteEntry(id: Long) = viewModelScope.launch { historyRepo.delete(id) }
    fun togglePin(id: Long) = viewModelScope.launch { historyRepo.togglePin(id) }
    fun clearHistory() = viewModelScope.launch { historyRepo.clearUnpinned() }

    // --- settings actions ---
    fun setOutputMode(v: String) = viewModelScope.launch { settingsRepo.setOutputMode(v) }
    fun setStripFillers(v: Boolean) = viewModelScope.launch { settingsRepo.setStripFillers(v) }
    fun setSpokenPunctuation(v: Boolean) = viewModelScope.launch { settingsRepo.setSpokenPunctuation(v) }
    fun setSpokenCommands(v: Boolean) = viewModelScope.launch { settingsRepo.setSpokenCommands(v) }
    fun setAutoPeriod(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoPeriod(v) }
    fun setCapitalizeSentences(v: Boolean) = viewModelScope.launch { settingsRepo.setCapitalizeSentences(v) }
    fun setTrailingSpace(v: Boolean) = viewModelScope.launch { settingsRepo.setTrailingSpace(v) }
    fun setHistoryEnabled(v: Boolean) = viewModelScope.launch { settingsRepo.setHistoryEnabled(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsRepo.setHaptics(v) }
    fun setPushToTalk(v: Boolean) = viewModelScope.launch { settingsRepo.setPushToTalk(v) }
    fun setHandsFree(v: Boolean) = viewModelScope.launch { settingsRepo.setHandsFree(v) }
    fun setVocabulary(terms: List<String>) = viewModelScope.launch { settingsRepo.setVocabulary(terms) }
    fun setSnippets(map: Map<String, String>) = viewModelScope.launch { settingsRepo.setSnippets(map) }
    fun setLlmApiKey(v: String) = viewModelScope.launch { settingsRepo.setLlmApiKey(v) }
    fun setLlmModel(v: String) = viewModelScope.launch { settingsRepo.setLlmModel(v) }
    fun setSmartFormatStyle(v: String) = viewModelScope.launch { settingsRepo.setSmartFormatStyle(v) }
    fun setSmartFormatTier(v: String) = viewModelScope.launch { settingsRepo.setSmartFormatTier(v) }
    fun setAutoPolish(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoPolish(v) }
    fun setMatchStyleToApp(v: Boolean) = viewModelScope.launch { settingsRepo.setMatchStyleToApp(v) }
    fun setPrivateMode(v: Boolean) = viewModelScope.launch { settingsRepo.setPrivateMode(v) }
    fun setFloatingMic(v: Boolean) = viewModelScope.launch { settingsRepo.setFloatingMic(v) }
    fun setAutoInsert(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoInsert(v) }
    fun setOnlineRecognition(v: Boolean) = viewModelScope.launch { settingsRepo.setOnlineRecognition(v) }
    fun setRecognitionLanguage(v: String) = viewModelScope.launch { settingsRepo.setRecognitionLanguage(v) }
    fun setKeyboardTheme(v: String) = viewModelScope.launch { settingsRepo.setKeyboardTheme(v) }
    fun setUserName(v: String) = viewModelScope.launch {
        settingsRepo.setUserName(v)
        runCatching { VibeFlowApp.supabaseAuth().pushProfile(v, settings.value.userTitle) }   // no-op if signed out
    }
    fun setUserTitle(v: String) = viewModelScope.launch {
        settingsRepo.setUserTitle(v)
        runCatching { VibeFlowApp.supabaseAuth().pushProfile(settings.value.userName, v) }
    }
    fun setOnboardingDone(v: Boolean) = viewModelScope.launch { settingsRepo.setOnboardingDone(v) }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }
}
