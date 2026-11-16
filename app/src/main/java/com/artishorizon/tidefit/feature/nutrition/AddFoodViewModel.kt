package com.artishorizon.tidefit.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.artishorizon.tidefit.core.util.parseDecimal
import com.artishorizon.tidefit.domain.model.Food
import com.artishorizon.tidefit.domain.model.Meal
import com.artishorizon.tidefit.domain.model.Nutrition
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.FoodRepository
import com.artishorizon.tidefit.navigation.AddFoodRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class FoodTab(val label: String) { ALL("All"), RECENT("Recent"), FAVOURITES("Favourites"), RECIPES("Recipes") }

data class LoggedInfo(val entryId: Long, val message: String)

data class AddFoodUiState(
    val query: String = "",
    val tab: FoodTab = FoodTab.ALL,
    val foods: List<Food> = emptyList(),
    val meal: Meal = Meal.OTHER,
    val day: LocalDate = LocalDate.EPOCH,
    val logged: LoggedInfo? = null,
    val manualError: String? = null,
    val manualSaved: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddFoodViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val foods: FoodRepository,
    private val log: FoodLogRepository
) : ViewModel() {
    private val route = savedState.toRoute<AddFoodRoute>()
    private val day = LocalDate.ofEpochDay(route.epochDay)
    private val meal = runCatching { Meal.valueOf(route.meal) }.getOrDefault(Meal.OTHER)

    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(FoodTab.ALL)
    private val extras = MutableStateFlow(AddFoodUiState(meal = meal, day = day))

    private val foodList = combine(query, tab) { q, t -> q.trim() to t }.flatMapLatest { (q, t) ->
        when (t) {
            FoodTab.ALL -> foods.search(q)
            FoodTab.RECENT -> foods.recent().filtered(q)
            FoodTab.FAVOURITES -> foods.favourites().filtered(q)
            FoodTab.RECIPES -> foods.recipes().filtered(q)
        }
    }

    val state: StateFlow<AddFoodUiState> = combine(query, tab, foodList, extras) { q, t, list, ex ->
        ex.copy(query = q, tab = t, foods = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddFoodUiState(meal = meal, day = day))

    fun setQuery(value: String) { query.value = value }
    fun setTab(value: FoodTab) { tab.value = value }
    fun toggleFavourite(food: Food) { viewModelScope.launch { foods.setFavourite(food.id, !food.isFavourite) } }

    /** One-tap logging of the food's default serving. */
    fun quickAdd(food: Food) {
        viewModelScope.launch {
            runCatching {
                val id = log.logFood(food, food.servingWeightG, "1 × ${food.servingLabel}", day, meal)
                extras.update { it.copy(logged = LoggedInfo(id, "Added ${food.name} to ${meal.label}")) }
            }
        }
    }

    fun undoLast(entryId: Long) { viewModelScope.launch { log.delete(entryId); extras.update { it.copy(logged = null) } } }
    fun loggedShown() = extras.update { it.copy(logged = null) }

    fun saveManual(name: String, kcal: String, protein: String, carbs: String, fat: String) {
        val calories = kcal.parseDecimal()
        if (name.isBlank()) { extras.update { it.copy(manualError = "Enter a name.") }; return }
        if (calories == null) { extras.update { it.copy(manualError = "Enter calories.") }; return }
        val n = Nutrition(calories, protein.blankAsZero() ?: return invalidMacros(), carbs.blankAsZero() ?: return invalidMacros(), fat.blankAsZero() ?: return invalidMacros())
        viewModelScope.launch {
            runCatching { log.logManual(name, n, day, meal) }
                .onSuccess { id -> extras.update { it.copy(manualError = null, manualSaved = true, logged = LoggedInfo(id, "Added $name to ${meal.label}")) } }
                .onFailure { extras.update { s -> s.copy(manualError = "Couldn't save. Check the values.") } }
        }
    }

    fun manualDialogClosed() = extras.update { it.copy(manualError = null, manualSaved = false) }

    private fun invalidMacros() { extras.update { it.copy(manualError = "Macros must be numbers (or blank).") } }
    private fun String.blankAsZero(): Double? = if (isBlank()) 0.0 else parseDecimal()

    private fun Flow<List<Food>>.filtered(q: String): Flow<List<Food>> =
        map { list -> if (q.isEmpty()) list else list.filter { it.name.contains(q, ignoreCase = true) || (it.brand?.contains(q, ignoreCase = true) == true) } }
}
