package com.ipc.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ipc.app.data.AuthApiService
import com.ipc.app.data.Conversation
import kotlinx.coroutines.launch
import java.util.Calendar

class DrawerManager(private val activity: MainActiviy) {

    private val binding get() = activity.binding
    private val authToken get() = activity.authToken
    private val drawerConversations = mutableListOf<Conversation>()

    fun loadConversations() {
        activity.lifecycleScope.launch {
            val list = AuthApiService.listConversations(authToken)
            drawerConversations.clear()
            drawerConversations.addAll(list)
            refreshDrawerConversations()
        }
    }

    private fun groupConversations(list: List<Conversation>): List<Any> {
        val now   = System.currentTimeMillis()
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
                    container.addView(TextView(activity).apply {
                        text = item
                        textSize = 11f
                        setTypeface(null, Typeface.NORMAL)
                        setTextColor(ContextCompat.getColor(activity, R.color.settings_section_label))
                        setPadding(dp(24), dp(12), dp(24), dp(4))
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    })
                }
                is Conversation -> {
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(20), 0, dp(12), 0)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
                        val a = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                        background = a.getDrawable(0); a.recycle()
                        isClickable = true; isFocusable = true
                    }

                    // pin_filled quando fixada
                    if (item.pinned) {
                        row.addView(ImageView(activity).apply {
                            setImageDrawable(activity.svgDrawable("icons/svg/pin_filled.svg", 13,
                                ContextCompat.getColor(activity, R.color.colorPrimary)))
                            layoutParams = LinearLayout.LayoutParams(dp(13), dp(13)).also { it.marginEnd = dp(6) }
                        })
                    }

                    row.addView(TextView(activity).apply {
                        text = item.title
                        textSize = 14.5f
                        setTextColor(ContextCompat.getColor(activity, R.color.drawer_text))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })

                    row.addView(ImageView(activity).apply {
                        setImageDrawable(activity.svgDrawable("icons/svg/trash.svg", 15, Color.parseColor("#C0C0C0")))
                        layoutParams = LinearLayout.LayoutParams(dp(36), dp(52)).also { it.marginStart = dp(4) }
                        scaleType = ImageView.ScaleType.CENTER
                        val a = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                        background = a.getDrawable(0); a.recycle()
                        isClickable = true; isFocusable = true
                        setOnClickListener { showDeleteConfirmation(item) }
                    })

                    row.setOnClickListener {
                        activity.closeDrawer()
                        binding.root.postDelayed({ activity.chatFragment.loadConversation(item) }, 250)
                    }
                    row.setOnLongClickListener {
                        showConversationOptions(item)
                        true
                    }
                    container.addView(row)
                }
            }
        }
    }

    // ─── Modal: opções da conversa ────────────────────────────────────────────

    fun showConversationOptions(conv: Conversation) {
        val dialog = BottomSheetDialog(activity)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
                setColor(ContextCompat.getColor(activity, R.color.dialog_background))
            }
        }
        card.addView(sheetHandle())
        card.addView(TextView(activity).apply {
            text = conv.title; textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(20), dp(4), dp(20), dp(12))
        })
        card.addView(divider())

        // Fixar/desafixar — pin / pin_filled
        val pinLabel = if (conv.pinned) "Desafixar" else "Fixar"
        val pinIcon  = if (conv.pinned) "icons/svg/pin_filled.svg" else "icons/svg/pin.svg"
        card.addView(optionRow(pinIcon, pinLabel) {
            dialog.dismiss()
            activity.lifecycleScope.launch {
                AuthApiService.pinConversation(authToken, conv.id, !conv.pinned)
                loadConversations()
            }
        })

        val archLabel = if (conv.archived) "Desarquivar" else "Arquivar"
        card.addView(optionRow("icons/svg/history.svg", archLabel) {
            dialog.dismiss()
            activity.lifecycleScope.launch {
                AuthApiService.archiveConversation(authToken, conv.id, !conv.archived)
                if (conv.id == activity.chatFragment.currentConversationId) activity.chatFragment.startNewConversation()
                else loadConversations()
            }
        })
        card.addView(optionRow("icons/svg/trash.svg", "Eliminar", Color.parseColor("#FF3B30")) {
            dialog.dismiss()
            showDeleteConfirmation(conv)
        })
        card.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20))
        })

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(card)
        }
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    // ─── Modal: confirmação de eliminação ─────────────────────────────────────

    fun showDeleteConfirmation(conv: Conversation) {
        val dialog = BottomSheetDialog(activity)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
                setColor(ContextCompat.getColor(activity, R.color.dialog_background))
            }
        }
        card.addView(sheetHandle())
        card.addView(ImageView(activity).apply {
            setImageDrawable(activity.svgDrawable("icons/svg/trash.svg", 28, Color.parseColor("#FF3B30")))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp(12)
            }
        })
        card.addView(TextView(activity).apply {
            text = "Eliminar conversa?"
            textSize = 17f; setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        card.addView(TextView(activity).apply {
            text = "\"${conv.title.take(40)}\" será eliminada permanentemente."
            textSize = 14f
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), dp(24))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), dp(24))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        btnRow.addView(TextView(activity).apply {
            text = "Cancelar"; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(activity, R.color.card_background))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).also { it.marginEnd = dp(8) }
            setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(TextView(activity).apply {
            text = "Eliminar"; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#FF3B30"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).also { it.marginStart = dp(8) }
            setOnClickListener {
                dialog.dismiss()
                activity.lifecycleScope.launch {
                    AuthApiService.deleteConversation(authToken, conv.id)
                    if (conv.id == activity.chatFragment.currentConversationId) activity.chatFragment.startNewConversation()
                    else loadConversations()
                }
            }
        })
        card.addView(btnRow)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(card)
        }
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    // ─── Utilitários ──────────────────────────────────────────────────────────

    private fun optionRow(iconPath: String, label: String, color: Int = ContextCompat.getColor(activity, R.color.text_primary), action: () -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54); setPadding(dp(20), 0, dp(20), 0)
            val a = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = a.getDrawable(0); a.recycle()
            isClickable = true; isFocusable = true
        }
        row.addView(ImageView(activity).apply {
            setImageDrawable(activity.svgDrawable(iconPath, 20, color))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginEnd = dp(16) }
        })
        row.addView(TextView(activity).apply {
            text = label; textSize = 15f; setTextColor(color)
        })
        row.setOnClickListener { action() }
        return row
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

    private fun divider() = View(activity).apply {
        setBackgroundColor(ContextCompat.getColor(activity, R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun dp(v: Int) = activity.dp(v)
}