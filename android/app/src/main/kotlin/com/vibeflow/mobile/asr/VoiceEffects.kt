package com.vibeflow.mobile.asr

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

/**
 * Attaches the device's hardware voice-cleanup effects to an AudioRecord session:
 *  - **AutomaticGainControl** — levels quiet/loud speech (fixes soft whispers),
 *  - **NoiseSuppressor** — focuses on the voice in noisy places,
 *  - **AcousticEchoCanceler** — removes speaker echo.
 *
 * On multi-mic phones the voice mic path already beam-forms toward the speaker;
 * these effects clean up the rest. Everything is best-effort (a missing effect on
 * a given device is simply skipped) and released with the recorder.
 */
object VoiceEffects {

    fun enable(audioSessionId: Int): List<AudioEffect> {
        val effects = mutableListOf<AudioEffect>()
        runCatching {
            if (AutomaticGainControl.isAvailable())
                AutomaticGainControl.create(audioSessionId)?.also { it.enabled = true; effects.add(it) }
        }
        runCatching {
            if (NoiseSuppressor.isAvailable())
                NoiseSuppressor.create(audioSessionId)?.also { it.enabled = true; effects.add(it) }
        }
        runCatching {
            if (AcousticEchoCanceler.isAvailable())
                AcousticEchoCanceler.create(audioSessionId)?.also { it.enabled = true; effects.add(it) }
        }
        return effects
    }

    fun release(effects: List<AudioEffect>) {
        effects.forEach { runCatching { it.release() } }
    }

    /** Human-readable summary of what the device offers (for diagnostics). */
    fun availability(): String = buildString {
        append("AGC=${tryAvail { AutomaticGainControl.isAvailable() }} ")
        append("NS=${tryAvail { NoiseSuppressor.isAvailable() }} ")
        append("AEC=${tryAvail { AcousticEchoCanceler.isAvailable() }}")
    }

    private inline fun tryAvail(block: () -> Boolean): Boolean = runCatching(block).getOrDefault(false)
}
