package com.ipc.app.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.caverock.androidsvg.SVG
import com.ipc.app.R

class SettingsActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDimAmount(0f)
        setContentView(R.layout.activity_settings)

        // Empurra toolbar para baixo do status bar transparente
        val toolbar = findViewById<View>(R.id.settingsToolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBar.top)
            insets
        }

        setupIcons()
        setupActions()
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
        findViewById<ImageView>(R.id.iconAbout)
            .setImageDrawable(svgDrawable("icons/svg/about.svg", 14, iconTint))

        listOf(R.id.chevronTheme, R.id.chevronLanguage, R.id.chevronPrivacy, R.id.chevronAbout).forEach {
            findViewById<ImageView>(it)
                .setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        }

        val currentTheme = prefs.getString("theme", "light")
        findViewById<TextView>(R.id.labelTheme).text =
            if (currentTheme == "dark") "Escuro" else "Claro"

        val currentLang = prefs.getString("language", "pt")
        findViewById<TextView>(R.id.labelLanguage).text =
            when (currentLang) { "en" -> "English"; else -> "Português" }

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("—")
        findViewById<TextView>(R.id.labelVersion).text = version

        val switchNotif = findViewById<SwitchCompat>(R.id.switchNotifications)
        switchNotif.isChecked = prefs.getBoolean("notifications", true)
    }

    private fun setupActions() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressed() }

        findViewById<View>(R.id.itemTheme).setOnClickListener { showThemeDialog() }
        findViewById<View>(R.id.itemLanguage).setOnClickListener { showLanguageDialog() }

        val switchNotif = findViewById<SwitchCompat>(R.id.switchNotifications)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
        findViewById<View>(R.id.itemNotifications).setOnClickListener { switchNotif.toggle() }
        findViewById<View>(R.id.itemPrivacy).setOnClickListener { }
        findViewById<View>(R.id.itemAbout).setOnClickListener { }
    }

    private fun showThemeDialog() {
        val currentTheme = prefs.getString("theme", "light")
        val options = arrayOf("Claro", "Escuro")
        val selected = if (currentTheme == "dark") 1 else 0

        AlertDialog.Builder(this, R.style.IpcAlertDialog)
            .setTitle("Tema")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val theme = if (which == 1) "dark" else "light"
                prefs.edit().putString("theme", theme).apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (which == 1) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun showLanguageDialog() {
        val currentLang = prefs.getString("language", "pt")
        val options = arrayOf("Português", "English")
        val selected = if (currentLang == "en") 1 else 0

        AlertDialog.Builder(this, R.style.IpcAlertDialog)
            .setTitle("Idioma")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val lang = if (which == 1) "en" else "pt"
                prefs.edit().putString("language", lang).apply()
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
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
}