package com.vibeflow.mobile.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * A brief ambient listen to estimate the **noise floor** before a dictation, so we can route
 * to Whisper (noisy) vs the system recognizer (quiet). Runs ~280 ms before "Listening…", so it
 * captures background noise — not the user's voice (they haven't spoken yet).
 */
object NoiseProbe {
    private const val SAMPLE_RATE = 16000

    /** True when the ambient noise floor exceeds [thresholdRms] (normalized 0..1).
     *  0.014 cleanly separates a quiet room (~0.006) from a fan/cafe (~0.02–0.05) on the F22. */
    suspend fun isNoisy(context: Context, thresholdRms: Float = 0.014f, durationMs: Int = 280): Boolean =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return@withContext false
            }
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return@withContext false
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
            } catch (e: Exception) { return@withContext false }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext false }

            val buf = ShortArray(minBuf)
            val frames = ArrayList<Float>()
            rec.startRecording()
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < durationMs) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sumSq = 0.0
                for (i in 0 until n) { val s = buf[i] / 32768f; sumSq += (s * s).toDouble() }
                frames.add(sqrt(sumSq / n).toFloat())
            }
            runCatching { rec.stop() }; rec.release()

            if (frames.isEmpty()) return@withContext false
            // Noise floor ≈ a low percentile of the frame RMS (robust to a transient blip).
            val floor = frames.sorted()[frames.size / 3]
            android.util.Log.d("VFNOISE", "floor=$floor threshold=$thresholdRms noisy=${floor > thresholdRms}")
            floor > thresholdRms
        }
}
