package com.vibeflow.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibeflow.mobile.ui.components.BrandMark
import com.vibeflow.mobile.ui.components.brandBrush
import kotlinx.coroutines.delay

/**
 * First-impression splash (~3s): the brand mark fades+scales in, then the "VibeFlow"
 * wordmark rises in, then the gradient "Polish with AI" tagline. Adaptive light/dark.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }   // 0 → mark → wordmark → tagline
    LaunchedEffect(Unit) {
        delay(150); stage = 1
        delay(650); stage = 2
        delay(550); stage = 3
        delay(1650)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(stage >= 1, enter = fadeIn() + scaleIn(initialScale = 0.82f)) {
                BrandMark(size = 104.dp)
            }
            Spacer(Modifier.height(22.dp))
            AnimatedVisibility(stage >= 2, enter = fadeIn() + slideInVertically { it / 3 }) {
                Text(
                    "VibeFlow",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(stage >= 3, enter = fadeIn()) {
                Text(
                    "Polish with AI",
                    style = MaterialTheme.typography.titleMedium.copy(brush = brandBrush()),
                )
            }
        }
    }
}
