package com.artishorizon.tidefit.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.components.HeroCard
import com.artishorizon.tidefit.core.designsystem.components.IconBadge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.domain.calc.BodyCalc
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.model.HeightUnit
import com.artishorizon.tidefit.domain.model.Measurement
import com.artishorizon.tidefit.domain.model.MeasurementType
import com.artishorizon.tidefit.domain.model.WeightUnit
import com.artishorizon.tidefit.domain.repository.MeasurementRepository
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.RecoveryRepository
import com.artishorizon.tidefit.domain.repository.SleepRepository
import com.artishorizon.tidefit.domain.repository.SupplementRepository
import com.artishorizon.tidefit.domain.repository.WeightRepository
import com.artishorizon.tidefit.feature.workouts.formatWeight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/** One line per tracked area. [headline] is null when there is no data, so the card says so instead of showing zeros. */
data class ProgressCardData(val headline: String?, val detail: String)

data class ProgressUiState(
    val loading: Boolean = true,
    val weight: ProgressCardData = ProgressCardData(null, ""),
    val measurements: ProgressCardData = ProgressCardData(null, ""),
    val sleep: ProgressCardData = ProgressCardData(null, ""),
    val recovery: ProgressCardData = ProgressCardData(null, ""),
    val supplements: ProgressCardData = ProgressCardData(null, "")
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    weights: WeightRepository,
    measurements: MeasurementRepository,
    sleep: SleepRepository,
    recovery: RecoveryRepository,
    supplements: SupplementRepository,
    profiles: ProfileRepository,
    clock: Clock
) : ViewModel() {
    private val today = LocalDate.now(clock)

    private val body = combine(weights.observeAll(), measurements.observeAll(), profiles.observeProfile()) { w, m, p -> Triple(w, m, p) }
    private val lifestyle = combine(
        sleep.observeRange(today.minusDays(29), today), recovery.observeRange(today.minusDays(29), today),
        supplements.observeAll(), supplements.observeLogs(today.minusDays(29), today)
    ) { s, r, sup, logs -> listOf(s, r, sup, logs) }

    val state: StateFlow<ProgressUiState> = combine(body, lifestyle) { (w, m, p), l ->
        @Suppress("UNCHECKED_CAST")
        val sleepEntries = l[0] as List<com.artishorizon.tidefit.domain.model.SleepEntry>
        @Suppress("UNCHECKED_CAST")
        val recoveryEntries = l[1] as List<com.artishorizon.tidefit.domain.model.RecoveryEntry>
        @Suppress("UNCHECKED_CAST")
        val sups = l[2] as List<com.artishorizon.tidefit.domain.model.Supplement>
        @Suppress("UNCHECKED_CAST")
        val logs = l[3] as List<com.artishorizon.tidefit.domain.model.SupplementLog>
        val unit = p?.weightUnit ?: WeightUnit.KG
        val hUnit = p?.heightUnit ?: HeightUnit.CM

        val trend = BodyCalc.weightTrend(w)
        val rate = BodyCalc.weeklyRateKg(w, today)
        val weight = trend.lastOrNull()?.let { last ->
            ProgressCardData(
                formatWeight(last.weightKg, unit),
                rate?.let { r -> val s = Units.round1(Units.fromKg(r, unit)); "${if (s > 0) "+" else ""}$s ${unit.label}/week (4-week trend)" }
                    ?: "Not enough weigh-ins yet for a weekly rate"
            )
        } ?: ProgressCardData(null, "No weigh-ins yet")

        val measured = MeasurementType.entries.mapNotNull { t -> m.filter { it.type == t }.maxByOrNull(Measurement::date)?.let { t to it } }
        val measurementCard = if (measured.isEmpty()) ProgressCardData(null, "Nothing measured yet")
        else ProgressCardData("${measured.size} tracked", measured.take(3).joinToString(" · ") { (t, x) -> "${t.label} ${formatMeasurement(t, x.value, hUnit)}" })

        val sl = BodyCalc.sleepSummary(sleepEntries, today, 7)
        val sleepCard = sl?.let { ProgressCardData(formatSleep(it.avgMinutes), "7-day average · ${it.nightsLogged} of 7 nights logged") } ?: ProgressCardData(null, "No sleep logged in the last 7 days")

        val rc = BodyCalc.recoverySummary(recoveryEntries, today, 7)
        val recoveryCard = rc?.let {
            ProgressCardData(
                "Energy ${"%.1f".format(Locale.US, it.energy)} / 5",
                "Soreness ${"%.1f".format(Locale.US, it.soreness)} · Stress ${"%.1f".format(Locale.US, it.stress)} · ${it.daysLogged} check-ins"
            )
        } ?: ProgressCardData(null, "No check-ins in the last 7 days")

        val adh = BodyCalc.supplementAdherence(sups, logs, today, 7)
        val supCard = adh?.let { ProgressCardData("${(it.fraction * 100).toInt()}%", "${it.taken} of ${it.expected} doses in the last 7 days") }
            ?: ProgressCardData(null, if (sups.isEmpty()) "No supplements added" else "No active supplements")

        ProgressUiState(false, weight, measurementCard, sleepCard, recoveryCard, supCard)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}

@Composable
fun ProgressScreen(
    contentPadding: PaddingValues,
    onOpenWeight: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenRecovery: () -> Unit,
    onOpenSupplements: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenCoach: () -> Unit,
    vm: ProgressViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = FitTheme.colors
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Column {
            Text("Insights", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Progress", style = MaterialTheme.typography.headlineLarge)
        }

        HeroCard(onClick = onOpenCoach) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("ChatGPT coaching", style = MaterialTheme.typography.headlineMedium)
                    Text("Export your data, paste ChatGPT's reply, review and approve changes.", style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.onHero), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = c.hero)
                }
            }
        }

        Text(
            "Averages use only the days you logged. Missing days are never counted as zero.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeader("Body")
        TileRow {
            AreaTile("Weight", Icons.Outlined.MonitorWeight, c.water, state.weight, onOpenWeight, Modifier.weight(1f))
            AreaTile("Measurements", Icons.Outlined.Straighten, c.carbs, state.measurements, onOpenMeasurements, Modifier.weight(1f))
        }

        SectionHeader("Lifestyle")
        TileRow {
            AreaTile("Sleep", Icons.Outlined.Bedtime, c.fat, state.sleep, onOpenSleep, Modifier.weight(1f))
            AreaTile("Recovery", Icons.Outlined.Favorite, c.protein, state.recovery, onOpenRecovery, Modifier.weight(1f))
        }
        AreaTile("Supplements", Icons.Outlined.Medication, c.fibre, state.supplements, onOpenSupplements, Modifier.fillMaxWidth())

        SectionHeader("Training")
        FitCard(onClick = onOpenTraining) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IconBadge(Icons.Outlined.QueryStats, MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Training stats", style = MaterialTheme.typography.titleMedium)
                    Text("Weekly volume, sets per muscle and personal records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
}

@Composable
private fun AreaTile(title: String, icon: ImageVector, tint: Color, data: ProgressCardData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FitCard(modifier.fillMaxHeight(), onClick = onClick) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconBadge(icon, tint, size = 40.dp)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                data.headline ?: "No data yet", style = MaterialTheme.typography.titleLarge,
                color = if (data.headline == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(data.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
