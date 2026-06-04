// UserProfileActivity.kt
package com.ipc.app.ui

import android.content.Context
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
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.ipc.app.R

class UserProfileActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.appbar_background))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
            setPadding(dp(8), 0, dp(16), 0)

            val btnBack = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also {
                    it.marginEnd = dp(8)
                }
                setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", 18, iconTint))
                background = ContextCompat.getDrawable(context, R.drawable.appbar_btn_bg)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener { onBackPressed() }
            }
            addView(btnBack)

            val titleTv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = getString(R.string.settings_profile)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                runCatching {
                    val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
                    typeface = Typeface.create(tf, Typeface.BOLD)
                }
            }
            addView(titleTv)
        }
        root.addView(appBar)

        root.addView(View(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(32), dp(40), dp(32), dp(32))
        }

        val name    = prefs.getString("auth_user_name", "Utilizador") ?: "Utilizador"
        val email   = prefs.getString("auth_user_email", "—") ?: "—"
        val userId  = prefs.getString("auth_user_id", "—") ?: "—"
        val initial = name.firstOrNull()?.uppercase() ?: "U"

        val avatarContainer = FrameLayout(this).apply {
            val size = dp(96)
            layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(24)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.colorPrimary))
            }
        }
        avatarContainer.addView(TextView(this).apply {
            text = initial
            textSize = 38f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER }
        })
        contentLayout.addView(avatarContainer)

        contentLayout.addView(TextView(this).apply {
            text = name
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(6) }
        })

        contentLayout.addView(TextView(this).apply {
            text = email
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(40) }
        })

        contentLayout.addView(View(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.bottomMargin = dp(24) }
        })

        contentLayout.addView(buildInfoRow("ID da conta", userId))
        contentLayout.addView(buildInfoRow("Email", email))
        contentLayout.addView(buildInfoRow("Nome", name))

        root.addView(contentLayout)
        setContentView(root)
    }

    private fun buildInfoRow(label: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(20) }

            addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(2) }
            })
            addView(TextView(context).apply {
                text = value
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    fun svgDrawable(path: String, sizeDp: Int, tint: Int): BitmapDrawable {
        val px  = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        runCatching {
            SVG.getFromAsset(assets, path).apply {
                documentWidth  = px.toFloat()
                documentHeight = px.toFloat()
                renderToCanvas(Canvas(bmp))
            }
        }
        return BitmapDrawable(resources, bmp).also {
            it.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
    }
}