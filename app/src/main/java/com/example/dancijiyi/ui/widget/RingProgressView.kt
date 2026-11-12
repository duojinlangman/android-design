package com.example.dancijiyi.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.dancijiyi.R

/** 环形掌握进度 */
class RingProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var percent: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.divider)
        style = Paint.Style.STROKE
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.brand_mid)
        style = Paint.Style.STROKE
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val stroke = (w.coerceAtMost(h) * 0.10f).coerceAtLeast(6f)
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        val inset = stroke / 2f + 2f
        rect.set(inset, inset, w - inset, h - inset)
        valuePaint.textSize = w * 0.22f
        labelPaint.textSize = w * 0.11f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        canvas.drawArc(rect, -90f, 360f * percent / 100f, false, arcPaint)

        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText("$percent%", cx, cy + valuePaint.textSize * 0.22f, valuePaint)
        canvas.drawText("已掌握", cx, cy + valuePaint.textSize * 0.22f + labelPaint.textSize + 8f, labelPaint)
    }
}
