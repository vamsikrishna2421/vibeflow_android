package com.vibeflow.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vibeflow_settings")

/**
 * Persists [Settings] in a Preferences DataStore. Exposes a reactive [flow] and
 * a one-shot [snapshot] (used by the keyboard at delivery time). Lists/maps are
 * stored as JSON strings.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    // True when this session FAILED to decrypt an existing (encrypted) API key — a
    // transient Keystore hiccup. We use it to refuse a blank-overwrite that would
    // otherwise silently destroy the real key (the UI shows "Not set" in this state).
    @Volatile private var apiKeyReadFailed = false

    private object Keys {
        val outputMode = stringPreferencesKey("output_mode")
        val trailingSpace = booleanPreferencesKey("trailing_space")
        val stripFillers = booleanPreferencesKey("strip_fillers")
        val spokenPunctuation = booleanPreferencesKey("spoken_punctuation")
        val spokenCommands = booleanPreferencesKey("spoken_commands")
        val capitalizeSentences = booleanPreferencesKey("capitalize_sentences")
        val capitalizeFirst = booleanPreferencesKey("capitalize_first")
        val autoPeriod = booleanPreferencesKey("auto_period")
        val fixPronounI = booleanPreferencesKey("fix_pronoun_i")
        val historyEnabled = booleanPreferencesKey("history_enabled")
        val historyMax = intPreferencesKey("history_max")
        val haptics = booleanPreferencesKey("haptics")
        val soundCues = booleanPreferencesKey("sound_cues")
        val pushToTalk = booleanPreferencesKey("push_to_talk")
        val handsFree = booleanPreferencesKey("hands_free")
        val endSilenceMs = intPreferencesKey("end_silence_ms")
        val vocabulary = stringPreferencesKey("vocabulary_json")
        val snippets = stringPreferencesKey("snippets_json")
        val llmApiKey = stringPreferencesKey("llm_api_key")
        val llmModel = stringPreferencesKey("llm_model")
        val smartFormatStyle = stringPreferencesKey("smart_format_style")
        val smartFormatTier = stringPreferencesKey("smart_format_tier")
        val autoPolish = booleanPreferencesKey("auto_polish")
        val matchStyleToApp = booleanPreferencesKey("match_style_to_app")
        val privateMode = booleanPreferencesKey("private_mode")
        val onlineRecognition = booleanPreferencesKey("online_recognition")
        val recognitionLanguage = stringPreferencesKey("recognition_language")
        val noiseModel = stringPreferencesKey("noise_model")
        val floatingMic = booleanPreferencesKey("floating_mic")
        val autoInsert = booleanPreferencesKey("auto_insert")
        val keyboardTheme = stringPreferencesKey("keyboard_theme")
        val userName = stringPreferencesKey("user_name")
        val userTitle = stringPreferencesKey("user_title")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val flow: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun snapshot(): Settings = flow.first()

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        // Decrypt the API key once, recording whether a transient failure (null) hid a
        // real blob — so the save path can refuse to overwrite it with a blank.
        val storedKey = this[Keys.llmApiKey]
        val decryptedKey = storedKey?.let { KeystoreCrypto.decrypt(it) }
        apiKeyReadFailed = storedKey != null && KeystoreCrypto.isEncrypted(storedKey) && decryptedKey == null
        return Settings(
            outputMode = this[Keys.outputMode] ?: d.outputMode,
            trailingSpace = this[Keys.trailingSpace] ?: d.trailingSpace,
            stripFillers = this[Keys.stripFillers] ?: d.stripFillers,
            spokenPunctuation = this[Keys.spokenPunctuation] ?: d.spokenPunctuation,
            spokenCommands = this[Keys.spokenCommands] ?: d.spokenCommands,
            capitalizeSentences = this[Keys.capitalizeSentences] ?: d.capitalizeSentences,
            capitalizeFirst = this[Keys.capitalizeFirst] ?: d.capitalizeFirst,
            autoPeriod = this[Keys.autoPeriod] ?: d.autoPeriod,
            fixPronounI = this[Keys.fixPronounI] ?: d.fixPronounI,
            historyEnabled = this[Keys.historyEnabled] ?: d.historyEnabled,
            historyMax = this[Keys.historyMax] ?: d.historyMax,
            haptics = this[Keys.haptics] ?: d.haptics,
            soundCues = this[Keys.soundCues] ?: d.soundCues,
            pushToTalk = this[Keys.pushToTalk] ?: d.pushToTalk,
            handsFree = this[Keys.handsFree] ?: d.handsFree,
            endSilenceMs = this[Keys.endSilenceMs] ?: d.endSilenceMs,
            vocabulary = this[Keys.vocabulary]?.let { decodeList(it) } ?: d.vocabulary,
            snippets = this[Keys.snippets]?.let { decodeMap(it) } ?: d.snippets,
            // The API key is stored encrypted (Android Keystore); a transient decrypt
            // failure (null) surfaces as "" in memory (feature inert) but the blob is kept.
            llmApiKey = decryptedKey ?: d.llmApiKey,
            llmModel = this[Keys.llmModel] ?: d.llmModel,
            smartFormatStyle = this[Keys.smartFormatStyle] ?: d.smartFormatStyle,
            smartFormatTier = this[Keys.smartFormatTier] ?: d.smartFormatTier,
            autoPolish = this[Keys.autoPolish] ?: d.autoPolish,
            matchStyleToApp = this[Keys.matchStyleToApp] ?: d.matchStyleToApp,
            privateMode = this[Keys.privateMode] ?: d.privateMode,
            onlineRecognition = this[Keys.onlineRecognition] ?: d.onlineRecognition,
            recognitionLanguage = this[Keys.recognitionLanguage] ?: d.recognitionLanguage,
            noiseModel = this[Keys.noiseModel] ?: d.noiseModel,
            floatingMic = this[Keys.floatingMic] ?: d.floatingMic,
            autoInsert = this[Keys.autoInsert] ?: d.autoInsert,
            keyboardTheme = this[Keys.keyboardTheme] ?: d.keyboardTheme,
            userName = this[Keys.userName] ?: d.userName,
            userTitle = this[Keys.userTitle] ?: d.userTitle,
            onboardingDone = this[Keys.onboardingDone] ?: d.onboardingDone,
        )
    }

    private fun decodeList(s: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), s) }.getOrDefault(emptyList())

    private fun decodeMap(s: String): Map<String, String> =
        runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), s) }
            .getOrDefault(emptyMap())

    suspend fun setOutputMode(value: String) = store.edit { it[Keys.outputMode] = value }
    suspend fun setTrailingSpace(value: Boolean) = store.edit { it[Keys.trailingSpace] = value }
    suspend fun setStripFillers(value: Boolean) = store.edit { it[Keys.stripFillers] = value }
    suspend fun setSpokenPunctuation(value: Boolean) = store.edit { it[Keys.spokenPunctuation] = value }
    suspend fun setSpokenCommands(value: Boolean) = store.edit { it[Keys.spokenCommands] = value }
    suspend fun setCapitalizeSentences(value: Boolean) = store.edit { it[Keys.capitalizeSentences] = value }
    suspend fun setCapitalizeFirst(value: Boolean) = store.edit { it[Keys.capitalizeFirst] = value }
    suspend fun setAutoPeriod(value: Boolean) = store.edit { it[Keys.autoPeriod] = value }
    suspend fun setFixPronounI(value: Boolean) = store.edit { it[Keys.fixPronounI] = value }
    suspend fun setHistoryEnabled(value: Boolean) = store.edit { it[Keys.historyEnabled] = value }
    suspend fun setHaptics(value: Boolean) = store.edit { it[Keys.haptics] = value }
    suspend fun setSoundCues(value: Boolean) = store.edit { it[Keys.soundCues] = value }
    suspend fun setPushToTalk(value: Boolean) = store.edit { it[Keys.pushToTalk] = value }
    suspend fun setHandsFree(value: Boolean) = store.edit { it[Keys.handsFree] = value }

    /**
     * Store the API key encrypted (Android Keystore) — only ciphertext touches disk. Two
     * guards protect the (paid) key: (1) if encryption fails we skip the write entirely
     * rather than persist plaintext; (2) we refuse to overwrite a real encrypted key with
     * a blank when we couldn't decrypt it this session (a transient Keystore hiccup showing
     * "Not set"). An explicit clear still works whenever the key was readable.
     */
    suspend fun setLlmApiKey(value: String) = store.edit { prefs ->
        val v = value.trim()
        val existing = prefs[Keys.llmApiKey]
        val hasEncryptedKey = existing != null && KeystoreCrypto.isEncrypted(existing)
        if (v.isEmpty()) {
            if (hasEncryptedKey && apiKeyReadFailed) return@edit   // don't wipe a key we just couldn't read
            prefs[Keys.llmApiKey] = ""
        } else {
            val enc = KeystoreCrypto.encrypt(v) ?: return@edit     // Keystore down → keep prior, never store plaintext
            prefs[Keys.llmApiKey] = enc
        }
    }

    /** One-time: encrypt any pre-existing *plaintext* API key from before Keystore storage. */
    suspend fun migrateSecretsIfNeeded() {
        val raw = store.data.first()[Keys.llmApiKey] ?: return
        if (raw.isNotEmpty() && !KeystoreCrypto.isEncrypted(raw)) {
            // Only commit if we actually produced ciphertext; on Keystore failure leave the
            // legacy plaintext untouched and retry next launch (never re-commit cleartext).
            val enc = KeystoreCrypto.encrypt(raw) ?: return
            store.edit { it[Keys.llmApiKey] = enc }
        }
    }
    suspend fun setLlmModel(value: String) = store.edit { it[Keys.llmModel] = value.trim() }
    suspend fun setSmartFormatStyle(value: String) = store.edit { it[Keys.smartFormatStyle] = value }
    suspend fun setSmartFormatTier(value: String) = store.edit { it[Keys.smartFormatTier] = value }
    suspend fun setAutoPolish(value: Boolean) = store.edit { it[Keys.autoPolish] = value }
    suspend fun setMatchStyleToApp(value: Boolean) = store.edit { it[Keys.matchStyleToApp] = value }
    suspend fun setPrivateMode(value: Boolean) = store.edit { it[Keys.privateMode] = value }
    suspend fun setFloatingMic(value: Boolean) = store.edit { it[Keys.floatingMic] = value }
    suspend fun setAutoInsert(value: Boolean) = store.edit { it[Keys.autoInsert] = value }
    suspend fun setOnlineRecognition(value: Boolean) = store.edit { it[Keys.onlineRecognition] = value }
    suspend fun setRecognitionLanguage(value: String) = store.edit { it[Keys.recognitionLanguage] = value }
    suspend fun setNoiseModel(value: String) = store.edit { it[Keys.noiseModel] = value }
    suspend fun setKeyboardTheme(value: String) = store.edit { it[Keys.keyboardTheme] = value }
    suspend fun setUserName(value: String) = store.edit { it[Keys.userName] = value.trim() }
    suspend fun setUserTitle(value: String) = store.edit { it[Keys.userTitle] = value.trim() }
    suspend fun setOnboardingDone(value: Boolean) = store.edit { it[Keys.onboardingDone] = value }

    suspend fun setVocabulary(terms: List<String>) = store.edit {
        it[Keys.vocabulary] = json.encodeToString(ListSerializer(String.serializer()), terms)
    }

    suspend fun setSnippets(snippets: Map<String, String>) = store.edit {
        it[Keys.snippets] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), snippets)
    }
}
