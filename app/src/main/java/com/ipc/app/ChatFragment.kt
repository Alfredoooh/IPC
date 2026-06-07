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
import android.text.Html
import android.text.Spanned
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
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
import com.ipc.app.data.NvidiaApiService
import com.ipc.app.data.StreamChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

class ChatFragment(private val activity: MainActiviy) {

    private val binding     get() = activity.binding
    private val prefs       get() = activity.prefs
    private val authToken   get() = activity.authToken

    private var flashMode     = false
    private var thinkMoreMode = false
    private var sheetsEnabled = false

    var currentConversationId    = ""
    var currentConversationTitle = "Nova conversa"
    private var titleGenerated   = false
    private var thinkingContent  = ""

    private val chatHistory     = mutableListOf<ChatMessage>()
    private val displayMessages = mutableListOf<DisplayMessage>()
    private var streamJob: Job? = null
    private lateinit var chatAdapter: ChatAdapter

    private var sendBtnVisible       = false
    var inputRowVisible              = true
    private var inputRowHeight       = 0
    private var frozenInputRowHeight = 0
    private var inputRowHeightFrozen = false
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var sendBtnAnimator: ValueAnimator?   = null
    private var inputRowAnimator: ValueAnimator?  = null
    private var inputHeightAnimator: ValueAnimator? = null

    val newChatEnabled: Boolean
        get() = chatHistory.isNotEmpty() || currentConversationId.isNotEmpty()

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
            if (inputRowHeight == 0) {
                inputRowHeight = binding.inputRow.height
                frozenInputRowHeight = inputRowHeight
            }
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
        // Carregar logo.png acima da saudação
        runCatching {
            val logoBitmap = activity.assets.open("icons/png/logo.png").use { BitmapFactory.decodeStream(it) }
            if (logoBitmap != null) binding.emptyLogo.setImageBitmap(logoBitmap)
        }
        runCatching {
            val tf = Typeface.createFromAsset(activity.assets, "fonts/pattern/times_new_roman.ttf")
            binding.emptyGreeting.typeface  = Typeface.create(tf, Typeface.BOLD)
            binding.emptySubtitle.typeface  = tf
            binding.previewTitle.typeface   = Typeface.create(tf, Typeface.BOLD)
            binding.previewSubtitle.typeface = tf
            binding.drawerAppName.typeface  = Typeface.create(tf, Typeface.BOLD)
        }
    }

    // ─── New chat btn ─────────────────────────────────────────────────────────

    fun refreshNewChatBtn() {
        val enabled = newChatEnabled
        binding.btnNewChat.alpha      = if (enabled) 1f else 0.35f
        binding.btnNewChat.isClickable = enabled
        binding.btnNewChat.isFocusable = enabled
    }

    // ─── Sync visibility ──────────────────────────────────────────────────────

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

    // ─── Teclado ──────────────────────────────────────────────────────────────

    fun onKeyboardOpen(extraShift: Int) {
        if (displayMessages.isEmpty()) {
            binding.emptyState.animate()
                .translationY(-(extraShift * 0.45f))
                .setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
        } else {
            binding.chatRecyclerView.animate()
                .translationY(-extraShift.toFloat())
                .setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
            binding.chatRecyclerView.post {
                binding.chatRecyclerView.scrollToPosition(displayMessages.lastIndex)
            }
        }
    }

    fun onKeyboardShiftChanged(extraShift: Int) {
        if (displayMessages.isNotEmpty()) {
            binding.chatRecyclerView.animate()
                .translationY(-extraShift.toFloat())
                .setDuration(180).setInterpolator(DecelerateInterpolator(1.6f)).start()
        } else {
            binding.emptyState.animate()
                .translationY(-(extraShift * 0.45f))
                .setDuration(180).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    fun onKeyboardClose() {
        binding.chatRecyclerView.animate()
            .translationY(0f)
            .setDuration(300).setInterpolator(DecelerateInterpolator(1.6f)).start()
        binding.emptyState.animate()
            .translationY(0f)
            .setDuration(300).setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    // ─── Input row ────────────────────────────────────────────────────────────

    private fun setupInput() {
        binding.inputMessage.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val newH = bottom - top; val oldH = oldBottom - oldTop
            if (newH == oldH || newH <= 0 || oldH <= 0 || !inputRowVisible) return@addOnLayoutChangeListener
            val fromH = if (inputRowHeightFrozen) frozenInputRowHeight else binding.inputRow.height
            if (preDrawListener != null) {
                binding.inputRow.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                preDrawListener = null
            }
            inputHeightAnimator?.cancel()
            val targetH = fromH + (newH - oldH)
            inputHeightAnimator = ValueAnimator.ofInt(fromH, targetH).apply {
                duration = 180; interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { anim ->
                    val h = anim.animatedValue as Int
                    inputRowHeightFrozen = true; frozenInputRowHeight = fromH
                    binding.inputRow.layoutParams = binding.inputRow.layoutParams.also { it.height = h }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        inputRowHeightFrozen = false
                        binding.inputRow.layoutParams = binding.inputRow.layoutParams.also {
                            it.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        inputRowHeight = binding.inputRow.height
                    }
                })
                start()
            }
        }

        binding.inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                if (hasText != sendBtnVisible) {
                    sendBtnVisible = hasText
                    animateSendBtn(hasText)
                }
            }
        })

        binding.btnSend.setOnClickListener {
            val text = binding.inputMessage.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }
    }

    private fun animateSendBtn(show: Boolean) {
        sendBtnAnimator?.cancel()
        if (show) {
            binding.btnSend.visibility = View.VISIBLE
            sendBtnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200; interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { binding.btnSend.alpha = it.animatedValue as Float }
                start()
            }
        } else {
            sendBtnAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 150; interpolator = DecelerateInterpolator()
                addUpdateListener { binding.btnSend.alpha = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.btnSend.visibility = View.GONE
                    }
                })
                start()
            }
        }
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
            duration = 260; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                binding.inputRow.layoutParams = binding.inputRow.layoutParams.also { it.height = h }
                binding.inputRow.alpha = h.toFloat() / targetH
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.inputRow.layoutParams = binding.inputRow.layoutParams.also {
                        it.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    binding.inputRow.alpha = 1f
                    frozenInputRowHeight = binding.inputRow.height
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
            duration = 220; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                val h = anim.animatedValue as Int
                binding.inputRow.layoutParams = binding.inputRow.layoutParams.also { it.height = h }
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

    // ─── Conversa ─────────────────────────────────────────────────────────────

    fun startNewConversation() {
        streamJob?.cancel()
        chatHistory.clear()
        displayMessages.clear()
        currentConversationId    = ""
        currentConversationTitle = "Nova conversa"
        titleGenerated           = false
        thinkingContent          = ""
        chatAdapter.notifyDataSetChanged()
        binding.inputMessage.setText("")
        syncVisibility()
        refreshNewChatBtn()
        // se estiver no tab preview, volta ao chat
        if (activity.currentTab == R.id.tabPreview) {
            activity.currentTab = R.id.tabPreview
        }
    }

    fun loadConversation(conv: Conversation) {
        streamJob?.cancel()
        chatHistory.clear()
        displayMessages.clear()
        currentConversationId    = conv.id
        currentConversationTitle = conv.title
        titleGenerated           = true
        thinkingContent          = ""
        conv.messages.forEach { msg ->
            chatHistory.add(msg)
            displayMessages.add(DisplayMessage(msg.role, msg.content))
        }
        chatAdapter.notifyDataSetChanged()
        binding.chatRecyclerView.post {
            if (displayMessages.isNotEmpty())
                binding.chatRecyclerView.scrollToPosition(displayMessages.lastIndex)
        }
        syncVisibility()
        refreshNewChatBtn()
        if (activity.currentTab == R.id.tabPreview) {
            activity.selectTab(R.id.tabPreview)
        }
    }

    fun saveCurrentConversation() {
        if (chatHistory.isEmpty()) return
        activity.lifecycleScope.launch {
            val id = AuthApiService.saveConversation(
                authToken,
                currentConversationId,
                currentConversationTitle,
                chatHistory
            )
            if (currentConversationId.isEmpty() && id.isNotEmpty()) {
                currentConversationId = id
                refreshNewChatBtn()
            }
        }
    }

    // ─── Enviar mensagem ──────────────────────────────────────────────────────

    private fun sendMessage(text: String) {
        binding.inputMessage.setText("")
        activity.hideKeyboard()

        if (displayMessages.isEmpty()) {
            binding.emptyState.visibility       = View.GONE
            binding.chatRecyclerView.visibility = View.VISIBLE
        }

        displayMessages.add(DisplayMessage("user", text))
        chatHistory.add(ChatMessage("user", text))
        chatAdapter.notifyItemInserted(displayMessages.lastIndex)

        val aiIndex = displayMessages.size
        val aiMsg   = DisplayMessage("assistant", "", isStreaming = true, isThinking = thinkMoreMode)
        displayMessages.add(aiMsg)
        chatAdapter.notifyItemInserted(aiIndex)
        binding.chatRecyclerView.scrollToPosition(aiIndex)
        refreshNewChatBtn()

        val token = authToken
        val lang  = prefs.getString("language", "pt") ?: "pt"

        streamJob?.cancel()
        streamJob = activity.lifecycleScope.launch {
            NvidiaApiService.streamMessage(
                messages   = chatHistory.dropLast(1),
                userMessage = text,
                token      = token,
                language   = lang,
                flash      = flashMode,
                thinkMore  = thinkMoreMode
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Thinking -> {
                        thinkingContent += chunk.text
                        aiMsg.isThinking = true
                        chatAdapter.notifyItemChanged(aiIndex)
                    }
                    is StreamChunk.Text -> {
                        if (aiMsg.isThinking) {
                            aiMsg.isThinking = false
                            aiMsg.content    = ""
                        }
                        aiMsg.content += chunk.text
                        chatAdapter.notifyItemChanged(aiIndex)
                        binding.chatRecyclerView.scrollToPosition(aiIndex)
                    }
                    is StreamChunk.Done -> {
                        aiMsg.isStreaming     = false
                        aiMsg.isThinking      = false
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
                                activity.drawerManager.loadConversations()
                            }
                        } else {
                            saveCurrentConversation()
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
                    textSize = 15f
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
                        col.addView(TextView(holder.wrapper.context).apply {
                            textSize = 15f
                            setLineSpacing(0f, 1.5f)
                            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
                            setPadding(dp(2), dp(4), dp(8), dp(4))
                            text = parseMarkdown(msg.content)
                        })
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

    // ─── Think modal ──────────────────────────────────────────────────────────

    private fun showThinkModal(content: String) {
        val dialog = BottomSheetDialog(activity)
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
            text = "Processo de pensamento"; textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setPadding(dp(20), dp(8), dp(20), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        card.addView(divider())
        val scroll = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        scroll.addView(TextView(activity).apply {
            text = content; textSize = 13f
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
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
                val screenH = activity.resources.displayMetrics.heightPixels
                it.layoutParams.height = (screenH * 0.75f).toInt()
                it.requestLayout()
                val behavior = BottomSheetBehavior.from(it)
                behavior.peekHeight = (screenH * 0.75f).toInt()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    // ─── Extras sheet ─────────────────────────────────────────────────────────

    fun showExtrasSheet() {
        activity.hidePopup()
        val dialog = BottomSheetDialog(activity)
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
            "Sheets", "A IA insere rascunhos HTML na conversa", sheetsEnabled, isSwitch = true) {
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
            text = subtitle; textSize = 12f
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2) }
        })
        row.addView(textCol)
        if (isSwitch) {
            row.addView(MaterialSwitch(activity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, _ -> onClick() }
            })
        } else {
            if (checked) {
                row.addView(ImageView(activity).apply {
                    setImageDrawable(activity.svgDrawable(iconOn, 18, GREEN))
                    layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                })
            }
            row.setOnClickListener { onClick() }
        }
        return row
    }

    // ─── Utilitários de UI ────────────────────────────────────────────────────

    private fun sheetHandle() = View(activity).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
            setColor(ContextCompat.getColor(activity, R.color.divider))
        }
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12); it.bottomMargin = dp(4)
        }
    }

    private fun divider() = View(activity).apply {
        setBackgroundColor(ContextCompat.getColor(activity, R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun extrasDiv() = View(activity).apply {
        setBackgroundColor(ContextCompat.getColor(activity, R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.marginStart = dp(58)
        }
    }

    private fun buildLoaderView(ctx: Context): View {
        val dotSize = dp(8); val gap = dp(6)
        val color   = ContextCompat.getColor(activity, R.color.colorPrimary)
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

    private fun dp(v: Int) = activity.dp(v)
}