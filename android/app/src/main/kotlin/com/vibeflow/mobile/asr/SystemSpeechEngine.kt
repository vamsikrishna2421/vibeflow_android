package com.vibeflow.mobile.asr

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.math.sqrt

/**
 * Live [SpeechEngine] backed by Android's on-device [SpeechRecognizer] — the engine
 * the benchmark crowned for everyday use: near-Whisper accuracy at normal volume and
 * in noise, delivered *instantly*. Its one weakness is quiet voice, which the app
 * handles by routing to Whisper (see EngineRouter) rather than this engine.
 *
 * On-device only (no cloud). It captures its own audio, so unlike Vosk we don't run
 * an AudioRecord here. The recognizer resets its partial buffer at each sentence, so
 * we keep the same reset-detection stitching proven in the Engine Lab, otherwise only
 * the last sentence would survive a multi-sentence dictation.
 */
class SystemSpeechEngine(
    context: Context,
    private val online: Boolean = false,
    private val language: String = "en-IN",   // BCP-47 recognition locale (default: Indian English)
) : SpeechEngine {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var listener: SpeechEngine.Listener? = null

    private val accumulated = StringBuilder()
    private val rawAccumulated = StringBuilder()   // the recognizer's text, no sentence-period insertion
    private var lastPartial = ""
    // The recognizer finalizes a segment only after a real pause, which is a strong
    // sentence boundary. We mark it so the NEXT segment is joined with ". " instead of
    // a space — turning a run-on into sentences. L3 later fixes any imperfect splits.
    private var pendingBreak = false
    @Volatile private var listening = false
    private var handsFree = false
    private var announcedListening = false
    private var endSilence = 1500
    private var activeLang = language        // may fall back to en-US if the requested offline pack is missing
    private var lastVoiceMs = 0L      // last time real speech was recognized
    private var sessionStartMs = 0L

    companion object {
        const val DEBUG_ASR = false                   // flip on to log the recognizer timeline (tag VFASR)
        private const val TAIL_SILENCE_MS = 2200L     // hands-free: finish after this much trailing silence
        private const val INITIAL_TIMEOUT_MS = 8000L  // give up if nothing is recognized in this long
        private const val MAX_SESSION_MS = 45_000L    // hard cap on a single dictation

        /** On-device recognition present (API 31+) and a language pack installed. */
        fun isAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= 31 &&
                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context.applicationContext) }
                    .getOrDefault(false)

        /** Any recognition service present — used for the online (Google) path. */
        fun isOnlineAvailable(context: Context): Boolean =
            runCatching { SpeechRecognizer.isRecognitionAvailable(context.applicationContext) }.getOrDefault(false)
    }

    override val isModelLoaded: Boolean get() = if (online) isOnlineAvailable(appContext) else isAvailable(appContext)
    override val lastRawTranscript: String get() = rawAccumulated.toString().trim()

    override suspend fun warmUp() { /* system model is managed by the OS */ }

    override fun start(listener: SpeechEngine.Listener, handsFree: Boolean, endSilenceMs: Int) {
        main.post {
            if (listening) return@post
            if (!(if (online) isOnlineAvailable(appContext) else isAvailable(appContext))) {
                listener.onError("Speech recognition isn't available on this phone."); return@post
            }
            this.listener = listener
            this.handsFree = handsFree
            accumulated.setLength(0)
            rawAccumulated.setLength(0)
            lastPartial = ""
            pendingBreak = false
            announcedListening = false
            listening = true
            endSilence = endSilenceMs
            activeLang = language
            sessionStartMs = SystemClock.uptimeMillis()
            lastVoiceMs = sessionStartMs
            t0 = 0L; log("start online=$online handsFree=$handsFree endSilence=$endSilenceMs")
            sr = (if (online && SpeechRecognizer.isRecognitionAvailable(appContext))
                    SpeechRecognizer.createSpeechRecognizer(appContext)              // Google online — best accuracy
                else SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext))    // on-device only (Private)
                .also { it.setRecognitionListener(rec) }
            startOnce()
        }
    }

    override fun stop() { main.post { listening = false; runCatching { sr?.stopListening() } } }

    override fun cancel() { main.post { listening = false; runCatching { sr?.cancel() }; destroy() } }

    override fun release() { main.post { destroy() } }

    // --- internals ---------------------------------------------------------

    private fun startOnce() {
        lastPartial = ""
        // Hands-free: the recognizer endpoints after a pause; we then RESTART (not finish) until
        // the overall tail-silence elapses, so multi-phrase dictation with natural pauses is kept
        // in full instead of stopping after the first phrase. Push-to-talk: user ends it.
        val silence = if (handsFree) endSilence.coerceAtLeast(1200) else 8000
        log("startOnce silence=$silence (restart, acc='${accumulated.toString().trim()}')")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !online)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
        }
        runCatching { sr?.startListening(intent) }
    }

    private fun combined(latest: String): String {
        val base = accumulated.toString().trim()
        return if (base.isEmpty()) latest else "$base $latest".trim()
    }

    /** True when [new] continues the same growing utterance as [old] (vs a fresh sentence). */
    private fun isContinuation(new: String, old: String): Boolean {
        if (old.isEmpty()) return true
        val a = new.lowercase(); val b = old.lowercase()
        val k = minOf(12, a.length, b.length)
        return a.startsWith(b.take(k)) || b.startsWith(a.take(k))
    }

    private fun commit(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val cur = accumulated.toString()
        if (cur.endsWith(t)) return
        if (cur.isNotEmpty()) {
            val endsSentence = cur.last() == '.' || cur.last() == '!' || cur.last() == '?'
            accumulated.append(if (pendingBreak && !endsSentence) ". " else " ")
        }
        accumulated.append(t)
        // Raw mirror: same words, but joined with plain spaces (no inserted periods).
        if (rawAccumulated.isNotEmpty()) rawAccumulated.append(' ')
        rawAccumulated.append(t)
        pendingBreak = false
    }

    /** Hands-free auto-stop: end only after a genuine tail of silence (or a hard cap / no-speech
     *  timeout), so natural pauses between phrases don't truncate a long dictation. */
    private fun handsFreeShouldFinish(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - sessionStartMs >= MAX_SESSION_MS) return true
        if (accumulated.isEmpty()) return now - sessionStartMs >= INITIAL_TIMEOUT_MS   // nothing said yet
        return now - lastVoiceMs >= TAIL_SILENCE_MS
    }

    private fun finishNow() {
        commit(lastPartial); lastPartial = ""
        destroy()
        listening = false
        log("finishNow FINAL='${accumulated.toString().trim()}'")
        android.util.Log.d("VFRESULT", "system(online=$online lang=$activeLang) → '${accumulated.toString().trim().take(220)}'")
        listener?.onFinal(accumulated.toString().trim())
    }

    private fun destroy() { runCatching { sr?.destroy() }; sr = null }

    private var t0 = 0L
    private fun log(m: String) {
        if (!DEBUG_ASR) return
        if (t0 == 0L) t0 = SystemClock.uptimeMillis()
        android.util.Log.d("VFASR", "+${SystemClock.uptimeMillis() - t0}ms $m")
    }

    private val rec = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            log("onReadyForSpeech")
            if (!announcedListening) { announcedListening = true; listener?.onListening() }
        }
        override fun onBeginningOfSpeech() { log("onBeginningOfSpeech") }
        override fun onEndOfSpeech() { log("onEndOfSpeech") }
        override fun onRmsChanged(rmsdB: Float) {
            val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            listener?.onAmplitude(sqrt(norm))
        }
        override fun onPartialResults(b: Bundle) {
            val t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
            if (t.isBlank()) return
            log("partial '$t'")
            if (lastPartial.isNotBlank() && !isContinuation(t, lastPartial)) {
                // The recognizer started a fresh phrase → the previous one is a sentence.
                commit(lastPartial); pendingBreak = true
            }
            lastPartial = t
            lastVoiceMs = SystemClock.uptimeMillis()
            listener?.onPartial(combined(t))
        }
        override fun onResults(b: Bundle) {
            val t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val committed = t.ifBlank { lastPartial }
            log("onResults raw='$t' → commit '$committed' (lastPartial was '$lastPartial')")
            if (committed.isNotBlank()) lastVoiceMs = SystemClock.uptimeMillis()
            commit(committed); lastPartial = ""
            if (!listening) { finishNow(); return }
            // The recognizer endpointed (a pause). Keep going — a sentence break before the next
            // phrase — unless the user has truly fallen silent for the whole tail window.
            if (handsFree && handsFreeShouldFinish()) finishNow() else { pendingBreak = true; startOnce() }
        }
        override fun onError(error: Int) {
            log("onError $error (acc='${accumulated.toString().trim()}', lastPartial='$lastPartial')")
            // The requested OFFLINE language pack (e.g. en-IN) isn't installed → fall back to en-US
            // (which on-device recognizers ship by default) so dictation still works.
            val langMissing = error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
            if (listening && langMissing && !activeLang.equals("en-US", ignoreCase = true)) {
                log("language '$activeLang' unavailable offline → retry with en-US")
                activeLang = "en-US"
                startOnce()
                return
            }
            val pause = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (listening && pause) {
                commit(lastPartial); lastPartial = ""
                // A pause / no-match: finish only once the overall tail-silence has elapsed (so a
                // mid-sentence pause or a quiet stretch doesn't cut the dictation short); else retry.
                if (handsFree && handsFreeShouldFinish()) finishNow() else { pendingBreak = true; startOnce() }
                return
            }
            if (!listening) { finishNow(); return }
            listening = false
            destroy()
            listener?.onError(errorText(error))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
            "On-device language pack not installed — add it in your phone's voice-input settings."
        else -> "Recognizer error ($code)"
    }
}
