package com.artishorizon.tidefit.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.ChipGroup
import com.artishorizon.tidefit.core.designsystem.components.EmptyState
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.designsystem.components.StatCard
import com.artishorizon.tidefit.domain.calc.BodyCalc
import com.artishorizon.tidefit.domain.model.Supplement
import com.artishorizon.tidefit.domain.model.SupplementLog
import com.artishorizon.tidefit.domain.model.TimeOfDay
import com.artishorizon.tidefit.domain.repository.SaveResult
import com.artishorizon.tidefit.domain.repository.SupplementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class SupplementsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.EPOCH,
    val supplements: List<Supplement> = emptyList(),
    val takenToday: Set<Long> = emptySet(),
    val week: BodyCalc.Adherence? = null,
    val month: BodyCalc.Adherence? = null
)

@HiltViewModel
class SupplementsViewModel @Inject constructor(private val repo: SupplementRepository, clock: Clock) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<SupplementsUiState> = combine(repo.observeAll(), repo.observeLogs(today.minusDays(29), today)) { list, logs: List<SupplementLog> ->
        SupplementsUiState(
            false, today, list, logs.filter { it.date == today }.map { it.supplementId }.toSet(),
            BodyCalc.supplementAdherence(list, logs, today, 7), BodyCalc.supplementAdherence(list, logs, today, 30)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplementsUiState())

    fun toggle(id: Long, taken: Boolean) { viewModelScope.launch { repo.setTaken(id, today, taken) } }

    fun save(s: Supplement, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult((repo.save(s) as? SaveResult.Invalid)?.error) }
    }

    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
}

@Composable
fun SupplementsScreen(onBack: () -> Unit, vm: SupplementsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Supplement?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val active = state.supplements.filter { it.active }
    val inactive = state.supplements.filter { !it.active }

    Scaffold(topBar = {
        FitTopBar("Supplements", onBack, actions = {
            TextButton({ adding = true }) { Text("Add") }
        })
    }) { padding ->
        if (state.supplements.isEmpty() && !state.loading) {
            EmptyState(Icons.Default.Add, "No supplements yet", "Add what you take and tick it off each day. Adherence is tracked from the day you add it.", Modifier.padding(padding), "Add supplement") { adding = true }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard("Today", "${active.count { it.id in state.takenToday }} / ${active.size}", Modifier.weight(1f), caption = "taken")
                    StatCard("7 days", state.week?.let { "${(it.fraction * 100).toInt()}%" } ?: "–", Modifier.weight(1f), caption = state.week?.let { "${it.taken} of ${it.expected} doses" } ?: "no data yet")
                }
            }
            item { SectionHeader("Today") }
            if (active.isEmpty()) item { Text("No active supplements.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(active, key = { it.id }) { s ->
                FitCard(onClick = { editing = s }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.id in state.takenToday, onCheckedChange = { vm.toggle(s.id, it) })
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(s.dose, s.timeOfDay.label).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (inactive.isNotEmpty()) {
                item { SectionHeader("Paused") }
                items(inactive, key = { it.id }) { s ->
                    FitCard(onClick = { editing = s }) {
                        Text("${s.name}${s.dose?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(
                    "Paused supplements aren't counted in adherence. This is a personal checklist, not medical advice.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (adding) SupplementDialog(null, { adding = false }, onDelete = null) { s, done -> vm.save(s) { err -> if (err == null) adding = false; done(err) } }
    editing?.let { s ->
        SupplementDialog(s, { editing = null }, onDelete = { vm.delete(s.id); editing = null }) { upd, done -> vm.save(upd) { err -> if (err == null) editing = null; done(err) } }
    }
}

@Composable
private fun SupplementDialog(
    existing: Supplement?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (Supplement, (String?) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var dose by rememberSaveable { mutableStateOf(existing?.dose.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(existing?.timeOfDay ?: TimeOfDay.ANYTIME) }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add supplement" else "Edit supplement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(name, { name = it; error = null }, "Name", error = error)
                FitTextField(dose, { dose = it }, "Dose (optional, e.g. 5 g)")
                ChipGroup(TimeOfDay.entries.map { it.label }, setOf(time.label), { l -> time = TimeOfDay.entries.first { it.label == l } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
                onDelete?.let { TextButton(it) { Text("Delete supplement and its history", color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton({
                onSave((existing ?: Supplement(name = name)).copy(name = name, dose = dose, timeOfDay = time, active = active)) { err -> error = err }
            }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
