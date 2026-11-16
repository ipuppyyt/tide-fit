package com.fitos.app.domain.calc

import com.fitos.app.domain.model.Measurement
import com.fitos.app.domain.model.MeasurementType
import com.fitos.app.domain.model.SleepEntry
import com.fitos.app.domain.model.Supplement
import com.fitos.app.domain.model.SupplementLog
import com.fitos.app.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BodyCalcTest {
    private val today = LocalDate.of(2026, 11, 16)

    private fun w(daysAgo: Long, kg: Double, at: Long = 0) =
        WeightEntry(date = today.minusDays(daysAgo), weightKg = kg, recordedAt = Instant.ofEpochSecond(at))

    @Test fun trendUsesLastEntryPerDayAndSevenDayMean() {
        val trend = BodyCalc.weightTrend(listOf(w(1, 80.0, 1), w(1, 79.0, 2), w(0, 81.0)))
        assertEquals(2, trend.size)
        assertEquals(79.0, trend[0].weightKg, 0.001)
        assertEquals(80.0, trend[1].trendKg, 0.001) // mean(79, 81)
    }

    @Test fun trendIgnoresReadingsOlderThanSevenDays() {
        val trend = BodyCalc.weightTrend(listOf(w(10, 100.0), w(0, 80.0)))
        assertEquals(80.0, trend.last().trendKg, 0.001)
    }

    @Test fun weeklyRateNullWithTooFewPoints() {
        assertNull(BodyCalc.weeklyRateKg(listOf(w(20, 80.0), w(10, 79.0), w(0, 78.0)), today))
    }

    @Test fun weeklyRateNullWhenSpanTooShort() {
        assertNull(BodyCalc.weeklyRateKg(listOf(w(4, 80.0), w(3, 80.0), w(2, 80.0), w(0, 79.0)), today))
    }

    @Test fun weeklyRateIsLinearSlope() {
        // 0.1 kg lost per day => -0.7 kg/week
        val entries = (0L..14L step 2).map { w(14 - it, 80.0 - 0.1 * it) }
        val rate = BodyCalc.weeklyRateKg(entries, today)
        assertNotNull(rate)
        assertEquals(-0.7, rate!!, 0.001)
    }

    @Test fun goalProgressClampsAndHandlesNoDistance() {
        assertEquals(0.5, BodyCalc.goalProgress(90.0, 85.0, 80.0)!!, 0.001)
        assertEquals(1.0, BodyCalc.goalProgress(90.0, 75.0, 80.0)!!, 0.001)
        assertEquals(0.0, BodyCalc.goalProgress(90.0, 95.0, 80.0)!!, 0.001)
        assertNull(BodyCalc.goalProgress(80.0, 80.0, 80.0))
    }

    @Test fun directionHasDeadband() {
        assertEquals(BodyCalc.Direction.LOSE, BodyCalc.direction(90.0, 80.0))
        assertEquals(BodyCalc.Direction.GAIN, BodyCalc.direction(70.0, 80.0))
        assertEquals(BodyCalc.Direction.HOLD, BodyCalc.direction(80.2, 80.0))
    }

    @Test fun measurementChangeNeedsTwoReadings() {
        val one = listOf(Measurement(date = today, type = MeasurementType.WAIST, value = 90.0))
        assertNull(BodyCalc.measurementChange(one, today, 30))
        val two = one + Measurement(date = today.minusDays(20), type = MeasurementType.WAIST, value = 92.0)
        assertEquals(-2.0, BodyCalc.measurementChange(two, today, 30)!!, 0.001)
    }

    @Test fun sleepSummaryAveragesLoggedNightsOnly() {
        val e = listOf(
            SleepEntry(date = today, durationMinutes = 420, quality = 4),
            SleepEntry(date = today.minusDays(1), durationMinutes = 480, quality = null)
        )
        val s = BodyCalc.sleepSummary(e, today, 7)!!
        assertEquals(2, s.nightsLogged)
        assertEquals(450, s.avgMinutes)
        assertEquals(4.0, s.avgQuality!!, 0.001)
        assertNull(BodyCalc.sleepSummary(emptyList(), today, 7))
    }

    @Test fun adherenceCountsFromCreationAndIgnoresInactive() {
        val a = Supplement(id = 1, name = "Creatine", createdOn = today.minusDays(3))
        val inactive = Supplement(id = 2, name = "Old", active = false, createdOn = today.minusDays(10))
        val logs = listOf(SupplementLog(1, today), SupplementLog(1, today.minusDays(1)), SupplementLog(2, today))
        val r = BodyCalc.supplementAdherence(listOf(a, inactive), logs, today, 7)!!
        assertEquals(4, r.expected) // 4 days since creation
        assertEquals(2, r.taken)
        assertNull(BodyCalc.supplementAdherence(emptyList(), logs, today, 7))
    }
}
