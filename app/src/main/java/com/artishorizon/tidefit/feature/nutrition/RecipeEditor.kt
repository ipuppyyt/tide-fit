package com.artishorizon.tidefit.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.util.parseDecimal
import com.artishorizon.tidefit.core.util.rounded
import com.artishorizon.tidefit.domain.calc.RecipeCalculator
import com.artishorizon.tidefit.domain.calc.RecipeResult
import com.artishorizon.tidefit.domain.model.Food
import com.artishorizon.tidefit.domain.model.RecipeDraft
import com.artishorizon.tidefit.domain.model.RecipeIngredient
import com.artishorizon.tidefit.domain.model.formatAmount
import com.artishorizon.tidefit.domain.repository.FoodRepository
import com.artishorizon.tidefit.domain.repository.RecipeRepository
import com.artishorizon.tidefit.navigation.RecipeEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One ingredient row; grams stay as text so partial typing is never lost. */
data class IngredientRow(val sourceFoodId: Long?, val name: String, val per100: com.artishorizon.tidefit.domain.model.Nutrition, val gramsText: String)

data class RecipeUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val name: String = "",
    val servingsText: String = "4",
    val cookedWeightText: String = "",
    val ingredients: List<IngredientRow> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false
) {
    val servings: Int? get() = servingsText.trim().toIntOrNull()?.takeIf { it > 0 }
    val cookedWeight: Double? get() = cookedWeightText.parseDecimal()?.takeIf { it > 0 }
    private fun ingredientList(): List<RecipeIngredient>? =
        ingredients.map { r -> RecipeIngredient(r.sourceFoodId, r.name, r.gramsText.parseDecimal()?.takeIf { it > 0 } ?: return null, r.per100) }

    val draftIngredients get() = ingredientList()
    val result: RecipeResult?
        get() {
            val s = servings ?: return null
            if (cookedWeightText.isNotBlank() && cookedWeight == null) return null
            return RecipeCalculator.calculate(ingredientList() ?: return null, s, cookedWeight)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val foods: FoodRepository,
    private val recipes: RecipeRepository
) : ViewModel() {
    private val foodId = savedState.toRoute<RecipeEditorRoute>().foodId
    private val _state = MutableStateFlow(RecipeUiState(isEdit = foodId != 0L))
    val state: StateFlow<RecipeUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")
    val pickerResults: StateFlow<List<Food>> = query.flatMapLatest { foods.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val draft = if (foodId != 0L) recipes.load(foodId) else null
            _state.update {
                if (draft == null) it.copy(loading = false) else it.copy(
                    loading = false, name = draft.name, servingsText = draft.servings.toString(),
                    cookedWeightText = draft.cookedWeightG?.let(::formatAmount).orEmpty(),
                    ingredients = draft.ingredients.map { i -> IngredientRow(i.sourceFoodId, i.name, i.per100, formatAmount(i.grams)) }
                )
            }
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setServings(v: String) = _state.update { it.copy(servingsText = v, error = null) }
    fun setCooked(v: String) = _state.update { it.copy(cookedWeightText = v, error = null) }
    fun setGrams(index: Int, v: String) = _state.update { s ->
        s.copy(ingredients = s.ingredients.mapIndexed { i, r -> if (i == index) r.copy(gramsText = v) else r }, error = null)
    }
    fun remove(index: Int) = _state.update { s -> s.copy(ingredients = s.ingredients.filterIndexed { i, _ -> i != index }) }
    fun addIngredient(food: Food) = _state.update { s ->
        s.copy(ingredients = s.ingredients + IngredientRow(food.id, food.name, food.per100, "100"), error = null)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val error = when {
            s.name.isBlank() -> "Enter a recipe name."
            s.ingredients.isEmpty() -> "Add at least one ingredient."
            s.draftIngredients == null -> "Every ingredient needs an amount in grams greater than zero."
            s.servings == null -> "Servings must be a whole number greater than zero."
            s.cookedWeightText.isNotBlank() && s.cookedWeight == null -> "Cooked weight must be greater than zero, or left blank."
            s.result == null -> "Couldn't calculate this recipe. Check the numbers."
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return }
        val draft = RecipeDraft(foodId, s.name.trim(), s.servings!!, s.cookedWeight, s.draftIngredients!!)
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { recipes.save(draft) }
                .onSuccess { _state.update { it.copy(saving = false, done = true) } }
                .onFailure { _state.update { it.copy(saving = false, error = "Couldn't save. Please try again.") } }
        }
    }
}

@Composable
fun RecipeEditorScreen(onBack: () -> Unit, viewModel: RecipeEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onBack() }

    Scaffold(topBar = { FitTopBar(if (state.isEdit) "Edit recipe" else "New recipe", onBack) }) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            FitTextField(state.name, viewModel::setName, "Recipe name")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(state.servingsText, viewModel::setServings, "Servings", Modifier.weight(1f), KeyboardType.Number)
                FitTextField(state.cookedWeightText, viewModel::setCooked, "Cooked weight", Modifier.weight(1f), KeyboardType.Decimal, "g")
            }
            Text(
                "Cooked weight is optional. Leave it blank to use the total weight of the ingredients.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SectionHeader("Ingredients")
            if (state.ingredients.isEmpty()) {
                Text("No ingredients yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.ingredients.forEachIndexed { i, row ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Column(Modifier.weight(1f).padding(top = Spacing.md)) { Text(row.name, style = MaterialTheme.typography.titleSmall) }
                    FitTextField(row.gramsText, { viewModel.setGrams(i, it) }, "Amount", Modifier.weight(0.7f), KeyboardType.Decimal, "g",
                        error = if (row.gramsText.parseDecimal()?.let { it > 0 } != true) "> 0" else null)
                    IconButton({ viewModel.remove(i) }) { Icon(Icons.Default.Close, contentDescription = "Remove ${row.name}") }
                }
            }
            SecondaryButton("Add ingredient", { picking = true }, Modifier.fillMaxWidth())

            state.result?.let { r ->
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Per serving (${r.servingWeightG.rounded()} g)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${r.perServing.calories.rounded()} kcal", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "P ${r.perServing.protein.rounded()} g · C ${r.perServing.carbs.rounded()} g · F ${r.perServing.fat.rounded()} g",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Per 100 g: ${r.per100.calories.rounded()} kcal · total ${r.totalWeightG.rounded()} g, ${r.total.calories.rounded()} kcal",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save recipe", viewModel::save, Modifier.fillMaxWidth(), enabled = !state.saving)
        }
    }

    if (picking) {
        val results by viewModel.pickerResults.collectAsStateWithLifecycle()
        var q by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = false; viewModel.setQuery("") },
            title = { Text("Add ingredient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FitTextField(q, { q = it; viewModel.setQuery(it) }, "Search foods")
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(results.filter { !it.isRecipe }, key = { it.id }) { food ->
                            TextButton({ viewModel.addIngredient(food); picking = false; viewModel.setQuery("") }, Modifier.fillMaxWidth()) {
                                Text(food.name, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ picking = false; viewModel.setQuery("") }) { Text("Close") } }
        )
    }
}
