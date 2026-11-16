package com.artishorizon.tidefit.core.designsystem

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Status bar or display cutout, whichever is taller, at the top edge. */
val SafeTop: WindowInsets
    @Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)

/** System bars and display cutout on the left and right edges (landscape, foldables). */
val SafeHorizontal: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)

object FitTheme {
    val colors: FitColors
        @Composable get() = LocalFitColors.current
}

@Composable
fun TideFitTheme(
    paletteId: String? = null,
    content: @Composable () -> Unit
) {
    val palette = resolvePalette(paletteId)
    val dark = palette.dark
    val scheme = palette.scheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalFitColors provides palette.fit) {
        MaterialTheme(colorScheme = scheme, typography = FitTypography, shapes = FitShapes) {
            // Paint the theme's own background so it never depends on the window's static colour.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    // Keep everything out of side cutouts and landscape system bars; top/bottom are handled per screen.
                    Box(Modifier.fillMaxSize().windowInsetsPadding(SafeHorizontal)) { content() }
                    // Scrim so scrolling content fades out under the status bar instead of colliding with the clock.
                    val bg = MaterialTheme.colorScheme.background
                    val bar = SafeTop.asPaddingValues().calculateTopPadding()
                    val height = bar + 20.dp
                    val solid = bar / height
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(height)
                            .background(Brush.verticalGradient(0f to bg, solid to bg, 1f to bg.copy(alpha = 0f)))
                    )
                    // Same fade above the gesture bar / 3-button bar so text never sits under the system handle.
                    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    if (navBar > 0.dp) {
                        val h = navBar + 16.dp
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h)
                                .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 1f - navBar / h to bg, 1f to bg))
                        )
                    }
                }
            }
        }
    }
}
