package com.vibeflow.mobile.data

import android.content.Context
import com.vibeflow.mobile.core.SuggestionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Owns the process-wide [SuggestionEngine]: loads the bundled base dictionary
 * (`assets/en_50k.txt`) once, restores the user's learned words, and persists new
 * learning (throttled). Also powers vocabulary export/import for migration.
 */
class DictionaryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val learnedFile = File(appContext.filesDir, "learned_words.json")
    private val mapSerializer = MapSerializer(String.serializer(), Long.serializer())

    val engine = SuggestionEngine()
    @Volatile var ready = false
        private set

    private var dirtyCount = 0

    suspend fun ensureLoaded() {
        if (ready) return
        mutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.assets.open("en_50k.txt").bufferedReader().useLines { engine.loadBase(it) }
                }
                runCatching {
                    if (learnedFile.exists()) {
                        engine.setLearned(json.decodeFromString(mapSerializer, learnedFile.readText()))
                    }
                }
            }
            ready = true
        }
    }

    /** Learn a committed word and persist periodically. */
    fun learn(word: String) {
        if (!ready) return
        val map = engine.learn(word)
        if (++dirtyCount >= 6) { dirtyCount = 0; save(map) }
    }

    fun flush() = save(engine.learnedSnapshot())

    private fun save(map: Map<String, Long>) {
        scope.launch {
            runCatching {
                val tmp = File(learnedFile.parentFile, learnedFile.name + ".tmp")
                tmp.writeText(json.encodeToString(mapSerializer, map))
                if (learnedFile.exists()) learnedFile.delete()
                tmp.renameTo(learnedFile)
            }
        }
    }

    // --- migration: export / import the personal dictionary ---

    fun exportLearned(): String = json.encodeToString(mapSerializer, engine.learnedSnapshot())

    fun importLearned(text: String, merge: Boolean = true) {
        val incoming = runCatching { json.decodeFromString(mapSerializer, text) }.getOrNull() ?: return
        val base = if (merge) engine.learnedSnapshot().toMutableMap() else mutableMapOf()
        for ((k, v) in incoming) base[k] = (base[k] ?: 0L) + v
        engine.setLearned(base)
        save(base)
    }

    /** Teach from a block of the user's own writing (the practical "import"). */
    fun learnFromText(text: String) {
        Regex("[\\p{L}']+").findAll(text).forEach { engine.learn(it.value) }
        save(engine.learnedSnapshot())
    }
}
