package com.artishorizon.tidefit.feature.workouts

import com.artishorizon.tidefit.core.designsystem.components.lucide
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.components.PhotoCard
import com.artishorizon.tidefit.core.designsystem.components.IconBadge
import com.artishorizon.tidefit.core.designsystem.components.QuickTile
import com.artishorizon.tidefit.core.designsystem.components.pressable
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
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.domain.model.Routine
import com.artishorizon.tidefit.domain.model.Workout
import com.artishorizon.tidefit.domain.model.WeightUnit
import com.artishorizon.tidefit.domain.model.WorkoutSummary
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.RoutineRepository
import com.artishorizon.tidefit.domain.repository.WorkoutRepository
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
        Column {
            Text("Train", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Workouts", style = MaterialTheme.typography.headlineLarge)
        }

        // Hero: resume the running session, or invite the next one.
        val active = state.active
        PhotoCard(com.artishorizon.tidefit.R.drawable.photo_barbell, height = 300.dp, onClick = if (active != null) ({ onOpenWorkout(active.id) }) else null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (active != null) {
                    val done = active.sets.count { it.completed }
                    Text("In progress", style = MaterialTheme.typography.labelLarge)
                    Text(active.name, style = MaterialTheme.typography.displayMedium)
                    Text("$done of ${active.sets.size} sets done", style = MaterialTheme.typography.bodyMedium)
                    DarkPill("Continue workout") { onOpenWorkout(active.id) }
                } else {
                    Text("Ready when you are", style = MaterialTheme.typography.labelLarge)
                    Text("Start training", style = MaterialTheme.typography.displayMedium)
                    Text("Log sets, track rest and beat your last session.", style = MaterialTheme.typography.bodyMedium)
                    DarkPill("Start empty workout") { vm.start(null, onOpenWorkout) }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickTile(lucide("dumbbell"), "Exercises", onOpenExercises)
            QuickTile(lucide("clock"), "History", onOpenHistory)
            QuickTile(lucide("chart-no-axes-combined"), "Stats", onOpenStats)
            QuickTile(lucide("plus"), "Routine", onClick = { onEditRoutine(0) })
        }

        SectionHeader("Routines")
        if (state.routines.isEmpty()) {
            FitCard(onClick = { onEditRoutine(0) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(lucide("plus"), MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Create your first routine", style = MaterialTheme.typography.titleMedium)
                        Text("No routines yet. Create one to start a planned workout in one tap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        state.routines.forEach { r ->
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(lucide("dumbbell"), MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${plural(r.exercises.size, "exercise")} · ${plural(r.exercises.sumOf { it.targetSets }, "set")}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        com.artishorizon.tidefit.core.designsystem.components.TextButton({ onEditRoutine(r.id) }) { Text("Edit") }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(lucide("clock"), FitTheme.colors.water)
                    Column(Modifier.weight(1f)) {
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
        Spacer(Modifier.height(96.dp))
    }
}

/** Dark pill that sits on the lime hero card. */
@Composable
private fun DarkPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(Radii.pill).background(FitTheme.colors.hero).pressable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = FitTheme.colors.onHero) }
}

fun friendlyDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.year}"
}
