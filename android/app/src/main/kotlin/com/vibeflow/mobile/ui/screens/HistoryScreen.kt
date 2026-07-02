package com.vibeflow.mobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import com.vibeflow.mobile.BuildConfig
import androidx.compose.foundation.BorderStroke
import com.vibeflow.mobile.core.model.HistoryEntry
import com.vibeflow.mobile.ui.MainViewModel
import com.vibeflow.mobile.ui.components.AiPolishedBadge
import com.vibeflow.mobile.ui.components.CosmicChip
import com.vibeflow.mobile.ui.components.GradientIcon
import com.vibeflow.mobile.ui.theme.Brand
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier = Modifier, onNewDictation: () -> Unit = {}) {
    var detail by remember { mutableStateOf<HistoryEntry?>(null) }
    val open = detail
    if (open != null) {
        CaptureDetailScreen(open, vm, modifier, onBack = { detail = null })
        return
    }

    val entries by vm.history.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }   // all | pinned | polished
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(entries, query, filter) {
        val q = query.trim().lowercase()
        entries.asSequence()
            .filter {
                when (filter) {
                    "pinned" -> it.pinned
                    "polished" -> it.polished.isNotBlank()
                    "meetings" -> (it.text + " " + it.app).lowercase().let { s -> s.contains("meeting") || s.contains("call") || s.contains("standup") || s.contains("sync") }
                    "ideas" -> it.text.lowercase().let { s -> s.contains("idea") || s.contains("note") || s.contains("todo") || s.contains("remember") }
                    else -> true
                }
            }
            .filter { q.isEmpty() || it.text.lowercase().contains(q) || it.app.lowercase().contains(q) }
            .sortedWith(compareByDescending<HistoryEntry> { it.pinned }.thenByDescending { it.ts })
            .toList()
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("History", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text("${entries.size} dictations", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entries.any { !it.pinned }) {
                TextButton(onClick = { confirmClear = true }) { Text("Clear", color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search dictations", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("all" to "All", "pinned" to "Pinned", "polished" to "AI Polished", "meetings" to "Meetings", "ideas" to "Ideas").forEach { (k, label) ->
                CosmicChip(label, filter == k, { filter = k })
            }
        }
        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (entries.isEmpty()) "Nothing yet.\nDictations you make appear here." else "No matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onOpen = { detail = entry },
                        onCopy = { vm.copyEntry(entry) },
                        onPin = { vm.togglePin(entry.id) },
                        onDelete = { vm.deleteEntry(entry.id) },
                    )
                }
            }
        }
        }
        com.vibeflow.mobile.ui.components.FloatingMicButton(
            onNewDictation,
            Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 16.dp),
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes all unpinned dictations. Pinned ones are kept.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** A cosmic glass card: gradient mic icon · 2-line transcript · app · time · ✨ AI Polished. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onCopy: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val polished = entry.polished.isNotBlank()
    // The destination app's real launcher icon (where this dictation went); gradient mic if unknown.
    val appIcon = remember(entry.pkg) {
        if (entry.pkg.isBlank()) null
        else runCatching { context.packageManager.getApplicationIcon(entry.pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
    }
    val body = (if (polished) entry.polished else entry.text).trim()
    Box {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (appIcon != null) {
                    Image(appIcon, contentDescription = entry.app, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                } else {
                    GradientIcon(Icons.Filled.Mic, boxSize = 42.dp, iconSize = 20.dp, radius = 12.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.pinned) {
                            Icon(Icons.Filled.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            "${entry.app.ifBlank { "Voice" }}  ·  ${formatWhen(entry.ts)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (polished) { Spacer(Modifier.width(8.dp)); AiPolishedBadge() }
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(); menu = false })
            DropdownMenuItem(text = { Text(if (entry.pinned) "Unpin" else "Pin") }, onClick = { onPin(); menu = false })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(); menu = false })
        }
    }
}

private val timeFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
private fun formatWhen(tsSeconds: Long): String =
    runCatching { timeFmt.format(Date(tsSeconds * 1000)) }.getOrDefault("")

/**
 * Full capture view: the same dictation at every pipeline stage — Raw (engine),
 * Clean (curation), Polished (L3) — each viewable, editable and copyable, so a bad
 * result never means re-speaking. Polished is shown once Smart Formatting exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureDetailScreen(entry: HistoryEntry, vm: MainViewModel, modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()

    var polished by remember(entry.id) { mutableStateOf(entry.polished) }
    var polishing by remember(entry.id) { mutableStateOf(false) }
    var selected by remember(entry.id) { mutableIntStateOf(if (entry.polished.isNotBlank()) 2 else 1) }   // open to the best stage
    // Token readout (prompt/completion) — DEBUG builds only; auto-stripped from release via BuildConfig.DEBUG below.
    var polishTokens by remember(entry.id) { mutableStateOf(entry.promptTokens to entry.completionTokens) }
    var polishStyle by remember(entry.id) { mutableStateOf(settings.smartFormatStyle) }
    var showStyles by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var editing by remember(entry.id, selected) { mutableStateOf(false) }   // read-only until the pencil is tapped

    val labels = listOf("Raw", "Clean", "Polished")
    val hints = listOf("Exactly what the recognizer heard", "Punctuated text delivered to the app", "AI-formatted version")
    val contents = listOf(entry.raw.ifBlank { entry.text }, entry.text, polished)
    var edited by remember(selected, entry.id, polished) { mutableStateOf(contents[selected]) }

    // Persist edits so they survive leaving and returning (debounced).
    LaunchedEffect(edited, selected, entry.id) {
        kotlinx.coroutines.delay(500)
        if (edited != contents[selected]) vm.saveCaptureStage(entry.id, selected, edited)
    }

    // Re-polish the original CLEAN text in [style] (tier-aware) and replace the Polished stage.
    fun runPolish(style: String) {
        polishing = true
        scope.launch {
            when (val r = vm.polishAnyTier(entry.text, style)) {
                is MainViewModel.PolishOutcome.Success -> {
                    polished = r.text
                    vm.savePolished(entry.id, r.text, r.promptTokens, r.completionTokens)
                    polishTokens = r.promptTokens to r.completionTokens
                    polishStyle = style
                    selected = 2
                }
                is MainViewModel.PolishOutcome.Failure -> Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
            }
            polishing = false
        }
    }

    val appIcon = remember(entry.pkg) {
        if (entry.pkg.isBlank()) null
        else runCatching { context.packageManager.getApplicationIcon(entry.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    val over = edited.length > com.vibeflow.mobile.ai.SmartFormatter.MAX_INPUT_CHARS

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Capture", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Compact source meta — small icon + app · time · duration on one line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appIcon != null) {
                    Image(appIcon, contentDescription = null, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    buildString {
                        append(entry.app.ifBlank { "Voice dictation" })
                        append("  ·  ").append(formatWhen(entry.ts))
                        if (entry.durationSec > 0f) append("  ·  ${entry.durationSec.toInt()}s")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (entry.polished.isNotBlank()) { Spacer(Modifier.width(8.dp)); AiPolishedBadge() }
            }
            Spacer(Modifier.height(12.dp))

            // Stage selector + a one-line descriptor so it's clear which version you're seeing.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = i == selected,
                        onClick = { selected = i },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(12.dp))

            // THE CONTENT WINDOW — the priority element; takes all remaining height, a clearly
            // distinct surface so it reads as "the text" (vs the chrome around it).
            if (selected == 2 && polishing) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Polishing with AI…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val focusRequester = remember { FocusRequester() }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Header INSIDE the window: stage label + actions — so they never sit over the text.
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                hints[selected],
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                if (editing) vm.saveCaptureStage(entry.id, selected, edited)
                                editing = !editing
                            }) {
                                Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = if (editing) "Done" else "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { vm.saveCaptureStage(entry.id, selected, edited); copyToClipboard(context, edited) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 12.dp))
                        if (editing) {
                            TextField(
                                value = edited,
                                onValueChange = { edited = it },
                                modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
                        } else {
                            // Read-only by default — tapping just reads/selects; no keyboard pops up.
                            SelectionContainer {
                                Text(
                                    edited.ifBlank { if (selected == 2) "Not AI-formatted yet — tap Format below." else "—" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (edited.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Disclaimer on the Polished stage, + a "too long" warning if needed.
            if (selected == 2 && edited.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "✨ AI can make mistakes. Please review.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (over) {
                Spacer(Modifier.height(4.dp))
                Text("Too long to AI-polish", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            // Action dock — Copy · AI Polish · Share · More.
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DockAction(Icons.Filled.ContentCopy, "Copy", Modifier.weight(1f)) {
                        vm.saveCaptureStage(entry.id, selected, edited); copyToClipboard(context, edited)
                    }
                    DockAction(Icons.Filled.AutoAwesome, if (polished.isBlank()) "AI Polish" else "Re-polish", Modifier.weight(1f)) { showStyles = true }
                    DockAction(Icons.Filled.Share, "Share", Modifier.weight(1f)) { shareText(context, edited) }
                    Box(Modifier.weight(1f)) {
                        DockAction(Icons.Filled.MoreHoriz, "More", Modifier.fillMaxWidth()) { moreMenu = true }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text(if (entry.pinned) "Unpin" else "Pin") }, onClick = { vm.togglePin(entry.id); moreMenu = false })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { vm.deleteEntry(entry.id); moreMenu = false; onBack() })
                        }
                    }
                }
            }
        }
    }

    if (showStyles) {
        ModalBottomSheet(onDismissRequest = { showStyles = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Format as", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Re-generates from your original dictation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    vm.smartFormatStyles.forEach { st ->
                        FilterChip(selected = st == polishStyle, onClick = { polishStyle = st }, label = { Text(st) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { showStyles = false; runPolish(polishStyle) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("✨ Polish as $polishStyle") }
            }
        }
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
    cm?.setPrimaryClip(android.content.ClipData.newPlainText("VibeFlow", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(send, "Share dictation").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** One icon+label button in the detail action dock. */
@Composable
private fun DockAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
