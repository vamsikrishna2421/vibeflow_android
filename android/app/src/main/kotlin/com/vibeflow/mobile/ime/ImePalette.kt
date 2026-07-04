package com.vibeflow.mobile.ime

import android.content.Context
import android.content.res.Configuration

/**
 * Gboard-accurate palette so VibeFlow feels like the keyboard people already use.
 * Neutral cool greys (not the old warm tones), flat keys, Google-blue accents.
 * Resolved once per [KeyboardView] from the system dark/light mode.
 */
class ImePalette(context: Context, themePref: String = "system") {

    // "dark"/"light" force the theme (like Gboard's setting); "system" follows the phone.
    val dark: Boolean = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    // Keyboard background (flat — Gboard has no gradient).
    val bgTop = pick(0xFF202124, 0xFFDDE1E6)
    val bgBottom = pick(0xFF202124, 0xFFDDE1E6)
    val surface = pick(0xFF3C4043, 0xFFFFFFFF)        // letter key caps
    val surfaceHigh = pick(0xFF2C2F33, 0xFFBFC4CB)    // function keys (shift, ?123, enter)
    val keyShadow = pick(0xFF17181A, 0xFFBCC1C8)      // baked drop-shadow lip under key caps (depth)
    val keyText = pick(0xFFE8EAED, 0xFF1F1F1F)
    val keyMuted = pick(0xFF9AA0A6, 0xFF5F6368)       // number hints, space label
    val ink = pick(0xFFE8EAED, 0xFF1F1F1F)
    val accentLavender = pick(0xFF8AB4F8, 0xFF1A73E8) // Google blue
    val accentTertiary = pick(0xFFAECBFA, 0xFF4285F4)
    val ctaGreen = pick(0xFF8AB4F8, 0xFF1A73E8)       // mic button (Google blue)
    val recording = pick(0xFFE8896B, 0xFFD93025)      // recording state
    val rippleColor = if (dark) 0x40FFFFFF else 0x1F1A73E8
    val divider = if (dark) 0x14FFFFFF else 0x14000000

    private fun pick(darkArgb: Long, lightArgb: Long): Int =
        (if (dark) darkArgb else lightArgb).toInt()
}
