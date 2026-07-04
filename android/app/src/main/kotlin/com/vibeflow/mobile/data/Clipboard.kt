package com.vibeflow.mobile.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Thin wrapper around the system clipboard. */
object Clipboard {
    fun copy(context: Context, text: String, label: String = "VibeFlow") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
