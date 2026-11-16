package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.Spacing

/**
 * TIDE's own control set. Names deliberately match the Material ones so feature code reads the same,
 * but nothing here is a Material widget: the look comes entirely from the design tokens.
 */

/** Floating rounded sheet used for every dialog. */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = FitTheme.colors
        Column(
            modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .shadow(24.dp, Radii.xl, ambientColor = androidx.compose.ui.graphics.Color.Black, spotColor = androidx.compose.ui.graphics.Color.Black)
                .clip(Radii.xl)
                .background(Brush.verticalGradient(listOf(colors.cardTop, colors.cardBottom)))
                .border(1.dp, colors.border, Radii.xl)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (title != null) ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { title() }
            }
            if (text != null) ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { text() }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** Quiet pill-shaped text action in the accent colour. */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(Radii.pill)
            .pressable(enabled, Role.Button, onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

/** Borderless round icon target, 44 dp. */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier.size(44.dp).clip(CircleShape).pressable(enabled, Role.Button, onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) { content() }
    }
}

/** Pill switch: lime track when on, raised grey track when off. */
@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, spring(), label = "track"
    )
    val thumb by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, spring(), label = "thumb"
    )
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(0.7f, 600f), label = "x")
    Box(
        modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, FitTheme.colors.border, CircleShape)
            .then(if (onCheckedChange != null) Modifier.pressable(enabled, Role.Switch) { onCheckedChange(!checked) } else Modifier)
            .padding(3.dp)
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(CircleShape).background(thumb))
    }
}

/** Rounded-square tick box. */
@Composable
fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val fill by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, spring(), label = "fill")
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier
            .size(44.dp)
            .then(if (onCheckedChange != null) Modifier.pressable(enabled, Role.Checkbox) { onCheckedChange(!checked) } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(26.dp).clip(shape).background(fill)
                .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(lucide("check"), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
