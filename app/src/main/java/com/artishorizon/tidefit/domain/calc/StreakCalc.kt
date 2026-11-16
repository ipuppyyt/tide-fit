package com.artishorizon.tidefit.domain.calc

import java.time.LocalDate

data class Streak(
    /** Consecutive days up to today (or up to yesterday while today is still open). */
    val current: Int,
    val best: Int,
    val todayDone: Boolean,
    /** The last [WEEK] days, oldest first, true where something was logged. */
    val week: List<Pair<LocalDate, Boolean>>
) {
    companion object { val EMPTY = Streak(0, 0, false, emptyList()) }
}

/**
 * A day counts when you logged food or finished a workout. Today never breaks a streak: until it ends, the streak is
 * measured up to yesterday and simply grows by one once today has an entry.
 */
object StreakCalc {
    const val WEEK = 7
    val MILESTONES = listOf(3, 7, 14, 30, 50, 100, 200, 365)

    fun compute(activeDays: Set<LocalDate>, today: LocalDate): Streak {
        val todayDone = today in activeDays
        var cursor = if (todayDone) today else today.minusDays(1)
        var current = 0
        while (cursor in activeDays) { current++; cursor = cursor.minusDays(1) }

        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in activeDays.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        val week = (WEEK - 1 downTo 0).map { today.minusDays(it.toLong()).let { d -> d to (d in activeDays) } }
        return Streak(current, maxOf(best, current), todayDone, week)
    }

    fun nextMilestone(current: Int): Int? = MILESTONES.firstOrNull { it > current }
}
