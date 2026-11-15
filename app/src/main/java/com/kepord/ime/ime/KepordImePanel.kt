package com.kepord.ime.ime

import android.content.Context
import android.graphics.Color
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
import com.kepord.ime.clipboard.ClipEntry
import com.kepord.ime.model.KepordConfig

class KepordImePanel(
    context: Context,
    private val actions: PanelActions
) : LinearLayout(context) {

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
    }

    enum class OverlayMode {
        KEYBOARD,
        CLIPBOARD,
        EMOJI,
        EDIT_TOOLS
    }

    private val density = resources.displayMetrics.density
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

    val keyboardView = KepordKeyboardView(context)
    private val contentContainer = FrameLayout(context)
    private val clipboardContainer = LinearLayout(context)
    private val emojiContainer = LinearLayout(context)
    private val editToolsContainer = LinearLayout(context)
    private val clipCountBadge = TextView(context)
    private val langBadge = TextView(context)
    private val fontBadge = TextView(context)
    private val clipEmptyView = TextView(context)
    private val clipListView = ListView(context)
    private val clipAdapter = ClipListAdapter()

    private val filterBtns = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.BLACK)

        addView(buildTopToolbar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(42)))

        val divider = View(context).apply {
            setBackgroundColor(Color.rgb(34, 34, 42))
        }
        addView(divider, LayoutParams(LayoutParams.MATCH_PARENT, 1))

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
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(230))
        )

        addView(contentContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun applyConfig(config: KepordConfig, forceDefaultLang: Boolean) {
        keyboardView.applyConfig(config, forceDefaultLang)
        updateLanguageLabel(config.defaultLanguageArabic)
        fontBadge.text = if (config.arabicFontStyle == ArabicFontStyle.NORMAL) {
            "الخط"
        } else {
            config.arabicFontStyle.name.take(4)
        }
        fontBadge.setTextColor(
            if (config.arabicFontStyle == ArabicFontStyle.NORMAL) Color.rgb(210, 210, 220) else Color.WHITE
        )
    }

    fun updateLanguageLabel(isArabic: Boolean) {
        langBadge.text = if (isArabic) "AR ⇄ EN" else "EN ⇄ AR"
    }

    fun updateClips(clips: List<ClipEntry>) {
        allClips = clips
        clipCountBadge.text = "الحافظة (${clips.size})"
        applyClipFilter()
    }

    fun updateRecentEmojis(recent: List<String>) {
        recentEmojis = recent
        emojiCategories = EmojiCatalog.getCategories(recentEmojis)
        if (mode == OverlayMode.EMOJI && selectedEmojiCategoryIdx == 0) {
            populateEmojiGrid()
        }
    }

    fun switchMode(newMode: OverlayMode) {
        mode = if (mode == newMode && newMode != OverlayMode.KEYBOARD) OverlayMode.KEYBOARD else newMode
        keyboardView.visibility = if (mode == OverlayMode.KEYBOARD) View.VISIBLE else View.GONE
        clipboardContainer.visibility = if (mode == OverlayMode.CLIPBOARD) View.VISIBLE else View.GONE
        emojiContainer.visibility = if (mode == OverlayMode.EMOJI) View.VISIBLE else View.GONE
        editToolsContainer.visibility = if (mode == OverlayMode.EDIT_TOOLS) View.VISIBLE else View.GONE
        keyboardView.setActiveWindowVisibility(mode == OverlayMode.KEYBOARD)

        if (mode == OverlayMode.EMOJI) {
            emojiCategories = EmojiCatalog.getCategories(recentEmojis)
            renderEmojiCategoryTabs()
            populateEmojiGrid()
        }
    }

    private fun buildTopToolbar(): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(6, 6, 8))
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        clipCountBadge.apply {
            text = "الحافظة (0)"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(20, 20, 26), Color.rgb(62, 62, 74))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { switchMode(OverlayMode.CLIPBOARD) }
        }
        bar.addView(clipCountBadge, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.25f).apply {
            marginEnd = dp(4)
        })

        langBadge.apply {
            text = "AR ⇄ EN"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(20, 20, 26), Color.rgb(62, 62, 74))
            setOnClickListener {
                actions.onToggleLanguage()
                switchMode(OverlayMode.KEYBOARD)
            }
        }
        bar.addView(langBadge, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f).apply {
            marginEnd = dp(4)
        })

        bar.addView(toolbarBtn("😀 إيموجي") { switchMode(OverlayMode.EMOJI) },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 0.95f).apply { marginEnd = dp(4) })

        bar.addView(toolbarBtn("تحرير") { switchMode(OverlayMode.EDIT_TOOLS) },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 0.72f).apply { marginEnd = dp(4) })

        fontBadge.apply {
            text = "الخط"
            setTextColor(Color.rgb(210, 210, 220))
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(18, 18, 22), Color.rgb(48, 48, 58))
            setOnClickListener { actions.onOpenSettings() }
        }
        bar.addView(fontBadge, LayoutParams(0, LayoutParams.MATCH_PARENT, 0.70f).apply { marginEnd = dp(4) })

        bar.addView(toolbarBtn("⚙") { actions.onOpenSettings() },
            LayoutParams(dp(36), LayoutParams.MATCH_PARENT))

        return bar
    }

    private fun toolbarBtn(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.rgb(235, 235, 245))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(18, 18, 22), Color.rgb(48, 48, 58))
            setOnClickListener { onClick() }
        }
    }

    private fun buildClipboardOverlay() {
        clipboardContainer.apply {
            orientation = VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
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
            header.addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(5)
            })
        }

        val spacer = View(context)
        header.addView(spacer, LayoutParams(0, 1, 1f))

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
        header.addView(closeBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(5)
        })

        clipboardContainer.addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        clipEmptyView.apply {
            text = "الحافظة فارغة حالياً.\nأي نص أو كود تنسخه (حتى آلاف الأسطر) سيُحفظ هنا ويبقى بعد إعادة تشغيل الهاتف."
            setTextColor(Color.rgb(165, 165, 178))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
            visibility = View.GONE
        }
        clipboardContainer.addView(clipEmptyView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        clipListView.apply {
            divider = null
            dividerHeight = dp(6)
            adapter = clipAdapter
        }
        clipboardContainer.addView(clipListView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

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
            orientation = VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val topStrip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
        }

        val catScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val catRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
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
            catRow.addView(tab, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            })
        }
        catScroll.addView(catRow)
        topStrip.addView(catScroll, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
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
        topStrip.addView(delBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
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
        emojiContainer.addView(emojiScrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

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
            orientation = VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 7))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        header.addView(TextView(context).apply {
            text = "أدوات التحرير الدقيقة"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

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

        val row1 = LinearLayout(context).apply { orientation = HORIZONTAL }
        row1.addView(toolBigBtn("◀ يسار") { actions.onMoveCursor(-1) }, LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        row1.addView(toolBigBtn("يمين ▶") { actions.onMoveCursor(1) }, LayoutParams(0, dp(52), 1f))
        editToolsContainer.addView(row1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val row2 = LinearLayout(context).apply { orientation = HORIZONTAL }
        row2.addView(toolBigBtn("تحديد الكل") { actions.onSelectAll() }, LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row2.addView(toolBigBtn("نسخ") { actions.onCopySelection() }, LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row2.addView(toolBigBtn("قص") { actions.onCutSelection() }, LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row2.addView(toolBigBtn("لصق مباشر") { actions.onPasteSystemClipboard() }, LayoutParams(0, dp(48), 1f))
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

    private inner class ClipListAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredClips.size
        override fun getItem(position: Int): ClipEntry = filteredClips[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder: ClipRowHolder
            val root: LinearLayout
            if (convertView == null) {
                root = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = pillDrawable(Color.rgb(14, 14, 19), Color.rgb(46, 46, 56))
                    setPadding(dp(10), dp(8), dp(8), dp(8))
                }
                val textCol = LinearLayout(context).apply {
                    orientation = VERTICAL
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
                root.addView(textCol, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })

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
