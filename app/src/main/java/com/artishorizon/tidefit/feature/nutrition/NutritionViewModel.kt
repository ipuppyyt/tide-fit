package com.artishorizon.tidefit.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.util.totals
import com.artishorizon.tidefit.domain.model.BeverageKind
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.FoodEntry
import com.artishorizon.tidefit.domain.model.Meal
import com.artishorizon.tidefit.domain.model.Nutrition
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.WaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class NutritionUiState(
    val loading: Boolean = true,
    val day: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
    val entriesByMeal: Map<Meal, List<FoodEntry>> = emptyMap(),
    val totals: Nutrition = Nutrition.ZERO,
    val targets: DailyTargets? = null,
    val waterMl: Int = 0
) {
    val isToday get() = day == today
    val isEmpty get() = entriesByMeal.values.all { it.isEmpty() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    log: FoodLogRepository,
    water: WaterRepository,
    profiles: ProfileRepository,
    private val clock: Clock
) : ViewModel() {

    private val dayEpoch = savedState.getStateFlow(KEY_DAY, LocalDate.now(clock).toEpochDay())

    val state: StateFlow<NutritionUiState> = dayEpoch.flatMapLatest { epoch ->
        val day = LocalDate.ofEpochDay(epoch)
        combine(log.observeDay(day), water.observeDay(day), profiles.observeCurrentTargets()) { entries, waterEntries, targets ->
            NutritionUiState(
                loading = false,
                day = day,
                today = LocalDate.now(clock),
                entriesByMeal = Meal.entries.associateWith { m -> entries.filter { it.meal == m } },
                totals = entries.totals(),
                targets = targets,
                waterMl = waterEntries.filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState())

    fun previousDay() = shift(-1)
    fun nextDay() = shift(1)
    fun goToToday() { savedState[KEY_DAY] = LocalDate.now(clock).toEpochDay() }

    private fun shift(days: Long) { savedState[KEY_DAY] = (dayEpoch.value + days) }

    private companion object {
        const val KEY_DAY = "day"
    }
}
