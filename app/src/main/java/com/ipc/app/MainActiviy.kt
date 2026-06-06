package com.ipc.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caverock.androidsvg.SVG
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.ipc.app.data.AuthApiService
import com.ipc.app.data.ChatMessage
import com.ipc.app.data.Conversation
import com.ipc.app.data.NvidiaApiService
import com.ipc.app.data.StreamChunk
import com.ipc.app.databinding.ActivityMainBinding
import com.ipc.app.ui.BaseActivity
import com.ipc.app.ui.LoginActivity
import com.ipc.app.ui.SettingsActivity
import com.ipc.app.ui.UserProfileActivity
import kotlinx.coroutines.Job
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

    // Swipe drawer
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

    // Extras state
    private var flashMode = false
    private var thinkMoreMode = false
    private var sheetsEnabled = false

    private var currentConversationId: String = ""
    private var currentConversationTitle: String = "Nova conversa"
    private var titleGenerated = false
    private var thinkingContent = ""

    private val drawerConversations = mutableListOf<Conversation>()
    private val chatHistory = mutableListOf<ChatMessage>()
    private val displayMessages = mutableListOf<DisplayMessage>()
    private var streamJob: Job? = null
    private lateinit var chatAdapter: ChatAdapter

    data class DisplayMessage(
        val role: String,
        var content: String,
        var isStreaming: Boolean = false,
        var isThinking: Boolean = false,
        var thinkingContent: String = ""
    )

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }
    private val authToken get() = prefs.getString("auth_token", "") ?: ""

    private val activeIconColor: Int
        get() = if (isAppDarkMode) Color.WHITE else Color.BLACK
    private val inactiveIconColor: Int
        get() = Color.parseColor("#888888")
    private val drawerWidth: Int
        get() = (resources.displayMetrics.widthPixels * 0.75f).toInt()
    private val density: Float
        get() = resources.displayMetrics.density

    // Nova conversa só ativa se já há histórico
    private val newChatEnabled: Boolean
        get() = chatHistory.isNotEmpty() || currentConversationId.isNotEmpty()

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
            binding.bottomNavWrapper.updatePadding(
                bottom = if (extraShift == 0) navInsets.bottom else 0
            )

            // Fix ponto 5: não mover o chat se já tiver conteúdo visível
            if (imeNowOpen && !keyboardOpen) {
                keyboardOpen = true
                if (displayMessages.isEmpty()) {
                    binding.emptyState.animate()
                        .translationY(-(extraShift * 0.45f))
                        .setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
                }
                // RecyclerView: só scroll suave para o último item, sem translationY
                if (displayMessages.isNotEmpty()) {
                    binding.chatRecyclerView.post {
                        binding.chatRecyclerView.smoothScrollToPosition(displayMessages.lastIndex)
                    }
                }
            } else if (!imeNowOpen && keyboardOpen) {
                keyboardOpen = false
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

        setupBottomBarSolid()
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
        loadDrawerConversations()
        refreshNewChatBtn()
    }

    // ─── Nova conversa ────────────────────────────────────────────────────────

    private fun refreshNewChatBtn() {
        val enabled = newChatEnabled
        binding.btnNewChat.alpha = if (enabled) 1f else 0.35f
        binding.btnNewChat.isClickable = enabled
        binding.btnNewChat.isFocusable = enabled
    }

    // ─── Bottom bar ───────────────────────────────────────────────────────────

    private fun setupBottomBarSolid() {
        val bg = GradientDrawable().apply {
            cornerRadius = currentBarRadiusPx
            setColor(ContextCompat.getColor(this@MainActiviy, R.color.bottom_bar_solid))
        }
        binding.bottomNavWrapper.background = bg
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupChatRecycler() {
        chatAdapter = ChatAdapter(displayMessages)
        val llm = LinearLayoutManager(this)
        llm.stackFromEnd = true
        binding.chatRecyclerView.layoutManager = llm
        binding.chatRecyclerView.adapter = chatAdapter
        binding.chatRecyclerView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    // ─── Conversas ────────────────────────────────────────────────────────────

    private fun loadDrawerConversations() {
        lifecycleScope.launch {
            val list = AuthApiService.listConversations(authToken)
            drawerConversations.clear()
            drawerConversations.addAll(list)
            refreshDrawerConversations()
        }
    }

    private fun saveCurrentConversation() {
        if (chatHistory.isEmpty()) return
        val token = authToken
        val title = currentConversationTitle
        val msgs  = chatHistory.toList()
        val id    = currentConversationId
        lifecycleScope.launch {
            if (id.isEmpty()) {
                val newId = AuthApiService.createConversation(token, title, msgs)
                if (newId != null) {
                    currentConversationId = newId
                    loadDrawerConversations()
                }
            } else {
                AuthApiService.updateConversation(token, id, title, msgs)
                loadDrawerConversations()
            }
        }
    }

    private fun loadConversation(conv: Conversation) {
        currentConversationId    = conv.id
        currentConversationTitle = conv.title
        titleGenerated = true
        chatHistory.clear()
        chatHistory.addAll(conv.messages)
        displayMessages.clear()
        conv.messages.forEach { displayMessages.add(DisplayMessage(it.role, it.content)) }
        chatAdapter.notifyDataSetChanged()
        if (displayMessages.isNotEmpty()) binding.chatRecyclerView.scrollToPosition(displayMessages.lastIndex)
        binding.chatRecyclerView.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        refreshNewChatBtn()
    }

    /** Agrupa conversas por período: "7 Dias", "30 Dias", "MM/AAAA" */
    private fun groupConversations(list: List<Conversation>): List<Any> {
        val now = System.currentTimeMillis()
        val day7  = now - 7L  * 24 * 3600 * 1000
        val day30 = now - 30L * 24 * 3600 * 1000

        val result = mutableListOf<Any>()
        val g7    = list.filter { it.updatedAt >= day7 }
        val g30   = list.filter { it.updatedAt < day7 && it.updatedAt >= day30 }
        val older = list.filter { it.updatedAt < day30 }
            .groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.updatedAt }
                String.format("%02d/%04d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
            }

        if (g7.isNotEmpty())  { result.add("7 Dias");  result.addAll(g7) }
        if (g30.isNotEmpty()) { result.add("30 Dias"); result.addAll(g30) }
        older.entries.sortedByDescending { it.key }.forEach { (label, convs) ->
            result.add(label); result.addAll(convs)
        }
        return result
    }

    private fun refreshDrawerConversations() {
        val container = binding.drawerConversationsList
        container.removeAllViews()
        val grouped = groupConversations(drawerConversations)

        grouped.forEach { item ->
            when (item) {
                is String -> {
                    // Cabeçalho de período
                    container.addView(TextView(this).apply {
                        text = item
                        textSize = 11f
                        setTypeface(null, Typeface.NORMAL)
                        setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.settings_section_label))
                        setPadding(dp(24), dp(12), dp(24), dp(4))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    })
                }
                is Conversation -> {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(24), 0, dp(24), 0)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
                        )
                        val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                        background = a.getDrawable(0); a.recycle()
                        isClickable = true; isFocusable = true
                    }
                    row.addView(TextView(this).apply {
                        text = item.title
                        textSize = 14.5f
                        setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.drawer_text))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    row.setOnClickListener {
                        closeDrawer()
                        binding.root.postDelayed({ loadConversation(item) }, 250)
                    }
                    container.addView(row)
                }
            }
        }
    }

    private fun startNewConversation() {
        if (!newChatEnabled) return
        saveCurrentConversation()
        currentConversationId    = ""
        currentConversationTitle = "Nova conversa"
        titleGenerated = false
        chatHistory.clear()
        displayMessages.clear()
        chatAdapter.notifyDataSetChanged()
        binding.chatRecyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        closeDrawer()
        refreshNewChatBtn()
    }

    // ─── Enviar mensagem ──────────────────────────────────────────────────────

    private fun sendChatMessage(text: String) {
        if (text.isBlank() || streamJob?.isActive == true) return

        binding.emptyState.visibility = View.GONE
        binding.chatRecyclerView.visibility = View.VISIBLE

        chatHistory.add(ChatMessage("user", text))
        displayMessages.add(DisplayMessage("user", text))
        chatAdapter.notifyItemInserted(displayMessages.lastIndex)
        binding.chatRecyclerView.scrollToPosition(displayMessages.lastIndex)

        val aiMsg = DisplayMessage("assistant", "", isStreaming = true)
        displayMessages.add(aiMsg)
        val aiIndex = displayMessages.lastIndex
        chatAdapter.notifyItemInserted(aiIndex)
        binding.chatRecyclerView.scrollToPosition(aiIndex)

        val lang         = prefs.getString("language", "pt") ?: "pt"
        val token        = authToken
        val systemPrompt = NvidiaApiService.buildSystemPrompt(lang)
        val isThinking   = thinkMoreMode
        thinkingContent  = ""

        // Ativa botão nova conversa assim que começa a primeira mensagem
        refreshNewChatBtn()

        streamJob = lifecycleScope.launch {
            NvidiaApiService.streamChat(chatHistory, systemPrompt, token, isThinking)
                .collect { chunk ->
                    when (chunk) {
                        is StreamChunk.ThinkToken -> {
                            thinkingContent += chunk.text
                            if (aiMsg.content.isEmpty()) {
                                aiMsg.isThinking = true
                                aiMsg.content = "thinking"
                            }
                            chatAdapter.notifyItemChanged(aiIndex)
                        }
                        is StreamChunk.Token -> {
                            if (aiMsg.isThinking) {
                                aiMsg.isThinking = false
                                aiMsg.content = ""
                            }
                            aiMsg.content += chunk.text
                            chatAdapter.notifyItemChanged(aiIndex)
                            binding.chatRecyclerView.scrollToPosition(aiIndex)
                        }
                        is StreamChunk.Done -> {
                            aiMsg.isStreaming = false
                            aiMsg.isThinking  = false
                            if (aiMsg.content.isBlank()) aiMsg.content = chunk.fullText
                            aiMsg.thinkingContent = thinkingContent
                            chatHistory.add(ChatMessage("assistant", aiMsg.content))
                            chatAdapter.notifyItemChanged(aiIndex)
                            binding.chatRecyclerView.scrollToPosition(aiIndex)
                            if (!titleGenerated && chatHistory.size >= 2) {
                                titleGenerated = true
                                launch {
                                    val title = NvidiaApiService.generateTitle(text, token, lang)
                                    currentConversationTitle = title
                                    saveCurrentConversation()
                                }
                            } else {
                                saveCurrentConversation()
                            }
                        }
                        is StreamChunk.Error -> {
                            aiMsg.isStreaming = false
                            aiMsg.isThinking  = false
                            aiMsg.content = "⚠️ ${chunk.message}"
                            chatAdapter.notifyItemChanged(aiIndex)
                        }
                    }
                }
        }
    }

    // ─── ChatAdapter ──────────────────────────────────────────────────────────

    inner class ChatAdapter(
        private val msgs: List<DisplayMessage>
    ) : RecyclerView.Adapter<ChatAdapter.VH>() {

        inner class VH(val wrapper: FrameLayout) : RecyclerView.ViewHolder(wrapper)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val wrapper = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(6); it.bottomMargin = dp(6) }
            }
            return VH(wrapper)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = msgs[position]
            holder.wrapper.removeAllViews()

            if (msg.role == "user") {
                val tv = TextView(holder.wrapper.context).apply {
                    textSize = 15f
                    setLineSpacing(0f, 1.4f)
                    setTextColor(Color.WHITE)
                    setPadding(dp(16), dp(11), dp(16), dp(11))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    }
                    text = msg.content
                    maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
                }
                holder.wrapper.addView(tv, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.gravity = Gravity.END
                    it.marginStart = dp(64)
                })

            } else {
                val col = LinearLayout(holder.wrapper.context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                // Think button
                if (msg.thinkingContent.isNotEmpty() || (msg.isThinking && msg.isStreaming)) {
                    val thinkBtn = LinearLayout(holder.wrapper.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(7), dp(14), dp(7))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(10).toFloat()
                            setColor(ContextCompat.getColor(context, R.color.card_background))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.bottomMargin = dp(8) }
                        isClickable = true; isFocusable = true
                    }
                    thinkBtn.addView(View(holder.wrapper.context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#FF3B30"))
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).also { it.marginEnd = dp(7) }
                    })
                    thinkBtn.addView(TextView(holder.wrapper.context).apply {
                        text = if (msg.isThinking && msg.isStreaming) "A pensar…" else "Ver pensamento"
                        textSize = 12.5f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    })
                    if (msg.thinkingContent.isNotEmpty()) {
                        thinkBtn.setOnClickListener { showThinkModal(msg.thinkingContent) }
                    }
                    col.addView(thinkBtn)
                }

                // Loader / skeleton / texto
                when {
                    msg.isStreaming && msg.isThinking -> {
                        // Skeleton "thinking" com shimmer
                        col.addView(buildThinkingSkeletonView(holder.wrapper.context))
                    }
                    msg.isStreaming && msg.content.isBlank() -> {
                        // Loader animado enquanto aguarda primeiro token
                        col.addView(buildLoaderView(holder.wrapper.context))
                    }
                    else -> {
                        val tv = TextView(holder.wrapper.context).apply {
                            textSize = 15f
                            setLineSpacing(0f, 1.5f)
                            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                            setPadding(dp(2), dp(4), dp(8), dp(4))
                            text = parseMarkdown(msg.content)
                        }
                        col.addView(tv)
                        // Loader de streaming ainda ativo (a receber tokens)
                        if (msg.isStreaming) {
                            col.addView(buildLoaderView(holder.wrapper.context))
                        }
                    }
                }

                holder.wrapper.addView(col, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(16) })
            }
        }

        override fun getItemCount() = msgs.size
    }

    /**
     * Loader animado baseado no CSS do Uiverse (ZacharyCrespin) — dois quadrados
     * que se movem em direcções opostas numa grelha 2×2.
     */
    private fun buildLoaderView(ctx: Context): View {
        val sizePx  = dp(12) // equivale ao --size: 30px escalado para mobile
        val color   = ContextCompat.getColor(this, R.color.colorPrimary)

        val container = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).also {
                it.topMargin = dp(6); it.bottomMargin = dp(4)
            }
        }

        // Quadrado "before" — começa centro, anda para a direita/baixo
        val sq1 = View(ctx).apply {
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(2).toFloat() }
        }
        // Quadrado "after" — começa offset, anda para a esquerda/cima
        val sq2 = View(ctx).apply {
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(2).toFloat() }
        }

        val lp1 = FrameLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER
        }
        val lp2 = FrameLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER
            leftMargin  = -sizePx
            topMargin   = -sizePx
        }
        container.addView(sq1, lp1)
        container.addView(sq2, lp2)

        container.post {
            val center = container.width / 2f - sizePx / 2f
            val sq1Animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    val (tx, ty) = when {
                        f < 0.25f -> Pair(f / 0.25f * sizePx, 0f)
                        f < 0.50f -> Pair(sizePx.toFloat(), (f - 0.25f) / 0.25f * sizePx)
                        f < 0.75f -> Pair(sizePx - (f - 0.50f) / 0.25f * sizePx, sizePx.toFloat())
                        else      -> Pair(0f, sizePx - (f - 0.75f) / 0.25f * sizePx)
                    }
                    sq1.translationX = tx; sq1.translationY = ty
                }
                start()
            }
            val sq2Animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    val (tx, ty) = when {
                        f < 0.25f -> Pair(-(f / 0.25f * sizePx), 0f)
                        f < 0.50f -> Pair(-sizePx.toFloat(), -((f - 0.25f) / 0.25f * sizePx))
                        f < 0.75f -> Pair(-(sizePx - (f - 0.50f) / 0.25f * sizePx), -sizePx.toFloat())
                        else      -> Pair(0f, -(sizePx - (f - 0.75f) / 0.25f * sizePx))
                    }
                    sq2.translationX = tx; sq2.translationY = ty
                }
                start()
            }
            container.tag = listOf(sq1Animator, sq2Animator)
        }
        return container
    }

    /** Skeleton shimmer "A pensar…" */
    private fun buildThinkingSkeletonView(ctx: Context): View {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val label = TextView(ctx).apply {
            text = "🧠 A pensar…"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_secondary))
            setPadding(dp(2), dp(4), dp(8), dp(6))
        }
        wrap.addView(label)

        // Linhas skeleton com shimmer
        listOf(0.85f, 0.7f, 0.55f).forEach { widthFraction ->
            val bar = View(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(ContextCompat.getColor(this@MainActiviy, R.color.card_background))
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(12)).also {
                    it.width = (resources.displayMetrics.widthPixels * widthFraction * 0.78f).toInt()
                    it.bottomMargin = dp(6)
                }
            }
            wrap.addView(bar)

            // Shimmer: alterna alpha
            ValueAnimator.ofFloat(0.4f, 1f, 0.4f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { bar.alpha = it.animatedValue as Float }
                start()
            }
        }
        return wrap
    }

    private fun parseMarkdown(text: String): Spanned {
        val cleaned = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.MULTILINE), "").trim()
        val html = cleaned
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("__(.+?)__"), "<b>$1</b>")
            .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")
            .replace(Regex("`(.+?)`"), "<tt>$1</tt>")
            .replace(Regex("^#{1,2}\\s+(.+)$", RegexOption.MULTILINE), "<b><big>$1</big></b>")
            .replace(Regex("^#{3,}\\s+(.+)$", RegexOption.MULTILINE), "<b>$1</b>")
            .replace(Regex("^[-•]\\s+(.+)$", RegexOption.MULTILINE), "• $1")
            .replace(Regex("^\\|.+\\|$", RegexOption.MULTILINE), "")
            .replace(Regex("^[|:\\- ]+$", RegexOption.MULTILINE), "")
            .replace("\n", "<br>")
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    }

    // ─── Think modal ──────────────────────────────────────────────────────────

    private fun showThinkModal(content: String) {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.dialog_background))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(ContextCompat.getColor(this@MainActiviy, R.color.divider))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(10); it.bottomMargin = dp(4)
            }
        })
        card.addView(TextView(this).apply {
            text = "Processo de pensamento"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_primary))
            setPadding(dp(20), dp(8), dp(20), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        card.addView(View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActiviy, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        scroll.addView(TextView(this).apply {
            text = content
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActiviy, R.color.text_secondary))
            setLineSpacing(0f, 1.5f)
            setPadding(dp(20), dp(16), dp(20), dp(24))
        })
        card.addView(scroll)
        root.addView(card)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let {
                it.setBackgroundColor(Color.TRANSPARENT)
                val screenH = resources.displayMetrics.heightPixels
                it.layoutParams.height = (screenH * 0.75f).toInt()
                it.requestLayout()
                val behavior = BottomSheetBehavior.from(it)
                behavior.peekHeight = (screenH * 0.75f).toInt()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    // ─── Extras modal ─────────────────────────────────────────────────────────

    private fun showExtrasSheet() {
        hidePopup()
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
                setColor(Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Handle
        card.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#E0E0E0"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12); it.bottomMargin = dp(4)
            }
        })

        // Título
        card.addView(TextView(this).apply {
            text = "Extras"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(8), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // Flash
        card.addView(buildExtrasRow(
            iconPath = "icons/svg/flash.svg",
            label    = "Flash",
            subtitle = "Respostas rápidas e diretas",
            isSwitch = false,
            checked  = flashMode
        ) { flashMode = !flashMode; thinkMoreMode = false })

        card.addView(extrasDiv())

        // Think More
        card.addView(buildExtrasRow(
            iconPath = "icons/svg/brain.svg",
            label    = "Think More",
            subtitle = "Respostas mais detalhadas e profundas",
            isSwitch = false,
            checked  = thinkMoreMode
        ) { thinkMoreMode = !thinkMoreMode; flashMode = false })

        card.addView(extrasDiv())

        // Sheets
        card.addView(buildExtrasRow(
            iconPath = "icons/svg/sheets.svg",
            label    = "Sheets",
            subtitle = "A IA insere rascunhos HTML na conversa",
            isSwitch = true,
            checked  = sheetsEnabled
        ) { sheetsEnabled = !sheetsEnabled })

        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24))
        })

        root.addView(card)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun extrasDiv() = View(this).apply {
        setBackgroundColor(ContextCompat.getColor(this@MainActiviy, R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.marginStart = dp(58)
        }
    }

    private fun buildExtrasRow(
        iconPath: String,
        label: String,
        subtitle: String,
        isSwitch: Boolean,
        checked: Boolean,
        onClick: () -> Unit
    ): View {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = a.getDrawable(0); a.recycle()
            isClickable = true; isFocusable = true
        }

        // Ícone
        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.marginEnd = dp(14) }
            background = ContextCompat.getDrawable(this@MainActiviy, R.drawable.drawer_icon_bg)
        }
        iconFrame.addView(ImageView(this).apply {
            setImageDrawable(svgDrawable(iconPath, 14, iconTint))
            layoutParams = FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)
        })
        row.addView(iconFrame)

        // Texto
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.BLACK)
        })
        textCol.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2) }
        })
        row.addView(textCol)

        if (isSwitch) {
            val sw = MaterialSwitch(this).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, _ -> onClick() }
            }
            row.addView(sw)
        } else {
            // Checkmark se ativo
            if (checked) {
                row.addView(ImageView(this).apply {
                    setImageDrawable(svgDrawable("icons/svg/ic_check.svg", 18,
                        ContextCompat.getColor(this@MainActiviy, R.color.colorPrimary)))
                    layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                })
            }
            row.setOnClickListener { onClick(); }
        }

        return row
    }

    // ─── Câmara ───────────────────────────────────────────────────────────────

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }
        startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }

    // ─── Setup geral ──────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val v = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(v.windowToken, 0)
        binding.inputMessage.clearFocus()
    }

    private fun syncBlurBgSize() {
        val wh = binding.bottomNavWrapper.height
        if (wh > 0 && binding.bottomBlurBg.layoutParams.height != wh) {
            binding.bottomBlurBg.layoutParams = binding.bottomBlurBg.layoutParams.also { it.height = wh }
        }
    }

    private fun animateBottomBarState() {
        val d = density
        val isPreview = currentTab == R.id.tabPreview
        val targetMargin = if (isPreview) (MARGIN_PREVIEW_DP * d).toInt() else (MARGIN_CHAT_DP * d).toInt()
        val targetRadius = if (isPreview) RADIUS_PREVIEW_DP * d else RADIUS_CHAT_DP * d
        val bottomMargin = (BOTTOM_MARGIN_DP * d).toInt()
        if (currentBarMarginPx == targetMargin && currentBarRadiusPx == targetRadius) return

        val wrapperBg = binding.bottomNavWrapper.background as? GradientDrawable ?: return
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
                wrapperBg.cornerRadius = radius
                (binding.bottomNavWrapper.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
                    it.marginStart = margin; it.marginEnd = margin; it.bottomMargin = bottomMargin
                    binding.bottomNavWrapper.layoutParams = it
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
            binding.previewTitle.typeface  = Typeface.create(tf, Typeface.BOLD)
            binding.previewSubtitle.typeface = tf
            binding.drawerAppName.typeface = Typeface.create(tf, Typeface.BOLD)
        }
    }

    private fun setupIcons() {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        val iconSec  = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        binding.btnMenu.setImageDrawable(svgDrawable("icons/svg/side_panel.svg", 16, iconTint))
        binding.btnMore.setImageDrawable(svgDrawable("icons/svg/more_vertical.svg", 16, iconTint))
        binding.btnNewChatIcon.setImageDrawable(svgDrawable("icons/svg/add.svg", 17, iconTint))
        binding.drawerIconSettings.setImageDrawable(svgDrawable("icons/svg/settings.svg", 14, iconTint))
        binding.drawerChevronSettings.setImageDrawable(svgDrawable("icons/svg/chevron_right.svg", 13, iconSec))
        // Popup icons
        binding.popupCameraIcon.setImageDrawable(svgDrawable("icons/svg/camera.svg", 20, iconTint))
        binding.popupImportIcon.setImageDrawable(svgDrawable("icons/svg/download.svg", 20, iconTint))
        binding.popupUrlIcon.setImageDrawable(svgDrawable("icons/svg/external.svg", 20, iconTint))
        binding.popupExtrasIcon.setImageDrawable(svgDrawable("icons/svg/extras.svg", 20, iconTint))
    }

    private fun setupPopupMenu() {
        binding.btnMore.setOnClickListener {
            if (displayMessages.isNotEmpty() || chatHistory.isNotEmpty()) {
                // Numa conversa ativa: "Mais" inicia nova conversa
                startNewConversation()
            } else {
                showPopup()
            }
        }
        // Clicar no overlay fecha popup — bloqueia interação fora
        binding.popupOverlay.setOnClickListener { hidePopup() }
        // Câmara
        binding.popupItemCamera.setOnClickListener { hidePopup(); openCamera() }
        // Importar
        binding.popupItemImport.setOnClickListener { hidePopup() }
        // URL — sem funcionalidade
        binding.popupItemUrl.setOnClickListener { /* inativo */ }
        // Extras
        binding.popupItemExtras.setOnClickListener { showExtrasSheet() }
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

    // ─── Swipe drawer progressivo ─────────────────────────────────────────────

    private fun setupSwipeDrawer() {
        val edgePx   = SWIPE_EDGE_WIDTH * density
        val minDistPx = SWIPE_MIN_DIST * density

        binding.coordinatorLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX; swipeStartY = event.rawY
                    isSwipingDrawer = !drawerOpen && swipeStartX < edgePx
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSwipingDrawer) {
                        val dx = event.rawX - swipeStartX
                        val dy = event.rawY - swipeStartY
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx > 0) {
                            // Movimento progressivo proporcional ao dedo
                            val progress = (dx / drawerWidth).coerceIn(0f, 1f)
                            binding.coordinatorLayout.translationX = dx.coerceAtMost(drawerWidth.toFloat())
                            binding.coordinatorLayout.elevation = 8f + progress * 16f
                            binding.drawerScrim.visibility = View.VISIBLE
                            true
                        } else false
                    } else if (drawerOpen) {
                        val dx = event.rawX - swipeStartX
                        if (dx < 0) {
                            val newX = (drawerWidth + dx).coerceAtLeast(0f)
                            val progress = newX / drawerWidth
                            binding.coordinatorLayout.translationX = newX
                            binding.coordinatorLayout.elevation = 8f + progress * 16f
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
        binding.btnMenu.setOnClickListener { if (drawerOpen) closeDrawer() else openDrawer() }
        binding.btnNewChat.setOnClickListener { startNewConversation() }
        binding.drawerScrim.setOnClickListener { closeDrawer() }
        binding.drawerItemSettings.setOnClickListener {
            closeDrawer()
            binding.root.postDelayed({ startActivity(Intent(this, SettingsActivity::class.java)) }, 250)
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        hideKeyboard()
        drawerOpen = true
        binding.drawerScrim.visibility = View.VISIBLE
        loadDrawerConversations()
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

    // ─── Tabs ─────────────────────────────────────────────────────────────────

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

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        refreshTabIcons()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentConversation()
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