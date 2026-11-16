package com.artishorizon.tidefit.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Draws the widget background at its real size: the palette's hero gradient with a soft sun and two tide waves, clipped to
 * rounded corners. Done in code (not a stretched drawable) so the corners and waves stay crisp at any widget size.
 */
object WidgetArt {
    fun render(context: Context, widthDp: Int, heightDp: Int, hero: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (widthDp.coerceIn(60, 600) * d).toInt()
        val h = (heightDp.coerceIn(40, 400) * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val radius = 20f * d
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW) })

        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), hero, shade(hero, 0.78f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Sun and bubble, top right.
        paint.color = 0x22FFFFFF
        c.drawCircle(w - 38f * d, -4f * d, 58f * d, paint)
        paint.color = 0x1AFFFFFF
        c.drawCircle(w - 68f * d, h * 0.34f, 14f * d, paint)

        // Two waves along the bottom.
        wave(c, paint, w, h, h * 0.66f, 0.10f * h, 0x26FFFFFF)
        wave(c, paint, w, h, h * 0.80f, 0.09f * h, 0x1F000000)
        return bmp
    }

    private fun wave(c: Canvas, paint: Paint, w: Int, h: Int, y: Float, amp: Float, color: Int) {
        val p = Path().apply {
            moveTo(0f, y)
            cubicTo(w * 0.17f, y - amp * 1.8f, w * 0.33f, y + amp * 1.2f, w * 0.5f, y)
            cubicTo(w * 0.67f, y - amp * 1.2f, w * 0.85f, y - amp * 1.8f, w.toFloat(), y - amp * 0.4f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        paint.color = color
        c.drawPath(p, paint)
    }

    private fun shade(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt()
        val g = ((color shr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
