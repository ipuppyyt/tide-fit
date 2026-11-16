package com.fitos.app.feature.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitos.app.core.designsystem.Spacing
import com.fitos.app.core.designsystem.components.ChipGroup
import com.fitos.app.core.designsystem.components.FitTextField
import com.fitos.app.domain.calc.Units
import com.fitos.app.domain.model.EquipmentType
import com.fitos.app.domain.model.Exercise
import com.fitos.app.domain.model.MuscleGroup
import com.fitos.app.domain.model.WeightUnit
import com.fitos.app.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Formats a stored kilogram value in the user's unit, e.g. "82.5 kg". */
fun formatWeight(kg: Double?, unit: WeightUnit, withUnit: Boolean = true): String {
    if (kg == null) return "–"
    val v = Units.round1(Units.fromKg(kg, unit))
    val s = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(Locale.US, v)
    return if (withUnit) "$s ${unit.label}" else s
}

fun formatVolume(kg: Double, unit: WeightUnit): String {
    val v = Units.fromKg(kg, unit)
    return if (v >= 10_000) "%.1fk %s".format(Locale.US, v / 1000, unit.label) else "%d %s".format(Locale.US, Math.round(v), unit.label)
}

fun plural(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(Locale.US, s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(Locale.US, s / 60, s % 60)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(private val exercises: ExerciseRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<MuscleGroup?>(null)

    val queryText: StateFlow<String> = query
    val muscleFilter: StateFlow<MuscleGroup?> = muscle

    val results: StateFlow<List<Exercise>> = combine(query, muscle) { q, m -> q to m }
        .flatMapLatest { (q, m) -> exercises.search(q, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    fun setQuery(q: String) { query.value = q }
    fun setMuscle(m: MuscleGroup?) { muscle.value = m }
    fun clearError() { error = null }

    /** Saves and reports the new id (or null on failure; [error] then holds the message). */
    fun save(exercise: Exercise, onSaved: (Exercise) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { exercises.save(exercise) }
                .onSuccess { id -> error = null; exercises.get(id)?.let(onSaved) }
                .onFailure { error = (it as? IllegalArgumentException)?.message ?: "Couldn't save. Please try again." }
        }
    }

    fun archive(id: Long) { viewModelScope.launch { exercises.archive(id) } }
}

@Composable
fun MuscleFilter(selected: MuscleGroup?, onSelect: (MuscleGroup?) -> Unit) {
    ChipGroup(
        listOf("All") + MuscleGroup.entries.map { it.label },
        setOf(selected?.label ?: "All"),
        { l -> onSelect(MuscleGroup.entries.firstOrNull { it.label == l }) }
    )
}

@Composable
fun ExerciseEditDialog(initial: Exercise?, error: String?, onDismiss: () -> Unit, onSave: (Exercise) -> Unit, onArchive: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var primary by rememberSaveable { mutableStateOf(initial?.primary ?: MuscleGroup.CHEST) }
    var equipment by rememberSaveable { mutableStateOf(initial?.equipment ?: EquipmentType.BARBELL) }
    var secondary by rememberSaveable { mutableStateOf(initial?.secondary?.joinToString(",") { it.name }.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    val secondarySet = secondary.split(",").filter { it.isNotBlank() }.mapNotNull { s -> MuscleGroup.entries.firstOrNull { it.name == s } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New exercise" else "Edit exercise") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(name, { name = it }, "Name")
                Text("Main muscle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipGroup(MuscleGroup.entries.map { it.label }, setOf(primary.label), { l -> primary = MuscleGroup.entries.first { it.label == l } })
                Text("Also works (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipGroup(
                    MuscleGroup.entries.filter { it != primary }.map { it.label }, secondarySet.map { it.label }.toSet(),
                    { l ->
                        val m = MuscleGroup.entries.first { it.label == l }
                        secondary = (if (m in secondarySet) secondarySet - m else secondarySet + m).joinToString(",") { it.name }
                    }
                )
                Text("Equipment", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipGroup(EquipmentType.entries.map { it.label }, setOf(equipment.label), { l -> equipment = EquipmentType.entries.first { it.label == l } })
                FitTextField(notes, { notes = it }, "Notes (optional)", singleLine = false)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton({
                onSave(
                    (initial ?: Exercise(name = "", primary = primary, equipment = equipment)).copy(
                        name = name, primary = primary, equipment = equipment, secondary = secondarySet.filter { it != primary }, notes = notes.ifBlank { null }
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onArchive != null) TextButton(onArchive) { Text("Hide", color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Search-and-pick dialog used when adding exercises to routines and workouts. */
@Composable
fun ExercisePickerDialog(onDismiss: () -> Unit, onPick: (Exercise) -> Unit, vm: ExerciseLibraryViewModel = hiltViewModel()) {
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val muscle by vm.muscleFilter.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(query, vm::setQuery, "Search")
                Column(Modifier.heightIn(max = 110.dp).verticalScroll(rememberScrollState())) { MuscleFilter(muscle, vm::setMuscle) }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    if (results.isEmpty()) item { Text("No exercises match.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(results, key = { it.id }) { e ->
                        TextButton({ onPick(e) }, Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.name)
                                Text(e.primary.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ creating = true }) { Text("New exercise") } },
        dismissButton = { TextButton(onDismiss) { Text("Close") } }
    )
    if (creating) {
        ExerciseEditDialog(null, vm.error, onDismiss = { creating = false; vm.clearError() }, onSave = { ex -> vm.save(ex) { saved -> creating = false; onPick(saved) } })
    }
}
