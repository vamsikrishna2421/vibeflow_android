package com.vibeflow.mobile.data

import android.content.Context
import com.vibeflow.mobile.core.CorrectionLearner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** A learned "heard → meant" correction and how many times the user has made it. */
@Serializable
data class CorrectionRecord(val from: String, val to: String, val count: Int)

/**
 * Persists vocabulary corrections learned from the user's edits ([CorrectionLearner]).
 * A correction is only *applied* once seen at least [CONFIRM_THRESHOLD] times, so a
 * one-off edit never sticks. Local JSON file; nothing leaves the device.
 */
class CorrectionsRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, "corrections.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _records = MutableStateFlow<List<CorrectionRecord>>(emptyList())
    val records: StateFlow<List<CorrectionRecord>> = _records.asStateFlow()

    @Volatile private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            _records.value = readFromDisk()
            loaded = true
        }
    }

    /** Record corrections, incrementing counts (a changed target resets the count). */
    suspend fun learn(corrections: List<CorrectionLearner.Correction>) {
        if (corrections.isEmpty()) return
        ensureLoaded()
        mutex.withLock {
            val byFrom = _records.value.associateBy { it.from }.toMutableMap()
            for (c in corrections) {
                val existing = byFrom[c.from]
                byFrom[c.from] = if (existing != null && existing.to.equals(c.to, ignoreCase = true))
                    existing.copy(count = existing.count + 1)
                else CorrectionRecord(c.from, c.to, 1)
            }
            val next = byFrom.values.toList()
            _records.value = next
            writeToDisk(next)
        }
    }

    /** from→to for corrections confirmed by repetition — the map applied to dictation. */
    fun confirmed(threshold: Int = CONFIRM_THRESHOLD): Map<String, String> =
        _records.value.filter { it.count >= threshold }.associate { it.from to it.to }

    suspend fun remove(from: String) = mutate { list -> list.filterNot { it.from == from } }
    suspend fun clear() = mutate { emptyList() }

    private suspend fun mutate(transform: (List<CorrectionRecord>) -> List<CorrectionRecord>) {
        ensureLoaded()
        mutex.withLock {
            val next = transform(_records.value)
            _records.value = next
            writeToDisk(next)
        }
    }

    private suspend fun readFromDisk(): List<CorrectionRecord> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) emptyList()
            else json.decodeFromString(ListSerializer(CorrectionRecord.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun writeToDisk(list: List<CorrectionRecord>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(CorrectionRecord.serializer()), list))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    // Learn from a SINGLE correction (the limited-change guard in CorrectionLearner already
    // blocks full rewrites, and learning only fires when the original word was unknown).
    companion object { const val CONFIRM_THRESHOLD = 1 }
}
