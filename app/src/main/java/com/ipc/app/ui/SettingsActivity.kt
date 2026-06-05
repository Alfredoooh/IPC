// SettingsActivity.kt
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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
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
                if (child is TextView) {
                    child.typeface = Typeface.create(tf, Typeface.BOLD)
                    break
                }
            }
        }
    }

    private fun setupAvatar() {
        val name  = prefs.getString("auth_user_name", "U") ?: "U"
        val email = prefs.getString("auth_user_email", "—") ?: "—"
        val initial = name.firstOrNull()?.uppercase() ?: "U"

        val avatarContainer = findViewById<FrameLayout>(R.id.avatarContainer)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(this@SettingsActivity, R.color.colorPrimary))
        }
        avatarContainer.background = bg

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
        findViewById<ImageView>(R.id.iconLogout)
            .setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", 14, Color.parseColor("#FF3B30")))

        listOf(R.id.chevronTheme, R.id.chevronLanguage, R.id.chevronPrivacy).forEach {
            findViewById<ImageView>(it)
                .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        }

        val currentTheme = prefs.getString("theme", "light")
        findViewById<TextView>(R.id.labelTheme).text =
            if (currentTheme == "dark") "Escuro" else "Claro"

        val currentLang = prefs.getString("language", "pt")
        findViewById<TextView>(R.id.labelLanguage).text =
            when (currentLang) { "en" -> "English"; else -> "Português" }

        val switchNotif = findViewById<MaterialSwitch>(R.id.switchNotifications)
        switchNotif.isChecked = prefs.getBoolean("notifications", true)
    }

    private fun setupActions() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressed() }
        findViewById<View>(R.id.itemTheme).setOnClickListener { showThemeDialog() }
        findViewById<View>(R.id.itemLanguage).setOnClickListener { showLanguageDialog() }

        val switchNotif = findViewById<MaterialSwitch>(R.id.switchNotifications)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
        findViewById<View>(R.id.itemNotifications).setOnClickListener { switchNotif.toggle() }
        findViewById<View>(R.id.itemPrivacy).setOnClickListener { }
        findViewById<View>(R.id.itemLogout).setOnClickListener { showLogoutDialog() }
    }

    private fun showCustomDialog(
        title: String,
        message: String? = null,
        positiveLabel: String,
        positiveColor: Int = ContextCompat.getColor(this, R.color.colorPrimary),
        negativeLabel: String = "Cancelar",
        customContent: View? = null,
        onPositive: () -> Unit
    ) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(ContextCompat.getColor(this@SettingsActivity, R.color.dialog_background))
            }
        }

        dialogView.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = if (message != null || customContent != null) dp(10) else dp(24) }
        })

        message?.let {
            dialogView.addView(TextView(this).apply {
                text = it
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { lp -> lp.bottomMargin = dp(24) }
            })
        }

        customContent?.let { dialogView.addView(it) }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(16) }
        }

        val cancelBtn = TextView(this).apply {
            text = negativeLabel
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(this@SettingsActivity, R.color.card_background))
            }
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginEnd = dp(8)
            }
        }

        val confirmBtn = TextView(this).apply {
            text = positiveLabel
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(positiveColor)
            }
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(confirmBtn)
        dialogView.addView(btnRow)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        cancelBtn.setOnClickListener { dialog.dismiss() }
        confirmBtn.setOnClickListener { dialog.dismiss(); onPositive() }
        dialog.show()
    }

    private fun showLogoutDialog() {
        showCustomDialog(
            title = "Terminar sessão",
            message = "Tens a certeza que queres sair?",
            positiveLabel = "Sair",
            positiveColor = Color.parseColor("#FF3B30"),
            onPositive = { doLogout() }
        )
    }

    private fun showThemeDialog() {
        val currentTheme = prefs.getString("theme", "light")
        val options = listOf("Claro" to "light", "Escuro" to "dark")
        var selected = if (currentTheme == "dark") 1 else 0

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        options.forEachIndexed { index, (label, _) ->
            radioGroup.addView(RadioButton(this).apply {
                text = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
                isChecked = index == selected
                id = index
                setPadding(0, dp(8), 0, dp(8))
                setOnCheckedChangeListener { _, isChecked -> if (isChecked) selected = index }
            })
        }

        showCustomDialog(
            title = "Tema",
            customContent = radioGroup,
            positiveLabel = "Aplicar",
            onPositive = {
                val theme = options[selected].second
                prefs.edit().putString("theme", theme).apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (theme == "dark") AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                recreate()
            }
        )
    }

    private fun showLanguageDialog() {
        val currentLang = prefs.getString("language", "pt")
        val options = listOf("Português" to "pt", "English" to "en")
        var selected = if (currentLang == "en") 1 else 0

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        options.forEachIndexed { index, (label, _) ->
            radioGroup.addView(RadioButton(this).apply {
                text = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
                isChecked = index == selected
                id = index
                setPadding(0, dp(8), 0, dp(8))
                setOnCheckedChangeListener { _, isChecked -> if (isChecked) selected = index }
            })
        }

        showCustomDialog(
            title = "Idioma",
            customContent = radioGroup,
            positiveLabel = "Aplicar",
            onPositive = {
                prefs.edit().putString("language", options[selected].second).apply()
                recreate()
            }
        )
    }

    private fun doLogout() {
        prefs.edit()
            .remove("auth_token")
            .remove("auth_user_id")
            .remove("auth_user_name")
            .remove("auth_user_email")
            .apply()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
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
}