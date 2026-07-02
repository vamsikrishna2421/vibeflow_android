package com.vibeflow.mobile.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.Choreographer
import android.view.View
import kotlin.math.sin

/**
 * A live, voice-reactive waveform. A single loudness value (0..1) drives an
 * "equalizer" of mirrored bars whose heights undulate with time and the current
 * amplitude — lively while you speak, gently resting in silence. Driven by a
 * Choreographer frame loop for buttery 60 fps motion.
 */
class WaveformView(context: Context) : View(context) {

    private val barCount = 27
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var colorLow = 0xFF8B85F0.toInt()
    private var colorHigh = 0xFF5B54E6.toInt()

    private var targetAmp = 0f
    private var amp = 0f
    private var active = false
    private var startNanos = 0L
    private var running = false

    // Per-bar envelope + phase so the row reads like a waveform, not a meter.
    private val envelope = FloatArray(barCount) { i ->
        val t = i / (barCount - 1f)
        0.45f + 0.55f * sin(Math.PI * t).toFloat()   // tall in the middle, short at edges
    }
    private val phase = FloatArray(barCount) { i -> i * 0.7f }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (startNanos == 0L) startNanos = frameTimeNanos
            // ease the amplitude toward its target for fluid response
            amp += (targetAmp - amp) * 0.25f
            if (!active) targetAmp = 0f
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) start()
    }

    /** Push a fresh loudness sample (0..1). */
    fun setLevel(level: Float) {
        targetAmp = level.coerceIn(0f, 1f)
    }

    private fun start() {
        if (running) return
        running = true
        startNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        barPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colorHigh, colorLow, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val cy = h / 2f
        val gap = dp(3f)
        val barW = (w - gap * (barCount - 1)) / barCount
        val radius = barW / 2f
        val maxHalf = h / 2f - dp(2f)
        val tSec = if (startNanos == 0L) 0f else (System.nanoTime() - startNanos) / 1_000_000_000f

        for (i in 0 until barCount) {
            val undulate = 0.5f + 0.5f * sin(tSec * 7f + phase[i])
            // baseline keeps a faint idle pulse; amplitude lifts the bars while speaking
            val idle = 0.06f + 0.03f * sin(tSec * 2.2f + i * 0.5f)
            val frac = (idle + amp * envelope[i] * (0.35f + 0.65f * undulate)).coerceIn(0.02f, 1f)
            val half = maxHalf * frac
            val left = i * (barW + gap)
            rect.set(left, cy - half, left + barW, cy + half)
            canvas.drawRoundRect(rect, radius, radius, barPaint)
        }
    }

    fun setColors(high: Int, low: Int) {
        colorHigh = high; colorLow = low
        if (width > 0) onSizeChanged(width, height, width, height)
        invalidate()
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
