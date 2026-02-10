package com.example.myapplication.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SleepChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class SleepStage(val level: Int, val color: Int) {
        AWAKE(0, Color.parseColor("#FFD54F")), // Yellow
        REM(1, Color.parseColor("#00BFA5")),   // Teal
        LIGHT(2, Color.parseColor("#4DB6AC")), // Lighter Teal
        DEEP(3, Color.parseColor("#00695C"))   // Dark Teal
    }

    data class SleepDataPoint(val timestamp: Long, val stage: SleepStage)

    private var data: List<SleepDataPoint> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.RIGHT
    }

    fun setData(newData: List<SleepDataPoint>) {
        this.data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val paddingLeft = 120f
        val paddingBottom = 40f
        val chartWidth = w - paddingLeft
        val chartHeight = h - paddingBottom
        
        val stageHeight = chartHeight / 4
        
        // Draw Y-Axis Labels
        SleepStage.values().forEach { stage ->
            val y = stage.level * stageHeight + stageHeight / 2
            canvas.drawText(stage.name, paddingLeft - 20f, y + 10f, textPaint)
        }

        // Calculate points
        val timeRange = data.last().timestamp - data.first().timestamp
        val points = data.map { point ->
            val x = paddingLeft + ((point.timestamp - data.first().timestamp).toFloat() / timeRange * chartWidth)
            val y = point.stage.level * stageHeight + stageHeight / 2
            PointF(x, y)
        }

        // Draw Fill Area
        val fillPath = Path()
        fillPath.moveTo(points.first().x, chartHeight)
        points.forEach { fillPath.lineTo(it.x, it.y) }
        fillPath.lineTo(points.last().x, chartHeight)
        fillPath.close()

        val gradient = LinearGradient(0f, 0f, 0f, h, 
            Color.parseColor("#4D00BFA5"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        fillPaint.shader = gradient
        canvas.drawPath(fillPath, fillPaint)

        // Draw Line with varying segments or single color for now
        linePaint.color = Color.parseColor("#00BFA5")
        val linePath = Path()
        linePath.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            // Using step-like line for hypnogram
            linePath.lineTo(points[i].x, points[i-1].y)
            linePath.lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(linePath, linePaint)
    }
}
