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
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ipc.app.R

class SettingsActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyTimesNewRomanTitle()
        setupAvatarAppBar()
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

    private fun setupAvatarAppBar() {
        val name    = prefs.getString("auth_user_name", "U") ?: "U"
        val initial = name.firstOrNull()?.uppercase() ?: "U"

        val avatarBtn = findViewById<FrameLayout>(R.id.settingsAvatarBtn)
        val avatarBg  = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(this@SettingsActivity, R.color.colorPrimary))
        }
        avatarBtn.background = avatarBg
        findViewById<TextView>(R.id.settingsAvatarInitial).text = initial

        avatarBtn.setOnClickListener {
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
            .setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", 16, Color.parseColor("#FF3B30")))

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
        switchNotif.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notifications", checked).apply()
        }
        findViewById<View>(R.id.itemNotifications).setOnClickListener { switchNotif.toggle() }
        findViewById<View>(R.id.itemPrivacy).setOnClickListener { showRecoverPasswordDialog() }
        findViewById<View>(R.id.itemLogout).setOnClickListener { showLogoutDialog() }
    }

    // ── Recuperar password ────────────────────────────────────────────────
    private fun showRecoverPasswordDialog() {
        val email = prefs.getString("auth_user_email", "") ?: ""

        val dialogView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val infoText = TextView(this).apply {
            text = "Introduz o teu email para receber as instruções de recuperação."
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        }
        dialogView.addView(infoText)

        val emailInput = TextInputLayout(this, null,
            com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox
        ).apply {
            hint = "Email"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val emailField = TextInputEditText(emailInput.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(email)
        }
        emailInput.addView(emailField)
        dialogView.addView(emailInput)

        MaterialAlertDialogBuilder(this, R.style.IpcAlertDialog)
            .setTitle("Recuperar password")
            .setView(dialogView)
            .setPositiveButton("Enviar") { dialog, _ ->
                val inputEmail = emailField.text.toString().trim()
                if (inputEmail.isEmpty()) {
                    emailInput.error = "Introduz o teu email"
                    return@setPositiveButton
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(inputEmail).matches()) {
                    emailInput.error = "Email inválido"
                    return@setPositiveButton
                }
                dialog.dismiss()
                doSendPasswordReset(inputEmail)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doSendPasswordReset(email: String) {
        // Mock local — quando a API real estiver pronta, enviar POST /auth/forgot-password
        // Por agora mostra confirmação
        MaterialAlertDialogBuilder(this, R.style.IpcAlertDialog)
            .setTitle("Email enviado")
            .setMessage("Se a conta '$email' existir, receberás um email com as instruções para redefinir a tua password.")
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Logout ────────────────────────────────────────────────────────────
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this, R.style.IpcAlertDialog)
            .setTitle("Terminar sessão")
            .setMessage("Tens a certeza que queres sair?")
            .setPositiveButton("Sair") { _, _ -> doLogout() }
            .setNegativeButton("Cancelar", null)
            .show()
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

    // ── Dialogs ───────────────────────────────────────────────────────────
    private fun showThemeDialog() {
        val currentTheme = prefs.getString("theme", "light")
        MaterialAlertDialogBuilder(this, R.style.IpcAlertDialog)
            .setTitle("Tema")
            .setSingleChoiceItems(arrayOf("Claro", "Escuro"), if (currentTheme == "dark") 1 else 0) { dialog, which ->
                prefs.edit().putString("theme", if (which == 1) "dark" else "light").apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (which == 1) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
                dialog.dismiss(); recreate()
            }.show()
    }

    private fun showLanguageDialog() {
        val currentLang = prefs.getString("language", "pt")
        MaterialAlertDialogBuilder(this, R.style.IpcAlertDialog)
            .setTitle("Idioma")
            .setSingleChoiceItems(arrayOf("Português", "English"), if (currentLang == "en") 1 else 0) { dialog, which ->
                prefs.edit().putString("language", if (which == 1) "en" else "pt").apply()
                dialog.dismiss(); recreate()
            }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { super.onBackPressed() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun svgDrawable(path: String, sizeDp: Int, tint: Int): BitmapDrawable {
        val px  = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        runCatching {
            SVG.getFromAsset(assets, path).apply {
                documentWidth = px.toFloat(); documentHeight = px.toFloat()
                renderToCanvas(Canvas(bmp))
            }
        }
        return BitmapDrawable(resources, bmp).also { it.setColorFilter(tint, PorterDuff.Mode.SRC_IN) }
    }
}