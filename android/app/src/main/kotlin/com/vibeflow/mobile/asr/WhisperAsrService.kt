package com.vibeflow.mobile.asr

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs Whisper inference in a DEDICATED process (`:asr`, see the manifest) so the heavy model
 * (~350 MB during inference) is isolated — if the OS OOM-kills it, only this process dies and the
 * keyboard / bubble survive (they just get an empty result and can fall back). The model is loaded
 * once here and reused. Callers pass a PCM-float file path + the model path via Messenger.
 */
class WhisperAsrService : Service() {

    private val thread = HandlerThread("whisper-asr").apply { start() }
    private val handler = Handler(thread.looper) { msg -> onMessage(msg); true }
    private val messenger = Messenger(handler)

    private var ctx: WhisperContext? = null
    private var ctxPath: String? = null

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun onMessage(msg: Message) {
        if (msg.what != MSG_TRANSCRIBE) return
        val pcmPath = msg.data.getString("pcmPath")
        val modelPath = msg.data.getString("modelPath")
        val reply = msg.replyTo
        val text = if (pcmPath == null || modelPath == null) "" else
            runCatching {
                val floats = readFloats(File(pcmPath))
                val c = ensureCtx(modelPath)
                runBlocking { c.transcribeData(floats, printTimestamp = false) }
            }.getOrElse { "" }
        runCatching {
            reply?.send(Message.obtain(null, MSG_RESULT).apply { data = Bundle().apply { putString("text", text) } })
        }
    }

    private fun ensureCtx(path: String): WhisperContext {
        ctx?.let { if (ctxPath == path) return it }
        runCatching { runBlocking { ctx?.release() } }
        return WhisperContext.createContextFromFile(path).also { ctx = it; ctxPath = path }
    }

    private fun readFloats(file: File): FloatArray {
        val bytes = file.readBytes()
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }

    override fun onDestroy() {
        runCatching { runBlocking { ctx?.release() } }
        thread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val MSG_TRANSCRIBE = 1
        const val MSG_RESULT = 2

        /** Write a PCM float buffer to a little-endian .f32 file for the :asr process to read. */
        fun writeFloats(file: File, data: FloatArray) {
            val bb = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            bb.asFloatBuffer().put(data)
            file.writeBytes(bb.array())
        }
    }
}
