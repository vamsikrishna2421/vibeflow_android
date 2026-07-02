package com.vibeflow.mobile.data

import android.content.Context
import com.vibeflow.mobile.core.model.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Local, capped, searchable dictation history — stored as a single JSON file in
 * the app's private files dir. Nothing leaves the device. Pinned entries are
 * always kept and are never auto-trimmed.
 */
class HistoryRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, "dictation_history.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    @Volatile private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            _entries.value = readFromDisk()
            loaded = true
        }
    }

    /** Append a dictation; trims to [max] keeping pinned entries. Returns the new entry. */
    suspend fun add(
        text: String, app: String, target: String, max: Int,
        raw: String = "", durationSec: Float = 0f, pkg: String = "",
    ): HistoryEntry? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        ensureLoaded()
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val entry = HistoryEntry(
                id = now, ts = now / 1000, text = clean, app = app, pkg = pkg, target = target,
                raw = raw.trim(), durationSec = durationSec,
            )
            val next = (_entries.value + entry)
            val trimmed = trim(next, max)
            _entries.value = trimmed
            writeToDisk(trimmed)
            entry
        }
    }

    /** Replace the text of a stage on an entry (e.g. user edited it, or L3 finished). */
    suspend fun update(
        id: Long, raw: String? = null, text: String? = null, polished: String? = null,
        promptTokens: Int? = null, completionTokens: Int? = null,
    ) = mutate { list ->
        list.map {
            if (it.id != id) it
            else it.copy(
                raw = raw ?: it.raw, text = text ?: it.text, polished = polished ?: it.polished,
                promptTokens = promptTokens ?: it.promptTokens,
                completionTokens = completionTokens ?: it.completionTokens,
            )
        }
    }

    suspend fun delete(id: Long) = mutate { list -> list.filterNot { it.id == id } }

    suspend fun togglePin(id: Long) = mutate { list ->
        list.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
    }

    /** Clears unpinned entries; pinned ones are kept. */
    suspend fun clearUnpinned() = mutate { list -> list.filter { it.pinned } }

    private suspend fun mutate(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        ensureLoaded()
        mutex.withLock {
            val next = transform(_entries.value)
            _entries.value = next
            writeToDisk(next)
        }
    }

    private fun trim(list: List<HistoryEntry>, max: Int): List<HistoryEntry> {
        if (max <= 0 || list.size <= max) return list
        val pinned = list.filter { it.pinned }
        val unpinned = list.filterNot { it.pinned }
        val keepUnpinned = (max - pinned.size).coerceAtLeast(0)
        val keptUnpinned = unpinned.takeLast(keepUnpinned)
        // Preserve original ordering (chronological by id).
        return (pinned + keptUnpinned).sortedBy { it.id }
    }

    private suspend fun readFromDisk(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) emptyList()
            else json.decodeFromString(ListSerializer(HistoryEntry.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun writeToDisk(list: List<HistoryEntry>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(HistoryEntry.serializer()), list))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }
}
