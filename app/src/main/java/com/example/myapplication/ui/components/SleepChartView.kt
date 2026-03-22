package com.example.myapplication.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

class SleepChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class SleepStage(val level: Int, val label: String) {
        AWAKE(0, "Awake"),
        REM(1, "REM"),
        LIGHT(2, "Light"),
        DEEP(3, "Deep")
    }

    data class SleepDataPoint(val timestamp: Long, val stage: SleepStage)

    private var data: List<SleepDataPoint> = emptyList()

    private val accentColor = Color.parseColor("#1C9C91")
    private val brightAccent = Color.parseColor("#2DD4AA")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = accentColor
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1C9C91") // Very faint teal
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80B4D2CD") // Muted teal-grey
        textSize = 28f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setData(newData: List<SleepDataPoint>) {
        this.data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.size < 2) return

        val w = width.toFloat()
        val h = height.toFloat()
        val marginLeft = 140f
        val marginBottom = 60f
        val marginTop = 40f
        val marginRight = 40f
        
        val chartWidth = w - marginLeft - marginRight
        val chartHeight = h - marginBottom - marginTop
        
        val stageHeight = chartHeight / 3 // 0 to 3 levels
        
        // Draw Horizontal Grid & Labels
        SleepStage.values().forEach { stage ->
            val y = marginTop + stage.level * stageHeight
            canvas.drawLine(marginLeft, y, w - marginRight, y, gridPaint)
            canvas.drawText(stage.label, marginLeft - 20f, y + 10f, textPaint)
        }

        // Calculate points
        val timeStart = data.first().timestamp
        val timeRange = data.last().timestamp - timeStart
        
        val points = data.map { point ->
            val x = marginLeft + ((point.timestamp - timeStart).toFloat() / timeRange * chartWidth)
            val y = marginTop + point.stage.level * stageHeight
            PointF(x, y)
        }

        // Draw Vertical Time Grid (every 2 hours approx)
        val timePoints = 4
        for (i in 0..timePoints) {
            val x = marginLeft + (i.toFloat() / timePoints * chartWidth)
            canvas.drawLine(x, marginTop, x, marginTop + chartHeight, gridPaint)
            
            val timeLabel = timeFormat.format(Date(timeStart + (i.toFloat() / timePoints * timeRange).toLong()))
            val oldAlign = textPaint.textAlign
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(timeLabel, x, h - 10f, textPaint)
            textPaint.textAlign = oldAlign
        }

        // Draw Path (Smoothing with Cubic)
        val linePath = Path()
        val fillPath = Path()
        
        linePath.moveTo(points[0].x, points[0].y)
        fillPath.moveTo(points[0].x, marginTop + chartHeight)
        fillPath.lineTo(points[0].x, points[0].y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            
            // Cubic interpolation for smoothness
            val conX1 = p0.x + (p1.x - p0.x) / 2
            val conY1 = p0.y
            val conX2 = p0.x + (p1.x - p0.x) / 2
            val conY2 = p1.y
            
            linePath.cubicTo(conX1, conY1, conX2, conY2, p1.x, p1.y)
            fillPath.cubicTo(conX1, conY1, conX2, conY2, p1.x, p1.y)
        }

        fillPath.lineTo(points.last().x, marginTop + chartHeight)
        fillPath.close()

        // Apply Premium Gradient to Fill
        val gradient = LinearGradient(0f, marginTop, 0f, marginTop + chartHeight, 
            intArrayOf(Color.parseColor("#4D1C9C91"), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        fillPaint.shader = gradient
        canvas.drawPath(fillPath, fillPaint)

        // Draw Accent Line
        canvas.drawPath(linePath, linePaint)
        
        // Draw Points (markers)
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = brightAccent
        }
        val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
        }
        
        points.forEach { point ->
            canvas.drawCircle(point.x, point.y, 6f, markerPaint)
            canvas.drawCircle(point.x, point.y, 6f, markerStroke)
        }
    }
}
