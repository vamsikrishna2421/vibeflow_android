package com.vibeflow.mobile.asr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Client to the isolated [WhisperAsrService]: binds, sends the PCM file + model path, and awaits
 * the transcript. If the `:asr` process dies (e.g. OOM during inference), it resolves to "" so the
 * caller (keyboard / bubble) survives and falls back gracefully — it is never taken down with it.
 */
object WhisperAsrClient {

    suspend fun transcribe(context: Context, pcmPath: String, modelPath: String): String =
        suspendCancellableCoroutine { cont ->
            val appCtx = context.applicationContext
            var conn: ServiceConnection? = null
            var done = false
            fun finish(result: String) {
                if (done) return
                done = true
                conn?.let { runCatching { appCtx.unbindService(it) } }
                if (cont.isActive) cont.resume(result)
            }

            val replyHandler = Handler(Looper.getMainLooper()) { msg ->
                if (msg.what == WhisperAsrService.MSG_RESULT) finish(msg.data.getString("text") ?: "")
                true
            }
            val replyMessenger = Messenger(replyHandler)

            conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runCatching {
                        Messenger(service).send(Message.obtain(null, WhisperAsrService.MSG_TRANSCRIBE).apply {
                            data = Bundle().apply { putString("pcmPath", pcmPath); putString("modelPath", modelPath) }
                            replyTo = replyMessenger
                        })
                    }.onFailure { finish("") }
                }
                override fun onServiceDisconnected(name: ComponentName?) { finish("") }   // :asr died → graceful empty
            }

            val ok = runCatching {
                appCtx.bindService(Intent(appCtx, WhisperAsrService::class.java), conn, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (!ok) finish("")
            cont.invokeOnCancellation { conn?.let { runCatching { appCtx.unbindService(it) } } }
        }
}
