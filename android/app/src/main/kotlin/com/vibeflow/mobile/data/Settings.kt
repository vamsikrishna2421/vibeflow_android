package com.vibeflow.mobile.data

import com.vibeflow.mobile.core.OutputRouting
import com.vibeflow.mobile.core.Pipeline
import com.vibeflow.mobile.core.TextCuration

/**
 * All user-tunable behaviour in one immutable snapshot. Sensible, opinionated
 * defaults so Mynah is great out of the box — history ON (the user asked for
 * it), spoken punctuation + auto-capitalisation ON (the offline engine emits raw
 * lowercase), filler removal OFF (conservative, opt-in like the desktop).
 */
data class Settings(
    val outputMode: String = "auto",          // auto | type | clipboard
    val trailingSpace: Boolean = true,
    val stripFillers: Boolean = false,
    val spokenPunctuation: Boolean = true,
    val spokenCommands: Boolean = true,
    val capitalizeSentences: Boolean = true,
    val capitalizeFirst: Boolean = true,
    val autoPeriod: Boolean = true,
    val fixPronounI: Boolean = true,
    val historyEnabled: Boolean = true,
    val historyMax: Int = 1000,
    val haptics: Boolean = true,
    val soundCues: Boolean = false,
    val pushToTalk: Boolean = false,           // false = tap-to-toggle, true = hold-to-talk
    val handsFree: Boolean = false,            // tap once, speak, auto-stops on silence (VAD)
    val endSilenceMs: Int = 1500,              // trailing-silence that ends a hands-free session
    val vocabulary: List<String> = emptyList(),
    val snippets: Map<String, String> = emptyMap(),
    // L3 Smart Formatting (BYOK). Empty key = feature off. The key is the user's own
    // and stays on-device; the managed/Supabase tier comes later.
    val llmApiKey: String = "",
    val llmModel: String = "gpt-4.1-nano",
    val smartFormatStyle: String = "cleanup",       // faithful default (preserves words); cleanup | structured | email | message | notes
    val autoPolish: Boolean = true,                // auto-run L3 after each dictation (only acts when a key is set)
    val matchStyleToApp: Boolean = true,           // pick the style from the app you're typing into
    val privateMode: Boolean = false,              // on-device only — never send text to the cloud LLM
    val onlineRecognition: Boolean = true,         // Google online recognizer (accuracy); Private mode forces on-device
    val recognitionLanguage: String = "en-IN",     // BCP-47 STT locale: auto | en-IN | en-US | en-GB | en-AU
    val noiseModel: String = "whisper",            // "off" = always system recognizer; "whisper" = auto-route to offline Whisper when noisy (default ON — model auto-downloads at setup)
    // Which Smart Formatting path to use:
    //  private  = on-device L1/L2 only, nothing leaves the device (== privateMode)
    //  byok     = remote OpenAI with the user's own key (llmApiKey)
    //  managed  = Mynah's Supabase proxy (Google sign-in, free-50 trial / Pro)
    val smartFormatTier: String = "byok",
    val floatingMic: Boolean = false,              // show the draggable floating mic bubble
    val autoInsert: Boolean = false,               // opt-in: type dictation at the cursor via the Accessibility service
    val keyboardTheme: String = "dark",            // keyboard appearance: system | light | dark (dark default, like most keyboards)
    // Profile for personalized formatting (e.g. email sign-offs).
    val userName: String = "",
    val userTitle: String = "",
    val onboardingDone: Boolean = false,   // first-run onboarding flow completed
) {
    // These curation behaviours are ALWAYS on (not user-customizable) — they're what
    // makes dictation read like written text: punctuation, caps, fillers, spoken commands.
    fun curationOptions(): TextCuration.Options = TextCuration.Options(
        spokenCommands = true,
        spokenPunctuation = true,
        capitalizeSentences = true,
        capitalizeFirst = true,
        fixPronounI = true,
        stripFillers = true,
        autoPeriod = true,
    )

    fun pipelineConfig(): Pipeline.Config = Pipeline.Config(
        vocabulary = vocabulary,
        snippets = snippets,
        curation = curationOptions(),
    )

    fun routingMode(): OutputRouting.Mode = OutputRouting.modeFromString(outputMode)

    /** Concrete BCP-47 tag for the recognizer; "auto" follows the device locale. */
    fun resolvedRecognitionLanguage(): String =
        if (recognitionLanguage == "auto") java.util.Locale.getDefault().toLanguageTag() else recognitionLanguage
}
