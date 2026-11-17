package com.kepord.ime.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.kepord.ime.ai.AiChatMessage
import com.kepord.ime.ai.AiConfigState
import com.kepord.ime.ai.AiKeyStatus
import com.kepord.ime.clipboard.ClipEntry
import com.kepord.ime.model.KepordConfig
import kotlin.math.max

class KepordImePanel(
    context: Context,
    private val actions: PanelActions
) : FrameLayout(context) {

    interface PanelActions {
        fun onPasteClip(entry: ClipEntry)
        fun onTogglePin(entry: ClipEntry)
        fun onDeleteClip(entry: ClipEntry)
        fun onClearUnpinned()
        fun onToggleLanguage()
        fun onOpenSettings()
        fun onCommitEmoji(emoji: String)
        fun onEmojiBackspace()
        fun onMoveCursor(delta: Int)
        fun onSelectAll()
        fun onCopySelection()
        fun onCutSelection()
        fun onPasteSystemClipboard()
        fun onActivateMouseTouchpad()
        fun onOpenAccessibilitySettings()
        fun onSendAiPrompt(prompt: String)
        fun onFetchAiModels()
        fun onSelectAiModel(modelId: String)
        fun onToggleAiWebSearch()
        fun onInsertAiReply(text: String)
        fun onCopyAiReply(text: String)
        fun onClearAiChat()
        fun onAiComposerModeChanged(composing: Boolean)
    }

    enum class OverlayMode {
        KEYBOARD,
        CLIPBOARD,
        EMOJI,
        EDIT_TOOLS,
        AI_CHAT,
        TOUCHPAD_PROMPT
    }

    private val density = resources.displayMetrics.density
    private val spScale = resources.displayMetrics.scaledDensity
    private var mode = OverlayMode.KEYBOARD
    private var clipFilter = 0
    private var allClips: List<ClipEntry> = emptyList()
    private var filteredClips: List<ClipEntry> = emptyList()

    private var recentEmojis: List<String> = emptyList()
    private var selectedEmojiCategoryIdx = 1
    private var emojiCategories: List<EmojiCategory> = EmojiCatalog.getCategories(emptyList())
    private val emojiTabViews = mutableListOf<TextView>()
    private val emojiGrid = GridLayout(context)
    private val emojiCategoryLabel = TextView(context)
    private val emojiScrollView = ScrollView(context)

    private val mainColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.BLACK)
    }
    private val popupOverlay = FloatingKeyPopupOverlay(context).apply {
        translationZ = 24f * density
    }

    val keyboardView = KepordKeyboardView(context)
    private val contentContainer = FrameLayout(context)
    private val clipboardContainer = LinearLayout(context)
    private val emojiContainer = LinearLayout(context)
    private val editToolsContainer = LinearLayout(context)
    private val aiChatContainer = LinearLayout(context)
    private val touchpadFallbackContainer = LinearLayout(context)

    private val clipCountBadge = TextView(context)
    private val langBadge = TextView(context)
    private val fontBadge = TextView(context)
    private val aiBtn = TextView(context)
    private val mouseBtn = TextView(context)

    private val clipEmptyView = TextView(context)
    private val clipListView = ListView(context)
    private val clipAdapter = ClipListAdapter()
    private val filterBtns = mutableListOf<TextView>()

    private val aiDraftBar = LinearLayout(context)
    private val aiDraftPreviewTv = TextView(context)
    private val aiModelChip = TextView(context)
    private val aiWebChip = TextView(context)
    private val aiKeysChip = TextView(context)
    private val aiFetchChip = TextView(context)
    private val aiModelPickerScroll = ScrollView(context)
    private val aiModelPickerList = LinearLayout(context)
    private val aiMessagesScroll = ScrollView(context)
    private val aiMessagesList = LinearLayout(context)
    private val aiPromptTriggerTv = TextView(context)
    private val aiLoadingTv = TextView(context)

    private var isComposingAiPrompt = false
    private val aiDraftBuilder = StringBuilder()
    private var aiStateSnapshot = AiConfigState()
    private var aiMessagesSnapshot: List<AiChatMessage> = emptyList()
    private var aiModelDropdownOpen = false

    init {
        setBackgroundColor(Color.BLACK)

        buildAiDraftComposerBar()
        aiDraftBar.visibility = View.GONE
        mainColumn.addView(
            aiDraftBar,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44))
        )

        mainColumn.addView(
            buildTopToolbar(),
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40))
        )

        val divider = View(context).apply {
            setBackgroundColor(Color.rgb(34, 34, 42))
        }
        mainColumn.addView(divider, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1))

        contentContainer.addView(
            keyboardView,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        buildClipboardOverlay()
        clipboardContainer.visibility = View.GONE
        contentContainer.addView(
            clipboardContainer,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(276))
        )

        buildEmojiOverlay()
        emojiContainer.visibility = View.GONE
        contentContainer.addView(
            emojiContainer,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(276))
        )

        buildEditToolsOverlay()
        editToolsContainer.visibility = View.GONE
        contentContainer.addView(
            editToolsContainer,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(236))
        )

        buildAiChatOverlay()
        aiChatContainer.visibility = View.GONE
        contentContainer.addView(
            aiChatContainer,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(276))
        )

        buildTouchpadPromptOverlay()
        touchpadFallbackContainer.visibility = View.GONE
        contentContainer.addView(
            touchpadFallbackContainer,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(246))
        )

        mainColumn.addView(
            contentContainer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        addView(mainColumn, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(popupOverlay, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        keyboardView.popupDelegate = object : KepordKeyboardView.PopupDelegate {
            override fun showSingleKeyPopup(
                anchorRectInKb: RectF,
                label: String,
                color: Int,
                config: KepordConfig
            ) {
                popupOverlay.setSinglePopup(anchorRectInKb, label, color, config)
            }

            override fun showLongPressPopup(
                anchorRectInKb: RectF,
                alternates: List<String>,
                selectedIndex: Int
            ) {
                popupOverlay.setLongPressPopup(anchorRectInKb, alternates, selectedIndex)
            }

            override fun hidePopup() {
                popupOverlay.clear()
            }
        }
    }

    fun applyConfig(config: KepordConfig, forceDefaultLang: Boolean) {
        keyboardView.applyConfig(config, forceDefaultLang)
        updateLanguageLabel(config.defaultLanguageArabic)
        fontBadge.text = if (config.arabicFontStyle == ArabicFontStyle.NORMAL) "الخط" else "الخط ✓"
        fontBadge.setTextColor(
            if (config.arabicFontStyle == ArabicFontStyle.NORMAL) Color.rgb(210, 210, 220) else Color.WHITE
        )
    }

    fun updateLanguageLabel(isArabic: Boolean) {
        langBadge.text = if (isArabic) "AR/EN" else "EN/AR"
    }

    fun updateClips(clips: List<ClipEntry>) {
        allClips = clips
        clipCountBadge.text = "حافظة(${clips.size})"
        applyClipFilter()
    }

    fun updateRecentEmojis(recent: List<String>) {
        recentEmojis = recent
        emojiCategories = EmojiCatalog.getCategories(recentEmojis)
        if (mode == OverlayMode.EMOJI && selectedEmojiCategoryIdx == 0) {
            populateEmojiGrid()
        }
    }

    fun isAiPromptComposing(): Boolean = isComposingAiPrompt

    fun appendAiDraftText(txt: String) {
        aiDraftBuilder.append(txt)
        refreshAiDraftUi()
    }

    fun backspaceAiDraft() {
        if (aiDraftBuilder.isNotEmpty()) {
            val lastIdx = aiDraftBuilder.length - 1
            if (Character.isLowSurrogate(aiDraftBuilder[lastIdx]) && lastIdx > 0) {
                aiDraftBuilder.delete(lastIdx - 1, aiDraftBuilder.length)
            } else {
                aiDraftBuilder.deleteCharAt(lastIdx)
            }
            refreshAiDraftUi()
        }
    }

    fun submitCurrentAiDraft() {
        val q = aiDraftBuilder.toString().trim()
        isComposingAiPrompt = false
        actions.onAiComposerModeChanged(false)
        aiDraftBar.visibility = View.GONE
        if (q.isNotEmpty()) {
            aiDraftBuilder.clear()
            refreshAiDraftUi()
            switchMode(OverlayMode.AI_CHAT)
            actions.onSendAiPrompt(q)
        } else {
            switchMode(OverlayMode.AI_CHAT)
        }
    }

    fun updateAiState(state: AiConfigState) {
        aiStateSnapshot = state
        val shortModel = state.selectedModel.substringAfterLast('/').take(18)
        aiModelChip.text = "$shortModel ▾"
        aiWebChip.text = if (state.webSearchEnabled) "🌐 ويب ✓" else "🌐 ويب ✕"
        aiWebChip.setTextColor(if (state.webSearchEnabled) Color.BLACK else Color.WHITE)
        aiWebChip.background = if (state.webSearchEnabled) {
            pillDrawable(Color.WHITE, Color.WHITE)
        } else {
            pillDrawable(Color.rgb(22, 22, 28), Color.rgb(65, 65, 78))
        }

        val now = System.currentTimeMillis()
        val providerKeys = state.keys.filter { it.provider == state.provider }
        val healthyCount = providerKeys.count {
            it.status == AiKeyStatus.ACTIVE || (it.status == AiKeyStatus.COOLDOWN && it.cooldownUntilMs <= now)
        }
        aiKeysChip.text = "🔑 $healthyCount/${providerKeys.size}"
        renderAiModelPickerList()
    }

    fun updateAiChatMessages(messages: List<AiChatMessage>, isLoading: Boolean, statusNote: String = "") {
        aiMessagesSnapshot = messages
        aiLoadingTv.visibility = if (isLoading || statusNote.isNotEmpty()) View.VISIBLE else View.GONE
        aiLoadingTv.text = if (isLoading) "جاري التفكير والبحث الذكي..." else statusNote

        aiMessagesList.removeAllViews()
        if (messages.isEmpty()) {
            val welcome = TextView(context).apply {
                text = "اسأل الذكاء الاصطناعي أي سؤال (أسعار الصرف الآن، ترجمة، برمجة، بحث ويب) وسيتم استخدام مفاتيحك الذكية بالتناوب التلقائي."
                setTextColor(Color.rgb(175, 175, 190))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(22), dp(12), dp(16))
            }
            aiMessagesList.addView(welcome)
            return
        }

        for (msg in messages) {
            val bubble = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = if (msg.isUser) {
                    pillDrawable(Color.rgb(26, 26, 34), Color.rgb(90, 90, 108))
                } else {
                    pillDrawable(Color.rgb(12, 12, 16), Color.WHITE)
                }
            }

            val tv = TextView(context).apply {
                text = msg.text
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setTextIsSelectable(false)
            }
            bubble.addView(tv)

            if (msg.meta.isNotBlank()) {
                val metaTv = TextView(context).apply {
                    text = msg.meta
                    setTextColor(Color.rgb(160, 160, 176))
                    textSize = 10f
                    setPadding(0, dp(3), 0, 0)
                }
                bubble.addView(metaTv)
            }

            if (!msg.isUser) {
                val actRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(6), 0, 0)
                }
                val insertBtn = TextView(context).apply {
                    text = "إدراج في النص ↵"
                    setTextColor(Color.BLACK)
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    background = pillDrawable(Color.WHITE, Color.WHITE)
                    setPadding(dp(9), dp(4), dp(9), dp(4))
                    setOnClickListener { actions.onInsertAiReply(msg.text) }
                }
                val copyBtn = TextView(context).apply {
                    text = "نسخ"
                    setTextColor(Color.WHITE)
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    background = pillDrawable(Color.rgb(24, 24, 30), Color.rgb(85, 85, 98))
                    setPadding(dp(9), dp(4), dp(9), dp(4))
                    setOnClickListener { actions.onCopyAiReply(msg.text) }
                }
                actRow.addView(
                    insertBtn,
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(6)
                    }
                )
                actRow.addView(copyBtn)
                bubble.addView(actRow)
            }

            val lp = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
                if (msg.isUser) leftMargin = dp(24) else rightMargin = dp(10)
            }
            aiMessagesList.addView(bubble, lp)
        }

        aiMessagesScroll.post {
            aiMessagesScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun exitAiComposing() {
        isComposingAiPrompt = false
        aiDraftBar.visibility = View.GONE
        actions.onAiComposerModeChanged(false)
    }

    private fun startComposingAiPrompt() {
        mode = OverlayMode.KEYBOARD
        isComposingAiPrompt = true
        aiDraftBar.visibility = View.VISIBLE
        keyboardView.visibility = View.VISIBLE
        clipboardContainer.visibility = View.GONE
        emojiContainer.visibility = View.GONE
        editToolsContainer.visibility = View.GONE
        aiChatContainer.visibility = View.GONE
        touchpadFallbackContainer.visibility = View.GONE
        keyboardView.setActiveWindowVisibility(true)
        actions.onAiComposerModeChanged(true)
        refreshAiDraftUi()
    }

    fun switchMode(newMode: OverlayMode) {
        popupOverlay.clear()
        if (newMode != OverlayMode.KEYBOARD && isComposingAiPrompt) {
            isComposingAiPrompt = false
            aiDraftBar.visibility = View.GONE
            actions.onAiComposerModeChanged(false)
        }

        mode = if (mode == newMode && newMode != OverlayMode.KEYBOARD) OverlayMode.KEYBOARD else newMode
        keyboardView.visibility = if (mode == OverlayMode.KEYBOARD) View.VISIBLE else View.GONE
        clipboardContainer.visibility = if (mode == OverlayMode.CLIPBOARD) View.VISIBLE else View.GONE
        emojiContainer.visibility = if (mode == OverlayMode.EMOJI) View.VISIBLE else View.GONE
        editToolsContainer.visibility = if (mode == OverlayMode.EDIT_TOOLS) View.VISIBLE else View.GONE
        aiChatContainer.visibility = if (mode == OverlayMode.AI_CHAT) View.VISIBLE else View.GONE
        touchpadFallbackContainer.visibility = if (mode == OverlayMode.TOUCHPAD_PROMPT) View.VISIBLE else View.GONE
        keyboardView.setActiveWindowVisibility(mode == OverlayMode.KEYBOARD)

        if (mode == OverlayMode.EMOJI) {
            emojiCategories = EmojiCatalog.getCategories(recentEmojis)
            renderEmojiCategoryTabs()
            populateEmojiGrid()
        }
    }

    private fun buildTopToolbar(): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(6, 6, 8))
            setPadding(dp(5), dp(4), dp(5), dp(4))
        }

        clipCountBadge.apply {
            text = "حافظة(0)"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(20, 20, 26), Color.rgb(62, 62, 74))
            setOnClickListener { switchMode(OverlayMode.CLIPBOARD) }
        }
        bar.addView(clipCountBadge, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.15f).apply { marginEnd = dp(3) })

        langBadge.apply {
            text = "AR/EN"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(20, 20, 26), Color.rgb(62, 62, 74))
            setOnClickListener {
                actions.onToggleLanguage()
                switchMode(OverlayMode.KEYBOARD)
            }
        }
        bar.addView(langBadge, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.90f).apply { marginEnd = dp(3) })

        bar.addView(
            toolbarBtn("😀") { switchMode(OverlayMode.EMOJI) },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.58f).apply { marginEnd = dp(3) }
        )

        mouseBtn.apply {
            text = "🖱 ماوس"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(18, 18, 24), Color.rgb(75, 75, 90))
            setOnClickListener { actions.onActivateMouseTouchpad() }
        }
        bar.addView(mouseBtn, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.95f).apply { marginEnd = dp(3) })

        aiBtn.apply {
            text = "✨ AI"
            setTextColor(Color.BLACK)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setOnClickListener { switchMode(OverlayMode.AI_CHAT) }
        }
        bar.addView(aiBtn, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.78f).apply { marginEnd = dp(3) })

        bar.addView(
            toolbarBtn("تحرير") { switchMode(OverlayMode.EDIT_TOOLS) },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.68f).apply { marginEnd = dp(3) }
        )

        fontBadge.apply {
            text = "الخط"
            setTextColor(Color.rgb(210, 210, 220))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(18, 18, 22), Color.rgb(48, 48, 58))
            setOnClickListener { actions.onOpenSettings() }
        }
        bar.addView(fontBadge, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.66f).apply { marginEnd = dp(3) })

        bar.addView(
            toolbarBtn("⚙") { actions.onOpenSettings() },
            LinearLayout.LayoutParams(dp(32), LayoutParams.MATCH_PARENT)
        )

        return bar
    }

    private fun toolbarBtn(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.rgb(235, 235, 245))
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(18, 18, 22), Color.rgb(48, 48, 58))
            setOnClickListener { onClick() }
        }
    }

    private fun buildAiDraftComposerBar() {
        aiDraftBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(12, 12, 16))
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }

        val cancelBtn = TextView(context).apply {
            text = "رجوع للشات"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            background = pillDrawable(Color.rgb(24, 24, 30), Color.rgb(75, 75, 88))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                exitAiComposing()
                switchMode(OverlayMode.AI_CHAT)
            }
        }
        aiDraftBar.addView(cancelBtn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(6)
        })

        aiDraftPreviewTv.apply {
            text = "اكتب سؤالك للذكاء الاصطناعي..."
            setTextColor(Color.rgb(175, 175, 190))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL
            background = pillDrawable(Color.BLACK, Color.rgb(65, 65, 78))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        aiDraftBar.addView(aiDraftPreviewTv, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(6)
        })

        val sendBtn = TextView(context).apply {
            text = "إرسال ➤"
            setTextColor(Color.BLACK)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { submitCurrentAiDraft() }
        }
        aiDraftBar.addView(sendBtn)
    }

    private fun refreshAiDraftUi() {
        if (aiDraftBuilder.isEmpty()) {
            aiDraftPreviewTv.text = "اكتب سؤالك للذكاء الاصطناعي..."
            aiDraftPreviewTv.setTextColor(Color.rgb(160, 160, 176))
            aiPromptTriggerTv.text = "✍️ اضغط هنا لكتابة سؤالك بالكيبورد..."
        } else {
            aiDraftPreviewTv.text = aiDraftBuilder.toString()
            aiDraftPreviewTv.setTextColor(Color.WHITE)
            aiPromptTriggerTv.text = aiDraftBuilder.toString()
        }
    }

    private fun buildAiChatOverlay() {
        aiChatContainer.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
        }

        aiModelChip.apply {
            text = "Model ▾"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = pillDrawable(Color.rgb(18, 18, 24), Color.rgb(75, 75, 90))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setOnClickListener {
                aiModelDropdownOpen = !aiModelDropdownOpen
                aiModelPickerScroll.visibility = if (aiModelDropdownOpen) View.VISIBLE else View.GONE
            }
        }
        header.addView(aiModelChip, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.35f).apply { marginEnd = dp(4) })

        aiFetchChip.apply {
            text = "↻ جلب"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            background = pillDrawable(Color.rgb(20, 20, 26), Color.rgb(70, 70, 84))
            setPadding(dp(7), dp(5), dp(7), dp(5))
            setOnClickListener { actions.onFetchAiModels() }
        }
        header.addView(aiFetchChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })

        aiWebChip.apply {
            text = "🌐 ويب ✓"
            setTextColor(Color.BLACK)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(7), dp(5), dp(7), dp(5))
            setOnClickListener { actions.onToggleAiWebSearch() }
        }
        header.addView(aiWebChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })

        aiKeysChip.apply {
            text = "🔑 0"
            setTextColor(Color.rgb(210, 210, 222))
            textSize = 10f
            background = pillDrawable(Color.rgb(16, 16, 20), Color.rgb(55, 55, 66))
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setOnClickListener { actions.onOpenSettings() }
        }
        header.addView(aiKeysChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })

        val backToKbBtn = TextView(context).apply {
            text = "⌨ كيبورد"
            setTextColor(Color.BLACK)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setOnClickListener { switchMode(OverlayMode.KEYBOARD) }
        }
        header.addView(backToKbBtn)
        aiChatContainer.addView(header)

        aiModelPickerList.orientation = LinearLayout.VERTICAL
        aiModelPickerScroll.apply {
            visibility = View.GONE
            setBackgroundColor(Color.rgb(12, 12, 17))
            addView(aiModelPickerList)
        }
        aiChatContainer.addView(aiModelPickerScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(110)).apply {
            bottomMargin = dp(4)
        })

        aiLoadingTv.apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(4))
        }
        aiChatContainer.addView(aiLoadingTv)

        aiMessagesList.orientation = LinearLayout.VERTICAL
        aiMessagesScroll.addView(aiMessagesList)
        aiChatContainer.addView(aiMessagesScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val quickRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        quickRow.addView(
            quickAiChip("سعر الصرف اليوم") { actions.onSendAiPrompt("ما هي أسعار صرف الدولار واليورو والشيكل والدينار والريال اليوم؟ أجب بإيجاز.") },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) }
        )
        quickRow.addView(
            quickAiChip("ترجم آخر منسوخ") {
                val clipTxt = allClips.firstOrNull()?.preview ?: ""
                if (clipTxt.isBlank()) {
                    actions.onSendAiPrompt("اشرح لي كيف أترجم نصاً بسرعة.")
                } else {
                    actions.onSendAiPrompt("ترجم النص التالي للعربية أو الإنجليزية حسب لغته بشكل احترافي:\n$clipTxt")
                }
            },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) }
        )
        quickRow.addView(
            quickAiChip("مسح المحادثة") { actions.onClearAiChat() },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        aiChatContainer.addView(quickRow)

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        aiPromptTriggerTv.apply {
            text = "✍️ اضغط هنا لكتابة سؤالك بالكيبورد..."
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = pillDrawable(Color.rgb(16, 16, 22), Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { startComposingAiPrompt() }
        }
        inputRow.addView(aiPromptTriggerTv, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val sendShortcut = TextView(context).apply {
            text = "إرسال ➤"
            setTextColor(Color.BLACK)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(11), dp(8), dp(11), dp(8))
            setOnClickListener {
                val txt = aiDraftBuilder.toString().trim()
                if (txt.isNotEmpty()) {
                    aiDraftBuilder.clear()
                    refreshAiDraftUi()
                    actions.onSendAiPrompt(txt)
                } else {
                    startComposingAiPrompt()
                }
            }
        }
        inputRow.addView(sendShortcut)
        aiChatContainer.addView(inputRow)
    }

    private fun quickAiChip(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.rgb(225, 225, 238))
            textSize = 10f
            background = pillDrawable(Color.rgb(18, 18, 24), Color.rgb(55, 55, 68))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
        }
    }

    private fun renderAiModelPickerList() {
        aiModelPickerList.removeAllViews()
        val models = aiStateSnapshot.fetchedModels
        if (models.isEmpty()) {
            aiModelPickerList.addView(TextView(context).apply {
                text = "اضغط (↻ جلب) لاستجلاب كل النماذج المتاحة من هذا المزود، أو اكتب النموذج من الإعدادات."
                setTextColor(Color.rgb(175, 175, 190))
                textSize = 11f
                setPadding(dp(10), dp(10), dp(10), dp(10))
            })
            return
        }
        for (m in models) {
            val selected = m.id == aiStateSnapshot.selectedModel
            val row = TextView(context).apply {
                text = if (selected) "✓ ${m.displayName} (${m.id})" else "${m.displayName} (${m.id})"
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                textSize = 11.5f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = if (selected) {
                    pillDrawable(Color.WHITE, Color.WHITE)
                } else {
                    pillDrawable(Color.rgb(20, 20, 26), Color.rgb(48, 48, 60))
                }
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener {
                    actions.onSelectAiModel(m.id)
                    aiModelDropdownOpen = false
                    aiModelPickerScroll.visibility = View.GONE
                }
            }
            aiModelPickerList.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            })
        }
    }

    private fun buildTouchpadPromptOverlay() {
        touchpadFallbackContainer.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.CENTER
        }

        touchpadFallbackContainer.addView(TextView(context).apply {
            text = "تفعيل الماوس ولوحة اللمس الشاملة على الشاشة"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        touchpadFallbackContainer.addView(TextView(context).apply {
            text = "لإظهار مؤشر الماوس والتحكم في كامل الشاشة وتحديد النص بالضغطتين الذكيتين:\n1) فعّل «Kepord Mouse & Touchpad» من إعدادات إمكانية الوصول.\n2) على Android 13 (Realme): إن ظهر الخيار باهتاً، افتح معلومات التطبيق ← ⋮ بالأعلى ← السماح بالإعدادات المقيدة."
            setTextColor(Color.rgb(190, 190, 205))
            textSize = 11.5f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val openAccBtn = TextView(context).apply {
            text = "فتح إعدادات إمكانية الوصول"
            setTextColor(Color.BLACK)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { actions.onOpenAccessibilitySettings() }
        }
        val backBtn = TextView(context).apply {
            text = "⌨ كيبورد"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(24, 24, 30), Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { switchMode(OverlayMode.KEYBOARD) }
        }
        btnRow.addView(openAccBtn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(8)
        })
        btnRow.addView(backBtn)
        touchpadFallbackContainer.addView(btnRow)
    }

    private fun buildClipboardOverlay() {
        clipboardContainer.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val filterLabels = listOf("الكل", "المثبتة", "نصوص طويلة")
        filterBtns.clear()
        filterLabels.forEachIndexed { idx, title ->
            val btn = TextView(context).apply {
                text = title
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                setOnClickListener {
                    clipFilter = idx
                    applyClipFilter()
                }
            }
            filterBtns.add(btn)
            header.addView(btn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(5)
            })
        }

        val spacer = View(context)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val clearBtn = TextView(context).apply {
            text = "مسح غير المثبت"
            setTextColor(Color.rgb(210, 210, 220))
            textSize = 10.5f
            background = pillDrawable(Color.rgb(22, 22, 26), Color.rgb(70, 70, 82))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setOnClickListener { actions.onClearUnpinned() }
        }
        header.addView(clearBtn)

        val closeBtn = TextView(context).apply {
            text = "كيبورد ✕"
            setTextColor(Color.BLACK)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            setOnClickListener { switchMode(OverlayMode.KEYBOARD) }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(5)
        })

        clipboardContainer.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        clipEmptyView.apply {
            text = "الحافظة فارغة حالياً.\nأي نص أو كود تنسخه (حتى آلاف الأسطر) سيُحفظ هنا ويبقى بعد إعادة تشغيل الهاتف."
            setTextColor(Color.rgb(165, 165, 178))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
            visibility = View.GONE
        }
        clipboardContainer.addView(clipEmptyView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        clipListView.apply {
            divider = null
            dividerHeight = dp(6)
            adapter = clipAdapter
        }
        clipboardContainer.addView(clipListView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        applyClipFilter()
    }

    private fun applyClipFilter() {
        filterBtns.forEachIndexed { idx, tv ->
            val selected = idx == clipFilter
            tv.setTextColor(if (selected) Color.BLACK else Color.WHITE)
            tv.background = if (selected) {
                pillDrawable(Color.WHITE, Color.WHITE)
            } else {
                pillDrawable(Color.rgb(20, 20, 25), Color.rgb(60, 60, 72))
            }
        }

        filteredClips = when (clipFilter) {
            1 -> allClips.filter { it.isPinned }
            2 -> allClips.filter { it.lineCount >= 20 || it.charCount >= 1000 }
            else -> allClips
        }

        clipEmptyView.visibility = if (filteredClips.isEmpty()) View.VISIBLE else View.GONE
        clipListView.visibility = if (filteredClips.isEmpty()) View.GONE else View.VISIBLE
        clipAdapter.notifyDataSetChanged()
    }

    private fun buildEmojiOverlay() {
        emojiContainer.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val topStrip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
        }

        val catScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val catRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        emojiTabViews.clear()
        emojiCategories.forEachIndexed { idx, cat ->
            val tab = TextView(context).apply {
                text = cat.icon
                textSize = 16.5f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                setOnClickListener {
                    selectedEmojiCategoryIdx = idx
                    renderEmojiCategoryTabs()
                    populateEmojiGrid()
                    emojiScrollView.scrollTo(0, 0)
                }
            }
            emojiTabViews.add(tab)
            catRow.addView(tab, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            })
        }
        catScroll.addView(catRow)
        topStrip.addView(catScroll, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val delBtn = TextView(context).apply {
            text = "⌫"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(22, 22, 28), Color.rgb(70, 70, 82))
            setPadding(dp(11), dp(5), dp(11), dp(5))
            setOnClickListener { actions.onEmojiBackspace() }
        }
        topStrip.addView(delBtn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(5)
        })

        val backBtn = TextView(context).apply {
            text = "ABC"
            setTextColor(Color.BLACK)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { switchMode(OverlayMode.KEYBOARD) }
        }
        topStrip.addView(backBtn)
        emojiContainer.addView(topStrip)

        emojiCategoryLabel.apply {
            setTextColor(Color.rgb(175, 175, 190))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, dp(6), dp(4))
        }
        emojiContainer.addView(emojiCategoryLabel)

        emojiGrid.columnCount = 8
        emojiScrollView.addView(emojiGrid)
        emojiContainer.addView(emojiScrollView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        renderEmojiCategoryTabs()
        populateEmojiGrid()
    }

    private fun renderEmojiCategoryTabs() {
        emojiTabViews.forEachIndexed { idx, tv ->
            val selected = idx == selectedEmojiCategoryIdx
            tv.background = if (selected) {
                pillDrawable(Color.rgb(42, 42, 52), Color.WHITE)
            } else {
                pillDrawable(Color.rgb(16, 16, 20), Color.rgb(40, 40, 50))
            }
        }
    }

    private fun populateEmojiGrid() {
        val cat = emojiCategories.getOrElse(selectedEmojiCategoryIdx) { emojiCategories.first() }
        emojiCategoryLabel.text = "${cat.titleAr} (${cat.emojis.size})"
        emojiGrid.removeAllViews()
        cat.emojis.forEach { emoji ->
            val cell = TextView(context).apply {
                text = emoji
                textSize = 23f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(7), dp(2), dp(7))
                setOnClickListener { actions.onCommitEmoji(emoji) }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            emojiGrid.addView(cell, lp)
        }
    }

    private fun buildEditToolsOverlay() {
        editToolsContainer.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        header.addView(TextView(context).apply {
            text = "أدوات التحرير الدقيقة"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        header.addView(TextView(context).apply {
            text = "رجوع"
            setTextColor(Color.BLACK)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setOnClickListener { switchMode(OverlayMode.KEYBOARD) }
        })
        editToolsContainer.addView(header)

        val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(toolBigBtn("◀ يسار") { actions.onMoveCursor(-1) }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        row1.addView(toolBigBtn("يمين ▶") { actions.onMoveCursor(1) }, LinearLayout.LayoutParams(0, dp(52), 1f))
        editToolsContainer.addView(row1, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val row2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(toolBigBtn("تحديد الكل") { actions.onSelectAll() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row2.addView(toolBigBtn("نسخ") { actions.onCopySelection() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row2.addView(toolBigBtn("قص") { actions.onCutSelection() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row2.addView(toolBigBtn("لصق مباشر") { actions.onPasteSystemClipboard() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        editToolsContainer.addView(row2)
    }

    private fun toolBigBtn(title: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(18, 18, 24), Color.rgb(65, 65, 78))
            setOnClickListener { onClick() }
        }
    }

    private inner class FloatingKeyPopupOverlay(context: Context) : View(context) {
        private var isShowing = false
        private var isLongPress = false
        private val anchorRect = RectF()
        private var singleLabel = ""
        private var singleColor = Color.WHITE
        private var singleConfig = KepordConfig()
        private var alternates: List<String> = emptyList()
        private var selectedIndex = 0

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            color = Color.WHITE
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(85, 255, 255, 255)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val boxRect = RectF()
        private val cellRect = RectF()
        private val overlayLoc = IntArray(2)
        private val kbLoc = IntArray(2)

        init {
            isClickable = false
            isFocusable = false
        }

        fun setSinglePopup(anchorInKb: RectF, label: String, color: Int, cfg: KepordConfig) {
            isShowing = true
            isLongPress = false
            anchorRect.set(anchorInKb)
            singleLabel = label
            singleColor = color
            singleConfig = cfg
            invalidate()
        }

        fun setLongPressPopup(anchorInKb: RectF, alts: List<String>, selIdx: Int) {
            isShowing = true
            isLongPress = true
            anchorRect.set(anchorInKb)
            alternates = alts
            selectedIndex = selIdx
            invalidate()
        }

        fun clear() {
            if (!isShowing) return
            isShowing = false
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isShowing || width <= 0 || height <= 0) return

            getLocationInWindow(overlayLoc)
            keyboardView.getLocationInWindow(kbLoc)
            val dx = (kbLoc[0] - overlayLoc[0]).toFloat()
            val dy = (kbLoc[1] - overlayLoc[1]).toFloat()

            val aLeft = anchorRect.left + dx
            val aRight = anchorRect.right + dx
            val aTop = anchorRect.top + dy
            val aCenterX = (aLeft + aRight) * 0.5f
            val keyW = aRight - aLeft

            if (isLongPress && alternates.isNotEmpty()) {
                val cellW = max(keyW * 1.15f, 38f * density)
                val totalW = cellW * alternates.size
                val popupH = 42f * density
                val left = (aCenterX - totalW * 0.5f)
                    .coerceIn(6f * density, max(6f * density, width - totalW - 6f * density))
                val bottom = max(popupH + 2f * density, aTop - 5f * density)
                val top = max(2f * density, bottom - popupH)

                boxRect.set(left, top, left + totalW, bottom)
                val r = 10f * density
                bgPaint.color = Color.rgb(10, 10, 14)
                bgPaint.setShadowLayer(12f * density, 0f, 3f * density, Color.BLACK)
                canvas.drawRoundRect(boxRect, r, r, bgPaint)
                bgPaint.clearShadowLayer()
                canvas.drawRoundRect(boxRect, r, r, borderPaint)

                textPaint.textSize = 18.5f * spScale
                val fm = textPaint.fontMetrics
                val textOffY = -(fm.ascent + fm.descent) * 0.5f

                for (i in alternates.indices) {
                    val cellL = left + i * cellW
                    cellRect.set(cellL + 3f, top + 3f, cellL + cellW - 3f, bottom - 3f)
                    if (i == selectedIndex) {
                        canvas.drawRoundRect(cellRect, 7f * density, 7f * density, highlightPaint)
                    }
                    textPaint.color = if (i == selectedIndex) Color.WHITE else Color.rgb(205, 205, 218)
                    val txt = if (alternates[i].length == 1 && alternates[i][0] in '\u064B'..'\u0652') {
                        "\u25CC${alternates[i]}"
                    } else {
                        alternates[i]
                    }
                    canvas.drawText(txt, cellRect.centerX(), cellRect.centerY() + textOffY, textPaint)
                }
            } else {
                val popW = max(keyW * 1.30f, 38f * density)
                val popH = 42f * density
                val cx = aCenterX.coerceIn(popW * 0.5f + 4f, width - popW * 0.5f - 4f)
                val bottom = max(popH + 2f * density, aTop - 5f * density)
                val top = max(2f * density, bottom - popH)

                boxRect.set(cx - popW * 0.5f, top, cx + popW * 0.5f, bottom)
                val r = 10f * density
                bgPaint.color = Color.rgb(14, 14, 20)
                bgPaint.setShadowLayer(10f * density, 0f, 3f * density, Color.BLACK)
                canvas.drawRoundRect(boxRect, r, r, bgPaint)
                bgPaint.clearShadowLayer()
                canvas.drawRoundRect(boxRect, r, r, borderPaint)

                textPaint.textSize = 21f * spScale * singleConfig.letterSizeScale
                shadowPaint.textSize = textPaint.textSize
                val fm = textPaint.fontMetrics
                val ty = boxRect.centerY() - (fm.ascent + fm.descent) * 0.5f
                shadowPaint.color = Color.BLACK
                canvas.drawText(singleLabel, boxRect.centerX() + 1.5f * density, ty + 1.5f * density, shadowPaint)

                textPaint.color = singleColor
                if (singleConfig.letterGlow > 0.05f) {
                    textPaint.setShadowLayer(8f * density * singleConfig.letterGlow, 0f, 0f, singleColor)
                } else {
                    textPaint.clearShadowLayer()
                }
                canvas.drawText(singleLabel, boxRect.centerX(), ty, textPaint)
                textPaint.clearShadowLayer()
            }
        }
    }

    private inner class ClipListAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredClips.size
        override fun getItem(position: Int): ClipEntry = filteredClips[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder: ClipRowHolder
            val root: LinearLayout
            if (convertView == null) {
                root = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = pillDrawable(Color.rgb(14, 14, 19), Color.rgb(46, 46, 56))
                    setPadding(dp(10), dp(8), dp(8), dp(8))
                }
                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val previewTv = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 12.5f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val metaTv = TextView(context).apply {
                    setTextColor(Color.rgb(165, 165, 180))
                    textSize = 10f
                    setPadding(0, dp(2), 0, 0)
                }
                textCol.addView(previewTv)
                textCol.addView(metaTv)
                root.addView(textCol, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })

                val pinBtn = TextView(context).apply {
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                }
                val delBtn = TextView(context).apply {
                    text = "🗑"
                    setTextColor(Color.rgb(200, 200, 210))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                }
                root.addView(pinBtn)
                root.addView(delBtn)

                holder = ClipRowHolder(previewTv, metaTv, pinBtn, delBtn)
                root.tag = holder
            } else {
                root = convertView as LinearLayout
                holder = root.tag as ClipRowHolder
            }

            val entry = getItem(position)
            holder.preview.text = entry.preview
            val kb = String.format("%.1f KB", entry.byteSize / 1024.0)
            holder.meta.text = "${entry.lineCount} سطر • ${entry.charCount} حرف • $kb" + if (entry.isPinned) " • مثبت" else ""

            holder.pin.text = if (entry.isPinned) "📌" else "📍"
            holder.pin.setTextColor(if (entry.isPinned) Color.WHITE else Color.rgb(120, 120, 134))

            root.setOnClickListener { actions.onPasteClip(entry) }
            holder.pin.setOnClickListener { actions.onTogglePin(entry) }
            holder.del.setOnClickListener { actions.onDeleteClip(entry) }

            return root
        }
    }

    private class ClipRowHolder(
        val preview: TextView,
        val meta: TextView,
        val pin: TextView,
        val del: TextView
    )

    private fun pillDrawable(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(fill)
            setStroke((1f * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
