package com.vibeflow.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Cosmic AI reusable components ────────────────────────────────────────────────────
// Shared building blocks for the dark "Cosmic" design system: gradient accents, glass
// cards, filter chips. Keeps every screen consistent with the theme tokens.

/** A gradient-filled rounded icon chip — the signature cosmic accent. */
@Composable
fun GradientIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    boxSize: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    radius: Dp = 11.dp,
) {
    Box(
        modifier.size(boxSize).clip(RoundedCornerShape(radius)).background(brandBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** A pill filter chip — purple→blue gradient when selected, subtle glass when not. */
@Composable
fun CosmicChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val fill = if (selected) Modifier.background(brandBrush())
    else Modifier.background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    Box(
        modifier.clip(shape).then(fill).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The small "✨ AI Polished" badge shown on polished items. */
@Composable
fun AiPolishedBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(Color(0xFF241B4D)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✨", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(4.dp))
        Text(
            "AI Polished",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB9A9FF),
        )
    }
}

/** A standard glass card border (subtle white-6%). */
@Composable
fun cosmicBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** A floating gradient mic button (FAB) — purple→blue, white mic. */
@Composable
fun FloatingMicButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(brandBrush()).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "New dictation", tint = Color.White, modifier = Modifier.size(size * 0.45f))
    }
}
