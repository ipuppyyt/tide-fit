package com.artishorizon.tidefit.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.artishorizon.tidefit.core.designsystem.components.AlertDialog
import androidx.compose.material3.Icon
import com.artishorizon.tidefit.core.designsystem.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import com.artishorizon.tidefit.core.designsystem.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.ChipGroup
import com.artishorizon.tidefit.core.designsystem.components.EmptyState
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SourceBadge
import com.artishorizon.tidefit.core.util.rounded
import com.artishorizon.tidefit.domain.calc.PortionCalculator
import com.artishorizon.tidefit.domain.model.Food

@Composable
fun AddFoodScreen(
    onBack: () -> Unit,
    onOpenFood: (Food) -> Unit,
    onCreateFood: () -> Unit,
    onCreateRecipe: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showManual by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.logged) {
        val logged = state.logged ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(logged.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLast(logged.entryId) else viewModel.loggedShown()
    }
    LaunchedEffect(state.manualSaved) { if (state.manualSaved) showManual = false }

    Scaffold(
        topBar = { FitTopBar("Add to ${state.meal.label}", onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item(key = "search") { FitTextField(state.query, viewModel::setQuery, "Search foods") }
            item(key = "tabs") {
                ChipGroup(FoodTab.entries.map { it.label }, setOf(state.tab.label), { l -> viewModel.setTab(FoodTab.entries.first { it.label == l }) })
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton("Quick add", { showManual = true }, Modifier.weight(1f))
                    SecondaryButton("New food", onCreateFood, Modifier.weight(1f))
                    SecondaryButton("Recipe", onCreateRecipe, Modifier.weight(1f))
                }
            }
            if (state.foods.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Default.Add,
                        title = when (state.tab) {
                            FoodTab.ALL -> "No foods match"
                            FoodTab.RECENT -> "Nothing logged yet"
                            FoodTab.FAVOURITES -> "No favourites yet"
                            FoodTab.RECIPES -> "No recipes yet"
                        },
                        message = when (state.tab) {
                            FoodTab.ALL -> "Try another search, or create a custom food."
                            FoodTab.RECENT -> "Foods you log will appear here for faster logging."
                            FoodTab.FAVOURITES -> "Tap the star on a food to keep it here."
                            FoodTab.RECIPES -> "Create a recipe to log home-cooked meals."
                        }
                    )
                }
            }
            items(state.foods, key = { it.id }) { food ->
                FoodRow(food, onClick = { onOpenFood(food) }, onQuickAdd = { viewModel.quickAdd(food) }, onFavourite = { viewModel.toggleFavourite(food) })
            }
        }
    }

    if (showManual) {
        ManualEntryDialog(
            error = state.manualError,
            onDismiss = { showManual = false; viewModel.manualDialogClosed() },
            onSave = viewModel::saveManual
        )
    }
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit, onQuickAdd: () -> Unit, onFavourite: () -> Unit) {
    val perServing = PortionCalculator.forGrams(food.per100, food.servingWeightG)
    FitCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(food.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(food.brand, food.servingLabel, perServing?.let { "${it.calories.rounded()} kcal" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SourceBadge(food.source)
            }
            IconButton(onFavourite) {
                Icon(
                    if (food.isFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (food.isFavourite) "Remove favourite" else "Add favourite",
                    tint = if (food.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onQuickAdd) { Icon(Icons.Default.Add, contentDescription = "Add one serving of ${food.name}", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ManualEntryDialog(error: String?, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Log a one-off meal by entering its numbers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FitTextField(name, { name = it }, "Name")
                FitTextField(kcal, { kcal = it }, "Calories", keyboardType = KeyboardType.Decimal, suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FitTextField(protein, { protein = it }, "Protein", Modifier.weight(1f), KeyboardType.Decimal)
                    FitTextField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), KeyboardType.Decimal)
                    FitTextField(fat, { fat = it }, "Fat", Modifier.weight(1f), KeyboardType.Decimal)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton({ onSave(name, kcal, protein, carbs, fat) }) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
