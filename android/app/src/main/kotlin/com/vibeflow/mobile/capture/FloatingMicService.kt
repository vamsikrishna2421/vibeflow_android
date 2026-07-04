package com.vibeflow.mobile.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.vibeflow.mobile.R
import com.vibeflow.mobile.VibeFlowApp
import com.vibeflow.mobile.accessibility.AutoInsert
import com.vibeflow.mobile.ai.SmartFormatter
import com.vibeflow.mobile.asr.SpeechEngine
import com.vibeflow.mobile.asr.SpeechEngines
import com.vibeflow.mobile.core.Pipeline
import com.vibeflow.mobile.data.Clipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A draggable floating mic bubble (system overlay) so users can keep their own
 * keyboard (Gboard, etc.) and still dictate VibeFlow voice + AI anywhere: tap →
 * speak → the result is copied to the clipboard, where any keyboard's paste chip
 * (including ours) offers it in one tap. No Accessibility Service needed.
 */
class FloatingMicService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private var iconView: ImageView? = null    // the gray circle with the full-colour logo
    private var bubbleRoot: View? = null       // overlay container (room for the pulse)
    private lateinit var params: WindowManager.LayoutParams
    private var engine: SpeechEngine? = null
    private var listening = false
    private var autoMode = false                // opt-in: bubble syncs to the keyboard + inserts at cursor
    @Volatile private var wantAutoInsert = false
    @Volatile private var wantOnline = true        // Google online recognizer unless Private mode / user opted out
    @Volatile private var wantLanguage = "en-IN"   // BCP-47 recognition locale (profile setting)
    @Volatile private var wantNoiseModel = "off"   // "whisper" routes to offline Whisper in noisy rooms
    private var pulse: android.animation.ValueAnimator? = null
    private var spinnerView: android.widget.ProgressBar? = null
    private enum class BubbleState { IDLE, LISTENING, PROCESSING }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
        // Auto-insert mode (only when the user enabled the setting AND the Accessibility service
        // is on): the bubble follows the keyboard — visible only while an editable field is focused.
        // Re-evaluated whenever the setting changes OR the service connects, so enabling it later
        // flips the bubble live (no app restart needed).
        AutoInsert.onReadyChanged = { applyMode() }
        scope.launch {
            VibeFlowApp.settings().flow.collect { s ->
                wantAutoInsert = s.autoInsert
                wantOnline = s.onlineRecognition       // transcription is independent of AI polish
                wantLanguage = s.resolvedRecognitionLanguage()
                wantNoiseModel = s.noiseModel
                applyMode()
            }
        }
    }

    private fun applyMode() {
        val mode = wantAutoInsert && AutoInsert.isReady
        if (mode == autoMode) return
        autoMode = mode
        if (mode) {
            AutoInsert.onEditableFocus = { focused -> setBubbleVisible(focused) }
            setBubbleVisible(AutoInsert.fieldFocused)
        } else {
            AutoInsert.onEditableFocus = null
            setBubbleVisible(true)
        }
    }

    private fun setBubbleVisible(visible: Boolean) {
        bubbleRoot?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    // ---- overlay bubble ---------------------------------------------------

    private fun addBubble() {
        if (bubbleRoot != null) return
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)   // the full-colour VibeFlow logo
            scaleType = ImageView.ScaleType.CENTER_CROP           // fill the circle
            background = grayBg()                                 // start IDLE (gray); state set below
            clipToOutline = true                                  // MASK the logo into a circle (no square corners)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            elevation = dp(8).toFloat()
        }
        // A spinning ring shown only while PROCESSING (transcribe/polish) — larger than the icon so
        // it reads as a halo around the logo. Hidden in idle/listening.
        val spinner = android.widget.ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFFB794FF.toInt())
        }
        // A slightly bigger container so the listening pulse (scale-up) + spinner ring aren't clipped.
        val container = android.widget.FrameLayout(this).apply {
            addView(icon, android.widget.FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
            addView(spinner, android.widget.FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        }
        params = WindowManager.LayoutParams(
            dp(84), dp(84),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(220)
        }
        container.setOnTouchListener(DragTap())
        runCatching { wm.addView(container, params) }
        iconView = icon
        spinnerView = spinner
        bubbleRoot = container
        setBubbleState(BubbleState.IDLE)
    }

    // ---- bubble state: IDLE (gray) → LISTENING (logo colour + pulse) → PROCESSING (spinner) -------

    /** The single source of truth for what the bubble looks like. Each call is idempotent. */
    private fun setBubbleState(state: BubbleState) {
        val icon = iconView ?: return
        when (state) {
            BubbleState.IDLE -> {
                stopPulse()
                spinnerView?.visibility = View.GONE
                icon.colorFilter = grayFilter()      // desaturate the logo → clearly "off"
                icon.background = grayBg()
                icon.alpha = 0.9f
            }
            BubbleState.LISTENING -> {
                spinnerView?.visibility = View.GONE
                icon.colorFilter = null              // full-colour logo = "live, listening"
                icon.background = liveBg()
                icon.alpha = 1f
                startPulse()                         // breathing pulse
            }
            BubbleState.PROCESSING -> {
                stopPulse()                          // pulse off; the spinner is the busy signal
                icon.colorFilter = null
                icon.background = busyBg()
                icon.alpha = 1f
                spinnerView?.visibility = View.VISIBLE
            }
        }
    }

    /** Saturation-0 matrix → renders the colourful logo in gray for the IDLE state. */
    private fun grayFilter() =
        android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })

    /** IDLE: muted gray-slate circle = not listening. */
    private fun grayBg() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFF2B313D.toInt())          // muted slate gray
        setStroke(dp(1), 0x33FFFFFF.toInt())  // faint outline
    }

    /** LISTENING: deep navy with a bright purple ring = capturing your voice. */
    private fun liveBg() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFF0C1426.toInt())          // deep navy (matches the logo)
        setStroke(dp(2), 0xDD8B5CF6.toInt())  // bright purple ring (cosmic glow)
    }

    /** PROCESSING: indigo-navy with a soft ring, under the spinning halo. */
    private fun busyBg() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFF141B2E.toInt())          // indigo-navy
        setStroke(dp(1), 0x668B5CF6.toInt())
    }

    /** Drag to move; a tap (negligible movement) toggles dictation; long-press stops. */
    private inner class DragTap : View.OnTouchListener {
        private var downX = 0f; private var downY = 0f
        private var startX = 0; private var startY = 0
        private var downTime = 0L
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                    downTime = android.os.SystemClock.uptimeMillis()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(v, params) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(e.rawX - downX) > dp(8) || abs(e.rawY - downY) > dp(8)
                    val held = android.os.SystemClock.uptimeMillis() - downTime > 600
                    if (!moved && held) stopSelf()
                    else if (!moved) onTap()
                    return true
                }
            }
            return false
        }
    }

    private fun onTap() {
        if (listening) {
            engine?.stop()
            setBubbleState(BubbleState.PROCESSING)   // stopped → show "working" immediately
        } else startDictation()
    }

    private fun startDictation() {
        listening = true
        setBubbleState(BubbleState.LISTENING)        // logo colour + pulse = it's listening
        scope.launch {
            // Environment-aware: brief noise probe → Whisper if noisy & downloaded, else system.
            val eng = SpeechEngines.createForEnvironment(this@FloatingMicService, wantOnline, wantLanguage, wantNoiseModel)
            if (!listening) return@launch    // tapped off during the probe
            engine = eng
            // The bubble has no text strip, so set expectations with a toast when Whisper kicks in.
            if (eng is com.vibeflow.mobile.asr.WhisperSpeechEngine) toast("🎧 Noise handling — needs patience")
            eng.start(object : SpeechEngine.Listener {
                override fun onListening() { if (listening) setBubbleState(BubbleState.LISTENING) }
                // Whisper streaming: recording stopped, finishing the last phrase → show the spinner.
                override fun onProcessing() { setBubbleState(BubbleState.PROCESSING) }
                override fun onFinal(text: String) {
                    listening = false
                    if (text.isBlank()) { setBubbleState(BubbleState.IDLE); return }
                    setBubbleState(BubbleState.PROCESSING)   // transcribe done → polishing/saving
                    process(text)            // back to IDLE when the text lands
                }
                override fun onError(message: String) {
                    listening = false; setBubbleState(BubbleState.IDLE); toast(message)
                }
            }, handsFree = true, endSilenceMs = 1500)
        }
    }

    private fun process(rawText: String) {
        scope.launch {
            val s = VibeFlowApp.settings().snapshot()
            VibeFlowApp.corrections().ensureLoaded()
            val cfg = s.pipelineConfig().copy(corrections = VibeFlowApp.corrections().confirmed())
            val cleaned = Pipeline.process(rawText, cfg)

            // Auto-insert: drop the CLEANED text at the cursor instantly (zero gap), then swap it
            // for the AI-polished version when/if that arrives. Falls back to clipboard otherwise.
            val handle = if (autoMode) AutoInsert.insert(cleaned) else null
            val inserted = handle != null

            var finalText = cleaned
            var polishedText: String? = null     // the AI-polished stage, stored separately so History can show it
            val wantPolish = s.autoPolish && !s.privateMode && when (s.smartFormatTier) {
                "managed" -> true
                "byok" -> s.llmApiKey.isNotBlank()
                else -> false
            }
            if (wantPolish) {
                val polished = runCatching {
                    if (s.smartFormatTier == "managed") {
                        when (val r = com.vibeflow.mobile.ai.ManagedFormatter(VibeFlowApp.supabaseAuth())
                            .format(cleaned, s.smartFormatStyle, s.userName, s.userTitle)) {
                            is com.vibeflow.mobile.ai.ManagedFormatter.Result.Success -> r.text
                            com.vibeflow.mobile.ai.ManagedFormatter.Result.DeviceSuperseded -> { VibeFlowApp.supabaseAuth().signOut(); null }
                            is com.vibeflow.mobile.ai.ManagedFormatter.Result.Maintenance -> {
                                toast(r.message.ifBlank { "AI paused for maintenance — offline mode" }); null
                            }
                            else -> null   // TooLong / LimitReached / errors → keep the cleaned text
                        }
                    } else {
                        SmartFormatter().format(cleaned, s.llmApiKey, s.llmModel, s.smartFormatStyle, s.userName, s.userTitle)
                            .getOrNull()?.text
                    }
                }.getOrNull()
                if (!polished.isNullOrBlank()) {
                    finalText = polished
                    polishedText = polished
                    if (inserted) AutoInsert.swap(handle!!, polished)   // swap cleaned → polished in place
                }
            }

            // Save the three stages like the keyboard does: raw → clean (text) → polished (separate
            // field). Storing the polished version in its own field is what makes the "✨ AI Polished"
            // badge + the Polished stage tab show up in History (they key off entry.polished).
            runCatching {
                val entry = VibeFlowApp.history().add(
                    cleaned, app = "Floating mic",
                    target = if (inserted) "insert" else "clipboard", max = s.historyMax, raw = rawText,
                )
                if (entry != null && !polishedText.isNullOrBlank()) {
                    VibeFlowApp.history().update(entry.id, polished = polishedText)
                }
            }
            if (!inserted) {
                Clipboard.copy(this@FloatingMicService, finalText)
                toast(if (autoMode) "Couldn't reach the field — copied instead" else "✨ Copied — tap your text field, then the paste chip")
            }
            setBubbleState(BubbleState.IDLE)     // done → back to gray
            if (autoMode) setBubbleVisible(AutoInsert.fieldFocused)
        }
    }

    /** A small scale "pulse" on the button while it's listening / working. */
    private fun startPulse() {
        stopPulse()
        pulse = android.animation.ValueAnimator.ofFloat(1f, 1.18f).apply {
            duration = 600
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val s = a.animatedValue as Float
                iconView?.scaleX = s; iconView?.scaleY = s
            }
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel(); pulse = null
        iconView?.scaleX = 1f; iconView?.scaleY = 1f
    }

    // ---- chrome -----------------------------------------------------------

    private fun startForegroundCompat() {
        val chId = "vibeflow_floating"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(chId, "Floating mic", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val stop = Intent(this, FloatingMicService::class.java).setAction(ACTION_STOP)
        val stopPi = android.app.PendingIntent.getService(
            this, 0, stop,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = androidx.core.app.NotificationCompat.Builder(this, chId)
            .setSmallIcon(R.drawable.ic_mic_tile)
            .setContentTitle("VibeFlow floating mic")
            .setContentText("Tap the bubble to dictate · long-press it to stop")
            .addAction(0, "Stop", stopPi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        AutoInsert.onEditableFocus = null
        AutoInsert.onReadyChanged = null
        stopPulse()
        runCatching { engine?.release() }
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        iconView = null; bubbleRoot = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4711
        const val ACTION_STOP = "com.vibeflow.mobile.FLOATING_STOP"
    }
}
