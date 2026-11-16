package com.artishorizon.tidefit.feature.coach

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.domain.coaching.CoachingImport
import com.artishorizon.tidefit.domain.coaching.Suggestion
import com.artishorizon.tidefit.domain.coaching.TargetsDto
import com.artishorizon.tidefit.domain.repository.CoachingPreview
import com.artishorizon.tidefit.domain.repository.CoachingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachUiState(
    val days: Int = 14,
    val busy: Boolean = false,
    val pasted: String = "",
    val preview: CoachingPreview? = null,
    val applyTargets: Boolean = true,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class CoachViewModel @Inject constructor(private val repo: CoachingRepository) : ViewModel() {
    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    fun setDays(d: Int) = _state.update { it.copy(days = d, message = null) }
    fun setPasted(t: String) = _state.update { it.copy(pasted = t, preview = null, error = null, message = null) }
    fun setApplyTargets(v: Boolean) = _state.update { it.copy(applyTargets = v) }
    fun discardPreview() = _state.update { it.copy(preview = null, pasted = "") }

    fun export(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { repo.buildExportText(_state.value.days) }
                .onSuccess { onReady(it); _state.update { s -> s.copy(busy = false) } }
                .onFailure { _state.update { s -> s.copy(busy = false, error = "Couldn't build the export. Please try again.") } }
        }
    }

    fun showMessage(m: String) = _state.update { it.copy(message = m) }

    fun review() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            val p = repo.preview(_state.value.pasted)
            _state.update { it.copy(busy = false, preview = p, applyTargets = true) }
        }
    }

    fun confirm() {
        val s = _state.value
        if (s.preview !is CoachingPreview.Ready) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repo.confirm(s.pasted, s.applyTargets && s.preview.parsed.targets != null) }
                .onSuccess {
                    _state.update { st ->
                        CoachUiState(days = st.days, message = if (st.applyTargets && s.preview.parsed.targets != null)
                            "New targets applied. You can roll them back from History." else "Saved to History. No targets were changed.")
                    }
                }
                .onFailure { e -> _state.update { st -> st.copy(busy = false, error = e.message ?: "Couldn't save the import.") } }
        }
    }
}

@Composable
fun CoachScreen(onBack: () -> Unit, onOpenHistory: () -> Unit, vm: CoachViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = { FitTopBar("ChatGPT coaching", onBack, actions = { TextButton(onOpenHistory) { Text("History") } }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "TIDE Fit never connects to the internet. You copy your data into ChatGPT yourself, then paste its reply back here. " +
                    "Nothing changes until you approve it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            SectionHeader("1. Export")
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Include the last", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipGroup(listOf("7 days", "14 days", "28 days"), setOf("${state.days} days"), { vm.setDays(it.substringBefore(' ').toInt()) })
                    Text(
                        "Includes your profile, targets, weight, logged food and water, workouts, sleep and recovery. Days you didn't log are marked as not logged.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PrimaryButton("Copy", {
                            vm.export { text ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("TIDE Fit export", text))
                                vm.showMessage("Copied. Paste it into ChatGPT.")
                            }
                        }, Modifier.weight(1f), enabled = !state.busy)
                        SecondaryButton("Share", {
                            vm.export { text ->
                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                                context.startActivity(Intent.createChooser(send, "Share export"))
                            }
                        }, Modifier.weight(1f), enabled = !state.busy)
                    }
                }
            }

            val preview = state.preview
            if (preview == null) {
                SectionHeader("2. Import ChatGPT's reply")
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FitTextField(state.pasted, vm::setPasted, "Paste the JSON reply", Modifier.fillMaxWidth(), singleLine = false, maxLines = 6)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SecondaryButton("Paste", {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                vm.setPasted(cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty())
                            }, Modifier.weight(1f))
                            PrimaryButton("Review", vm::review, Modifier.weight(1f), enabled = state.pasted.isNotBlank() && !state.busy)
                        }
                    }
                }
            } else {
                PreviewSection(preview, state, vm)
            }
        }
    }
}

@Composable
private fun PreviewSection(preview: CoachingPreview, state: CoachUiState, vm: CoachViewModel) {
    when (preview) {
        is CoachingPreview.Rejected -> {
            SectionHeader("Can't import this")
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    preview.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Text("Nothing was changed. Fix the reply (or ask ChatGPT to resend it as valid JSON) and review again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SecondaryButton("Edit reply", { vm.setPasted(state.pasted) }, Modifier.fillMaxWidth())
        }
        is CoachingPreview.Duplicate -> {
            SectionHeader("Already imported")
            FitCard { Text("This exact response was imported before (see History). Importing it again would do nothing new.") }
            SecondaryButton("Back", vm::discardPreview, Modifier.fillMaxWidth())
        }
        is CoachingPreview.Ready -> ReadyPreview(preview, state, vm)
    }
}

@Composable
private fun ReadyPreview(preview: CoachingPreview.Ready, state: CoachUiState, vm: CoachViewModel) {
    val u = preview.parsed.update
    SectionHeader("Review before anything changes")
    if (preview.parsed.warnings.isNotEmpty()) {
        FitCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Check these", style = MaterialTheme.typography.titleSmall)
                preview.parsed.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    u.analysis?.summary?.takeIf { it.isNotBlank() }?.let { s ->
        FitCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Analysis", style = MaterialTheme.typography.titleSmall)
                Text(s, style = MaterialTheme.typography.bodyMedium)
                u.analysis?.observations?.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    preview.parsed.targets?.let { t ->
        FitCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Proposed daily targets", style = MaterialTheme.typography.titleSmall)
                TargetRow("Calories", preview.currentTargets?.caloriesKcal, t.caloriesKcal, "kcal")
                TargetRow("Protein", preview.currentTargets?.proteinG, t.proteinG, "g")
                TargetRow("Carbs", preview.currentTargets?.carbsG, t.carbsG, "g")
                TargetRow("Fat", preview.currentTargets?.fatG, t.fatG, "g")
                TargetRow("Fibre", preview.currentTargets?.fibreG, t.fibreG, "g")
                TargetRow("Water", preview.currentTargets?.waterMl, t.waterMl, "ml")
                u.updatedTargets?.reason?.takeIf { it.isNotBlank() }?.let { Text("Why: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(state.applyTargets, vm::setApplyTargets)
                    Text("Apply these targets")
                }
            }
        }
    } ?: FitCard { Text("This reply doesn't change your targets.", color = MaterialTheme.colorScheme.onSurfaceVariant) }

    SuggestionCard("Recommendations", u.recommendations)
    SuggestionCard("Workout suggestions", u.workoutSuggestions)
    SuggestionCard("Habit suggestions", u.habitSuggestions)
    u.coachNotes?.takeIf { it.isNotBlank() }?.let { FitCard { Column { Text("Coach notes", style = MaterialTheme.typography.titleSmall); Text(it, style = MaterialTheme.typography.bodyMedium) } } }
    Text(
        "Suggestions are advice to read, not changes. Only the targets above can be applied automatically.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SecondaryButton("Discard", vm::discardPreview, Modifier.weight(1f))
        PrimaryButton(if (preview.parsed.targets != null && state.applyTargets) "Apply & save" else "Save to history", vm::confirm, Modifier.weight(1f), enabled = !state.busy)
    }
}

@Composable
private fun TargetRow(label: String, old: Int?, new: Int, unit: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        val changed = old != null && old != new
        Text(
            if (old == null) "$new $unit" else if (changed) "$old → $new $unit" else "$new $unit (unchanged)",
            style = MaterialTheme.typography.bodyMedium,
            color = if (changed || old == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuggestionCard(title: String, items: List<Suggestion>) {
    if (items.isEmpty()) return
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            items.forEach { s ->
                Column {
                    s.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    s.detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
