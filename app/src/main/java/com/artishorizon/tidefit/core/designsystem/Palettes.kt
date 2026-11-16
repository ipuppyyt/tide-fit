package com.artishorizon.tidefit.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.LocalContext

/** A complete, predefined look: Material scheme plus the app's domain colours. */
@Immutable
data class Palette(
    val id: String,
    val name: String,
    val dark: Boolean,
    val scheme: ColorScheme,
    val fit: FitColors
) {
    val accent: Color get() = scheme.primary
    val background: Color get() = scheme.background
    val surface: Color get() = scheme.surfaceContainerHigh
}

private fun dark(id: String, name: String, accent: Long, container: Long, onContainer: Long, tertiary: Long, onTertiary: Long = 0xFF001018) =
    Palette(
        id, name, true,
        DarkScheme.copy(
            primary = Color(accent), onPrimary = Color(0xFF0A0C0E),
            primaryContainer = Color(container), onPrimaryContainer = Color(onContainer),
            tertiary = Color(tertiary), onTertiary = Color(onTertiary)
        ),
        DarkFitColors.copy(hero = Color(accent), onHero = Color(0xFF0A0C0E))
    )

private fun light(id: String, name: String, accent: Long, container: Long, onContainer: Long, tertiary: Long, hero: Long) =
    Palette(
        id, name, false,
        LightScheme.copy(
            primary = Color(accent), onPrimary = Color.White,
            primaryContainer = Color(container), onPrimaryContainer = Color(onContainer),
            tertiary = Color(tertiary)
        ),
        LightFitColors.copy(hero = Color(hero), onHero = Color(0xFF0A0C0E))
    )

object Palettes {
    val Volt = Palette("volt", "Volt", true, DarkScheme, DarkFitColors)
    val Paper = Palette("paper", "Paper", false, LightScheme, LightFitColors)

    val all: List<Palette> = listOf(
        Volt,
        dark("ocean", "Ocean", 0xFF5CC8FF, 0xFF0B2F45, 0xFFBDE8FF, 0xFFC8F53C),
        dark("aurora", "Aurora", 0xFFB78CFF, 0xFF2E1B52, 0xFFE3D2FF, 0xFF5CD6FF),
        dark("ember", "Ember", 0xFFFF8A4C, 0xFF4A2308, 0xFFFFD9C2, 0xFFFFD166),
        dark("rose", "Rose", 0xFFFF6FA5, 0xFF4F1230, 0xFFFFD0E2, 0xFFFFB86B),
        dark("mint", "Mint", 0xFF3DDC97, 0xFF0B3A27, 0xFFB5F5D8, 0xFF5CD6FF),
        Paper,
        light("daylight", "Daylight", 0xFF0B6FB8, 0xFFCDE7FA, 0xFF001E33, 0xFF5B3FD0, 0xFF5CC8FF),
        light("lilac", "Lilac", 0xFF6B4BC9, 0xFFE4DAFF, 0xFF1D0E4D, 0xFF0B8FC2, 0xFFB78CFF),
        light("blossom", "Blossom", 0xFFC2185B, 0xFFFFD9E6, 0xFF3F0020, 0xFF0B6FB8, 0xFFFF6FA5),
        light("linen", "Linen", 0xFFB85400, 0xFFFFDDC2, 0xFF3A1900, 0xFF0B8FC2, 0xFFFF8A4C),
        light("meadow", "Meadow", 0xFF1E7A4F, 0xFFC2F0D9, 0xFF00210F, 0xFF0B6FB8, 0xFF3DDC97)
    )

    fun byId(id: String?): Palette? = all.firstOrNull { it.id == id }

    /**
     * Pick the palette whose accent is closest in hue to the system accent (Material You, Android 12+).
     * Dark-mode systems choose among dark palettes, light-mode systems among light ones.
     * Below Android 12 there is no system accent, so fall back to Volt or Paper.
     */
    fun fromSystem(systemAccent: Color?, systemDark: Boolean): Palette {
        val pool = all.filter { it.dark == systemDark }
        if (systemAccent == null) return pool.first()
        val h = hue(systemAccent)
        return pool.minBy { hueDistance(h, hue(it.fit.hero)) }
    }

    private fun hueDistance(a: Float, b: Float): Float { val d = kotlin.math.abs(a - b) % 360f; return if (d > 180f) 360f - d else d }

    private fun hue(c: Color): Float {
        val s = c.convert(ColorSpaces.Srgb)
        val r = s.red; val g = s.green; val b = s.blue
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        if (d < 0.0001f) return 0f
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return if (h < 0) h + 360f else h
    }
}

/** The palette the system would choose for this device right now (used for the Auto option). */
@Composable
fun systemPalette(): Palette {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context).primary else null
    return Palettes.fromSystem(accent, dark)
}

/** Stored palette id, or null meaning "Auto": follow the system. */
@Composable
fun resolvePalette(id: String?): Palette = Palettes.byId(id) ?: systemPalette()

/** Same choice as [resolvePalette], for code outside Compose (the home screen widget). */
fun Palettes.resolve(context: android.content.Context, id: String?): Palette {
    byId(id)?.let { return it }
    val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context).primary else null
    return fromSystem(accent, dark)
}
