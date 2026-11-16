package com.fitos.app.feature.nutrition

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitos.app.core.designsystem.FitTheme
import com.fitos.app.core.designsystem.Spacing
import com.fitos.app.core.designsystem.components.ChipGroup
import com.fitos.app.core.designsystem.components.EmptyState
import com.fitos.app.core.designsystem.components.FitCard
import com.fitos.app.core.designsystem.components.FitTextField
import com.fitos.app.core.designsystem.components.FitTopBar
import com.fitos.app.core.designsystem.components.LoadingState
import com.fitos.app.core.designsystem.components.PrimaryButton
import com.fitos.app.core.designsystem.components.SecondaryButton
import com.fitos.app.core.designsystem.components.SourceBadge
import com.fitos.app.core.util.oneDecimal
import com.fitos.app.core.util.rounded
import com.fitos.app.core.util.roundedOrDash
import com.fitos.app.domain.model.Meal
import com.fitos.app.domain.model.Nutrition

@Composable
fun LogEntryScreen(onBack: () -> Unit, onEditFood: (foodId: Long, isRecipe: Boolean) -> Unit, viewModel: LogEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onBack() }

    Scaffold(
        topBar = {
            FitTopBar(if (state.isEdit) "Edit entry" else "Log food", onBack) {
                if (state.isEdit) IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete entry") }
            }
        }
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound -> EmptyState(Icons.Default.Delete, "Not found", "This item no longer exists.", Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                if (!state.isManual) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(state.title, style = MaterialTheme.typography.headlineMedium)
                        state.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        SourceBadge(state.source)
                    }
                    QuantityInput(state, viewModel)
                } else {
                    FitTextField(state.manualName, { viewModel.setManual(name = it) }, "Name")
                    FitTextField(state.manualKcal, { viewModel.setManual(kcal = it) }, "Calories", keyboardType = KeyboardType.Decimal, suffix = "kcal")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FitTextField(state.manualProtein, { viewModel.setManual(protein = it) }, "Protein", Modifier.weight(1f), KeyboardType.Decimal)
                        FitTextField(state.manualCarbs, { viewModel.setManual(carbs = it) }, "Carbs", Modifier.weight(1f), KeyboardType.Decimal)
                        FitTextField(state.manualFat, { viewModel.setManual(fat = it) }, "Fat", Modifier.weight(1f), KeyboardType.Decimal)
                    }
                }

                Text("Meal", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipGroup(Meal.entries.map { it.label }, setOf(state.meal.label), { l -> viewModel.setMeal(Meal.entries.first { it.label == l }) })

                NutritionPreview(state.preview)

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PrimaryButton(if (state.isEdit) "Save changes" else "Add to log", viewModel::save, Modifier.fillMaxWidth(), enabled = !state.saving && state.preview != null)
                val foodId = state.foodId
                if (foodId != null) SecondaryButton("Edit this food", { onEditFood(foodId, state.isRecipe) }, Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("It will be removed from your log. This can't be undone.") },
            confirmButton = { TextButton({ confirmDelete = false; viewModel.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuantityInput(state: LogEntryUiState, vm: LogEntryViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (state.servingWeightG != null) {
            ChipGroup(QuantityMode.entries.map { it.label }, setOf(state.mode.label), { l -> vm.setMode(QuantityMode.entries.first { it.label == l }) })
        }
        FitTextField(
            state.quantityText, vm::setQuantity,
            if (state.mode == QuantityMode.GRAMS) "Amount" else "Number of servings",
            keyboardType = KeyboardType.Decimal,
            suffix = if (state.mode == QuantityMode.GRAMS) "g" else null,
            error = if (state.quantityText.isNotBlank() && state.grams == null) "Enter a number greater than zero." else null
        )
        if (state.mode == QuantityMode.SERVINGS) {
            Text("1 serving = ${state.servingLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipGroup(listOf("0.5", "1", "1.5", "2", "3"), setOf(state.quantityText), vm::setQuantity)
        } else {
            ChipGroup(listOf("50", "100", "150", "200", "250"), setOf(state.quantityText), vm::setQuantity)
        }
    }
}

@Composable
private fun NutritionPreview(n: Nutrition?) {
    val c = FitTheme.colors
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(if (n != null) "${n.calories.rounded()} kcal" else "– kcal", style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroCell("Protein", n?.protein.roundedOrDash(), c.protein)
                MacroCell("Carbs", n?.carbs.roundedOrDash(), c.carbs)
                MacroCell("Fat", n?.fat.roundedOrDash(), c.fat)
            }
            Text(
                "Fibre ${n?.fibre?.oneDecimal() ?: "–"} g · Sugar ${n?.sugar?.oneDecimal() ?: "–"} g · Sat. fat ${n?.saturatedFat?.oneDecimal() ?: "–"} g · Sodium ${n?.sodiumMg.roundedOrDash()} mg",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MacroCell(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text("$value g", style = MaterialTheme.typography.titleMedium)
    }
}
