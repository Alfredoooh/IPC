package com.ipc.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.caverock.androidsvg.SVG
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.ipc.app.databinding.ActivityMainBinding
import com.ipc.app.ui.BaseActivity
import java.util.Locale

class MainActiviy : BaseActivity() {

    lateinit var binding: ActivityMainBinding

    private var drawerOpen = false
    private var drawerAnimator: ValueAnimator? = null
    private var currentTab = R.id.tabChat

    private val activeIconColor: Int
        get() = if (isAppDarkMode) Color.WHITE else Color.BLACK

    private val inactiveIconColor: Int
        get() = Color.parseColor("#888888")

    private val drawerWidth: Int
        get() = (resources.displayMetrics.widthPixels * 0.75f).toInt()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "light")) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else   -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        val lang = prefs.getString("language", "") ?: ""
        val base = if (lang.isNotEmpty()) {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else newBase
        super.attachBaseContext(base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.drawerContainer.layoutParams = binding.drawerContainer.layoutParams.also {
            it.width = drawerWidth
        }

        setupIcons()
        setupDrawer()
        setupBottomTabs()
    }

    private fun setupIcons() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val iconSec  = ContextCompat.getColor(this, R.color.icon_tint_secondary)

        binding.btnMenu.setImageDrawable(svgDrawable("icons/svg/side_panel.svg", 16, iconTint))
        binding.btnMore.setImageDrawable(svgDrawable("icons/svg/more_vertical.svg", 16, iconTint))

        binding.drawerIconSettings.setImageDrawable(svgDrawable("icons/svg/settings.svg", 14, iconTint))
        binding.drawerIconAbout.setImageDrawable(svgDrawable("icons/svg/about.svg", 14, iconTint))
        binding.drawerChevronSettings.setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        binding.drawerChevronAbout.setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))

        binding.emptyIcon.setImageDrawable(svgDrawable("icons/svg/chat.svg", 58, iconSec))
    }

    private fun setupDrawer() {
        binding.btnMenu.setOnClickListener {
            if (drawerOpen) closeDrawer() else openDrawer()
        }
        binding.drawerScrim.setOnClickListener { closeDrawer() }
        binding.drawerItemSettings.setOnClickListener { closeDrawer() }
        binding.drawerItemAbout.setOnClickListener { closeDrawer() }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        binding.drawerScrim.visibility = View.VISIBLE
        animateDrawer(from = binding.coordinatorLayout.translationX, to = drawerWidth.toFloat())
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        animateDrawer(from = binding.coordinatorLayout.translationX, to = 0f) {
            binding.drawerScrim.visibility = View.GONE
        }
    }

    private fun animateDrawer(from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        drawerAnimator?.cancel()
        drawerAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = 300
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                binding.coordinatorLayout.translationX = value
                val progress = value / drawerWidth
                binding.drawerScrim.alpha = progress * 0.4f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun setupBottomTabs() {
        refreshTabIcons()
        binding.tabChat.setOnClickListener    { selectTab(R.id.tabChat) }
        binding.tabPreview.setOnClickListener { selectTab(R.id.tabPreview) }
    }

    private fun selectTab(tabId: Int) {
        if (currentTab == tabId) return
        currentTab = tabId
        refreshTabIcons()
        showBottomNav()
    }

    private fun refreshTabIcons() {
        val active   = activeIconColor
        val inactive = inactiveIconColor

        binding.tabChatIcon.setImageDrawable(
            if (currentTab == R.id.tabChat)
                svgDrawable("icons/svg/chat_filled.svg", 22, active)
            else
                svgDrawable("icons/svg/chat.svg", 22, inactive)
        )
        binding.tabChatLabel.setTextColor(if (currentTab == R.id.tabChat) active else inactive)

        binding.tabPreviewIcon.setImageDrawable(
            if (currentTab == R.id.tabPreview)
                svgDrawable("icons/svg/preview_filled.svg", 22, active)
            else
                svgDrawable("icons/svg/preview.svg", 22, inactive)
        )
        binding.tabPreviewLabel.setTextColor(if (currentTab == R.id.tabPreview) active else inactive)
    }

    override fun onResume() {
        super.onResume()
        refreshTabIcons()
        showBottomNav()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) {
            closeDrawer()
            return
        }
        super.onBackPressed()
    }

    @Suppress("UNCHECKED_CAST")
    fun showBottomNav() {
        val lp = binding.bottomNavWrapper.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val behavior = lp.behavior as? HideBottomViewOnScrollBehavior<View> ?: return
        behavior.slideUp(binding.bottomNavWrapper)
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