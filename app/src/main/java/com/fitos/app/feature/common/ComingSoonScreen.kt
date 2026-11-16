package com.fitos.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.fitos.app.core.designsystem.Spacing
import com.fitos.app.core.designsystem.components.EmptyState

/** Honest stand-in for modules scheduled in later phases; it performs no fake behaviour. */
@Composable
fun ComingSoonScreen(
    title: String,
    icon: ImageVector,
    description: String,
    phase: String,
    contentPadding: PaddingValues
) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(icon, "Arrives in $phase", description)
        }
    }
}
