package com.ipc.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ipc.app.data.AuthApiService
import com.ipc.app.data.Conversation
import com.ipc.app.databinding.ActivityMainBinding
import com.ipc.app.ui.BaseActivity
import com.ipc.app.ui.SettingsActivity
import kotlinx.coroutines.launch
import java.util.Locale

class MainActiviy : BaseActivity() {

    lateinit var binding: ActivityMainBinding

    var drawerOpen     = false
    var popupVisible   = false
    private var drawerAnimator:    ValueAnimator? = null
    private var bottomBarAnimator: ValueAnimator? = null
    var currentTab = R.id.tabPreview
    var keyboardOpen = false

    private var swipeStartX     = 0f
    private var swipeStartY     = 0f
    private var isSwipingDrawer = false
    private val SWIPE_EDGE_WIDTH = 40f
    private val SWIPE_MIN_DIST   = 30f

    val MARGIN_CHAT_DP    = 16f
    val MARGIN_PREVIEW_DP = 36f
    val RADIUS_CHAT_DP    = 20f
    val RADIUS_PREVIEW_DP = 38f
    val BOTTOM_MARGIN_DP  = 20f

    var currentBarMarginPx: Int   = -1
    var currentBarRadiusPx: Float = -1f

    var lastImeShift = 0
    var maxImeShift  = 0

    val prefs     by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }
    val authToken get() = prefs.getString("auth_token", "") ?: ""

    val activeIconColor: Int
        get() = if (isAppDarkMode) Color.WHITE else Color.BLACK
    val inactiveIconColor: Int
        get() = Color.parseColor("#888888")
    val drawerWidth: Int
        get() = (resources.displayMetrics.widthPixels * 0.75f).toInt()
    val density: Float
        get() = resources.displayMetrics.density

    lateinit var chatFragment:  ChatFragment
    lateinit var drawerManager: DrawerManager

    // ─── Preview modal ────────────────────────────────────────────────────────

    private var previewDialog: BottomSheetDialog? = null

    fun showPreviewModal() {
        if (previewDialog?.isShowing == true) return

        val dialog = BottomSheetDialog(this, R.style.PreviewModalDialog)
        previewDialog = dialog

        val screenH  = resources.displayMetrics.heightPixels
        val topGap   = dp(40)
        val topColor    = ContextCompat.getColor(this, R.color.gradient_warm_top)
        val bottomColor = ContextCompat.getColor(this, R.color.gradient_warm_bottom)
        val cornerPx = dp(24).toFloat()

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(cornerPx, cornerPx, cornerPx, cornerPx, 0f, 0f, 0f, 0f)
                colors = intArrayOf(topColor, bottomColor)
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            clipToOutline = true
        }

        val handle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#30000000"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12)
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dp(32), dp(80), dp(32), dp(100))
        }

        val previewBitmap = runCatching {
            assets.open("icons/png/preview.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()

        content.addView(ImageView(this).apply {
            if (previewBitmap != null) setImageBitmap(previewBitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        })
        content.addView(TextView(this).apply {
            text = "Resultado da Análise"; textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(20) }
        })
        content.addView(TextView(this).apply {
            text = "O output da sua consulta será apresentado aqui."; textSize = 17f
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_hint))
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.4f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) }
        })

        root.addView(content); root.addView(handle)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams.height = screenH - topGap; sheet.requestLayout()
                val beh = BottomSheetBehavior.from(sheet)
                beh.peekHeight = screenH - topGap; beh.state = BottomSheetBehavior.STATE_EXPANDED
                beh.isDraggable = true; beh.isHideable = true
                beh.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(v: View, newState: Int) { if (newState == BottomSheetBehavior.STATE_HIDDEN) dialog.dismiss() }
                    override fun onSlide(v: View, offset: Float) {}
                })
            }
        }
        dialog.show()
    }

    // ─── attachBaseContext ────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val p = newBase.getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE)
        when (p.getString("theme", "light")) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else   -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        val lang = p.getString("language", "") ?: ""
        val base = if (lang.isNotEmpty()) {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else newBase
        super.attachBaseContext(base)
    }

    // ─── onCreate ────────────────────────────────────────────────────────────

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
            val imeNowOpen = insets.isVisible(WindowInsetsCompat.Type.ime()) && imeInsets.bottom > 0
            val rawShift = (imeInsets.bottom - navInsets.bottom).coerceAtLeast(0)

            if (imeNowOpen) {
                if (!keyboardOpen || rawShift > maxImeShift) {
                    maxImeShift = rawShift
                }
                keyboardOpen = true
                lastImeShift = maxImeShift.coerceAtLeast(rawShift)
            } else {
                keyboardOpen = false
                lastImeShift = 0
                maxImeShift = 0
            }

            val appliedShift = if (imeNowOpen) lastImeShift else 0

            binding.bottomNavWrapper.animate().cancel()
            binding.chatRecyclerView.animate().cancel()
            binding.emptyState.animate().cancel()

            binding.bottomNavWrapper.translationY = -appliedShift.toFloat()
            binding.chatRecyclerView.translationY = -appliedShift.toFloat()
            binding.emptyState.translationY = if (imeNowOpen) -(appliedShift * 0.45f) else 0f

            binding.bottomNavWrapper.updatePadding(
                bottom = if (imeNowOpen) 0 else navInsets.bottom
            )
            insets
        }

        binding.drawerContainer.layoutParams = binding.drawerContainer.layoutParams.also {
            it.width = drawerWidth
        }

        currentBarMarginPx = (MARGIN_CHAT_DP * density).toInt()
        currentBarRadiusPx = RADIUS_CHAT_DP * density

        chatFragment  = ChatFragment(this)
        drawerManager = DrawerManager(this)

        setupBottomBarSolid()
        setupAppBarSolid()
        setupIcons()
        setupDrawer()
        setupSwipeDrawer()
        setupBottomTabs()
        setupBottomAddButton()
        setupPopupMenu()
        chatFragment.setup()
        drawerManager.loadConversations()
    }

    // ─── AppBar ───────────────────────────────────────────────────────────────

    private fun setupAppBarSolid() {
        binding.appBarLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.appbar_solid))
        binding.appBarGradient.layoutParams = binding.appBarGradient.layoutParams.also { it.height = dp(80) }
    }

    fun setupBottomBarSolid() {
        val bg = GradientDrawable().apply {
            cornerRadius = currentBarRadiusPx
            setColor(ContextCompat.getColor(this@MainActiviy, R.color.bottom_bar_solid))
        }
        binding.bottomNavWrapper.background = bg
    }

    // ─── Ícones ───────────────────────────────────────────────────────────────

    fun setupIcons() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val iconSec  = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        binding.btnMenu.setImageDrawable(svgDrawable("icons/svg/side_panel.svg", 16, iconTint))
        binding.btnNewChatIcon.setImageDrawable(svgDrawable("icons/svg/new_chat.svg", 17, iconTint))
        binding.drawerIconSettings.setImageDrawable(svgDrawable("icons/svg/settings.svg", 14, iconTint))
        binding.drawerChevronSettings.setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        binding.popupCameraIcon.setImageDrawable(svgDrawable("icons/svg/camera.svg", 20, iconTint))
        binding.popupImportIcon.setImageDrawable(svgDrawable("icons/svg/download.svg", 20, iconTint))
        binding.popupUrlIcon.setImageDrawable(svgDrawable("icons/svg/external.svg", 20, iconTint))
        binding.popupExtrasIcon.setImageDrawable(svgDrawable("icons/svg/extras.svg", 20, iconTint))
        binding.btnBottomAddIcon.setImageDrawable(svgDrawable("icons/svg/add.svg", 18, iconTint))
        runCatching { binding.btnRecordIcon.setImageDrawable(svgDrawable("icons/svg/record.svg", 18, iconTint)) }
        binding.btnMoreWrapper.visibility = View.INVISIBLE
    }

    fun refreshMoreBtn() {
        val hasConversation = chatFragment.chatHistoryNotEmpty
        if (hasConversation) {
            val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
            binding.btnMore.setImageDrawable(svgDrawable("icons/svg/more_vertical.svg", 16, iconTint))
        }
    }

    // ─── Tab Preview ──────────────────────────────────────────────────────────

    private fun setupBottomTabs() {
        refreshTabPreviewPill()
        binding.tabPreview.setOnClickListener { showPreviewModal() }
    }

    fun selectTab(tabId: Int) {
        if (currentTab == tabId) return
        currentTab = tabId
        refreshTabPreviewPill()
        updateContentForTab()
    }

    private fun updateContentForTab() {
        val isPreview = currentTab == R.id.tabPreview
        binding.previewState.visibility = if (isPreview) View.VISIBLE else View.GONE
        if (!isPreview) chatFragment.syncVisibility()
        else { binding.emptyState.visibility = View.GONE; binding.chatRecyclerView.visibility = View.GONE }
        animateBottomBarState()
        if (isPreview) chatFragment.hideInputRow() else chatFragment.showInputRow()
    }

    fun refreshTabPreviewPill() {
        val iconColor = activeIconColor
        binding.tabPreviewIcon.setImageDrawable(svgDrawable("icons/svg/preview_filled.svg", 20, iconColor))
        binding.tabPreviewLabel.setTextColor(iconColor)
        val pillBg = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(ContextCompat.getColor(this@MainActiviy, R.color.tab_preview_pill_bg))
        }
        binding.tabPreview.background = pillBg
    }

    // ─── Bottom bar animação ──────────────────────────────────────────────────

    fun animateBottomBarState() {
        val d = density
        val isPreview    = currentTab == R.id.tabPreview
        val targetMargin = if (isPreview) (MARGIN_PREVIEW_DP * d).toInt() else (MARGIN_CHAT_DP * d).toInt()
        val targetRadius = if (isPreview) RADIUS_PREVIEW_DP * d else RADIUS_CHAT_DP * d
        val bottomMargin = (BOTTOM_MARGIN_DP * d).toInt()
        if (currentBarMarginPx == targetMargin && currentBarRadiusPx == targetRadius) return
        val wrapperBg = binding.bottomNavWrapper.background as? GradientDrawable ?: return
        val fromMargin = currentBarMarginPx; val fromRadius = currentBarRadiusPx
        bottomBarAnimator?.cancel()
        bottomBarAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380; interpolator = DecelerateInterpolator(2.0f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val margin = (fromMargin + (targetMargin - fromMargin) * f).toInt()
                val radius = fromRadius + (targetRadius - fromRadius) * f
                currentBarMarginPx = margin; currentBarRadiusPx = radius
                wrapperBg.cornerRadius = radius
                (binding.bottomNavWrapper.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
                    it.marginStart = margin; it.marginEnd = margin; it.bottomMargin = bottomMargin
                    binding.bottomNavWrapper.layoutParams = it
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentBarMarginPx = targetMargin; currentBarRadiusPx = targetRadius
                    chatFragment.syncBlurBgSize()
                }
            })
            start()
        }
    }

    // ─── Botão ADD — popup menu iOS 26 ────────────────────────────────────────

    private var addPopup: PopupWindow? = null

    private fun setupBottomAddButton() {
        binding.btnBottomAdd.setOnClickListener { v -> showAddPopupMenu(v) }
    }

    private fun showAddPopupMenu(anchor: View) {
        if (addPopup?.isShowing == true) return
        val popupWidth = dp(220)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.dialog_background))
            }
            elevation = dp(12).toFloat()
            clipToOutline = true
        }

        fun menuRow(iconPath: String, label: String, dimmed: Boolean = false, action: () -> Unit): View {
            val color = ContextCompat.getColor(this, R.color.text_primary)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(50); setPadding(dp(16), 0, dp(16), 0)
                val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = a.getDrawable(0); a.recycle()
                isClickable = !dimmed; isFocusable = !dimmed
                alpha = if (dimmed) 0.38f else 1f
            }
            val iconFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).also { it.marginEnd = dp(12) }
                background = ContextCompat.getDrawable(this@MainActiviy, R.drawable.drawer_icon_bg)
            }
            iconFrame.addView(ImageView(this).apply {
                setImageDrawable(svgDrawable(iconPath, 13, color))
                layoutParams = FrameLayout.LayoutParams(dp(13), dp(13), Gravity.CENTER)
            })
            row.addView(iconFrame)
            row.addView(TextView(this).apply {
                text = label; textSize = 15f; setTextColor(color)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!dimmed) row.setOnClickListener { addPopup?.dismiss(); action() }
            return row
        }

        fun menuDiv() = View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActiviy, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.marginStart = dp(56) }
        }

        card.addView(menuRow("icons/svg/camera.svg", "Câmara") { openCamera() })
        card.addView(menuDiv())
        card.addView(menuRow("icons/svg/download.svg", "Importar Ficheiro") { })
        card.addView(menuDiv())
        card.addView(menuRow("icons/svg/external.svg", "URL / Link", dimmed = true) { })
        card.addView(menuDiv())
        card.addView(menuRow("icons/svg/extras.svg", "Extras") { chatFragment.showExtrasSheet() })

        val popup = PopupWindow(card, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true; elevation = dp(12).toFloat()
            setBackgroundDrawable(null); animationStyle = 0
        }
        addPopup = popup

        card.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupH = card.measuredHeight
        val xOff   = 0
        val yOff   = -(popupH + anchor.height + dp(8))

        card.scaleX = 0.85f; card.scaleY = 0.85f; card.alpha = 0f
        card.pivotX = dp(40).toFloat(); card.pivotY = popupH.toFloat()

        popup.showAsDropDown(anchor, xOff, yOff)

        card.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    // ─── Modal de voz ─────────────────────────────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceDialog: BottomSheetDialog? = null
    private var isListening = false

    fun showVoiceModal() {
        if (voiceDialog?.isShowing == true) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 102)
            return
        }

        val dialog = BottomSheetDialog(this, R.style.PreviewModalDialog)
        voiceDialog = dialog

        val screenH     = resources.displayMetrics.heightPixels
        val topGap      = dp(120)
        val topColor    = ContextCompat.getColor(this, R.color.gradient_warm_top)
        val bottomColor = ContextCompat.getColor(this, R.color.gradient_warm_bottom)
        val cornerPx    = dp(24).toFloat()

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(cornerPx, cornerPx, cornerPx, cornerPx, 0f, 0f, 0f, 0f)
                colors = intArrayOf(topColor, bottomColor)
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            clipToOutline = true
        }

        val handle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#30000000"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12)
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dp(32), dp(60), dp(32), dp(60))
        }

        val pulseRing = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.colorPrimary))
                alpha = 80
            }
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val micBtn = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.colorPrimary))
            }
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = -dp(86)
            }
            isClickable = true; isFocusable = true
        }
        runCatching {
            micBtn.addView(ImageView(this).apply {
                setImageDrawable(svgDrawable("icons/svg/record.svg", 28, Color.WHITE))
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
            })
        }

        val statusTv = TextView(this).apply {
            text = "A ouvir…"; textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(28); it.gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val transcriptTv = TextView(this).apply {
            text = ""; textSize = 15f
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_secondary))
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.4f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(12)
            }
        }

        content.addView(pulseRing)
        content.addView(micBtn)
        content.addView(statusTv)
        content.addView(transcriptTv)
        root.addView(content); root.addView(handle)

        val pulseAnim = ValueAnimator.ofFloat(1f, 1.4f, 1f).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                pulseRing.scaleX = s; pulseRing.scaleY = s
                pulseRing.alpha  = 1f - ((s - 1f) / 0.4f) * 0.7f
            }
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { isListening = true; pulseAnim.start(); statusTv.text = "A ouvir…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val scale = 1f + (rmsdB.coerceIn(0f, 10f) / 10f) * 0.3f
                micBtn.animate().scaleX(scale).scaleY(scale).setDuration(80).start()
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusTv.text = "A processar…" }
            override fun onError(error: Int) { pulseAnim.cancel(); isListening = false; statusTv.text = "Erro ao ouvir. Tenta de novo." }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull() ?: ""
                transcriptTv.text = finalText
                statusTv.text = "Concluído"
                pulseAnim.cancel(); isListening = false
                if (finalText.isNotBlank()) {
                    binding.inputMessage.setText(finalText)
                    binding.inputMessage.setSelection(finalText.length)
                    dialog.dismiss()
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                transcriptTv.text = partial
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        micBtn.setOnClickListener {
            if (isListening) speechRecognizer?.stopListening()
            else speechRecognizer?.startListening(recognizerIntent)
        }

        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams.height = screenH - topGap; sheet.requestLayout()
                val beh = BottomSheetBehavior.from(sheet)
                beh.peekHeight = screenH - topGap; beh.state = BottomSheetBehavior.STATE_EXPANDED
                beh.isDraggable = true; beh.isHideable = true
                beh.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(v: View, s: Int) {
                        if (s == BottomSheetBehavior.STATE_HIDDEN) { speechRecognizer?.destroy(); pulseAnim.cancel(); dialog.dismiss() }
                    }
                    override fun onSlide(v: View, o: Float) {}
                })
            }
            speechRecognizer?.startListening(recognizerIntent)
        }
        dialog.setOnDismissListener { speechRecognizer?.destroy(); pulseAnim.cancel() }
        dialog.show()
    }

    // ─── PopupMenu btnMore ────────────────────────────────────────────────────

    private var morePopup: PopupWindow? = null

    private fun setupPopupMenu() {
        binding.btnMore.setOnClickListener { v -> showMorePopup(v) }
        binding.popupOverlay.setOnClickListener { hidePopup() }
        binding.popupItemCamera.setOnClickListener { hidePopup(); openCamera() }
        binding.popupItemImport.setOnClickListener { hidePopup() }
        binding.popupItemUrl.setOnClickListener { }
        binding.popupItemExtras.setOnClickListener { chatFragment.showExtrasSheet() }
    }

    private fun showMorePopup(anchor: View) {
        if (morePopup?.isShowing == true) return
        val conv = chatFragment.currentConversationSnapshot ?: return
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val redColor = Color.parseColor("#FF3B30")
        val popupWidth = dp(220)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.dialog_background))
            }
            elevation = dp(8).toFloat(); clipToOutline = true
        }

        fun popupRow(iconPath: String, label: String, color: Int, action: () -> Unit): View {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52); setPadding(dp(16), 0, dp(16), 0)
                val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = a.getDrawable(0); a.recycle()
                isClickable = true; isFocusable = true
            }
            row.addView(ImageView(this).apply {
                setImageDrawable(svgDrawable(iconPath, 20, color))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginEnd = dp(14) }
            })
            row.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(color) })
            row.setOnClickListener { morePopup?.dismiss(); action() }
            return row
        }

        fun rowDiv() = View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActiviy, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.marginStart = dp(50) }
        }

        val pinLabel = if (conv.pinned) "Desafixar conversa" else "Fixar conversa"
        val pinIcon  = if (conv.pinned) "icons/svg/pin_filled.svg" else "icons/svg/pin.svg"
        card.addView(popupRow(pinIcon, pinLabel, iconTint) {
            lifecycleScope.launch { AuthApiService.pinConversation(authToken, conv.id, !conv.pinned); drawerManager.loadConversations() }
        })
        card.addView(rowDiv())

        val archLabel = if (conv.archived) "Desarquivar" else "Arquivar"
        val archIcon  = if (conv.archived) "icons/svg/bookmark_filled.svg" else "icons/svg/bookmark.svg"
        card.addView(popupRow(archIcon, archLabel, iconTint) {
            lifecycleScope.launch {
                AuthApiService.archiveConversation(authToken, conv.id, !conv.archived)
                if (conv.id == chatFragment.currentConversationId) chatFragment.startNewConversation()
                else drawerManager.loadConversations()
            }
        })
        card.addView(rowDiv())
        card.addView(popupRow("icons/svg/share.svg", "Partilhar", iconTint) {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, conv.title) }
            startActivity(Intent.createChooser(intent, "Partilhar conversa"))
        })
        card.addView(rowDiv())
        card.addView(popupRow("icons/svg/trash.svg", "Eliminar", redColor) { drawerManager.showDeleteConfirmation(conv) })

        val popup = PopupWindow(card, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true; elevation = dp(8).toFloat()
            setBackgroundDrawable(null); animationStyle = 0
        }
        morePopup = popup

        card.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupH = card.measuredHeight
        val anchorLoc = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val anchorBottom = anchorLoc[1] + anchor.height
        val screenH = resources.displayMetrics.heightPixels
        val xOff = -(popupWidth - anchor.width)
        val yOff = if (anchorBottom + popupH + dp(8) < screenH) dp(4) else -(popupH + anchor.height + dp(4))

        card.scaleX = 0.85f; card.scaleY = 0.85f; card.alpha = 0f
        card.pivotX = popupWidth.toFloat(); card.pivotY = 0f

        popup.showAsDropDown(anchor, xOff, yOff)

        card.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(300).setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    fun showConvOptionsSheet() {
        val conv = chatFragment.currentConversationSnapshot ?: return
        drawerManager.showConversationOptions(conv)
    }

    fun showPopup() {
        if (popupVisible) return
        popupVisible = true
        binding.inputMessage.isEnabled = false; binding.inputMessage.isFocusable = false
        hideKeyboard()
        binding.popupOverlay.visibility = View.VISIBLE; binding.popupOverlay.alpha = 0f
        binding.popupMenu.post {
            binding.popupMenu.pivotX = binding.popupMenu.width.toFloat(); binding.popupMenu.pivotY = 0f
            binding.popupMenu.scaleX = 0.85f; binding.popupMenu.scaleY = 0.85f; binding.popupMenu.alpha = 0f
            binding.bottomNavWrapper.animate().alpha(0.35f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
            binding.popupOverlay.animate().alpha(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
            binding.popupMenu.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.2f)).start()
        }
    }

    fun hidePopup() {
        if (!popupVisible) return
        popupVisible = false
        binding.inputMessage.isEnabled = true; binding.inputMessage.isFocusable = true; binding.inputMessage.isFocusableInTouchMode = true
        binding.bottomNavWrapper.animate().alpha(1f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        binding.popupMenu.animate().scaleX(0.85f).scaleY(0.85f).alpha(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        binding.popupOverlay.animate().alpha(0f).setDuration(180).setInterpolator(DecelerateInterpolator())
            .withEndAction { binding.popupOverlay.visibility = View.GONE }.start()
    }

    // ─── Câmara ───────────────────────────────────────────────────────────────

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }
        startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
    }

    // ─── Drawer ───────────────────────────────────────────────────────────────

    private fun setupDrawer() {
        binding.btnMenu.setOnClickListener { if (drawerOpen) closeDrawer() else openDrawer() }
        binding.btnNewChat.setOnClickListener { chatFragment.startNewConversation() }
        binding.drawerScrim.setOnClickListener { closeDrawer() }
        binding.drawerItemSettings.setOnClickListener {
            closeDrawer()
            binding.root.postDelayed({ startActivity(Intent(this, SettingsActivity::class.java)) }, 250)
        }
    }

    fun openDrawer() {
        if (drawerOpen) return
        hideKeyboard(); drawerOpen = true
        binding.drawerScrim.visibility = View.VISIBLE
        drawerManager.loadConversations()
        animateDrawer(binding.coordinatorLayout.translationX, drawerWidth.toFloat())
    }

    fun closeDrawer() {
        if (!drawerOpen) return; drawerOpen = false
        animateDrawer(binding.coordinatorLayout.translationX, 0f) { binding.drawerScrim.visibility = View.GONE }
    }

    private fun animateDrawer(from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        drawerAnimator?.cancel()
        drawerAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = 300; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                binding.coordinatorLayout.translationX = v
                binding.coordinatorLayout.elevation    = 8f + ((v / drawerWidth) * 16f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
            })
            start()
        }
    }

    // ─── Swipe drawer ─────────────────────────────────────────────────────────

    private fun setupSwipeDrawer() {
        val edgePx    = SWIPE_EDGE_WIDTH * density
        val minDistPx = SWIPE_MIN_DIST * density
        binding.coordinatorLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX; swipeStartY = event.rawY
                    isSwipingDrawer = !drawerOpen && swipeStartX < edgePx; false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSwipingDrawer) {
                        val dx = event.rawX - swipeStartX; val dy = event.rawY - swipeStartY
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx > 0) {
                            val progress = (dx / drawerWidth).coerceIn(0f, 1f)
                            binding.coordinatorLayout.translationX = dx.coerceAtMost(drawerWidth.toFloat())
                            binding.coordinatorLayout.elevation    = 8f + progress * 16f
                            binding.drawerScrim.visibility = View.VISIBLE; true
                        } else false
                    } else if (drawerOpen) {
                        val dx = event.rawX - swipeStartX
                        if (dx < 0) {
                            val newX = (drawerWidth + dx).coerceAtLeast(0f)
                            binding.coordinatorLayout.translationX = newX
                            binding.coordinatorLayout.elevation    = 8f + (newX / drawerWidth) * 16f; true
                        } else false
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - swipeStartX
                    if (isSwipingDrawer) {
                        isSwipingDrawer = false
                        if (dx > minDistPx) { drawerOpen = true; animateDrawer(binding.coordinatorLayout.translationX, drawerWidth.toFloat()) }
                        else animateDrawer(binding.coordinatorLayout.translationX, 0f) { binding.drawerScrim.visibility = View.GONE }
                        true
                    } else if (drawerOpen && dx < -minDistPx) { closeDrawer(); true }
                    else false
                }
                else -> false
            }
        }
    }

    // ─── Utilitários ─────────────────────────────────────────────────────────

    private fun sheetHandle() = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
            setColor(ContextCompat.getColor(this@MainActiviy, R.color.divider))
        }
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12); it.bottomMargin = dp(8)
        }
    }

    fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val v = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(v.windowToken, 0)
        binding.inputMessage.clearFocus()
    }

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun svgDrawable(path: String, sizeDp: Int, tint: Int): android.graphics.drawable.BitmapDrawable {
        val px  = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        runCatching {
            com.caverock.androidsvg.SVG.getFromAsset(assets, path).apply {
                documentWidth = px.toFloat(); documentHeight = px.toFloat()
                renderToCanvas(android.graphics.Canvas(bmp))
            }
        }
        return android.graphics.drawable.BitmapDrawable(resources, bmp).also {
            it.setColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN)
        }
    }

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onResume() { super.onResume(); refreshTabPreviewPill() }

    override fun onPause() { super.onPause(); chatFragment.saveCurrentConversation() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (morePopup?.isShowing == true) { morePopup?.dismiss(); return }
        if (addPopup?.isShowing == true) { addPopup?.dismiss(); return }
        if (popupVisible) { hidePopup(); return }
        if (drawerOpen)   { closeDrawer(); return }
        super.onBackPressed()
    }
}