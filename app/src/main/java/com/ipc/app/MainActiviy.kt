// MainActiviy.kt
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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caverock.androidsvg.SVG
import com.ipc.app.data.ChatMessage
import com.ipc.app.data.NvidiaApiService
import com.ipc.app.data.StreamChunk
import com.ipc.app.databinding.ActivityMainBinding
import com.ipc.app.ui.BaseActivity
import com.ipc.app.ui.LoginActivity
import com.ipc.app.ui.SettingsActivity
import com.ipc.app.ui.UserProfileActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    private var inputHeightAnimator: ValueAnimator? = null
    private var sendBtnVisible = false
    private var inputRowVisible = true
    private var inputRowHeight = 0
    private var currentTab = R.id.tabChat
    private var keyboardOpen = false
    private var keyboardShift = 0f

    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var isSwipingDrawer = false
    private val SWIPE_EDGE_WIDTH = 40f
    private val SWIPE_MIN_DIST = 30f

    private val MARGIN_CHAT_DP    = 16f
    private val MARGIN_PREVIEW_DP = 36f
    private val RADIUS_CHAT_DP    = 20f
    private val RADIUS_PREVIEW_DP = 38f
    private val BOTTOM_MARGIN_DP  = 20f

    private var currentBarMarginPx: Int = -1
    private var currentBarRadiusPx: Float = -1f
    private var frozenInputRowHeight: Int = 0
    private var inputRowHeightFrozen = false
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    private val chatHistory = mutableListOf<ChatMessage>()
    private val displayMessages = mutableListOf<DisplayMessage>()
    private var streamJob: Job? = null
    private lateinit var chatAdapter: ChatAdapter

    data class DisplayMessage(
        val role: String,
        var content: String,
        var isStreaming: Boolean = false
    )

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    private val activeIconColor: Int
        get() = if (isAppDarkMode) Color.WHITE else Color.BLACK
    private val inactiveIconColor: Int
        get() = Color.parseColor("#888888")
    private val drawerWidth: Int
        get() = (resources.displayMetrics.widthPixels * 0.75f).toInt()
    private val density: Float
        get() = resources.displayMetrics.density

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
            val imeNowOpen = extraShift > 0

            binding.bottomNavWrapper.animate()
                .translationY(-extraShift.toFloat())
                .setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
            binding.bottomBlurBg.animate()
                .translationY(-extraShift.toFloat())
                .setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
            binding.bottomNavWrapper.updatePadding(
                bottom = if (extraShift == 0) navInsets.bottom else 0
            )

            if (imeNowOpen && !keyboardOpen) {
                keyboardOpen = true
                keyboardShift = -(extraShift * 0.55f)
                binding.emptyState.animate()
                    .translationY(keyboardShift)
                    .setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
            } else if (!imeNowOpen && keyboardOpen) {
                keyboardOpen = false
                keyboardShift = 0f
                binding.emptyState.animate()
                    .translationY(0f)
                    .setDuration(300).setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
            insets
        }

        binding.drawerContainer.layoutParams = binding.drawerContainer.layoutParams.also {
            it.width = drawerWidth
        }
        binding.inputRow.post {
            if (inputRowHeight == 0) {
                inputRowHeight = binding.inputRow.height
                frozenInputRowHeight = inputRowHeight
            }
        }

        currentBarMarginPx = (MARGIN_CHAT_DP * density).toInt()
        currentBarRadiusPx = RADIUS_CHAT_DP * density

        setupBlurBehindBottomBar()
        setupLogo()
        setupGreeting()
        setupIcons()
        setupDrawer()
        setupSwipeDrawer()
        setupBottomTabs()
        setupPreviewImage()
        setupInput()
        setupPopupMenu()
        setupChatRecycler()
        setupAvatarBtn()
    }

    private fun setupChatRecycler() {
        chatAdapter = ChatAdapter(displayMessages)
        val llm = LinearLayoutManager(this)
        llm.stackFromEnd = true
        binding.chatRecyclerView.layoutManager = llm
        binding.chatRecyclerView.adapter = chatAdapter
        binding.chatRecyclerView.overScrollMode = View.OVER_SCROLL_NEVER
    }

   private fun sendChatMessage(text: String) {
    if (text.isBlank() || streamJob?.isActive == true) return

    binding.emptyState.visibility = View.GONE
    binding.chatRecyclerView.visibility = View.VISIBLE

    chatHistory.add(ChatMessage("user", text))
    displayMessages.add(DisplayMessage("user", text))
    chatAdapter.notifyItemInserted(displayMessages.lastIndex)
    binding.chatRecyclerView.smoothScrollToPosition(displayMessages.lastIndex)

    val aiMsg = DisplayMessage("assistant", "", isStreaming = true)
    displayMessages.add(aiMsg)
    val aiIndex = displayMessages.lastIndex
    chatAdapter.notifyItemInserted(aiIndex)
    binding.chatRecyclerView.smoothScrollToPosition(aiIndex)

    val lang = prefs.getString("language", "pt") ?: "pt"
    val authToken = prefs.getString("auth_token", "") ?: ""
    val systemPrompt = NvidiaApiService.buildSystemPrompt(lang)

    streamJob = lifecycleScope.launch {
        NvidiaApiService.streamChat(chatHistory, systemPrompt, authToken)
            .collect { chunk ->
                when (chunk) {
                    is StreamChunk.Token -> {
                        aiMsg.content += chunk.text
                        chatAdapter.notifyItemChanged(aiIndex)
                        binding.chatRecyclerView.scrollToPosition(aiIndex)
                    }
                    is StreamChunk.Done -> {
                        aiMsg.isStreaming = false
                        if (aiMsg.content.isBlank()) aiMsg.content = chunk.fullText
                        chatHistory.add(ChatMessage("assistant", aiMsg.content))
                        chatAdapter.notifyItemChanged(aiIndex)
                        binding.chatRecyclerView.scrollToPosition(aiIndex)
                    }
                    is StreamChunk.Error -> {
                        aiMsg.isStreaming = false
                        aiMsg.content = "⚠️ ${chunk.message}"
                        chatAdapter.notifyItemChanged(aiIndex)
                    }
                }
            }
    }
}

    inner class ChatAdapter(
        private val msgs: List<DisplayMessage>
    ) : RecyclerView.Adapter<ChatAdapter.VH>() {

        inner class VH(val wrapper: FrameLayout, val tv: TextView) : RecyclerView.ViewHolder(wrapper)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                textSize = 15f
                setLineSpacing(0f, 1.5f)
                maxWidth = (parent.width * 0.84f).toInt()
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            val wrapper = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = dp(4)
                    it.bottomMargin = dp(4)
                }
            }
            wrapper.addView(tv)
            return VH(wrapper, tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = msgs[position]
            val tvLp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            if (msg.role == "user") {
                holder.tv.setTextColor(Color.WHITE)
                holder.tv.background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(ContextCompat.getColor(holder.tv.context, R.color.colorPrimary))
                }
                tvLp.gravity = Gravity.END
                tvLp.marginStart = dp(56)
                tvLp.marginEnd = 0
                holder.tv.text = msg.content
            } else {
                holder.tv.setTextColor(ContextCompat.getColor(holder.tv.context, R.color.text_primary))
                holder.tv.background = null
                holder.tv.setPadding(dp(4), dp(6), dp(4), dp(6))
                tvLp.gravity = Gravity.START
                tvLp.marginStart = 0
                tvLp.marginEnd = dp(32)
                val content = if (msg.isStreaming && msg.content.isEmpty()) "…" else msg.content
                holder.tv.text = parseMarkdown(content)
            }
            holder.tv.layoutParams = tvLp
        }

        override fun getItemCount() = msgs.size
    }

    private fun parseMarkdown(text: String): Spanned {
        val html = text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("__(.+?)__"), "<b>$1</b>")
            .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")
            .replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "<i>$1</i>")
            .replace(Regex("`(.+?)`"), "<tt>$1</tt>")
            .replace(Regex("^#{1,2}\\s+(.+)$", RegexOption.MULTILINE), "<b><big>$1</big></b>")
            .replace(Regex("^#{3,}\\s+(.+)$", RegexOption.MULTILINE), "<b>$1</b>")
            .replace(Regex("^[-•]\\s+(.+)$", RegexOption.MULTILINE), "• $1")
            .replace("\n", "<br>")
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    }

    private fun setupAvatarBtn() {
        val name = prefs.getString("auth_user_name", "U") ?: "U"
        binding.btnUserAvatarInitial.text = name.firstOrNull()?.uppercase() ?: "U"
        binding.btnUserAvatar.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }
    }

    private fun setupBlurBehindBottomBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.bottomBlurBg.setRenderEffect(
                RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
            )
        }
        val blurBg = GradientDrawable().apply {
            cornerRadius = currentBarRadiusPx
            setColor(Color.TRANSPARENT)
        }
        binding.bottomBlurBg.background = blurBg
        binding.bottomNavWrapper.post { syncBlurBgSize() }
    }

    private fun syncBlurBgSize() {
        val wh = binding.bottomNavWrapper.height
        if (wh > 0 && binding.bottomBlurBg.layoutParams.height != wh) {
            binding.bottomBlurBg.layoutParams = binding.bottomBlurBg.layoutParams.also {
                it.height = wh
            }
        }
    }

    private fun animateBottomBarState() {
        val d = density
        val isPreview = currentTab == R.id.tabPreview
        val targetMargin = if (isPreview) (MARGIN_PREVIEW_DP * d).toInt() else (MARGIN_CHAT_DP * d).toInt()
        val targetRadius = if (isPreview) RADIUS_PREVIEW_DP * d else RADIUS_CHAT_DP * d
        val bottomMargin = (BOTTOM_MARGIN_DP * d).toInt()
        if (currentBarMarginPx == targetMargin && currentBarRadiusPx == targetRadius) return

        val wrapperBg = getOrCreateBottomBg()
        val blurBg = binding.bottomBlurBg.background as? GradientDrawable
        val fromMargin = currentBarMarginPx
        val fromRadius = currentBarRadiusPx

        bottomBarAnimator?.cancel()
        bottomBarAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380; interpolator = DecelerateInterpolator(2.0f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val margin = (fromMargin + (targetMargin - fromMargin) * f).toInt()
                val radius = fromRadius + (targetRadius - fromRadius) * f
                currentBarMarginPx = margin; currentBarRadiusPx = radius
                wrapperBg.cornerRadius = radius; blurBg?.cornerRadius = radius
                (binding.bottomNavWrapper.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
                    it.marginStart = margin; it.marginEnd = margin; it.bottomMargin = bottomMargin
                    binding.bottomNavWrapper.layoutParams = it
                }
                (binding.bottomBlurBg.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
                    it.marginStart = margin; it.marginEnd = margin; it.bottomMargin = bottomMargin
                    binding.bottomBlurBg.layoutParams = it
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentBarMarginPx = targetMargin; currentBarRadiusPx = targetRadius
                    syncBlurBgSize()
                }
            })
            start()
        }
    }

    private fun getOrCreateBottomBg(): GradientDrawable {
        return (binding.bottomNavWrapper.background as? GradientDrawable)
            ?: GradientDrawable().also { gd ->
                gd.setColor(if (isAppDarkMode)
                    ContextCompat.getColor(this, R.color.card_background)
                else Color.parseColor("#EAFFFFFF"))
                gd.cornerRadius = currentBarRadiusPx
                binding.bottomNavWrapper.background = gd
            }
    }

    private fun setupInput() {
        binding.inputMessage.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val newH = bottom - top; val oldH = oldBottom - oldTop
            if (newH == oldH || newH <= 0 || oldH <= 0 || !inputRowVisible) return@addOnLayoutChangeListener
            val delta = newH - oldH
            val fromH = if (inputRowHeightFrozen) frozenInputRowHeight else binding.inputRow.height
            if (fromH <= 0) return@addOnLayoutChangeListener
            val toH = (fromH + delta).coerceAtLeast(1)
            if (fromH == toH) return@addOnLayoutChangeListener
            preDrawListener?.let { binding.inputMessage.viewTreeObserver.removeOnPreDrawListener(it) }
            preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.inputMessage.viewTreeObserver.removeOnPreDrawListener(this)
                    preDrawListener = null
                    inputRowHeightFrozen = true; frozenInputRowHeight = fromH
                    binding.inputRow.layoutParams = binding.inputRow.layoutParams.also { it.height = fromH }
                    inputHeightAnimator?.cancel()
                    inputHeightAnimator = ValueAnimator.ofInt(fromH, toH).apply {
                        duration = 180; interpolator = DecelerateInterpolator(1.5f)
                        addUpdateListener { anim ->
                            val h = anim.animatedValue as Int
                            frozenInputRowHeight = h
                            binding.inputRow.layoutParams = binding.inputRow.layoutParams.also { it.height = h }
                            syncBlurBgSize()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                inputRowHeightFrozen = false
                                binding.inputRow.layoutParams = binding.inputRow.layoutParams.also {
                                    it.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                }
                                syncBlurBgSize()
                            }
                        })
                        start()
                    }
                    return false
                }
            }
            binding.inputMessage.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        }

        binding.inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                if (hasText && !sendBtnVisible) showSendBtn()
                else if (!hasText && sendBtnVisible) hideSendBtn()
            }
        })

        binding.btnSend.setOnClickListener {
            val text = binding.inputMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.inputMessage.text?.clear()
                sendChatMessage(text)
            }
        }
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
            duration = 280; interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                binding.inputRow.layoutParams = binding.inputRow.layoutParams.also { it.height = h }
                binding.inputRow.alpha = h.toFloat() / targetH
                syncBlurBgSize()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.inputRow.layoutParams = binding.inputRow.layoutParams.also {
                        it.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    binding.inputRow.alpha = 1f
                    frozenInputRowHeight = binding.inputRow.height
                    syncBlurBgSize()
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
            duration = 240; interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                binding.inputRow.layoutParams = binding.inputRow.layoutParams.also { it.height = h }
                binding.inputRow.alpha = h.toFloat() / fromH
                syncBlurBgSize()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.inputRow.visibility = View.GONE
                    binding.inputRow.alpha = 1f
                    syncBlurBgSize()
                }
            })
            start()
        }
    }

    private fun showSendBtn() {
        sendBtnVisible = true; binding.btnSend.visibility = View.VISIBLE
        sendBtnAnimator?.cancel()
        sendBtnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180; interpolator = DecelerateInterpolator()
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
        sendBtnVisible = false; sendBtnAnimator?.cancel()
        sendBtnAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 150; interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                binding.btnSend.alpha = v
                binding.btnSend.scaleX = 0.7f + (v * 0.3f)
                binding.btnSend.scaleY = 0.7f + (v * 0.3f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { binding.btnSend.visibility = View.GONE }
            })
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
        binding.emptyGreeting.text = when {
            hour < 12 -> "Bom dia"; hour < 18 -> "Boa tarde"; else -> "Boa noite"
        }
        runCatching {
            val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
            binding.emptyGreeting.typeface = Typeface.create(tf, Typeface.BOLD)
            binding.emptySubtitle.typeface = tf
            binding.previewTitle.typeface = Typeface.create(tf, Typeface.BOLD)
            binding.previewSubtitle.typeface = tf
            binding.drawerAppName.typeface = Typeface.create(tf, Typeface.BOLD)
        }
    }

    private fun setupIcons() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val iconSec  = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        binding.btnMenu.setImageDrawable(svgDrawable("icons/svg/side_panel.svg", 16, iconTint))
        binding.btnMore.setImageDrawable(svgDrawable("icons/svg/more_vertical.svg", 16, iconTint))
        binding.drawerIconSettings.setImageDrawable(svgDrawable("icons/svg/settings.svg", 14, iconTint))
        binding.drawerChevronSettings.setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        binding.drawerNewChatIcon.setImageDrawable(svgDrawable("icons/svg/add.svg", 14, iconTint))
        binding.drawerNewChatChevron.setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        binding.popupImportIcon.setImageDrawable(svgDrawable("icons/svg/download.svg", 18, iconTint))
        binding.popupCameraIcon.setImageDrawable(svgDrawable("icons/svg/preview.svg", 18, iconTint))
        binding.popupUrlIcon.setImageDrawable(svgDrawable("icons/svg/external.svg", 18, iconTint))
    }

    private fun setupPopupMenu() {
        binding.btnMore.setOnClickListener { showPopup() }
        binding.popupOverlay.setOnClickListener { hidePopup() }
        binding.popupItemImport.setOnClickListener { hidePopup() }
        binding.popupItemCamera.setOnClickListener { hidePopup() }
        binding.popupItemUrl.setOnClickListener { hidePopup() }
    }

    private fun showPopup() {
        if (popupVisible) return; popupVisible = true
        binding.popupOverlay.visibility = View.VISIBLE; binding.popupOverlay.alpha = 0f
        binding.popupMenu.post {
            binding.popupMenu.pivotX = binding.popupMenu.width.toFloat(); binding.popupMenu.pivotY = 0f
            binding.popupMenu.scaleX = 0.85f; binding.popupMenu.scaleY = 0.85f; binding.popupMenu.alpha = 0f
            binding.bottomNavWrapper.animate().alpha(0.35f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
            binding.popupOverlay.animate().alpha(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
            binding.popupMenu.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.2f)).start()
        }
    }

    private fun hidePopup() {
        if (!popupVisible) return; popupVisible = false
        binding.bottomNavWrapper.animate().alpha(1f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        binding.popupMenu.animate().scaleX(0.85f).scaleY(0.85f).alpha(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        binding.popupOverlay.animate().alpha(0f).setDuration(180).setInterpolator(DecelerateInterpolator())
            .withEndAction { binding.popupOverlay.visibility = View.GONE }.start()
    }

    private fun setupSwipeDrawer() {
        val edgePx = SWIPE_EDGE_WIDTH * density; val minDistPx = SWIPE_MIN_DIST * density
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
                            binding.coordinatorLayout.translationX = dx.coerceAtMost(drawerWidth.toFloat())
                            binding.drawerScrim.visibility = View.VISIBLE; true
                        } else false
                    } else if (drawerOpen) {
                        val dx = event.rawX - swipeStartX
                        if (dx < 0) { binding.coordinatorLayout.translationX = (drawerWidth + dx).coerceAtLeast(0f); true } else false
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - swipeStartX
                    if (isSwipingDrawer) {
                        isSwipingDrawer = false
                        if (dx > minDistPx) { drawerOpen = true; animateDrawer(binding.coordinatorLayout.translationX, drawerWidth.toFloat()) }
                        else animateDrawer(binding.coordinatorLayout.translationX, 0f) { binding.drawerScrim.visibility = View.GONE }
                        true
                    } else if (drawerOpen && dx < -minDistPx) { closeDrawer(); true } else false
                }
                else -> false
            }
        }
    }

    private fun setupDrawer() {
        binding.btnMenu.setOnClickListener { if (drawerOpen) closeDrawer() else openDrawer() }
        binding.drawerScrim.setOnClickListener { closeDrawer() }
        binding.drawerItemSettings.setOnClickListener {
            closeDrawer()
            binding.root.postDelayed({ startActivity(Intent(this, SettingsActivity::class.java)) }, 250)
        }
        binding.drawerNewChat.setOnClickListener {
            closeDrawer()
            binding.root.postDelayed({
                chatHistory.clear()
                displayMessages.clear()
                chatAdapter.notifyDataSetChanged()
                binding.chatRecyclerView.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
            }, 250)
        }
        // drawerItemLogout removido — logout foi movido para SettingsActivity
    }

    private fun showLogoutDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.dialog_background))
            }
        }

        dialogView.addView(TextView(this).apply {
            text = "Terminar sessão"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
        })

        dialogView.addView(TextView(this).apply {
            text = "Tens a certeza que queres sair?"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_secondary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(24) }
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = TextView(this).apply {
            text = "Cancelar"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_secondary))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.card_background))
            }
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginEnd = dp(8)
            }
        }

        val logoutBtn = TextView(this).apply {
            text = "Sair"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#FF3B30"))
            }
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(logoutBtn)
        dialogView.addView(btnRow)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        cancelBtn.setOnClickListener { dialog.dismiss() }
        logoutBtn.setOnClickListener { dialog.dismiss(); doLogout() }
        dialog.show()
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

    private fun openDrawer() {
        if (drawerOpen) return; drawerOpen = true
        binding.drawerScrim.visibility = View.VISIBLE
        animateDrawer(binding.coordinatorLayout.translationX, drawerWidth.toFloat())
    }

    private fun closeDrawer() {
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
        if (currentTab == tabId) return; currentTab = tabId
        refreshTabIcons(); updateContentForTab()
    }

    private fun updateContentForTab() {
        val isPreview = currentTab == R.id.tabPreview
        binding.previewState.visibility = if (isPreview) View.VISIBLE else View.GONE
        if (!isPreview) {
            if (displayMessages.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.chatRecyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.chatRecyclerView.visibility = View.VISIBLE
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.chatRecyclerView.visibility = View.GONE
        }
        animateBottomBarState()
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
        val active = activeIconColor; val inactive = inactiveIconColor
        binding.tabChatIcon.setImageDrawable(
            if (currentTab == R.id.tabChat) svgDrawable("icons/svg/chat_filled.svg", 22, active)
            else svgDrawable("icons/svg/chat.svg", 22, inactive)
        )
        binding.tabChatLabel.setTextColor(if (currentTab == R.id.tabChat) active else inactive)
        binding.tabPreviewIcon.setImageDrawable(
            if (currentTab == R.id.tabPreview) svgDrawable("icons/svg/preview_filled.svg", 22, active)
            else svgDrawable("icons/svg/preview.svg", 22, inactive)
        )
        binding.tabPreviewLabel.setTextColor(if (currentTab == R.id.tabPreview) active else inactive)
    }

    override fun onResume() {
        super.onResume()
        refreshTabIcons()
        val name = prefs.getString("auth_user_name", "U") ?: "U"
        binding.btnUserAvatarInitial.text = name.firstOrNull()?.uppercase() ?: "U"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (popupVisible) { hidePopup(); return }
        if (drawerOpen) { closeDrawer(); return }
        super.onBackPressed()
    }

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