package com.fitos.app.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitos.app.core.designsystem.Spacing
import com.fitos.app.core.designsystem.components.FitCard
import com.fitos.app.core.designsystem.components.SectionHeader
import com.fitos.app.domain.calc.BodyCalc
import com.fitos.app.domain.calc.Units
import com.fitos.app.domain.model.HeightUnit
import com.fitos.app.domain.model.Measurement
import com.fitos.app.domain.model.MeasurementType
import com.fitos.app.domain.model.WeightUnit
import com.fitos.app.domain.repository.MeasurementRepository
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.domain.repository.RecoveryRepository
import com.fitos.app.domain.repository.SleepRepository
import com.fitos.app.domain.repository.SupplementRepository
import com.fitos.app.domain.repository.WeightRepository
import com.fitos.app.feature.workouts.formatWeight
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
        val sleepEntries = l[0] as List<com.fitos.app.domain.model.SleepEntry>
        @Suppress("UNCHECKED_CAST")
        val recoveryEntries = l[1] as List<com.fitos.app.domain.model.RecoveryEntry>
        @Suppress("UNCHECKED_CAST")
        val sups = l[2] as List<com.fitos.app.domain.model.Supplement>
        @Suppress("UNCHECKED_CAST")
        val logs = l[3] as List<com.fitos.app.domain.model.SupplementLog>
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
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Averages use only the days you logged. Missing days are never counted as zero.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FitCard(onClick = onOpenCoach) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("ChatGPT coaching", style = MaterialTheme.typography.titleMedium)
                Text("Export your data, paste ChatGPT's reply, review and approve changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionHeader("Body")
        AreaCard("Weight", state.weight, onOpenWeight)
        AreaCard("Measurements", state.measurements, onOpenMeasurements)
        SectionHeader("Lifestyle")
        AreaCard("Sleep", state.sleep, onOpenSleep)
        AreaCard("Recovery", state.recovery, onOpenRecovery)
        AreaCard("Supplements", state.supplements, onOpenSupplements)
        SectionHeader("Training")
        FitCard(onClick = onOpenTraining) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Training stats", style = MaterialTheme.typography.titleMedium)
                Text("Weekly volume, sets per muscle and personal records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AreaCard(title: String, data: ProgressCardData, onClick: () -> Unit) {
    FitCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(data.headline ?: "No data yet", style = MaterialTheme.typography.titleLarge,
                color = if (data.headline == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(data.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
