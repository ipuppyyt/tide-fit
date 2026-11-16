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
import androidx.compose.material3.Icon
import com.artishorizon.tidefit.core.designsystem.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.artishorizon.tidefit.core.designsystem.components.StatCard
import com.artishorizon.tidefit.domain.calc.BodyCalc
import com.artishorizon.tidefit.domain.model.RecoveryEntry
import com.artishorizon.tidefit.domain.model.SleepEntry
import com.artishorizon.tidefit.domain.repository.RecoveryRepository
import com.artishorizon.tidefit.domain.repository.SaveResult
import com.artishorizon.tidefit.domain.repository.SleepRepository
import com.artishorizon.tidefit.feature.workouts.friendlyDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

fun formatSleep(minutes: Int): String = "${minutes / 60} h ${"%02d".format(minutes % 60)} min"

// ---------------------------------------------------------------- Sleep

data class SleepUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.EPOCH,
    val entries: List<SleepEntry> = emptyList(),   // newest first, last 30 days
    val summary: BodyCalc.SleepSummary? = null
)

@HiltViewModel
class SleepViewModel @Inject constructor(private val repo: SleepRepository, clock: Clock) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<SleepUiState> = repo.observeRange(today.minusDays(29), today).map { all ->
        SleepUiState(false, today, all.sortedByDescending { it.date }, BodyCalc.sleepSummary(all, today, 7))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepUiState())

    fun save(entry: SleepEntry, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult((repo.save(entry) as? SaveResult.Invalid)?.error) }
    }

    fun delete(date: LocalDate) { viewModelScope.launch { repo.delete(date) } }
}

@Composable
fun SleepScreen(onBack: () -> Unit, vm: SleepViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var date by rememberSaveable(state.today) { mutableStateOf(state.today) }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // When the chosen date already has an entry, load it so saving edits rather than silently replacing blind.
    val existing = state.entries.firstOrNull { it.date == date }
    LaunchedEffect(date, existing?.id, existing?.durationMinutes, existing?.quality) {
        hours = existing?.let { (it.durationMinutes / 60).toString() }.orEmpty()
        minutes = existing?.let { (it.durationMinutes % 60).toString() }.orEmpty()
        quality = existing?.quality
        note = existing?.note.orEmpty()
        error = null
    }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)

    Scaffold(topBar = { FitTopBar("Sleep", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                val s = state.summary
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard("7-day average", s?.let { formatSleep(it.avgMinutes) } ?: "–", Modifier.weight(1f), caption = s?.let { "${it.nightsLogged} of 7 nights logged" } ?: "No nights logged")
                    StatCard("Quality", s?.avgQuality?.let { "%.1f / 5".format(Locale.US, it) } ?: "–", Modifier.weight(1f), caption = "your own rating")
                }
            }
            item {
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(if (existing != null) "Edit night" else "Log sleep", style = MaterialTheme.typography.titleMedium)
                        Text("Date the sleep ended (the morning you woke up).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DateButton(date, state.today, { date = it }, Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FitTextField(hours, { hours = it.filter(Char::isDigit).take(2); error = null }, "Hours", Modifier.weight(1f), KeyboardType.Number, "h")
                            FitTextField(minutes, { minutes = it.filter(Char::isDigit).take(2); error = null }, "Minutes", Modifier.weight(1f), KeyboardType.Number, "min")
                        }
                        Text("Quality (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChipGroup(listOf("1", "2", "3", "4", "5"), quality?.let { setOf(it.toString()) } ?: emptySet(), { q -> quality = if (quality == q.toInt()) null else q.toInt() })
                        FitTextField(note, { note = it }, "Note (optional)", singleLine = false)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        PrimaryButton(if (existing != null) "Update" else "Save", {
                            vm.save(SleepEntry(date = date, durationMinutes = total, quality = quality, note = note)) { error = it }
                        }, Modifier.fillMaxWidth(), enabled = total > 0)
                    }
                }
            }
            item { SectionHeader("Last 30 days") }
            if (state.entries.isEmpty()) item { Text("No sleep logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.entries, key = { it.date.toEpochDay() }) { e ->
                FitCard(onClick = { date = e.date }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatSleep(e.durationMinutes) + (e.quality?.let { " · quality $it/5" } ?: ""), style = MaterialTheme.typography.titleSmall)
                            Text(friendlyDate(e.date, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.delete(e.date) }) { Icon(Icons.Default.Delete, contentDescription = "Delete sleep entry") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Recovery

data class RecoveryUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.EPOCH,
    val entries: List<RecoveryEntry> = emptyList(),
    val summary: BodyCalc.RecoverySummary? = null
)

@HiltViewModel
class RecoveryViewModel @Inject constructor(private val repo: RecoveryRepository, clock: Clock) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<RecoveryUiState> = repo.observeRange(today.minusDays(29), today).map { all ->
        RecoveryUiState(false, today, all.sortedByDescending { it.date }, BodyCalc.recoverySummary(all, today, 7))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryUiState())

    fun save(entry: RecoveryEntry, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult((repo.save(entry) as? SaveResult.Invalid)?.error) }
    }

    fun delete(date: LocalDate) { viewModelScope.launch { repo.delete(date) } }
}

@Composable
fun RecoveryScreen(onBack: () -> Unit, vm: RecoveryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var date by rememberSaveable(state.today) { mutableStateOf(state.today) }
    var soreness by rememberSaveable { mutableStateOf<Int?>(null) }
    var energy by rememberSaveable { mutableStateOf<Int?>(null) }
    var stress by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val existing = state.entries.firstOrNull { it.date == date }
    LaunchedEffect(date, existing?.id, existing?.soreness, existing?.energy, existing?.stress) {
        soreness = existing?.soreness; energy = existing?.energy; stress = existing?.stress
        note = existing?.note.orEmpty(); error = null
    }

    Scaffold(topBar = { FitTopBar("Recovery", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                val s = state.summary
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard("Soreness", s?.let { "%.1f".format(Locale.US, it.soreness) } ?: "–", Modifier.weight(1f), caption = "1 none · 5 severe")
                    StatCard("Energy", s?.let { "%.1f".format(Locale.US, it.energy) } ?: "–", Modifier.weight(1f), caption = "1 low · 5 high")
                    StatCard("Stress", s?.let { "%.1f".format(Locale.US, it.stress) } ?: "–", Modifier.weight(1f), caption = "1 calm · 5 high")
                }
            }
            item {
                Text(
                    state.summary?.let { "7-day averages from ${it.daysLogged} check-in${if (it.daysLogged == 1) "" else "s"}. These are your own ratings, not a medical score." }
                        ?: "Check in to see 7-day averages. These are your own ratings, not a medical score.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(if (existing != null) "Edit check-in" else "Daily check-in", style = MaterialTheme.typography.titleMedium)
                        DateButton(date, state.today, { date = it }, Modifier.fillMaxWidth())
                        Rating("Muscle soreness (1 none, 5 severe)", soreness) { soreness = it }
                        Rating("Energy (1 low, 5 high)", energy) { energy = it }
                        Rating("Stress (1 calm, 5 high)", stress) { stress = it }
                        FitTextField(note, { note = it }, "Note (optional)", singleLine = false)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        PrimaryButton(if (existing != null) "Update" else "Save", {
                            vm.save(RecoveryEntry(date = date, soreness = soreness!!, energy = energy!!, stress = stress!!, note = note)) { error = it }
                        }, Modifier.fillMaxWidth(), enabled = soreness != null && energy != null && stress != null)
                    }
                }
            }
            item { SectionHeader("Last 30 days") }
            if (state.entries.isEmpty()) item { Text("No check-ins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.entries, key = { it.date.toEpochDay() }) { e ->
                FitCard(onClick = { date = e.date }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Soreness ${e.soreness} · Energy ${e.energy} · Stress ${e.stress}", style = MaterialTheme.typography.titleSmall)
                            Text(friendlyDate(e.date, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.delete(e.date) }) { Icon(Icons.Default.Delete, contentDescription = "Delete check-in") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Rating(label: String, value: Int?, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipGroup(listOf("1", "2", "3", "4", "5"), value?.let { setOf(it.toString()) } ?: emptySet(), { onChange(it.toInt()) })
    }
}
