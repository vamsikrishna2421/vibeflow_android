package com.vibeflow.mobile.core

/**
 * Decides WHICH engine transcribes an utterance — the defense-in-depth brain.
 *
 * Because the OS won't let the system recognizer and Whisper share the mic on one
 * utterance, we pick a single engine rather than running both:
 *
 *  - PROACTIVE: from the live speech level (dBFS). Quiet voice → Whisper (the system
 *    recognizer's VAD rejects quiet speech and hallucinates). Normal/loud → system.
 *  - REACTIVE: if we used the system recognizer and its transcript still comes back
 *    degraded ([TranscriptHealth]), escalate to Whisper as a safety net.
 *
 * Thresholds are provisional — calibrate against the SNR readings from real clips.
 */
object EngineRouter {

    enum class Engine { SYSTEM, WHISPER }

    data class Decision(val engine: Engine, val reason: String, val proactive: Boolean)

    /** Speech level at/below this = quiet voice → route to Whisper. Provisional. */
    const val QUIET_SPEECH_DBFS = -28f

    /** Proactive choice from the measured speech level of the (recent) audio. */
    fun routeByLevel(speechDbfs: Float): Decision =
        if (speechDbfs <= QUIET_SPEECH_DBFS)
            Decision(Engine.WHISPER, "Quiet voice (${speechDbfs.toInt()} dBFS) — Whisper handles low voice far better", true)
        else
            Decision(Engine.SYSTEM, "Normal/loud voice — system recognizer (instant) is best", true)

    /**
     * Reactive safety net: we already used SYSTEM and have its transcript. Should we
     * still re-run Whisper? True when the system output looks like the low-voice
     * failure signature.
     */
    fun shouldEscalateToWhisper(systemHealth: TranscriptHealth.Health): Boolean =
        systemHealth.looksDegraded
}
