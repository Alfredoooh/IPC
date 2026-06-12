package com.ipc.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ipc.app.data.AuthApiService
import com.ipc.app.data.ChatMessage
import com.ipc.app.data.Conversation
import com.ipc.app.data.GeminiApiService
import com.ipc.app.data.StreamChunk
import com.ipc.app.ui.GooeyLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
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

    private var flashCardView:   View? = null; private var flashCardIcon:   ImageView? = null; private var flashCardLabel:  TextView? = null
    private var thinkCardView:   View? = null; private var thinkCardIcon:   ImageView? = null; private var thinkCardLabel:  TextView? = null
    private var sheetsCardView:  View? = null; private var sheetsCardIcon:  ImageView? = null; private var sheetsCardLabel: TextView? = null
    private var extrasDialog: BottomSheetDialog? = null

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
        setupInputFocusBorderGlow()
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

    // ─── Input + Brilho na borda ──────────────────────────────────────────────

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

    private fun setupInputFocusBorderGlow() {
        val wrapper = binding.bottomNavWrapper
        val bg = wrapper.background as? GradientDrawable ?: return
        val defaultStrokeColor = ContextCompat.getColor(activity, R.color.divider)
        val glowColor = ContextCompat.getColor(activity, R.color.colorPrimary)
        val strokePx = (1.5f * activity.density).toInt()

        binding.inputMessage.setOnFocusChangeListener { _, hasFocus ->
            val targetColor = if (hasFocus) glowColor else defaultStrokeColor
            val anim = ValueAnimator.ofArgb(
                if (hasFocus) defaultStrokeColor else glowColor,
                targetColor
            )
            anim.duration = 260L
            anim.addUpdateListener {
                bg.setStroke(strokePx, it.animatedValue as Int)
            }
            anim.start()
        }
        bg.setStroke(strokePx, defaultStrokeColor)
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

        if (!titleGenerated) {
            titleGenerated = true
            activity.lifecycleScope.launch {
                val generated = GeminiApiService.generateTitle(text, token, lang)
                currentConversationTitle = if (generated.isNotBlank()) generated
                    else text.trim().split("\\s+".toRegex()).take(4).joinToString(" ").take(40)
                activity.drawerManager.loadConversations()
            }
        }

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
                            saveCurrentConversation()
                            activity.drawerManager.loadConversations()
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
                        else if (msg.role == "assistant" && msg.content.isNotBlank()) {
                            col.addView(buildActionRow(holder.wrapper.context, msg.content))
                        }
                    }
                }

                holder.wrapper.addView(col, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(16) })
            }
        }

        override fun getItemCount() = msgs.size
    }

    // ─── Action row (copiar, gostei, não gostei, partilhar, regenerar) ──────

    private fun buildActionRow(ctx: Context, messageContent: String): View {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val tint = ContextCompat.getColor(activity, R.color.icon_tint_secondary)
        val iconSize = 20

        fun addAction(icon: String, label: String, listener: () -> Unit) {
            val btn = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            btn.addView(ImageView(ctx).apply {
                setImageDrawable(activity.svgDrawable(icon, iconSize, tint))
                val px = dp(iconSize)
                layoutParams = LinearLayout.LayoutParams(px, px, Gravity.CENTER)
            })
            btn.addView(TextView(ctx).apply {
                text = label; textSize = 11f; setTextColor(tint); gravity = Gravity.CENTER
            })
            btn.setOnClickListener { listener() }
            container.addView(btn)
        }

        addAction("icons/svg/copy.svg", "Copiar") { copyToClipboard(messageContent) }
        addAction("icons/svg/thumbs_up.svg", "Gostei") { /* enviar like */ }
        addAction("icons/svg/thumbs_down.svg", "Não") { /* dislike */ }
        addAction("icons/svg/share.svg", "Partilhar") { shareText(messageContent) }
        addAction("icons/svg/regenerate.svg", "Regenerar") { regenerateResponse() }

        return container
    }

    private fun copyToClipboard(text: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IPC", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, "Copiado!", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "Partilhar resposta"))
    }

    private fun regenerateResponse() {
        if (chatHistory.isEmpty()) return
        val lastUserMsg = chatHistory.last { it.role == "user" }.content
        streamJob?.cancel()
        val aiIndex = displayMessages.lastIndex
        displayMessages.removeAt(aiIndex)
        chatHistory.removeLast()
        chatAdapter.notifyItemRemoved(aiIndex)
        sendChatMessage(lastUserMsg)
    }

    // ─── Renderização de mensagens (widgets corrigidos) ──────────────────────

    private fun renderMessageContent(parent: LinearLayout, rawContent: String) {
        val text = rawContent.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.MULTILINE), "").trim()

        if (sheetsEnabled) {
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
                    parent.addView(buildNativeWidget(parent.context, widgetType, jsonStr))
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

    // ─── Widgets nativos ──────────────────────────────────────────────────────

    private fun buildNativeWidget(ctx: Context, widgetType: String, jsonData: String): View {
        return try {
            val json = JSONObject(jsonData)
            when (widgetType) {
                "widget_bar"   -> buildNativeBarChart(ctx, json)
                "widget_pie"   -> buildNativePieChart(ctx, json)
                "widget_table" -> buildNativeTable(ctx, json)
                "widget_sheet" -> buildNativeSheet(ctx, json)
                else           -> buildNativeBarChart(ctx, json)
            }
        } catch (e: Exception) {
            TextView(ctx).apply {
                text = "⚠️ Widget inválido"
                textSize = 13f
                setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            }
        }
    }

    private fun widgetContainer(ctx: Context): LinearLayout {
        val isDark = activity.isDarkMode
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8); it.bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(ContextCompat.getColor(ctx, R.color.card_background))
                setStroke(dp(1), if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#E5E5EA"))
            }
            clipToOutline = true
        }
    }

    private fun widgetHeader(ctx: Context, title: String): View {
        val isDark = activity.isDarkMode
        val headerBg = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#F5F7FA")
        val dividerColor = ContextCompat.getColor(ctx, R.color.divider)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                text = title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(headerBg)
            })
            addView(View(ctx).apply {
                setBackgroundColor(dividerColor)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            })
        }
    }

    private fun buildNativeBarChart(ctx: Context, json: JSONObject): View {
        val container = widgetContainer(ctx)
        val title = json.optString("title", "Gráfico")
        container.addView(widgetHeader(ctx, title))

        val items = json.optJSONArray("items") ?: return container
        val barColor = ContextCompat.getColor(activity, R.color.colorPrimary)
        val textPrimary = ContextCompat.getColor(ctx, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(ctx, R.color.text_secondary)
        val isDark = activity.isDarkMode
        val gridColor = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#E5E5EA")

        data class BarItem(val label: String, val value: Float, val color: Int)
        val barItems = mutableListOf<BarItem>()
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            val colorStr = obj.optString("color", "")
            val c = if (colorStr.isNotEmpty()) runCatching { Color.parseColor(colorStr) }.getOrDefault(barColor) else barColor
            barItems.add(BarItem(obj.optString("label", ""), obj.optDouble("value", 0.0).toFloat(), c))
        }
        val maxVal = barItems.maxOfOrNull { it.value } ?: 1f

        val chartHeight = dp(220)
        val barAreaHeight = dp(160)
        val bottomPad = dp(36)
        val sidePad = dp(16)

        val chartView = object : View(ctx) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val totalBars = barItems.size
                if (totalBars == 0) return
                val barMaxW = dp(40).toFloat()
                val gap = dp(10).toFloat()
                val totalGap = gap * (totalBars - 1)
                val barW = ((w - sidePad * 2 - totalGap) / totalBars).coerceAtMost(barMaxW)
                val totalContentW = barW * totalBars + totalGap
                val startX = (w - totalContentW) / 2

                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gridColor; strokeWidth = 1f }
                for (i in 0..4) {
                    val y = sidePad + barAreaHeight * (1 - i / 4f)
                    canvas.drawLine(0f, y, w, y, gridPaint)
                }

                barItems.forEachIndexed { i, item ->
                    val barX = startX + i * (barW + gap)
                    val barH = if (maxVal > 0) (item.value / maxVal) * barAreaHeight else 0f
                    val top = sidePad + barAreaHeight - barH
                    val bottom = sidePad + barAreaHeight.toFloat()

                    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.color }
                    val rect = RectF(barX, top, barX + barW, bottom)
                    val path = Path().apply {
                        val r = dp(7).toFloat()
                        addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
                    }
                    canvas.drawPath(path, barPaint)

                    val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textPrimary; textSize = dp(11).toFloat(); textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val valStr = if (item.value == item.value.toLong().toFloat()) item.value.toInt().toString() else item.value.toString()
                    canvas.drawText(valStr, barX + barW / 2, top - dp(6), valPaint)

                    val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textSecondary; textSize = dp(11).toFloat(); textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(item.label, barX + barW / 2, bottom + dp(20), lblPaint)
                }
            }
        }
        chartView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, chartHeight + bottomPad
        ).also { it.setMargins(sidePad, dp(14), sidePad, dp(16)) }
        container.addView(chartView)
        return container
    }

    private fun buildNativePieChart(ctx: Context, json: JSONObject): View {
        val container = widgetContainer(ctx)
        val title = json.optString("title", "Gráfico de Pizza")
        container.addView(widgetHeader(ctx, title))

        val slices = json.optJSONArray("slices") ?: return container
        val COLORS = listOf("#6F5AF6","#FF3B30","#34C759","#FF9500","#007AFF","#AF52DE","#5AC8FA","#FFCC00")
        val textSecondary = ContextCompat.getColor(ctx, R.color.text_secondary)

        data class Slice(val label: String, val value: Float, val color: Int)
        val sliceItems = mutableListOf<Slice>()
        for (i in 0 until slices.length()) {
            val obj = slices.getJSONObject(i)
            val colorStr = obj.optString("color", "")
            val c = if (colorStr.isNotEmpty()) runCatching { Color.parseColor(colorStr) }.getOrDefault(Color.parseColor(COLORS[i % COLORS.size]))
                    else Color.parseColor(COLORS[i % COLORS.size])
            sliceItems.add(Slice(obj.optString("label", ""), obj.optDouble("value", 0.0).toFloat(), c))
        }
        val total = sliceItems.sumOf { it.value.toDouble() }.toFloat()

        val pieSize = dp(180)
        val pieView = object : View(ctx) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val radius = (pieSize / 2f) - dp(4)
                val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                var startAngle = -90f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ContextCompat.getColor(ctx, R.color.card_background)
                    style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
                }
                sliceItems.forEach { slice ->
                    val sweep = if (total > 0) (slice.value / total) * 360f else 0f
                    paint.color = slice.color; paint.style = Paint.Style.FILL
                    canvas.drawArc(oval, startAngle, sweep, true, paint)
                    canvas.drawArc(oval, startAngle, sweep, true, strokePaint)

                    val pct = if (total > 0) (slice.value / total * 100).toInt() else 0
                    if (pct >= 5) {
                        val midAngle = Math.toRadians((startAngle + sweep / 2).toDouble())
                        val lx = cx + (radius * 0.65f * Math.cos(midAngle)).toFloat()
                        val ly = cy + (radius * 0.65f * Math.sin(midAngle)).toFloat()
                        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE; textSize = dp(12).toFloat()
                            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
                        }
                        canvas.drawText("$pct%", lx, ly + dp(4), txtPaint)
                    }
                    startAngle += sweep
                }
            }
        }
        pieView.layoutParams = LinearLayout.LayoutParams(pieSize, pieSize).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(16)
        }

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        body.addView(pieView)

        val legend = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(14) }
        }
        sliceItems.forEach { slice ->
            val pct = if (total > 0) (slice.value / total * 100).toInt() else 0
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(6); it.marginEnd = dp(16) }
            }
            row.addView(View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(slice.color) }
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).also { it.marginEnd = dp(6) }
            })
            row.addView(TextView(ctx).apply {
                text = "${slice.label} ($pct%)"
                textSize = 12f
                setTextColor(textSecondary)
            })
            legend.addView(row)
        }
        body.addView(legend)
        container.addView(body)
        return container
    }

    private fun buildNativeTable(ctx: Context, json: JSONObject): View {
        val headersArr = json.optJSONArray("headers")
        val rowsArr = json.optJSONArray("rows")

        val headers = mutableListOf<String>()
        if (headersArr != null) for (i in 0 until headersArr.length()) headers.add(headersArr.getString(i))
        val rows = mutableListOf<List<String>>()
        if (rowsArr != null) for (i in 0 until rowsArr.length()) {
            val rowArr = rowsArr.getJSONArray(i)
            val row = mutableListOf<String>()
            for (j in 0 until rowArr.length()) row.add(rowArr.getString(j))
            rows.add(row)
        }

        val isDark = activity.isDarkMode
        val textPrimary = ContextCompat.getColor(ctx, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(ctx, R.color.text_secondary)
        val dividerColor = ContextCompat.getColor(ctx, R.color.divider)
        val headerBg = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#ECEAFF")
        val cardBg = ContextCompat.getColor(ctx, R.color.card_background)

        val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)

        val hScroll = HorizontalScrollView(ctx).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6); it.bottomMargin = dp(6) }
        }

        val table = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(cardBg)
                setStroke(dp(1), if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#E5E5EA"))
            }
            clipToOutline = true
        }

        fun makeRow(cells: List<String>, isHeader: Boolean) {
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
                    setPadding(dp(10), dp(8), dp(10), dp(8))
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
        }

        if (headers.isNotEmpty()) makeRow(List(colCount) { headers.getOrElse(it) { "" } }, true)
        rows.forEachIndexed { idx, row ->
            if (idx > 0 || headers.isNotEmpty()) {
                table.addView(View(ctx).apply {
                    setBackgroundColor(dividerColor)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                })
            }
            makeRow(List(colCount) { row.getOrElse(it) { "" } }, false)
        }

        hScroll.addView(table)
        return hScroll
    }

    private fun buildNativeSheet(ctx: Context, json: JSONObject): View {
        val linesArr = json.optJSONArray("lines") ?: return View(ctx)
        val isDark = activity.isDarkMode
        val textPrimary = ContextCompat.getColor(ctx, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(ctx, R.color.text_secondary)
        val surfaceColor = if (isDark) Color.parseColor("#1E1E1E") else Color.parseColor("#FFFEF8")
        val borderColor = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#D6D6D6")
        val ruleColor = if (isDark) Color.parseColor("#1A3A5AFF") else Color.parseColor("#285FFF29")
        val marginColor = if (isDark) Color.parseColor("#3AFF5A5A") else Color.parseColor("#33FF5A5A")

        val lineHeight = dp(32)
        val leftPad = dp(72)
        val topPad = dp(10)

        data class SheetLine(val text: String, val isTitle: Boolean)
        val lines = mutableListOf<SheetLine>()
        for (i in 0 until linesArr.length()) {
            val obj = linesArr.getJSONObject(i)
            lines.add(SheetLine(obj.optString("text", ""), obj.optBoolean("title", false)))
        }

        val totalHeight = topPad * 2 + lines.size * lineHeight + dp(16)

        val sheetView = object : View(ctx) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()

                val bgPaint = Paint().apply { color = surfaceColor }
                canvas.drawRect(0f, 0f, w, h, bgPaint)

                val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ruleColor; strokeWidth = dp(1).toFloat() }
                var ry = topPad.toFloat()
                while (ry < h) {
                    canvas.drawLine(0f, ry, w, ry, rulePaint)
                    ry += lineHeight
                }

                val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = marginColor; strokeWidth = dp(1).toFloat() }
                canvas.drawLine(dp(56).toFloat(), 0f, dp(56).toFloat(), h, marginPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = borderColor; style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
                }
                canvas.drawRect(0f, 0f, w, h, borderPaint)

                lines.forEachIndexed { i, line ->
                    val y = topPad + (i + 1) * lineHeight - dp(8)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (line.isTitle) textPrimary else textSecondary
                        typeface = if (line.isTitle) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        textSize = if (line.isTitle) dp(16).toFloat() else dp(14).toFloat()
                    }
                    canvas.drawText(line.text, leftPad.toFloat(), y.toFloat(), paint)
                }
            }
        }
        sheetView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, totalHeight
        ).also { it.topMargin = dp(6); it.bottomMargin = dp(6) }

        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8); it.bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1), borderColor)
            }
            clipToOutline = true
        }
        wrapper.addView(sheetView)
        return wrapper
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
        val borderColor = if (isDark) Color.parseColor("#2F7BF640") else Color.parseColor("#2F7BF630")
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

    // ─── Table view (markdown) ────────────────────────────────────────────────

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
        activity.hideKeyboard()
        val dialog  = BottomSheetDialog(activity, R.style.Theme_IPC_BottomSheet)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
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

    // ─── EXTRAS SHEET (CARDS NÃO DESLIZÁVEIS, ÍCONES 20DP) ──────────────────

    fun showExtrasSheet() {
        activity.hideKeyboard()
        activity.hidePopup()

        if (extrasDialog?.isShowing == true) {
            refreshExtraCards()
            return
        }

        val dialog = BottomSheetDialog(activity, R.style.Theme_IPC_BottomSheet)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        extrasDialog = dialog

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
            text = "Extras"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(20))
        })

        val cardsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(20), dp(24))
            weightSum = 3f
        }

        val iconSizeDp = 20

        val flashData = buildExtraCard(
            name = "Flash",
            iconOff = "icons/svg/flash.svg",
            iconOn = "icons/svg/flash_filled.svg",
            active = flashMode,
            iconSizeDp = iconSizeDp
        ) {
            flashMode = true
            thinkMoreMode = false
            refreshExtraCards()
        }
        cardsRow.addView(flashData.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        flashCardView = flashData.root
        flashCardIcon = flashData.icon
        flashCardLabel = flashData.label

        val thinkData = buildExtraCard(
            name = "Think More",
            iconOff = "icons/svg/brain.svg",
            iconOn = "icons/svg/brain_filled.svg",
            active = thinkMoreMode,
            iconSizeDp = iconSizeDp
        ) {
            thinkMoreMode = true
            flashMode = false
            refreshExtraCards()
        }
        cardsRow.addView(thinkData.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        thinkCardView = thinkData.root
        thinkCardIcon = thinkData.icon
        thinkCardLabel = thinkData.label

        val sheetsData = buildExtraCard(
            name = "Sheets",
            iconOff = "icons/svg/sheets.svg",
            iconOn = "icons/svg/sheets_filled.svg",
            active = sheetsEnabled,
            iconSizeDp = iconSizeDp
        ) {
            sheetsEnabled = !sheetsEnabled
            refreshExtraCards()
        }
        cardsRow.addView(sheetsData.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sheetsCardView = sheetsData.root
        sheetsCardIcon = sheetsData.icon
        sheetsCardLabel = sheetsData.label

        card.addView(cardsRow)
        root.addView(card)
        dialog.setContentView(root)

        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.setOnDismissListener {
            extrasDialog = null
        }
        dialog.show()
    }

    private fun refreshExtraCards() {
        val ctx = activity
        fun applyCard(view: View?, icon: ImageView?, label: TextView?, active: Boolean) {
            view ?: return
            val bg = view.background as? GradientDrawable
            val cardBg = if (active) {
                ContextCompat.getColor(ctx, R.color.extras_card_active)
            } else {
                ContextCompat.getColor(ctx, R.color.card_background)
            }
            bg?.setColor(cardBg)
            val textColor = if (active) {
                ContextCompat.getColor(ctx, R.color.extras_card_active_text)
            } else {
                ContextCompat.getColor(ctx, R.color.text_secondary)
            }
            label?.setTextColor(textColor)
            icon?.setColorFilter(
                if (active) ContextCompat.getColor(ctx, R.color.extras_card_active_text)
                else ContextCompat.getColor(ctx, R.color.icon_tint),
                PorterDuff.Mode.SRC_IN
            )
        }

        applyCard(flashCardView, flashCardIcon, flashCardLabel, flashMode)
        applyCard(thinkCardView, thinkCardIcon, thinkCardLabel, thinkMoreMode)
        applyCard(sheetsCardView, sheetsCardIcon, sheetsCardLabel, sheetsEnabled)
    }

    private data class ExtraCardData(val root: View, val icon: ImageView, val label: TextView)

    private fun buildExtraCard(
        name: String,
        iconOff: String,
        iconOn: String,
        active: Boolean,
        iconSizeDp: Int,
        onClick: () -> Unit
    ): ExtraCardData {
        val ctx = activity
        val isDark = activity.isDarkMode

        val cardBg = if (active) {
            ContextCompat.getColor(ctx, R.color.extras_card_active)
        } else {
            ContextCompat.getColor(ctx, R.color.card_background)
        }
        val textColor = if (active) {
            ContextCompat.getColor(ctx, R.color.extras_card_active_text)
        } else {
            ContextCompat.getColor(ctx, R.color.text_secondary)
        }
        val iconTint = if (active) {
            ContextCompat.getColor(ctx, R.color.extras_card_active_text)
        } else {
            ContextCompat.getColor(ctx, R.color.icon_tint)
        }

        val iconPath = if (active) iconOn else iconOff

        val cardView = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(cardBg)
                if (!active && !isDark) {
                    setStroke(dp(1), Color.parseColor("#E0E0E0"))
                }
            }
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(20), dp(8), dp(16))
        }

        val icon = ImageView(ctx).apply {
            setImageDrawable(activity.svgDrawable(iconPath, iconSizeDp, iconTint))
            layoutParams = LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(8)
            }
        }
        inner.addView(icon)

        val label = TextView(ctx).apply {
            text = name
            textSize = 13f
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        inner.addView(label)

        cardView.addView(inner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        return ExtraCardData(cardView, icon, label)
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

    // ─── Loader com GooeyLoader ──────────────────────────────────────────────

    private fun buildLoaderView(ctx: Context): View {
        val container = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = dp(8)
                it.bottomMargin = dp(4)
            }
        }
        val loader = GooeyLoader(ctx)
        val size = dp(48)
        container.addView(loader, FrameLayout.LayoutParams(size, size))
        return container
    }

    // ─── Thinking skeleton ────────────────────────────────────────────────────

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
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (i > 0) sb.append("\n")
            when {
                line.trimStart().startsWith("## ") || line.trimStart().startsWith("# ") -> {
                    val content = line.trimStart().trimStart('#').trim()
                    val start = sb.length
                    appendInlineSpans(sb, content)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.15f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.trimStart().startsWith("### ") -> {
                    val content = line.trimStart().trimStart('#').trim()
                    val start = sb.length
                    appendInlineSpans(sb, content)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.trimStart().startsWith("* ") || line.trimStart().startsWith("- ") || line.trimStart().startsWith("• ") -> {
                    val content = line.trimStart().substring(2).trim()
                    val start = sb.length
                    sb.append("  ")
                    appendInlineSpans(sb, content)
                    sb.setSpan(
                        BulletSpan(dp(8), ContextCompat.getColor(activity, R.color.text_primary)),
                        start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                line.trim().matches(Regex("[-=|: ]+")) -> { /* ignorar separadores de tabela */ }
                else -> appendInlineSpans(sb, line)
            }
            i++
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
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[2].isNotEmpty() -> {
                    sb.append(match.groupValues[2])
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[3].isNotEmpty() -> {
                    sb.append(match.groupValues[3])
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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