package com.vibeflow.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.vibeflow.mobile.R

// ── VibeFlow "Cosmic AI" — deep navy, purple→blue gradient brand, white text ─────────
// A forced dark theme (Linear / Raycast / Arc vibe): timeless, premium, room for glows.
val Ink = Color(0xFFFFFFFF)          // primary text & numbers (white)
val InkSoft = Color(0xFFB7C2D3)      // secondary text
val Muted = Color(0xFF7D889A)        // muted labels
val Electric = Color(0xFF8B5CF6)     // purple — primary accent
val ElectricDeep = Color(0xFF6D7CFF) // indigo
val ElectricSoft = Color(0xFF56B6FF) // sky blue
val Cyan = Color(0xFF37D8FF)
val Navy = Color(0xFF08101F)         // primary background
val Slate = Color(0xFF111827)        // secondary background
val Midnight = Color(0xFF151C2F)     // card
val Elevated = Color(0xFF1A2338)     // elevated card
val Amber = Color(0xFFFFCC66)        // warning / delight

// Back-compat aliases (used across older screens) — repointed to the cosmic palette.
val Brand = Electric
val BrandDark = ElectricDeep
val BrandLight = ElectricSoft
val CtaGreen = Color(0xFF38E8B8)     // success
val CtaGreenLight = Color(0xFF43E6C1)
val AccentRed = Color(0xFFFF6B81)    // danger
val AccentAmber = Amber
val Tangerine = Electric             // mic now uses the brand gradient

private val CosmicColors = darkColorScheme(
    primary = Electric,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF241B4D),
    onPrimaryContainer = Color(0xFFE3DEFF),
    secondary = ElectricSoft,
    onSecondary = Color(0xFF04223A),
    secondaryContainer = Color(0xFF12263F),
    onSecondaryContainer = Color(0xFFCDE7FF),
    tertiary = CtaGreen,
    onTertiary = Color(0xFF003A2C),
    tertiaryContainer = Color(0xFF0E3A30),
    onTertiaryContainer = Color(0xFFB9F6E4),
    background = Navy,
    onBackground = Ink,
    surface = Midnight,
    onSurface = Ink,
    surfaceVariant = Elevated,
    onSurfaceVariant = Color(0xFF9AA6B8),   // muted labels
    outline = Color(0xFF26324A),
    outlineVariant = Color(0xFF1E2840),
    error = AccentRed,
    onError = Color(0xFF3A0410),
    scrim = Color(0xFF05080F),
)

// Editorial serif for display/headlines; the system sans carries body & UI.
val Fraunces = FontFamily(
    Font(R.font.fraunces_light, FontWeight.Light),
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
        displayMedium = displayMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
        displaySmall = displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun VibeFlowTheme(
    darkTheme: Boolean = true,            // VibeFlow is always the Cosmic dark theme
    content: @Composable () -> Unit,
) {
    val colors = CosmicColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = false      // light icons on the dark bar
            ctrl.isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
