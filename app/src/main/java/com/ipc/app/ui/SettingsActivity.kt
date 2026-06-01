package com.ipc.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ipc.app.MainActiviy
import com.ipc.app.R

class SettingsActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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
            when (currentLang) {
                "en" -> "English"
                else -> "Português"
            }

        val version = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.labelVersion).text = version

        val switchNotif = findViewById<SwitchCompat>(R.id.switchNotifications)
        switchNotif.isChecked = prefs.getBoolean("notifications", true)
    }

    private fun setupActions() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressed() }

        findViewById<View>(R.id.itemTheme).setOnClickListener {
            val options = arrayOf("Claro", "Escuro")
            val current = if (prefs.getString("theme", "light") == "dark") 1 else 0
            MaterialAlertDialogBuilder(this)
                .setTitle("Tema")
                .setSingleChoiceItems(options, current) { dialog, which ->
                    val theme = if (which == 1) "dark" else "light"
                    prefs.edit().putString("theme", theme).apply()
                    if (which == 1)
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    dialog.dismiss()

                    // Reinicia a MainActivity e limpa o back stack todo
                    val intent = Intent(this, MainActiviy::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .show()
        }

        findViewById<View>(R.id.itemLanguage).setOnClickListener {
            val options = arrayOf("Português", "English")
            val current = if (prefs.getString("language", "pt") == "en") 1 else 0
            MaterialAlertDialogBuilder(this)
                .setTitle("Idioma")
                .setSingleChoiceItems(options, current) { dialog, which ->
                    val lang = if (which == 1) "en" else "pt"
                    prefs.edit().putString("language", lang).apply()
                    dialog.dismiss()

                    val intent = Intent(this, MainActiviy::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .show()
        }

        val switchNotif = findViewById<SwitchCompat>(R.id.switchNotifications)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
        findViewById<View>(R.id.itemNotifications).setOnClickListener {
            switchNotif.toggle()
        }

        findViewById<View>(R.id.itemPrivacy).setOnClickListener { }
        findViewById<View>(R.id.itemAbout).setOnClickListener { }
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