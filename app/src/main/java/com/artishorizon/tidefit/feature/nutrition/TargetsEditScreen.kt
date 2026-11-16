package com.artishorizon.tidefit.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.domain.calc.TargetCalculator
import com.artishorizon.tidefit.domain.calc.TargetInput
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.ValueSource
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TargetsForm(
    val calories: String = "", val protein: String = "", val carbs: String = "",
    val fat: String = "", val fibre: String = "", val water: String = ""
) {
    /** Whole numbers only; calories and water must be positive, macros may be zero. */
    fun toTargets(): Pair<DailyTargets?, String?> {
        fun n(s: String) = s.trim().toIntOrNull()?.takeIf { it >= 0 }
        val kcal = n(calories)?.takeIf { it > 0 } ?: return null to "Calories must be a whole number greater than zero."
        val p = n(protein) ?: return null to "Protein must be a whole number."
        val c = n(carbs) ?: return null to "Carbohydrates must be a whole number."
        val f = n(fat) ?: return null to "Fat must be a whole number."
        val fi = n(fibre) ?: return null to "Fibre must be a whole number."
        val w = n(water)?.takeIf { it > 0 } ?: return null to "Water must be a whole number of ml greater than zero."
        return DailyTargets(kcal, p, c, f, fi, w, ValueSource.USER) to null
    }

    companion object {
        fun from(t: DailyTargets) = TargetsForm(
            t.caloriesKcal.toString(), t.proteinG.toString(), t.carbohydratesG.toString(),
            t.fatG.toString(), t.fibreG.toString(), t.waterMl.toString()
        )
    }
}

data class TargetsUiState(
    val loading: Boolean = true,
    val form: TargetsForm = TargetsForm(),
    val canSuggest: Boolean = false,
    val note: String? = null,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class TargetsEditViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow(TargetsUiState())
    val state: StateFlow<TargetsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = profiles.observeCurrentTargets().first()
            val profile = profiles.observeProfile().first()
            _state.update { TargetsUiState(loading = false, form = current?.let(TargetsForm::from) ?: TargetsForm(), canSuggest = profile != null) }
        }
    }

    fun update(t: (TargetsForm) -> TargetsForm) = _state.update { it.copy(form = t(it.form), error = null) }

    fun useSuggested() {
        viewModelScope.launch {
            val p = profiles.observeProfile().first() ?: return@launch
            val s = TargetCalculator.suggest(TargetInput(p.age, p.sex, p.heightCm, p.currentWeightKg, p.activityLevel, p.goal))
            _state.update { it.copy(form = TargetsForm.from(s.targets), note = "Estimate from your profile: ${s.explanation.take(3).joinToString(" ")} Saving marks these as your own targets.", error = null) }
        }
    }

    fun save() {
        val (targets, err) = _state.value.form.toTargets()
        if (targets == null) { _state.update { it.copy(error = err) }; return }
        viewModelScope.launch {
            runCatching { profiles.saveTargets(targets) }
                .onSuccess { _state.update { it.copy(done = true) } }
                .onFailure { _state.update { it.copy(error = "Couldn't save. Please try again.") } }
        }
    }
}

@Composable
fun TargetsEditScreen(onBack: () -> Unit, viewModel: TargetsEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onBack() }
    Scaffold(topBar = { FitTopBar("Daily targets", onBack) }) { padding ->
        if (state.loading) return@Scaffold
        val f = state.form
        val u = viewModel::update
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "Targets are a starting point. Adjust them from your real weight trend. Earlier days keep the targets they had.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FitTextField(f.calories, { v -> u { it.copy(calories = v) } }, "Calories", keyboardType = KeyboardType.Number, suffix = "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(f.protein, { v -> u { it.copy(protein = v) } }, "Protein", Modifier.weight(1f), KeyboardType.Number, "g")
                FitTextField(f.carbs, { v -> u { it.copy(carbs = v) } }, "Carbs", Modifier.weight(1f), KeyboardType.Number, "g")
                FitTextField(f.fat, { v -> u { it.copy(fat = v) } }, "Fat", Modifier.weight(1f), KeyboardType.Number, "g")
            }
            FitTextField(f.fibre, { v -> u { it.copy(fibre = v) } }, "Fibre", keyboardType = KeyboardType.Number, suffix = "g")
            FitTextField(f.water, { v -> u { it.copy(water = v) } }, "Water", keyboardType = KeyboardType.Number, suffix = "ml")
            state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.canSuggest) SecondaryButton("Use suggested estimate", viewModel::useSuggested, Modifier.fillMaxWidth())
            PrimaryButton("Save targets", viewModel::save, Modifier.fillMaxWidth())
        }
    }
}
