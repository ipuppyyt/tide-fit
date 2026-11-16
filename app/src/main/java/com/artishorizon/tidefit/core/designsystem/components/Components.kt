package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Motion
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.Sizes
import com.artishorizon.tidefit.core.designsystem.Spacing

/** Standard content card: soft vertical gradient, hairline border, large radius, press feedback when tappable. */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val c = FitTheme.colors
    val base = modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.pressable(onClick = onClick) else it }
        .clip(Radii.lg)
        .background(Brush.verticalGradient(listOf(c.cardTop, c.cardBottom)))
        .border(Sizes.borderThin, c.border, Radii.lg)
    Box(base.padding(Spacing.lg)) { content() }
}

/** Bright accent card for the single most important thing on a screen. Text on it is always dark. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val c = FitTheme.colors
    val base = modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.pressable(onClick = onClick) else it }
        .clip(Radii.xl)
        .background(Brush.linearGradient(listOf(c.hero, c.hero.copy(red = 0.55f, green = 0.84f, blue = 0.2f))))
    CompositionLocalProvider(LocalContentColor provides c.onHero) {
        Box(base.padding(Spacing.xl)) { content() }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        trailing?.invoke()
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    contentColor: Color,
    borderColor: Color? = null
) {
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier
            .heightIn(min = Sizes.buttonHeight)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(Radii.pill)
            .background(container.copy(alpha = container.alpha * alpha))
            .let { if (borderColor != null) it.border(Sizes.borderThin, borderColor, Radii.pill) else it }
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = contentColor.copy(alpha = alpha), textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    PillButton(text, onClick, modifier, enabled, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)

/** Tinted, borderless secondary action (iOS "tinted"/"gray" button). */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    PillButton(
        text, onClick, modifier, enabled, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface,
        borderColor = FitTheme.colors.border
    )

@Composable
fun FitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    TextField(
        maxLines = maxLines,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = Radii.md,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            // One step lighter than dialogs (which use surfaceContainerHigh) so fields stay visible inside them.
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.fieldHeight)
    )
}
/** Pill-shaped toggle, used for filters and multi-select options. */
@Composable
fun FitChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .heightIn(min = 40.dp)
            .pressable(role = Role.Checkbox, onClick = onClick)
            .clip(Radii.pill)
            .background(bg)
            .let { if (!selected) it.border(Sizes.borderThin, FitTheme.colors.border, Radii.pill) else it }
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Multi-select chips that wrap onto several lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { option -> FitChip(option, option in selected, { onToggle(option) }) }
    }
}

/** Single-choice card row with a title and hint. */
@Composable
fun OptionCard(
    title: String,
    hint: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = Radii.md
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .pressable(role = Role.RadioButton, onClick = onClick)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 2.dp else Sizes.borderThin, if (selected) MaterialTheme.colorScheme.primary else FitTheme.colors.border, shape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Circular progress with a rounded, gradient-tinted arc. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = Sizes.ringStroke,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.Long, easing = Motion.Standard), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(strokePx))
            if (animated > 0f) {
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color)),
                        startAngle = 0f, sweepAngle = 360f * animated, useCenter = false,
                        topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun FitLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.Medium, easing = Motion.Standard), label = "bar")
    Canvas(modifier.fillMaxWidth().height(Sizes.progressBar)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (animated > 0f) drawRoundRect(color, size = Size(maxOf(size.width * animated, size.height), size.height), cornerRadius = r)
    }
}

@Composable
fun MacroIndicator(label: String, value: String, progress: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        FitLinearProgress(progress, color = color)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    FitCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Icon-over-label shortcut tile, sized for a row of four. */
@Composable
fun QuickTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            Modifier.size(64.dp).clip(Radii.lg)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(Sizes.borderThin, FitTheme.colors.border, Radii.lg),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().semantics { contentDescription = "Loading" }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) SecondaryButton("Try again", onRetry)
    }
}

/** Marks a number as an estimate rather than a measured/user-entered value. */
@Composable
fun EstimateBadge(text: String = "Estimate", modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = FitTheme.colors.warning,
        modifier = modifier
            .border(Sizes.borderThin, FitTheme.colors.warning.copy(alpha = 0.5f), Radii.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
    )
}
