package com.artishorizon.tidefit.domain.calc

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Turns raw Health Connect readings into at most one value per day. Pure, so it is unit-testable without Health Connect. */
object HealthMapping {

    data class WeightReading(val time: Instant, val kg: Double)

    /** One weight per local day: the latest reading of that day. Implausible values are dropped. */
    fun dailyWeights(readings: List<WeightReading>, zone: ZoneId, minKg: Double = 20.0, maxKg: Double = 500.0): Map<LocalDate, Double> =
        readings.filter { it.kg in minKg..maxKg }
            .sortedBy { it.time }
            .associate { it.time.atZone(zone).toLocalDate() to it.kg }

    /** [asleep] is the time actually asleep, with awake stages already excluded by the caller. */
    data class SleepSession(val end: Instant, val asleep: Duration)

    /** Total sleep per night, keyed by the local date the sleep ended (the morning you woke up). Nights under 30 minutes or over 24 h are dropped. */
    fun dailySleepMinutes(sessions: List<SleepSession>, zone: ZoneId): Map<LocalDate, Int> =
        sessions.groupBy { it.end.atZone(zone).toLocalDate() }
            .mapValues { (_, l) -> l.sumOf { it.asleep.toMinutes() }.toInt() }
            .filterValues { it in 30..24 * 60 }
}
