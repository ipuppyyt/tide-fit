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
import com.artishorizon.tidefit.core.designsystem.components.AlertDialog
import androidx.compose.material3.Icon
import com.artishorizon.tidefit.core.designsystem.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.artishorizon.tidefit.core.designsystem.components.TextButton
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
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.ChipGroup
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.util.parseDecimal
import com.artishorizon.tidefit.domain.calc.BodyCalc
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.model.HeightUnit
import com.artishorizon.tidefit.domain.model.Measurement
import com.artishorizon.tidefit.domain.model.MeasurementType
import com.artishorizon.tidefit.domain.repository.MeasurementRepository
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.SaveResult
import com.artishorizon.tidefit.feature.workouts.friendlyDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/** Lengths are stored in cm and shown in inches when the user's height unit is feet/inches. */
fun MeasurementType.unitLabel(heightUnit: HeightUnit) = if (isPercent) "%" else if (heightUnit == HeightUnit.CM) "cm" else "in"
fun displayValue(type: MeasurementType, storedValue: Double, heightUnit: HeightUnit): Double =
    if (type.isPercent || heightUnit == HeightUnit.CM) storedValue else Units.cmToInches(storedValue)
fun storedValue(type: MeasurementType, shownValue: Double, heightUnit: HeightUnit): Double =
    if (type.isPercent || heightUnit == HeightUnit.CM) shownValue else Units.inchesToCm(shownValue)
fun formatMeasurement(type: MeasurementType, stored: Double, heightUnit: HeightUnit): String =
    "%.1f %s".format(Locale.US, displayValue(type, stored, heightUnit), type.unitLabel(heightUnit))

data class MeasurementsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.EPOCH,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val all: List<Measurement> = emptyList()
) {
    fun of(type: MeasurementType) = all.filter { it.type == type }.sortedWith(compareBy({ it.date }, { it.id }))
}

@HiltViewModel
class MeasurementsViewModel @Inject constructor(
    private val repo: MeasurementRepository,
    profiles: ProfileRepository,
    clock: Clock
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<MeasurementsUiState> = combine(repo.observeAll(), profiles.observeProfile()) { all, p ->
        MeasurementsUiState(false, today, p?.heightUnit ?: HeightUnit.CM, all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementsUiState())

    fun save(id: Long?, type: MeasurementType, date: LocalDate, value: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val r = if (id == null) repo.add(date, type, value) else repo.update(id, date, value)
            onResult((r as? SaveResult.Invalid)?.error)
        }
    }

    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
}

@Composable
fun MeasurementsScreen(onBack: () -> Unit, vm: MeasurementsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(MeasurementType.WAIST) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Measurement?>(null) }
    var confirmDelete by remember { mutableStateOf<Measurement?>(null) }
    val series = state.of(selected)
    val unit = state.heightUnit

    Scaffold(topBar = { FitTopBar("Measurements", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                ChipGroup(MeasurementType.entries.map { it.label }, setOf(selected.label), { l -> selected = MeasurementType.entries.first { it.label == l } })
            }
            item {
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(selected.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val latest = series.lastOrNull()
                        if (latest == null) {
                            Text("No ${selected.label.lowercase()} readings yet.", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text(formatMeasurement(selected, latest.value, unit), style = MaterialTheme.typography.headlineMedium)
                            val change = BodyCalc.measurementChange(series, state.today, 30)
                            Text(
                                change?.let { c ->
                                    val shown = displayValue(selected, c, unit).let { Units.round1(it) }
                                    "${if (shown > 0) "+" else ""}$shown ${selected.unitLabel(unit)} over the last 30 days"
                                } ?: "Change appears after two readings within 30 days.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (series.size >= 2) {
                            LineChart(
                                listOf(ChartSeries(series.takeLast(30).map { ChartPoint(it.date, displayValue(selected, it.value, unit)) }, MaterialTheme.colorScheme.primary)),
                                unitLabel = selected.unitLabel(unit)
                            )
                        }
                    }
                }
            }
            item { PrimaryButton("Log ${selected.label.lowercase()}", { adding = true }, Modifier.fillMaxWidth()) }
            item { SectionHeader("History") }
            if (series.isEmpty()) item { Text("Nothing logged for ${selected.label.lowercase()}.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(series.reversed(), key = { it.id }) { m ->
                FitCard(onClick = { editing = m }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatMeasurement(m.type, m.value, unit), style = MaterialTheme.typography.titleSmall)
                            Text(friendlyDate(m.date, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ confirmDelete = m }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${m.type.label} reading") }
                    }
                }
            }
        }
    }

    if (adding) MeasurementDialog("Log ${selected.label.lowercase()}", selected, null, null, state, { adding = false }) { date, v, done ->
        vm.save(null, selected, date, v) { err -> if (err == null) adding = false; done(err) }
    }
    editing?.let { m ->
        MeasurementDialog("Edit ${m.type.label.lowercase()}", m.type, m.value, m.date, state, { editing = null }) { date, v, done ->
            vm.save(m.id, m.type, date, v) { err -> if (err == null) editing = null; done(err) }
        }
    }
    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this reading?") },
            text = { Text("${formatMeasurement(m.type, m.value, unit)} on ${friendlyDate(m.date, state.today)} will be removed.") },
            confirmButton = { TextButton({ vm.delete(m.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MeasurementDialog(
    title: String,
    type: MeasurementType,
    initialStored: Double?,
    initialDate: LocalDate?,
    state: MeasurementsUiState,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, (String?) -> Unit) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialStored?.let { "%.1f".format(Locale.US, displayValue(type, it, state.heightUnit)) }.orEmpty()) }
    var date by remember { mutableStateOf(initialDate ?: state.today) }
    var error by remember { mutableStateOf<String?>(null) }
    val value = text.parseDecimal()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(text, { text = it; error = null }, type.label, keyboardType = KeyboardType.Decimal, suffix = type.unitLabel(state.heightUnit),
                    error = error ?: if (text.isNotBlank() && value == null) "Enter a number." else null)
                DateButton(date, state.today, { date = it })
            }
        },
        confirmButton = { TextButton({ value?.let { onSave(date, storedValue(type, it, state.heightUnit)) { err -> error = err } } }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
