package com.vibeflow.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A voice-reactive waveform for Compose screens — mirrored bars whose heights
 * undulate with time and are lifted by [amplitude] (0..1). Mirrors the keyboard's
 * WaveformView so the app and IME feel like one product.
 */
@Composable
fun VoiceWave(
    amplitude: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    high: Color = Color(0xFFA99BF0),
    low: Color = Color(0xFF7A68D9),
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable<Float>(animation = tween(1500, easing = LinearEasing)),
        label = "t",
    )
    val amp by animateFloatAsState(if (active) amplitude else 0f, tween(120), label = "amp")

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val gap = 3.dp.toPx()
        val barW = (w - gap * (barCount - 1)) / barCount
        val maxHalf = h / 2f - 1.dp.toPx()
        val brush = Brush.verticalGradient(listOf(high, low))
        for (i in 0 until barCount) {
            val env = 0.45f + 0.55f * sin(PI * i / (barCount - 1)).toFloat()
            val undulate = 0.5f + 0.5f * sin(t * 4.5f + i * 0.7f)
            val idle = 0.07f + 0.03f * sin(t * 1.3f + i * 0.4f)
            val frac = (idle + amp * env * (0.35f + 0.65f * undulate)).coerceIn(0.04f, 1f)
            val half = maxHalf * frac
            val x = i * (barW + gap) + barW / 2f
            drawLine(
                brush = brush,
                start = Offset(x, cy - half),
                end = Offset(x, cy + half),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}
