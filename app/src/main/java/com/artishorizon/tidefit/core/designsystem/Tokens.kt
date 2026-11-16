package com.artishorizon.tidefit.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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

/** Large, soft radii in the spirit of Apple's continuous corners. */
object Radii {
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(50)
}

/** Applied to every Material component (dialogs, fields, menus, chips) so they match the custom ones. */
internal val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

object Elevation {
    val none: Dp = 0.dp
    val card: Dp = 0.dp // depth comes from tone and a hairline border, not shadows
    val overlay: Dp = 12.dp
}

object Sizes {
    val minTouchTarget = 48.dp
    val buttonHeight = 56.dp
    val fieldHeight = 56.dp
    val borderThin = 1.dp
    val ringStroke = 12.dp
    val progressBar = 10.dp
    val bottomBarHeight = 68.dp
}

object Motion {
    const val Short = 150
    const val Medium = 300
    const val Long = 600
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** iOS-like spring used for press feedback and selection. */
    fun <T> pressSpring() = spring<T>(dampingRatio = 0.65f, stiffness = 800f)
}
