package com.vibeflow.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibeflow.mobile.capture.FloatingMicService
import com.vibeflow.mobile.ui.AppRoot
import kotlinx.coroutines.launch
import com.vibeflow.mobile.ui.MainViewModel
import com.vibeflow.mobile.ui.SetupStatus
import com.vibeflow.mobile.ui.SystemActions
import com.vibeflow.mobile.ui.theme.MynahTheme

class MainActivity : ComponentActivity() {

    private var setupStatus by mutableStateOf(SetupStatus())

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSetup() }
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSetup()              // real setup state on the first frame, so reminders don't flash
        maybeRequestNotifications()

        setContent {
            MynahTheme {
                val vm: MainViewModel = viewModel()
                val settings by vm.settings.collectAsState()
                val actions = SystemActions(
                    openImeSettings = { openImeSettings() },
                    showImePicker = { showImePicker() },
                    requestMic = { requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                    openAppDetails = { openAppDetails() },
                    toggleFloatingMic = { enable -> toggleFloatingMic(enable) },
                    requestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openAppDetails()
                    },
                    requestOverlay = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                    },
                    openAccessibilitySettings = {
                        // Deep-link straight to our service where supported (Pixel/AOSP); Samsung
                        // ignores the extras and lands on the main page → the dialog tells the user
                        // to open "Downloaded apps" ▸ "Mynah Auto-insert".
                        val cn = android.content.ComponentName(
                            this, "com.vibeflow.mobile.accessibility.VibeFlowAccessibilityService",
                        ).flattenToString()
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            putExtra(":settings:fragment_args_key", cn)
                            putExtra(":settings:show_fragment_args", android.os.Bundle().apply {
                                putString(":settings:fragment_args_key", cn)
                            })
                        })
                    },
                )
                var showSplash by remember { mutableStateOf(true) }
                when {
                    showSplash -> com.vibeflow.mobile.ui.screens.SplashScreen { showSplash = false }
                    !settings.onboardingDone ->
                        com.vibeflow.mobile.ui.screens.OnboardingFlow(vm, actions, setupStatus) { vm.setOnboardingDone(true) }
                    else -> AppRoot(vm = vm, setup = setupStatus, actions = actions)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        // If the user enabled the floating mic and has since granted overlay permission, start it.
        lifecycleScope.launch {
            runCatching {
                val s = VibeFlowApp.settings().snapshot()
                if (s.floatingMic && Settings.canDrawOverlays(this@MainActivity)) startFloatingMic()
            }
        }
    }

    private fun startFloatingMic() {
        val i = Intent(this, FloatingMicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun stopFloatingMic() {
        stopService(Intent(this, FloatingMicService::class.java))
    }

    private fun toggleFloatingMic(enable: Boolean) {
        if (!enable) { stopFloatingMic(); return }
        if (Settings.canDrawOverlays(this)) {
            startFloatingMic()
        } else {
            // Send the user to grant "Display over other apps"; we auto-start on return (onResume).
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
            )
        }
    }

    private fun refreshSetup() {
        setupStatus = SetupStatus(
            keyboardEnabled = isImeEnabled(),
            micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            overlayGranted = Settings.canDrawOverlays(this),
        )
    }

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun openImeSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun showImePicker() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun openAppDetails() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
        )
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
