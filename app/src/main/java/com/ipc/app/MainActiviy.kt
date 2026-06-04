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
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.caverock.androidsvg.SVG
import com.ipc.app.databinding.ActivityMainBinding
import com.ipc.app.ui.BaseActivity
import com.ipc.app.ui.MyCoinActivity
import com.ipc.app.ui.SettingsActivity
import java.util.Calendar
import java.util.Locale

class MainActiviy : BaseActivity() {

    lateinit var binding: ActivityMainBinding

    private var drawerOpen = false
    private var popupVisible = false
    private var drawerAnimator: ValueAnimator? = null
    private var sendBtnAnimator: ValueAnimator? = null
    private var inputRowAnimator: ValueAnimator? = null
    private var bottomBarAnimator: ValueAnimator? = null
    private var sendBtnVisible = false
    private var inputRowVisible = true
    private var inputRowHeight = 0
    private var currentTab = R.id.tabChat
    private var bottomBarCollapsed = false

    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var isSwipingDrawer = false
    private val SWIPE_EDGE_WIDTH = 40f
    private val SWIPE_MIN_DIST = 30f

    private val MARGIN_NORMAL_DP    = 10f
    private val MARGIN_COLLAPSED_DP = 28f
    private val RADIUS_NORMAL_DP    = 20f
    private val RADIUS_COLLAPSED_DP = 32f

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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val extraShift = (imeInsets.bottom - navInsets.bottom).coerceAtLeast(0)

            binding.bottomNavWrapper.animate()
                .translationY(-extraShift.toFloat())
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()

            binding.bottomNavWrapper.updatePadding(
                bottom = if (extraShift == 0) navInsets.bottom else 0
            )
            insets
        }

        binding.drawerContainer.layoutParams = binding.drawerContainer.layoutParams.also {
            it.width = drawerWidth
        }

        binding.inputRow.post {
            if (inputRowHeight == 0) inputRowHeight = binding.inputRow.height
        }

        setupAppBarScrollListener()
        setupLogo()
        setupGreeting()
        setupIcons()
        setupDrawer()
        setupSwipeDrawer()
        setupBottomTabs()
        setupPreviewImage()
        setupInput()
        setupPopupMenu()
    }

    private fun setupAppBarScrollListener() {
        binding.appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            val collapsed = verticalOffset < -10
            if (collapsed != bottomBarCollapsed) {
                bottomBarCollapsed = collapsed
                animateBottomBar(collapsed)
            }
        }
    }

    private fun animateBottomBar(collapse: Boolean) {
        val d = density
        val marginFrom = if (collapse) (MARGIN_NORMAL_DP * d).toInt() else (MARGIN_COLLAPSED_DP * d).toInt()
        val marginTo   = if (collapse) (MARGIN_COLLAPSED_DP * d).toInt() else (MARGIN_NORMAL_DP * d).toInt()
        val radiusFrom = if (collapse) RADIUS_NORMAL_DP * d else RADIUS_COLLAPSED_DP * d
        val radiusTo   = if (collapse) RADIUS_COLLAPSED_DP * d else RADIUS_NORMAL_DP * d

        val bgDrawable = (binding.bottomNavWrapper.background as? GradientDrawable)
            ?: GradientDrawable().also {
                it.setColor(ContextCompat.getColor(this, R.color.card_background))
                binding.bottomNavWrapper.background = it
            }

        bottomBarAnimator?.cancel()
        bottomBarAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val margin = (marginFrom + (marginTo - marginFrom) * f).toInt()
                val radius = radiusFrom + (radiusTo - radiusFrom) * f
                bgDrawable.cornerRadius = radius
                val lp = binding.bottomNavWrapper.layoutParams
                    as? android.widget.FrameLayout.LayoutParams ?: return@addUpdateListener
                lp.marginStart  = margin
                lp.marginEnd    = margin
                lp.bottomMargin = (10 * d).toInt()
                binding.bottomNavWrapper.layoutParams = lp
            }
            start()
        }
    }

    private fun setupLogo() {
        runCatching {
            val bmp = assets.open("icons/png/logo.png").use { BitmapFactory.decodeStream(it) }
            binding.emptyLogo.setImageBitmap(bmp)
        }
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Bom dia 👋"
            hour < 18 -> "Boa tarde 👋"
            else      -> "Boa noite 👋"
        }
        binding.emptyGreeting.text = greeting

        runCatching {
            val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
            binding.emptyGreeting.typeface = Typeface.create(tf, Typeface.BOLD)
            binding.emptySubtitle.typeface = tf
            binding.previewTitle.typeface = Typeface.create(tf, Typeface.BOLD)
            binding.previewSubtitle.typeface = tf
        }
    }

    private fun setupIcons() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val iconSec  = ContextCompat.getColor(this, R.color.icon_tint_secondary)

        binding.btnMenu.setImageDrawable(svgDrawable("icons/svg/side_panel.svg", 16, iconTint))
        binding.btnMore.setImageDrawable(svgDrawable("icons/svg/more_vertical.svg", 16, iconTint))
        binding.btnPullIcon.setImageDrawable(svgDrawable("icons/svg/add.svg", 16, iconTint))

        binding.drawerIconSettings.setImageDrawable(svgDrawable("icons/svg/settings.svg", 14, iconTint))
        binding.drawerChevronSettings.setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))

        // Coin no popup
        runCatching {
            val bmp = assets.open("icons/png/coin.png").use { BitmapFactory.decodeStream(it) }
            binding.popupCoinIcon.setImageBitmap(bmp)
        }

        binding.popupImportIcon.setImageDrawable(svgDrawable("icons/svg/download.svg", 18, iconTint))
        binding.popupCameraIcon.setImageDrawable(svgDrawable("icons/svg/preview.svg", 18, iconTint))
        binding.popupUrlIcon.setImageDrawable(svgDrawable("icons/svg/external.svg", 18, iconTint))
    }

    private fun setupPopupMenu() {
        binding.btnMore.setOnClickListener { showPopup() }
        binding.btnPull.setOnClickListener { showPopup() }

        binding.popupOverlay.setOnClickListener { hidePopup() }

        binding.popupItemCoin.setOnClickListener {
            hidePopup()
            startActivity(Intent(this, MyCoinActivity::class.java))
        }

        binding.popupItemImport.setOnClickListener { hidePopup() }
        binding.popupItemCamera.setOnClickListener { hidePopup() }
        binding.popupItemUrl.setOnClickListener { hidePopup() }
    }

    private fun showPopup() {
        if (popupVisible) return
        popupVisible = true
        binding.popupOverlay.visibility = View.VISIBLE
        binding.popupOverlay.alpha = 0f
        binding.popupMenu.scaleX = 0.85f
        binding.popupMenu.scaleY = 0.85f
        binding.popupMenu.alpha = 0f
        binding.popupMenu.pivotX = binding.popupMenu.width.toFloat()
        binding.popupMenu.pivotY = 0f

        binding.popupOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.popupMenu.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun hidePopup() {
        if (!popupVisible) return
        popupVisible = false

        binding.popupMenu.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .alpha(0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.popupOverlay.animate()
            .alpha(0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.popupOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun showInputRow() {
        if (inputRowVisible) return
        inputRowVisible = true
        val targetH = if (inputRowHeight > 0) inputRowHeight else {
            binding.inputRow.measure(
                View.MeasureSpec.makeMeasureSpec(binding.inputRow.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            binding.inputRow.measuredHeight.also { inputRowHeight = it }
        }
        binding.inputRow.visibility = View.VISIBLE
        inputRowAnimator?.cancel()
        inputRowAnimator = ValueAnimator.ofInt(0, targetH).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                binding.inputRow.layoutParams =
                    binding.inputRow.layoutParams.also { it.height = h }
                binding.inputRow.alpha = h.toFloat() / targetH
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.inputRow.layoutParams =
                        binding.inputRow.layoutParams.also {
                            it.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    binding.inputRow.alpha = 1f
                }
            })
            start()
        }
    }

    private fun hideInputRow() {
        if (!inputRowVisible) return
        inputRowVisible = false
        val fromH = if (inputRowHeight > 0) inputRowHeight else binding.inputRow.height
        inputRowAnimator?.cancel()
        inputRowAnimator = ValueAnimator.ofInt(fromH, 0).apply {
            duration = 240
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                binding.inputRow.layoutParams =
                    binding.inputRow.layoutParams.also { it.height = h }
                binding.inputRow.alpha = h.toFloat() / fromH
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.inputRow.visibility = View.GONE
                    binding.inputRow.alpha = 1f
                }
            })
            start()
        }
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

    private fun setupSwipeDrawer() {
        val edgePx    = SWIPE_EDGE_WIDTH * density
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
                            binding.coordinatorLayout.translationX =
                                dx.coerceAtMost(drawerWidth.toFloat())
                            binding.drawerScrim.visibility = View.VISIBLE
                            true
                        } else false
                    } else if (drawerOpen) {
                        val dx = event.rawX - swipeStartX
                        if (dx < 0) {
                            binding.coordinatorLayout.translationX =
                                (drawerWidth + dx).coerceAtLeast(0f)
                            true
                        } else false
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - swipeStartX
                    if (isSwipingDrawer) {
                        isSwipingDrawer = false
                        if (dx > minDistPx) {
                            drawerOpen = true
                            animateDrawer(binding.coordinatorLayout.translationX, drawerWidth.toFloat())
                        } else {
                            animateDrawer(binding.coordinatorLayout.translationX, 0f) {
                                binding.drawerScrim.visibility = View.GONE
                            }
                        }
                        true
                    } else if (drawerOpen && dx < -minDistPx) {
                        closeDrawer(); true
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
            }, 250)
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        binding.drawerScrim.visibility = View.VISIBLE
        animateDrawer(binding.coordinatorLayout.translationX, drawerWidth.toFloat())
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        animateDrawer(binding.coordinatorLayout.translationX, 0f) {
            binding.drawerScrim.visibility = View.GONE
        }
    }

    private fun animateDrawer(from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        drawerAnimator?.cancel()
        drawerAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = 300
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                binding.coordinatorLayout.translationX = v
                binding.coordinatorLayout.elevation = 8f + ((v / drawerWidth) * 16f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
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
        updateContentForTab()
    }

    private fun updateContentForTab() {
        val isPreview = currentTab == R.id.tabPreview
        binding.previewState.visibility = if (isPreview) View.VISIBLE else View.GONE
        binding.emptyState.visibility   = if (isPreview) View.GONE   else View.VISIBLE
        if (isPreview) hideInputRow() else showInputRow()
    }

    private fun setupPreviewImage() {
        val bitmap = runCatching {
            assets.open("icons/png/preview.png").use { BitmapFactory.decodeStream(it) }
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
        if (popupVisible) { hidePopup(); return }
        if (drawerOpen) { closeDrawer(); return }
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