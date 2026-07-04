package com.vibeflow.mobile.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Recognizer
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Offline streaming engine: we drive [AudioRecord] ourselves (16 kHz mono PCM16)
 * and feed frames to a Vosk [Recognizer]. Owning the audio lets us run an
 * adaptive **energy-based VAD** for hands-free auto-stop, while still streaming
 * partial results for live text. Behind the [SpeechEngine] interface so a
 * whisper.cpp backend can replace it later.
 *
 * VAD: per ~100 ms frame we compute RMS and track an adaptive noise floor (the
 * quietest energy seen, with a slow upward leak). A frame is "speech" when its
 * RMS clears `max(noiseFloor * SPEECH_FACTOR, MIN_SPEECH_RMS)`. Once speech has
 * begun, sustained silence of `endSilenceMs` ends the session. Guard rails:
 * if nothing is said within `INITIAL_TIMEOUT_MS`, or total time exceeds
 * `MAX_DURATION_MS`, we stop anyway. The user can always tap to stop early.
 */
class VoskSpeechEngine(context: Context) : SpeechEngine {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())

    private val sampleRate = 16000
    private val frameSamples = 1600          // 100 ms per frame
    private val frameMs = 1000 * frameSamples / sampleRate

    @Volatile private var running = false
    @Volatile private var stopRequested = false
    @Volatile private var cancelRequested = false
    private var audioThread: Thread? = null
    private var listener: SpeechEngine.Listener? = null

    override val isModelLoaded: Boolean get() = ModelManager.isLoaded

    override suspend fun warmUp() { ModelManager.getModel(appContext) }

    override fun start(listener: SpeechEngine.Listener, handsFree: Boolean, endSilenceMs: Int) {
        if (running) return
        this.listener = listener
        stopRequested = false
        cancelRequested = false

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onError("Microphone permission is needed. Open the VibeFlow app to grant it.")
            return
        }

        running = true
        scope.launch {
            val model = try {
                ModelManager.getModel(appContext)
            } catch (t: ModelManager.ModelNotDownloaded) {
                running = false
                listener.onError("Offline voice model isn't downloaded. Get it in Settings ▸ Offline mode.")
                return@launch
            } catch (t: Throwable) {
                running = false
                listener.onError("Could not load the speech model: ${t.message}")
                return@launch
            }
            val recognizer = try {
                Recognizer(model, sampleRate.toFloat())
            } catch (t: Throwable) {
                running = false
                listener.onError("Could not start recognizer: ${t.message}")
                return@launch
            }
            audioThread = thread(name = "vibeflow-audio") {
                runAudioLoop(recognizer, handsFree, endSilenceMs)
            }
        }
    }

    override fun stop() { stopRequested = true }

    override fun cancel() {
        cancelRequested = true
        stopRequested = true
    }

    override fun release() {
        cancel()
        scope.cancel()
    }

    private fun runAudioLoop(recognizer: Recognizer, handsFree: Boolean, endSilenceMs: Int) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = maxOf(minBuf, frameSamples * 2 * 4)
        val record = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes
            )
        } catch (t: Throwable) {
            finishWithError(recognizer, "Microphone unavailable: ${t.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            finishWithError(recognizer, "Microphone could not be initialised")
            return
        }
        // Hardware voice cleanup: AGC (whispers), noise suppression (noisy rooms), echo cancel.
        val effects = VoiceEffects.enable(record.audioSessionId)

        val accumulated = StringBuilder()
        val frame = ShortArray(frameSamples)
        val gained = ShortArray(frameSamples)
        var runningPeak = 0f
        var lastEmitted = ""
        var speechStarted = false
        var silenceMs = 0
        var elapsedMs = 0
        var noiseFloor = -1f

        try {
            record.startRecording()
            postListening()
            while (running && !stopRequested) {
                val n = record.read(frame, 0, frame.size)
                if (n <= 0) continue
                elapsedMs += 1000 * n / sampleRate

                // Auto-gain: this phone captures very quietly, so boost the signal
                // fed to the recognizer toward a healthy peak (VAD stays on raw rms).
                var fpk = 0
                for (i in 0 until n) { val a = kotlin.math.abs(frame[i].toInt()); if (a > fpk) fpk = a }
                runningPeak = maxOf(fpk.toFloat(), runningPeak * 0.997f)
                val gain = if (runningPeak < 150f) 1f else (LIVE_TARGET_PEAK / runningPeak).coerceIn(1f, LIVE_MAX_GAIN)
                for (i in 0 until n) gained[i] = (frame[i] * gain).toInt().coerceIn(-32768, 32767).toShort()

                val endpoint = recognizer.acceptWaveForm(gained, n)
                if (endpoint) {
                    val seg = parse(recognizer.result, "text")
                    if (seg.isNotEmpty()) appendSegment(accumulated, seg)
                } else {
                    val partial = parse(recognizer.partialResult, "partial")
                    val combined = combine(accumulated, partial)
                    if (combined != lastEmitted) { lastEmitted = combined; postPartial(combined) }
                }

                // Always surface mic loudness for the live waveform (log-scaled for a
                // lively-but-stable visual), and run VAD when hands-free.
                val rms = rms(frame, n)
                postAmplitude(normalizeAmplitude(rms))
                if (handsFree) {
                    noiseFloor = if (noiseFloor < 0f) rms else minOf(noiseFloor * 1.005f, rms)
                    val threshold = maxOf(noiseFloor * SPEECH_FACTOR, MIN_SPEECH_RMS)
                    if (rms > threshold) {
                        speechStarted = true
                        silenceMs = 0
                    } else if (speechStarted) {
                        silenceMs += frameMs
                    }
                    if (speechStarted && silenceMs >= endSilenceMs) break
                    if (!speechStarted && elapsedMs >= INITIAL_TIMEOUT_MS) break
                }
                if (elapsedMs >= MAX_DURATION_MS) break
            }
        } catch (t: Throwable) {
            VoiceEffects.release(effects)
            try { record.stop() } catch (_: Throwable) {}
            record.release()
            finishWithError(recognizer, "Recording error: ${t.message}")
            return
        }

        VoiceEffects.release(effects)
        try { record.stop() } catch (_: Throwable) {}
        record.release()

        if (cancelRequested) {
            running = false
            try { recognizer.close() } catch (_: Throwable) {}
            return
        }
        val finalSeg = parse(recognizer.finalResult, "text")
        if (finalSeg.isNotEmpty()) appendSegment(accumulated, finalSeg)
        try { recognizer.close() } catch (_: Throwable) {}
        running = false
        val full = accumulated.toString().trim()
        postFinal(full)
    }

    // --- helpers ---

    private fun appendSegment(sb: StringBuilder, seg: String) {
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(seg)
    }

    private fun combine(accumulated: StringBuilder, partial: String): String {
        val base = accumulated.toString().trim()
        val p = partial.trim()
        return when {
            base.isEmpty() -> p
            p.isEmpty() -> base
            else -> "$base $p"
        }
    }

    private fun rms(frame: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) { val s = frame[i].toDouble(); sum += s * s }
        return sqrt(sum / n).toFloat()
    }

    /** Map RMS (~0..6000 for speech) to a lively 0..1 level on a log curve. */
    private fun normalizeAmplitude(rms: Float): Float {
        if (rms <= AMP_FLOOR) return 0f
        val ratio = (rms - AMP_FLOOR) / (AMP_CEIL - AMP_FLOOR)
        val clamped = ratio.coerceIn(0f, 1f)
        // gentle log shaping so quiet speech still moves the bars
        return sqrt(clamped)
    }

    private fun postListening() = main.post { listener?.onListening() }
    private fun postAmplitude(level: Float) = main.post { listener?.onAmplitude(level) }
    private fun postPartial(text: String) = main.post { listener?.onPartial(text) }
    private fun postFinal(text: String) = main.post {
        val l = listener; listener = null; l?.onFinal(text)
    }

    private fun finishWithError(recognizer: Recognizer, message: String) {
        running = false
        try { recognizer.close() } catch (_: Throwable) {}
        main.post { val l = listener; listener = null; l?.onError(message) }
    }

    private fun parse(hypothesis: String?, key: String): String =
        try {
            if (hypothesis.isNullOrBlank()) "" else JSONObject(hypothesis).optString(key, "")
        } catch (_: Throwable) { "" }

    private companion object {
        const val SPEECH_FACTOR = 3.0f          // RMS must exceed noiseFloor * this
        const val MIN_SPEECH_RMS = 550f         // absolute floor so quiet rooms still gate
        const val INITIAL_TIMEOUT_MS = 6000     // stop if nothing is said
        const val MAX_DURATION_MS = 30000       // hard safety cap per session
        const val AMP_FLOOR = 220f              // RMS below this reads as silence
        const val AMP_CEIL = 5000f              // RMS at/above this is full-scale
        const val LIVE_TARGET_PEAK = 18000f     // auto-gain target for the recognizer
        const val LIVE_MAX_GAIN = 12f
    }
}
