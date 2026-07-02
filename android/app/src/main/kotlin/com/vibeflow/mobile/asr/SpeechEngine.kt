package com.vibeflow.mobile.asr

/**
 * Offline streaming speech-to-text, abstracted so the rest of the app never
 * depends on a specific engine. The MVP ships a Vosk implementation; a
 * whisper.cpp engine can be added later behind the same contract.
 */
interface SpeechEngine {

    /** Live recognition callbacks. All are delivered on the main thread. */
    interface Listener {
        /** The model is loaded and the mic is now live. */
        fun onListening() {}
        /** Interim hypothesis for the current utterance (updates rapidly). */
        fun onPartial(text: String) {}
        /** Recording has stopped; the engine is finishing the final transcript (e.g. the Whisper
         *  streaming drain) — distinct from [onListening]. UIs can switch to a "processing" cue. */
        fun onProcessing() {}
        /** Live mic loudness in 0f..1f, ~10x/sec — drives the waveform animation. */
        fun onAmplitude(level: Float) {}
        /** Final, stable transcript for the whole session (after [stop]). */
        fun onFinal(text: String) {}
        /** A non-fatal or fatal problem occurred; recognition has ended. */
        fun onError(message: String) {}
    }

    /** True once the model has been loaded into memory at least once. */
    val isModelLoaded: Boolean

    /**
     * The most recent session's transcript BEFORE any curation/sentence-splitting —
     * i.e. what the recognizer literally produced. Used for the "Raw" capture stage.
     * Defaults to empty for engines that don't distinguish it.
     */
    val lastRawTranscript: String get() = ""

    /** Load the model into memory ahead of time (safe to call repeatedly). */
    suspend fun warmUp()

    /**
     * Begin listening. Emits [Listener.onListening] then [Listener.onPartial]s.
     *
     * @param handsFree when true, the engine auto-stops after the speaker pauses
     *   for [endSilenceMs] (voice-activity detection); when false it records until
     *   [stop]/[cancel].
     * @param endSilenceMs trailing-silence duration that ends a hands-free session.
     */
    fun start(listener: Listener, handsFree: Boolean = false, endSilenceMs: Int = 1500)

    /** Stop and finalize — produces [Listener.onFinal]. */
    fun stop()

    /** Stop and discard the current session (no final result). */
    fun cancel()

    /** Free native resources held by the recognizer (keeps the shared model). */
    fun release()
}
