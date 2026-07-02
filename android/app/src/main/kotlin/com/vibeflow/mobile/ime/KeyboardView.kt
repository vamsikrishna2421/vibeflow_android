package com.vibeflow.mobile.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.vibeflow.mobile.R

/** What the keyboard view asks the IME to do. */
interface KeyboardActions {
    fun onMicTap()
    fun onMicPressStart()
    fun onMicPressEnd()
    fun onKey(text: String)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onSwitchIme()
    fun onOpenSettings()
    fun onPickSuggestion(text: String)
    fun onHide()
    val pushToTalk: Boolean
}

/** Immutable render state pushed from the IME to the view. */
data class KeyboardUiState(
    val listening: Boolean = false,
    val processing: Boolean = false,   // post-stop: transcribing (Whisper) / working on the result
    val preview: String = "",
    val hint: String = "",
)

/**
 * The VibeFlow keyboard — a normal everyday keyboard first (full QWERTY + a
 * Gboard-style suggestion strip), with one-tap offline voice on the right of the
 * strip. Warm palette, theme-aware, shift + symbols.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val actions: KeyboardActions, themePref: String = "system") : LinearLayout(context) {

    private val p = ImePalette(context, themePref)

    private val micButton: MicButton
    private val waveform: WaveformView
    private val suggestionsRow: LinearLayout
    private val transcriptText: TextView
    private val autofillScroll: android.widget.HorizontalScrollView
    private val autofillRow: LinearLayout
    private val slots = ArrayList<TextView>()

    private var listeningNow = false
    private var processingNow = false
    private var hasAutofill = false
    private var bannerText: String? = null      // full-width strip message (polishing status / polished result)

    // Key-press pop-up preview (the magnified balloon above your finger, Gboard-style).
    private var keyPreviewPopup: android.widget.PopupWindow? = null
    private var keyPreviewText: TextView? = null
    private var secureField = false      // suppress the preview on password fields (no shoulder-surfing)

    private enum class Shift { NONE, SHIFT, CAPS }
    private var shift = Shift.NONE
    private var symbols = false
    private var symPage = 0          // 0 = primary symbols, 1 = "=\<" more-symbols page
    private var lastShiftTap = 0L
    private val letterKeys = ArrayList<TextView>()
    private val lettersZone: LinearLayout
    private var shiftKey: ImageView? = null

    private val lettersRows = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"),
    )
    private val symbolRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","_","&","-","+","(",")"),
        listOf("*","\"","'",":",";","!","?"),
    )
    private val TOP_HINTS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    // Second symbols page (reached via the "=\<" key) — where "=" and friends live.
    private val symbolRows2 = listOf(
        listOf("~","`","|","•","√","π","÷","×","¶","∆"),
        listOf("£","¢","€","¥","^","°","=","{","}"),
        listOf("\\","/","%","<",">","[","]"),
    )

    // Swipe down anywhere on the keyboard to hide it (no button to hunt for).
    private val hideDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (vy > 1800f && e1 != null && (e2.y - e1.y) > dp(60)) { hideKeyPreview(); actions.onHide(); return true }
            return false
        }
    })

    // Feed touches to the swipe detector but STILL dispatch to keys (typing unaffected).
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        hideDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    init {
        orientation = VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(p.bgTop, p.bgBottom))
        setPadding(dp(4), dp(5), dp(4), dp(8))

        // --- top strip: suggestions (or live transcript) + mic on the right ---
        val strip = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val area = FrameLayout(context)
        suggestionsRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        repeat(3) { i ->
            val slot = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(p.keyText)
                background = rippleKey(dp(10).toFloat(), Color.TRANSPARENT)
                isClickable = true
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                setOnClickListener { (tag as? String)?.let { w -> actions.onPickSuggestion(w) } }
            }
            slots.add(slot)
            suggestionsRow.addView(slot, LinearLayout.LayoutParams(0, dp(40), 1f))
            // Gboard has no dividers between suggestions.
        }
        transcriptText = TextView(context).apply {
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(p.ink)
            visibility = View.GONE
        }
        // Inline autofill chips (passwords/OTP) the system hands us go here — a
        // horizontally scrollable row that takes over the strip when present.
        autofillRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        autofillScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            visibility = View.GONE
            addView(autofillRow, FrameLayout.LayoutParams(WRAP, MATCH))
        }
        area.addView(suggestionsRow, FrameLayout.LayoutParams(MATCH, dp(44)))
        area.addView(transcriptText, FrameLayout.LayoutParams(MATCH, dp(44)))
        area.addView(autofillScroll, FrameLayout.LayoutParams(MATCH, dp(44)))

        // A reliable, always-visible "hide keyboard" button (swipe-down can be flaky per device).
        val hideKeyBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_down)
            imageTintList = ColorStateList.valueOf(p.keyMuted)
            background = rippleKey(dp(8).toFloat(), Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            contentDescription = "Hide keyboard"
            setOnClickListener { actions.onHide() }
        }
        strip.addView(hideKeyBtn, LinearLayout.LayoutParams(dp(38), dp(40)).also { it.marginStart = dp(2); it.marginEnd = dp(2) })
        strip.addView(area, LinearLayout.LayoutParams(0, dp(44), 1f))

        micButton = MicButton(context).apply {
            pushToTalk = actions.pushToTalk
            setColors(p.ctaGreen, p.recording, 0xFFFFFFFF.toInt())
            onTap = { actions.onMicTap() }
            onPressStart = { actions.onMicPressStart() }
            onPressEnd = { actions.onMicPressEnd() }
        }
        strip.addView(micButton, LinearLayout.LayoutParams(dp(46), dp(46)).also { it.marginStart = dp(4); it.marginEnd = dp(2) })
        addView(strip, lp(MATCH, dp(48)).also { it.leftMargin = dp(6); it.rightMargin = dp(4) })

        // --- waveform strip (visible while listening) ---
        waveform = WaveformView(context).apply {
            setColors(p.accentTertiary, p.accentLavender)
            visibility = View.INVISIBLE
        }
        addView(waveform, lp(MATCH, dp(16)).also { it.bottomMargin = dp(2); it.leftMargin = dp(16); it.rightMargin = dp(16) })

        // --- QWERTY ---
        lettersZone = LinearLayout(context).apply { orientation = VERTICAL }
        addView(lettersZone, lp(MATCH, WRAP))
        buildKeyboard()

        render(KeyboardUiState())
    }

    // ---------------- rendering ----------------

    fun render(state: KeyboardUiState) {
        micButton.setListening(state.listening)
        micButton.setProcessing(state.processing)
        waveform.setActive(state.listening)                 // waveform is for live audio only
        waveform.visibility = if (state.listening) View.VISIBLE else View.INVISIBLE
        listeningNow = state.listening
        processingNow = state.processing
        when {
            state.listening -> { hideKeyPreview(); bannerText = null; transcriptText.text = state.preview.ifBlank { "Listening…" } }
            state.processing -> { hideKeyPreview(); bannerText = null; transcriptText.text = state.preview.ifBlank { "Transcribing…" } }
        }
        updateStripVisibility()
    }

    /** The IME flags password fields so we don't flash each typed character in the preview. */
    fun setSecureField(secure: Boolean) {
        secureField = secure
        if (secure) hideKeyPreview()
    }

    /** Pick which of the strip layers (transcript / banner / autofill / suggestions) is shown. */
    private fun updateStripVisibility() {
        when {
            listeningNow || processingNow -> {                  // dictating / transcribing → transcript strip wins
                transcriptText.ellipsize = android.text.TextUtils.TruncateAt.START   // keep the latest words in view
                transcriptText.visibility = View.VISIBLE
                autofillScroll.visibility = View.GONE
                suggestionsRow.visibility = View.INVISIBLE
            }
            hasAutofill -> {                                    // system offered passwords/OTP
                transcriptText.visibility = View.GONE
                autofillScroll.visibility = View.VISIBLE
                suggestionsRow.visibility = View.INVISIBLE
            }
            bannerText != null -> {                             // polishing status / the polished sentence (full width)
                transcriptText.ellipsize = android.text.TextUtils.TruncateAt.END     // read the sentence from the start
                transcriptText.visibility = View.VISIBLE
                autofillScroll.visibility = View.GONE
                suggestionsRow.visibility = View.INVISIBLE
            }
            else -> {                                           // normal predictive suggestions
                transcriptText.visibility = View.GONE
                autofillScroll.visibility = View.GONE
                suggestionsRow.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Show a full-width message in the strip — the "✨ Polishing…" status, then the actual
     * polished sentence — using the whole width instead of a cramped 1/3 suggestion slot.
     * Tapping it (when [onTap] is set) reverts to the original. Pass null to clear.
     */
    fun setTranscriptBanner(text: String?, onTap: (() -> Unit)? = null) {
        bannerText = text
        if (text != null) {
            transcriptText.text = text
            transcriptText.isClickable = onTap != null
            transcriptText.background = if (onTap != null) rippleKey(dp(10).toFloat(), Color.TRANSPARENT) else null
            if (onTap != null) transcriptText.setOnClickListener { onTap() } else transcriptText.setOnClickListener(null)
        } else {
            transcriptText.isClickable = false
            transcriptText.background = null
            transcriptText.setOnClickListener(null)
        }
        updateStripVisibility()
    }

    /**
     * Render the system's inline autofill suggestions (Google Password Manager / OTP,
     * etc.) into the strip. The keyboard never stores credentials — these views are
     * inflated and owned by the autofill provider; we only host them. Empty = clear.
     */
    fun setAutofillViews(views: List<View>) {
        autofillRow.removeAllViews()
        hasAutofill = views.isNotEmpty()
        for (v in views) {
            (v.parent as? ViewGroup)?.removeView(v)
            autofillRow.addView(
                v,
                LinearLayout.LayoutParams(WRAP, dp(40)).also { it.marginStart = dp(6); it.gravity = Gravity.CENTER_VERTICAL },
            )
        }
        autofillScroll.scrollX = 0
        updateStripVisibility()
    }

    fun setAmplitude(level: Float) = waveform.setLevel(level)

    /** Show up to 3 suggestions; the first is emphasised. */
    fun setSuggestions(items: List<String>) {
        for (i in slots.indices) {
            val slot = slots[i]
            val w = items.getOrNull(i)
            if (w == null) {
                slot.text = ""; slot.tag = null; slot.isClickable = false
            } else {
                slot.text = w; slot.tag = w; slot.isClickable = true
                slot.setTypeface(null, if (i == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                slot.setTextColor(if (i == 0) p.ink else p.keyText)
            }
        }
    }

    // ---------------- keyboard build ----------------

    private fun buildKeyboard() {
        lettersZone.removeAllViews()
        letterKeys.clear()
        val rows = if (!symbols) lettersRows else if (symPage == 0) symbolRows else symbolRows2

        // Top letter row carries tiny number hints (1–0), Gboard-style.
        val topRow = if (!symbols) rows[0].mapIndexed { i, c -> hintKey(c, TOP_HINTS[i]) }
        else rows[0].map { charKey(it) }
        lettersZone.addView(keyRow(topRow))
        lettersZone.addView(keyRow(rows[1].map { charKey(it) }), rowLp().also { it.leftMargin = dp(18); it.rightMargin = dp(18) })

        val r3 = LinearLayout(context).apply { orientation = HORIZONTAL }
        r3.addView(
            if (symbols) textFnKey(if (symPage == 0) "=\\<" else "?123", 1.4f) {
                symPage = if (symPage == 0) 1 else 0; buildKeyboard()
            }
            else iconFnKey(R.drawable.ic_shift, 1.4f) { onShift() }.also { shiftKey = it },
            wkLp(1.4f)
        )
        rows[2].forEach { r3.addView(charKey(it), wkLp(1f)) }
        r3.addView(iconFnKey(R.drawable.ic_backspace, 1.4f, repeat = true) { actions.onBackspace() }, wkLp(1.4f))
        lettersZone.addView(r3, rowLp())

        val r4 = LinearLayout(context).apply { orientation = HORIZONTAL }
        r4.addView(textFnKey(if (symbols) "ABC" else "?123", 1.5f) { symbols = !symbols; symPage = 0; buildKeyboard() }, wkLp(1.5f))
        r4.addView(globeKey(), wkLp(1.1f))
        r4.addView(charKey(","), wkLp(1f))
        r4.addView(spaceKey(), wkLp(4.6f))
        r4.addView(charKey("."), wkLp(1f))
        r4.addView(iconFnKey(R.drawable.ic_return, 1.5f) { actions.onEnter() }, wkLp(1.5f))
        lettersZone.addView(r4, rowLp())

        applyShiftLabels()
    }

    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            now - lastShiftTap < 300 -> Shift.CAPS
            shift == Shift.NONE -> Shift.SHIFT
            else -> Shift.NONE
        }
        lastShiftTap = now
        applyShiftLabels()
    }

    /** Externally set the shift state (e.g. IME auto-capitalisation). */
    fun setAutoShift(on: Boolean) {
        if (symbols) return
        if (shift != Shift.CAPS) { shift = if (on) Shift.SHIFT else Shift.NONE; applyShiftLabels() }
    }

    private fun applyShiftLabels() {
        if (symbols) return
        val upper = shift != Shift.NONE
        for (k in letterKeys) {
            val base = k.tag as String
            k.text = if (upper) base.uppercase() else base
        }
        shiftKey?.alpha = if (shift == Shift.NONE) 0.6f else 1f
        shiftKey?.setColorFilter(if (shift == Shift.CAPS) p.accentLavender else p.keyText)
    }

    private fun commitChar(base: String) {
        val out = if (!symbols && shift != Shift.NONE) base.uppercase() else base
        actions.onKey(out)
        if (shift == Shift.SHIFT) { shift = Shift.NONE; applyShiftLabels() }
    }

    // ---------------- key-press pop-up preview ----------------

    /** What the magnified balloon should show for [base] given the current shift state. */
    private fun previewLabel(base: String): String =
        if (!symbols && shift != Shift.NONE) base.uppercase() else base

    /**
     * Show the preview on touch-down, hide on up/cancel. We return `false` so the key
     * keeps its own ripple + click — we're only peeking at the gesture, not consuming it.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachPreview(view: View, label: () -> String) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var downX = 0f
        var downY = 0f
        var pending: Runnable? = null
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    // Show the preview only after a brief settle. A swipe-to-hide moves fast and
                    // cancels it (below) before it appears — otherwise the popup window would
                    // interrupt the swipe gesture and break swipe-down-to-close.
                    pending = Runnable { showKeyPreview(v, label()) }
                    handler.postDelayed(pending!!, 45)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - downX) > dp(16) || kotlin.math.abs(e.rawY - downY) > dp(16)) {
                        pending?.let { handler.removeCallbacks(it) }
                        hideKeyPreview()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pending?.let { handler.removeCallbacks(it) }
                    hideKeyPreview()
                }
            }
            false
        }
    }

    private fun ensurePreviewPopup(): android.widget.PopupWindow {
        keyPreviewPopup?.let { return it }
        val tv = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(p.ink)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(p.surfaceHigh)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), p.divider)
            }
        }
        val popup = android.widget.PopupWindow(tv, dp(46), dp(54)).apply {
            isClippingEnabled = false    // allow drawing above the keyboard's own bounds
            isTouchable = false          // never intercept touches — let them reach the keys
            isFocusable = false
            animationStyle = 0
            elevation = dp(8).toFloat()
        }
        keyPreviewText = tv
        keyPreviewPopup = popup
        return popup
    }

    private fun showKeyPreview(anchor: View, label: String) {
        if (secureField || label.isBlank() || !isAttachedToWindow) return
        val popup = ensurePreviewPopup()
        keyPreviewText?.text = label
        val loc = IntArray(2); anchor.getLocationInWindow(loc)
        val w = dp(46); val h = dp(54)
        val x = loc[0] + (anchor.width - w) / 2
        val y = loc[1] - h - dp(4)       // float just above the pressed key
        runCatching {
            if (popup.isShowing) popup.update(x, y, w, h)
            else popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hideKeyPreview() { runCatching { keyPreviewPopup?.dismiss() } }

    override fun onDetachedFromWindow() {
        hideKeyPreview()                 // avoid a leaked PopupWindow when the keyboard tears down
        super.onDetachedFromWindow()
    }

    /**
     * The IME window can be HIDDEN without detaching the (reused) input view — e.g.
     * swipe-to-hide / requestHideSelf. Dismiss the preview then too, so a balloon from
     * the gesture's down-press doesn't linger over the app, and no popup is leaked.
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != View.VISIBLE) hideKeyPreview()
    }

    // ---------------- key builders ----------------

    private fun charKey(base: String): TextView = TextView(context).apply {
        text = base
        tag = base
        textSize = 17f
        setTextColor(p.keyText)
        gravity = Gravity.CENTER
        background = rippleKey(dp(6).toFloat(), p.surface)
        isClickable = true
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setOnClickListener { commitChar(base) }
        attachPreview(this) { previewLabel(base) }
        if (base.length == 1 && base[0].isLetter()) letterKeys.add(this)
    }

    /** A letter key with a tiny number hint at the top — Gboard's top row. */
    private fun hintKey(base: String, hint: String): View {
        val frame = FrameLayout(context).apply {
            background = rippleKey(dp(6).toFloat(), p.surface)
            isClickable = true
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setOnClickListener { commitChar(base) }
        }
        val letter = TextView(context).apply {
            text = base; tag = base; textSize = 17f
            setTextColor(p.keyText); gravity = Gravity.CENTER
        }
        val num = TextView(context).apply {
            text = hint; textSize = 9.5f; setTextColor(p.keyMuted); gravity = Gravity.CENTER
        }
        frame.addView(letter, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(
            num,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = dp(2) },
        )
        attachPreview(frame) { previewLabel(base) }
        letterKeys.add(letter)
        return frame
    }

    private fun spaceKey(): View = TextView(context).apply {
        text = "VibeFlow"
        setTextColor(p.keyMuted)
        textSize = 12f
        letterSpacing = 0.04f
        gravity = Gravity.CENTER
        background = rippleKey(dp(6).toFloat(), p.surface)
        isClickable = true
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setOnClickListener { actions.onSpace() }
    }

    private fun globeKey(): View {
        val iv = ImageView(context).apply {
            setImageResource(R.drawable.ic_language)
            imageTintList = ColorStateList.valueOf(p.keyText)
            background = rippleKey(dp(6).toFloat(), p.surfaceHigh)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            isClickable = true
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setOnClickListener { actions.onSwitchIme() }
            setOnLongClickListener { actions.onOpenSettings(); true }
        }
        return iv
    }

    private fun textFnKey(label: String, weight: Float, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(p.keyMuted)
        gravity = Gravity.CENTER
        background = rippleKey(dp(6).toFloat(), p.surfaceHigh)
        isClickable = true
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setOnClickListener { onClick() }
    }

    private fun iconFnKey(iconRes: Int, weight: Float, repeat: Boolean = false, onClick: () -> Unit): ImageView {
        val iv = ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(p.keyText)
            background = rippleKey(dp(6).toFloat(), p.surfaceHigh)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        if (repeat) attachRepeat(iv, onClick) else iv.setOnClickListener { onClick() }
        return iv
    }

    private fun keyRow(keys: List<View>): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        keys.forEach { row.addView(it, wkLp(1f)) }
        return row
    }

    private fun divider() = View(context).apply { setBackgroundColor(p.divider) }

    private fun attachRepeat(view: View, onClick: () -> Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var runnable: Runnable? = null
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onClick()
                    runnable = object : Runnable { override fun run() { onClick(); handler.postDelayed(this, 50) } }
                    handler.postDelayed(runnable!!, 360); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    runnable?.let { handler.removeCallbacks(it) }; v.performClick(); true
                }
                else -> false
            }
        }
    }

    // ---------------- drawables + dims ----------------

    private fun rippleKey(radius: Float, fill: Int): RippleDrawable {
        val cap = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = radius }
        // Opaque keys get a baked shadow lip (~2dp peeking at the bottom) for Gboard-like depth;
        // transparent items (suggestion slots, hide button) stay flat.
        val content: android.graphics.drawable.Drawable = if (Color.alpha(fill) == 0) {
            cap
        } else {
            val shadow = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(p.keyShadow); cornerRadius = radius }
            android.graphics.drawable.LayerDrawable(arrayOf(shadow, cap)).apply {
                setLayerInset(1, 0, 0, 0, dp(2))   // raise the cap so the shadow shows along the bottom edge
            }
        }
        val mask = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(Color.WHITE); cornerRadius = radius }
        return RippleDrawable(ColorStateList.valueOf(p.rippleColor), content, mask)
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun rowLp() = LinearLayout.LayoutParams(MATCH, dp(46)).also { it.topMargin = dp(3) }
    private fun wkLp(weight: Float) = LinearLayout.LayoutParams(0, dp(46), weight).also { it.leftMargin = dp(3); it.rightMargin = dp(3) }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
