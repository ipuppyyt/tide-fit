package com.fitos.app.feature.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
import com.fitos.app.domain.model.Exercise
import com.fitos.app.domain.model.Routine
import com.fitos.app.domain.model.RoutineExercise
import com.fitos.app.domain.repository.RoutineRepository
import com.fitos.app.navigation.RoutineEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Text-backed row so half-typed numbers are never lost. */
data class RoutineRow(val exerciseId: Long, val name: String, val sets: String, val repsMin: String, val repsMax: String, val rest: String) {
    fun toDomain(): RoutineExercise? {
        val s = sets.trim().toIntOrNull() ?: return null
        val lo = repsMin.trim().toIntOrNull() ?: return null
        val hi = repsMax.trim().toIntOrNull() ?: return null
        val r = rest.trim().toIntOrNull() ?: return null
        return RoutineExercise(exerciseId, name, s, lo, hi, r)
    }
}

data class RoutineEditorState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val name: String = "",
    val notes: String = "",
    val rows: List<RoutineRow> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false
)

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(savedState: SavedStateHandle, private val routines: RoutineRepository) : ViewModel() {
    private val routineId = savedState.toRoute<RoutineEditorRoute>().routineId
    private val _state = MutableStateFlow(RoutineEditorState(isEdit = routineId != 0L))
    val state: StateFlow<RoutineEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = if (routineId != 0L) routines.get(routineId) else null
            _state.update {
                it.copy(
                    loading = false, name = r?.name.orEmpty(), notes = r?.notes.orEmpty(),
                    rows = r?.exercises?.map { e -> RoutineRow(e.exerciseId, e.exerciseName, e.targetSets.toString(), e.repsMin.toString(), e.repsMax.toString(), e.restSeconds.toString()) }.orEmpty()
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun addExercise(e: Exercise) = _state.update { it.copy(rows = it.rows + RoutineRow(e.id, e.name, "3", "8", "12", "90"), error = null) }
    fun remove(i: Int) = _state.update { it.copy(rows = it.rows.filterIndexed { idx, _ -> idx != i }) }
    fun move(i: Int, delta: Int) = _state.update { s ->
        val j = i + delta
        if (j !in s.rows.indices) s else s.copy(rows = s.rows.toMutableList().also { l -> val t = l[i]; l[i] = l[j]; l[j] = t })
    }
    fun edit(i: Int, t: (RoutineRow) -> RoutineRow) = _state.update { s -> s.copy(rows = s.rows.mapIndexed { idx, r -> if (idx == i) t(r) else r }, error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val exercises = s.rows.map { it.toDomain() }
        if (exercises.any { it == null }) { _state.update { it.copy(error = "Every exercise needs whole numbers for sets, reps and rest.") }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { routines.save(Routine(routineId, s.name, s.notes.ifBlank { null }, exercises.filterNotNull())) }
                .onSuccess { _state.update { it.copy(saving = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = (e as? IllegalArgumentException)?.message ?: "Couldn't save. Please try again.") } }
        }
    }

    fun delete() {
        viewModelScope.launch { runCatching { routines.delete(routineId) }.onSuccess { _state.update { it.copy(done = true) } } }
    }
}

@Composable
fun RoutineEditorScreen(onBack: () -> Unit, vm: RoutineEditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onBack() }

    Scaffold(topBar = {
        FitTopBar(if (state.isEdit) "Edit routine" else "New routine", onBack) {
            if (state.isEdit) IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete routine") }
        }
    }) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            FitTextField(state.name, vm::setName, "Routine name")
            FitTextField(state.notes, vm::setNotes, "Notes (optional)", singleLine = false)
            Text("Exercises", style = MaterialTheme.typography.titleMedium)
            if (state.rows.isEmpty()) Text("No exercises yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.rows.forEachIndexed { i, row ->
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            IconButton({ vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move ${row.name} up") }
                            IconButton({ vm.move(i, 1) }, enabled = i < state.rows.lastIndex) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move ${row.name} down") }
                            IconButton({ vm.remove(i) }) { Icon(Icons.Default.Close, contentDescription = "Remove ${row.name}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FitTextField(row.sets, { v -> vm.edit(i) { it.copy(sets = v) } }, "Sets", Modifier.weight(1f), KeyboardType.Number)
                            FitTextField(row.repsMin, { v -> vm.edit(i) { it.copy(repsMin = v) } }, "Reps min", Modifier.weight(1f), KeyboardType.Number)
                            FitTextField(row.repsMax, { v -> vm.edit(i) { it.copy(repsMax = v) } }, "Reps max", Modifier.weight(1f), KeyboardType.Number)
                        }
                        FitTextField(row.rest, { v -> vm.edit(i) { it.copy(rest = v) } }, "Rest", keyboardType = KeyboardType.Number, suffix = "sec")
                    }
                }
            }
            SecondaryButton("Add exercise", { picking = true }, Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save routine", vm::save, Modifier.fillMaxWidth(), enabled = !state.saving)
        }
    }

    if (picking) ExercisePickerDialog(onDismiss = { picking = false }, onPick = { vm.addExercise(it); picking = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this routine?") },
            text = { Text("Workouts you've already done are kept.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
