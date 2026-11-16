package com.fitos.app.feature.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.fitos.app.core.designsystem.Spacing
import com.fitos.app.core.designsystem.components.FitCard
import com.fitos.app.core.designsystem.components.FitTextField
import com.fitos.app.core.designsystem.components.FitTopBar
import com.fitos.app.core.designsystem.components.LoadingState
import com.fitos.app.core.designsystem.components.PrimaryButton
import com.fitos.app.core.designsystem.components.SecondaryButton
import com.fitos.app.core.util.parseDecimal
import com.fitos.app.domain.calc.TrainingCalc
import com.fitos.app.domain.calc.Units
import com.fitos.app.domain.model.Exercise
import com.fitos.app.domain.model.WeightUnit
import com.fitos.app.domain.model.Workout
import com.fitos.app.domain.model.WorkoutSet
import com.fitos.app.domain.repository.FinishResult
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.domain.repository.WorkoutRepository
import com.fitos.app.navigation.ActiveWorkoutRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class ActiveWorkoutUiState(
    val loading: Boolean = true,
    val workout: Workout? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val restEndsAtMs: Long? = null,
    val message: String? = null
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val workouts: WorkoutRepository,
    profiles: ProfileRepository,
    private val clock: Clock
) : ViewModel() {
    val sessionId = savedState.toRoute<ActiveWorkoutRoute>().sessionId

    private val restEndsAt = MutableStateFlow<Long?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<ActiveWorkoutUiState> = combine(
        workouts.observe(sessionId), profiles.observeProfile().map { it?.weightUnit ?: WeightUnit.KG }, restEndsAt, message
    ) { w, unit, rest, msg -> ActiveWorkoutUiState(false, w, unit, rest, msg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { message.value = (it as? IllegalArgumentException)?.message ?: "Something went wrong. Please try again." }
        }
    }

    fun clearMessage() { message.value = null }

    fun updateSet(set: WorkoutSet) = launchSafe { workouts.updateSet(set) }

    fun toggleComplete(set: WorkoutSet) {
        if (!set.completed && set.reps == null) { message.value = "Enter the reps first."; return }
        launchSafe {
            workouts.updateSet(set.copy(completed = !set.completed))
            if (!set.completed && !set.isWarmup && set.restSeconds > 0) restEndsAt.value = clock.millis() + set.restSeconds * 1000L
        }
    }

    fun addSet(exerciseId: Long) = launchSafe { workouts.addSet(sessionId, exerciseId) }
    fun deleteSet(id: Long) = launchSafe { workouts.deleteSet(id) }
    fun removeExercise(id: Long) = launchSafe { workouts.removeExercise(sessionId, id) }
    fun addExercise(e: Exercise) = launchSafe { workouts.addExercise(sessionId, e) }
    fun setNotes(text: String) = launchSafe { workouts.updateNotes(sessionId, text) }

    fun skipRest() { restEndsAt.value = null }
    fun extendRest() { restEndsAt.value = (restEndsAt.value ?: clock.millis()) + 30_000 }

    fun finish(onDone: (FinishResult) -> Unit) {
        viewModelScope.launch { onDone(workouts.finish(sessionId)) }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch { workouts.discard(sessionId); onDone() }
    }
}

@Composable
fun ActiveWorkoutScreen(onClose: () -> Unit, onFinished: (Long) -> Unit, vm: ActiveWorkoutViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(500) } }

    val workout = state.workout
    Scaffold(topBar = {
        FitTopBar(workout?.name ?: "Workout", onClose) {
            if (workout != null && workout.isActive) {
                Text(
                    formatDuration((nowMs - workout.startedAt.toEpochMilli()) / 1000),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = Spacing.md)
                )
            }
        }
    }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            workout == null -> Column(Modifier.padding(padding).padding(Spacing.xl)) {
                Text("This workout no longer exists.")
                SecondaryButton("Back", onClose)
            }
            else -> {
                val rest = state.restEndsAtMs?.let { (it - nowMs) / 1000 }?.takeIf { it > 0 }
                val groups = workout.sets.groupBy { it.exerciseId to it.exerciseOrder }.toList().sortedBy { it.first.second }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).imePadding(),
                    contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    if (rest != null) item(key = "rest") {
                        FitCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Rest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatDuration(rest), style = MaterialTheme.typography.headlineMedium)
                                }
                                Row {
                                    TextButton(vm::extendRest) { Text("+30s") }
                                    TextButton(vm::skipRest) { Text("Skip") }
                                }
                            }
                        }
                    }
                    if (groups.isEmpty()) item(key = "empty") {
                        Text("Add an exercise to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(groups, key = { it.first.first }) { (key, sets) ->
                        ExerciseBlock(sets.first().exerciseName, key.first, sets, state.unit, vm)
                    }
                    state.message?.let { msg -> item(key = "msg") { Text(msg, color = MaterialTheme.colorScheme.error) } }
                    item(key = "add") { SecondaryButton("Add exercise", { picking = true }, Modifier.fillMaxWidth()) }
                    item(key = "notes") {
                        var notes by rememberSaveable(workout.id) { mutableStateOf(workout.notes.orEmpty()) }
                        FitTextField(notes, { notes = it; vm.setNotes(it) }, "Workout notes (optional)", singleLine = false)
                    }
                    item(key = "finish") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            PrimaryButton("Finish workout", { confirmFinish = true }, Modifier.fillMaxWidth())
                            TextButton({ confirmDiscard = true }, Modifier.fillMaxWidth()) { Text("Discard workout", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (picking) ExercisePickerDialog(onDismiss = { picking = false }, onPick = { vm.addExercise(it); picking = false })

    if (confirmFinish && workout != null) {
        val done = workout.sets.count { it.completed }
        val pending = workout.sets.size - done
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = {
                Text(
                    if (done == 0) "No sets are marked done, so this workout will be discarded."
                    else "$done sets done." + if (pending > 0) " $pending unfinished sets will be removed." else ""
                )
            },
            confirmButton = {
                TextButton({
                    confirmFinish = false
                    vm.finish { result -> if (result == FinishResult.SAVED) onFinished(vm.sessionId) else onClose() }
                }) { Text("Finish") }
            },
            dismissButton = { TextButton({ confirmFinish = false }) { Text("Keep going") } }
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("Nothing from it will be saved. This can't be undone.") },
            confirmButton = { TextButton({ confirmDiscard = false; vm.discard(onClose) }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDiscard = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ExerciseBlock(name: String, exerciseId: Long, sets: List<WorkoutSet>, unit: WeightUnit, vm: ActiveWorkoutViewModel) {
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton({ vm.removeExercise(exerciseId) }) { Icon(Icons.Default.Close, contentDescription = "Remove $name") }
            }
            sets.forEach { SetRow(it, unit, vm) }
            SecondaryButton("Add set", { vm.addSet(exerciseId) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetRow(set: WorkoutSet, unit: WeightUnit, vm: ActiveWorkoutViewModel) {
    var weightText by rememberSaveable(set.id) { mutableStateOf(set.weightKg?.let { formatWeight(it, unit, withUnit = false) }.orEmpty()) }
    var repsText by rememberSaveable(set.id) { mutableStateOf(set.reps?.toString().orEmpty()) }

    fun commit(w: String, r: String) {
        val weight = if (w.isBlank()) null else w.parseDecimal()?.let { Units.toKg(it, unit) } ?: return
        val reps = if (r.isBlank()) null else r.trim().toIntOrNull() ?: return
        vm.updateSet(set.copy(weightKg = weight, reps = reps))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (set.isWarmup) "W" else set.setNumber.toString(),
            Modifier.size(32.dp).clickable { vm.updateSet(set.copy(isWarmup = !set.isWarmup)) }
                .semantics { contentDescription = "Set ${set.setNumber}${if (set.isWarmup) ", warm-up" else ""}. Tap to toggle warm-up." },
            style = MaterialTheme.typography.titleMedium,
            color = if (set.isWarmup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        FitTextField(weightText, { weightText = it; commit(it, repsText) }, unit.label, Modifier.weight(1f), KeyboardType.Decimal)
        FitTextField(repsText, { repsText = it; commit(weightText, it) }, "Reps", Modifier.weight(1f), KeyboardType.Number)
        FilledIconToggleButton(checked = set.completed, onCheckedChange = { vm.toggleComplete(set) }) {
            Icon(Icons.Default.Check, contentDescription = if (set.completed) "Set ${set.setNumber} done" else "Mark set ${set.setNumber} done")
        }
        IconButton({ vm.deleteSet(set.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete set ${set.setNumber}") }
    }
}
