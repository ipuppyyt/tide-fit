package com.artishorizon.tidefit.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Domain colours that Material's scheme has no slot for (macros, hydration, status, surfaces). */
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
    val border: Color,
    /** Top and bottom of the subtle vertical gradient on cards. */
    val cardTop: Color,
    val cardBottom: Color,
    /** Translucent fill for floating surfaces such as the tab bar. */
    val glass: Color,
    /** Bright accent for the hero card and highlights, and the text colour that sits on it. */
    val hero: Color,
    val onHero: Color
)

internal val DarkFitColors = FitColors(
    protein = Color(0xFFFF7A59),
    carbs = Color(0xFFFFC24B),
    fat = Color(0xFF5AA9FF),
    fibre = Color(0xFF4ADE80),
    water = Color(0xFF38BDF8),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFFB020),
    surfaceRaised = Color(0xFF1C2025),
    border = Color(0x1FFFFFFF),
    cardTop = Color(0xFF181B20),
    cardBottom = Color(0xFF111317),
    glass = Color(0xF0171A1F),
    hero = Color(0xFFC8F53C),
    onHero = Color(0xFF0B1100)
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
    border = Color(0x14000000),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFFAFAFC),
    glass = Color(0xEBFFFFFF),
    hero = Color(0xFFC8F53C),
    onHero = Color(0xFF0B1100)
)

val LocalFitColors = staticCompositionLocalOf { DarkFitColors }

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFC8F53C),
    onPrimary = Color(0xFF0B1100),
    primaryContainer = Color(0xFF27350A),
    onPrimaryContainer = Color(0xFFE3FA9C),
    secondary = Color(0xFF98A1AD),
    onSecondary = Color(0xFF07080A),
    tertiary = Color(0xFF5CD6FF),
    onTertiary = Color(0xFF00252F),
    background = Color(0xFF07080A),
    onBackground = Color(0xFFF1F3F6),
    surface = Color(0xFF0E1013),
    onSurface = Color(0xFFF1F3F6),
    surfaceVariant = Color(0xFF1C2025),
    onSurfaceVariant = Color(0xFF98A1AD),
    surfaceContainerLowest = Color(0xFF07080A),
    surfaceContainerLow = Color(0xFF0E1013),
    surfaceContainer = Color(0xFF14171B),
    surfaceContainerHigh = Color(0xFF1C2025),
    surfaceContainerHighest = Color(0xFF262B32),
    outline = Color(0xFF3B424C),
    outlineVariant = Color(0xFF23282F),
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
    tertiary = Color(0xFF0B8FC2),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF12151A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFEAECF0),
    onSurfaceVariant = Color(0xFF55606E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEBEBF0),
    surfaceContainerHighest = Color(0xFFE2E4EA),
    outline = Color(0xFF8A94A2),
    outlineVariant = Color(0xFFDDE1E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)
