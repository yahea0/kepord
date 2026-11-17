package com.kepord.ime.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kepord.ime.KepordApplication
import kotlin.math.abs
import kotlin.math.hypot

class KepordAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density by lazy { resources.displayMetrics.density }

    private var screenW = 1080
    private var screenH = 2400

    private var cursorX = 540f
    private var cursorY = 1000f
    private var cursorFlashUntilMs = 0L

    private var cursorView: CursorPointerView? = null
    private var cursorLp: WindowManager.LayoutParams? = null

    private var touchpadRoot: LinearLayout? = null
    private var touchpadLp: WindowManager.LayoutParams? = null

    private var dragModeEnabled = false
    private var dragModeBtn: TextView? = null
    private var isOverlayShowing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        cursorX = screenW * 0.5f
        cursorY = screenH * 0.42f

        TouchpadController.serviceBridge = object : TouchpadController.ServiceBridge {
            override fun showFloatingTouchpad() {
                mainHandler.post { showOverlays() }
            }

            override fun hideFloatingTouchpad() {
                mainHandler.post { hideOverlays() }
            }

            override fun isShowing(): Boolean = isOverlayShowing
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        TouchpadController.serviceBridge = null
        hideOverlays()
        super.onDestroy()
    }

    private fun showOverlays() {
        if (isOverlayShowing) return
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        cursorX = cursorX.coerceIn(20f, (screenW - 20f).coerceAtLeast(20f))
        cursorY = cursorY.coerceIn(40f, (screenH - 40f).coerceAtLeast(40f))

        // 1. Full-screen floating mouse pointer layer (non-touchable)
        val cView = CursorPointerView(this)
        val cLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // 2. Movable Laptop Touchpad Window
        val tpW = dp(248)
        val tpH = dp(188)
        val tpView = buildFloatingTouchpadView()
        val tpLp = WindowManager.LayoutParams(
            tpW,
            tpH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW - tpW) / 2
            y = (screenH - tpH - dp(86)).coerceAtLeast(dp(40))
        }

        try {
            windowManager.addView(cView, cLp)
            windowManager.addView(tpView, tpLp)
            cursorView = cView
            cursorLp = cLp
            touchpadRoot = tpView
            touchpadLp = tpLp
            isOverlayShowing = true
        } catch (_: Throwable) {
            hideOverlays()
        }
    }

    private fun hideOverlays() {
        isOverlayShowing = false
        dragModeEnabled = false
        mainHandler.removeCallbacksAndMessages(null)
        cursorView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        touchpadRoot?.let { runCatching { windowManager.removeViewImmediate(it) } }
        cursorView = null
        cursorLp = null
        touchpadRoot = null
        touchpadLp = null
    }

    private fun buildFloatingTouchpadView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(Color.argb(242, 7, 7, 10))
                setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.WHITE)
            }
            setPadding(dp(7), dp(6), dp(7), dp(7))
        }

        // Top bar with Movable Handle + Drag Toggle + Return to Normal Keyboard
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
        }

        // Small Move Handle Button beside the panel to move it freely anywhere on screen
        val moveBtn = TextView(this).apply {
            text = "✥ تحريك"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(22, 22, 28), Color.rgb(120, 120, 138))
            setPadding(dp(9), dp(4), dp(9), dp(4))
            var startRawX = 0f
            var startRawY = 0f
            var startWinX = 0
            var startWinY = 0
            setOnTouchListener { _, ev ->
                val lp = touchpadLp ?: return@setOnTouchListener false
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = ev.rawX
                        startRawY = ev.rawY
                        startWinX = lp.x
                        startWinY = lp.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = (startWinX + (ev.rawX - startRawX).toInt()).coerceIn(0, (screenW - lp.width).coerceAtLeast(0))
                        lp.y = (startWinY + (ev.rawY - startRawY).toInt()).coerceIn(dp(24), (screenH - lp.height - dp(12)).coerceAtLeast(dp(24)))
                        runCatching { windowManager.updateViewLayout(touchpadRoot, lp) }
                        true
                    }
                    else -> false
                }
            }
        }
        header.addView(moveBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
            marginEnd = dp(4)
        })

        val dragBtn = TextView(this).apply {
            text = "سحب"
            setTextColor(Color.rgb(210, 210, 222))
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.rgb(16, 16, 20), Color.rgb(62, 62, 74))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                dragModeEnabled = !dragModeEnabled
                updateDragButtonUi()
            }
        }
        dragModeBtn = dragBtn
        header.addView(dragBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
            marginEnd = dp(4)
        })

        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        // Return to Normal Keyboard button right beside it
        val returnKbBtn = TextView(this).apply {
            text = "⌨ كيبورد"
            setTextColor(Color.BLACK)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillDrawable(Color.WHITE, Color.WHITE)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            setOnClickListener {
                TouchpadController.closeTouchpadAndReturnToKeyboard()
            }
        }
        header.addView(returnKbBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)))

        root.addView(header)

        // Trackpad Surface + Right-Edge Vertical Scroll Bar
        val trackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val padView = TrackpadSurfaceView(this)
        trackRow.addView(padView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(5)
        })

        val scrollStrip = ScrollStripView(this)
        trackRow.addView(scrollStrip, LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.MATCH_PARENT))

        root.addView(trackRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun updateDragButtonUi() {
        val btn = dragModeBtn ?: return
        if (dragModeEnabled) {
            btn.setTextColor(Color.BLACK)
            btn.background = pillDrawable(Color.WHITE, Color.WHITE)
        } else {
            btn.setTextColor(Color.rgb(210, 210, 222))
            btn.background = pillDrawable(Color.rgb(16, 16, 20), Color.rgb(62, 62, 74))
        }
    }

    private inner class TrackpadSurfaceView(context: Context) : View(context) {

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 255, 255, 255)
            strokeWidth = 1f * density
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(70, 70, 84)
            strokeWidth = 1.2f * density
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 10.5f * resources.displayMetrics.scaledDensity
        }

        private var lastX = 0f
        private var lastY = 0f
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var movedTooMuch = false

        private var pendingTapTime = 0L
        private var pendingTapRunnable: Runnable? = null

        private var dragStartCursorX = 0f
        private var dragStartCursorY = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawColor(Color.rgb(12, 12, 16))
            val cols = 6
            val rows = 4
            for (c in 1 until cols) {
                val x = (c.toFloat() / cols) * w
                canvas.drawLine(x, 0f, x, h, gridPaint)
            }
            for (r in 1 until rows) {
                val y = (r.toFloat() / rows) * h
                canvas.drawLine(0f, y, w, y, gridPaint)
            }
            canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, 10f * density, 10f * density, borderPaint)
            canvas.drawText(
                if (dragModeEnabled) "اسحب هنا لتمرير/تظليل الشاشة" else "نقرة = ضغط • نقرتان = تحديد نص / نقر مزدوج",
                w * 0.5f,
                h - 9f * density,
                hintPaint
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val sens = (applicationContext as? KepordApplication)?.preferences?.current()?.touchpadSensitivity ?: 1.65f
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    downX = event.x
                    downY = event.y
                    downTime = SystemClock.uptimeMillis()
                    movedTooMuch = false
                    dragStartCursorX = cursorX
                    dragStartCursorY = cursorY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y

                    if (hypot(event.x - downX, event.y - downY) > 7f * density) {
                        movedTooMuch = true
                    }

                    cursorX = (cursorX + dx * sens).coerceIn(6f, (screenW - 6f).coerceAtLeast(6f))
                    cursorY = (cursorY + dy * sens).coerceIn(12f, (screenH - 12f).coerceAtLeast(12f))
                    cursorView?.invalidate()
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val elapsed = SystemClock.uptimeMillis() - downTime
                    if (dragModeEnabled && movedTooMuch) {
                        dispatchDragGesture(dragStartCursorX, dragStartCursorY, cursorX, cursorY)
                        dragModeEnabled = false
                        updateDragButtonUi()
                        invalidate()
                        return true
                    }

                    if (!movedTooMuch && elapsed < 240L) {
                        val now = SystemClock.uptimeMillis()
                        val existingPending = pendingTapRunnable
                        if (existingPending != null && (now - pendingTapTime) < 250L) {
                            // Genuine Double Tap detected!
                            mainHandler.removeCallbacks(existingPending)
                            pendingTapRunnable = null
                            handleSmartDoubleTap(cursorX, cursorY)
                        } else {
                            // Wait briefly to distinguish single tap vs double tap
                            pendingTapTime = now
                            val tx = cursorX
                            val ty = cursorY
                            val run = Runnable {
                                pendingTapRunnable = null
                                dispatchSingleTap(tx, ty)
                            }
                            pendingTapRunnable = run
                            mainHandler.postDelayed(run, 235L)
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private inner class ScrollStripView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(175, 175, 190)
            textAlign = Paint.Align.CENTER
            textSize = 12f * resources.displayMetrics.scaledDensity
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(65, 65, 78)
            strokeWidth = 1f * density
        }
        private var lastY = 0f
        private var lastDispatchMs = 0L

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(15, 15, 20))
            canvas.drawRoundRect(1f, 1f, width - 1f, height - 1f, 8f * density, 8f * density, borderPaint)
            canvas.drawText("↕", width * 0.5f, height * 0.5f + 4f * density, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastY
                    val now = SystemClock.uptimeMillis()
                    if (abs(dy) > 18f * density && (now - lastDispatchMs) > 190L) {
                        lastY = event.y
                        lastDispatchMs = now
                        val swipeDelta = (dy * 3.2f).coerceIn(-360f, 360f)
                        val sx = cursorX.coerceIn(60f, (screenW - 60f).coerceAtLeast(60f))
                        val sy = cursorY.coerceIn(220f, (screenH - 220f).coerceAtLeast(220f))
                        dispatchDragGesture(sx, sy, sx, (sy + swipeDelta).coerceIn(100f, screenH - 100f), durationMs = 150L)
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private inner class CursorPointerView(context: Context) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            color = Color.argb(190, 255, 255, 255)
        }
        private val arrowPath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val x = cursorX
            val y = cursorY
            val s = 17f * density

            arrowPath.reset()
            arrowPath.moveTo(x, y)
            arrowPath.lineTo(x, y + s * 1.15f)
            arrowPath.lineTo(x + s * 0.34f, y + s * 0.85f)
            arrowPath.lineTo(x + s * 0.56f, y + s * 1.28f)
            arrowPath.lineTo(x + s * 0.76f, y + s * 1.18f)
            arrowPath.lineTo(x + s * 0.53f, y + s * 0.76f)
            arrowPath.lineTo(x + s * 0.92f, y + s * 0.72f)
            arrowPath.close()

            if (SystemClock.uptimeMillis() < cursorFlashUntilMs) {
                canvas.drawCircle(x, y, 13f * density, ringPaint)
                postInvalidateDelayed(30L)
            }

            canvas.drawPath(arrowPath, fillPaint)
            canvas.drawPath(arrowPath, strokePaint)
        }
    }

    private fun handleSmartDoubleTap(x: Float, y: Float) {
        flashCursor(260L)
        if (hasSelectableTextAt(x, y)) {
            // Select text at cursor using native press-and-hold gesture
            dispatchLongPressForSelection(x, y)
        } else {
            // No text underneath -> execute two normal quick taps!
            dispatchNormalDoubleTap(x, y)
        }
    }

    private fun hasSelectableTextAt(x: Float, y: Float): Boolean {
        val ix = x.toInt()
        val iy = y.toInt()
        val node = findDeepestNodeAt(ix, iy) ?: return false
        return try {
            val isEditWithText = node.isEditable && !node.text.isNullOrEmpty()
            val cls = node.className?.toString().orEmpty()
            val isExplicitlySelectable = node.isTextSelectable ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_SELECTION }
            val isReadableTextBlock = !node.isEditable &&
                !node.text.isNullOrBlank() &&
                (!node.isClickable || isExplicitlySelectable) &&
                !cls.contains("Button", ignoreCase = true)
            isEditWithText || isExplicitlySelectable || isReadableTextBlock
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun findDeepestNodeAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val winList = windows
        for (win in winList) {
            if (win.type == AccessibilityNodeInfo.ACTION_FOCUS) continue
            val root = win.root ?: continue
            // Skip Kepord's own accessibility overlay window
            if (root.packageName?.toString() == packageName && isInsideTouchpadRect(x, y)) {
                @Suppress("DEPRECATION")
                root.recycle()
                continue
            }
            val found = searchDeepest(root, x, y)
            if (found != null) {
                if (found !== root) {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
                return found
            }
            @Suppress("DEPRECATION")
            root.recycle()
        }
        val fallbackRoot = rootInActiveWindow ?: return null
        val found = searchDeepest(fallbackRoot, x, y)
        if (found !== fallbackRoot) {
            @Suppress("DEPRECATION")
            fallbackRoot.recycle()
        }
        return found
    }

    private fun searchDeepest(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null

        var bestChild: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = searchDeepest(child, x, y)
            if (match != null) {
                val r = Rect()
                match.getBoundsInScreen(r)
                val area = r.width() * r.height()
                if (area in 1 until bestArea) {
                    @Suppress("DEPRECATION")
                    bestChild?.recycle()
                    bestChild = match
                    bestArea = area
                } else if (match !== child) {
                    @Suppress("DEPRECATION")
                    match.recycle()
                }
            }
            if (child !== bestChild) {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return bestChild ?: node
    }

    private fun isInsideTouchpadRect(x: Int, y: Int): Boolean {
        val lp = touchpadLp ?: return false
        return x in lp.x..(lp.x + lp.width) && y in lp.y..(lp.y + lp.height)
    }

    private fun dispatchSingleTap(x: Float, y: Float) {
        flashCursor(180L)
        withPassthroughIfNeeded(x, y, restoreDelayMs = 150L) {
            val p = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(p, 0L, 42L)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
    }

    private fun dispatchNormalDoubleTap(x: Float, y: Float) {
        withPassthroughIfNeeded(x, y, restoreDelayMs = 260L) {
            val p1 = Path().apply { moveTo(x, y) }
            val p2 = Path().apply { moveTo(x, y) }
            val s1 = GestureDescription.StrokeDescription(p1, 0L, 42L)
            val s2 = GestureDescription.StrokeDescription(p2, 105L, 42L)
            val gesture = GestureDescription.Builder().addStroke(s1).addStroke(s2).build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun dispatchLongPressForSelection(x: Float, y: Float) {
        withPassthroughIfNeeded(x, y, restoreDelayMs = 680L) {
            val p = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(p, 0L, 580L)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
    }

    private fun dispatchDragGesture(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 220L
    ) {
        withPassthroughIfNeeded(x1, y1, restoreDelayMs = durationMs + 120L) {
            val p = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(p, 0L, durationMs)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
    }

    private fun withPassthroughIfNeeded(x: Float, y: Float, restoreDelayMs: Long, action: () -> Unit) {
        val tp = touchpadRoot
        val lp = touchpadLp
        if (tp != null && lp != null && isInsideTouchpadRect(x.toInt(), y.toInt())) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(tp, lp) }
            mainHandler.postDelayed({
                action()
                mainHandler.postDelayed({
                    val curLp = touchpadLp
                    val curTp = touchpadRoot
                    if (curLp != null && curTp != null) {
                        curLp.flags = curLp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        runCatching { windowManager.updateViewLayout(curTp, curLp) }
                    }
                }, restoreDelayMs)
            }, 25L)
        } else {
            action()
        }
    }

    private fun flashCursor(durationMs: Long) {
        cursorFlashUntilMs = SystemClock.uptimeMillis() + durationMs
        cursorView?.invalidate()
    }

    private fun pillDrawable(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(fill)
            setStroke((1f * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    private fun dp(v: Int): Int = (v * density).toInt()
}
