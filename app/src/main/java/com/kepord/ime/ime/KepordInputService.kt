package com.kepord.ime.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.kepord.ime.KepordApplication
import com.kepord.ime.accessibility.TouchpadController
import com.kepord.ime.ai.AiChatMessage
import com.kepord.ime.ai.KepordAiEngine
import com.kepord.ime.ai.KepordAiPrefs
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
    private lateinit var aiPrefs: KepordAiPrefs
    private lateinit var aiEngine: KepordAiEngine
    private lateinit var soundEngine: MechanicalSoundEngine
    private var clipboardManager: ClipboardManager? = null

    private var imePanel: KepordImePanel? = null
    private var lastSpaceMs = 0L
    private var isArabicCurrent = true

    private val aiHistory = mutableListOf<AiChatMessage>()
    private var isAiBusy = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        clipboardStore.captureFromSystemClipboard(clipboardManager)
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as KepordApplication
        preferences = app.preferences
        clipboardStore = app.clipboardStore
        aiPrefs = app.aiPrefs
        aiEngine = app.aiEngine
        soundEngine = app.soundEngine
        isArabicCurrent = preferences.current().defaultLanguageArabic

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
        clipboardStore.captureFromSystemClipboard(clipboardManager)

        TouchpadController.imeBridge = object : TouchpadController.ImeBridge {
            override fun onReturnToNormalKeyboard() {
                serviceScope.launch {
                    requestShowSelf(0)
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        val panel = KepordImePanel(this, object : KepordImePanel.PanelActions {
            override fun onPasteClip(entry: ClipEntry) {
                serviceScope.launch {
                    val fullText = clipboardStore.readFullText(entry)
                    if (imePanel?.isAiPromptComposing() == true) {
                        imePanel?.appendAiDraftText(fullText)
                    } else {
                        commitLargeText(fullText)
                    }
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
                if (imePanel?.isAiPromptComposing() == true) {
                    imePanel?.appendAiDraftText(emoji)
                } else {
                    currentInputConnection?.commitText(emoji, 1)
                }
                preferences.recordRecentEmoji(emoji)
                imePanel?.updateRecentEmojis(preferences.getRecentEmojis())
            }

            override fun onEmojiBackspace() {
                if (imePanel?.isAiPromptComposing() == true) {
                    imePanel?.backspaceAiDraft()
                } else {
                    handleBackspace()
                }
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
                if (imePanel?.isAiPromptComposing() == true) {
                    imePanel?.appendAiDraftText(text)
                } else {
                    commitLargeText(text)
                }
            }

            override fun onActivateMouseTouchpad() {
                if (TouchpadController.requestOpenTouchpad(this@KepordInputService)) {
                    requestHideSelf(0)
                } else {
                    imePanel?.switchMode(KepordImePanel.OverlayMode.TOUCHPAD_PROMPT)
                }
            }

            override fun onOpenAccessibilitySettings() {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            override fun onSendAiPrompt(prompt: String) {
                sendAiPromptInternal(prompt)
            }

            override fun onFetchAiModels() {
                fetchAiModelsInternal()
            }

            override fun onSelectAiModel(modelId: String) {
                aiPrefs.update { it.copy(selectedModel = modelId) }
            }

            override fun onToggleAiWebSearch() {
                aiPrefs.update { it.copy(webSearchEnabled = !it.webSearchEnabled) }
            }

            override fun onInsertAiReply(text: String) {
                commitLargeText(text)
            }

            override fun onCopyAiReply(text: String) {
                val cm = clipboardManager ?: return
                cm.setPrimaryClip(ClipData.newPlainText("kepord_ai", text))
                clipboardStore.saveTextAsync(text)
                Toast.makeText(this@KepordInputService, "تم نسخ رد الذكاء الاصطناعي", Toast.LENGTH_SHORT).show()
            }

            override fun onClearAiChat() {
                aiHistory.clear()
                imePanel?.updateAiChatMessages(emptyList(), isLoading = false, statusNote = "")
            }

            override fun onAiComposerModeChanged(composing: Boolean) = Unit
        })

        panel.keyboardView.soundEngine = soundEngine
        panel.keyboardView.listener = object : KepordKeyboardView.Listener {
            override fun onCommitText(text: String) {
                if (imePanel?.isAiPromptComposing() == true) {
                    imePanel?.appendAiDraftText(text)
                } else {
                    handleCommitText(text)
                }
            }

            override fun onBackspace() {
                if (imePanel?.isAiPromptComposing() == true) {
                    imePanel?.backspaceAiDraft()
                } else {
                    handleBackspace()
                }
            }

            override fun onEnter() {
                if (imePanel?.isAiPromptComposing() == true) {
                    imePanel?.submitCurrentAiDraft()
                } else {
                    handleEnter()
                }
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
        serviceScope.launch {
            aiPrefs.state.collectLatest { aiSt ->
                imePanel?.updateAiState(aiSt)
            }
        }

        val cfg = preferences.current()
        panel.applyConfig(cfg, forceDefaultLang = true)
        panel.updateLanguageLabel(isArabicCurrent)
        panel.updateClips(clipboardStore.entries.value)
        panel.updateRecentEmojis(preferences.getRecentEmojis())
        panel.updateAiState(aiPrefs.current())
        panel.updateAiChatMessages(aiHistory.toList(), isLoading = false)

        return panel
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyDarkImeNavigationBar()
        clipboardStore.captureFromSystemClipboard(clipboardManager)
        imePanel?.updateRecentEmojis(preferences.getRecentEmojis())
        imePanel?.exitAiComposing()
        imePanel?.switchMode(KepordImePanel.OverlayMode.KEYBOARD)
        imePanel?.keyboardView?.setActiveWindowVisibility(true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        imePanel?.keyboardView?.setActiveWindowVisibility(false)
    }

    override fun onDestroy() {
        TouchpadController.imeBridge = null
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun sendAiPromptInternal(prompt: String) {
        if (isAiBusy || prompt.isBlank()) return
        isAiBusy = true
        val priorHistory = aiHistory.toList()
        aiHistory.add(AiChatMessage(isUser = true, text = prompt))
        imePanel?.updateAiChatMessages(aiHistory.toList(), isLoading = true)

        serviceScope.launch {
            val res = aiEngine.sendChat(priorHistory, prompt)
            val metaParts = mutableListOf<String>()
            if (res.usedKeyLabel.isNotBlank()) metaParts.add(res.usedKeyLabel)
            if (res.switchedKeysCount > 0) metaParts.add("تبديل تلقائي ×${res.switchedKeysCount}")
            if (res.usedWebSearch) metaParts.add("بحث ويب ✓")
            if (res.sourcesText.isNotBlank()) metaParts.add(res.sourcesText)

            aiHistory.add(
                AiChatMessage(
                    isUser = false,
                    text = res.text,
                    meta = metaParts.joinToString(" • ")
                )
            )
            isAiBusy = false
            imePanel?.updateAiChatMessages(aiHistory.toList(), isLoading = false)
        }
    }

    private fun fetchAiModelsInternal() {
        if (isAiBusy) return
        isAiBusy = true
        imePanel?.updateAiChatMessages(aiHistory.toList(), isLoading = true, statusNote = "جاري جلب النماذج المتاحة...")
        serviceScope.launch {
            val res = aiEngine.fetchAvailableModels()
            isAiBusy = false
            imePanel?.updateAiChatMessages(
                aiHistory.toList(),
                isLoading = false,
                statusNote = res.message
            )
        }
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
            val before = ic.getTextBeforeCursor(6, 0)?.toString().orEmpty()
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
            val before = ic.getTextBeforeCursor(6, 0)?.toString().orEmpty()
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
