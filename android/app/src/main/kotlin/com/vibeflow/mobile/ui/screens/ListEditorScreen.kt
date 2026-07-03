package com.vibeflow.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibeflow.mobile.data.Settings
import com.vibeflow.mobile.ui.EditorType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListEditorScreen(
    type: EditorType,
    settings: Settings,
    onSave: (EditorType, List<String>, Map<String, String>) -> Unit,
    onBack: () -> Unit,
) {
    val isVocab = type == EditorType.Vocabulary
    val terms = remember { mutableStateListOf<String>().apply { addAll(settings.vocabulary) } }
    val snippets = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(settings.snippets.toList()) } }

    var newTerm by remember { mutableStateOf("") }
    var newTrigger by remember { mutableStateOf("") }
    var newExpansion by remember { mutableStateOf("") }

    fun persist() {
        if (isVocab) onSave(type, terms.toList(), emptyMap())
        else onSave(type, emptyList(), snippets.toMap())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isVocab) "Vocabulary" else "Snippets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                if (isVocab) "Teach Mynah proper spelling & casing for names and jargon. Each term is restored on every dictation."
                else "Say the trigger phrase and Mynah inserts the full text. Great for emails, addresses, signatures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            if (isVocab) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTerm,
                        onValueChange = { newTerm = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g. Kubernetes, GitHub, Acme") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val t = newTerm.trim()
                            if (t.isNotEmpty() && terms.none { it.equals(t, true) }) { terms.add(t); persist() }
                            newTerm = ""
                        },
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(terms.toList()) { term ->
                        EditorCard(title = term, subtitle = null) { terms.remove(term); persist() }
                    }
                }
            } else {
                OutlinedTextField(
                    value = newTrigger,
                    onValueChange = { newTrigger = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Trigger phrase — e.g. my email") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newExpansion,
                        onValueChange = { newExpansion = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Expands to — e.g. you@example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val k = newTrigger.trim()
                            val v = newExpansion.trim()
                            if (k.isNotEmpty() && v.isNotEmpty()) {
                                snippets.removeAll { it.first.equals(k, true) }
                                snippets.add(k to v); persist()
                            }
                            newTrigger = ""; newExpansion = ""
                        },
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snippets.toList()) { (trigger, expansion) ->
                        EditorCard(title = trigger, subtitle = expansion) {
                            snippets.removeAll { it.first == trigger }; persist()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorCard(title: String, subtitle: String?, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}
