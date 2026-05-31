package com.ipc.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.caverock.androidsvg.SVG
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.ipc.app.databinding.ActivityMainBinding
import com.ipc.app.ui.BaseActivity

class MainActiviy : BaseActivity() {

    lateinit var binding: ActivityMainBinding

    private var currentTab = R.id.tabChat

    private val activeIconColor: Int
        get() = Color.WHITE

    private val inactiveIconColor: Int
        get() = Color.parseColor("#666666")

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupBottomTabs()
    }

    private fun setupTopBar() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        binding.btnMenu.setImageDrawable(svgDrawable("icons/svg/menu.svg", 18, iconTint))
        binding.btnMore.setImageDrawable(svgDrawable("icons/svg/more_vertical.svg", 18, iconTint))
    }

    private fun setupBottomTabs() {
        refreshTabIcons()

        binding.tabChat.setOnClickListener   { selectTab(R.id.tabChat) }
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
                svgDrawable("icons/svg/hub_filled.svg", 24, active)
            else
                svgDrawable("icons/svg/hub.svg", 24, inactive)
        )
        binding.tabChatLabel.setTextColor(if (currentTab == R.id.tabChat) active else inactive)

        binding.tabPreviewIcon.setImageDrawable(
            svgDrawable("icons/svg/side_panel.svg", 24, inactive)
        )
        binding.tabPreviewLabel.setTextColor(inactive)
    }

    override fun onResume() {
        super.onResume()
        refreshTabIcons()
        showBottomNav()
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