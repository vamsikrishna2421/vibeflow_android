package com.vibeflow.mobile

import android.app.Application
import com.vibeflow.mobile.auth.SupabaseAuth
import com.vibeflow.mobile.data.CorrectionsRepository
import com.vibeflow.mobile.data.DictionaryRepository
import com.vibeflow.mobile.data.HistoryRepository
import com.vibeflow.mobile.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application + tiny service locator. Repositories are process-wide singletons so
 * the keyboard, the Quick Settings capture flow, and the app UI all read and
 * write the same settings and history.
 */
class VibeFlowApp : Application() {

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val history: HistoryRepository by lazy { HistoryRepository(this) }
    val dictionary: DictionaryRepository by lazy { DictionaryRepository(this) }
    val corrections: CorrectionsRepository by lazy { CorrectionsRepository(this) }
    val supabaseAuth: SupabaseAuth by lazy { SupabaseAuth(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Google Play billing (inert until the goog_ key is set).
        com.vibeflow.mobile.billing.RevenueCatManager.configure(this)
        // Encrypt any legacy plaintext API key at rest (Android Keystore migration).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { settings.migrateSecretsIfNeeded() }
        }
    }

    companion object {
        @Volatile
        lateinit var instance: VibeFlowApp
            private set

        fun settings(): SettingsRepository = instance.settings
        fun history(): HistoryRepository = instance.history
        fun dictionary(): DictionaryRepository = instance.dictionary
        fun corrections(): CorrectionsRepository = instance.corrections
        fun supabaseAuth(): SupabaseAuth = instance.supabaseAuth
    }
}
