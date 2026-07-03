package com.vibeflow.mobile.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vibeflow.mobile.VibeFlowApp
import com.vibeflow.mobile.asr.SpeechEngine
import com.vibeflow.mobile.asr.SpeechEngines
import com.vibeflow.mobile.core.Pipeline
import com.vibeflow.mobile.data.Clipboard
import com.vibeflow.mobile.data.Settings
import com.vibeflow.mobile.ui.theme.AccentRed
import com.vibeflow.mobile.ui.theme.Brand
import com.vibeflow.mobile.ui.theme.CtaGreen
import com.vibeflow.mobile.ui.theme.MynahTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A lightweight floating sheet launched from the Quick Settings tile. It records
 * offline, copies the finished text to the clipboard, saves it to history, and
 * confirms — all without needing a focused text field.
 */
class CaptureActivity : ComponentActivity() {

    private lateinit var engine: SpeechEngine
    private var settings: Settings = Settings()

    private val uiState = mutableStateOf<CaptureUi>(CaptureUi.Preparing)
    private val preview = mutableStateOf("")

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) beginListening() else uiState.value = CaptureUi.NeedsPermission
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = SpeechEngines.create(this, online = true)

        lifecycleScope.launch { settings = VibeFlowApp.settings().flow.first() }

        setContent {
            MynahTheme {
                CaptureSheet(
                    state = uiState.value,
                    preview = preview.value,
                    onStop = { engine.stop() },
                    onCopyAgain = { (uiState.value as? CaptureUi.Done)?.let { Clipboard.copy(this, it.text) } },
                    onDismiss = { finish() },
                )
            }
        }

        ensurePermissionThenListen()
    }

    private fun ensurePermissionThenListen() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) beginListening() else requestMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginListening() {
        uiState.value = CaptureUi.Listening
        haptic(18L)
        lifecycleScope.launch {
        engine = SpeechEngines.createForEnvironment(this@CaptureActivity, online = settings.onlineRecognition, language = settings.resolvedRecognitionLanguage(), noiseModel = settings.noiseModel)
        engine.start(object : SpeechEngine.Listener {
            override fun onListening() { uiState.value = CaptureUi.Listening }
            override fun onPartial(text: String) { preview.value = text }
            override fun onFinal(text: String) { deliver(text) }
            override fun onError(message: String) { uiState.value = CaptureUi.Error(message) }
        }, handsFree = settings.handsFree, endSilenceMs = settings.endSilenceMs)
        }
    }

    private fun deliver(raw: String) {
        haptic(12L)
        val processed = Pipeline.process(raw, settings.pipelineConfig())
        if (processed.isBlank()) {
            uiState.value = CaptureUi.Error("Didn't catch that — try again")
            return
        }
        Clipboard.copy(this, processed)
        if (settings.historyEnabled) {
            lifecycleScope.launch {
                runCatching {
                    VibeFlowApp.history().add(processed, app = "Clipboard", target = "clipboard", max = settings.historyMax)
                }
            }
        }
        uiState.value = CaptureUi.Done(processed)
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun haptic(ms: Long) {
        if (!settings.haptics) return
        val vib = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else vib.vibrate(ms)
    }
}

sealed interface CaptureUi {
    data object Preparing : CaptureUi
    data object Listening : CaptureUi
    data object NeedsPermission : CaptureUi
    data class Done(val text: String) : CaptureUi
    data class Error(val message: String) : CaptureUi
}

@Composable
private fun CaptureSheet(
    state: CaptureUi,
    preview: String,
    onStop: () -> Unit,
    onCopyAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    CaptureUi.Preparing, CaptureUi.Listening -> {
                        val pulsing = state == CaptureUi.Listening
                        val color by animateColorAsState(if (pulsing) AccentRed else CtaGreen, label = "mic")
                        Box(
                            modifier = Modifier.size(76.dp).clip(CircleShape).background(color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Listening", tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (preview.isBlank()) "Listening… speak now" else preview,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onStop, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop & copy")
                        }
                    }
                    is CaptureUi.Done -> {
                        Text("Copied to clipboard ✓", style = MaterialTheme.typography.titleMedium, color = Brand)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(state.text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onCopyAgain, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text("Copy")
                            }
                            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("Done") }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Now switch to any app and paste.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CaptureUi.NeedsPermission -> {
                        Text("Microphone needed", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Open Mynah and grant the microphone permission, then try again.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("Close") }
                    }
                    is CaptureUi.Error -> {
                        Text("Hmm…", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("Close") }
                    }
                }
            }
        }
    }
}
