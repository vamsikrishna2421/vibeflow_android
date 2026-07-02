package com.vibeflow.mobile.ai

/**
 * Maps the app you're typing into to a Smart-Formatting style — the keyboard's
 * superpower (it knows the target app via EditorInfo.packageName). Email apps get
 * the "email" style, chat apps get "message", notes apps get "notes"; anything else
 * falls back to the user's chosen default. Hardcoded for the common apps; unknown
 * apps just use the default, so it never guesses wrongly into a weird shape.
 */
object AppStyle {

    private val EMAIL = setOf(
        "com.google.android.gm",                 // Gmail
        "com.microsoft.office.outlook",          // Outlook
        "com.yahoo.mobile.client.android.mail",  // Yahoo Mail
        "com.fsck.k9", "com.samsung.android.email.provider", "ch.protonmail.android",
    )
    private val MESSAGE = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.google.android.apps.messaging",     // Messages
        "org.telegram.messenger",
        "com.facebook.orca",                     // Messenger
        "com.instagram.android",
        "org.thoughtcrime.securesms",            // Signal
        "com.discord", "com.snapchat.android",
    )
    private val NOTES = setOf(
        "com.google.android.keep",
        "com.samsung.android.app.notes",         // Samsung Notes
        "com.microsoft.office.onenote",
        "com.evernote",
        "com.google.android.apps.docs.editors.docs",
        "md.obsidian", "com.notion.id",
    )
    private val PROFESSIONAL = setOf(
        "com.linkedin.android",
        "com.Slack",
    )

    /** Explicit style for a known app, or null when unrecognized (let the LLM infer it). */
    fun styleFor(packageName: String?): String? = when (packageName) {
        in EMAIL -> "email"
        in MESSAGE -> "message"
        in NOTES -> "notes"
        in PROFESSIONAL -> "structured"
        else -> null
    }
}
