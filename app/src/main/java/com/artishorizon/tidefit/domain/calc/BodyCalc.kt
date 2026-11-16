package com.artishorizon.tidefit.domain.calc

import com.artishorizon.tidefit.domain.model.Measurement
import com.artishorizon.tidefit.domain.model.RecoveryEntry
import com.artishorizon.tidefit.domain.model.SleepEntry
import com.artishorizon.tidefit.domain.model.Supplement
import com.artishorizon.tidefit.domain.model.SupplementLog
import com.artishorizon.tidefit.domain.model.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure body-tracking maths. Everything returns null rather than guessing when there isn't enough data. */
object BodyCalc {

    data class TrendPoint(val date: LocalDate, val weightKg: Double, val trendKg: Double)

    /** One value per day (the last entry that day), each with the mean of the readings in the 7 days up to and including it. */
    fun weightTrend(entries: List<WeightEntry>): List<TrendPoint> {
        val perDay = entries.sortedWith(compareBy({ it.date }, { it.recordedAt })).groupBy { it.date }.map { (d, l) -> d to l.last().weightKg }
        return perDay.map { (date, kg) ->
            val window = perDay.filter { (d, _) -> !d.isAfter(date) && ChronoUnit.DAYS.between(d, date) < TREND_DAYS }
            TrendPoint(date, kg, window.map { it.second }.average())
        }
    }

    /**
     * Weekly change in kg from a least-squares line over the last [days] days. Needs at least [MIN_POINTS] distinct
     * days that span at least [MIN_SPAN_DAYS] days, otherwise null: a rate from a few noisy days is misleading.
     */
    fun weeklyRateKg(entries: List<WeightEntry>, today: LocalDate, days: Int = 28): Double? {
        val from = today.minusDays(days.toLong() - 1)
        val points = weightTrend(entries).filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        if (points.size < MIN_POINTS) return null
        val span = ChronoUnit.DAYS.between(points.first().date, points.last().date)
        if (span < MIN_SPAN_DAYS) return null
        val xs = points.map { ChronoUnit.DAYS.between(points.first().date, it.date).toDouble() }
        val ys = points.map { it.weightKg }
        val mx = xs.average()
        val my = ys.average()
        val denom = xs.sumOf { (it - mx) * (it - mx) }
        if (denom == 0.0) return null
        val slopePerDay = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / denom
        return slopePerDay * 7.0
    }

    /** 0..1 progress from the starting weight to the target, or null when start and target are the same. Clamped. */
    fun goalProgress(startKg: Double, currentKg: Double, targetKg: Double): Double? {
        val total = targetKg - startKg
        if (kotlin.math.abs(total) < 0.05) return null
        return ((currentKg - startKg) / total).coerceIn(0.0, 1.0)
    }

    enum class Direction { LOSE, GAIN, HOLD }

    fun direction(currentKg: Double, targetKg: Double): Direction = when {
        targetKg - currentKg > 0.5 -> Direction.GAIN
        currentKg - targetKg > 0.5 -> Direction.LOSE
        else -> Direction.HOLD
    }

    /** Change between the oldest and newest measurement within [days]; null with fewer than two readings. */
    fun measurementChange(entries: List<Measurement>, today: LocalDate, days: Int): Double? {
        val from = today.minusDays(days.toLong())
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }.sortedBy { it.date }
        if (inRange.size < 2) return null
        return inRange.last().value - inRange.first().value
    }

    data class SleepSummary(val nightsLogged: Int, val avgMinutes: Int, val avgQuality: Double?)

    /** Averages over the nights that were logged in the last [days] days; unlogged nights are not counted as zero. */
    fun sleepSummary(entries: List<SleepEntry>, today: LocalDate, days: Int): SleepSummary? {
        val from = today.minusDays(days.toLong() - 1)
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        if (inRange.isEmpty()) return null
        return SleepSummary(inRange.size, inRange.map { it.durationMinutes }.average().toInt(), inRange.mapNotNull { it.quality }.takeIf { it.isNotEmpty() }?.average())
    }

    data class RecoverySummary(val daysLogged: Int, val soreness: Double, val energy: Double, val stress: Double)

    fun recoverySummary(entries: List<RecoveryEntry>, today: LocalDate, days: Int): RecoverySummary? {
        val from = today.minusDays(days.toLong() - 1)
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        if (inRange.isEmpty()) return null
        return RecoverySummary(inRange.size, inRange.map { it.soreness }.average(), inRange.map { it.energy }.average(), inRange.map { it.stress }.average())
    }

    data class Adherence(val taken: Int, val expected: Int) {
        val fraction: Double get() = if (expected == 0) 0.0 else taken.toDouble() / expected
    }

    /**
     * Doses taken versus expected over the last [days] days. A supplement only counts from the day it was added, and
     * logs for removed or inactive supplements are ignored. Null when nothing was expected.
     */
    fun supplementAdherence(supplements: List<Supplement>, logs: List<SupplementLog>, today: LocalDate, days: Int): Adherence? {
        val from = today.minusDays(days.toLong() - 1)
        var expected = 0
        var taken = 0
        supplements.filter { it.active }.forEach { s ->
            val start = if (s.createdOn.isAfter(from)) s.createdOn else from
            if (start.isAfter(today)) return@forEach
            expected += (ChronoUnit.DAYS.between(start, today) + 1).toInt()
            taken += logs.count { it.supplementId == s.id && !it.date.isBefore(start) && !it.date.isAfter(today) }
        }
        return if (expected == 0) null else Adherence(taken, expected)
    }

    const val TREND_DAYS = 7L
    const val MIN_POINTS = 4
    const val MIN_SPAN_DAYS = 10L
}
