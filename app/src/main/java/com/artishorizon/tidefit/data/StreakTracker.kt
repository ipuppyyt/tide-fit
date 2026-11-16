package com.artishorizon.tidefit.data

import com.artishorizon.tidefit.domain.calc.Streak
import com.artishorizon.tidefit.domain.calc.StreakCalc
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Works the daily streak out from what is already logged, so nothing extra is stored and edits or deletions are always reflected. */
@Singleton
class StreakTracker @Inject constructor(
    private val food: FoodLogRepository,
    private val workouts: WorkoutRepository,
    private val clock: Clock
) {
    fun observe(): Flow<Streak> {
        val today = LocalDate.now(clock)
        return combine(food.observeRange(EARLIEST, today), workouts.observeHistory()) { entries, sessions ->
            val zone = ZoneId.systemDefault()
            val days = HashSet<LocalDate>()
            entries.forEach { days += it.date }
            sessions.filter { it.endedAt != null }.forEach { days += it.startedAt.atZone(zone).toLocalDate() }
            StreakCalc.compute(days, today)
        }
    }

    suspend fun current(): Streak = observe().first()

    private companion object { val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1) }
}
