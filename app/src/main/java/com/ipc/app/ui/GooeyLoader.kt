package com.ipc.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.BlurMaskFilter
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
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val colorStart = ContextCompat.getColor(context, R.color.colorPrimary)
    private val colorEnd   = Color.parseColor("#A999F6")

    private val centerRadius = 12f
    private val orbitRadius  = 20f

    private var angle = 0f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gradient = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(colorStart, colorEnd),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(bitmap)

        val positions = getOrbitPositions(angle, cx, cy)
        for (pos in positions) {
            bitmapCanvas.drawCircle(pos.first, pos.second, centerRadius, paint)
        }

        blurPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        val blurBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurBitmap)
        blurCanvas.drawBitmap(bitmap, 0f, 0f, blurPaint)

        val cm = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 20f, -10f
        ))
        drawPaint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(blurBitmap, 0f, 0f, drawPaint)

        bitmap.recycle()
        blurBitmap.recycle()
    }

    private fun getOrbitPositions(angleDeg: Float, cx: Float, cy: Float): List<Pair<Float, Float>> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val y1 = cy - orbitRadius + (orbitRadius * 2 * (kotlin.math.sin(rad).toFloat() + 1f) / 2f)
        val x2 = cx - orbitRadius + (orbitRadius * 2 * (kotlin.math.cos(rad).toFloat() + 1f) / 2f)
        val x3 = cx + orbitRadius - (orbitRadius * 2 * (kotlin.math.cos(rad).toFloat() + 1f) / 2f)
        val y4 = cy + orbitRadius - (orbitRadius * 2 * (kotlin.math.sin(rad).toFloat() + 1f) / 2f)

        return listOf(
            Pair(cx, y1),
            Pair(x2, cy),
            Pair(x3, cy),
            Pair(cx, y4)
        )
    }
}