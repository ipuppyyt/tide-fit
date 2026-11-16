package com.fitos.app.feature.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitos.app.core.designsystem.Spacing
import com.fitos.app.core.designsystem.components.FitCard
import com.fitos.app.core.designsystem.components.LoadingState
import com.fitos.app.core.designsystem.components.PrimaryButton
import com.fitos.app.core.designsystem.components.SecondaryButton
import com.fitos.app.core.designsystem.components.SectionHeader
import com.fitos.app.domain.model.Routine
import com.fitos.app.domain.model.Workout
import com.fitos.app.domain.model.WeightUnit
import com.fitos.app.domain.model.WorkoutSummary
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.domain.repository.RoutineRepository
import com.fitos.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class WorkoutsUiState(
    val loading: Boolean = true,
    val active: Workout? = null,
    val routines: List<Routine> = emptyList(),
    val recent: List<WorkoutSummary> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG
)

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    routines: RoutineRepository,
    profiles: ProfileRepository
) : ViewModel() {
    val state: StateFlow<WorkoutsUiState> = combine(
        workouts.observeActive(), routines.observeAll(), workouts.observeHistory(), profiles.observeProfile().map { it?.weightUnit ?: WeightUnit.KG }
    ) { active, r, history, unit -> WorkoutsUiState(false, active, r, history.take(3), unit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutsUiState())

    /** Starts (or resumes) a workout and reports its id. */
    fun start(routineId: Long?, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(workouts.start(routineId)) }
    }
}

@Composable
fun WorkoutsScreen(
    contentPadding: PaddingValues,
    onOpenWorkout: (Long) -> Unit,
    onEditRoutine: (Long) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSummary: (Long) -> Unit,
    vm: WorkoutsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }
    val zone = ZoneId.systemDefault()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text("Workouts", style = MaterialTheme.typography.headlineLarge)

        state.active?.let { w ->
            FitCard(onClick = { onOpenWorkout(w.id) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Workout in progress", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(w.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${w.sets.count { it.completed }} of ${w.sets.size} sets done. Tap to continue.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.active == null) {
            PrimaryButton("Start empty workout", { vm.start(null, onOpenWorkout) }, Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton("Exercises", onOpenExercises, Modifier.weight(1f))
            SecondaryButton("History", onOpenHistory, Modifier.weight(1f))
            SecondaryButton("Stats", onOpenStats, Modifier.weight(1f))
        }

        SectionHeader("Routines") {
            androidx.compose.material3.TextButton({ onEditRoutine(0) }) { Text("New routine") }
        }
        if (state.routines.isEmpty()) {
            Text("No routines yet. Create one to start a planned workout in one tap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.routines.forEach { r ->
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${plural(r.exercises.size, "exercise")} · ${plural(r.exercises.sumOf { it.targetSets }, "set")}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.TextButton({ onEditRoutine(r.id) }) { Text("Edit") }
                    }
                    Text(
                        r.exercises.joinToString(", ") { it.exerciseName },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2
                    )
                    SecondaryButton(
                        if (state.active != null) "Finish current workout first" else "Start",
                        { vm.start(r.id, onOpenWorkout) }, Modifier.fillMaxWidth(), enabled = state.active == null
                    )
                }
            }
        }

        SectionHeader("Recent")
        if (state.recent.isEmpty()) {
            Text("Finished workouts will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.recent.forEach { s ->
            FitCard(onClick = { onOpenSummary(s.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(s.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            s.startedAt.atZone(zone).toLocalDate().let { friendlyDate(it, LocalDate.now(zone)) } + " · ${plural(s.setCount, "set")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(formatVolume(s.volumeKg, state.unit), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

fun friendlyDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.year}"
}
