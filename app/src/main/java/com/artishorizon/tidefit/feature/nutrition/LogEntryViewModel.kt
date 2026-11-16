package com.artishorizon.tidefit.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.artishorizon.tidefit.core.util.parseDecimal
import com.artishorizon.tidefit.domain.calc.PortionCalculator
import com.artishorizon.tidefit.domain.model.Food
import com.artishorizon.tidefit.domain.model.Meal
import com.artishorizon.tidefit.domain.model.Nutrition
import com.artishorizon.tidefit.domain.model.NutritionSource
import com.artishorizon.tidefit.domain.model.formatAmount
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.FoodRepository
import com.artishorizon.tidefit.navigation.LogEntryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class QuantityMode(val label: String) { SERVINGS("Servings"), GRAMS("Grams") }

data class LogEntryUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isEdit: Boolean = false,
    val isManual: Boolean = false,
    val title: String = "",
    val brand: String? = null,
    val source: NutritionSource = NutritionSource.ESTIMATED,
    val foodId: Long? = null,
    val isRecipe: Boolean = false,
    val per100: Nutrition? = null,
    val servingWeightG: Double? = null,
    val servingLabel: String? = null,
    val mode: QuantityMode = QuantityMode.GRAMS,
    val quantityText: String = "",
    val meal: Meal = Meal.OTHER,
    val manualName: String = "",
    val manualKcal: String = "",
    val manualProtein: String = "",
    val manualCarbs: String = "",
    val manualFat: String = "",
    val manualFixed: Nutrition? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false
) {
    /** Quantity converted to grams, or null while the input is blank/invalid. */
    val grams: Double?
        get() {
            val q = quantityText.parseDecimal()?.takeIf { it > 0 } ?: return null
            return if (mode == QuantityMode.GRAMS) q else servingWeightG?.let { PortionCalculator.gramsForServings(it, q) }
        }

    val preview: Nutrition?
        get() = if (isManual) manualNutrition() else grams?.let { g -> per100?.let { PortionCalculator.forGrams(it, g) } }

    fun manualNutrition(): Nutrition? {
        val kcal = manualKcal.parseDecimal() ?: return null
        fun v(s: String) = if (s.isBlank()) 0.0 else s.parseDecimal()
        return Nutrition(kcal, v(manualProtein) ?: return null, v(manualCarbs) ?: return null, v(manualFat) ?: return null, manualFixed?.saturatedFat, manualFixed?.fibre, manualFixed?.sugar, manualFixed?.sodiumMg)
    }
}

@HiltViewModel
class LogEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val foods: FoodRepository,
    private val log: FoodLogRepository
) : ViewModel() {
    private val route = savedState.toRoute<LogEntryRoute>()
    private val day = LocalDate.ofEpochDay(route.epochDay)

    private val _state = MutableStateFlow(LogEntryUiState(meal = runCatching { Meal.valueOf(route.meal) }.getOrDefault(Meal.OTHER)))
    val state: StateFlow<LogEntryUiState> = _state.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        if (route.entryId != 0L) loadEntry(route.entryId) else loadFood(route.foodId)
    }

    private suspend fun loadFood(foodId: Long) {
        val food = foods.get(foodId)
        if (food == null) { _state.update { it.copy(loading = false, notFound = true) }; return }
        val servings = food.servingWeightG > 0
        _state.update {
            it.copy(
                loading = false, title = food.name, brand = food.brand, source = food.source, foodId = food.id, isRecipe = food.isRecipe,
                per100 = food.per100, servingWeightG = food.servingWeightG, servingLabel = food.servingLabel,
                mode = if (servings) QuantityMode.SERVINGS else QuantityMode.GRAMS,
                quantityText = if (servings) "1" else "100"
            )
        }
    }

    private suspend fun loadEntry(id: Long) {
        val e = log.get(id)
        if (e == null) { _state.update { it.copy(loading = false, notFound = true) }; return }
        val food = e.foodId?.let { foods.get(it) }
        val grams = e.quantityG
        _state.update {
            it.copy(
                loading = false, isEdit = true, title = e.name, brand = e.brand, source = e.source, foodId = food?.id, isRecipe = food?.isRecipe == true,
                meal = e.meal, isManual = grams == null,
                // Entry's own snapshot defines its nutrition density, not the (possibly edited) food.
                per100 = grams?.let { g -> PortionCalculator.per100From(e.total, g) },
                servingWeightG = food?.servingWeightG, servingLabel = food?.servingLabel,
                mode = QuantityMode.GRAMS, quantityText = grams?.let(::formatAmount).orEmpty(),
                manualName = e.name, manualFixed = e.total,
                manualKcal = formatAmount(e.total.calories), manualProtein = formatAmount(e.total.protein),
                manualCarbs = formatAmount(e.total.carbs), manualFat = formatAmount(e.total.fat)
            )
        }
    }

    fun setMode(mode: QuantityMode) = _state.update { s ->
        if (mode == s.mode) return@update s
        // Convert the typed amount so switching modes keeps the same quantity.
        val g = s.grams
        val sw = s.servingWeightG
        val text = when {
            g == null -> s.quantityText
            mode == QuantityMode.GRAMS -> formatAmount(g)
            sw != null && sw > 0 -> formatAmount(Math.round(g / sw * 100) / 100.0)
            else -> s.quantityText
        }
        s.copy(mode = mode, quantityText = text, error = null)
    }

    fun setQuantity(text: String) = _state.update { it.copy(quantityText = text, error = null) }
    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }
    fun setManual(name: String? = null, kcal: String? = null, protein: String? = null, carbs: String? = null, fat: String? = null) =
        _state.update {
            it.copy(
                manualName = name ?: it.manualName, manualKcal = kcal ?: it.manualKcal, manualProtein = protein ?: it.manualProtein,
                manualCarbs = carbs ?: it.manualCarbs, manualFat = fat ?: it.manualFat, error = null
            )
        }

    fun save() {
        val s = _state.value
        if (s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val result = runCatching {
                if (s.isManual) {
                    val n = s.manualNutrition() ?: throw IllegalArgumentException("Enter valid numbers.")
                    if (s.isEdit) log.updateManual(route.entryId, s.manualName, n, s.meal) else Unit
                } else {
                    val grams = s.grams ?: throw IllegalArgumentException("Enter a quantity greater than zero.")
                    val label = if (s.mode == QuantityMode.GRAMS) "${formatAmount(grams)} g"
                    else "${s.quantityText.trim()} × ${s.servingLabel}"
                    if (s.isEdit) log.updateQuantity(route.entryId, grams, label, s.meal)
                    else {
                        val food: Food = foods.get(s.foodId ?: 0) ?: throw IllegalStateException("Food no longer exists.")
                        log.logFood(food, grams, label, day, s.meal)
                    }
                }
            }
            result.onSuccess { _state.update { it.copy(saving = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = if (e is IllegalArgumentException) e.message else "Couldn't save. Please try again.") } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { log.delete(route.entryId) }
                .onSuccess { _state.update { it.copy(done = true) } }
                .onFailure { _state.update { it.copy(error = "Couldn't delete. Please try again.") } }
        }
    }
}
