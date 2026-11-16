package com.artishorizon.tidefit.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.artishorizon.tidefit.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws the dark "glass" widgets: a charcoal card with a coloured glow rising from the bottom edge, a big number, and one
 * progress shape. Everything is painted at the widget's real pixel size so edges stay sharp at any size.
 */
class GlassArt(context: Context, widthDp: Int, heightDp: Int) {
    private val d = context.resources.displayMetrics.density
    private val w = (widthDp.coerceIn(60, 600) * d).toInt()
    private val h = (heightDp.coerceIn(60, 400) * d).toInt()
    private val base = ResourcesCompat.getFont(context, R.font.plusjakarta) ?: Typeface.DEFAULT
    private val regular = Typeface.create(base, 500, false)
    private val bold = Typeface.create(base, 700, false)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun dp(v: Float) = v * d

    private enum class Glow(val top: Int, val bottom: Int) {
        ORANGE(0xFFFF5A1F.toInt(), 0xFFFFB020.toInt()),
        BLUE(0xFF1D4ED8.toInt(), 0xFF22B8FF.toInt()),
        RED(0xFFB3122B.toInt(), 0xFFFF3B52.toInt())
    }

    private fun card(glow: Glow): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = dp(28f)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF202228.toInt(), 0xFF0C0D10.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(rect, paint)
        paint.shader = LinearGradient(
            0f, h * 0.30f, 0f, h.toFloat(),
            intArrayOf(glow.top and 0xFFFFFF, (0x99 shl 24) or (glow.top and 0xFFFFFF), glow.bottom),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * 0.30f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        c.restore()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = 0x24FFFFFF
        c.drawRoundRect(RectF(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f)), r, r, paint)
        paint.style = Paint.Style.FILL
        return bmp to c
    }

    private fun text(c: Canvas, s: String, x: Float, baseline: Float, sizeSp: Float, color: Int, strong: Boolean = false) {
        paint.style = Paint.Style.FILL
        paint.typeface = if (strong) bold else regular
        paint.textSize = dp(sizeSp); paint.color = color; paint.textAlign = Paint.Align.LEFT
        c.drawText(s, x, baseline, paint)
    }

    private fun textWidth(s: String, sizeSp: Float, strong: Boolean): Float {
        paint.typeface = if (strong) bold else regular; paint.textSize = dp(sizeSp)
        return paint.measureText(s)
    }

    /** Big number with a small unit on the same baseline. */
    private fun bigValue(c: Canvas, value: String, unit: String, x: Float, baseline: Float, bigSp: Float) {
        text(c, value, x, baseline, bigSp, 0xFFFFFFFF.toInt(), strong = true)
        if (unit.isNotEmpty()) text(c, unit, x + textWidth(value, bigSp, true) + dp(5f), baseline, 14f, 0xFFD0D3DA.toInt())
    }

    // ------------------------------------------------------------------ calories (wide)

    fun calories(snap: WidgetSnapshot): Bitmap {
        val (bmp, c) = card(Glow.ORANGE)
        val pad = dp(20f)
        val muted = 0xFF9096A0.toInt()
        text(c, "Eaten today", pad, dp(34f), 13f, muted)
        bigValue(c, "%,d".format(snap.eatenKcal), "kcal", pad, dp(34f) + dp(36f), 30f)
        val left = snap.leftKcal
        text(c, if (left != null && left < 0) "Calories over" else "Calories left", pad, h - dp(64f), 13f, 0xCCFFE0C4.toInt())
        bigValue(c, left?.let { "%,d".format(abs(it)) } ?: "-", "kcal", pad, h - dp(24f), 30f)

        val frame = RectF(w * 0.55f, pad, w - pad, h - pad)
        val target = snap.targetKcal
        val progress = if (target != null && target > 0) (snap.eatenKcal.toFloat() / target).coerceIn(0f, 1f) else 0f
        loop(c, frame, progress)
        return bmp
    }

    /** A rounded-square track with a bright arc that starts on the bottom edge and runs anticlockwise, ending in a dot. */
    private fun loop(c: Canvas, frame: RectF, progress: Float) {
        val inset = dp(8f)
        val f = RectF(frame.left + inset, frame.top + inset, frame.right - inset, frame.bottom - inset)
        val path = Path().apply { addRoundRect(f, dp(22f), dp(22f), Path.Direction.CW) }
        val pm = PathMeasure(path, true)
        val len = pm.length
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = dp(7f); paint.shader = null
        paint.color = 0x33FFFFFF
        c.drawPath(path, paint)
        if (progress <= 0f) return

        // The arc begins on the bottom edge, a fifth of the way in, and runs right then up.
        val sx = f.left + f.width() * 0.2f
        var start = 0f
        var best = Float.MAX_VALUE
        val pos = FloatArray(2)
        for (i in 0 until 600) {
            val dist = len * i / 600f
            pm.getPosTan(dist, pos, null)
            val e = hypot(pos[0] - sx, pos[1] - f.bottom)
            if (e < best) { best = e; start = dist }
        }
        // Direction: CW path runs left-to-right along the bottom edge on screen only if walking backwards, so pick whichever heads right.
        pm.getPosTan((start + 4f) % len, pos, null)
        val goesRight = pos[0] > sx
        val arc = Path()
        if (goesRight) {
            val end = start + len * progress
            if (end <= len) pm.getSegment(start, end, arc, true)
            else { pm.getSegment(start, len, arc, true); pm.getSegment(0f, end - len, arc, true) }
            pm.getPosTan(if (end <= len) end else end - len, pos, null)
        } else {
            val end = start - len * progress
            if (end >= 0f) pm.getSegment(end, start, arc, true)
            else { pm.getSegment(0f, start, arc, true); pm.getSegment(len + end, len, arc, true) }
            pm.getPosTan(if (end >= 0f) end else len + end, pos, null)
        }
        paint.shader = LinearGradient(f.left, f.bottom, f.right, f.top, intArrayOf(0xFFFF3D00.toInt(), 0xFFFF7A00.toInt(), 0xFFFFC21A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPath(arc, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL; paint.color = 0xFFFFC21A.toInt()
        c.drawCircle(pos[0], pos[1], dp(7f), paint)
    }

    // ------------------------------------------------------------------ water

    fun water(snap: WidgetSnapshot): Bitmap {
        val (bmp, c) = card(Glow.BLUE)
        val pad = dp(18f)
        val gauge = RectF(w - pad - dp(26f), pad + dp(6f), w - pad, h - pad - dp(6f))
        val textW = gauge.left - pad - dp(8f)
        wrapped(c, "Water consumed today", pad, dp(30f), textW, 13f, 0xFF9096A0.toInt())
        text(c, "Liters", pad, h - dp(64f), 13f, 0xFFB5CDFF.toInt())
        text(c, "%.1f".format(java.util.Locale.US, snap.waterMl / 1000f), pad, h - dp(22f), 40f, 0xFFFFFFFF.toInt(), strong = true)
        val progress = snap.waterTargetMl?.takeIf { it > 0 }?.let { (snap.waterMl.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
        verticalGauge(c, gauge, progress)
        return bmp
    }

    private fun wrapped(c: Canvas, s: String, x: Float, firstBaseline: Float, maxWidth: Float, sizeSp: Float, color: Int) {
        var line = ""
        var y = firstBaseline
        for (word in s.split(" ")) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (textWidth(next, sizeSp, false) > maxWidth && line.isNotEmpty()) {
                text(c, line, x, y, sizeSp, color); y += dp(sizeSp + 5f); line = word
            } else line = next
        }
        if (line.isNotEmpty()) text(c, line, x, y, sizeSp, color)
    }

    private fun verticalGauge(c: Canvas, r: RectF, progress: Float) {
        val radius = r.width() / 2f
        c.save()
        c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
        paint.style = Paint.Style.FILL; paint.color = 0x2EFFFFFF; c.drawRect(r, paint)
        val top = r.bottom - r.height() * progress.coerceAtLeast(0.05f)
        val fill = Path().apply {
            moveTo(r.left, r.bottom)
            lineTo(r.left, top)
            val steps = 12
            for (i in 0..steps) {
                val x = r.left + r.width() * i / steps
                lineTo(x, top + dp(2.5f) * sin(i / steps.toFloat() * 2 * PI).toFloat())
            }
            lineTo(r.right, r.bottom); close()
        }
        paint.shader = LinearGradient(0f, top, 0f, r.bottom, 0xFFFFFFFF.toInt(), 0xFFBFD4FF.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(fill, paint)
        paint.shader = null
        c.restore()
    }

    // ------------------------------------------------------------------ workout

    fun workout(snap: WidgetSnapshot): Bitmap {
        val (bmp, c) = card(Glow.RED)
        val pad = dp(18f)
        text(c, "Daily workout", pad, dp(30f), 13f, 0xFF9096A0.toInt())
        text(c, snap.workoutName ?: "No workout yet", pad, h - dp(88f), 13f, 0xCCFFC2CB.toInt())
        val m = snap.workoutMinutes
        val value = if (m >= 60) "${m / 60}h ${m % 60}min" else "${m}min"
        text(c, value, pad, h - dp(56f), 26f, 0xFFFFFFFF.toInt(), strong = true)

        val bar = RectF(pad, h - pad - dp(24f), w - pad, h - pad)
        val radius = bar.height() / 2f
        paint.style = Paint.Style.FILL; paint.color = 0x4D000000
        c.drawRoundRect(bar, radius, radius, paint)
        val frac = (m / 60f).coerceIn(0f, 1f)
        if (frac > 0f) {
            val inset = dp(3f)
            val fill = RectF(bar.left + inset, bar.top + inset, bar.left + inset + (bar.width() - 2 * inset) * frac.coerceAtLeast(0.12f), bar.bottom - inset)
            paint.shader = LinearGradient(fill.left, 0f, fill.right, 0f, 0xFFFFD6DB.toInt(), 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(fill, fill.height() / 2f, fill.height() / 2f, paint)
            paint.shader = null
        }
        return bmp
    }
}
