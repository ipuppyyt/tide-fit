package com.artishorizon.tidefit.widget

import com.artishorizon.tidefit.domain.model.BeverageKind
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Everything the glass widgets show, read once per refresh from the local database. */
data class WidgetSnapshot(
    val eatenKcal: Int,
    val targetKcal: Int?,
    val waterMl: Int,
    val waterTargetMl: Int?,
    val workoutMinutes: Int,
    val workoutName: String?,
    val proteinG: Int = 0, val carbsG: Int = 0, val fatG: Int = 0,
    val proteinTargetG: Int? = null, val carbsTargetG: Int? = null, val fatTargetG: Int? = null,
    val streak: Int = 0,
    val streakToday: Boolean = false,
    val week: List<Boolean> = emptyList()
) {
    val leftKcal: Int? get() = targetKcal?.let { it - eatenKcal }

    companion object {
        suspend fun load(ep: WidgetEntryPoint): WidgetSnapshot {
            val today = LocalDate.now(ep.clock())
            val zone = ZoneId.systemDefault()
            val targets = ep.profiles().observeCurrentTargets().first()
            val entries = ep.foodLog().observeDay(today).first()
            val eaten = entries.sumOf { it.total.calories }.toInt()
            val water = ep.water().observeDay(today).first().filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl }
            val sessions = ep.workouts().observeHistory().first()
                .filter { it.endedAt != null && it.startedAt.atZone(zone).toLocalDate() == today }
            val minutes = sessions.sumOf { Duration.between(it.startedAt, it.endedAt).toMinutes() }.toInt()
            val streak = ep.streaks().current()
            return WidgetSnapshot(eaten, targets?.caloriesKcal, water, targets?.waterMl, minutes, sessions.maxByOrNull { it.startedAt }?.name,
                entries.sumOf { it.total.protein }.toInt(), entries.sumOf { it.total.carbs }.toInt(), entries.sumOf { it.total.fat }.toInt(),
                targets?.proteinG, targets?.carbohydratesG, targets?.fatG, streak.current, streak.todayDone, streak.week.map { it.second })
        }
    }
}
