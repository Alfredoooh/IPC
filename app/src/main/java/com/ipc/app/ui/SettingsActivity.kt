package com.ipc.app.ui

import android.content.Context
import android.content.Intent
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.ipc.app.R

class SettingsActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyTimesNewRomanTitle()
        setupAvatar()
        setupIcons()
        setupActions()
    }

    private fun applyTimesNewRomanTitle() {
        runCatching {
            val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
            val toolbar = findViewById<ViewGroup>(R.id.settingsToolbar)
            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(i)
                if (child is TextView) { child.typeface = Typeface.create(tf, Typeface.BOLD); break }
            }
        }
    }

    private fun setupAvatar() {
        val name    = prefs.getString("auth_user_name", "U") ?: "U"
        val email   = prefs.getString("auth_user_email", "—") ?: "—"
        val initial = name.firstOrNull()?.uppercase() ?: "U"

        val avatarContainer = findViewById<LinearLayout>(R.id.avatarContainer)
        findViewById<TextView>(R.id.avatarInitial).text = initial
        findViewById<TextView>(R.id.settingsUserName).text = name
        findViewById<TextView>(R.id.settingsUserEmail).text = email

        val iconSec = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        findViewById<ImageView>(R.id.chevronProfile)
            .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        avatarContainer.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }
    }

    private fun setupIcons() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val iconSec  = ContextCompat.getColor(this, R.color.icon_tint_secondary)

        findViewById<ImageView>(R.id.btnBack)
            .setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", 16, iconTint))
        findViewById<ImageView>(R.id.iconTheme)
            .setImageDrawable(svgDrawable("icons/svg/appearance.svg", 14, iconTint))
        findViewById<ImageView>(R.id.iconLanguage)
            .setImageDrawable(svgDrawable("icons/svg/language.svg", 14, iconTint))
        findViewById<ImageView>(R.id.iconPrivacy)
            .setImageDrawable(svgDrawable("icons/svg/privacy.svg", 14, iconTint))
        findViewById<ImageView>(R.id.iconNotifications)
            .setImageDrawable(svgDrawable("icons/svg/notifications.svg", 14, iconTint))

        listOf(R.id.chevronTheme, R.id.chevronLanguage, R.id.chevronPrivacy).forEach {
            findViewById<ImageView>(it)
                .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        }

        val currentTheme = prefs.getString("theme", "light")
        findViewById<TextView>(R.id.labelTheme).text = if (currentTheme == "dark") "Escuro" else "Claro"

        val currentLang = prefs.getString("language", "pt")
        findViewById<TextView>(R.id.labelLanguage).text = when (currentLang) { "en" -> "English"; else -> "Português" }

        val switchNotif = findViewById<MaterialSwitch>(R.id.switchNotifications)
        switchNotif.isChecked = prefs.getBoolean("notifications", true)
    }

    private fun setupActions() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.itemTheme).setOnClickListener { showThemeSheet() }
        findViewById<View>(R.id.itemLanguage).setOnClickListener { showLanguageSheet() }
        val switchNotif = findViewById<MaterialSwitch>(R.id.switchNotifications)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
        findViewById<View>(R.id.itemNotifications).setOnClickListener { switchNotif.toggle() }
        findViewById<View>(R.id.itemPrivacy).setOnClickListener {}
        findViewById<View>(R.id.itemLogout).setOnClickListener { showLogoutSheet() }
    }

    private fun showIosSheet(title: String, block: LinearLayout.() -> Unit) {
        val dialog = BottomSheetDialog(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(20), dp(20), dp(20), dp(20), 0f, 0f, 0f, 0f)
                setColor(Color.WHITE) // branco puro sempre
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Handle pill
        card.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3)
                setColor(Color.parseColor("#E0E0E0"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36).toInt(), dp(4).toInt()).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(12).toInt()
                it.bottomMargin = dp(4).toInt()
            }
        })

        // Título sólido, sem linha abaixo
        card.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#8E8E93")) // cinza sólido
            gravity = Gravity.CENTER
            setPadding(dp(20).toInt(), dp(8).toInt(), dp(20).toInt(), dp(12).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        // Sem linha divisória após o título — direto para os rows
        card.block()

        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24).toInt()
            )
        })

        root.addView(card)
        dialog.setContentView(root)

        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialog.show()
    }

    private fun sheetRow(label: String, selected: Boolean, labelColor: Int? = null, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52).toInt()
            setPadding(dp(20).toInt(), dp(14).toInt(), dp(20).toInt(), dp(14).toInt())
            isClickable = true; isFocusable = true
            val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = a.getDrawable(0); a.recycle()
        }

        row.addView(TextView(this).apply {
            text = label
            textSize = 17f
            // texto sempre sólido: preto para normal, cor customizada para destrutivo
            setTextColor(labelColor ?: Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (selected) {
            row.addView(ImageView(this).apply {
                setImageDrawable(svgDrawable("icons/svg/ic_check.svg", 20,
                    ContextCompat.getColor(this@SettingsActivity, R.color.colorPrimary)))
                layoutParams = LinearLayout.LayoutParams(dp(20).toInt(), dp(20).toInt())
            })
        }

        row.setOnClickListener { onClick() }
        return row
    }

    // Sem sheetDivider — removido completamente

    private fun showThemeSheet() {
        val current = prefs.getString("theme", "light")
        showIosSheet("Tema") {
            listOf("Claro" to "light", "Escuro" to "dark").forEach { (label, value) ->
                addView(sheetRow(label, current == value) {
                    prefs.edit().putString("theme", value).apply()
                    AppCompatDelegate.setDefaultNightMode(
                        if (value == "dark") AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    recreate()
                })
                // sem divisórias
            }
        }
    }

    private fun showLanguageSheet() {
        val current = prefs.getString("language", "pt")
        showIosSheet("Idioma") {
            listOf("Português" to "pt", "English" to "en").forEach { (label, value) ->
                addView(sheetRow(label, current == value) {
                    prefs.edit().putString("language", value).apply()
                    recreate()
                })
            }
        }
    }

    private fun showLogoutSheet() {
        showIosSheet("Terminar sessão") {
            addView(sheetRow("Sair", false, Color.parseColor("#FF3B30")) {
                doLogout()
            })
        }
    }

    private fun doLogout() {
        prefs.edit()
            .remove("auth_token").remove("auth_user_id")
            .remove("auth_user_name").remove("auth_user_email")
            .apply()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
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
        return BitmapDrawable(resources, bmp).also { it.setColorFilter(tint, PorterDuff.Mode.SRC_IN) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density)
    override fun finish() { super.finish() }
}