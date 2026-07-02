package com.vibeflow.mobile.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages the offline Vosk model.
 *
 * The model is intentionally **not bundled** in the APK (it's ~40 MB). It's downloaded
 * on demand the first time a user actually needs the offline engine — i.e. on a phone
 * without an on-device system recognizer, or when they explicitly want a guaranteed-
 * offline experience. The everyday path uses the OS's on-device [SystemSpeechEngine],
 * so most users never download this at all. Once fetched it's unzipped into the app's
 * private files dir and cached as a [Model] for the whole process.
 */
object ModelManager {

    private const val TARGET_DIR = "vibeflow-model"
    private const val VERSION = "vosk-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    /** Lifecycle of the offline model, surfaced to the UI. */
    sealed interface State {
        object Absent : State
        data class Downloading(val percent: Int) : State
        object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Absent)
    val state: StateFlow<State> = _state

    @Volatile private var cached: Model? = null
    private val mutex = Mutex()

    val isLoaded: Boolean get() = cached != null

    private fun modelDir(context: Context) = File(context.applicationContext.filesDir, TARGET_DIR)

    /** True once the model has been fully downloaded & unzipped. */
    fun isDownloaded(context: Context): Boolean {
        val marker = File(modelDir(context), ".version")
        return marker.exists() && runCatching { marker.readText() == VERSION }.getOrDefault(false)
    }

    /** Sync the public [state] with what's actually on disk (call on app start). */
    fun refreshState(context: Context) {
        if (_state.value is State.Downloading) return
        _state.value = if (isDownloaded(context)) State.Ready else State.Absent
    }

    /** Build (or return the cached) [Model]. Throws [ModelNotDownloaded] if it isn't present yet. */
    suspend fun getModel(context: Context): Model {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: run {
                if (!isDownloaded(context)) throw ModelNotDownloaded()
                Model(modelDir(context).absolutePath).also { cached = it; _state.value = State.Ready }
            }
        }
    }

    /** Download + unzip the model if absent. Idempotent; returns true when the model is ready. */
    suspend fun ensureDownloaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(context)) { _state.value = State.Ready; return@withContext true }
        mutex.withLock {
            if (isDownloaded(context)) { _state.value = State.Ready; return@withLock true }
            val dir = modelDir(context)
            try {
                _state.value = State.Downloading(0)
                if (dir.exists()) dir.deleteRecursively()
                dir.mkdirs()
                downloadAndUnzip(MODEL_URL, dir) { pct -> _state.value = State.Downloading(pct) }
                File(dir, ".version").writeText(VERSION)
                _state.value = State.Ready
                true
            } catch (t: Throwable) {
                runCatching { dir.deleteRecursively() }
                _state.value = State.Failed(t.message ?: "Download failed")
                false
            }
        }
    }

    /** Delete the downloaded model to reclaim space. */
    fun remove(context: Context) {
        cached = null
        runCatching { modelDir(context).deleteRecursively() }
        _state.value = State.Absent
    }

    /**
     * Stream the model zip straight through [ZipInputStream] into [dest], stripping the
     * archive's single top-level folder so files land directly under [dest]. Progress is
     * reported against the compressed Content-Length (good enough for a progress bar).
     */
    private fun downloadAndUnzip(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Server returned ${conn.responseCode}")
            val total = conn.contentLength.toLong()
            var read = 0L
            var lastPct = -1
            val counting = object : FilterInputStream(conn.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0 && total > 0) {
                        read += n
                        val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                    }
                    return n
                }
            }
            ZipInputStream(BufferedInputStream(counting)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val rel = entry.name.substringAfter('/', "")   // strip the top-level folder
                    if (rel.isNotEmpty() && !entry.isDirectory) {
                        val outFile = File(dest, rel)
                        // Zip-slip guard: never let an entry escape [dest].
                        if (!outFile.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
                            throw IOException("Unsafe zip entry: ${entry.name}")
                        }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Thrown by [getModel] when the offline model hasn't been downloaded yet. */
    class ModelNotDownloaded : IllegalStateException("Offline model not downloaded")
}
