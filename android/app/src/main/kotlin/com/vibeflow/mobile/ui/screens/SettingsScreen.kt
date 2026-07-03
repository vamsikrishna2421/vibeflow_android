package com.vibeflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import android.widget.Toast
import com.vibeflow.mobile.asr.ModelManager
import com.vibeflow.mobile.data.Settings
import com.vibeflow.mobile.ui.EditorType
import com.vibeflow.mobile.ui.MainViewModel
import com.vibeflow.mobile.ui.SetupStatus
import com.vibeflow.mobile.ui.SystemActions
import com.vibeflow.mobile.ui.components.brandBrush
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    settings: Settings,
    setup: SetupStatus,
    actions: SystemActions,
    modifier: Modifier = Modifier,
    onOpenEditor: (EditorType) -> Unit,
    onOpenLegal: (LegalDoc) -> Unit = {},
    openNameEditor: Boolean = false,
    onNameEditorOpened: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showTeach by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var teachText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    var modelText by remember { mutableStateOf("") }
    var showCorrections by remember { mutableStateOf(false) }
    val corrections by vm.corrections.collectAsState()
    val authState by vm.authState.collectAsState()
    val quota by vm.quota.collectAsState()
    val offlineModel by vm.offlineModelState.collectAsState()
    val whisperModel by vm.whisperModelState.collectAsState()
    LaunchedEffect(authState.signedIn) { if (authState.signedIn) vm.refreshQuota() }
    var signingIn by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var showTitle by remember { mutableStateOf(false) }
    var titleText by remember { mutableStateOf("") }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showAutoInsert by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }
    val accountSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    // Dismiss the account sheet (animated) THEN run the action — avoids a window race where
    // opening a dialog / launching sign-in while the sheet tears down drops it silently.
    fun closeSheetThen(action: () -> Unit) {
        scope.launch { accountSheetState.hide() }.invokeOnCompletion {
            if (!accountSheetState.isVisible) {
                showAccountSheet = false
                action()
            }
        }
    }
    // Honor an external request (e.g. from the on-open setup reminder's "Add your name") to
    // jump straight into name entry after landing on this screen.
    LaunchedEffect(openNameEditor) {
        if (openNameEditor) {
            nameText = settings.userName
            showName = true
            onNameEditorOpened()
        }
    }

    if (showPlans) {
        SubscriptionScreen(quota, onBack = { showPlans = false }, onUpgrade = { act, plan -> vm.buyPro(act, plan) }, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar("Settings")

        SectionHeader("Account")
        ProfileHeader(
            name = settings.userName,
            subtitle = when {
                authState.signedIn -> authState.email
                settings.userName.isBlank() -> "Tap to set up your profile"
                else -> settings.userTitle.ifBlank { "Personalize your formatting" }
            },
            onClick = { showAccountSheet = true },
        )

        Group("Your profile", Icons.Filled.Person) {
            Text(
                "Used to personalize formatting — e.g. signing off your emails.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            NavRow("Your name", if (settings.userName.isBlank()) "Not set — add it for email sign-offs" else settings.userName) { nameText = settings.userName; showName = true }
            NavRow("Job title / details", if (settings.userTitle.isBlank()) "Optional — e.g. Product Manager, Acme" else settings.userTitle) { titleText = settings.userTitle; showTitle = true }
            NavRow("Voice language", "${voiceLangLabel(settings.recognitionLanguage)} — match your accent for better recognition") { showLang = true }
        }

        SectionHeader("Mynah Pro")
        ProCard(quota) { showPlans = true }

        SectionHeader("Preferences")
        Group("Output", Icons.AutoMirrored.Filled.Send) {
            Text("Where dictation goes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            val modes = listOf("auto" to "Auto", "type" to "Type", "clipboard" to "Clipboard")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                modes.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = settings.outputMode == value,
                        onClick = { vm.setOutputMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
            SwitchRow("Add a trailing space", "Stops back-to-back dictations running together", settings.trailingSpace) { vm.setTrailingSpace(it) }
        }

        Group("Smart Formatting (AI polish)", Icons.Filled.AutoAwesome) {
            Text(
                "Restructure & polish dictation with AI. Choose how it runs.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            // Tier: Private (on-device) · Your key (BYOK) · Mynah (managed proxy).
            val tiers = listOf("private" to "Private", "byok" to "Your key", "managed" to "Mynah")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                tiers.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = settings.smartFormatTier == value,
                        onClick = { vm.setSmartFormatTier(value); vm.setPrivateMode(value == "private") },
                        shape = SegmentedButtonDefaults.itemShape(i, tiers.size),
                    ) { Text(label) }
                }
            }
            when (settings.smartFormatTier) {
                "private" -> InfoRow(
                    "On-device only",
                    "Nothing leaves your phone — dictation is cleaned & punctuated locally, with no AI restructuring.",
                )
                "managed" -> {
                    if (authState.signedIn) {
                        NavRow("Signed in", authState.email.ifBlank { "Google account" }) {}
                        Text(
                            quota.let { q ->
                                when {
                                    q == null -> "Checking your usage…"
                                    q.isPro -> "✨ Pro · unlimited polishes"
                                    else -> "✨ ${q.remaining} free polishes left" + if (q.weekly) " · resets weekly" else ""
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                        )
                        NavRow("Sign out", "Disconnect this Google account") { vm.signOutManaged() }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(16.dp)) {
                            Button(
                                enabled = !signingIn,
                                onClick = {
                                    signingIn = true
                                    vm.signInWithGoogle(context) { err ->
                                        signingIn = false
                                        Toast.makeText(context, err ?: "Signed in ✓", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) { Text(if (signingIn) "Signing in…" else "Sign in with Google") }
                        }
                        Text(
                            "50 free polishes, then upgrade to Pro (coming soon). Your text is proxied through Mynah — no key needed.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                }
                else -> {   // "byok"
                    NavRow(
                        "API key (BYOK)",
                        if (settings.llmApiKey.isBlank()) "Not set — add your OpenAI key to enable" else "Key set ✓ · tap to change",
                    ) { showKey = true }
                    NavRow("Model", settings.llmModel) { modelText = settings.llmModel; showModel = true }
                }
            }
            SwitchRow("Auto-polish after dictation", "Automatically run AI polish on every dictation", settings.autoPolish) { vm.setAutoPolish(it) }
            SwitchRow("Match style to app", "Email style in mail apps, message style in chats, etc.", settings.matchStyleToApp) { vm.setMatchStyleToApp(it) }
            Text("Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                vm.smartFormatStyles.forEach { style ->
                    FilterChip(
                        selected = settings.smartFormatStyle == style,
                        onClick = { vm.setSmartFormatStyle(style) },
                        label = { Text(style) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        Group("Personalize", Icons.Filled.Tune) {
            NavRow("Vocabulary", "Teach Mynah your names & jargon (${settings.vocabulary.size})") { onOpenEditor(EditorType.Vocabulary) }
            NavRow("Snippets", "Say a phrase, insert longer text (${settings.snippets.size})") { onOpenEditor(EditorType.Snippets) }
            NavRow("Learned corrections", "Fixes Mynah learned from your edits (${corrections.size})") { showCorrections = true }
        }

        Group("Typing dictionary", Icons.Filled.Spellcheck) {
            NavRow("Teach from your text", "Paste your writing — Mynah learns your words") { teachText = ""; showTeach = true }
            NavRow("Export my words", "Copy your learned vocabulary to migrate or back up") {
                clipboard.setText(AnnotatedString(vm.exportVocabulary()))
                Toast.makeText(context, "Your words are on the clipboard", Toast.LENGTH_SHORT).show()
            }
            NavRow("Import words", "Paste a vocabulary you exported before") { importText = ""; showImport = true }
        }

        Group("Speech recognition", Icons.Filled.Mic) {
            Text(
                "How your voice is transcribed. Online (Google) is the most accurate, even in noise & low voice; on-device keeps audio on your phone. Also on the Home screen.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            val recModes = listOf(true to "Online (Google)", false to "On-device")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                recModes.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = settings.onlineRecognition == value,
                        onClick = { vm.setOnlineRecognition(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, recModes.size),
                    ) { Text(label) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 16.dp))
            val ws = whisperModel
            SwitchRow(
                "Noisy-room model (Whisper)",
                when (ws) {
                    is com.vibeflow.mobile.asr.WhisperModelManager.State.Downloading -> "Downloading the offline model… ${ws.percent}%"
                    com.vibeflow.mobile.asr.WhisperModelManager.State.Ready -> "On · offline Whisper auto-kicks-in when it's noisy"
                    com.vibeflow.mobile.asr.WhisperModelManager.State.WaitingForWifi -> "Waiting for Wi-Fi to download · ~181 MB"
                    is com.vibeflow.mobile.asr.WhisperModelManager.State.Failed -> "Download failed — toggle again to retry"
                    else -> "Auto-use offline Whisper in noisy rooms · ~181 MB download, transcribes after you stop"
                },
                checked = settings.noiseModel == "whisper",
            ) { on ->
                if (on) {
                    vm.setNoiseModel("whisper")
                    if (ws != com.vibeflow.mobile.asr.WhisperModelManager.State.Ready) vm.downloadWhisperModel()
                } else vm.setNoiseModel("off")
            }
            if (ws == com.vibeflow.mobile.asr.WhisperModelManager.State.Ready) {
                NavRow("Remove noise model", "Free ~181 MB · turns this off") { vm.removeWhisperModel() }
            }
        }

        Group("Behavior", Icons.Filled.RecordVoiceOver) {
            SwitchRow("Hands-free (auto-stop)", "Tap once, speak, and Mynah stops on its own when you pause", settings.handsFree) { vm.setHandsFree(it) }
            SwitchRow("Hold-to-talk", "Hold the mic to talk instead of tap-to-toggle", settings.pushToTalk) { vm.setPushToTalk(it) }
            SwitchRow("Haptic feedback", "Buzz when recording starts/stops", settings.haptics) { vm.setHaptics(it) }
            SwitchRow("Keep history", "Save dictations locally so you can copy them later", settings.historyEnabled) { vm.setHistoryEnabled(it) }
        }

        Group("Offline mode", Icons.Filled.CloudDownload) {
            when (val ms = offlineModel) {
                is ModelManager.State.Downloading -> {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)) {
                        Text("Downloading offline voice model… ${ms.percent}%", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { ms.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
                ModelManager.State.Ready ->
                    NavRow("Offline voice model installed ✓", "Dictate with no internet. Tap to remove & free ~40 MB.") { vm.removeOfflineModel() }
                is ModelManager.State.Failed ->
                    NavRow("Download failed — tap to retry", ms.message) { vm.downloadOfflineModel() }
                ModelManager.State.Absent ->
                    NavRow("Download offline voice model", "≈40 MB · dictate fully offline, or on phones without a built-in recognizer") { vm.downloadOfflineModel() }
            }
        }

        Group("Floating mic", Icons.Filled.Adjust) {
            SwitchRow(
                "Floating mic button",
                "A draggable bubble to dictate anywhere — keep your own keyboard, then tap the paste chip",
                settings.floatingMic,
            ) { on -> vm.setFloatingMic(on); actions.toggleFloatingMic(on) }
            val accessibilityOn = isAccessibilityServiceEnabled(context)
            SwitchRow(
                "Auto-insert at cursor",
                when {
                    settings.autoInsert && accessibilityOn -> "On — dictation types straight into the field, no copy-paste"
                    settings.autoInsert && !accessibilityOn -> "⚠ Turn on “Mynah Auto-insert” in Accessibility settings"
                    else -> "Type dictation into the field instead of copy-paste (uses an Accessibility service)"
                },
                settings.autoInsert,
            ) { want -> if (want) showAutoInsert = true else vm.setAutoInsert(false) }
            if (settings.autoInsert && !accessibilityOn) {
                NavRow("Open Accessibility settings", "Enable “Mynah Auto-insert” to finish") { actions.openAccessibilitySettings() }
            }
        }

        Group("Keyboard", Icons.Filled.Keyboard) {
            Text("Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                themes.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = settings.keyboardTheme == value,
                        onClick = { vm.setKeyboardTheme(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                    ) { Text(label) }
                }
            }
            NavRow("Enable / manage keyboard", "Open system keyboard settings") { actions.openImeSettings() }
            NavRow("Switch keyboard now", "Choose Mynah as the active keyboard") { actions.showImePicker() }
            NavRow("App permissions", "Microphone & notifications") { actions.openAppDetails() }
        }

        Group("Setup & permissions", Icons.Filled.Checklist) {
            Text(
                "Grant these for the full experience — recommended. You can come back here anytime.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
            PermissionRow(setup.micGranted, "Microphone", "Required for voice typing — your voice stays on-device", "Grant") { actions.requestMic() }
            PermissionRow(setup.keyboardEnabled, "Enable Mynah keyboard", "Turn it on in system keyboard settings", "Enable") { actions.openImeSettings() }
            PermissionRow(true, "Set Mynah as your keyboard", "Pick it now, or use the 🌐 globe key while typing", "Choose", alwaysAction = true) { actions.showImePicker() }
            PermissionRow(setup.notificationsGranted, "Notifications", "Recommended — recording status & quick actions", "Allow") { actions.requestNotifications() }
            PermissionRow(setup.overlayGranted, "Floating mic (display over apps)", "Optional — dictate from any keyboard", "Allow") { actions.requestOverlay() }
        }

        SectionHeader("Support")
        Group("Help & support", Icons.Filled.Info) {
            NavRow("Help & Support", "Guides, tips and how-tos") { emailSupport(context, "Mynah — Help") }
            NavRow("Give Feedback", "Tell us what to improve") { emailSupport(context, "Mynah — Feedback") }
            NavRow("Privacy Policy", "How Mynah handles your data") { onOpenLegal(LegalDoc.Privacy) }
            NavRow("Terms of Service", "The terms you agree to") { onOpenLegal(LegalDoc.Terms) }
            InfoRow("About Mynah", "Version 0.1.0 · your voice, beautifully written")
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showTeach) {
        TextDialog(
            title = "Teach from your text",
            help = "Paste a few messages or notes you've written. Mynah learns the words you actually use (any language).",
            placeholder = "Paste your writing here…",
            confirm = "Teach",
            value = teachText,
            onValueChange = { teachText = it },
            onConfirm = {
                if (teachText.isNotBlank()) {
                    vm.teachFromText(teachText)
                    Toast.makeText(context, "Learning your words…", Toast.LENGTH_SHORT).show()
                }
                showTeach = false
            },
            onDismiss = { showTeach = false },
        )
    }
    if (showImport) {
        TextDialog(
            title = "Import words",
            help = "Paste a vocabulary exported from Mynah (from another device or a backup).",
            placeholder = "Paste exported JSON…",
            confirm = "Import",
            value = importText,
            onValueChange = { importText = it },
            onConfirm = {
                if (importText.isNotBlank()) {
                    vm.importVocabulary(importText)
                    Toast.makeText(context, "Imported", Toast.LENGTH_SHORT).show()
                }
                showImport = false
            },
            onDismiss = { showImport = false },
        )
    }
    if (showKey) {
        ApiKeyDialog(
            currentMasked = maskKey(settings.llmApiKey),
            onSave = { k ->
                vm.setLlmApiKey(k)
                Toast.makeText(context, "Key saved", Toast.LENGTH_SHORT).show()
                showKey = false
            },
            onClear = {
                vm.setLlmApiKey("")
                Toast.makeText(context, "Key cleared — Smart Formatting disabled", Toast.LENGTH_SHORT).show()
                showKey = false
            },
            onDismiss = { showKey = false },
        )
    }
    if (showModel) {
        TextDialog(
            title = "Model",
            help = "The chat model to use for Smart Formatting (e.g. gpt-4.1-nano). Use a small, cheap model.",
            placeholder = "gpt-4.1-nano",
            confirm = "Save",
            value = modelText,
            onValueChange = { modelText = it },
            onConfirm = { vm.setLlmModel(modelText.ifBlank { "gpt-4.1-nano" }); showModel = false },
            onDismiss = { showModel = false },
        )
    }
    if (showName) {
        TextDialog(
            title = "Your name",
            help = "Your name, used to sign off emails (e.g. \"Best regards, <name>\").",
            placeholder = "e.g. Vamsi Reddy",
            confirm = "Save",
            value = nameText,
            onValueChange = { nameText = it },
            onConfirm = { vm.setUserName(nameText); showName = false },
            onDismiss = { showName = false },
        )
    }
    if (showTitle) {
        TextDialog(
            title = "Job title / details",
            help = "Optional — added under your name in email sign-offs (e.g. \"Product Manager, Acme\").",
            placeholder = "e.g. Product Manager, Acme",
            confirm = "Save",
            value = titleText,
            onValueChange = { titleText = it },
            onConfirm = { vm.setUserTitle(titleText); showTitle = false },
            onDismiss = { showTitle = false },
        )
    }
    if (showAutoInsert) {
        AlertDialog(
            onDismissRequest = { showAutoInsert = false },
            title = { Text("Enable Auto-insert?") },
            text = {
                Text(
                    "Mynah will use an Accessibility service to type your dictated text straight into the box " +
                        "you're writing in, and to show the floating mic when a keyboard opens.\n\n" +
                        "It only writes into the text field you're focused on — it does not read, collect, or share " +
                        "any other screen content. You can turn it off anytime.\n\n" +
                        "Tap Continue, then on the Accessibility screen open “Downloaded apps” (or “Installed apps”) → " +
                        "“Mynah Auto-insert” → turn it On and accept the prompt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAutoInsert = false; vm.setAutoInsert(true); actions.openAccessibilitySettings() }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showAutoInsert = false }) { Text("Cancel") } },
        )
    }
    if (showLang) {
        val langs = listOf(
            "en-IN" to "English (India)",
            "en-US" to "English (US)",
            "en-GB" to "English (UK)",
            "en-AU" to "English (Australia)",
            "auto" to "Match device language",
        )
        AlertDialog(
            onDismissRequest = { showLang = false },
            title = { Text("Voice language") },
            text = {
                Column {
                    Text(
                        "Pick the accent closest to how you speak — the recognizer uses a model tuned for it, " +
                            "which sharply cuts mis-hears (e.g. Indian English handles your accent far better than US English).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    langs.forEach { (code, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { vm.setRecognitionLanguage(code); showLang = false }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.recognitionLanguage == code,
                                onClick = { vm.setRecognitionLanguage(code); showLang = false },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLang = false }) { Text("Done") } },
        )
    }
    if (showAccountSheet) {
        ModalBottomSheet(onDismissRequest = { showAccountSheet = false }, sheetState = accountSheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp),
            ) {
                // Identity row — gradient avatar + name + email/status.
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(brandBrush()), contentAlignment = Alignment.Center) {
                        if (settings.userName.isBlank()) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                        } else {
                            Text(settings.userName.trim().first().uppercase(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(settings.userName.ifBlank { "Set up your profile" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (authState.signedIn) authState.email.ifBlank { "Google account" } else "Not signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Plan / quota card.
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Plan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                quota.let { q ->
                                    when {
                                        !authState.signedIn -> "Free — Private or your own key"
                                        q == null -> "Checking usage…"
                                        q.isPro -> "Pro · unlimited polishes"
                                        else -> "${q.remaining} free polishes left" + if (q.weekly) " · resets weekly" else ""
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Actions.
                SheetAction(Icons.Filled.Edit, "Edit profile") {
                    closeSheetThen { nameText = settings.userName; showName = true }
                }
                if (authState.signedIn) {
                    SheetAction(Icons.AutoMirrored.Filled.Logout, "Sign out", danger = true) {
                        closeSheetThen { vm.signOutManaged() }
                    }
                } else {
                    SheetAction(Icons.AutoMirrored.Filled.Login, if (signingIn) "Signing in…" else "Sign in with Google") {
                        if (!signingIn) {
                            closeSheetThen {
                                signingIn = true
                                vm.signInWithGoogle(context) { err ->
                                    signingIn = false
                                    if (err == null) { vm.setSmartFormatTier("managed"); vm.setPrivateMode(false) }
                                    Toast.makeText(context, err ?: "Signed in ✓", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCorrections) {
        AlertDialog(
            onDismissRequest = { showCorrections = false },
            title = { Text("Learned corrections") },
            text = {
                if (corrections.isEmpty()) {
                    Text(
                        "None yet. When you fix a mis-heard word and send, Mynah learns it after a couple of times — then applies it automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        corrections.sortedByDescending { it.count }.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${r.from} → ${r.to}", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "seen ${r.count}× · active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { vm.removeCorrection(r.from) }) { Icon(Icons.Filled.Delete, "Remove") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCorrections = false }) { Text("Done") } },
            dismissButton = {
                if (corrections.isNotEmpty()) TextButton(onClick = { vm.clearCorrections() }) { Text("Clear all") }
            },
        )
    }
}

/** A masked preview of a saved key — only the last 4 chars, never the whole thing. */
private fun maskKey(key: String): String = if (key.isBlank()) "" else "•••• " + key.takeLast(4)

/**
 * API-key dialog that never displays the saved key. Shows only a masked hint, takes a NEW
 * key (masked input with a Show toggle), and offers an explicit Clear. Leaving the field
 * blank and tapping Save does nothing — clearing is deliberate, via the Clear button.
 */
@Composable
private fun ApiKeyDialog(
    currentMasked: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val hasKey = currentMasked.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API key (BYOK)") },
        text = {
            Column {
                Text(
                    "Your OpenAI key is stored encrypted on this device and used only for Smart Formatting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasKey) {
                    Spacer(Modifier.height(10.dp))
                    Text("Current key: $currentMasked", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text(if (hasKey) "Enter a new key to replace" else "sk-…") },
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = input.isNotBlank(), onClick = { onSave(input.trim()) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (hasKey) {
                    TextButton(onClick = onClear) { Text("Clear key", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TextDialog(
    title: String,
    help: String,
    placeholder: String,
    confirm: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text(placeholder) },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) })
}

@Composable
private fun Group(
    title: String,
    icon: ImageVector,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            // Tappable section header with a leading icon tile (WhatsApp-style), collapses contents.
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.vibeflow.mobile.ui.components.GradientIcon(icon, boxSize = 34.dp, iconSize = 18.dp, radius = 10.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) Column(Modifier.padding(bottom = 6.dp), content = content)
        }
    }
}

/** Opens the user's mail app to the Mynah support address. */
private fun emailSupport(context: android.content.Context, subject: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:vamsy.24@gmail.com")
        putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
    }
    runCatching { context.startActivity(intent) }
}

/** A small uppercase section label (ACCOUNT · PREFERENCES · SUPPORT). */
@Composable
private fun SectionHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** The Mynah Pro / subscription card — the monetization surface. */
@Composable
private fun ProCard(quota: com.vibeflow.mobile.auth.SupabaseAuth.Quota?, onUpgrade: () -> Unit) {
    val isPro = quota?.isPro == true
    Surface(
        onClick = onUpgrade,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.vibeflow.mobile.ui.components.GradientIcon(Icons.Filled.AutoAwesome, boxSize = 46.dp, iconSize = 25.dp, radius = 14.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Mynah Pro", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (isPro) "Active · unlimited AI polishes"
                    else quota?.let { "${it.remaining} free polishes left · go unlimited" } ?: "Unlimited AI polishes & priority models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isPro) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(brandBrush())
                        .clickable(onClick = onUpgrade).padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text("Upgrade", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** WhatsApp-style profile header — gradient avatar + name + status, tap to edit. */
@Composable
private fun ProfileHeader(name: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(brandBrush()), contentAlignment = Alignment.Center) {
                if (name.isBlank()) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                } else {
                    Text(name.trim().first().uppercase(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Set up your profile" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** True if the Mynah auto-insert Accessibility service is currently enabled by the user. */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val id = "${context.packageName}/com.vibeflow.mobile.accessibility.VibeFlowAccessibilityService"
    val enabled = android.provider.Settings.Secure.getString(
        context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(id, ignoreCase = true) }
}

/** A full-width tappable row used inside the account bottom sheet. */
@Composable
private fun SheetAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun PermissionRow(done: Boolean, title: String, subtitle: String, cta: String, alwaysAction: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done || alwaysAction) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(cta) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Human label for a stored recognition-language code. */
private fun voiceLangLabel(code: String): String = when (code) {
    "en-IN" -> "English (India)"
    "en-US" -> "English (US)"
    "en-GB" -> "English (UK)"
    "en-AU" -> "English (Australia)"
    "auto" -> "Match device"
    else -> code
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
