// AboutActivity.kt
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

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

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
            setPadding(dp(16), 0, dp(16), dp(32))
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)
            )
        }
        scrollContent.addView(spacer)

        // ── Cabeçalho com logo/nome da app ──────────────────────────────
        scrollContent.addView(TextView(this).apply {
            text     = "IPC"
            textSize = 36f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            gravity  = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(24); it.bottomMargin = dp(4) }
            runCatching {
                val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
                typeface = Typeface.create(tf, Typeface.BOLD)
            }
        })

        scrollContent.addView(TextView(this).apply {
            text     = "Versão 1.0.0"
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity  = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(32) }
        })

        // ── Divisor ─────────────────────────────────────────────────────
        scrollContent.addView(divider())

        // ── Secção de itens legais ───────────────────────────────────────
        scrollContent.addView(sectionLabel("Legal"))

        val legalGroup = buildLegalGroup(
            listOf(
                "Termos de Uso"            to "terms",
                "Política de Privacidade"  to "privacy",
                "Licenças de terceiros"    to "licenses"
            )
        )
        scrollContent.addView(legalGroup)

        // ── Secção de informações ────────────────────────────────────────
        scrollContent.addView(sectionLabel("Informações"))

        val infoGroup = buildInfoGroup(
            listOf(
                "Versão"          to "1.0.0",
                "Build"           to "100",
                "Identificador"   to "com.ipc.app"
            )
        )
        scrollContent.addView(infoGroup)

        // ── Rodapé ──────────────────────────────────────────────────────
        scrollContent.addView(TextView(this).apply {
            text     = "© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} IPC. Todos os direitos reservados."
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity  = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(32) }
        })

        scroll.addView(scrollContent)
        contentWrapper.addView(scroll)
        root.addView(contentWrapper)
        root.addView(buildTransparentAppBar("Sobre", spacer))

        setContentView(root)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Grupo legal — itens clicáveis com card iOS
    // ─────────────────────────────────────────────────────────────────────
    private fun buildLegalGroup(items: List<Pair<String, String>>): LinearLayout {
        val iconTint   = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        val cardColor  = ContextCompat.getColor(this, R.color.card_background)
        val strong     = dp(22f)
        val soft       = dp(6f)

        val group = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            clipToOutline  = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        val total = items.size

        items.forEachIndexed { idx, (label, _) ->
            // Separador entre rows
            if (idx > 0) {
                group.addView(View(this).apply {
                    tag = "divider"
                    setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.marginStart = dp(16) }
                })
            }

            val row = LinearLayout(this).apply {
                orientation   = LinearLayout.HORIZONTAL
                gravity       = Gravity.CENTER_VERTICAL
                layoutParams  = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
                )
                setPadding(dp(16), 0, dp(16), 0)
                isClickable   = true
                isFocusable   = true

                val radii: FloatArray = when {
                    total == 1       -> floatArrayOf(strong, strong, strong, strong, strong, strong, strong, strong)
                    idx == 0         -> floatArrayOf(strong, strong, strong, strong, soft,   soft,   soft,   soft)
                    idx == total - 1 -> floatArrayOf(soft,   soft,   soft,   soft,   strong, strong, strong, strong)
                    else             -> floatArrayOf(soft,   soft,   soft,   soft,   soft,   soft,   soft,   soft)
                }
                val shape = GradientDrawable().apply {
                    setColor(cardColor); cornerRadii = radii
                }
                val rippleColor = android.content.res.ColorStateList.valueOf(
                    if (isDarkTheme()) 0x33FFFFFF else 0x1F000000
                )
                background = android.graphics.drawable.RippleDrawable(rippleColor, shape, shape)
            }

            row.addView(TextView(this).apply {
                text     = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(ImageView(this).apply {
                setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 15, iconTint))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })

            // Por agora sem navegação — podes adicionar WebView/Activity depois
            row.setOnClickListener { }

            group.addView(row)
        }

        return group
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Grupo info — label + valor, sem clique
    // ─────────────────────────────────────────────────────────────────────
    private fun buildInfoGroup(items: List<Pair<String, String>>): LinearLayout {
        val cardColor = ContextCompat.getColor(this, R.color.card_background)
        val strong    = dp(22f)
        val soft      = dp(6f)
        val total     = items.size

        val group = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        items.forEachIndexed { idx, (label, value) ->
            if (idx > 0) {
                group.addView(View(this).apply {
                    tag = "divider"
                    setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.marginStart = dp(16) }
                })
            }

            val row = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                gravity      = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
                )
                setPadding(dp(16), 0, dp(16), 0)

                val radii: FloatArray = when {
                    total == 1       -> floatArrayOf(strong, strong, strong, strong, strong, strong, strong, strong)
                    idx == 0         -> floatArrayOf(strong, strong, strong, strong, soft,   soft,   soft,   soft)
                    idx == total - 1 -> floatArrayOf(soft,   soft,   soft,   soft,   strong, strong, strong, strong)
                    else             -> floatArrayOf(soft,   soft,   soft,   soft,   soft,   soft,   soft,   soft)
                }
                background = GradientDrawable().apply {
                    setColor(cardColor); cornerRadii = radii
                }
            }

            row.addView(TextView(this).apply {
                text     = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(TextView(this).apply {
                text     = value
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            })

            group.addView(row)
        }

        return group
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers UI
    // ─────────────────────────────────────────────────────────────────────
    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize  = 12f
        isAllCaps = true
        setTextColor(ContextCompat.getColor(context, R.color.settings_section_label))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(28); it.bottomMargin = dp(6); it.marginStart = dp(8) }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun isDarkTheme(): Boolean {
        val mode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // ─────────────────────────────────────────────────────────────────────
    //  AppBar transparent
    // ─────────────────────────────────────────────────────────────────────
    private fun buildTransparentAppBar(title: String, spacer: View): LinearLayout {
        val iconTint   = ContextCompat.getColor(this, R.color.icon_tint)
        val solidColor = ContextCompat.getColor(this, R.color.appbar_solid)

        val wrapper = LinearLayout(this).apply {
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
        wrapper.addView(inner)
        wrapper.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)
            )
        })

        ViewCompat.setOnApplyWindowInsetsListener(wrapper) { _, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            inner.updatePadding(top = sb.top)
            spacer.layoutParams = spacer.layoutParams.also { lp ->
                lp.height = sb.top + resources.getDimensionPixelSize(
                    androidx.appcompat.R.dimen.abc_action_bar_default_height_material
                ) + dp(24)
            }
            insets
        }

        return wrapper
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

    private fun dp(v: Int)   = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = v * resources.displayMetrics.density
}