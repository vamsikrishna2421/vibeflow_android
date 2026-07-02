package com.vibeflow.mobile.asr

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

/**
 * Offline **Whisper** engine (whisper.cpp) with **VAD-aligned streaming**.
 *
 * Instead of waiting for the user to stop and then transcribing the whole clip in one slow batch,
 * this cuts the audio at natural pauses (silence) and transcribes each phrase IN THE BACKGROUND
 * while recording continues. By the time the user stops, the earlier phrases are already done — so
 * the post-stop wait collapses to roughly the final phrase. Cuts land on silence, not mid-word, so
 * accuracy stays close to a single pass, and each completed phrase streams back as a live partial.
 *
 * Inference runs in the isolated `:asr` process. A keep-alive binding holds that process (and its
 * loaded model) open for the whole dictation, so segments don't reload the 181 MB model each time.
 */
class WhisperSpeechEngine(context: Context, private val modelPath: String) : SpeechEngine {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var recording = false
    @Volatile private var cancelled = false
    private var listener: SpeechEngine.Listener? = null
    private var handsFree = false
    private var endSilenceMs = 1500
    private var lastRaw = ""
    private var keepAlive: ServiceConnection? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MAX_SECONDS = 60                      // hard cap on a single dictation
        private const val SILENCE_RMS = 0.012f                  // normalized RMS below this = silence
        private const val PHRASE_SILENCE_MS = 600               // a pause this long ends a phrase → flush it
        private const val MIN_SEGMENT_MS = 1800                 // don't cut a phrase shorter than this (keep context)
        private const val MAX_SEGMENT_SAMPLES = 14 * SAMPLE_RATE // cut a run-on with no pause at 14 s

        fun isModelAvailable(context: Context): Boolean = WhisperModelManager.isDownloaded(context)
    }

    override val isModelLoaded: Boolean get() = WhisperModelManager.isDownloaded(appContext)
    override val lastRawTranscript: String get() = lastRaw

    override suspend fun warmUp() { /* the model loads in the isolated :asr process on first use */ }

    override fun start(listener: SpeechEngine.Listener, handsFree: Boolean, endSilenceMs: Int) {
        this.listener = listener
        this.handsFree = handsFree
        this.endSilenceMs = endSilenceMs
        cancelled = false
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emitError("Microphone permission needed"); return
        }
        scope.launch { record() }
    }

    private suspend fun record() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { emitError("Audio init failed"); return }
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 5)   // ~200 ms of shorts
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 4,
            )
        } catch (e: Exception) { emitError("Mic unavailable"); return }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); emitError("Mic unavailable"); return }

        bindKeepAlive()   // hold the :asr model loaded across all segments of this dictation

        // Background transcription pipeline: phrases queue here; a SINGLE consumer transcribes them
        // serially (the :asr context is single-threaded) and grows the running transcript.
        val queue = Channel<FloatArray>(Channel.UNLIMITED)
        val transcript = StringBuilder()
        val lock = Any()
        val consumer = scope.launch {
            for (seg in queue) {
                if (cancelled) break
                val text = transcribeSegment(seg)
                if (text.isNotBlank() && !cancelled) {
                    val snapshot = synchronized(lock) {
                        if (transcript.isNotEmpty()) transcript.append(' ')
                        transcript.append(text)
                        transcript.toString()
                    }
                    main.post { if (!cancelled) listener?.onPartial(snapshot) }
                }
            }
        }

        val segment = ArrayList<ShortArray>(64)
        var segSamples = 0
        var segHasSpeech = false
        val buf = ShortArray(bufSize)
        recording = true
        recorder.startRecording()
        main.post { listener?.onListening() }

        var silentMs = 0
        var anySpeech = false
        val startMs = System.currentTimeMillis()

        // Send the current phrase to the background queue (only if it actually contains speech).
        fun flushSegment() {
            if (!segHasSpeech || segSamples < SAMPLE_RATE / 2) { segment.clear(); segSamples = 0; segHasSpeech = false; return }
            val floats = FloatArray(segSamples)
            var i = 0
            for (c in segment) for (s in c) floats[i++] = s / 32768f
            queue.trySend(floats)
            segment.clear(); segSamples = 0; segHasSpeech = false
        }

        while (recording) {
            val n = recorder.read(buf, 0, buf.size)
            if (n <= 0) continue
            segment.add(buf.copyOf(n)); segSamples += n
            var sumSq = 0.0
            for (i in 0 until n) { val s = buf[i] / 32768f; sumSq += (s * s).toDouble() }
            val rms = sqrt(sumSq / n).toFloat()
            val chunkMs = n * 1000 / SAMPLE_RATE
            main.post { listener?.onAmplitude(sqrt(rms).coerceIn(0f, 1f)) }
            if (rms < SILENCE_RMS) silentMs += chunkMs else { silentMs = 0; anySpeech = true; segHasSpeech = true }

            val segMs = segSamples * 1000 / SAMPLE_RATE
            // Phrase boundary: a real pause (but short of the end-of-dictation silence) after enough
            // speech → flush this phrase and keep recording. A run-on with no pause is cut at the cap.
            if (segMs >= MIN_SEGMENT_MS && silentMs in PHRASE_SILENCE_MS until endSilenceMs) {
                flushSegment()
            } else if (segSamples >= MAX_SEGMENT_SAMPLES) {
                flushSegment()
            }
            // Hands-free stop: a long trailing silence ends the whole dictation.
            if (handsFree && anySpeech && silentMs >= endSilenceMs) break
            if (System.currentTimeMillis() - startMs > MAX_SECONDS * 1000) break
        }
        recording = false
        runCatching { recorder.stop() }; recorder.release()

        if (cancelled) { queue.close(); consumer.cancel(); unbindKeepAlive(); return }

        if (anySpeech) main.post { listener?.onProcessing() }   // recording done; finishing the last phrase
        flushSegment()        // enqueue the final phrase
        queue.close()         // no more segments
        consumer.join()       // wait for the backlog to drain
        unbindKeepAlive()

        val full = synchronized(lock) { transcript.toString() }.trim()
        lastRaw = full
        android.util.Log.d("VFRESULT", "whisper-stream → '${full.take(220)}'")
        if (!cancelled) main.post { listener?.onFinal(full) }
    }

    /** Transcribe one phrase via the isolated :asr process; "" if it OOMs (caller survives). */
    private suspend fun transcribeSegment(floats: FloatArray): String {
        val pcmFile = File(appContext.cacheDir, "whisper_${System.nanoTime()}.f32")
        return runCatching {
            WhisperAsrService.writeFloats(pcmFile, floats)
            WhisperAsrClient.transcribe(appContext, pcmFile.absolutePath, modelPath)
        }.getOrElse { "" }.let { cleanWhisper(it) }.also { runCatching { pcmFile.delete() } }
    }

    /** Hold the :asr service bound for the whole dictation so the model stays loaded across segments. */
    private fun bindKeepAlive() {
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        runCatching { appContext.bindService(Intent(appContext, WhisperAsrService::class.java), c, Context.BIND_AUTO_CREATE) }
            .onSuccess { keepAlive = c }
    }

    private fun unbindKeepAlive() {
        keepAlive?.let { runCatching { appContext.unbindService(it) } }
        keepAlive = null
    }

    /** Whisper emits bracketed non-speech tags like [BLANK_AUDIO] / (wind) — strip them. */
    private fun cleanWhisper(s: String): String =
        s.replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\s+"), " ").trim()

    override fun stop() { recording = false }
    override fun cancel() { cancelled = true; recording = false }
    override fun release() { /* keep the shared model loaded for reuse */ }

    private fun emitError(msg: String) { recording = false; main.post { listener?.onError(msg) } }
}
