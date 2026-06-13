package com.ipc.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.ipc.app.R

class GooeyLoader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private val barCount = 3
    private val barHeightDp = 3.5f
    private val barSpacingDp = 7f
    private val barRadiusDp = 4f
    private val widths = floatArrayOf(0.6f, 0.45f, 0.3f)

    private val colorPrimary by lazy { ContextCompat.getColor(context, R.color.colorPrimary) }
    private val colorFade by lazy { ContextCompat.getColor(context, R.color.text_hint) }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val barH = barHeightDp * density
        val spacing = barSpacingDp * density
        val radius = barRadiusDp * density
        val w = width.toFloat()

        val totalH = barCount * barH + (barCount - 1) * spacing
        var y = (height - totalH) / 2f

        for (i in 0 until barCount) {
            val barW = w * widths[i]

            // shimmer offset por linha com delay escalonado
            val offset = (progress + i * 0.18f) % 1f
            val shimmerX = -barW + offset * (barW * 2.4f)

            paint.shader = LinearGradient(
                shimmerX, 0f,
                shimmerX + barW * 0.8f, 0f,
                intArrayOf(colorFade, colorPrimary, colorFade),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )

            val rect = RectF(0f, y, barW, y + barH)
            canvas.drawRoundRect(rect, radius, radius, paint)

            y += barH + spacing
        }
    }
}