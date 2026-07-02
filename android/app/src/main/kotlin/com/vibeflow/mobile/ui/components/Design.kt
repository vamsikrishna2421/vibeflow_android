package com.vibeflow.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibeflow.mobile.R

/**
 * VibeFlow design-system primitives — one rhythm + premium components shared by every
 * screen. Inspired by Notion's restraint (whitespace, hierarchy, one accent) and
 * WhatsApp's grouped cards (leading icon tiles + chevrons). Adaptive light/dark via the
 * Material color scheme. New screens compose these instead of raw Material widgets.
 */
object Dimens {
    val gutter = 20.dp      // screen side padding
    val gap = 12.dp         // between cards
    val cardRadius = 22.dp
    val tile = 34.dp        // leading-icon tile
}

/** The brand gradient (purple → indigo → sky blue) — logo, active states, AI effects. */
fun brandBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFA855F7), Color(0xFF7C5CFF), Color(0xFF56B6FF)),
)

/** The mic / "tap to talk" gradient — purple → blue (the AI hero). */
fun micBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFF8B5CF6), Color(0xFF5FA8FF)),
)

/** The VibeFlow waveform mark. */
@Composable
fun BrandMark(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_vibeflow_mark), contentDescription = null, modifier = modifier.size(size))
}

/** A small section label above a [GroupCard]. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Dimens.gutter + 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** A grouped rounded card holding a column of rows (WhatsApp-style). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Dimens.cardRadius),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
    ) { Column(content = content) }
}

/** A premium list row: leading icon tile, title/subtitle, optional trailing text + chevron. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Dimens.tile).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full-width primary CTA painted with the brand gradient. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) brandBrush() else SolidColor(MaterialTheme.colorScheme.surfaceVariant))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
