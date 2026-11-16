package com.fitos.app.feature.workouts

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.fitos.app.core.designsystem.Spacing
import com.fitos.app.core.designsystem.components.EmptyState
import com.fitos.app.core.designsystem.components.FitCard
import com.fitos.app.core.designsystem.components.FitTopBar
import com.fitos.app.core.designsystem.components.LoadingState
import com.fitos.app.domain.calc.TrainingCalc
import com.fitos.app.domain.model.WeightUnit
import com.fitos.app.domain.model.Workout
import com.fitos.app.domain.model.WorkoutSummary
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.domain.repository.WorkoutRepository
import com.fitos.app.navigation.WorkoutDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HistoryUiState(val loading: Boolean = true, val items: List<WorkoutSummary> = emptyList(), val unit: WeightUnit = WeightUnit.KG)

@HiltViewModel
class HistoryViewModel @Inject constructor(workouts: WorkoutRepository, profiles: ProfileRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = combine(workouts.observeHistory(), profiles.observeProfile().map { it?.weightUnit ?: WeightUnit.KG }) { h, u ->
        HistoryUiState(false, h, u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    Scaffold(topBar = { FitTopBar("History", onBack) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(Icons.Default.History, "No workouts yet", "Finished workouts are listed here.", Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(state.items, key = { it.id }) { s ->
                    FitCard(onClick = { onOpen(s.id) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    friendlyDate(s.startedAt.atZone(zone).toLocalDate(), today) + " · ${plural(s.exerciseCount, "exercise")} · ${plural(s.setCount, "set")}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(formatVolume(s.volumeKg, state.unit), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

data class DetailUiState(
    val loading: Boolean = true,
    val workout: Workout? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val records: List<TrainingCalc.NewRecord> = emptyList(),
    val deleted: Boolean = false
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val workouts: WorkoutRepository,
    profiles: ProfileRepository
) : ViewModel() {
    private val id = savedState.toRoute<WorkoutDetailRoute>().sessionId
    private val deleted = kotlinx.coroutines.flow.MutableStateFlow(false)

    val state: StateFlow<DetailUiState> = combine(
        workouts.observe(id), workouts.observeFinishedSets(), profiles.observeProfile().map { it?.weightUnit ?: WeightUnit.KG }, deleted
    ) { w, all, unit, gone ->
        // "Before" = finished workouts created earlier (ids grow with start time; only one workout can be active at a time).
        val records = if (w == null) emptyList() else TrainingCalc.newRecords(all.filter { it.sessionId < id }, w.sets)
        DetailUiState(false, w, unit, records, gone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun delete() { viewModelScope.launch { workouts.delete(id); deleted.value = true } }
}

@Composable
fun WorkoutDetailScreen(onBack: () -> Unit, vm: WorkoutDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val zone = ZoneId.systemDefault()

    Scaffold(topBar = {
        FitTopBar(state.workout?.name ?: "Workout", onBack) {
            if (state.workout != null) IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete workout") }
        }
    }) { padding ->
        val w = state.workout
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            w == null -> EmptyState(Icons.Default.History, "Not found", "This workout no longer exists.", Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    val duration = w.endedAt?.let { Duration.between(w.startedAt, it).seconds }
                    FitCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(friendlyDate(w.startedAt.atZone(zone).toLocalDate(), LocalDate.now(zone)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatVolume(TrainingCalc.volumeKg(w.sets), state.unit) + " total volume", style = MaterialTheme.typography.titleLarge)
                            Text(
                                plural(TrainingCalc.workingSetCount(w.sets), "working set") + (duration?.let { " · ${formatDuration(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            w.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                if (state.records.isNotEmpty()) item {
                    FitCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("New records", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            state.records.forEach { r ->
                                val v = if (r.kind == TrainingCalc.PrKind.REPS) "${r.value.toInt()} reps" else formatWeight(r.value, state.unit)
                                Text("${r.exerciseName}: ${r.kind.label.lowercase()} $v", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                val groups = w.sets.groupBy { it.exerciseId to it.exerciseOrder }.toList().sortedBy { it.first.second }
                items(groups, key = { it.first.first }) { (_, sets) ->
                    FitCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(sets.first().exerciseName, style = MaterialTheme.typography.titleSmall)
                            sets.forEach { s ->
                                val weight = if (s.weightKg == null) "bodyweight" else formatWeight(s.weightKg, state.unit)
                                Text(
                                    "${if (s.isWarmup) "W" else s.setNumber.toString()}  $weight × ${s.reps ?: "–"}" + if (s.isWarmup) "  (warm-up)" else "",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("It will be removed from your history and statistics. This can't be undone.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
