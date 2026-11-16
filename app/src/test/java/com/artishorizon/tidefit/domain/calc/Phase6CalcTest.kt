package com.artishorizon.tidefit.domain.calc

import com.artishorizon.tidefit.domain.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class Phase6CalcTest {
    private val utc = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, 16, h, m, 0, 0, utc)

    // ---- reminder timing
    @Test fun nextOccurrenceLaterToday() = assertEquals(Duration.ofHours(2), ReminderTime.delayUntilNext(at(13), 15 * 60))
    @Test fun passedTimeRollsToTomorrow() = assertEquals(Duration.ofHours(22), ReminderTime.delayUntilNext(at(13), 11 * 60))
    @Test fun exactlyNowRollsToTomorrow() = assertEquals(Duration.ofHours(24), ReminderTime.delayUntilNext(at(15), 15 * 60))
    @Test fun rejectsInvalidMinutes() { assertTrue(runCatching { ReminderTime.delayUntilNext(at(1), 1440) }.isFailure) }

    @Test fun delayAcrossDstKeepsWallClockTime() {
        val zone = ZoneId.of("Europe/London") // clocks go forward on 2027-03-28
        val now = ZonedDateTime.of(2027, 3, 27, 12, 0, 0, 0, zone)
        val delay = ReminderTime.delayUntilNext(now, 8 * 60) // 08:00 next morning is only 19 h away in real time
        assertEquals(Duration.ofHours(19), delay)
    }

    // ---- reminder rules
    @Test fun waterReminderOnlyWhenBehindTarget() {
        assertTrue(ReminderRules.shouldNotify(ReminderKind.WATER, 1000, 2500, 0, false))
        assertFalse(ReminderRules.shouldNotify(ReminderKind.WATER, 2500, 2500, 0, false))
        assertTrue(ReminderRules.shouldNotify(ReminderKind.WATER, 0, null, 0, false))
        assertFalse(ReminderRules.shouldNotify(ReminderKind.WATER, 300, null, 0, false))
    }

    @Test fun mealAndWeighInRemindersSkipWhenAlreadyDone() {
        assertTrue(ReminderRules.shouldNotify(ReminderKind.MEALS, 0, null, 0, false))
        assertFalse(ReminderRules.shouldNotify(ReminderKind.MEALS, 0, null, 2, false))
        assertTrue(ReminderRules.shouldNotify(ReminderKind.WEIGH_IN, 0, null, 0, false))
        assertFalse(ReminderRules.shouldNotify(ReminderKind.WEIGH_IN, 0, null, 0, true))
    }

    // ---- Health Connect mapping
    @Test fun dailyWeightKeepsLatestReadingPerLocalDay() {
        val r = listOf(
            HealthMapping.WeightReading(Instant.parse("2026-11-15T06:00:00Z"), 80.4),
            HealthMapping.WeightReading(Instant.parse("2026-11-15T20:00:00Z"), 79.9),
            HealthMapping.WeightReading(Instant.parse("2026-11-16T06:00:00Z"), 79.5)
        )
        val m = HealthMapping.dailyWeights(r, utc)
        assertEquals(2, m.size)
        assertEquals(79.9, m[LocalDate.of(2026, 11, 15)]!!, 0.001)
    }

    @Test fun dailyWeightDropsImplausibleValues() {
        val m = HealthMapping.dailyWeights(listOf(HealthMapping.WeightReading(Instant.parse("2026-11-15T06:00:00Z"), 0.8)), utc)
        assertTrue(m.isEmpty())
    }

    @Test fun sleepSessionsSumPerWakeUpDate() {
        val s = listOf(
            HealthMapping.SleepSession(Instant.parse("2026-11-16T06:30:00Z"), Duration.ofMinutes(400)),
            HealthMapping.SleepSession(Instant.parse("2026-11-16T14:00:00Z"), Duration.ofMinutes(45)), // a nap that day
            HealthMapping.SleepSession(Instant.parse("2026-11-15T06:30:00Z"), Duration.ofMinutes(10))  // too short, dropped
        )
        val m = HealthMapping.dailySleepMinutes(s, utc)
        assertEquals(mapOf(LocalDate.of(2026, 11, 16) to 445), m)
    }
}
