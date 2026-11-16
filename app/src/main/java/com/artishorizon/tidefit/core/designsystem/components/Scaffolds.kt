package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.Sizes
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.domain.model.NutritionSource

/** Top bar for pushed screens: round back button and a bold left-aligned title, no container colour. */
@Composable
fun FitTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(com.artishorizon.tidefit.core.designsystem.SafeTop).heightIn(min = 64.dp).padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp)
                .pressable(onClick = onBack)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(Sizes.borderThin, FitTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(lucide("arrow-left"), contentDescription = "Back", modifier = Modifier.size(22.dp)) }
        Text(
            title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)
        )
        actions()
    }
}

/** Verified / Sourced / Estimated marker so estimated numbers are never mistaken for exact ones. */
@Composable
fun SourceBadge(source: NutritionSource, modifier: Modifier = Modifier) {
    val color = when (source) {
        NutritionSource.VERIFIED -> FitTheme.colors.success
        NutritionSource.SOURCED -> MaterialTheme.colorScheme.primary
        NutritionSource.ESTIMATED -> FitTheme.colors.warning
    }
    Text(
        source.label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .border(Sizes.borderThin, color.copy(alpha = 0.5f), Radii.pill)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
    )
}
