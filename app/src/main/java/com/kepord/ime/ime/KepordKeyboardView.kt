package com.kepord.ime.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.kepord.ime.model.KepordConfig
import com.kepord.ime.model.LetterColorMode
import com.kepord.ime.model.WavePattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class KepordKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onCommitText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onMoveCursor(delta: Int)
        fun onLanguageSwitch()
        fun onOpenEmoji()
        fun onOpenClipboard()
    }

    private class PlacedKey(
        val spec: KeySpec,
        val rect: RectF,
        val normX: Float,
        val normY: Float,
        val orderIndex: Int
    )

    private class RippleSlot {
        var active = false
        var cx = 0f
        var cy = 0f
        var startMs = 0L
    }

    var listener: Listener? = null

    private var config = KepordConfig()
    private var currentPage = KeyboardPage.ARABIC
    private var lastAlphaPage = KeyboardPage.ARABIC
    private var isShiftOn = false
    private var isCapsLock = false
    private var lastShiftTapMs = 0L

    private var placedKeys: List<PlacedKey> = emptyList()
    private val bgRenderer = Background3DRenderer()

    private var activeKey: PlacedKey? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastSpaceScrubX = 0f
    private var spaceScrubTriggered = false
    private var spaceScrubAccumPx = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var isLongPressOpen = false
    private var longPressAlternates: List<String> = emptyList()
    private var longPressSelectedIndex = 0
    private var isRepeatingBackspace = false

    private val ripples = Array(6) { RippleSlot() }
    private var nextRippleIdx = 0

    private val density = resources.displayMetrics.density
    private val spScale = resources.displayMetrics.scaledDensity

    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySkirtPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val keyTopBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val letterShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val letterExtrudePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val letterFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val emojiKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.WHITE
    }
    private val popupHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 255, 255, 255)
    }

    private val tempRect = RectF()
    private val popupRect = RectF()
    private val shaderMatrix = Matrix()

    private var cachedShader: Shader? = null
    private var cachedShaderW = 0
    private var cachedShaderH = 0
    private var cachedShaderMode: LetterColorMode? = null
    private var cachedShaderPattern: WavePattern? = null
    private var cachedGradientSig = 0

    private var paletteStops: IntArray = intArrayOf(Color.WHITE, Color.WHITE)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private var isAttachedAndVisible = true

    private val longPressRunnable = Runnable {
        val key = activeKey ?: return@Runnable
        if (key.spec.action == KeyAction.CHAR && key.spec.alternates.size > 1) {
            isLongPressOpen = true
            longPressAlternates = key.spec.alternates
            longPressSelectedIndex = 0
            performHaptic(12)
            invalidate()
        }
    }

    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isRepeatingBackspace) return
            listener?.onBackspace()
            performHaptic(8)
            handler.postDelayed(this, 48L)
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true
    }

    fun applyConfig(newConfig: KepordConfig, forceDefaultLanguage: Boolean = false) {
        val prevShowNum = config.showNumberRow
        val prevScale = config.keyboardHeightScale
        config = newConfig
        if (forceDefaultLanguage) {
            currentPage = if (newConfig.defaultLanguageArabic) KeyboardPage.ARABIC else KeyboardPage.ENGLISH
            lastAlphaPage = currentPage
        }
        rebuildPalette()
        invalidateShaderCache()
        if (prevShowNum != newConfig.showNumberRow || prevScale != newConfig.keyboardHeightScale) {
            requestLayout()
            layoutKeys(width, height)
        }
        invalidate()
    }

    fun setLanguageArabic(arabic: Boolean) {
        currentPage = if (arabic) KeyboardPage.ARABIC else KeyboardPage.ENGLISH
        lastAlphaPage = currentPage
        isShiftOn = false
        isCapsLock = false
        layoutKeys(width, height)
        invalidate()
    }

    fun setActiveWindowVisibility(visible: Boolean) {
        isAttachedAndVisible = visible
        if (visible) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttachedAndVisible = true
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttachedAndVisible = false
        handler.removeCallbacksAndMessages(null)
        bgRenderer.release()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = KeyboardLayouts.getRows(currentPage, config.showNumberRow).size
        val baseRowHeightDp = if (rows >= 5) 48f else 54f
        val desiredH = (rows * baseRowHeightDp * config.keyboardHeightScale * density + 16f * density).toInt()
        setMeasuredDimension(w, desiredH)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateShaderCache()
        layoutKeys(w, h)
    }

    private fun layoutKeys(w: Int, h: Int) {
        if (w <= 0 || h <= 0) {
            placedKeys = emptyList()
            return
        }
        val rows = KeyboardLayouts.getRows(currentPage, config.showNumberRow)
        val outerPadX = 4f * density
        val outerPadTop = 6f * density
        val outerPadBottom = 8f * density
        val gapX = 4.2f * density
        val gapY = 6.2f * density

        val usableW = w - outerPadX * 2f
        val usableH = h - outerPadTop - outerPadBottom
        val rowCount = rows.size.coerceAtLeast(1)
        val rowHeight = (usableH - gapY * (rowCount - 1)) / rowCount

        val list = ArrayList<PlacedKey>(52)
        var order = 0
        for (rIdx in rows.indices) {
            val row = rows[rIdx]
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            val rowUsableW = usableW - gapX * (row.size - 1)
            val top = outerPadTop + rIdx * (rowHeight + gapY)
            val bottom = top + rowHeight
            var curX = outerPadX

            for (spec in row) {
                val keyW = (spec.weight / totalWeight) * rowUsableW
                val rect = RectF(curX, top, curX + keyW, bottom)
                list.add(
                    PlacedKey(
                        spec = spec,
                        rect = rect,
                        normX = rect.centerX() / w.toFloat(),
                        normY = rect.centerY() / h.toFloat(),
                        orderIndex = order++
                    )
                )
                curX += keyW + gapX
            }
        }
        placedKeys = list
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val nowMs = SystemClock.uptimeMillis()

        bgRenderer.draw(canvas, w, h, config.background3DStyle, config.bg3DIntensity)

        val isAnimatedMode = config.letterColorMode != LetterColorMode.STATIC
        val hasActiveRipple = hasActiveRipples(nowMs)
        val phase = ((nowMs % 60000L) / 1000f) * config.waveSpeed * 0.65f

        val activeShader = if (isAnimatedMode && config.wavePattern != WavePattern.KEY_CASCADE && config.wavePattern != WavePattern.BREATHING) {
            ensureShader(w, h)?.also { shader ->
                updateShaderMatrix(shader, w.toFloat(), h.toFloat(), phase)
            }
        } else {
            null
        }

        val radius = config.keyCornerRadiusDp * density
        val bevelDepth = config.keyBevelDepth * density
        val keyAlpha = (config.keycapOpacity * 255f).toInt().coerceIn(175, 255)
        val totalKeys = placedKeys.size.coerceAtLeast(1)

        for (i in placedKeys.indices) {
            val pk = placedKeys[i]
            val isPressed = pk === activeKey
            val pressShiftY = if (isPressed) min(bevelDepth * 0.72f, 2.5f * density) else 0f

            tempRect.set(
                pk.rect.left,
                pk.rect.top + bevelDepth + 1.4f * density,
                pk.rect.right,
                pk.rect.bottom + bevelDepth + 1.4f * density
            )
            keyShadowPaint.color = Color.argb(220, 0, 0, 0)
            canvas.drawRoundRect(tempRect, radius, radius, keyShadowPaint)

            if (bevelDepth > 0.5f && !isPressed) {
                tempRect.set(pk.rect.left, pk.rect.top + bevelDepth, pk.rect.right, pk.rect.bottom + bevelDepth)
                keySkirtPaint.color = Color.argb(keyAlpha, 5, 5, 8)
                canvas.drawRoundRect(tempRect, radius, radius, keySkirtPaint)
            }

            tempRect.set(
                pk.rect.left,
                pk.rect.top + pressShiftY,
                pk.rect.right,
                pk.rect.bottom + pressShiftY
            )
            val baseTone = when {
                isPressed -> 34
                pk.spec.isAccentSpecial -> 22
                else -> 14
            }
            keyBodyPaint.color = Color.argb(keyAlpha, baseTone, baseTone, baseTone + 5)
            canvas.drawRoundRect(tempRect, radius, radius, keyBodyPaint)

            keyTopBevelPaint.strokeWidth = 1.0f * density
            keyTopBevelPaint.color = if (isPressed) {
                Color.argb(145, 255, 255, 255)
            } else {
                Color.argb(if (pk.spec.isAccentSpecial) 68 else 48, 255, 255, 255)
            }
            canvas.drawLine(
                tempRect.left + radius * 0.5f,
                tempRect.top + 1.0f * density,
                tempRect.right - radius * 0.5f,
                tempRect.top + 1.0f * density,
                keyTopBevelPaint
            )

            if (config.showKeyBorders) {
                keyBorderPaint.strokeWidth = (if (isPressed) 1.3f else 0.85f) * density
                keyBorderPaint.color = if (isPressed) {
                    Color.argb(200, 255, 255, 255)
                } else {
                    Color.argb(if (pk.spec.isAccentSpecial) 76 else 52, 220, 220, 232)
                }
                canvas.drawRoundRect(tempRect, radius, radius, keyBorderPaint)
            }

            val hint = pk.spec.hint
            if (config.showSecondaryHints && !hint.isNullOrEmpty()) {
                hintPaint.textSize = 9.5f * spScale * config.letterSizeScale
                hintPaint.color = Color.argb(185, 200, 200, 212)
                canvas.drawText(
                    hint,
                    tempRect.right - 4.5f * density,
                    tempRect.top + 11.5f * density,
                    hintPaint
                )
            }

            if (pk.spec.action == KeyAction.OPEN_EMOJI) {
                emojiKeyPaint.textSize = 18f * spScale
                val fm = emojiKeyPaint.fontMetrics
                val cy = tempRect.centerY() - (fm.ascent + fm.descent) * 0.5f
                canvas.drawText(pk.spec.label, tempRect.centerX(), cy, emojiKeyPaint)
            } else {
                val keyColor = evaluateKeyColor(pk, i, totalKeys, phase, nowMs, isPressed)
                val label = resolveKeyLabel(pk.spec)
                draw3DIlluminatedLetter(
                    canvas = canvas,
                    label = label,
                    keyRect = tempRect,
                    spec = pk.spec,
                    solidOrSampleColor = keyColor,
                    shader = activeShader,
                    isPressed = isPressed,
                    hasRippleBoost = hasActiveRipple && computeRippleBoost(pk.rect.centerX(), pk.rect.centerY(), nowMs) > 0.15f
                )
            }
        }

        val ak = activeKey
        if (ak != null) {
            if (isLongPressOpen && longPressAlternates.isNotEmpty()) {
                drawLongPressPopup(canvas, ak, longPressAlternates, longPressSelectedIndex)
            } else if (config.keyPopupEnabled && ak.spec.action == KeyAction.CHAR) {
                drawSingleKeyPopup(canvas, ak)
            }
        }

        if (isAttachedAndVisible && (isAnimatedMode || hasActiveRipple)) {
            postInvalidateOnAnimation()
        }
    }

    private fun draw3DIlluminatedLetter(
        canvas: Canvas,
        label: String,
        keyRect: RectF,
        spec: KeySpec,
        solidOrSampleColor: Int,
        shader: Shader?,
        isPressed: Boolean,
        hasRippleBoost: Boolean
    ) {
        val baseSp = when {
            spec.action == KeyAction.SPACE -> 11.5f
            label.length >= 4 -> 11.0f
            label.length >= 2 -> 14.2f
            else -> 19.5f
        }
        val textSizePx = baseSp * spScale * config.letterSizeScale
        letterShadowPaint.textSize = textSizePx
        letterExtrudePaint.textSize = textSizePx
        letterFacePaint.textSize = textSizePx

        val fm = letterFacePaint.fontMetrics
        val textCenterOffsetY = -(fm.ascent + fm.descent) * 0.5f
        val cx = keyRect.centerX()
        val cy = keyRect.centerY() + textCenterOffsetY

        val depth = config.letter3DDepth
        val stepPx = 0.72f * density

        if (depth > 0) {
            val shadowOffset = (depth * stepPx) + 1.4f * density
            letterShadowPaint.color = Color.argb(235, 0, 0, 0)
            canvas.drawText(label, cx + shadowOffset, cy + shadowOffset, letterShadowPaint)

            val sideColor = scaleBrightness(solidOrSampleColor, 0.30f)
            letterExtrudePaint.color = sideColor
            for (step in depth downTo 1) {
                val off = step * stepPx
                canvas.drawText(label, cx + off * 0.85f, cy + off, letterExtrudePaint)
            }

            letterExtrudePaint.color = Color.argb(95, 255, 255, 255)
            canvas.drawText(label, cx - 0.55f * density, cy - 0.55f * density, letterExtrudePaint)
        }

        if (shader != null && !hasRippleBoost && !isPressed) {
            letterFacePaint.shader = shader
            letterFacePaint.color = Color.WHITE
        } else {
            letterFacePaint.shader = null
            letterFacePaint.color = if (isPressed || hasRippleBoost) {
                blendToWhite(solidOrSampleColor, if (isPressed) 0.55f else 0.45f)
            } else {
                solidOrSampleColor
            }
        }

        val glowAmt = config.letterGlow
        if (glowAmt > 0.03f || isPressed) {
            val radius = (3f + glowAmt * 11f + (if (isPressed) 4f else 0f)) * density
            val glowColor = if (isPressed) blendToWhite(solidOrSampleColor, 0.4f) else solidOrSampleColor
            letterFacePaint.setShadowLayer(radius, 0f, 0f, glowColor)
        } else {
            letterFacePaint.clearShadowLayer()
        }

        canvas.drawText(label, cx, cy, letterFacePaint)
        letterFacePaint.clearShadowLayer()
        letterFacePaint.shader = null
    }

    private fun drawSingleKeyPopup(canvas: Canvas, pk: PlacedKey) {
        val w = pk.rect.width() * 1.25f
        val h = pk.rect.height() * 1.15f
        val cx = pk.rect.centerX().coerceIn(w * 0.5f + 4f, width - w * 0.5f - 4f)
        val bottom = pk.rect.top - 6f * density
        val top = max(4f, bottom - h)
        popupRect.set(cx - w * 0.5f, top, cx + w * 0.5f, bottom)

        popupPaint.color = Color.rgb(16, 16, 22)
        val r = 10f * density
        canvas.drawRoundRect(popupRect, r, r, popupPaint)
        canvas.drawRoundRect(popupRect, r, r, popupBorderPaint)

        val label = resolveKeyLabel(pk.spec)
        val color = evaluateKeyColor(
            pk, pk.orderIndex, placedKeys.size.coerceAtLeast(1),
            ((SystemClock.uptimeMillis() % 60000L) / 1000f) * config.waveSpeed * 0.65f,
            SystemClock.uptimeMillis(), true
        )
        draw3DIlluminatedLetter(canvas, label, popupRect, pk.spec, color, null, true, false)
    }

    private fun drawLongPressPopup(
        canvas: Canvas,
        pk: PlacedKey,
        alternates: List<String>,
        selectedIdx: Int
    ) {
        val cellW = max(pk.rect.width() * 1.05f, 38f * density)
        val totalW = cellW * alternates.size
        val h = pk.rect.height() * 1.08f
        val center = pk.rect.centerX()
        val left = (center - totalW * 0.5f).coerceIn(6f * density, max(6f * density, width - totalW - 6f * density))
        val bottom = pk.rect.top - 8f * density
        val top = max(4f, bottom - h)
        popupRect.set(left, top, left + totalW, bottom)

        val r = 10f * density
        popupPaint.color = Color.rgb(12, 12, 17)
        canvas.drawRoundRect(popupRect, r, r, popupPaint)
        canvas.drawRoundRect(popupRect, r, r, popupBorderPaint)

        val itemPaint = Paint(letterFacePaint).apply {
            shader = null
            textSize = 18f * spScale
        }
        val fm = itemPaint.fontMetrics
        val textOffsetY = -(fm.ascent + fm.descent) * 0.5f

        for (i in alternates.indices) {
            val cellLeft = left + i * cellW
            val cellRect = RectF(cellLeft + 3f, top + 3f, cellLeft + cellW - 3f, bottom - 3f)
            if (i == selectedIdx) {
                canvas.drawRoundRect(cellRect, 7f * density, 7f * density, popupHighlightPaint)
            }
            itemPaint.color = if (i == selectedIdx) Color.WHITE else Color.rgb(200, 200, 212)
            val txt = if (alternates[i].length == 1 && alternates[i][0] in '\u064B'..'\u0652') {
                "\u25CC${alternates[i]}"
            } else {
                alternates[i]
            }
            canvas.drawText(txt, cellRect.centerX(), cellRect.centerY() + textOffsetY, itemPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId != MotionEvent.INVALID_POINTER_ID && action == MotionEvent.ACTION_POINTER_DOWN) {
                    commitPreviousBeforeRollover()
                }
                val ptrId = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                activePointerId = ptrId
                downX = x
                downY = y
                lastSpaceScrubX = x
                spaceScrubTriggered = false
                spaceScrubAccumPx = 0f
                isLongPressOpen = false

                val hit = findKeyAt(x, y)
                activeKey = hit
                if (hit != null) {
                    performHaptic(config.hapticStrengthMs)
                    playKeyClickSound()
                    spawnRipple(hit.rect.centerX(), hit.rect.centerY())

                    if (hit.spec.action == KeyAction.BACKSPACE) {
                        listener?.onBackspace()
                        isRepeatingBackspace = true
                        handler.postDelayed(backspaceRepeatRunnable, 340L)
                    } else if (hit.spec.action == KeyAction.CHAR && hit.spec.alternates.size > 1) {
                        handler.postDelayed(longPressRunnable, 360L)
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pIdx = event.findPointerIndex(activePointerId)
                if (pIdx < 0) return true
                val x = event.getX(pIdx)
                val y = event.getY(pIdx)

                val ak = activeKey
                if (ak != null && ak.spec.action == KeyAction.SPACE && config.spacebarCursorSwipe) {
                    val dx = x - lastSpaceScrubX
                    lastSpaceScrubX = x
                    if (abs(x - downX) > 16f * density) {
                        spaceScrubTriggered = true
                    }
                    if (spaceScrubTriggered) {
                        spaceScrubAccumPx += dx
                        val stepPx = 15f * density
                        while (spaceScrubAccumPx >= stepPx) {
                            listener?.onMoveCursor(1)
                            spaceScrubAccumPx -= stepPx
                            performHaptic(6)
                        }
                        while (spaceScrubAccumPx <= -stepPx) {
                            listener?.onMoveCursor(-1)
                            spaceScrubAccumPx += stepPx
                            performHaptic(6)
                        }
                    }
                    return true
                }

                if (isLongPressOpen && ak != null && longPressAlternates.isNotEmpty()) {
                    val cellW = max(ak.rect.width() * 1.05f, 38f * density)
                    val totalW = cellW * longPressAlternates.size
                    val left = (ak.rect.centerX() - totalW * 0.5f)
                        .coerceIn(6f * density, max(6f * density, width - totalW - 6f * density))
                    val rel = ((x - left) / cellW).toInt().coerceIn(0, longPressAlternates.lastIndex)
                    if (rel != longPressSelectedIndex) {
                        longPressSelectedIndex = rel
                        performHaptic(6)
                        invalidate()
                    }
                    return true
                }

                val movedKey = findKeyAt(x, y)
                if (movedKey !== ak && movedKey != null && abs(x - downX) > 12f * density) {
                    handler.removeCallbacks(longPressRunnable)
                    isRepeatingBackspace = false
                    handler.removeCallbacks(backspaceRepeatRunnable)
                    activeKey = movedKey
                    if (movedKey.spec.action == KeyAction.CHAR && movedKey.spec.alternates.size > 1) {
                        handler.postDelayed(longPressRunnable, 360L)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val ptrId = event.getPointerId(idx)
                if (ptrId != activePointerId) return true
                handler.removeCallbacks(longPressRunnable)
                isRepeatingBackspace = false
                handler.removeCallbacks(backspaceRepeatRunnable)

                val ak = activeKey
                if (ak != null) {
                    if (isLongPressOpen && longPressAlternates.isNotEmpty()) {
                        val chosen = longPressAlternates[longPressSelectedIndex]
                        listener?.onCommitText(chosen)
                    } else if (!(ak.spec.action == KeyAction.SPACE && spaceScrubTriggered)) {
                        if (ak.spec.action != KeyAction.BACKSPACE) {
                            executeKeyAction(ak.spec)
                        }
                    }
                }
                activeKey = null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isLongPressOpen = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacksAndMessages(null)
                isRepeatingBackspace = false
                activeKey = null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isLongPressOpen = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun commitPreviousBeforeRollover() {
        handler.removeCallbacks(longPressRunnable)
        isRepeatingBackspace = false
        handler.removeCallbacks(backspaceRepeatRunnable)
        val prev = activeKey ?: return
        if (!isLongPressOpen && prev.spec.action != KeyAction.BACKSPACE) {
            executeKeyAction(prev.spec)
        }
        activeKey = null
        isLongPressOpen = false
    }

    private fun executeKeyAction(spec: KeySpec) {
        when (spec.action) {
            KeyAction.CHAR -> {
                val txt = if (currentPage == KeyboardPage.ENGLISH && (isShiftOn || isCapsLock)) {
                    spec.shiftCommit
                } else {
                    spec.commitText
                }
                listener?.onCommitText(txt)
                if (currentPage == KeyboardPage.ENGLISH && isShiftOn && !isCapsLock) {
                    isShiftOn = false
                }
            }
            KeyAction.SPACE -> listener?.onCommitText(" ")
            KeyAction.ENTER -> listener?.onEnter()
            KeyAction.BACKSPACE -> listener?.onBackspace()
            KeyAction.LANG_SWITCH -> listener?.onLanguageSwitch()
            KeyAction.OPEN_EMOJI -> listener?.onOpenEmoji()
            KeyAction.OPEN_CLIPBOARD -> listener?.onOpenClipboard()
            KeyAction.TO_SYMBOLS_1 -> {
                if (currentPage == KeyboardPage.ARABIC || currentPage == KeyboardPage.ARABIC_SHIFT || currentPage == KeyboardPage.ENGLISH) {
                    lastAlphaPage = if (currentPage == KeyboardPage.ARABIC_SHIFT) KeyboardPage.ARABIC else currentPage
                }
                currentPage = KeyboardPage.SYMBOLS_1
                layoutKeys(width, height)
            }
            KeyAction.TO_SYMBOLS_2 -> {
                currentPage = KeyboardPage.SYMBOLS_2
                layoutKeys(width, height)
            }
            KeyAction.TO_ALPHA -> {
                currentPage = lastAlphaPage
                layoutKeys(width, height)
            }
            KeyAction.SHIFT -> {
                when (currentPage) {
                    KeyboardPage.ARABIC -> {
                        currentPage = KeyboardPage.ARABIC_SHIFT
                        layoutKeys(width, height)
                    }
                    KeyboardPage.ARABIC_SHIFT -> {
                        currentPage = KeyboardPage.ARABIC
                        layoutKeys(width, height)
                    }
                    KeyboardPage.ENGLISH -> {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastShiftTapMs < 320L && isShiftOn) {
                            isCapsLock = !isCapsLock
                            isShiftOn = isCapsLock
                        } else {
                            isCapsLock = false
                            isShiftOn = !isShiftOn
                        }
                        lastShiftTapMs = now
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun resolveKeyLabel(spec: KeySpec): String {
        if (spec.action == KeyAction.SHIFT && currentPage == KeyboardPage.ENGLISH) {
            return when {
                isCapsLock -> "⇪"
                isShiftOn -> "⬆"
                else -> "⇧"
            }
        }
        return if (currentPage == KeyboardPage.ENGLISH && (isShiftOn || isCapsLock)) {
            spec.shiftLabel
        } else {
            spec.label
        }
    }

    private fun findKeyAt(x: Float, y: Float): PlacedKey? {
        for (pk in placedKeys) {
            if (pk.rect.contains(x, y)) return pk
        }
        var best: PlacedKey? = null
        var bestDist = Float.MAX_VALUE
        for (pk in placedKeys) {
            val dx = if (x < pk.rect.left) pk.rect.left - x else if (x > pk.rect.right) x - pk.rect.right else 0f
            val dy = if (y < pk.rect.top) pk.rect.top - y else if (y > pk.rect.bottom) y - pk.rect.bottom else 0f
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = pk
            }
        }
        return if (bestDist <= 18f * density * 18f * density) best else null
    }

    private fun spawnRipple(cx: Float, cy: Float) {
        if (!config.reactiveTouchRipple) return
        val slot = ripples[nextRippleIdx]
        slot.active = true
        slot.cx = cx
        slot.cy = cy
        slot.startMs = SystemClock.uptimeMillis()
        nextRippleIdx = (nextRippleIdx + 1) % ripples.size
    }

    private fun hasActiveRipples(nowMs: Long): Boolean {
        for (r in ripples) {
            if (r.active) {
                if (nowMs - r.startMs < 520L) return true
                r.active = false
            }
        }
        return false
    }

    private fun computeRippleBoost(x: Float, y: Float, nowMs: Long): Float {
        var maxBoost = 0f
        val maxRadius = width * 0.48f
        for (r in ripples) {
            if (!r.active) continue
            val elapsed = nowMs - r.startMs
            if (elapsed > 520L) {
                r.active = false
                continue
            }
            val progress = elapsed / 520f
            val waveR = progress * maxRadius
            val dist = hypot(x - r.cx, y - r.cy)
            val band = 46f * density
            val diff = abs(dist - waveR)
            if (diff < band) {
                val b = (1f - diff / band) * (1f - progress)
                if (b > maxBoost) maxBoost = b
            }
        }
        return maxBoost
    }

    private fun rebuildPalette() {
        paletteStops = when (config.letterColorMode) {
            LetterColorMode.STATIC -> intArrayOf(config.staticLetterColor, config.staticLetterColor)
            LetterColorMode.RAINBOW_WAVE -> intArrayOf(
                0xFFFF1744.toInt(),
                0xFFFFEA00.toInt(),
                0xFF00E676.toInt(),
                0xFF00E5FF.toInt(),
                0xFF651FFF.toInt(),
                0xFFFF00AA.toInt(),
                0xFFFF1744.toInt()
            )
            LetterColorMode.CUSTOM_GRADIENT_WAVE -> intArrayOf(
                config.gradientColor1,
                config.gradientColor2,
                config.gradientColor3,
                config.gradientColor1
            )
            LetterColorMode.MONOCHROME_WAVE -> intArrayOf(
                0xFF70707C.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFD4D4E0.toInt(),
                0xFF888896.toInt(),
                0xFFFFFFFF.toInt(),
                0xFF70707C.toInt()
            )
        }
    }

    private fun invalidateShaderCache() {
        cachedShader = null
    }

    private fun ensureShader(w: Int, h: Int): Shader? {
        val sig = config.gradientColor1 xor (config.gradientColor2 * 31) xor (config.gradientColor3 * 131)
        if (cachedShader != null &&
            cachedShaderW == w &&
            cachedShaderH == h &&
            cachedShaderMode == config.letterColorMode &&
            cachedShaderPattern == config.wavePattern &&
            cachedGradientSig == sig
        ) {
            return cachedShader
        }

        val fw = w.toFloat()
        val fh = h.toFloat()
        val shader = when (config.wavePattern) {
            WavePattern.HORIZONTAL -> LinearGradient(
                0f, 0f, fw, 0f,
                paletteStops, null, Shader.TileMode.REPEAT
            )
            WavePattern.VERTICAL -> LinearGradient(
                0f, 0f, 0f, fh,
                paletteStops, null, Shader.TileMode.REPEAT
            )
            WavePattern.DIAGONAL -> LinearGradient(
                0f, 0f, fw, fh,
                paletteStops, null, Shader.TileMode.REPEAT
            )
            WavePattern.RADIAL -> RadialGradient(
                fw * 0.5f, fh * 0.5f, max(fw, fh) * 0.55f,
                paletteStops, null, Shader.TileMode.REPEAT
            )
            WavePattern.BREATHING, WavePattern.KEY_CASCADE -> null
        }

        cachedShader = shader
        cachedShaderW = w
        cachedShaderH = h
        cachedShaderMode = config.letterColorMode
        cachedShaderPattern = config.wavePattern
        cachedGradientSig = sig
        return shader
    }

    private fun updateShaderMatrix(shader: Shader, w: Float, h: Float, phase: Float) {
        shaderMatrix.reset()
        val p = phase % 1f
        when (config.wavePattern) {
            WavePattern.HORIZONTAL -> shaderMatrix.setTranslate(p * w, 0f)
            WavePattern.VERTICAL -> shaderMatrix.setTranslate(0f, p * h)
            WavePattern.DIAGONAL -> shaderMatrix.setTranslate(p * w, p * h)
            WavePattern.RADIAL -> {
                val scale = 0.7f + 0.6f * (0.5f + 0.5f * sin(phase * 2f * PI.toFloat()))
                shaderMatrix.setScale(scale, scale, w * 0.5f, h * 0.5f)
            }
            else -> Unit
        }
        shader.setLocalMatrix(shaderMatrix)
    }

    private fun evaluateKeyColor(
        pk: PlacedKey,
        index: Int,
        totalKeys: Int,
        phase: Float,
        nowMs: Long,
        isPressed: Boolean
    ): Int {
        var base = if (config.letterColorMode == LetterColorMode.STATIC) {
            config.staticLetterColor
        } else {
            val t = when (config.wavePattern) {
                WavePattern.HORIZONTAL -> (pk.normX - phase)
                WavePattern.VERTICAL -> (pk.normY - phase)
                WavePattern.DIAGONAL -> ((pk.normX + pk.normY) * 0.5f - phase)
                WavePattern.RADIAL -> {
                    val d = hypot(pk.normX - 0.5f, pk.normY - 0.5f)
                    (d * 1.4f - phase)
                }
                WavePattern.BREATHING -> phase * 0.55f
                WavePattern.KEY_CASCADE -> ((index.toFloat() / totalKeys.toFloat()) - phase)
            }
            samplePalette(t)
        }

        if (config.reactiveTouchRipple) {
            val boost = computeRippleBoost(pk.rect.centerX(), pk.rect.centerY(), nowMs)
            if (boost > 0.02f) {
                base = blendToWhite(base, boost * 0.75f)
            }
        }
        if (isPressed) {
            base = blendToWhite(base, 0.5f)
        }
        return base
    }

    private fun samplePalette(tInput: Float): Int {
        val stops = paletteStops
        if (stops.size <= 1) return stops.firstOrNull() ?: Color.WHITE
        val t = ((tInput % 1f) + 1f) % 1f
        val scaled = t * (stops.size - 1)
        val idx = scaled.toInt().coerceIn(0, stops.size - 2)
        val frac = scaled - idx
        val c1 = stops[idx]
        val c2 = stops[idx + 1]
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * frac).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * frac).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * frac).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun scaleBrightness(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun blendToWhite(color: Int, amt: Float): Int {
        val f = amt.coerceIn(0f, 1f)
        val r = (Color.red(color) + (255 - Color.red(color)) * f).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * f).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun performHaptic(ms: Int) {
        if (ms <= 0) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            vib.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Throwable) {
        }
    }

    private fun playKeyClickSound() {
        if (!config.keySoundEnabled) return
        try {
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, config.keySoundVolume)
        } catch (_: Throwable) {
        }
    }
}
