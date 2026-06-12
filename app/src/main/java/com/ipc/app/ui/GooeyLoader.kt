package com.ipc.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GooeyLoader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cores do gradiente (primária e secundária)
    private val colorStart = ContextCompat.getColor(context, R.color.colorPrimary)
    private val colorEnd   = Color.parseColor("#A999F6")   // cor secundária

    private val centerRadius = 12f   // raio dos círculos
    private val orbitRadius  = 20f   // distância do centro para as órbitas

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
        // Define o gradiente linear do paint das bolhas
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

        // 1. Desenhar as bolhas num bitmap separado com blur
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(bitmap)

        val positions = getOrbitPositions(angle, cx, cy)
        for (pos in positions) {
            bitmapCanvas.drawCircle(pos.first, pos.second, centerRadius, paint)
        }

        // 2. Aplicar blur ao bitmap
        blurPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        val blurBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurBitmap)
        blurCanvas.drawBitmap(bitmap, 0f, 0f, blurPaint)

        // 3. Aplicar filtro de threshold (gooey) – aumentar contraste
        val cm = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 20f, -10f   // multiplica alpha por 20 e subtrai 10 → corta o blur
        ))
        drawPaint.colorFilter = ColorMatrixColorFilter(cm)

        // Desenhar o bitmap processado na tela
        canvas.drawBitmap(blurBitmap, 0f, 0f, drawPaint)

        // Limpar referências
        bitmap.recycle()
        blurBitmap.recycle()
    }

    private fun getOrbitPositions(angleDeg: Float, cx: Float, cy: Float): List<Pair<Float, Float>> {
        val rad = Math.toRadians(angleDeg.toDouble())
        // Quatro círculos que se movem em linhas ortogonais (como no CSS)
        // 1: move-se verticalmente de topo a baixo
        val y1 = cy - orbitRadius + (orbitRadius * 2 * (sin(rad).toFloat() + 1f) / 2f)
        // 2: move-se horizontalmente da esquerda para a direita
        val x2 = cx - orbitRadius + (orbitRadius * 2 * (cos(rad).toFloat() + 1f) / 2f)
        // 3: move-se horizontalmente da direita para a esquerda (oposto ao 2)
        val x3 = cx + orbitRadius - (orbitRadius * 2 * (cos(rad).toFloat() + 1f) / 2f)
        // 4: move-se verticalmente de baixo para cima (oposto ao 1)
        val y4 = cy + orbitRadius - (orbitRadius * 2 * (sin(rad).toFloat() + 1f) / 2f)

        return listOf(
            Pair(cx, y1),
            Pair(x2, cy),
            Pair(x3, cy),
            Pair(cx, y4)
        )
    }
}