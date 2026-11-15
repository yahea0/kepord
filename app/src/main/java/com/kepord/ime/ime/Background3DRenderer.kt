package com.kepord.ime.ime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.kepord.ime.model.Background3DStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Background3DRenderer {

    private var cachedBitmap: Bitmap? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastStyle: Background3DStyle? = null
    private var lastIntensity = -1f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        style: Background3DStyle,
        intensity: Float
    ) {
        if (width <= 0 || height <= 0) return

        val bmp = cachedBitmap
        if (bmp == null ||
            bmp.isRecycled ||
            lastWidth != width ||
            lastHeight != height ||
            lastStyle != style ||
            lastIntensity != intensity
        ) {
            cachedBitmap?.recycle()
            cachedBitmap = renderOffscreen(width, height, style, intensity)
            lastWidth = width
            lastHeight = height
            lastStyle = style
            lastIntensity = intensity
        }

        cachedBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    fun release() {
        cachedBitmap?.recycle()
        cachedBitmap = null
    }

    private fun renderOffscreen(
        width: Int,
        height: Int,
        style: Background3DStyle,
        intensity: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()

        if (style == Background3DStyle.PURE_BLACK) {
            c.drawColor(Color.BLACK)
            return bitmap
        }

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.rgb(18, 18, 22),
            Color.rgb(2, 2, 3),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, bgPaint)
        bgPaint.shader = null

        when (style) {
            Background3DStyle.GRID_3D -> drawPerspectiveGrid(c, w, h, intensity)
            Background3DStyle.ISOMETRIC_CUBES -> drawIsometricCubes(c, w, h, intensity)
            Background3DStyle.WAVES_3D -> drawTopographicWaves(c, w, h, intensity)
            Background3DStyle.PURE_BLACK -> Unit
        }

        return bitmap
    }

    private fun drawPerspectiveGrid(c: Canvas, w: Float, h: Float, intensity: Float) {
        val vpX = w * 0.5f
        val vpY = -h * 0.35f
        val baseAlpha = (85f * intensity).toInt().coerceIn(22, 115)

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.45f,
            Color.argb((42 * intensity).toInt(), 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h * 0.45f, bgPaint)
        bgPaint.shader = null

        val columns = 18
        strokePaint.strokeWidth = 2.0f
        for (i in -columns..columns) {
            val bottomX = vpX + (i.toFloat() / columns) * w * 1.45f
            val distNorm = kotlin.math.abs(i.toFloat() / columns)
            val lineAlpha = (baseAlpha * (1f - distNorm * 0.4f)).toInt().coerceAtLeast(18)
            strokePaint.color = Color.argb(lineAlpha, 255, 255, 255)
            c.drawLine(vpX, vpY, bottomX, h, strokePaint)
        }

        val rows = 14
        for (r in 1..rows) {
            val t = r.toFloat() / rows
            val depthY = h * (t * t)
            val rungAlpha = (baseAlpha * (0.35f + 0.65f * t)).toInt().coerceIn(20, 120)
            strokePaint.strokeWidth = 1.3f + t * 1.6f
            strokePaint.color = Color.argb(rungAlpha, 245, 245, 250)
            c.drawLine(0f, depthY, w, depthY, strokePaint)
        }
    }

    private fun drawIsometricCubes(c: Canvas, w: Float, h: Float, intensity: Float) {
        val rx = w / 9.5f
        val ry = rx * 0.56f
        val cz = rx * 0.68f
        val cols = (w / (rx * 2f)).toInt() + 3
        val rows = (h / (ry * 2f + cz)).toInt() + 3

        val topShade = Color.argb((46 * intensity).toInt(), 255, 255, 255)
        val leftShade = Color.argb((22 * intensity).toInt(), 255, 255, 255)
        val rightShade = Color.argb((120 * intensity).toInt(), 0, 0, 0)
        val edgeColor = Color.argb((78 * intensity).toInt(), 255, 255, 255)

        strokePaint.strokeWidth = 1.6f
        strokePaint.color = edgeColor

        for (row in -1..rows) {
            val offsetX = if (row % 2 == 0) 0f else rx
            val cy = row * (ry + cz)
            for (col in -1..cols) {
                val cx = col * (rx * 2f) + offsetX

                path.reset()
                path.moveTo(cx, cy - ry)
                path.lineTo(cx + rx, cy)
                path.lineTo(cx, cy + ry)
                path.lineTo(cx - rx, cy)
                path.close()
                fillPaint.color = topShade
                c.drawPath(path, fillPaint)
                c.drawPath(path, strokePaint)

                path.reset()
                path.moveTo(cx - rx, cy)
                path.lineTo(cx, cy + ry)
                path.lineTo(cx, cy + ry + cz)
                path.lineTo(cx - rx, cy + cz)
                path.close()
                fillPaint.color = leftShade
                c.drawPath(path, fillPaint)
                c.drawPath(path, strokePaint)

                path.reset()
                path.moveTo(cx, cy + ry)
                path.lineTo(cx + rx, cy)
                path.lineTo(cx + rx, cy + cz)
                path.lineTo(cx, cy + ry + cz)
                path.close()
                fillPaint.color = rightShade
                c.drawPath(path, fillPaint)
                c.drawPath(path, strokePaint)
            }
        }
    }

    private fun drawTopographicWaves(c: Canvas, w: Float, h: Float, intensity: Float) {
        val layers = 11
        val steps = 48
        strokePaint.strokeWidth = 2.0f

        for (layer in 0 until layers) {
            val baseNorm = layer.toFloat() / (layers - 1)
            val baseY = h * (0.12f + baseNorm * 0.85f)
            val amp = h * (0.045f + 0.025f * sin(layer * 0.9f))

            path.reset()
            for (s in 0..steps) {
                val nx = s.toFloat() / steps
                val x = nx * w
                val phase = nx * PI.toFloat() * 3.0f + layer * 0.55f
                val y = baseY + sin(phase) * amp + cos(phase * 0.5f) * (amp * 0.45f)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val ridgePath = Path(path)
            ridgePath.lineTo(w, h)
            ridgePath.lineTo(0f, h)
            ridgePath.close()

            val fillGray = (10 + (layer * 2)).coerceAtMost(32)
            fillPaint.color = Color.argb(180, fillGray, fillGray, fillGray + 3)
            c.drawPath(ridgePath, fillPaint)

            val rimAlpha = ((40 + layer * 6) * intensity).toInt().coerceIn(25, 115)
            strokePaint.color = Color.argb(rimAlpha, 255, 255, 255)
            c.drawPath(path, strokePaint)
        }
    }
}
