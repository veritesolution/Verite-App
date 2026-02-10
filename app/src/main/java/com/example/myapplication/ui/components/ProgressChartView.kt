package com.example.myapplication.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ProgressChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progressData: List<Float> = emptyList()
    
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BFA5")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BFA5")
        style = Paint.Style.FILL
    }

    private val dotInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    fun setProgressData(data: List<Float>) {
        this.progressData = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progressData.size < 2) return

        val w = width.toFloat()
        val h = height.toFloat()
        val spacing = w / (progressData.size - 1)
        
        val points = progressData.mapIndexed { index, progress ->
            PointF(index * spacing, h - (progress * h))
        }

        // Draw fill
        val fillPath = Path()
        fillPath.moveTo(points.first().x, h)
        fillPath.lineTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            fillPath.lineTo(points[i].x, points[i].y)
        }
        fillPath.lineTo(points.last().x, h)
        fillPath.close()

        val gradient = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#4D00BFA5"),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient
        canvas.drawPath(fillPath, fillPaint)

        // Draw line
        val linePath = Path()
        linePath.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            linePath.lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(linePath, linePaint)

        // Draw dots
        points.forEach { point ->
            canvas.drawCircle(point.x, point.y, 10f, dotPaint)
            canvas.drawCircle(point.x, point.y, 5f, dotInnerPaint)
        }
    }
}
