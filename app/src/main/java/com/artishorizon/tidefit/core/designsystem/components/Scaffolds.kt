package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.Sizes
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.domain.model.NutritionSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
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
