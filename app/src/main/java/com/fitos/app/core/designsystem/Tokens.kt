package com.fitos.app.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screenHorizontal = 20.dp
}

object Radii {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50)
}

object Elevation {
    val none: Dp = 0.dp
    val card: Dp = 0.dp // flat surfaces with borders; shadows reserved for overlays
    val overlay: Dp = 6.dp
}

object Sizes {
    val minTouchTarget = 48.dp
    val buttonHeight = 52.dp
    val fieldHeight = 56.dp
    val borderThin = 1.dp
    val ringStroke = 10.dp
    val progressBar = 8.dp
    val bottomBarHeight = 72.dp
}

object Motion {
    const val Short = 150
    const val Medium = 300
    const val Long = 500
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
