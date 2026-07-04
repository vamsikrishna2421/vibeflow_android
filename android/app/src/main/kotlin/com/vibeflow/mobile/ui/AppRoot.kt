package com.vibeflow.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.vibeflow.mobile.ui.screens.HistoryScreen
import com.vibeflow.mobile.ui.screens.HomeScreen
import com.vibeflow.mobile.ui.screens.ListEditorScreen
import com.vibeflow.mobile.ui.screens.SettingsScreen

/** System-level actions the UI can ask the host Activity to perform. */
data class SystemActions(
    val openImeSettings: () -> Unit,
    val showImePicker: () -> Unit,
    val requestMic: () -> Unit,
    val openAppDetails: () -> Unit,
    val toggleFloatingMic: (Boolean) -> Unit = {},
    val requestNotifications: () -> Unit = {},
    val requestOverlay: () -> Unit = {},
    val openAccessibilitySettings: () -> Unit = {},
)

/** Live setup state computed by the Activity (recomputed on resume). */
data class SetupStatus(
    val keyboardEnabled: Boolean = false,
    val micGranted: Boolean = false,
    val notificationsGranted: Boolean = true,   // true on < API 33 (no runtime prompt)
    val overlayGranted: Boolean = false,
)

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Talk", Icons.Filled.Mic),
    History("History", Icons.Filled.History),
    Settings("Settings", Icons.Filled.Settings),
}

enum class EditorType { Vocabulary, Snippets }

@Composable
fun AppRoot(vm: MainViewModel, setup: SetupStatus, actions: SystemActions) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var editor by remember { mutableStateOf<EditorType?>(null) }
    var legal by remember { mutableStateOf<com.vibeflow.mobile.ui.screens.LegalDoc?>(null) }
    var setupDismissed by rememberSaveable { mutableStateOf(false) }   // per app launch
    var signInDismissed by rememberSaveable { mutableStateOf(false) }  // per app launch
    var pendingEditName by remember { mutableStateOf(false) }          // one-shot: open name editor on Settings

    val settings by vm.settings.collectAsState()
    val authState by vm.authState.collectAsState()
    val ready by vm.ready.collectAsState()   // real data loaded? (gates on-open dialogs to avoid flashes)

    if (editor != null) {
        ListEditorScreen(
            type = editor!!,
            settings = settings,
            onSave = { type, terms, map ->
                when (type) {
                    EditorType.Vocabulary -> vm.setVocabulary(terms)
                    EditorType.Snippets -> vm.setSnippets(map)
                }
            },
            onBack = { editor = null },
        )
        return
    }

    legal?.let { doc ->
        com.vibeflow.mobile.ui.screens.LegalScreen(doc, onBack = { legal = null })
        return
    }

    Scaffold(
        bottomBar = { BottomNav(current = tab) { tab = it } },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (tab) {
            Tab.Home -> HomeScreen(vm, setup, actions, mod)
            Tab.History -> HistoryScreen(vm, mod, onNewDictation = { tab = Tab.Home })
            Tab.Settings -> SettingsScreen(vm, settings, setup, actions, mod, onOpenEditor = { editor = it }, onOpenLegal = { legal = it }, openNameEditor = pendingEditName, onNameEditorOpened = { pendingEditName = false })
        }
    }

    // On app open, surface a one-tap sign-in if the user hasn't set up AI polish at all
    // (no managed sign-in, no BYOK key, not deliberately Private). Skippable; re-shows next
    // launch. Otherwise fall through to the recommended-setup reminder.
    val showSignIn = ready &&
        !authState.signedIn &&
        settings.llmApiKey.isBlank() &&
        settings.smartFormatTier != "private" &&
        !signInDismissed
    if (showSignIn) {
        SignInPromptDialog(vm) { signInDismissed = true }
    } else if (ready && !setupDismissed) {
        val items = incompleteSetup(setup, settings, actions) { tab = Tab.Settings; pendingEditName = true; setupDismissed = true }
        if (items.isNotEmpty()) SetupReminderDialog(items) { setupDismissed = true }
    }
}

/**
 * Floating glass bottom navigation — a rounded, elevated bar that hovers above the
 * bottom edge. The selected tab shows a gradient icon chip + label; the rest are quiet.
 */
@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Surface(
            color = androidx.compose.ui.graphics.Color(0xFF101827),
            shape = RoundedCornerShape(30.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { t ->
                    val selected = t == current
                    // Each tab is an equal third (fixed), content centered.
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).clickable { onSelect(t) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            com.vibeflow.mobile.ui.components.GradientIcon(t.icon, boxSize = 32.dp, iconSize = 17.dp, radius = 10.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(t.label, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(t.icon, contentDescription = t.label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/** On-open prompt to enable managed Smart Formatting in one tap (Google sign-in), or skip. */
@Composable
private fun SignInPromptDialog(vm: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var signingIn by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Turn on AI Smart Formatting") },
        text = {
            Text(
                "Sign in to get 50 free AI polishes — no API key needed. VibeFlow automatically cleans up and " +
                    "restructures your dictation. You can switch to your own key or fully-private mode anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            FilledTonalButton(
                enabled = !signingIn,
                onClick = {
                    signingIn = true
                    vm.signInWithGoogle(context) { err ->
                        signingIn = false
                        if (err == null) {
                            vm.setSmartFormatTier("managed"); vm.setPrivateMode(false)
                            Toast.makeText(context, "Signed in ✓", Toast.LENGTH_SHORT).show()
                            onDone()
                        } else {
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) { Text(if (signingIn) "Signing in…" else "Sign in with Google") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Skip now") } },
    )
}

private data class SetupItem(val title: String, val subtitle: String, val cta: String, val action: () -> Unit)

/** Recommended steps the user still needs — extend this list as new features are added. */
private fun incompleteSetup(
    setup: SetupStatus,
    settings: com.vibeflow.mobile.data.Settings,
    actions: SystemActions,
    goSettings: () -> Unit,
): List<SetupItem> = buildList {
    if (!setup.micGranted) add(SetupItem("Microphone", "Required for voice typing — stays on-device", "Grant", actions.requestMic))
    if (!setup.keyboardEnabled) add(SetupItem("Enable VibeFlow keyboard", "Turn it on in keyboard settings", "Enable", actions.openImeSettings))
    if (!setup.notificationsGranted) add(SetupItem("Allow notifications", "Recording status & quick actions", "Allow", actions.requestNotifications))
    if (settings.userName.isBlank()) add(SetupItem("Add your name", "Personalizes your email sign-offs", "Add", goSettings))
}

@Composable
private fun SetupReminderDialog(items: List<SetupItem>, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Finish setting up VibeFlow") },
        text = {
            Column {
                Text(
                    "A few recommended steps remain for the full experience. Skip if you like — they're always in Settings ▸ Setup & permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = item.action) { Text(item.cta) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSkip) { Text("Skip for now") } },
    )
}
