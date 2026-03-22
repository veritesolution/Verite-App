package com.example.myapplication.ui.components

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/**
 * A custom view that draws an animated sine-wave to simulate a real-time bio signal.
 * No external libraries needed.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BFA5")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3000BFA5")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0FFFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val wavePath = Path()
    private val glowPath = Path()
    private var phase = 0f
    private var amplitude = 0.35f
    private var frequency = 2.0f

    private val handler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            phase += 0.08f
            // Subtle random variation for realism
            amplitude = 0.30f + (Math.random() * 0.12f).toFloat()
            invalidate()
            handler.postDelayed(this, 32) // ~30fps
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(animRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f

        // Draw subtle horizontal grid lines
        canvas.drawLine(0f, midY - h * 0.3f, w, midY - h * 0.3f, gridPaint)
        canvas.drawLine(0f, midY, w, midY, gridPaint)
        canvas.drawLine(0f, midY + h * 0.3f, w, midY + h * 0.3f, gridPaint)

        // Build wave path
        wavePath.reset()
        glowPath.reset()

        val steps = width * 2
        for (i in 0..steps) {
            val x = i / steps.toFloat() * w
            val t = (i / steps.toFloat()) * Math.PI.toFloat() * 2f * frequency
            val y = midY + sin((t + phase).toDouble()).toFloat() * h * amplitude

            if (i == 0) {
                wavePath.moveTo(x, y)
                glowPath.moveTo(x, y)
            } else {
                wavePath.lineTo(x, y)
                glowPath.lineTo(x, y)
            }
        }

        // Draw glow then sharp wave on top
        canvas.drawPath(glowPath, glowPaint)
        canvas.drawPath(wavePath, wavePaint)
    }
}
