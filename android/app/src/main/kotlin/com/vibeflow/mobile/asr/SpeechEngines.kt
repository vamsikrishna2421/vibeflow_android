package com.vibeflow.mobile.asr

import android.content.Context

/**
 * Chooses the speech engine for the current preference:
 *  - **online** (Google) recognizer by default — best accuracy across devices; audio is sent to
 *    the phone's Google voice service (like any keyboard's voice typing), not to Mynah servers;
 *  - **on-device** system recognizer when Private mode is on (or online isn't available) — nothing
 *    leaves the phone;
 *  - downloadable **Vosk** as the last-resort fallback when neither system path exists.
 */
object SpeechEngines {

    /**
     * [online] = use Google's online recognizer. Callers pass `!privateMode && onlineRecognition`.
     * [language] = BCP-47 recognition locale (e.g. "en-IN"); applies to both system recognizers.
     */
    fun create(context: Context, online: Boolean, language: String = "en-IN"): SpeechEngine = when {
        online && SystemSpeechEngine.isOnlineAvailable(context) -> SystemSpeechEngine(context, online = true, language = language)
        SystemSpeechEngine.isAvailable(context) -> SystemSpeechEngine(context, online = false, language = language)
        else -> VoskSpeechEngine(context)
    }

    /**
     * Environment-aware engine: when [noiseModel] == "whisper" AND the model is downloaded, do a
     * brief noise probe — if the room is noisy, use offline Whisper; otherwise the system
     * recognizer (instant). Quiet → fast; noisy → accurate. Suspends ~280ms for the probe.
     */
    suspend fun createForEnvironment(
        context: Context, online: Boolean, language: String = "en-IN", noiseModel: String = "off",
    ): SpeechEngine {
        if (noiseModel == "whisper") {
            val modelPath = WhisperModelManager.modelPathOrNull(context)
            if (modelPath != null && NoiseProbe.isNoisy(context)) {
                return WhisperSpeechEngine(context, modelPath)
            }
        }
        return create(context, online, language)
    }
}
