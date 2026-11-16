package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.artishorizon.tidefit.core.designsystem.Motion

/**
 * Tappable surface with iOS-style feedback: it shrinks slightly under the finger on a spring and gives a
 * light haptic tick on release. No ripple, so it reads as one object.
 */
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, Motion.pressSpring(), label = "press")
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        }
}
