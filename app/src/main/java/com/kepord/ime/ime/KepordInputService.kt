package com.kepord.ime.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.kepord.ime.KepordApplication
import com.kepord.ime.clipboard.ClipEntry
import com.kepord.ime.clipboard.ClipboardStore
import com.kepord.ime.data.KepordPreferences
import com.kepord.ime.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KepordInputService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var preferences: KepordPreferences
    private lateinit var clipboardStore: ClipboardStore
    private var clipboardManager: ClipboardManager? = null

    private var imePanel: KepordImePanel? = null
    private var lastSpaceMs = 0L
    private var isArabicCurrent = true

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        clipboardStore.captureFromSystemClipboard(clipboardManager)
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as KepordApplication
        preferences = app.preferences
        clipboardStore = app.clipboardStore
        isArabicCurrent = preferences.current().defaultLanguageArabic

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
        clipboardStore.captureFromSystemClipboard(clipboardManager)
    }

    override fun onCreateInputView(): View {
        val panel = KepordImePanel(this, object : KepordImePanel.PanelActions {
            override fun onPasteClip(entry: ClipEntry) {
                serviceScope.launch {
                    val fullText = clipboardStore.readFullText(entry)
                    commitLargeText(fullText)
                }
            }

            override fun onTogglePin(entry: ClipEntry) {
                clipboardStore.togglePinAsync(entry.id)
            }

            override fun onDeleteClip(entry: ClipEntry) {
                clipboardStore.deleteEntryAsync(entry)
            }

            override fun onClearUnpinned() {
                clipboardStore.clearUnpinnedAsync()
            }

            override fun onToggleLanguage() {
                toggleLanguage()
            }

            override fun onOpenSettings() {
                val intent = Intent(this@KepordInputService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            override fun onCommitEmoji(emoji: String) {
                currentInputConnection?.commitText(emoji, 1)
                preferences.recordRecentEmoji(emoji)
                imePanel?.updateRecentEmojis(preferences.getRecentEmojis())
            }

            override fun onEmojiBackspace() {
                handleBackspace()
            }

            override fun onMoveCursor(delta: Int) {
                moveCursorBy(delta)
            }

            override fun onSelectAll() {
                currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            }

            override fun onCopySelection() {
                currentInputConnection?.performContextMenuAction(android.R.id.copy)
            }

            override fun onCutSelection() {
                currentInputConnection?.performContextMenuAction(android.R.id.cut)
            }

            override fun onPasteSystemClipboard() {
                val cm = clipboardManager ?: return
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this@KepordInputService)?.toString()
                    ?: return
                commitLargeText(text)
            }
        })

        panel.keyboardView.listener = object : KepordKeyboardView.Listener {
            override fun onCommitText(text: String) {
                handleCommitText(text)
            }

            override fun onBackspace() {
                handleBackspace()
            }

            override fun onEnter() {
                handleEnter()
            }

            override fun onMoveCursor(delta: Int) {
                moveCursorBy(delta)
            }

            override fun onLanguageSwitch() {
                toggleLanguage()
            }

            override fun onOpenEmoji() {
                imePanel?.switchMode(KepordImePanel.OverlayMode.EMOJI)
            }

            override fun onOpenClipboard() {
                imePanel?.switchMode(KepordImePanel.OverlayMode.CLIPBOARD)
            }
        }

        imePanel = panel

        serviceScope.launch {
            preferences.config.collectLatest { cfg ->
                imePanel?.applyConfig(cfg, forceDefaultLang = false)
            }
        }
        serviceScope.launch {
            clipboardStore.entries.collectLatest { list ->
                imePanel?.updateClips(list)
            }
        }

        val cfg = preferences.current()
        panel.applyConfig(cfg, forceDefaultLang = true)
        panel.updateLanguageLabel(isArabicCurrent)
        panel.updateClips(clipboardStore.entries.value)
        panel.updateRecentEmojis(preferences.getRecentEmojis())

        return panel
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyDarkImeNavigationBar()
        clipboardStore.captureFromSystemClipboard(clipboardManager)
        imePanel?.updateRecentEmojis(preferences.getRecentEmojis())
        imePanel?.switchMode(KepordImePanel.OverlayMode.KEYBOARD)
        imePanel?.keyboardView?.setActiveWindowVisibility(true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        imePanel?.keyboardView?.setActiveWindowVisibility(false)
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun toggleLanguage() {
        isArabicCurrent = !isArabicCurrent
        imePanel?.keyboardView?.setLanguageArabic(isArabicCurrent)
        imePanel?.updateLanguageLabel(isArabicCurrent)
    }

    private fun handleCommitText(text: String) {
        val ic = currentInputConnection ?: return
        val cfg = preferences.current()

        if (text == " " && cfg.doubleSpacePeriod) {
            val now = System.currentTimeMillis()
            if (now - lastSpaceMs < 380L) {
                val before = ic.getTextBeforeCursor(2, 0)?.toString()
                if (before != null && before.endsWith(" ") && before.length >= 2 && !before[before.length - 2].isWhitespace()) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpaceMs = 0L
                    return
                }
            }
            lastSpaceMs = now
        } else {
            lastSpaceMs = 0L
        }

        if (!isArabicCurrent && cfg.autoCapsEnglish && text.length == 1 && text[0] in 'a'..'z') {
            val before = ic.getTextBeforeCursor(3, 0)?.toString()
            if (before.isNullOrEmpty() || before.endsWith(". ") || before.endsWith("! ") || before.endsWith("? ") || before.endsWith("\n")) {
                ic.commitText(text.uppercase(), 1)
                return
            }
        }

        if (isArabicCurrent && cfg.arabicFontStyle != ArabicFontStyle.NORMAL) {
            val before = ic.getTextBeforeCursor(4, 0)?.toString().orEmpty()
            val styled = ArabicFontEngine.transformOnCommit(text, before, cfg.arabicFontStyle)
            ic.commitText(styled, 1)
            return
        }

        ic.commitText(text, 1)
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }

        val cfg = preferences.current()
        if (isArabicCurrent && cfg.arabicFontStyle != ArabicFontStyle.NORMAL) {
            val before = ic.getTextBeforeCursor(5, 0)?.toString().orEmpty()
            val delCount = ArabicFontEngine.computeBackspaceDeleteCount(before, cfg.arabicFontStyle)
            if (delCount > 1) {
                ic.deleteSurroundingText(delCount, 0)
                return
            }
        }

        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val actionId = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val isMultiLine = (editorInfo?.inputType ?: 0) and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE != 0

        if (!isMultiLine && actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun moveCursorBy(delta: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val steps = kotlin.math.abs(delta)
        repeat(steps) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun commitLargeText(text: String) {
        val ic = currentInputConnection ?: return
        if (text.length <= 8000) {
            ic.commitText(text, 1)
            return
        }
        serviceScope.launch {
            ic.beginBatchEdit()
            try {
                var idx = 0
                val total = text.length
                val chunkLen = 8000
                while (idx < total) {
                    var end = (idx + chunkLen).coerceAtMost(total)
                    if (end < total && Character.isHighSurrogate(text[end - 1])) {
                        end--
                    }
                    ic.commitText(text.substring(idx, end), 1)
                    idx = end
                    if (idx < total) {
                        ic.endBatchEdit()
                        delay(10L)
                        ic.beginBatchEdit()
                    }
                }
            } finally {
                ic.endBatchEdit()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyDarkImeNavigationBar() {
        try {
            val w = window?.window ?: return
            w.navigationBarColor = Color.BLACK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                w.isNavigationBarContrastEnforced = false
            }
        } catch (_: Throwable) {
        }
    }
}
