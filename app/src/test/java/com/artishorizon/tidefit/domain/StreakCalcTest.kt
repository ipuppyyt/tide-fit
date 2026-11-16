package com.artishorizon.tidefit.domain

import com.artishorizon.tidefit.domain.calc.StreakCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakCalcTest {
    private val today = LocalDate.of(2026, 11, 16)
    private fun days(vararg back: Int) = back.map { today.minusDays(it.toLong()) }.toSet()

    @Test fun emptyHasNoStreak() {
        val s = StreakCalc.compute(emptySet(), today)
        assertEquals(0, s.current); assertEquals(0, s.best); assertFalse(s.todayDone)
    }

    @Test fun todayCountsWhenDone() {
        val s = StreakCalc.compute(days(0, 1, 2), today)
        assertEquals(3, s.current); assertTrue(s.todayDone)
    }

    @Test fun openTodayDoesNotBreakStreak() {
        val s = StreakCalc.compute(days(1, 2, 3), today)
        assertEquals(3, s.current); assertFalse(s.todayDone)
    }

    @Test fun missedYesterdayBreaksIt() {
        assertEquals(0, StreakCalc.compute(days(2, 3), today).current)
    }

    @Test fun bestKeepsLongestEarlierRun() {
        val s = StreakCalc.compute(days(0, 10, 11, 12, 13, 14), today)
        assertEquals(1, s.current); assertEquals(5, s.best)
    }

    @Test fun weekIsSevenDaysOldestFirst() {
        val s = StreakCalc.compute(days(0, 6), today)
        assertEquals(7, s.week.size); assertTrue(s.week.first().second); assertTrue(s.week.last().second)
    }

    @Test fun nextMilestone() {
        assertEquals(3, StreakCalc.nextMilestone(0)); assertEquals(14, StreakCalc.nextMilestone(7))
    }
}

class StreakMilestoneTest {
    private val today = LocalDate.of(2026, 11, 16)
    private fun streak(len: Int, todayDone: Boolean = true): com.artishorizon.tidefit.domain.calc.Streak {
        val start = if (todayDone) 0 else 1
        return StreakCalc.compute((start until start + len).map { today.minusDays(it.toLong()) }.toSet(), today)
    }

    @Test fun firesOnMilestoneDay() { assertEquals(7, StreakCalc.milestoneHit(streak(7), 3)) }
    @Test fun notBetweenMilestones() { assertEquals(null, StreakCalc.milestoneHit(streak(8), 7)) }
    @Test fun onlyOncePerMilestone() { assertEquals(null, StreakCalc.milestoneHit(streak(7), 7)) }
    @Test fun waitsUntilTodayIsLogged() { assertEquals(null, StreakCalc.milestoneHit(streak(7, todayDone = false), 3)) }
}
