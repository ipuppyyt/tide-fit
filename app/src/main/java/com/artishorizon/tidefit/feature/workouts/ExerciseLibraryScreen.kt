package com.artishorizon.tidefit.feature.workouts

import com.artishorizon.tidefit.core.designsystem.components.lucide
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.EmptyState
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.domain.model.Exercise

@Composable
fun ExerciseLibraryScreen(onBack: () -> Unit, vm: ExerciseLibraryViewModel = hiltViewModel()) {
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val muscle by vm.muscleFilter.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(topBar = {
        FitTopBar("Exercises", onBack) {
            IconButton({ creating = true }) { Icon(lucide("plus"), contentDescription = "New exercise") }
        }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item { FitTextField(query, vm::setQuery, "Search exercises") }
            item { MuscleFilter(muscle, vm::setMuscle) }
            if (results.isEmpty()) {
                item { EmptyState(lucide("dumbbell"), "No exercises", "Try a different search or create your own.") }
            }
            items(results, key = { it.id }) { e ->
                FitCard(onClick = { editing = e }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(e.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            (listOf(e.primary.label) + e.secondary.map { it.label }).joinToString(", ") + " · " + e.equipment.label + if (e.isCustom) " · Custom" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        ExerciseEditDialog(null, vm.error, onDismiss = { creating = false; vm.clearError() }, onSave = { ex -> vm.save(ex) { creating = false } })
    }
    editing?.let { e ->
        // Built-in exercises can be edited too (e.g. to fix a muscle mapping) or hidden from the library.
        ExerciseEditDialog(
            e, vm.error, onDismiss = { editing = null; vm.clearError() },
            onSave = { ex -> vm.save(ex) { editing = null } },
            onArchive = { editing = null; confirmArchive = e }
        )
    }
    confirmArchive?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmArchive = null },
            title = { Text("Hide ${e.name}?") },
            text = { Text("It will leave the library. Past workouts keep it.") },
            confirmButton = { TextButton({ vm.archive(e.id); confirmArchive = null }) { Text("Hide") } },
            dismissButton = { TextButton({ confirmArchive = null }) { Text("Cancel") } }
        )
    }
}
