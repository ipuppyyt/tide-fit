package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Icons from the Lucide set (ISC licence, https://lucide.dev), bundled as SVG files in assets/icons.
 * Only `<path>` and `<circle>` elements are used by the icons we ship, so a tiny parser is enough
 * and no icon library is needed. Usage: `Icon(lucide("dumbbell"), null)`.
 */
private val cache = HashMap<String, ImageVector>()
private val pathRe = Regex("""<path\s+d="([^"]+)"""")
private val circleRe = Regex("""<circle\s+cx="([^"]+)"\s+cy="([^"]+)"\s+r="([^"]+)"""")

@Composable
fun lucide(name: String): ImageVector {
    val context = LocalContext.current
    return remember(name) {
        cache.getOrPut(name) {
            val svg = context.assets.open("icons/$name.svg").bufferedReader().use { it.readText() }
            val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            fun stroke(d: String) = builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
            pathRe.findAll(svg).forEach { stroke(it.groupValues[1]) }
            circleRe.findAll(svg).forEach {
                val (cx, cy, r) = it.destructured.let { (a, b, c) -> Triple(a.toFloat(), b.toFloat(), c.toFloat()) }
                stroke("M ${cx - r} $cy a $r $r 0 1 0 ${2 * r} 0 a $r $r 0 1 0 ${-2 * r} 0")
            }
            builder.build()
        }
    }
}
