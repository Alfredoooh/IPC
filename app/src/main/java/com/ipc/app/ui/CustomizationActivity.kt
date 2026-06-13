// CustomizationActivity.kt
package com.ipc.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.caverock.androidsvg.SVG
import com.ipc.app.R

class CustomizationActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Conteúdo
        val contentWrapper = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams   = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val scrollContent = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Espaçador para a appbar transparente
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)
            )
        }
        scrollContent.addView(spacer)

        // Placeholder — aqui entrarão as opções de personalização futuramente
        scrollContent.addView(TextView(this).apply {
            text    = "Personalização"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(32); it.bottomMargin = dp(8) }
        })

        scrollContent.addView(TextView(this).apply {
            text     = "Opções de personalização em breve."
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity  = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = dp(32); it.marginEnd = dp(32) }
        })

        scroll.addView(scrollContent)
        contentWrapper.addView(scroll)
        root.addView(contentWrapper)

        // AppBar transparente flutuante
        root.addView(buildTransparentAppBar("Personalização", spacer))

        setContentView(root)
    }

    private fun buildTransparentAppBar(title: String, spacer: View): LinearLayout {
        val iconTint  = ContextCompat.getColor(this, R.color.icon_tint)
        val solidColor = ContextCompat.getColor(this, R.color.appbar_solid)

        val appBarWrapper = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(solidColor, solidColor, Color.TRANSPARENT)
            )
        }

        val inner = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(
                    androidx.appcompat.R.dimen.abc_action_bar_default_height_material
                )
            )
            setPadding(dp(4), 0, dp(8), 0)
        }

        val btnBack = ImageView(this).apply {
            val size = dp(40)
            layoutParams = RelativeLayout.LayoutParams(size, size).also {
                it.addRule(RelativeLayout.ALIGN_PARENT_START)
                it.addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setPadding(dp(11), dp(11), dp(11), dp(11))
            val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
            background = a.getDrawable(0)
            a.recycle()
            setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", 12, iconTint))
            setOnClickListener { finish() }
        }

        val titleTv = TextView(this).apply {
            text     = title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).also { it.addRule(RelativeLayout.CENTER_IN_PARENT) }
            runCatching {
                val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
                typeface = Typeface.create(tf, Typeface.BOLD)
            }
        }

        inner.addView(btnBack)
        inner.addView(titleTv)
        appBarWrapper.addView(inner)

        // Cauda gradiente transparente
        appBarWrapper.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)
            )
        })

        // Ajusta padding de status bar
        ViewCompat.setOnApplyWindowInsetsListener(appBarWrapper) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            inner.updatePadding(top = sb.top)
            spacer.layoutParams = spacer.layoutParams.also { lp ->
                lp.height = sb.top + resources.getDimensionPixelSize(
                    androidx.appcompat.R.dimen.abc_action_bar_default_height_material
                ) + dp(24)
            }
            insets
        }

        return appBarWrapper
    }

    private fun svgDrawable(path: String, sizeDp: Int, tint: Int): BitmapDrawable {
        val px  = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        runCatching {
            SVG.getFromAsset(assets, path).apply {
                documentWidth  = px.toFloat()
                documentHeight = px.toFloat()
                renderToCanvas(Canvas(bmp))
            }
        }
        return BitmapDrawable(resources, bmp).also { it.setColorFilter(tint, PorterDuff.Mode.SRC_IN) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}