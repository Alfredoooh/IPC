// ChatActivity.kt
package com.ipc.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ipc.app.R
import com.ipc.app.data.ChatMessage
import com.ipc.app.data.NvidiaApiService
import com.ipc.app.data.StreamChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }
    private val history = mutableListOf<ChatMessage>()
    private val displayMessages = mutableListOf<DisplayMessage>()
    private var streamJob: Job? = null

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var inputField: EditText
    private lateinit var btnSend: FrameLayout
    private lateinit var btnBack: ImageView
    private lateinit var progressSend: ProgressBar

    data class DisplayMessage(
        val role: String,
        var content: String,
        var isStreaming: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
        }

        val appBar = buildAppBar()
        root.addView(appBar)

        root.addView(View(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
        })

        recycler = RecyclerView(this).apply {
            id = R.id.chatRecyclerView
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            val mgr = LinearLayoutManager(context)
            mgr.stackFromEnd = true
            layoutManager = mgr
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(16), dp(12), dp(16), dp(8))
            clipToPadding = false
        }
        adapter = ChatAdapter(displayMessages)
        recycler.adapter = adapter
        root.addView(recycler)

        val inputBar = buildInputBar()
        root.addView(inputBar)

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nav    = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime    = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(top = status.top, bottom = if (ime.bottom > 0) ime.bottom else nav.bottom)
            insets
        }
    }

    private fun buildAppBar(): LinearLayout {
        val iconTint = ContextCompat.getColor(this, R.color.icon_tint)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.appbar_background))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
            setPadding(dp(8), 0, dp(16), 0)

            btnBack = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also {
                    it.marginEnd = dp(8)
                }
                setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", 18, iconTint))
                background = ContextCompat.getDrawable(context, R.drawable.appbar_btn_bg)
                padding(dp(10))
                setOnClickListener { onBackPressed() }
            }
            addView(btnBack)

            val titleTv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "Chat"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                runCatching {
                    val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
                    typeface = Typeface.create(tf, Typeface.BOLD)
                }
            }
            addView(titleTv)
        }
    }

    private fun buildInputBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(12), dp(8), dp(12), dp(12))

            val pillBg = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(ContextCompat.getColor(context, R.color.input_background))
            }
            val pillContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = pillBg
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.marginEnd = dp(8)
                }
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }

            inputField = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                hint = getString(R.string.input_hint)
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setHintTextColor(ContextCompat.getColor(context, R.color.text_hint))
                background = null
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 1
                maxLines = 5
            }
            pillContainer.addView(inputField)
            addView(pillContainer)

            val sendBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.colorPrimary))
            }
            btnSend = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                background = sendBg
                isClickable = true
                isFocusable = true
            }

            progressSend = ProgressBar(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).also {
                    it.gravity = android.view.Gravity.CENTER
                }
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                visibility = View.GONE
            }
            btnSend.addView(progressSend)

            val sendIcon = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).also {
                    it.gravity = android.view.Gravity.CENTER
                }
                setImageDrawable(svgDrawable("icons/svg/add.svg", 16, Color.WHITE))
            }
            btnSend.addView(sendIcon)

            btnSend.setOnClickListener { sendMessage() }
            addView(btnSend)
        }
    }

    private fun sendMessage() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty() || streamJob?.isActive == true) return

        inputField.text?.clear()
        history.add(ChatMessage("user", text))
        displayMessages.add(DisplayMessage("user", text))
        adapter.notifyItemInserted(displayMessages.lastIndex)
        recycler.smoothScrollToPosition(displayMessages.lastIndex)

        val aiMsg = DisplayMessage("assistant", "", isStreaming = true)
        displayMessages.add(aiMsg)
        val aiIndex = displayMessages.lastIndex
        adapter.notifyItemInserted(aiIndex)
        recycler.smoothScrollToPosition(aiIndex)

        progressSend.visibility = View.VISIBLE

        val lang = prefs.getString("language", "pt") ?: "pt"
        val systemPrompt = NvidiaApiService.buildSystemPrompt(lang)

        streamJob = lifecycleScope.launch {
            NvidiaApiService.streamChat(history, systemPrompt).collectLatest { chunk ->
                when (chunk) {
                    is StreamChunk.Token -> {
                        aiMsg.content += chunk.text
                        adapter.notifyItemChanged(aiIndex)
                        recycler.scrollToPosition(aiIndex)
                    }
                    is StreamChunk.Done -> {
                        aiMsg.isStreaming = false
                        aiMsg.content = chunk.fullText
                        history.add(ChatMessage("assistant", chunk.fullText))
                        adapter.notifyItemChanged(aiIndex)
                        progressSend.visibility = View.GONE
                    }
                    is StreamChunk.Error -> {
                        aiMsg.isStreaming = false
                        aiMsg.content = chunk.message
                        adapter.notifyItemChanged(aiIndex)
                        progressSend.visibility = View.GONE
                    }
                }
            }
        }
    }

    inner class ChatAdapter(
        private val msgs: List<DisplayMessage>
    ) : RecyclerView.Adapter<ChatAdapter.VH>() {

        inner class VH(val container: FrameLayout, val tv: TextView) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val container = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(4), 0, dp(4))
            }
            val tv = TextView(parent.context).apply {
                textSize = 15f
                setLineSpacing(0f, 1.4f)
                maxWidth = (parent.width * 0.82f).toInt()
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            container.addView(tv)
            return VH(container, tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = msgs[position]
            holder.tv.text = if (msg.isStreaming && msg.content.isEmpty()) "…" else msg.content

            val lp = holder.tv.layoutParams as FrameLayout.LayoutParams

            if (msg.role == "user") {
                holder.tv.setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(ContextCompat.getColor(holder.tv.context, R.color.colorPrimary))
                }
                holder.tv.background = bg
                lp.gravity = Gravity.END
                lp.topMargin = dp(2)
                lp.bottomMargin = dp(2)
                lp.marginStart = dp(48)
                lp.marginEnd = dp(0)
            } else {
                holder.tv.setTextColor(ContextCompat.getColor(holder.tv.context, R.color.text_primary))
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(ContextCompat.getColor(holder.tv.context, R.color.card_background))
                }
                holder.tv.background = bg
                lp.gravity = Gravity.START
                lp.topMargin = dp(2)
                lp.bottomMargin = dp(2)
                lp.marginStart = dp(0)
                lp.marginEnd = dp(48)
            }
            holder.tv.layoutParams = lp
        }

        override fun getItemCount() = msgs.size
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun View.padding(p: Int) {
        setPadding(p, p, p, p)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
    }
}