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
        RED(0xFFB3122B.toInt(), 0xFFFF3B52.toInt()),
        PURPLE(0xFF5B21B6.toInt(), 0xFFE879F9.toInt())
    }

    private fun card(glow: Glow) = card(glow.top, glow.bottom)

    /** Just the card and its glow, for widgets that lay real views on top. */
    fun background(top: Int, bottom: Int): Bitmap = card(top, bottom).first

    private fun card(glowTop: Int, glowBottom: Int): Pair<Bitmap, Canvas> {
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
            intArrayOf(glowTop and 0xFFFFFF, (0x99 shl 24) or (glowTop and 0xFFFFFF), glowBottom),
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
        val left = snap.leftKcal
        val target = snap.targetKcal
        val progress = if (target != null && target > 0) snap.eatenKcal.toFloat() / target else 0f

        // Ring on the right, as large as the card allows.
        val dia = minOf(h - 2 * pad, w * 0.40f)
        val ring = RectF(w - pad - dia, (h - dia) / 2f, w - pad, (h + dia) / 2f)
        donut(c, ring, progress)
        val pct = "${(progress * 100).toInt()}%"
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = bold; paint.textSize = dp(if (dia > dp(110f)) 22f else 17f); paint.color = 0xFFFFFFFF.toInt(); paint.style = Paint.Style.FILL
        c.drawText(pct, ring.centerX(), ring.centerY() + dp(7f), paint)
        paint.textAlign = Paint.Align.LEFT
        text(c, "eaten", ring.centerX() - textWidth("eaten", 11f, false) / 2f, ring.centerY() + dp(24f), 11f, 0xFFD0D3DA.toInt())

        // Left: headline number, then macro bars.
        val over = left != null && left < 0
        text(c, if (over) "Calories over" else "Calories left", pad, dp(32f), 13f, 0xFFC9CDD6.toInt())
        bigValue(c, left?.let { "%,d".format(abs(it)) } ?: "-", "kcal", pad, dp(32f) + dp(40f), 34f)
        if (target != null) text(c, "%,d of %,d kcal".format(snap.eatenKcal, target), pad, dp(32f) + dp(62f), 12f, 0xFFD0D3DA.toInt())

        val barsW = ring.left - pad - dp(16f)
        val rowH = dp(30f)
        val top = h - pad - rowH * 3 + dp(4f)
        macro(c, "Protein", snap.proteinG, snap.proteinTargetG, pad, top, barsW)
        macro(c, "Carbs", snap.carbsG, snap.carbsTargetG, pad, top + rowH, barsW)
        macro(c, "Fat", snap.fatG, snap.fatTargetG, pad, top + rowH * 2, barsW)
        return bmp
    }

    private fun macro(c: Canvas, name: String, eaten: Int, target: Int?, x: Float, y: Float, width: Float) {
        text(c, name, x, y + dp(8f), 11f, 0xFFD0D3DA.toInt())
        val value = if (target != null) "$eaten/${target}g" else "${eaten}g"
        paint.typeface = regular; paint.textSize = dp(11f)
        text(c, value, x + width - paint.measureText(value), y + dp(8f), 11f, 0xFFFFFFFF.toInt())
        val bar = RectF(x, y + dp(14f), x + width, y + dp(19f))
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = 0x38000000
        c.drawRoundRect(bar, dp(3f), dp(3f), paint)
        val f = if (target != null && target > 0) (eaten.toFloat() / target).coerceIn(0f, 1f) else 0f
        if (f > 0f) {
            paint.color = 0xFFFFFFFF.toInt()
            c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * f.coerceAtLeast(0.05f), bar.bottom), dp(3f), dp(3f), paint)
        }
    }

    /** A thick ring with a gradient arc from the top, clockwise, ending in a bright dot. */
    private fun donut(c: Canvas, r: RectF, progress: Float) {
        val stroke = dp(10f)
        val oval = RectF(r.left + stroke, r.top + stroke, r.right - stroke, r.bottom - stroke)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = stroke; paint.shader = null
        paint.color = 0x33FFFFFF
        c.drawArc(oval, 0f, 360f, false, paint)
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return
        c.save()
        c.rotate(-90f, oval.centerX(), oval.centerY())
        paint.shader = android.graphics.SweepGradient(oval.centerX(), oval.centerY(), intArrayOf(0xFFFF3D00.toInt(), 0xFFFF8A00.toInt(), 0xFFFFE066.toInt()), floatArrayOf(0f, 0.5f * p.coerceAtLeast(0.2f), p.coerceAtLeast(0.25f)))
        c.drawArc(oval, 0f, 360f * p, false, paint)
        paint.shader = null
        c.restore()
        val ang = Math.toRadians((360.0 * p) - 90.0)
        val rad = oval.width() / 2f
        paint.style = Paint.Style.FILL; paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(oval.centerX() + (rad * Math.cos(ang)).toFloat(), oval.centerY() + (rad * Math.sin(ang)).toFloat(), dp(4.5f), paint)
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

    // ------------------------------------------------------------------ streak

    fun streak(snap: WidgetSnapshot): Bitmap {
        val (bmp, c) = card(Glow.PURPLE)
        val pad = dp(18f)
        text(c, "Daily streak", pad, dp(30f), 13f, 0xFF9096A0.toInt())
        val note = if (snap.streakToday) "Done for today" else if (snap.streak > 0) "Log today to keep it" else "Log a meal to start"
        text(c, note, pad, h - dp(92f), 12f, 0xCCF5D0FE.toInt())
        bigValue(c, snap.streak.toString(), if (snap.streak == 1) "day" else "days", pad, h - dp(56f), 34f)
        val n = snap.week.size.coerceAtLeast(1)
        val gap = dp(8f)
        val dot = ((w - 2 * pad - gap * (n - 1)) / n).coerceAtMost(dp(20f))
        val cy = h - pad - dot / 2f
        snap.week.forEachIndexed { i, on ->
            paint.style = Paint.Style.FILL
            paint.color = if (on) 0xFFFFFFFF.toInt() else 0x40FFFFFF
            c.drawCircle(pad + dot / 2f + i * (dot + gap), cy, dot / 2f, paint)
        }
        return bmp
    }
}
