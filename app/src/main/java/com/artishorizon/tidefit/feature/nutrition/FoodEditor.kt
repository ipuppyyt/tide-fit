package com.artishorizon.tidefit.feature.nutrition

import com.artishorizon.tidefit.core.designsystem.components.lucide
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import androidx.compose.material.icons.filled.Delete
import com.artishorizon.tidefit.core.designsystem.components.AlertDialog
import androidx.compose.material3.Icon
import com.artishorizon.tidefit.core.designsystem.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.artishorizon.tidefit.core.designsystem.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.ChipGroup
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.domain.model.Food
import com.artishorizon.tidefit.domain.model.NutritionSource
import com.artishorizon.tidefit.domain.repository.FoodRepository
import com.artishorizon.tidefit.navigation.FoodEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodEditorUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val form: FoodForm = FoodForm(),
    val error: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false
)

@HiltViewModel
class FoodEditorViewModel @Inject constructor(savedState: SavedStateHandle, private val foods: FoodRepository) : ViewModel() {
    private val foodId = savedState.toRoute<FoodEditorRoute>().foodId
    private var existing: Food? = null

    private val _state = MutableStateFlow(FoodEditorUiState(isEdit = foodId != 0L))
    val state: StateFlow<FoodEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            existing = if (foodId != 0L) foods.get(foodId) else null
            _state.update { it.copy(loading = false, form = existing?.let(FoodForm::from) ?: FoodForm()) }
        }
    }

    fun update(transform: (FoodForm) -> FoodForm) = _state.update { it.copy(form = transform(it.form), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        when (val r = s.form.toFood(existing)) {
            is FoodForm.Result.Error -> _state.update { it.copy(error = r.message) }
            is FoodForm.Result.Ok -> {
                _state.update { it.copy(saving = true) }
                viewModelScope.launch {
                    runCatching { foods.save(r.food) }
                        .onSuccess { _state.update { it.copy(saving = false, done = true) } }
                        .onFailure { _state.update { it.copy(saving = false, error = "Couldn't save. Please try again.") } }
                }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { foods.delete(foodId) }.onSuccess { _state.update { it.copy(done = true) } }
        }
    }
}

@Composable
fun FoodEditorScreen(onBack: () -> Unit, viewModel: FoodEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onBack() }

    Scaffold(topBar = {
        FitTopBar(if (state.isEdit) "Edit food" else "New food", onBack) {
            if (state.isEdit) IconButton({ confirmDelete = true }) { Icon(lucide("trash"), contentDescription = "Delete food") }
        }
    }) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        val f = state.form
        val u = viewModel::update
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            FitTextField(f.name, { v -> u { it.copy(name = v) } }, "Name")
            FitTextField(f.brand, { v -> u { it.copy(brand = v) } }, "Brand (optional)")
            Text("Serving", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(f.servingSize, { v -> u { it.copy(servingSize = v) } }, "Size", Modifier.weight(1f), KeyboardType.Decimal)
                FitTextField(f.servingUnit, { v -> u { it.copy(servingUnit = v) } }, "Unit", Modifier.weight(1f))
            }
            FitTextField(f.servingWeightG, { v -> u { it.copy(servingWeightG = v) } }, "Weight of one serving", keyboardType = KeyboardType.Decimal, suffix = "g")

            Text("Nutrition values are", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipGroup(NutritionBasis.entries.map { it.label }, setOf(f.basis.label), { l -> u { it.copy(basis = NutritionBasis.entries.first { b -> b.label == l }) } })
            FitTextField(f.calories, { v -> u { it.copy(calories = v) } }, "Calories", keyboardType = KeyboardType.Decimal, suffix = "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(f.protein, { v -> u { it.copy(protein = v) } }, "Protein", Modifier.weight(1f), KeyboardType.Decimal, "g")
                FitTextField(f.carbs, { v -> u { it.copy(carbs = v) } }, "Carbs", Modifier.weight(1f), KeyboardType.Decimal, "g")
                FitTextField(f.fat, { v -> u { it.copy(fat = v) } }, "Fat", Modifier.weight(1f), KeyboardType.Decimal, "g")
            }
            Text("Optional: leave blank if unknown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(f.saturatedFat, { v -> u { it.copy(saturatedFat = v) } }, "Sat. fat", Modifier.weight(1f), KeyboardType.Decimal, "g")
                FitTextField(f.fibre, { v -> u { it.copy(fibre = v) } }, "Fibre", Modifier.weight(1f), KeyboardType.Decimal, "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(f.sugar, { v -> u { it.copy(sugar = v) } }, "Sugar", Modifier.weight(1f), KeyboardType.Decimal, "g")
                FitTextField(f.sodiumMg, { v -> u { it.copy(sodiumMg = v) } }, "Sodium", Modifier.weight(1f), KeyboardType.Decimal, "mg")
            }

            Text("Where do these numbers come from?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipGroup(NutritionSource.entries.map { it.label }, setOf(f.source.label), { l -> u { it.copy(source = NutritionSource.entries.first { s -> s.label == l }) } })
            Text(f.source.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitTextField(f.sourceNote, { v -> u { it.copy(sourceNote = v) } }, "Source note (optional)", singleLine = false)

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save food", viewModel::save, Modifier.fillMaxWidth(), enabled = !state.saving)
            Text(
                "Editing a food never changes meals you've already logged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this food?") },
            text = { Text("Meals you've already logged keep their nutrition. This can't be undone.") },
            confirmButton = { TextButton({ confirmDelete = false; viewModel.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
