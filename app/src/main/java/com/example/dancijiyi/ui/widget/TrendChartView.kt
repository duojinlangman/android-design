package com.example.dancijiyi.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.dancijiyi.R
import com.example.dancijiyi.data.DayPoint

/** 近 7 天学习趋势的柱状图（复习次数 + 新增单词） */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<DayPoint> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.brand_mid)
    }
    private val bar2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.divider)
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_tertiary)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setData(data: List<DayPoint>) {
        points = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        labelPaint.textSize = 11f * resources.displayMetrics.scaledDensity
        valuePaint.textSize = 10f * resources.displayMetrics.scaledDensity
        val labelSpace = labelPaint.textSize + 10f
        val topSpace = valuePaint.textSize + 8f
        val bottom = height - labelSpace
        val top = topSpace
        val chartHeight = bottom - top

        val maxValue = (points.maxOfOrNull { maxOf(it.reviewed, it.learned) } ?: 0).coerceAtLeast(1)

        canvas.drawLine(0f, bottom, width.toFloat(), bottom, axisPaint)

        val slotWidth = width / points.size.toFloat()
        val barWidth = slotWidth * 0.26f
        points.forEachIndexed { index, point ->
            val centerX = slotWidth * index + slotWidth / 2f

            val h1 = chartHeight * point.reviewed / maxValue
            val h2 = chartHeight * point.learned / maxValue

            if (point.reviewed == 0 && point.learned == 0) {
                canvas.drawCircle(centerX, bottom - 6f, 2.5f, axisPaint)
            } else {
                rect.set(centerX - barWidth - 2f, bottom - h1, centerX - 2f, bottom)
                canvas.drawRoundRect(rect, 4f, 4f, barPaint)

                rect.set(centerX + 2f, bottom - h2, centerX + barWidth + 2f, bottom)
                canvas.drawRoundRect(rect, 4f, 4f, bar2Paint)

                val topValue = maxOf(point.reviewed, point.learned)
                canvas.drawText(
                    topValue.toString(),
                    centerX,
                    bottom - maxOf(h1, h2) - 5f,
                    valuePaint
                )
            }

            canvas.drawText(point.label, centerX, height - 4f, labelPaint)
        }
    }
}
