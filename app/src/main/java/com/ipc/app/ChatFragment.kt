// ChatFragment.kt
package com.ipc.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.ipc.app.data.AuthApiService
import com.ipc.app.data.ChatMessage
import com.ipc.app.data.Conversation
import com.ipc.app.data.GeminiApiService
import com.ipc.app.data.StreamChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

class ChatFragment(private val activity: MainActiviy) {

    private val binding   get() = activity.binding
    private val prefs     get() = activity.prefs
    private val authToken get() = activity.authToken

    private var flashMode     = false
    private var thinkMoreMode = false
    var sheetsEnabled         = false

    var currentConversationId    = ""
    var currentConversationTitle = "Nova conversa"
    private var titleGenerated   = false
    private var thinkingContent  = ""

    private val chatHistory     = mutableListOf<ChatMessage>()
    private val displayMessages = mutableListOf<DisplayMessage>()
    private var streamJob: Job? = null
    private lateinit var chatAdapter: ChatAdapter

    private var sendBtnVisible        = false
    var inputRowVisible               = true
    private var inputRowHeight        = 0
    private var inputRowAnimator:     ValueAnimator? = null
    private var newChatSlideAnimator: ValueAnimator? = null

    private val timesTypeface: Typeface? by lazy {
        runCatching {
            Typeface.createFromAsset(activity.assets, "fonts/pattern/times_new_roman.ttf")
        }.getOrNull()
    }

    val newChatEnabled: Boolean
        get() = chatHistory.isNotEmpty() || currentConversationId.isNotEmpty()

    val chatHistoryNotEmpty: Boolean
        get() = chatHistory.isNotEmpty()

    val currentConversationSnapshot: Conversation?
        get() {
            if (chatHistory.isEmpty() && currentConversationId.isEmpty()) return null
            return Conversation(
                id        = currentConversationId,
                title     = currentConversationTitle,
                messages  = chatHistory.toList(),
                updatedAt = System.currentTimeMillis()
            )
        }

    data class DisplayMessage(
        val role: String,
        var content: String,
        var isStreaming: Boolean = false,
        var isThinking: Boolean = false,
        var thinkingContent: String = ""
    )

    // ─── Setup ────────────────────────────────────────────────────────────────

    fun setup() {
        setupChatRecycler()
        setupPreviewImage()
        setupGreeting()
        setupInput()
        binding.inputRow.post {
            if (inputRowHeight == 0) inputRowHeight = binding.inputRow.height
        }
        refreshNewChatBtn()
    }

    private fun setupChatRecycler() {
        chatAdapter = ChatAdapter(displayMessages)
        val llm = LinearLayoutManager(activity)
        llm.stackFromEnd = true
        binding.chatRecyclerView.layoutManager = llm
        binding.chatRecyclerView.adapter = chatAdapter
        binding.chatRecyclerView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun setupPreviewImage() {
        val bitmap = runCatching {
            activity.assets.open("icons/png/preview.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (bitmap != null) binding.previewImage.setImageBitmap(bitmap)
        binding.previewState.visibility = View.GONE
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.emptyGreeting.text = when {
            hour < 12 -> "Bom dia"; hour < 18 -> "Boa tarde"; else -> "Boa noite"
        }
        runCatching {
            val logoBitmap = activity.assets.open("icons/png/logo.png").use { BitmapFactory.decodeStream(it) }
            binding.emptyLogo.setImageBitmap(logoBitmap)
        }
        runCatching {
            val tf = timesTypeface ?: return@runCatching
            binding.emptyGreeting.typeface   = Typeface.create(tf, Typeface.BOLD)
            binding.emptySubtitle.typeface   = tf
            binding.previewTitle.typeface    = Typeface.create(tf, Typeface.BOLD)
            binding.previewSubtitle.typeface = tf
            binding.drawerAppName.typeface   = Typeface.create(tf, Typeface.BOLD)
        }
    }

    // ─── New chat btn ─────────────────────────────────────────────────────────

    fun refreshNewChatBtn() {
        val hasConv = newChatEnabled
        activity.refreshMoreBtn()
        newChatSlideAnimator?.cancel()

        if (hasConv) {
            if (binding.btnNewChat.translationX != 0f) {
                newChatSlideAnimator = ValueAnimator.ofFloat(binding.btnNewChat.translationX, 0f).apply {
                    duration = 300; interpolator = DecelerateInterpolator(1.8f)
                    addUpdateListener { binding.btnNewChat.translationX = it.animatedValue as Float }
                    start()
                }
            }
            binding.btnMoreWrapper.visibility = View.VISIBLE
            binding.btnMoreWrapper.alpha = 0f
            binding.btnMoreWrapper.scaleX = 0.7f
            binding.btnMoreWrapper.scaleY = 0.7f
            binding.btnMoreWrapper.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(260).setInterpolator(OvershootInterpolator(1.2f)).start()
        } else {
            val targetX = dp(42).toFloat()
            if (binding.btnMoreWrapper.visibility == View.VISIBLE) {
                binding.btnMoreWrapper.animate()
                    .alpha(0f).scaleX(0.7f).scaleY(0.7f)
                    .setDuration(200).setInterpolator(DecelerateInterpolator())
                    .withEndAction { binding.btnMoreWrapper.visibility = View.INVISIBLE }
                    .start()
                newChatSlideAnimator = ValueAnimator.ofFloat(binding.btnNewChat.translationX, targetX).apply {
                    duration = 300; startDelay = 80L
                    interpolator = OvershootInterpolator(1.1f)
                    addUpdateListener { binding.btnNewChat.translationX = it.animatedValue as Float }
                    start()
                }
            } else {
                binding.btnMoreWrapper.visibility = View.INVISIBLE
                binding.btnNewChat.translationX = targetX
            }
        }

        binding.btnNewChat.alpha       = if (hasConv) 1f else 0.35f
        binding.btnNewChat.isClickable = hasConv
        binding.btnNewChat.isFocusable = hasConv
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    fun syncVisibility() {
        if (displayMessages.isEmpty()) {
            binding.emptyState.visibility       = View.VISIBLE
            binding.chatRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility       = View.GONE
            binding.chatRecyclerView.visibility = View.VISIBLE
        }
    }

    fun syncBlurBgSize() {
        val wh = binding.bottomNavWrapper.height
        if (wh > 0 && binding.bottomBlurBg.layoutParams.height != wh) {
            binding.bottomBlurBg.layoutParams = binding.bottomBlurBg.layoutParams.also { it.height = wh }
        }
    }

    fun applyKeyboardPadding(extraShift: Int) {
        val rv = binding.chatRecyclerView
        val base   = dp(160)
        val target = base + extraShift
        if (rv.paddingBottom != target) {
            rv.setPadding(rv.paddingLeft, rv.paddingTop, rv.paddingRight, target)
            if (displayMessages.isNotEmpty()) smoothScroll(displayMessages.lastIndex)
        }
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    private fun smoothScroll(position: Int) {
        binding.chatRecyclerView.smoothScrollToPosition(position)
    }

    // ─── Input ────────────────────────────────────────────────────────────────

    private fun setupInput() {
        binding.inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                when {
                    hasText && !sendBtnVisible -> showSendBtn()
                    !hasText && sendBtnVisible -> hideSendBtn()
                }
            }
        })
        binding.btnSend.setOnClickListener {
            val text = binding.inputMessage.text.toString().trim()
            if (text.isNotEmpty()) { binding.inputMessage.text?.clear(); sendChatMessage(text) }
        }
        binding.btnRecord.setOnClickListener { activity.showVoiceModal() }
    }

    fun showInputRow() {
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
                    syncBlurBgSize()
                }
            })
            start()
        }
    }

    fun hideInputRow() {
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
        sendBtnVisible = true
        binding.btnRecord.animate()
            .scaleX(0.4f).scaleY(0.4f).alpha(0f)
            .setDuration(150).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.btnRecord.visibility = View.GONE
                binding.btnSend.visibility = View.VISIBLE
                binding.btnSend.scaleX = 0.4f; binding.btnSend.scaleY = 0.4f; binding.btnSend.alpha = 0f
                binding.btnSend.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(220).setInterpolator(OvershootInterpolator(1.4f)).start()
            }.start()
    }

    private fun hideSendBtn() {
        sendBtnVisible = false
        binding.btnSend.animate()
            .scaleX(0.4f).scaleY(0.4f).alpha(0f)
            .setDuration(150).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.btnSend.visibility = View.GONE
                binding.btnRecord.visibility = View.VISIBLE
                binding.btnRecord.scaleX = 0.4f; binding.btnRecord.scaleY = 0.4f; binding.btnRecord.alpha = 0f
                binding.btnRecord.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(220).setInterpolator(OvershootInterpolator(1.4f)).start()
            }.start()
    }

    // ─── Conversas ────────────────────────────────────────────────────────────

    fun saveCurrentConversation() {
        if (chatHistory.isEmpty()) return
        val token = authToken
        val title = currentConversationTitle
        val msgs  = chatHistory.toList()
        val id    = currentConversationId
        activity.lifecycleScope.launch {
            if (id.isEmpty()) {
                val newId = AuthApiService.createConversation(token, title, msgs)
                if (newId != null) {
                    currentConversationId = newId
                    activity.drawerManager.loadConversations()
                }
            } else {
                AuthApiService.updateConversation(token, id, title, msgs)
                activity.drawerManager.loadConversations()
            }
        }
    }

    fun loadConversation(conv: Conversation) {
        currentConversationId    = conv.id
        currentConversationTitle = conv.title
        titleGenerated = true
        chatHistory.clear()
        chatHistory.addAll(conv.messages)
        displayMessages.clear()
        conv.messages.forEach { displayMessages.add(DisplayMessage(it.role, it.content)) }
        chatAdapter.notifyDataSetChanged()
        if (displayMessages.isNotEmpty()) smoothScroll(displayMessages.lastIndex)
        binding.chatRecyclerView.visibility = View.VISIBLE
        binding.emptyState.visibility       = View.GONE
        refreshNewChatBtn()
    }

    fun startNewConversation() {
        if (!newChatEnabled) return
        streamJob?.cancel()
        streamJob = null
        saveCurrentConversation()
        currentConversationId    = ""
        currentConversationTitle = "Nova conversa"
        titleGenerated = false
        chatHistory.clear()
        displayMessages.clear()
        chatAdapter.notifyDataSetChanged()
        binding.chatRecyclerView.visibility = View.GONE
        binding.emptyState.visibility       = View.VISIBLE
        activity.closeDrawer()
        refreshNewChatBtn()
    }

    // ─── Enviar mensagem ──────────────────────────────────────────────────────

    private fun sendChatMessage(text: String) {
        if (text.isBlank() || streamJob?.isActive == true) return

        binding.emptyState.visibility       = View.GONE
        binding.chatRecyclerView.visibility = View.VISIBLE

        chatHistory.add(ChatMessage("user", text))
        displayMessages.add(DisplayMessage("user", text))
        chatAdapter.notifyItemInserted(displayMessages.lastIndex)
        smoothScroll(displayMessages.lastIndex)

        val aiMsg   = DisplayMessage("assistant", "", isStreaming = true)
        displayMessages.add(aiMsg)
        val aiIndex = displayMessages.lastIndex
        chatAdapter.notifyItemInserted(aiIndex)
        smoothScroll(aiIndex)

        val lang         = prefs.getString("language", "pt") ?: "pt"
        val token        = authToken
        val systemPrompt = GeminiApiService.buildSystemPrompt(lang, sheetsEnabled)
        val isThinking   = thinkMoreMode
        thinkingContent  = ""

        refreshNewChatBtn()

        streamJob = activity.lifecycleScope.launch {
            GeminiApiService.streamChat(chatHistory, systemPrompt, token, isThinking)
                .collect { chunk ->
                    when (chunk) {
                        is StreamChunk.ThinkToken -> {
                            thinkingContent += chunk.text
                            if (aiMsg.content.isEmpty()) {
                                aiMsg.isThinking = true
                                aiMsg.content    = "thinking"
                            }
                            chatAdapter.notifyItemChanged(aiIndex)
                        }
                        is StreamChunk.Token -> {
                            if (aiMsg.isThinking) {
                                aiMsg.isThinking = false
                                aiMsg.content    = ""
                            }
                            aiMsg.content += chunk.text
                            chatAdapter.notifyItemChanged(aiIndex)
                            smoothScroll(aiIndex)
                        }
                        is StreamChunk.Done -> {
                            aiMsg.isStreaming     = false
                            aiMsg.isThinking      = false
                            if (aiMsg.content.isBlank()) aiMsg.content = chunk.fullText
                            aiMsg.thinkingContent = thinkingContent
                            chatHistory.add(ChatMessage("assistant", aiMsg.content))
                            chatAdapter.notifyItemChanged(aiIndex)
                            smoothScroll(aiIndex)

                            if (!titleGenerated && chatHistory.size >= 2) {
                                titleGenerated = true
                                launch {
                                    val firstUserMsg = chatHistory.firstOrNull { it.role == "user" }?.content ?: text
                                    val generated = GeminiApiService.generateTitle(firstUserMsg, token, lang)
                                    // Só usa o gerado pela IA — nunca a primeira mensagem do utilizador
                                    currentConversationTitle = if (generated.isNotBlank()) generated else
    firstUserMsg.trim().split("\\s+".toRegex()).take(4).joinToString(" ").take(40)
                                    saveCurrentConversation()
                                    activity.drawerManager.loadConversations()
                                }
                            } else {
                                saveCurrentConversation()
                                activity.drawerManager.loadConversations()
                            }
                        }
                        is StreamChunk.Error -> {
                            aiMsg.isStreaming = false
                            aiMsg.isThinking  = false
                            aiMsg.content     = "⚠️ ${chunk.message}"
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
                    textSize = 16f
                    setLineSpacing(0f, 1.4f)
                    setTextColor(Color.WHITE)
                    setPadding(dp(16), dp(11), dp(16), dp(11))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    }
                    text = msg.content
                    maxWidth = (activity.resources.displayMetrics.widthPixels * 0.78f).toInt()
                }
                holder.wrapper.addView(tv, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.gravity = Gravity.END; it.marginStart = dp(64) })
            } else {
                val col = LinearLayout(holder.wrapper.context).apply {
                    orientation = LinearLayout.VERTICAL
                }

                if (msg.thinkingContent.isNotEmpty() || (msg.isThinking && msg.isStreaming)) {
                    val thinkBtn = LinearLayout(holder.wrapper.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(7), dp(14), dp(7))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(10).toFloat()
                            setColor(ContextCompat.getColor(activity, R.color.card_background))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.bottomMargin = dp(8) }
                        isClickable = true; isFocusable = true
                    }
                    thinkBtn.addView(View(holder.wrapper.context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF3B30"))
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).also { it.marginEnd = dp(7) }
                    })
                    thinkBtn.addView(TextView(holder.wrapper.context).apply {
                        text = if (msg.isThinking && msg.isStreaming) "A pensar…" else "Ver pensamento"
                        textSize = 12.5f
                        setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
                    })
                    if (msg.thinkingContent.isNotEmpty()) {
                        thinkBtn.setOnClickListener { showThinkModal(msg.thinkingContent) }
                    }
                    col.addView(thinkBtn)
                }

                when {
                    msg.isStreaming && msg.isThinking -> col.addView(buildThinkingSkeletonView(holder.wrapper.context))
                    msg.isStreaming && msg.content.isBlank() -> col.addView(buildLoaderView(holder.wrapper.context))
                    else -> {
                        renderMessageContent(col, msg.content)
                        if (msg.isStreaming) col.addView(buildLoaderView(holder.wrapper.context))
                    }
                }

                holder.wrapper.addView(col, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(16) })
            }
        }

        override fun getItemCount() = msgs.size
    }

    // ─── Renderização de mensagens ────────────────────────────────────────────

    private fun renderMessageContent(parent: LinearLayout, rawContent: String) {
        val text = rawContent.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.MULTILINE), "").trim()

        if (sheetsEnabled) {
            // Detectar blocos ```widget_bar, ```widget_pie, ```widget_table, ```widget_sheet
            val widgetRegex = Regex("```(widget_bar|widget_pie|widget_table|widget_sheet)\\s*\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
            val matches = widgetRegex.findAll(text)
            if (matches.any()) {
                var lastEnd = 0
                matches.forEach { match ->
                    val before = text.substring(lastEnd, match.range.first).trim()
                    if (before.isNotEmpty()) {
                        splitIntoBlocks(before).forEach { block -> renderBlock(parent, block) }
                    }
                    val widgetType = match.groupValues[1]
                    val jsonStr    = match.groupValues[2].trim()
                    parent.addView(buildWidgetView(parent.context, widgetType, jsonStr))
                    lastEnd = match.range.last + 1
                }
                val after = text.substring(lastEnd).trim()
                if (after.isNotEmpty()) {
                    splitIntoBlocks(after).forEach { block -> renderBlock(parent, block) }
                }
                return
            }
        }

        splitIntoBlocks(text).forEach { block -> renderBlock(parent, block) }
    }

    // ─── Widget: carrega asset HTML e injeta dados ────────────────────────────

    private fun buildWidgetView(ctx: Context, widgetType: String, jsonData: String): View {
        val assetFile = when (widgetType) {
            "widget_bar"   -> "widgets/bar_chart.html"
            "widget_pie"   -> "widgets/pie_chart.html"
            "widget_table" -> "widgets/table.html"
            "widget_sheet" -> "widgets/sheet.html"
            else           -> "widgets/bar_chart.html"
        }

        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8); it.bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (activity.isDarkMode) Color.parseColor("#1E1E1E") else Color.parseColor("#FAFAFA"))
                setStroke(dp(1), if (activity.isDarkMode) Color.parseColor("#2A2A2A") else Color.parseColor("#E5E5EA"))
            }
            clipToOutline = true
        }

        val webView = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            webChromeClient = WebChromeClient()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)
            )
        }

        val isDark = activity.isDarkMode
        val themeVars = buildThemeVars(isDark)

        // Injectar dados e tema após carregamento
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val jsDataVar = when (widgetType) {
                    "widget_bar"   -> "window.barData = $jsonData;"
                    "widget_pie"   -> "window.pieData = $jsonData;"
                    "widget_table" -> "window.tableData = $jsonData;"
                    "widget_sheet" -> {
                        // widget_sheet espera window.linesData = [{text,title}]
                        // o JSON tem {"lines":[...]} — extrair o array
                        "try { var d = $jsonData; window.linesData = d.lines || d; } catch(e) { window.linesData = []; }"
                    }
                    else -> ""
                }
                val jsTheme = """
                    (function(){
                        var s = document.createElement('style');
                        s.textContent = ':root{$themeVars}';
                        document.head.insertBefore(s, document.head.firstChild);
                        $jsDataVar
                        if(typeof render === 'function') render();
                        if(window.linesData && typeof render === 'function') render();
                    })();
                """.trimIndent()
                view?.evaluateJavascript(jsTheme, null)
            }
        }

        webView.loadUrl("file:///android_asset/$assetFile")
        wrapper.addView(webView)
        return wrapper
    }

    private fun buildThemeVars(isDark: Boolean): String {
        return if (isDark) {
            "--bg:#1E1E1E;--surface:#2C2C2E;--border:#3A3A3C;--text:#F2F2F2;--text-secondary:#939393;--header-bg:#2C2C2E;--row-hover:#242424;--divider:#3A3A3C;--bar-color:#6F5AF6;"
        } else {
            "--bg:#FFFFFF;--surface:#F2F2F7;--border:#D4D8DD;--text:#111111;--text-secondary:#666666;--header-bg:#F5F7FA;--row-hover:#FAFBFC;--divider:#E5E5EA;--bar-color:#6F5AF6;"
        }
    }

    // ─── Blocos de conteúdo ───────────────────────────────────────────────────

    private fun renderBlock(parent: LinearLayout, block: ContentBlock) {
        when (block.type) {
            BlockType.TABLE -> parent.addView(buildTableView(parent.context, block.lines))
            BlockType.MATH  -> parent.addView(buildMathView(parent.context, block.lines.joinToString("\n")))
            BlockType.TEXT  -> {
                val spanned = parseMarkdownBlock(block.lines.joinToString("\n"))
                val tv = TextView(parent.context).apply {
                    textSize = 16f
                    setLineSpacing(0f, 1.5f)
                    setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
                    setPadding(dp(2), dp(4), dp(8), dp(4))
                }
                tv.setText(spanned, TextView.BufferType.SPANNABLE)
                parent.addView(tv)
            }
        }
    }

    enum class BlockType { TEXT, TABLE, MATH }
    data class ContentBlock(val type: BlockType, val lines: List<String>)

    private fun isMathLine(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (t.contains("$$") || t.contains("\\(") || t.contains("\\[")) return true
        if (Regex("\\$[^$]+\\$").containsMatchIn(t)) return true
        if (Regex("\\\\(frac|sqrt|sum|int|lim|alpha|beta|gamma|delta|pi|theta|sigma|lambda|mu|omega|infty|pm|times|leq|geq|neq)").containsMatchIn(t)) return true
        if (Regex("[a-zA-Z]\\^[{0-9]").containsMatchIn(t)) return true
        if (Regex("^[a-zA-Z]\\^?[0-9]?\\s*[+\\-*/=]").containsMatchIn(t)) return true
        return false
    }

    private fun splitIntoBlocks(text: String): List<ContentBlock> {
        val result = mutableListOf<ContentBlock>()
        val lines = text.split("\n")
        val currentTextLines = mutableListOf<String>()

        fun flushText() {
            if (currentTextLines.isNotEmpty()) {
                val trimmed = currentTextLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                if (trimmed.isNotEmpty()) result.add(ContentBlock(BlockType.TEXT, trimmed))
                currentTextLines.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                isTableLine(line) -> {
                    flushText()
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && (isTableLine(lines[i]) || isSeparatorLine(lines[i]))) {
                        tableLines.add(lines[i]); i++
                    }
                    if (tableLines.size >= 2) result.add(ContentBlock(BlockType.TABLE, tableLines))
                    else currentTextLines.addAll(tableLines)
                }
                line.trim().startsWith("$$") || line.trim().startsWith("\\[") -> {
                    flushText()
                    val mathLines = mutableListOf<String>()
                    val endMarker = if (line.trim().startsWith("$$")) "$$" else "\\]"
                    val rest = line.trim().removePrefix("$$").removePrefix("\\[")
                    if (rest.endsWith("$$") || rest.endsWith("\\]")) {
                        mathLines.add(line); i++
                    } else {
                        mathLines.add(line); i++
                        while (i < lines.size && !lines[i].trim().contains(endMarker)) {
                            mathLines.add(lines[i]); i++
                        }
                        if (i < lines.size) { mathLines.add(lines[i]); i++ }
                    }
                    result.add(ContentBlock(BlockType.MATH, mathLines))
                }
                isMathLine(line) && !line.trim().startsWith("#") && !line.trim().startsWith("-") && !line.trim().startsWith("*") -> {
                    flushText()
                    val mathLines = mutableListOf<String>()
                    while (i < lines.size && (isMathLine(lines[i]) || lines[i].isBlank()) &&
                           !isTableLine(lines[i]) && !lines[i].trim().startsWith("#")) {
                        mathLines.add(lines[i]); i++
                    }
                    val trimmed = mathLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                    if (trimmed.isNotEmpty()) result.add(ContentBlock(BlockType.MATH, trimmed))
                    else currentTextLines.addAll(mathLines)
                }
                else -> { currentTextLines.add(line); i++ }
            }
        }
        flushText()
        return result
    }

    private fun isTableLine(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("|") && t.endsWith("|") && t.count { it == '|' } >= 2
    }

    private fun isSeparatorLine(line: String): Boolean {
        val t = line.trim()
        return t.matches(Regex("\\|[-:| ]+\\|"))
    }

    // ─── Math view ────────────────────────────────────────────────────────────

    private fun buildMathView(ctx: Context, mathText: String): View {
        val isDark      = activity.isDarkMode
        val cardBg      = if (isDark) Color.parseColor("#1E1E1E") else Color.parseColor("#F8F7FF")
        val borderColor = if (isDark) Color.parseColor("#6F5AF640") else Color.parseColor("#6F5AF630")
        val textColor   = ContextCompat.getColor(activity, R.color.text_primary)

        val hScroll = HorizontalScrollView(ctx).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6); it.bottomMargin = dp(6) }
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(cardBg)
                setStroke(dp(1), borderColor)
            }
            clipToOutline = true
        }

        val cleaned = mathText
            .replace(Regex("^\\$\\$|\\$\\$$", RegexOption.MULTILINE), "")
            .replace(Regex("^\\\\\\[|\\\\\\]$", RegexOption.MULTILINE), "")
            .trim()

        val lines = cleaned.split("\n").filter { it.isNotBlank() }
        lines.forEachIndexed { idx, line ->
            val tv = TextView(ctx).apply {
                textSize = 15.5f
                setLineSpacing(0f, 1.4f)
                setTextColor(textColor)
                gravity = Gravity.CENTER_HORIZONTAL
                timesTypeface?.let { typeface = it }
                if (idx > 0) setPadding(0, dp(2), 0, 0)
            }
            tv.setText(parseInlineMarkdown(line), TextView.BufferType.SPANNABLE)
            card.addView(tv)
        }

        hScroll.addView(card)
        return hScroll
    }

    // ─── Table view ───────────────────────────────────────────────────────────

    private fun buildTableView(ctx: Context, lines: List<String>): View {
        val dataLines = lines.filter { !isSeparatorLine(it) }
        if (dataLines.isEmpty()) return View(ctx)

        val isDark        = activity.isDarkMode
        val textPrimary   = ContextCompat.getColor(activity, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(activity, R.color.text_secondary)
        val dividerColor  = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#DDDDDD")
        val cardBg        = ContextCompat.getColor(activity, R.color.card_background)
        val headerBg      = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#ECEAFF")

        val colCount = dataLines.maxOfOrNull { line ->
            line.trim().removePrefix("|").removeSuffix("|").split("|").size
        } ?: 1

        val hScroll = HorizontalScrollView(ctx).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6); it.bottomMargin = dp(6) }
        }

        val table = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(cardBg)
                if (!isDark) setStroke(dp(1), Color.parseColor("#D8D8D8"))
            }
            clipToOutline = true
        }

        dataLines.forEachIndexed { rowIndex, line ->
            val rawCells = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            val cells    = List(colCount) { idx -> rawCells.getOrElse(idx) { "" } }
            val isHeader = rowIndex == 0

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(if (isHeader) headerBg else Color.TRANSPARENT)
                minimumHeight = dp(36)
            }

            cells.forEachIndexed { colIndex, cellText ->
                if (colIndex > 0) {
                    row.addView(View(ctx).apply {
                        setBackgroundColor(dividerColor)
                        layoutParams = LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT)
                    })
                }
                val cell = TextView(ctx).apply {
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                    textSize = 13f
                    setTextColor(if (isHeader) textPrimary else textSecondary)
                    if (isHeader) setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                }
                cell.setText(parseInlineMarkdown(cellText), TextView.BufferType.SPANNABLE)
                row.addView(cell)
            }

            table.addView(row)
            if (rowIndex < dataLines.lastIndex) {
                table.addView(View(ctx).apply {
                    setBackgroundColor(dividerColor)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                })
            }
        }

        hScroll.addView(table)
        return hScroll
    }

    // ─── Think modal ──────────────────────────────────────────────────────────

    private fun showThinkModal(thinkText: String) {
        val dialog  = BottomSheetDialog(activity, R.style.Theme_IPC_BottomSheet)
        val screenH = activity.resources.displayMetrics.heightPixels

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
                setColor(ContextCompat.getColor(activity, R.color.dialog_background))
            }
        }

        card.addView(View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                setColor(ContextCompat.getColor(activity, R.color.divider))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12); it.bottomMargin = dp(8)
            }
        })

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }
        headerRow.addView(ImageView(activity).apply {
            setImageDrawable(activity.svgDrawable("icons/svg/brain_filled.svg", 18,
                ContextCompat.getColor(activity, R.color.colorPrimary)))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).also { it.marginEnd = dp(10) }
        })
        headerRow.addView(TextView(activity).apply {
            text = "Processo de raciocínio"; textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(headerRow)
        card.addView(View(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        val scroll = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val thinkTv = TextView(activity).apply {
            textSize = 14f; setLineSpacing(0f, 1.6f)
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        thinkTv.setText(parseMarkdown(thinkText), TextView.BufferType.SPANNABLE)
        scroll.addView(thinkTv)
        card.addView(scroll)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(card)
        }
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams.height = (screenH * 0.72f).toInt(); sheet.requestLayout()
                val beh = BottomSheetBehavior.from(sheet)
                beh.peekHeight = (screenH * 0.72f).toInt()
                beh.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    // ─── Extras sheet ─────────────────────────────────────────────────────────

    fun showExtrasSheet() {
        activity.hidePopup()
        val dialog = BottomSheetDialog(activity, R.style.Theme_IPC_BottomSheet)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
                setColor(ContextCompat.getColor(activity, R.color.dialog_background))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        card.addView(sheetHandle())
        card.addView(TextView(activity).apply {
            text = "Extras"; textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, R.color.settings_section_label))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(8), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        card.addView(buildExtrasToggleRow("icons/svg/flash.svg", "icons/svg/flash_filled.svg",
            "Flash", "Respostas rápidas e diretas", flashMode) {
            flashMode = !flashMode; thinkMoreMode = false; dialog.dismiss(); showExtrasSheet()
        })
        card.addView(extrasDiv())
        card.addView(buildExtrasToggleRow("icons/svg/brain.svg", "icons/svg/brain_filled.svg",
            "Think More", "Respostas mais detalhadas e profundas", thinkMoreMode) {
            thinkMoreMode = !thinkMoreMode; flashMode = false; dialog.dismiss(); showExtrasSheet()
        })
        card.addView(extrasDiv())
        card.addView(buildExtrasToggleRow("icons/svg/sheets.svg", "icons/svg/sheets_filled.svg",
            "Sheets", "A IA insere widgets visuais na conversa", sheetsEnabled, isSwitch = true) {
            sheetsEnabled = !sheetsEnabled
        })
        card.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24))
        })
        root.addView(card)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun buildExtrasToggleRow(
        iconOff: String, iconOn: String,
        label: String, subtitle: String,
        checked: Boolean, isSwitch: Boolean = false,
        onClick: () -> Unit
    ): View {
        val GREEN    = Color.parseColor("#34C759")
        val iconTint = if (checked) GREEN else ContextCompat.getColor(activity, R.color.icon_tint)
        val iconPath = if (checked) iconOn else iconOff

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60); setPadding(dp(20), dp(12), dp(20), dp(12))
            val a = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = a.getDrawable(0); a.recycle()
            isClickable = true; isFocusable = true
        }
        val iconFrame = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.marginEnd = dp(14) }
            background = ContextCompat.getDrawable(activity, R.drawable.drawer_icon_bg)
        }
        iconFrame.addView(ImageView(activity).apply {
            setImageDrawable(activity.svgDrawable(iconPath, 14, iconTint))
            layoutParams = FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)
        })
        row.addView(iconFrame)

        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(activity).apply {
            text = label; textSize = 15f
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        })
        textCol.addView(TextView(activity).apply {
            text = subtitle; textSize = 12.5f
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
        })
        row.addView(textCol)

        if (isSwitch) {
            val sw = MaterialSwitch(activity).apply {
                isChecked = checked
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnCheckedChangeListener { _, _ -> onClick() }
            }
            row.addView(sw)
        } else {
            val dot = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (checked) GREEN else Color.TRANSPARENT)
                }
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
            }
            row.addView(dot)
            row.setOnClickListener { onClick() }
        }

        return row
    }

    private fun extrasDiv() = View(activity).apply {
        setBackgroundColor(ContextCompat.getColor(activity, R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.marginStart = dp(60)
        }
    }

    private fun sheetHandle() = View(activity).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
            setColor(ContextCompat.getColor(activity, R.color.divider))
        }
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12); it.bottomMargin = dp(8)
        }
    }

    // ─── Loader / Skeleton ────────────────────────────────────────────────────

    private fun buildLoaderView(ctx: Context): View {
        val dotSize = dp(7); val gap = dp(5)
        val color = ContextCompat.getColor(activity, R.color.text_secondary)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(6); it.bottomMargin = dp(4) }
        }
        val dots = (0..2).map {
            View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also { lp -> if (it > 0) lp.marginStart = gap }
                container.addView(this)
            }
        }
        dots.forEachIndexed { i, dot ->
            ValueAnimator.ofFloat(0f, -dp(6).toFloat(), 0f).apply {
                duration = 600; startDelay = (i * 150).toLong()
                repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
                interpolator = DecelerateInterpolator()
                addUpdateListener { dot.translationY = it.animatedValue as Float }
                start()
            }
        }
        return container
    }

    private fun buildThinkingSkeletonView(ctx: Context): View {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        wrap.addView(TextView(ctx).apply {
            text = "🧠 A pensar…"; textSize = 14f
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            setPadding(dp(2), dp(4), dp(8), dp(6))
        })
        listOf(0.85f, 0.7f, 0.55f).forEach { widthFraction ->
            val bar = View(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(ContextCompat.getColor(activity, R.color.card_background))
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(12)).also {
                    it.width = (activity.resources.displayMetrics.widthPixels * widthFraction * 0.78f).toInt()
                    it.bottomMargin = dp(6)
                }
            }
            wrap.addView(bar)
            ValueAnimator.ofFloat(0.4f, 1f, 0.4f).apply {
                duration = 1200; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { bar.alpha = it.animatedValue as Float }
                start()
            }
        }
        return wrap
    }

    // ─── Markdown ─────────────────────────────────────────────────────────────

    private fun parseMarkdownBlock(raw: String): Spanned {
        val sb = SpannableStringBuilder()
        val lines = raw.split("\n")
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            if (idx > 0) sb.append("\n")
            when {
                trimmed.startsWith("## ") || trimmed.startsWith("# ") -> {
                    val content = trimmed.trimStart('#').trim()
                    val start = sb.length
                    appendInlineSpans(sb, content)
                    sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.15f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                trimmed.startsWith("### ") -> {
                    val content = trimmed.trimStart('#').trim()
                    val start = sb.length
                    appendInlineSpans(sb, content)
                    sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") -> {
                    val content = trimmed.substring(2)
                    val start = sb.length
                    sb.append("  ")
                    appendInlineSpans(sb, content)
                    sb.setSpan(
                        BulletSpan(dp(8), ContextCompat.getColor(activity, R.color.text_primary)),
                        start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                trimmed.matches(Regex("[-=|: ]+")) -> {}
                else -> appendInlineSpans(sb, line)
            }
        }
        return sb
    }

    private fun cleanLatex(text: String): String {
        return text
            .replace(Regex("\\$\\$([^$]+)\\$\\$")) { it.groupValues[1] }
            .replace(Regex("\\$([^$\n]+)\\$")) { it.groupValues[1] }
            .replace(Regex("\\\\\\((.+?)\\\\\\)")) { it.groupValues[1] }
            .replace(Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}"), "($1)/($2)")
            .replace(Regex("\\\\sqrt\\{([^}]+)\\}"), "√($1)")
            .replace(Regex("\\\\sqrt\\s+(\\S+)"), "√$1")
            .replace("\\pm", "±").replace("\\mp", "∓")
            .replace("\\times", "×").replace("\\cdot", "·").replace("\\div", "÷")
            .replace("\\leq", "≤").replace("\\geq", "≥")
            .replace("\\neq", "≠").replace("\\approx", "≈")
            .replace("\\infty", "∞").replace("\\partial", "∂")
            .replace("\\alpha", "α").replace("\\beta", "β").replace("\\gamma", "γ")
            .replace("\\delta", "δ").replace("\\Delta", "Δ").replace("\\epsilon", "ε")
            .replace("\\pi", "π").replace("\\theta", "θ").replace("\\lambda", "λ")
            .replace("\\mu", "μ").replace("\\sigma", "σ").replace("\\Sigma", "Σ")
            .replace("\\omega", "Ω").replace("\\phi", "φ").replace("\\psi", "ψ")
            .replace(Regex("\\^\\{([^}]+)\\}")) { "§SUP§${it.groupValues[1]}§/SUP§" }
            .replace(Regex("\\^([0-9+\\-a-zA-Z])")) { "§SUP§${it.groupValues[1]}§/SUP§" }
            .replace(Regex("_\\{([^}]+)\\}")) { toSubscriptString(it.groupValues[1]) }
            .replace(Regex("_([0-9])")) { subscriptDigit(it.groupValues[1]) }
            .replace(Regex("\\\\[a-zA-Z]+\\{([^}]*)\\}")) { it.groupValues[1] }
            .replace(Regex("\\\\[a-zA-Z]+"), "")
            .replace("{", "").replace("}", "")
    }

    private fun toSubscriptString(s: String): String =
        s.map { c ->
            when (c) {
                '0' -> '₀'; '1' -> '₁'; '2' -> '₂'; '3' -> '₃'; '4' -> '₄'
                '5' -> '₅'; '6' -> '₆'; '7' -> '₇'; '8' -> '₈'; '9' -> '₉'
                '+' -> '₊'; '-' -> '₋'; 'a' -> 'ₐ'; 'e' -> 'ₑ'; 'i' -> 'ᵢ'
                'o' -> 'ₒ'; 'u' -> 'ᵤ'; 'n' -> 'ₙ'; else -> c
            }
        }.joinToString("")

    private fun subscriptDigit(d: String): String = when (d) {
        "0" -> "₀"; "1" -> "₁"; "2" -> "₂"; "3" -> "₃"; "4" -> "₄"
        "5" -> "₅"; "6" -> "₆"; "7" -> "₇"; "8" -> "₈"; "9" -> "₉"
        else -> "_$d"
    }

    private fun parseInlineMarkdown(line: String): Spanned {
        val sb = SpannableStringBuilder()
        appendInlineSpans(sb, cleanLatex(line))
        return sb
    }

    private fun appendInlineSpans(sb: SpannableStringBuilder, rawLine: String) {
        val cleaned = cleanLatex(rawLine)
        val pattern = Regex(
            "\\*\\*(.+?)\\*\\*" +
            "|__(.+?)__" +
            "|(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)" +
            "|`(.+?)`" +
            "|§SUP§(.+?)§/SUP§"
        )
        var lastEnd = 0
        pattern.findAll(cleaned).forEach { match ->
            if (match.range.first > lastEnd) sb.append(cleaned.substring(lastEnd, match.range.first))
            val start = sb.length
            when {
                match.groupValues[1].isNotEmpty() -> {
                    sb.append(match.groupValues[1])
                    sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[2].isNotEmpty() -> {
                    sb.append(match.groupValues[2])
                    sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[3].isNotEmpty() -> {
                    sb.append(match.groupValues[3])
                    sb.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[4].isNotEmpty() -> {
                    sb.append(match.groupValues[4])
                    sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[5].isNotEmpty() -> {
                    sb.append(match.groupValues[5])
                    sb.setSpan(SuperscriptSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(0.65f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < cleaned.length) sb.append(cleaned.substring(lastEnd))
    }

    private fun parseMarkdown(raw: String): Spanned = parseMarkdownBlock(
        raw.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.MULTILINE), "").trim()
    )

    private fun dp(v: Int) = activity.dp(v)
}