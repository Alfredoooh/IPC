package com.ipc.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.caverock.androidsvg.SVG
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.ipc.app.databinding.ActivityMainBinding
import com.ipc.app.ui.BaseActivity
import com.ipc.app.ui.SettingsActivity
import java.util.Locale

class MainActiviy : BaseActivity() {

    lateinit var binding: ActivityMainBinding

    private var drawerOpen = false
    private var drawerAnimator: ValueAnimator? = null
    private var sendBtnAnimator: ValueAnimator? = null
    private var sendBtnVisible = false
    private var currentTab = R.id.tabChat

    // Swipe-to-open drawer
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var isSwipingDrawer = false
    private val SWIPE_EDGE_WIDTH = 40f  // dp da borda esquerda que activa o swipe
    private val SWIPE_MIN_DIST = 30f    // dp mínimos para considerar swipe horizontal

    private val activeIconColor: Int
        get() = if (isAppDarkMode) Color.WHITE else Color.BLACK

    private val inactiveIconColor: Int
        get() = Color.parseColor("#888888")

    private val drawerWidth: Int
        get() = (resources.displayMetrics.widthPixels * 0.75f).toInt()

    private val density: Float
        get() = resources.displayMetrics.density

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
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar padding no AppBar
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // ── Teclado sobe/desce o bottomNavWrapper suavemente ──────────────────
        // O CoordinatorLayout recebe os insets e anima translationY do wrapper
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Quando o teclado está visível, imeInsets.bottom > navInsets.bottom
            val imeHeight = imeInsets.bottom
            val navHeight = navInsets.bottom

            // translação extra = o quanto o teclado ultrapassa a nav bar
            val extraShift = (imeHeight - navHeight).coerceAtLeast(0)

            // Anima suavemente
            val targetTY = -extraShift.toFloat()
            binding.bottomNavWrapper.animate()
                .translationY(targetTY)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()

            // padding bottom da nav bar quando teclado está fechado
            binding.bottomNavWrapper.updatePadding(bottom = if (extraShift == 0) navHeight else 0)

            insets
        }

        binding.drawerContainer.layoutParams = binding.drawerContainer.layoutParams.also {
            it.width = drawerWidth
        }

        setupIcons()
        setupDrawer()
        setupSwipeDrawer()
        setupBottomTabs()
        setupPreviewImage()
        setupInput()
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

        // Ícone add.svg no botão Pull pill
        binding.btnPullIcon.setImageDrawable(svgDrawable("icons/svg/add.svg", 14, iconTint))
    }

    private fun setupInput() {
        binding.inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                if (hasText && !sendBtnVisible) showSendBtn()
                else if (!hasText && sendBtnVisible) hideSendBtn()
            }
        })
    }

    private fun showSendBtn() {
        sendBtnVisible = true
        binding.btnSend.visibility = View.VISIBLE
        sendBtnAnimator?.cancel()
        sendBtnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                binding.btnSend.alpha = v
                binding.btnSend.scaleX = 0.7f + (v * 0.3f)
                binding.btnSend.scaleY = 0.7f + (v * 0.3f)
            }
            start()
        }
    }

    private fun hideSendBtn() {
        sendBtnVisible = false
        sendBtnAnimator?.cancel()
        sendBtnAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                binding.btnSend.alpha = v
                binding.btnSend.scaleX = 0.7f + (v * 0.3f)
                binding.btnSend.scaleY = 0.7f + (v * 0.3f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.btnSend.visibility = View.GONE
                }
            })
            start()
        }
    }

    // ── Swipe edge-to-open drawer ─────────────────────────────────────────────
    private fun setupSwipeDrawer() {
        val edgePx = SWIPE_EDGE_WIDTH * density
        val minDistPx = SWIPE_MIN_DIST * density

        binding.coordinatorLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    isSwipingDrawer = !drawerOpen && swipeStartX < edgePx
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSwipingDrawer) {
                        val dx = event.rawX - swipeStartX
                        val dy = event.rawY - swipeStartY
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx > 0) {
                            // Acompanha o dedo
                            val progress = (dx / drawerWidth).coerceIn(0f, 1f)
                            binding.coordinatorLayout.translationX = dx.coerceAtMost(drawerWidth.toFloat())
                            binding.drawerScrim.visibility = View.VISIBLE
                            true
                        } else false
                    } else if (drawerOpen) {
                        val dx = event.rawX - swipeStartX
                        if (dx < 0) {
                            val newX = (drawerWidth + dx).coerceAtLeast(0f)
                            binding.coordinatorLayout.translationX = newX
                            true
                        } else false
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - swipeStartX
                    if (isSwipingDrawer) {
                        isSwipingDrawer = false
                        if (dx > minDistPx) {
                            // Confirma abertura
                            drawerOpen = true
                            animateDrawer(
                                from = binding.coordinatorLayout.translationX,
                                to = drawerWidth.toFloat()
                            )
                        } else {
                            // Cancela, fecha
                            animateDrawer(
                                from = binding.coordinatorLayout.translationX,
                                to = 0f
                            ) { binding.drawerScrim.visibility = View.GONE }
                        }
                        true
                    } else if (drawerOpen && dx < -minDistPx) {
                        closeDrawer()
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun setupDrawer() {
        binding.btnMenu.setOnClickListener {
            if (drawerOpen) closeDrawer() else openDrawer()
        }
        binding.drawerScrim.setOnClickListener { closeDrawer() }

        binding.drawerItemSettings.setOnClickListener {
            closeDrawer()
            binding.root.postDelayed({
                startActivity(Intent(this, SettingsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 250)
        }

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
                binding.coordinatorLayout.elevation = 8f + ((value / drawerWidth) * 16f)
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
        binding.btnPull.setOnClickListener    { showPullBottomSheet() }
    }

    private fun showPullBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.bottom_sheet_pull, null)

        // Fundo branco puro com cantos curvos
        sheet.setContentView(view)
        sheet.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        // Força o fundo do container do BottomSheetDialog a ser branco puro
        sheet.window?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.background = ContextCompat.getDrawable(this, R.drawable.pull_sheet_bg)

        // Ícones nas opções
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        view.findViewById<android.widget.ImageView>(R.id.pullIconImport)
            .setImageDrawable(svgDrawable("icons/svg/download.svg", 16, iconTint))
        view.findViewById<android.widget.ImageView>(R.id.pullIconCamera)
            .setImageDrawable(svgDrawable("icons/svg/preview.svg", 16, iconTint))
        view.findViewById<android.widget.ImageView>(R.id.pullIconUrl)
            .setImageDrawable(svgDrawable("icons/svg/external.svg", 16, iconTint))

        sheet.show()
    }

    private fun selectTab(tabId: Int) {
        if (currentTab == tabId) return
        currentTab = tabId
        refreshTabIcons()
        updateContentForTab()
    }

    private fun updateContentForTab() {
        val isPreview = currentTab == R.id.tabPreview
        binding.previewState.visibility = if (isPreview) View.VISIBLE else View.GONE
        binding.emptyState.visibility   = if (isPreview) View.GONE   else View.VISIBLE
        binding.inputRow.visibility     = if (isPreview) View.GONE   else View.VISIBLE
    }

    private fun setupPreviewImage() {
        val bitmap = runCatching {
            assets.open("icons/png/preview.png").use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
        if (bitmap != null) binding.previewImage.setImageBitmap(bitmap)
        binding.previewState.visibility = View.GONE
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
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) {
            closeDrawer()
            return
        }
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
}