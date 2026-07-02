package com.vibeflow.mobile.ui.screens

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.vibeflow.mobile.ui.MainViewModel
import com.vibeflow.mobile.ui.SetupStatus
import com.vibeflow.mobile.ui.SystemActions
import com.vibeflow.mobile.ui.components.BrandMark
import com.vibeflow.mobile.ui.components.Dimens
import com.vibeflow.mobile.ui.components.PrimaryButton
import com.vibeflow.mobile.ui.components.brandBrush

/**
 * First-run flow (after the splash): a value carousel → Google sign-in (with legal) →
 * a live setup checklist. Premium, adaptive light/dark, built on the design system.
 * Calls [onDone] when finished (which persists `onboardingDone`).
 */
@Composable
fun OnboardingFlow(vm: MainViewModel, actions: SystemActions, setup: SetupStatus, onDone: () -> Unit) {
    // Legal viewer overlays the whole flow without losing the current step.
    var legal by remember { mutableStateOf<LegalDoc?>(null) }
    legal?.let { doc ->
        LegalScreen(doc, onBack = { legal = null })
        return
    }
    var step by rememberSaveable { mutableIntStateOf(0) }   // 0 carousel · 1 sign-in · 2 setup
    when (step) {
        0 -> OnboardingCarousel(onNext = { step = 1 })
        1 -> OnboardingSignIn(vm, onNext = { step = 2 }, onOpenLegal = { legal = it })
        else -> OnboardingSetup(vm, setup, actions, onDone = onDone)
    }
}

private data class ValuePage(val icon: ImageVector, val title: String, val body: String)

@Composable
private fun OnboardingCarousel(onNext: () -> Unit) {
    val pages = listOf(
        ValuePage(Icons.Filled.Mic, "Dictate anywhere", "Talk instead of type — in any app, from your keyboard or a floating mic bubble."),
        ValuePage(Icons.Filled.AutoAwesome, "AI polishes your words", "Rambling speech becomes clean, structured text: emails, messages, notes — automatically."),
        ValuePage(Icons.Filled.Lock, "Private by default", "Speech is 100% on-device. Your voice never leaves your phone unless you choose AI polish."),
    )
    var i by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { idx ->
                Box(
                    Modifier.padding(horizontal = 4.dp)
                        .size(width = if (idx == i) 24.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (idx == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Crossfade(targetState = i, label = "page") { idx ->
            val p = pages[idx]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(124.dp).clip(CircleShape).background(brandBrush()),
                    contentAlignment = Alignment.Center,
                ) { Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(58.dp)) }
                Spacer(Modifier.height(40.dp))
                Text(
                    p.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    p.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (i == pages.lastIndex) "Get started" else "Next") {
            if (i == pages.lastIndex) onNext() else i++
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OnboardingSignIn(vm: MainViewModel, onNext: () -> Unit, onOpenLegal: (LegalDoc) -> Unit) {
    val context = LocalContext.current
    var signingIn by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(size = 76.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            "Your AI keyboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Sign in to unlock 50 free AI polishes — no API key needed.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (signingIn) "Signing in…" else "Continue with Google", enabled = !signingIn) {
            signingIn = true
            vm.signInWithGoogle(context) { err ->
                signingIn = false
                if (err == null) {
                    vm.setSmartFormatTier("managed"); vm.setPrivateMode(false)
                    onNext()
                } else {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNext) { Text("Skip for now") }
        Spacer(Modifier.height(6.dp))
        val linkColor = MaterialTheme.colorScheme.primary
        val legalLine = buildAnnotatedString {
            append("By continuing you agree to our ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "terms",
                    styles = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)),
                    linkInteractionListener = { onOpenLegal(LegalDoc.Terms) },
                ),
            ) { append("Terms") }
            append(" & ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "privacy",
                    styles = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)),
                    linkInteractionListener = { onOpenLegal(LegalDoc.Privacy) },
                ),
            ) { append("Privacy Policy") }
            append(".")
        }
        Text(
            legalLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OnboardingSetup(vm: MainViewModel, setup: SetupStatus, actions: SystemActions, onDone: () -> Unit) {
    // Kick off the offline noise-handling model download in the BACKGROUND, Wi-Fi only (idempotent —
    // no-op if already present or on cellular). Non-blocking: the user can finish setup + dictate
    // immediately; until it lands, noisy speech uses the system recognizer, then upgrades to Whisper.
    LaunchedEffect(Unit) { vm.autoDownloadWhisper() }
    val whisper by vm.whisperModelState.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.gutter)) {
        Spacer(Modifier.height(48.dp))
        Text(
            "Finish setup",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "A couple of quick steps to start dictating.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(30.dp))
        SetupCheck(setup.micGranted, "Microphone", "For voice typing — stays on-device", "Grant") { actions.requestMic() }
        SetupCheck(setup.keyboardEnabled, "Enable VibeFlow keyboard", "Turn it on in keyboard settings", "Enable") { actions.openImeSettings() }
        SetupCheck(setup.notificationsGranted, "Notifications", "Recording status & quick actions", "Allow") { actions.requestNotifications() }
        WhisperSetupRow(whisper)
        Spacer(Modifier.weight(1f))
        PrimaryButton("Start using VibeFlow") { onDone() }
        Spacer(Modifier.height(16.dp))
    }
}

/** Informational (no button): live status of the background noise-handling model download. */
@Composable
private fun WhisperSetupRow(state: com.vibeflow.mobile.asr.WhisperModelManager.State) {
    val ready = state is com.vibeflow.mobile.asr.WhisperModelManager.State.Ready
    val downloading = state as? com.vibeflow.mobile.asr.WhisperModelManager.State.Downloading
    val waiting = state is com.vibeflow.mobile.asr.WhisperModelManager.State.WaitingForWifi
    val sub = when {
        ready -> "Ready — sharper accuracy in noisy places"
        downloading != null -> "Downloading ${downloading.percent}% · ~180 MB, in the background"
        waiting -> "Will download on Wi-Fi · ~180 MB (you can dictate now)"
        state is com.vibeflow.mobile.asr.WhisperModelManager.State.Failed -> "Will finish later — you can dictate now"
        else -> "Setting up · ~180 MB, in the background"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            ready -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
            downloading != null -> CircularProgressIndicator(
                progress = { (downloading.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp,
            )
            waiting -> Icon(Icons.Filled.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Noise handling (offline)", style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupCheck(done: Boolean, title: String, sub: String, cta: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) FilledTonalButton(onClick = onClick) { Text(cta) }
    }
}
