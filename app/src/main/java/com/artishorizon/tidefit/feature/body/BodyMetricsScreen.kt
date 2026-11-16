package com.artishorizon.tidefit.feature.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.HeroCard
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.domain.calc.BmiCategory
import com.artishorizon.tidefit.domain.calc.BodyMetrics
import com.artishorizon.tidefit.domain.calc.CalorieLimits
import com.artishorizon.tidefit.domain.calc.TargetCalculator
import com.artishorizon.tidefit.domain.calc.TargetInput
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.calc.calorieLimitsFor
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.Measurement
import com.artishorizon.tidefit.domain.model.MeasurementType
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.repository.MeasurementRepository
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.feature.workouts.formatWeight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class BodyMetricsUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val targets: DailyTargets? = null,
    val latest: Map<MeasurementType, Double> = emptyMap(),
    val bmr: Int? = null,
    val tdee: Int? = null,
    val limits: CalorieLimits? = null
)

@HiltViewModel
class BodyMetricsViewModel @Inject constructor(
    profiles: ProfileRepository,
    measurements: MeasurementRepository
) : ViewModel() {
    val state: StateFlow<BodyMetricsUiState> = combine(
        profiles.observeProfile(), profiles.observeCurrentTargets(), measurements.observeAll()
    ) { p, t, m ->
        val latest = m.groupBy(Measurement::type).mapValues { (_, v) -> v.maxBy(Measurement::date).value }
        if (p == null) BodyMetricsUiState(loading = false)
        else {
            val bmr = TargetCalculator.bmr(TargetInput(p.age, p.sex, p.heightCm, p.currentWeightKg, p.activityLevel, p.goal))
            BodyMetricsUiState(
                false, p, t, latest, bmr.roundToInt(), (bmr * p.activityLevel.factor).roundToInt(),
                t?.let { calorieLimitsFor(p, it.caloriesKcal) }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyMetricsUiState())
}

@Composable
fun BodyMetricsScreen(onBack: () -> Unit, vm: BodyMetricsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { FitTopBar("Body metrics", onBack) }) { padding ->
        val p = state.profile
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        if (p == null) { Text("Finish setting up your profile to see body metrics.", Modifier.padding(padding).padding(Spacing.screenHorizontal)); return@Scaffold }

        val unit = p.weightUnit
        val bmi = BodyMetrics.bmi(p.currentWeightKg, p.heightCm)
        val category = BodyMetrics.bmiCategory(bmi)
        val range = BodyMetrics.healthyWeightRangeKg(p.heightCm)
        val waist = state.latest[MeasurementType.WAIST]
        val neck = state.latest[MeasurementType.NECK]
        val hips = state.latest[MeasurementType.HIPS]
        val measuredFat = state.latest[MeasurementType.BODY_FAT]
        val navyFat = BodyMetrics.navyBodyFatPercent(p.sex, p.heightCm, waist, neck, hips)
        val fat = measuredFat ?: navyFat

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            HeroCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Body mass index", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(String.format(Locale.US, "%.1f", bmi), style = MaterialTheme.typography.displayLarge)
                        Text(category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    BmiGauge(bmi)
                    Text(
                        "BMI ignores muscle mass, so it is a rough screen. Use it alongside waist and body-fat figures.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SectionHeader("Size")
            Tiles {
                Tile("Height", heightText(p), Modifier.weight(1f))
                Tile("Weight", formatWeight(p.currentWeightKg, unit), Modifier.weight(1f))
            }
            Tiles {
                Tile(
                    "Healthy range", "${formatWeight(range.start, unit, false)}–${formatWeight(range.endInclusive, unit)}",
                    Modifier.weight(1f), caption = "BMI 18.5 to 24.9"
                )
                Tile(
                    "Goal weight", formatWeight(p.targetWeightKg, unit), Modifier.weight(1f),
                    caption = goalCaption(p)
                )
            }

            SectionHeader("Energy")
            Tiles {
                Tile("BMR", state.bmr?.let { "$it kcal" } ?: "–", Modifier.weight(1f), caption = "burned at rest")
                Tile("Maintenance", state.tdee?.let { "$it kcal" } ?: "–", Modifier.weight(1f), caption = "with your activity level")
            }
            state.limits?.let { l ->
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(l.kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(l.ruleText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        l.expectedWeeklyChangeKg?.let { w ->
                            val pct = CalorieLimits.weeklyPercent(w, p.currentWeightKg) ?: 0.0
                            val sign = if (w > 0.005) "+" else if (w < -0.005) "−" else ""
                            Text(
                                "Expected change: $sign${String.format(Locale.US, "%.2f", kotlin.math.abs(Units.fromKg(w, unit)))} ${unit.label} per week (${String.format(Locale.US, "%.1f", pct)}% of body weight)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        l.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            SectionHeader("Composition")
            Tiles {
                Tile("Waist / height", waist?.let { String.format(Locale.US, "%.2f", BodyMetrics.waistToHeight(it, p.heightCm)) } ?: "–", Modifier.weight(1f),
                    caption = if (waist == null) "log your waist" else "keep under 0.50")
                Tile("Waist / hip", if (waist != null && hips != null) String.format(Locale.US, "%.2f", BodyMetrics.waistToHip(waist, hips)) else "–", Modifier.weight(1f),
                    caption = if (hips == null) "log your hips" else "lower is better")
            }
            Tiles {
                Tile("Body fat", fat?.let { String.format(Locale.US, "%.1f%%", it) } ?: "–", Modifier.weight(1f),
                    caption = when { measuredFat != null -> "logged by you"; navyFat != null -> "US Navy estimate"; else -> "needs waist and neck" })
                Tile("Lean mass", fat?.let { formatWeight(BodyMetrics.leanMassKg(p.currentWeightKg, it), unit) } ?: "–", Modifier.weight(1f),
                    caption = fat?.let { "fat ${formatWeight(BodyMetrics.fatMassKg(p.currentWeightKg, it), unit)}" } ?: "needs body fat")
            }
            Text(
                "Estimates come from standard formulas (Mifflin–St Jeor, WHO BMI bands, US Navy body-fat method) and can be off by several percent. They are not medical advice.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

private fun heightText(p: UserProfile): String =
    if (p.heightUnit == com.artishorizon.tidefit.domain.model.HeightUnit.FT_IN) {
        val totalIn = p.heightCm / 2.54
        "${(totalIn / 12).toInt()} ft ${(totalIn % 12).roundToInt()} in"
    } else "${p.heightCm.roundToInt()} cm"

private fun goalCaption(p: UserProfile): String {
    val diff = p.targetWeightKg - p.currentWeightKg
    val d = formatWeight(kotlin.math.abs(diff), p.weightUnit)
    return when { diff < -0.05 -> "$d to lose"; diff > 0.05 -> "$d to gain"; else -> "at goal" }
}

@Composable
private fun Tiles(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    FitCard(modifier.fillMaxHeight()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Four-band BMI bar (underweight / healthy / overweight / obese) with a marker at the current value. */
@Composable
private fun BmiGauge(bmi: Double) {
    val ink = FitTheme.colors.onHero
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val lo = 15.0; val hi = 40.0
        fun x(v: Double) = (((v.coerceIn(lo, hi) - lo) / (hi - lo)) * size.width).toFloat()
        val bar = 8.dp.toPx()
        val top = (size.height - bar) / 2
        val bands = listOf(lo to 18.5, 18.5 to 25.0, 25.0 to 30.0, 30.0 to hi)
        bands.forEachIndexed { i, (a, b) ->
            drawRect(ink.copy(alpha = if (i == 1) 0.55f else 0.22f), Offset(x(a) + if (i > 0) 2 else 0, top), Size(x(b) - x(a) - if (i > 0) 2 else 0, bar))
        }
        drawCircle(ink, 9.dp.toPx(), Offset(x(bmi), size.height / 2))
        drawCircle(Color.Transparent, 0f)
    }
}
