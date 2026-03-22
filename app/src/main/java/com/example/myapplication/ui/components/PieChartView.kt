package com.example.myapplication.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val slices = mutableListOf<Slice>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.BLACK
    }
    private val rectF = RectF()

    data class Slice(val value: Float, val color: Int)

    fun setSlices(newSlices: List<Slice>) {
        slices.clear()
        slices.addAll(newSlices)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) return

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return

        val width = width.toFloat()
        val height = height.toFloat()
        val minDimen = minOf(width, height)
        val radius = minDimen / 2f
        val cx = width / 2f
        val cy = height / 2f

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        var startAngle = -90f // Start from top
        for (slice in slices) {
            val sweepAngle = (slice.value / total) * 360f
            
            // Draw slice
            paint.color = slice.color
            paint.style = Paint.Style.FILL
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            
            // Draw stroke outline
            canvas.drawArc(rectF, startAngle, sweepAngle, true, strokePaint)

            startAngle += sweepAngle
        }
    }
}
