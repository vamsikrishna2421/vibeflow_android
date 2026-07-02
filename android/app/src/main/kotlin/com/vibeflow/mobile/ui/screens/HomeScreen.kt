package com.vibeflow.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vibeflow.mobile.ui.MainViewModel
import com.vibeflow.mobile.ui.RecordState
import com.vibeflow.mobile.ui.SetupStatus
import com.vibeflow.mobile.ui.SystemActions
import com.vibeflow.mobile.core.model.HistoryEntry
import com.vibeflow.mobile.ui.components.VoiceWave
import com.vibeflow.mobile.ui.components.brandBrush
import com.vibeflow.mobile.ui.components.micBrush
import com.vibeflow.mobile.ui.theme.AccentRed
import com.vibeflow.mobile.ui.theme.Amber
import com.vibeflow.mobile.ui.theme.Brand
import com.vibeflow.mobile.ui.theme.CtaGreen
import com.vibeflow.mobile.ui.theme.Electric
import com.vibeflow.mobile.ui.theme.Ink
import com.vibeflow.mobile.ui.theme.InkSoft

@Composable
fun HomeScreen(vm: MainViewModel, setup: SetupStatus, actions: SystemActions, modifier: Modifier = Modifier) {
    val recordState by vm.recordState.collectAsState()
    val preview by vm.preview.collectAsState()
    val amplitude by vm.amplitude.collectAsState()
    val settings by vm.settings.collectAsState()
    val authState by vm.authState.collectAsState()
    val online = settings.smartFormatTier == "managed" && authState.signedIn
    val quota by vm.quota.collectAsState()
    val history by vm.history.collectAsState()
    LaunchedEffect(authState.signedIn) { if (authState.signedIn) vm.refreshQuota() }
    val fullSetup = setup.keyboardEnabled && setup.micGranted
    val stats = remember(history, quota) { computeStats(history, quota) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(22.dp))
        // Editorial header — big serif wordmark
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text("Vibe", style = MaterialTheme.typography.displaySmall, color = Ink)
                    Text("Flow", style = MaterialTheme.typography.displaySmall.copy(brush = brandBrush()))
                }
                Spacer(Modifier.height(2.dp))
                Text("Speak. We'll write it, beautifully.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft)
            }
            StatusPill(online)
        }
        Spacer(Modifier.height(20.dp))

        // Delight stats strip (swipeable, serif numbers) — the engagement layer
        StatsStrip(stats)
        Spacer(Modifier.height(22.dp))

        Column(Modifier.padding(horizontal = 22.dp)) {

        // Hero recorder
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = recordState::class,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "rec",
                ) { _ ->
                    when (val s = recordState) {
                        is RecordState.Done -> DoneBlock(s.text, onCopy = { vm.copyEntry(toEntry(s.text)) }, onAgain = { vm.resetRecord() })
                        is RecordState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(18.dp))
                            MicFab(listening = false, amplitude = 0f) { onRecordTap(vm, setup, actions) }
                        }
                        RecordState.Listening -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(preview.ifBlank { "Listening…" },
                                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            VoiceWave(amplitude = amplitude, active = true,
                                modifier = Modifier.fillMaxWidth().height(44.dp))
                            Spacer(Modifier.height(18.dp))
                            MicFab(listening = true, amplitude = amplitude) { vm.toggleRecord() }
                            Spacer(Modifier.height(10.dp))
                            Text("Tap to stop", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RecordState.Idle -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tap to talk", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("I'll write it down and copy it for you.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            MicFab(listening = false, amplitude = 0f) { onRecordTap(vm, setup, actions) }
                            if (!setup.micGranted) {
                                Spacer(Modifier.height(10.dp))
                                Text("Tap to grant the mic & talk",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PrivacyControls(vm, settings, quota)
        if (!fullSetup) {
            Spacer(Modifier.height(18.dp))
            SetupCard(setup, actions)
        }

        Spacer(Modifier.height(26.dp))
        Text("Type anywhere by voice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        TipRow("1", "Open any app and tap a text box.")
        TipRow("2", "Switch to the VibeFlow keyboard (🌐 globe key).")
        TipRow("3", "Tap the mic, speak — your words land at the cursor.")
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Say it, don't tap it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("“comma”, “period”, “new line” to punctuate · “scratch that” to undo · “delete last word”.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(28.dp))
        }   // end inner padded content column
    }
}

private fun toEntry(text: String) = com.vibeflow.mobile.core.model.HistoryEntry(0, 0, text)

private fun onRecordTap(vm: MainViewModel, setup: SetupStatus, actions: SystemActions) {
    if (!setup.micGranted) actions.requestMic() else vm.toggleRecord()
}

/** Top-right status chip — Online when VibeFlow's managed AI is active, Offline (on-device) otherwise. */
@Composable
private fun StatusPill(online: Boolean) {
    val bg = if (online) Color(0xFF12263F) else Color(0xFF1A2338)
    val dot = if (online) Color(0xFF43E6C1) else MaterialTheme.colorScheme.onSurfaceVariant
    val txt = if (online) Color(0xFF6FEBCF) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = CircleShape, color = bg) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Text(if (online) "Online" else "Offline", style = MaterialTheme.typography.labelLarge, color = txt)
        }
    }
}

/** Two privacy toggles (AI polish · online transcription) + a plain-language status of
 *  exactly what leaves the phone for each combination. */
@Composable
private fun PrivacyControls(
    vm: MainViewModel,
    settings: com.vibeflow.mobile.data.Settings,
    quota: com.vibeflow.mobile.auth.SupabaseAuth.Quota?,
) {
    val aiOn = !settings.privateMode
    val onlineOn = settings.onlineRecognition
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
            ToggleRow(
                Icons.Filled.AutoAwesome,
                "Polish with AI",
                if (aiOn) {
                    quota?.let { q -> if (q.isPro) "Pro · unlimited polishes" else "✨ ${q.remaining} free polishes left" }
                        ?: "Cleans up & restructures your dictation"
                } else "On-device cleanup only",
                aiOn,
            ) { on ->
                if (on) { vm.setPrivateMode(false); vm.setSmartFormatTier("managed") }
                else { vm.setPrivateMode(true); vm.setSmartFormatTier("private") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ToggleRow(
                Icons.Filled.Mic,
                "Online transcription",
                if (onlineOn) "Google · handles noise & low voice" else "On-device · fully private",
                onlineOn,
            ) { on -> vm.setOnlineRecognition(on) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text(
                privacyStatus(aiOn, onlineOn),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Honest one-liner of what leaves the device for each toggle combination. */
private fun privacyStatus(aiOn: Boolean, onlineOn: Boolean): String = when {
    !aiOn && !onlineOn -> "True offline — nothing leaves your phone."
    !aiOn && onlineOn -> "Google transcription · no VibeFlow AI polish."
    aiOn && !onlineOn -> "On-device recognizer + AI polish · best in a quiet room with clear voice."
    else -> "Google transcription + AI polish · handles noisy rooms & low voice."
}

@Composable
private fun MicFab(listening: Boolean, amplitude: Float, onClick: () -> Unit) {
    val fill: Brush = if (listening) SolidColor(AccentRed) else micBrush()
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "p",
    )
    val glow by transition.animateFloat(
        0.92f, 1.12f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "g",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
        // AI glow halo behind the mic (purple → blue → transparent), gently breathing.
        Box(
            Modifier.size((156 * glow).dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(Color(0x2E7C5CFF), Color(0x1A56B6FF), Color.Transparent)),
            ),
        )
        if (listening) {
            val rscale = 1f + 0.5f * pulse
            Box(
                Modifier.size((94 * rscale).dp).clip(CircleShape)
                    .background(AccentRed.copy(alpha = 0.18f * (1f - pulse)))
            )
        }
        Box(
            Modifier.size(94.dp).clip(CircleShape).background(fill).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (listening) "Stop" else "Record",
                tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun DoneBlock(text: String, onCopy: () -> Unit, onAgain: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text("Copied to clipboard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(12.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCopy, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Copy")
            }
            Button(onClick = onAgain, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Again")
            }
        }
    }
}

@Composable
private fun SetupCard(setup: SetupStatus, actions: SystemActions) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Recommended setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Tap each to grant — choose “Allow / While using the app”.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            SetupRow(setup.micGranted, "Grant microphone", "Needed to hear you. Stays on-device.", "Grant", actions.requestMic)
            SetupRow(setup.keyboardEnabled, "Enable VibeFlow keyboard", "Turn it on in keyboard settings.", "Enable", actions.openImeSettings)
            SetupRow(false, "Pick VibeFlow when typing", "Choose it now, or use the 🌐 globe key.", "Choose", actions.showImePicker, showCheck = false)
            SetupRow(setup.notificationsGranted, "Allow notifications", "Recommended — status & quick actions.", "Allow", actions.requestNotifications)
            Spacer(Modifier.height(2.dp))
            Text("Missed these? They're always in Settings ▸ Setup & permissions.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupRow(done: Boolean, title: String, subtitle: String, cta: String, onClick: () -> Unit, showCheck: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showCheck) {
            Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) { Spacer(Modifier.width(8.dp)); FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(cta) } }
    }
}

@Composable
private fun TipRow(num: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
            Text(num, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Delight stats strip ─────────────────────────────────────────────────────────────
private data class HomeStats(
    val streakDays: Int,
    val totalWords: Int,
    val weekWords: Int,
    val freeLeft: Int?,    // null when Pro / unknown
    val isPro: Boolean,
)

private fun computeStats(history: List<HistoryEntry>, quota: com.vibeflow.mobile.auth.SupabaseAuth.Quota?): HomeStats {
    val tz = java.util.TimeZone.getDefault()
    val nowMs = System.currentTimeMillis()
    fun dayIdx(tsSec: Long): Long { val m = tsSec * 1000; return (m + tz.getOffset(m)) / 86_400_000L }
    fun words(s: String) = if (s.isBlank()) 0 else s.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    val today = dayIdx(nowMs / 1000)
    val days = HashSet<Long>(history.size)
    var total = 0
    var week = 0
    val weekAgo = nowMs / 1000 - 7L * 24 * 3600
    for (e in history) {
        val w = words(e.polished.ifBlank { e.text })
        total += w
        if (e.ts >= weekAgo) week += w
        days.add(dayIdx(e.ts))
    }
    var streak = 0
    var d = today
    if (!days.contains(d)) d -= 1   // today not active yet → count the run up to yesterday
    while (days.contains(d)) { streak++; d -= 1 }
    return HomeStats(streak, total, week, quota?.let { if (it.isPro) null else it.remaining }, quota?.isPro ?: false)
}

private data class StatSpec(val big: String, val label: String, val sub: String, val icon: ImageVector)

private fun streakSub(days: Int) = when {
    days <= 0 -> "start today"
    days < 3 -> "nice start"
    days < 7 -> "on a roll"
    else -> "🔥 on fire"
}

@Composable
private fun StatsStrip(stats: HomeStats) {
    val cards = buildList {
        add(StatSpec("${stats.streakDays}", "day streak", streakSub(stats.streakDays), Icons.Filled.Bolt))
        add(StatSpec("%,d".format(stats.totalWords), "words dictated", "lifetime", Icons.Filled.GraphicEq))
        add(StatSpec("%,d".format(stats.weekWords), "this week", "keep it going", Icons.Filled.TrendingUp))
        if (stats.isPro) add(StatSpec("∞", "Pro polishes", "unlimited", Icons.Filled.AutoAwesome))
        else stats.freeLeft?.let { add(StatSpec("$it", "free polishes", "left this week", Icons.Filled.AutoAwesome)) }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards.size) { i -> StatCard(cards[i]) }
    }
}

@Composable
private fun StatCard(s: StatSpec) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(140.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            // gradient icon chip
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(brandBrush()),
                contentAlignment = Alignment.Center,
            ) { Icon(s.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.height(12.dp))
            Text(s.big, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(s.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(s.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
