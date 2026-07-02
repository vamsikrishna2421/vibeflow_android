package com.vibeflow.mobile.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.vibeflow.mobile.R

/**
 * A tactile, animated mic button: a filled brand circle that turns red and emits
 * concentric "breathing" pulse rings while listening, a mic glyph that becomes a
 * stop square, and a gentle press-scale. All interaction (tap / hold) lives here
 * so the keyboard just wires callbacks.
 */
class MicButton(context: Context) : View(context) {

    var onTap: () -> Unit = {}
    var onPressStart: () -> Unit = {}
    var onPressEnd: () -> Unit = {}
    var pushToTalk: Boolean = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val stopRect = RectF()
    private val arcRect = RectF()

    private var brand = 0xFF5B54E6.toInt()
    private var recordRed = 0xFFE5484D.toInt()

    private val micIcon = ContextCompat.getDrawable(context, R.drawable.ic_mic)

    /** Theme the button: [idle] fill, [recording] fill, and glyph [tint]. */
    fun setColors(idle: Int, recording: Int, tint: Int) {
        brand = idle
        recordRed = recording
        glyph.color = tint
        micIcon?.setTint(tint)
        invalidate()
    }

    private var listening = false
    private var processing = false
    private var pulse = 0f
    private var spin = 0f
    private var pressed = false

    private val pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }

    // A continuous rotation that drives the "transcribing/working" spinner arc.
    private val spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { spin = it.animatedValue as Float; invalidate() }
    }

    fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        if (value) pulseAnim.start() else pulseAnim.cancel()
        invalidate()
    }

    /** PROCESSING (post-stop): Whisper transcription / AI wait. A spinning arc, NOT the red pulse,
     *  so it's visually obvious recording has stopped and we're working on the result. */
    fun setProcessing(value: Boolean) {
        if (processing == value) return
        processing = value
        if (value) spinAnim.start() else spinAnim.cancel()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                if (pushToTalk) onPressStart()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressed) {
                    pressed = false
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val inside = event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                    if (pushToTalk) onPressEnd()
                    else if (event.action == MotionEvent.ACTION_UP && inside) { performClick(); onTap() }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - dp(10f)

        // PROCESSING wins over everything: brand circle + a single rotating arc + a dimmed mic.
        if (processing) {
            fill.color = brand
            canvas.drawCircle(cx, cy, r, fill)
            ring.style = Paint.Style.STROKE
            ring.strokeWidth = dp(3f)
            ring.color = 0xFFFFFFFF.toInt()
            ring.alpha = 235
            val ar = r * 0.74f
            arcRect.set(cx - ar, cy - ar, cx + ar, cy + ar)
            canvas.drawArc(arcRect, spin, 96f, false, ring)
            val ic = micIcon
            if (ic != null) {
                val s = (r * 0.9f).toInt()
                ic.alpha = 110
                ic.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
                ic.draw(canvas)
                ic.alpha = 255
            }
            return
        }

        // breathing pulse rings while listening
        if (listening) {
            for (k in 0..1) {
                val p = ((pulse + k * 0.5f) % 1f)
                val rr = r * (1f + 0.55f * p)
                ring.color = recordRed
                ring.alpha = (90 * (1f - p)).toInt().coerceIn(0, 255)
                ring.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, rr, ring)
            }
        }

        fill.color = if (listening) recordRed else brand
        canvas.drawCircle(cx, cy, r, fill)

        if (listening) {
            val s = r * 0.46f
            stopRect.set(cx - s, cy - s, cx + s, cy + s)
            canvas.drawRoundRect(stopRect, dp(6f), dp(6f), glyph)
        } else {
            val ic = micIcon
            if (ic != null) {
                val s = (r * 1.1f).toInt()
                ic.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
                ic.draw(canvas)
            }
        }
    }

    private fun dp(v: Float): Float =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
        )
}
