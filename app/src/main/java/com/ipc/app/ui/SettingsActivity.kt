package com.ipc.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.caverock.androidsvg.SVG
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.ipc.app.R

class SettingsActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    // Tamanhos de ícone com 16% de redução
    // Antes: item=12, chevron=18, back=14  → Agora: item=10, chevron=15, back=12
    private val ICON_ITEM   = 10
    private val ICON_CHEV   = 15
    private val ICON_BACK   = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupTransparentAppBar()
        applyTimesNewRomanTitle()
        setupAvatar()
        setupIcons()
        setupActions()
        styleCards()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  AppBar progressive transparent (igual ao chat)
    // ─────────────────────────────────────────────────────────────────────
    private fun setupTransparentAppBar() {
        val toolbar = findViewById<LinearLayout>(R.id.settingsToolbar)
        val inner   = findViewById<View>(R.id.settingsAppBarInner)
        val spacer  = findViewById<View>(R.id.appbarSpacer)

        // Aplica padding de status bar ao appbar flutuante
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            inner.updatePadding(top = sb.top)
            // Ajusta o espaçador para ter exatamente a altura do appbar + status bar
            spacer.layoutParams = spacer.layoutParams.also {
                it.height = sb.top + resources.getDimensionPixelSize(
                    androidx.appcompat.R.dimen.abc_action_bar_default_height_material
                ) + dp(24f).toInt()
            }
            insets
        }

        // Gradiente: cor sólida do appbar → transparente
        val solidColor = ContextCompat.getColor(this, R.color.appbar_solid)
        toolbar.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(solidColor, solidColor, Color.TRANSPARENT)
        )
    }

    private fun applyTimesNewRomanTitle() {
        runCatching {
            val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
            val inner = findViewById<View>(R.id.settingsAppBarInner) as? ViewGroup ?: return@runCatching
            for (i in 0 until inner.childCount) {
                val child = inner.getChildAt(i)
                if (child is TextView) { child.typeface = Typeface.create(tf, Typeface.BOLD); break }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Avatar / Perfil
    // ─────────────────────────────────────────────────────────────────────
    private fun setupAvatar() {
        val name    = prefs.getString("auth_user_name", "U") ?: "U"
        val email   = prefs.getString("auth_user_email", "—") ?: "—"
        val initial = name.firstOrNull()?.uppercase() ?: "U"

        findViewById<TextView>(R.id.avatarInitial).text = initial
        findViewById<TextView>(R.id.settingsUserName).text = name
        findViewById<TextView>(R.id.settingsUserEmail).text = email

        val iconSec = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        findViewById<ImageView>(R.id.chevronProfile)
            .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", ICON_CHEV, iconSec))

        findViewById<View>(R.id.itemProfile).setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Ícones — reduzidos 16%
    // ─────────────────────────────────────────────────────────────────────
    private fun setupIcons() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val iconSec  = ContextCompat.getColor(this, R.color.icon_tint_secondary)

        // Back
        findViewById<ImageView>(R.id.btnBack)
            .setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", ICON_BACK, iconTint))

        // Conta
        findViewById<ImageView>(R.id.iconCustomization)
            .setImageDrawable(svgDrawable("icons/svg/customise.svg", ICON_ITEM, iconTint))
        findViewById<ImageView>(R.id.chevronCustomization)
            .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", ICON_CHEV, iconSec))

        findViewById<ImageView>(R.id.iconStorage)
            .setImageDrawable(svgDrawable("icons/svg/database.svg", ICON_ITEM, iconTint))
        findViewById<ImageView>(R.id.chevronStorage)
            .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", ICON_CHEV, iconSec))

        findViewById<ImageView>(R.id.iconSecurity)
            .setImageDrawable(svgDrawable("icons/svg/security.svg", ICON_ITEM, iconTint))
        findViewById<ImageView>(R.id.chevronSecurity)
            .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", ICON_CHEV, iconSec))

        // Aparência
        findViewById<ImageView>(R.id.iconTheme)
            .setImageDrawable(svgDrawable("icons/svg/appearance.svg", ICON_ITEM, iconTint))
        findViewById<ImageView>(R.id.iconLanguage)
            .setImageDrawable(svgDrawable("icons/svg/language.svg", ICON_ITEM, iconTint))

        listOf(R.id.chevronTheme, R.id.chevronLanguage).forEach {
            findViewById<ImageView>(it)
                .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", ICON_CHEV, iconSec))
        }

        // Notificações
        findViewById<ImageView>(R.id.iconNotifications)
            .setImageDrawable(svgDrawable("icons/svg/notifications.svg", ICON_ITEM, iconTint))

        // Sobre
        findViewById<ImageView>(R.id.iconAbout)
            .setImageDrawable(svgDrawable("icons/svg/about.svg", ICON_ITEM, iconTint))
        findViewById<ImageView>(R.id.chevronAbout)
            .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", ICON_CHEV, iconSec))

        // Labels
        val currentTheme = prefs.getString("theme", "light")
        findViewById<TextView>(R.id.labelTheme).text = if (currentTheme == "dark") "Escuro" else "Claro"

        val currentLang = prefs.getString("language", "pt")
        findViewById<TextView>(R.id.labelLanguage).text =
            when (currentLang) { "en" -> "English"; else -> "Português" }

        val switchNotif = findViewById<MaterialSwitch>(R.id.switchNotifications)
        switchNotif.isChecked = prefs.getBoolean("notifications", true)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Acções
    // ─────────────────────────────────────────────────────────────────────
    private fun setupActions() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.itemTheme).setOnClickListener { showThemeSheet() }
        findViewById<View>(R.id.itemLanguage).setOnClickListener { showLanguageSheet() }
        findViewById<View>(R.id.itemCustomization).setOnClickListener {
            startActivity(Intent(this, CustomizationActivity::class.java))
        }
        findViewById<View>(R.id.itemSecurity).setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }
        findViewById<View>(R.id.itemAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<View>(R.id.itemStorage).setOnClickListener { }
        findViewById<View>(R.id.itemLogout).setOnClickListener { showLogoutSheet() }

        val switchNotif = findViewById<MaterialSwitch>(R.id.switchNotifications)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
        findViewById<View>(R.id.itemNotifications).setOnClickListener { switchNotif.toggle() }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Cards iOS — cantos por posição + ripple preservado
    // ─────────────────────────────────────────────────────────────────────
    private fun styleCards() {
        applyGroupCorners(R.id.groupAccount)
        applyGroupCorners(R.id.groupAppearance)
        applyGroupCorners(R.id.groupPrivacy)
        applyGroupCorners(R.id.groupAbout)
    }

    private fun applyGroupCorners(groupId: Int) {
        val group     = findViewById<LinearLayout>(groupId)
        val cardColor = ContextCompat.getColor(this, R.color.card_background)
        val strong    = dp(22f)
        val soft      = dp(6f)

        val rows = (0 until group.childCount)
            .map { group.getChildAt(it) }
            .filter { it.tag != "divider" }

        val total = rows.size

        rows.forEachIndexed { idx, view ->
            val radii: FloatArray = when {
                total == 1       -> floatArrayOf(strong, strong, strong, strong, strong, strong, strong, strong)
                idx == 0         -> floatArrayOf(strong, strong, strong, strong, soft,   soft,   soft,   soft)
                idx == total - 1 -> floatArrayOf(soft,   soft,   soft,   soft,   strong, strong, strong, strong)
                else             -> floatArrayOf(soft,   soft,   soft,   soft,   soft,   soft,   soft,   soft)
            }

            val shape = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadii = radii
            }

            val rippleColor = ColorStateList.valueOf(
                if (isDarkTheme()) 0x33FFFFFF else 0x1F000000
            )
            view.background = RippleDrawable(rippleColor, shape, shape)
        }
    }

    private fun isDarkTheme(): Boolean {
        val mode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Modal iOS base
    // ─────────────────────────────────────────────────────────────────────
    private fun showIosSheet(title: String, block: LinearLayout.() -> Unit) {
        val dialog      = BottomSheetDialog(this, R.style.Theme_IPC_BottomSheet)
        val root        = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        val dialogBg    = ContextCompat.getColor(this, R.color.dialog_background)
        val handleColor = ContextCompat.getColor(this, R.color.divider)
        val titleColor  = ContextCompat.getColor(this, R.color.settings_section_label)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(12f), dp(12f), dp(12f), dp(12f), 0f, 0f, 0f, 0f)
                setColor(dialogBg)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        card.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(3f)
                setColor(handleColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(36f).toInt(), dp(4f).toInt()).also {
                it.gravity      = Gravity.CENTER_HORIZONTAL
                it.topMargin    = dp(12f).toInt()
                it.bottomMargin = dp(4f).toInt()
            }
        })

        card.addView(TextView(this).apply {
            text     = title
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(titleColor)
            gravity  = Gravity.CENTER
            setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), dp(12f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        card.block()

        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24f).toInt()
            )
        })

        root.addView(card)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun sheetRow(
        label: String,
        selected: Boolean,
        labelColor: Int? = null,
        dialog: BottomSheetDialog? = null,
        onClick: () -> Unit
    ): View {
        val textColor = labelColor ?: ContextCompat.getColor(this, R.color.text_primary)

        val row = LinearLayout(this).apply {
            orientation   = LinearLayout.HORIZONTAL
            gravity       = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52f).toInt()
            setPadding(dp(20f).toInt(), dp(14f).toInt(), dp(20f).toInt(), dp(14f).toInt())
            isClickable   = true
            isFocusable   = true
            val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = a.getDrawable(0)
            a.recycle()
        }

        row.addView(TextView(this).apply {
            text     = label
            textSize = 17f
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (selected) {
            row.addView(ImageView(this).apply {
                setImageDrawable(
                    svgDrawable(
                        "icons/svg/ic_check.svg", 18,
                        ContextCompat.getColor(this@SettingsActivity, R.color.colorPrimary)
                    )
                )
                layoutParams = LinearLayout.LayoutParams(dp(18f).toInt(), dp(18f).toInt())
            })
        }

        row.setOnClickListener {
            dialog?.dismiss()
            onClick()
        }
        return row
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Sheets
    // ─────────────────────────────────────────────────────────────────────
    private fun showThemeSheet() {
        val current = prefs.getString("theme", "light")
        val dialog  = BottomSheetDialog(this, R.style.Theme_IPC_BottomSheet)

        val root        = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        val dialogBg    = ContextCompat.getColor(this, R.color.dialog_background)
        val handleColor = ContextCompat.getColor(this, R.color.divider)
        val titleColor  = ContextCompat.getColor(this, R.color.settings_section_label)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(12f), dp(12f), dp(12f), dp(12f), 0f, 0f, 0f, 0f)
                setColor(dialogBg)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        card.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(3f)
                setColor(handleColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(36f).toInt(), dp(4f).toInt()).also {
                it.gravity      = Gravity.CENTER_HORIZONTAL
                it.topMargin    = dp(12f).toInt()
                it.bottomMargin = dp(4f).toInt()
            }
        })

        card.addView(TextView(this).apply {
            text     = "Tema"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(titleColor)
            gravity  = Gravity.CENTER
            setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), dp(12f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        listOf("Claro" to "light", "Escuro" to "dark").forEach { (label, value) ->
            card.addView(sheetRow(label, current == value, dialog = dialog) {
                prefs.edit().putString("theme", value).apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (value == "dark") AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                recreate()
            })
        }

        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24f).toInt()
            )
        })

        root.addView(card)
        dialog.setContentView(root)
        dialog.show()
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
            addView(sheetRow("Sair", false, Color.parseColor("#FF3B30")) { doLogout() })
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

    // ─────────────────────────────────────────────────────────────────────
    //  Utilitários
    // ─────────────────────────────────────────────────────────────────────
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

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dp(v: Int)   = v * resources.displayMetrics.density

    override fun finish() { super.finish() }
}