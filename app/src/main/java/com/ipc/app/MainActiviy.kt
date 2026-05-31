package com.ipc.app

import android.graphics.*
import android.graphics.drawable.*
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActiviy : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        val root = buildRoot()
        setContentView(root)
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density + 0.5f).toInt()

    // ─── ROOT ────────────────────────────────────────────────────────────────
    private fun buildRoot(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Content area (top bar + empty state)
        val content = buildContent()
        root.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Bottom nav + input (overlay at bottom)
        val bottomSection = buildBottomSection()
        root.addView(bottomSection, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        return root
    }

    // ─── CONTENT (top bar + empty state) ─────────────────────────────────────
    private fun buildContent(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        container.addView(buildTopBar())
        container.addView(buildEmptyState(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        return container
    }

    // ─── TOP BAR ─────────────────────────────────────────────────────────────
    private fun buildTopBar(): View {
        val bar = FrameLayout(this).apply {
            setPadding(dp(16f), dp(52f), dp(16f), dp(8f))
        }

        // Left icons (≡ and ···)
        val leftIcons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        leftIcons.addView(makeIconButton(makeHamburgerIcon()))
        leftIcons.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4f), 1)
        })
        leftIcons.addView(makeIconButton(makeDotsIcon()))

        bar.addView(leftIcons, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL
        ))

        // Right: "Compound" pill button
        val compound = TextView(this).apply {
            text = "Compound"
            textSize = 14f
            setTextColor(Color.parseColor("#E0E0E0"))
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            background = pillBackground(Color.parseColor("#2A2A2A"), dp(50f).toFloat())
            gravity = Gravity.CENTER
        }

        bar.addView(compound, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ))

        return bar
    }

    // ─── EMPTY STATE ─────────────────────────────────────────────────────────
    private fun buildEmptyState(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // Chat icon (two overlapping speech bubbles outline)
        val iconView = View(this).apply {
            val size = dp(72f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = makeChatBubblesIcon(size)
        }
        container.addView(iconView)

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(16f))
        })

        val label = TextView(this).apply {
            text = "Começa uma conversa"
            textSize = 16f
            setTextColor(Color.parseColor("#808080"))
            gravity = Gravity.CENTER
        }
        container.addView(label)

        return container
    }

    // ─── BOTTOM SECTION (input + nav) ────────────────────────────────────────
    private fun buildBottomSection(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16f), dp(8f), dp(16f), 0)
        }

        container.addView(buildInputBar())

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(8f))
        })

        container.addView(buildBottomNav())

        return container
    }

    // ─── INPUT BAR ───────────────────────────────────────────────────────────
    private fun buildInputBar(): View {
        val bar = FrameLayout(this).apply {
            background = pillBackground(Color.parseColor("#1C1C1C"), dp(30f).toFloat())
            setPadding(dp(20f), dp(4f), dp(8f), dp(4f))
        }

        val input = EditText(this).apply {
            hint = "Pergunta algo..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 16f
            background = null
            setSingleLine(true)
        }
        bar.addView(input, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.START
        ).apply { marginEnd = dp(52f) })

        // Send button (white circle with arrow)
        val sendBtn = FrameLayout(this).apply {
            val size = dp(44f)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.CENTER_VERTICAL)
            background = circleBackground(Color.WHITE)
            isClickable = true
            isFocusable = true
            foreground = rippleDrawable()
        }

        val arrow = View(this).apply {
            val s = dp(22f)
            layoutParams = FrameLayout.LayoutParams(s, s, Gravity.CENTER)
            background = makeArrowIcon(s, Color.BLACK)
        }
        sendBtn.addView(arrow)
        bar.addView(sendBtn)

        return bar
    }

    // ─── BOTTOM NAV ──────────────────────────────────────────────────────────
    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, dp(28f))
        }

        val chatTab = buildNavTab("Chat", makeChatNavIcon(true), true)
        val previewTab = buildNavTab("Preview", makePreviewNavIcon(false), false)

        nav.addView(chatTab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(previewTab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        return nav
    }

    private fun buildNavTab(label: String, icon: Drawable, active: Boolean): View {
        val tab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, dp(4f))
            isClickable = true
            isFocusable = true
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(icon)
            val size = dp(26f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        tab.addView(iconView)

        tab.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(3f))
        })

        val text = TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(if (active) Color.WHITE else Color.parseColor("#666666"))
        }
        tab.addView(text)

        return tab
    }

    // ─── ICON FACTORIES ──────────────────────────────────────────────────────

    private fun makeIconButton(icon: Drawable): ImageView {
        return ImageView(this).apply {
            setImageDrawable(icon)
            val size = dp(38f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
    }

    private fun makeHamburgerIcon(): Drawable {
        val size = dp(24f)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = dp(1.8f).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        val lineW = dp(18f).toFloat()
        val startX = dp(3f).toFloat()
        // 3 lines (top two lines like ≡ but compact)
        canvas.drawLine(startX, dp(7f).toFloat(), startX + lineW, dp(7f).toFloat(), paint)
        canvas.drawLine(startX, dp(12f).toFloat(), startX + lineW * 0.7f, dp(12f).toFloat(), paint)
        return BitmapDrawable(resources, bmp)
    }

    private fun makeDotsIcon(): Drawable {
        val size = dp(24f)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val r = dp(2f).toFloat()
        val y = size / 2f
        canvas.drawCircle(dp(4f).toFloat(), y, r, paint)
        canvas.drawCircle(dp(12f).toFloat(), y, r, paint)
        canvas.drawCircle(dp(20f).toFloat(), y, r, paint)
        return BitmapDrawable(resources, bmp)
    }

    private fun makeChatBubblesIcon(size: Int): Drawable {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666")
            style = Paint.Style.STROKE
            strokeWidth = size * 0.055f
        }

        // Back bubble (bottom-right)
        val back = RectF(size * 0.28f, size * 0.28f, size * 0.90f, size * 0.82f)
        val r = size * 0.18f
        canvas.drawRoundRect(back, r, r, paint)

        // Front bubble (top-left)
        val front = RectF(size * 0.08f, size * 0.08f, size * 0.70f, size * 0.62f)
        canvas.drawRoundRect(front, r, r, paint)

        return BitmapDrawable(resources, bmp)
    }

    private fun makeArrowIcon(size: Int, color: Int): Drawable {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = size * 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        val cx = size / 2f
        val cy = size / 2f
        val arm = size * 0.28f
        // Vertical stem
        canvas.drawLine(cx, cy + arm, cx, cy - arm, paint)
        // Left arrow head
        canvas.drawLine(cx - arm * 0.7f, cy - arm * 0.3f, cx, cy - arm, paint)
        // Right arrow head
        canvas.drawLine(cx + arm * 0.7f, cy - arm * 0.3f, cx, cy - arm, paint)
        return BitmapDrawable(resources, bmp)
    }

    private fun makeChatNavIcon(active: Boolean): Drawable {
        val size = dp(26f)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val color = if (active) Color.WHITE else Color.parseColor("#666666")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = if (active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            strokeWidth = size * 0.06f
        }
        val back = RectF(size * 0.28f, size * 0.28f, size * 0.90f, size * 0.82f)
        val r = size * 0.18f
        if (!active) canvas.drawRoundRect(back, r, r, paint)
        val front = RectF(size * 0.08f, size * 0.08f, size * 0.70f, size * 0.62f)
        if (active) {
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(front, r, r, paint)
        } else {
            canvas.drawRoundRect(front, r, r, paint)
        }
        return BitmapDrawable(resources, bmp)
    }

    private fun makePreviewNavIcon(active: Boolean): Drawable {
        val size = dp(26f)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val color = if (active) Color.WHITE else Color.parseColor("#666666")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = size * 0.07f
        }
        // Two overlapping squares (copy icon)
        val back = RectF(size * 0.30f, size * 0.24f, size * 0.88f, size * 0.82f)
        val r = size * 0.12f
        canvas.drawRoundRect(back, r, r, paint)
        val front = RectF(size * 0.12f, size * 0.12f, size * 0.70f, size * 0.70f)
        canvas.drawRoundRect(front, r, r, paint)
        return BitmapDrawable(resources, bmp)
    }

    // ─── SHAPE HELPERS ───────────────────────────────────────────────────────

    private fun pillBackground(color: Int, radius: Float): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun circleBackground(color: Int): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun rippleDrawable(): Drawable {
        return RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
            null,
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        )
    }
}