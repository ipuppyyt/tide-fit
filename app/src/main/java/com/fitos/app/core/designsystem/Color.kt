package com.fitos.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Domain colours that Material's scheme has no slot for (macros, hydration, status). */
@Immutable
data class FitColors(
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val fibre: Color,
    val water: Color,
    val success: Color,
    val warning: Color,
    val surfaceRaised: Color,
    val border: Color
)

internal val DarkFitColors = FitColors(
    protein = Color(0xFFFF7A59),
    carbs = Color(0xFFFFC24B),
    fat = Color(0xFF5AA9FF),
    fibre = Color(0xFF4ADE80),
    water = Color(0xFF38BDF8),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFFB020),
    surfaceRaised = Color(0xFF1A1E24),
    border = Color(0xFF262B33)
)

internal val LightFitColors = FitColors(
    protein = Color(0xFFD9502E),
    carbs = Color(0xFFB8860B),
    fat = Color(0xFF2B7DD6),
    fibre = Color(0xFF1E9E55),
    water = Color(0xFF0B8FC2),
    success = Color(0xFF1E9E55),
    warning = Color(0xFFB36B00),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFDDE1E6)
)

val LocalFitColors = staticCompositionLocalOf { DarkFitColors }

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFC6F135),
    onPrimary = Color(0xFF101400),
    primaryContainer = Color(0xFF2B3A06),
    onPrimaryContainer = Color(0xFFE3FA9C),
    secondary = Color(0xFF9AA4B2),
    onSecondary = Color(0xFF0B0D10),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFECEFF3),
    surface = Color(0xFF12151A),
    onSurface = Color(0xFFECEFF3),
    surfaceVariant = Color(0xFF1A1E24),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF12151A),
    surfaceContainer = Color(0xFF161A20),
    surfaceContainerHigh = Color(0xFF1A1E24),
    surfaceContainerHighest = Color(0xFF21262D),
    outline = Color(0xFF3A414B),
    outlineVariant = Color(0xFF262B33),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000)
)

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4F6B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7F27A),
    onPrimaryContainer = Color(0xFF151D00),
    secondary = Color(0xFF55606E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF12151A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF55606E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F9),
    surfaceContainer = Color(0xFFF0F2F5),
    surfaceContainerHigh = Color(0xFFEAECF0),
    surfaceContainerHighest = Color(0xFFE2E5EA),
    outline = Color(0xFF8A94A2),
    outlineVariant = Color(0xFFDDE1E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)
