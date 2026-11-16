package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Motion
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.Sizes
import com.artishorizon.tidefit.core.designsystem.Spacing

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Floating pill tab bar. The selected destination expands into an accent pill with its label; the others stay
 * icon-only. It floats above the content with a translucent fill and hairline border.
 */
@Composable
fun FloatingTabBar(items: List<TabItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val fade = MaterialTheme.colorScheme.background
  Box(modifier.background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, fade.copy(alpha = 0.92f), fade)))) {
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md)
            .fillMaxWidth()
            .height(Sizes.bottomBarHeight)
            .shadow(16.dp, Radii.pill, clip = false, ambientColor = androidx.compose.ui.graphics.Color.Black, spotColor = androidx.compose.ui.graphics.Color.Black)
            .clip(Radii.pill)
            .background(FitTheme.colors.glass)
            .border(Sizes.borderThin, FitTheme.colors.border, Radii.pill)
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                Motion.pressSpring(), label = "tabBg"
            )
            val fg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                Motion.pressSpring(), label = "tabFg"
            )
            Row(
                Modifier
                    .semantics { this.selected = selected; contentDescription = item.label }
                    .pressable(role = Role.Tab, onClick = { onSelect(i) })
                    .clip(Radii.pill)
                    .background(bg)
                    .animateContentSize(Motion.pressSpring())
                    .height(52.dp)
                    .padding(horizontal = if (selected) 18.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
                if (selected) Text(item.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
            }
        }
    }
}
  }
