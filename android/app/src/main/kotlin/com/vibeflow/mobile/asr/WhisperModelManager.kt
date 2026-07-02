package com.vibeflow.mobile.asr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads & manages the offline **Whisper small (q5)** model used for noisy environments.
 * NOT bundled in the APK (~181 MB) — fetched on demand into the app's private files dir, like
 * the Vosk model. The system recognizer handles quiet speech; this is the noise fallback.
 */
object WhisperModelManager {

    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"
    private const val FILE_NAME = "ggml-small-q5_1.bin"
    private const val MIN_VALID_BYTES = 100_000_000L   // sanity floor (~100 MB) so a partial download isn't "Ready"

    sealed interface State {
        data object Absent : State
        data object WaitingForWifi : State          // metered network — auto-download deferred until Wi-Fi
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Absent)
    val state: StateFlow<State> = _state
    private val mutex = Mutex()

    fun modelFile(context: Context): File = File(File(context.applicationContext.filesDir, "whisper").apply { mkdirs() }, FILE_NAME)

    fun isDownloaded(context: Context): Boolean = modelFile(context).let { it.exists() && it.length() >= MIN_VALID_BYTES }

    fun refreshState(context: Context) {
        if (_state.value is State.Downloading) return
        _state.value = if (isDownloaded(context)) State.Ready else State.Absent
    }

    /** Returns the absolute model path, or null if not downloaded. */
    fun modelPathOrNull(context: Context): String? = if (isDownloaded(context)) modelFile(context).absolutePath else null

    /** True only on an unmetered (Wi-Fi-class) connection — so the ~180 MB pull never hits cellular.
     *  A metered Wi-Fi hotspot correctly counts as metered and is excluded. */
    fun isUnmetered(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Background/auto entry point (onboarding + every app start). Downloads ONLY on Wi-Fi; on a
     * metered network it parks at [State.WaitingForWifi] and is retried next launch. User-initiated
     * downloads from Settings use [ensureDownloaded] and ignore this gate.
     */
    suspend fun autoDownload(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(context)) { _state.value = State.Ready; return@withContext true }
        if (!isUnmetered(context)) {
            if (_state.value !is State.Downloading) _state.value = State.WaitingForWifi
            return@withContext false
        }
        ensureDownloaded(context)
    }

    suspend fun ensureDownloaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(context)) { _state.value = State.Ready; return@withContext true }
        mutex.withLock {
            if (isDownloaded(context)) { _state.value = State.Ready; return@withLock true }
            val dest = modelFile(context)
            val tmp = File(dest.parentFile, "$FILE_NAME.part")
            runCatching {
                _state.value = State.Downloading(0)
                download(MODEL_URL, tmp) { pct -> _state.value = State.Downloading(pct) }
                if (tmp.length() < MIN_VALID_BYTES) error("Incomplete download")
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) error("Couldn't finalize the model file")
                _state.value = State.Ready
                true
            }.getOrElse { t ->
                runCatching { tmp.delete() }
                _state.value = State.Failed(t.message ?: "Download failed")
                false
            }
        }
    }

    fun remove(context: Context) {
        runCatching { modelFile(context).delete() }
        _state.value = State.Absent
    }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
        }
        conn.connect()
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var sum = 0L
                var lastPct = -1
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    sum += read
                    if (total > 0) {
                        val pct = ((sum * 100) / total).toInt()
                        if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                    }
                }
            }
        }
        conn.disconnect()
    }
}
