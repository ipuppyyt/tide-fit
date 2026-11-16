package com.artishorizon.tidefit.widget

import android.content.Context
import com.artishorizon.tidefit.data.StreakTracker
import com.artishorizon.tidefit.domain.model.BeverageKind
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.WaterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the widget current while the app runs: redraws whenever today's food, water or the streak changes. */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val food: FoodLogRepository,
    private val water: WaterRepository,
    private val streaks: StreakTracker,
    private val clock: Clock
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        val today = LocalDate.now(clock)
        scope.launch {
            combine(food.observeDay(today), water.observeDay(today), streaks.observe()) { f, w, s ->
                listOf(f.size, f.sumOf { it.total.calories }, w.filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl }, s.current, s.todayDone)
            }
                .debounce(500)
                .collect { runCatching { TodayWidget.updateAll(context) } }
        }
    }
}
