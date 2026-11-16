package com.artishorizon.tidefit.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitLinearProgress
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.designsystem.components.StatCard
import com.artishorizon.tidefit.core.util.parseDecimal
import com.artishorizon.tidefit.domain.calc.BodyCalc
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.model.WeightEntry
import com.artishorizon.tidefit.domain.model.WeightUnit
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.SaveResult
import com.artishorizon.tidefit.domain.repository.WeightRepository
import com.artishorizon.tidefit.feature.workouts.formatWeight
import com.artishorizon.tidefit.feature.workouts.friendlyDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class WeightUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.EPOCH,
    val unit: WeightUnit = WeightUnit.KG,
    val entries: List<WeightEntry> = emptyList(),      // newest first
    val trend: List<BodyCalc.TrendPoint> = emptyList(),
    val latestKg: Double? = null,
    val trendKg: Double? = null,
    val weeklyRateKg: Double? = null,
    val targetKg: Double? = null,
    val startKg: Double? = null,
    val progress: Double? = null,
    val direction: BodyCalc.Direction? = null
)

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weights: WeightRepository,
    profiles: ProfileRepository,
    clock: Clock
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<WeightUiState> = combine(weights.observeAll(), profiles.observeProfile()) { all, profile ->
        val trend = BodyCalc.weightTrend(all)
        val latest = all.maxWithOrNull(compareBy({ it.date }, { it.recordedAt }))?.weightKg
        val target = profile?.targetWeightKg
        val start = all.minWithOrNull(compareBy({ it.date }, { it.recordedAt }))?.weightKg ?: profile?.initialWeightKg
        WeightUiState(
            loading = false, today = today, unit = profile?.weightUnit ?: WeightUnit.KG,
            entries = all.sortedWith(compareByDescending<WeightEntry> { it.date }.thenByDescending { it.recordedAt }),
            trend = trend, latestKg = latest, trendKg = trend.lastOrNull()?.trendKg,
            weeklyRateKg = BodyCalc.weeklyRateKg(all, today), targetKg = target, startKg = start,
            progress = if (latest != null && target != null && start != null) BodyCalc.goalProgress(start, latest, target) else null,
            direction = if (latest != null && target != null) BodyCalc.direction(latest, target) else null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    /** Returns an error message, or null when saved. */
    fun save(id: Long?, date: LocalDate, kg: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val r = if (id == null) weights.add(date, kg) else weights.update(id, date, kg)
            onResult((r as? SaveResult.Invalid)?.error)
        }
    }

    fun delete(id: Long) { viewModelScope.launch { weights.delete(id) } }
}

@Composable
fun WeightScreen(onBack: () -> Unit, vm: WeightViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WeightEntry?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<WeightEntry?>(null) }

    Scaffold(topBar = { FitTopBar("Weight", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard("Latest", formatWeight(state.latestKg, state.unit), Modifier.weight(1f), caption = state.entries.firstOrNull()?.let { friendlyDate(it.date, state.today) })
                    StatCard("7-day average", formatWeight(state.trendKg, state.unit), Modifier.weight(1f), caption = "smooths daily swings")
                }
            }
            item {
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Weekly change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val rate = state.weeklyRateKg
                        if (rate != null) {
                            val shown = Units.round1(Units.fromKg(rate, state.unit))
                            Text("${if (shown > 0) "+" else ""}$shown ${state.unit.label} / week", style = MaterialTheme.typography.titleLarge)
                            Text("Based on the last 4 weeks of weigh-ins.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Not enough data yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "A reliable rate needs at least ${BodyCalc.MIN_POINTS} weigh-in days spread over ${BodyCalc.MIN_SPAN_DAYS} days or more.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            state.progress?.let { p ->
                item {
                    FitCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("Goal progress", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${formatWeight(state.startKg, state.unit)} → ${formatWeight(state.targetKg, state.unit)}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            FitLinearProgress(p.toFloat(), color = FitTheme.colors.protein)
                            Text("${(p * 100).toInt()}% of the way", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { PrimaryButton("Log weight", { adding = true }, Modifier.fillMaxWidth()) }
            item {
                SectionHeader("Trend")
            }
            item {
                FitCard {
                    if (state.trend.isEmpty()) {
                        Text("No weigh-ins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val recent = state.trend.takeLast(60)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            LineChart(
                                listOf(
                                    ChartSeries(recent.map { ChartPoint(it.date, Units.fromKg(it.weightKg, state.unit)) }, MaterialTheme.colorScheme.outline, dotsOnly = true),
                                    ChartSeries(recent.map { ChartPoint(it.date, Units.fromKg(it.trendKg, state.unit)) }, MaterialTheme.colorScheme.primary)
                                ),
                                unitLabel = state.unit.label
                            )
                            Text("Dots are daily weigh-ins; the line is the 7-day average.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionHeader("History") }
            items(state.entries, key = { it.id }) { e ->
                FitCard(onClick = { editing = e }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatWeight(e.weightKg, state.unit), style = MaterialTheme.typography.titleSmall)
                            Text(friendlyDate(e.date, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ confirmDelete = e }) { Icon(Icons.Default.Delete, contentDescription = "Delete weight entry") }
                    }
                }
            }
        }
    }

    if (adding) WeightDialog("Log weight", null, state, { adding = false }) { date, kg, done -> vm.save(null, date, kg) { err -> if (err == null) adding = false; done(err) } }
    editing?.let { e ->
        WeightDialog("Edit weight", e, state, { editing = null }) { date, kg, done -> vm.save(e.id, date, kg) { err -> if (err == null) editing = null; done(err) } }
    }
    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("${formatWeight(e.weightKg, state.unit)} on ${friendlyDate(e.date, state.today)} will be removed.") },
            confirmButton = { TextButton({ vm.delete(e.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun WeightDialog(
    title: String,
    entry: WeightEntry?,
    state: WeightUiState,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, (String?) -> Unit) -> Unit
) {
    val initialKg = entry?.weightKg ?: state.latestKg
    var text by rememberSaveable { mutableStateOf(initialKg?.let { formatWeight(it, state.unit, withUnit = false) }.orEmpty()) }
    var date by remember { mutableStateOf(entry?.date ?: state.today) }
    var error by remember { mutableStateOf<String?>(null) }
    val value = text.parseDecimal()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(text, { text = it; error = null }, "Weight", keyboardType = KeyboardType.Decimal, suffix = state.unit.label,
                    error = error ?: if (text.isNotBlank() && value == null) "Enter a number." else null)
                DateButton(date, state.today, { date = it })
            }
        },
        confirmButton = { TextButton({ value?.let { onSave(date, Units.toKg(it, state.unit)) { err -> error = err } } }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
