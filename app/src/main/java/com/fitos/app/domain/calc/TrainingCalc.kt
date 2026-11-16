package com.fitos.app.domain.calc

import com.fitos.app.domain.model.MuscleGroup
import com.fitos.app.domain.model.WorkoutSet
import java.time.LocalDate

/** Pure training maths. Only completed, non-warm-up sets count towards volume and records. */
object TrainingCalc {

    private fun WorkoutSet.counts() = completed && !isWarmup

    /** Sum of weight × reps in kg. Sets without a weight (bodyweight) contribute 0 volume but still count as sets. */
    fun volumeKg(sets: List<WorkoutSet>): Double =
        sets.filter { it.counts() }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    fun workingSetCount(sets: List<WorkoutSet>): Int = sets.count { it.counts() }

    /**
     * Epley estimate of a one-rep max. Returns null when it can't be trusted: missing/zero weight,
     * no reps, or more than 12 reps (estimates degrade quickly at high reps).
     */
    fun estimatedOneRepMaxKg(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null || weightKg <= 0.0 || reps <= 0 || reps > MAX_E1RM_REPS) return null
        return if (reps == 1) weightKg else weightKg * (1.0 + reps / 30.0)
    }

    data class Records(val maxWeightKg: Double?, val bestOneRepMaxKg: Double?, val bestSetVolumeKg: Double?, val maxReps: Int?)

    fun records(sets: List<WorkoutSet>): Records {
        val valid = sets.filter { it.counts() }
        return Records(
            maxWeightKg = valid.mapNotNull { it.weightKg }.filter { it > 0 }.maxOrNull(),
            bestOneRepMaxKg = valid.mapNotNull { estimatedOneRepMaxKg(it.weightKg, it.reps) }.maxOrNull(),
            bestSetVolumeKg = valid.mapNotNull { s -> s.weightKg?.let { w -> s.reps?.let { r -> w * r } } }.filter { it > 0 }.maxOrNull(),
            maxReps = valid.mapNotNull { it.reps }.maxOrNull()
        )
    }

    enum class PrKind(val label: String) { WEIGHT("Heaviest weight"), ONE_REP_MAX("Estimated 1RM"), REPS("Most reps") }

    data class NewRecord(val exerciseId: Long, val exerciseName: String, val kind: PrKind, val value: Double)

    /**
     * Records set in [session] that beat everything in [history]. With no history for an exercise nothing is a
     * record: the first time you do a lift is a baseline, not an achievement.
     */
    fun newRecords(history: List<WorkoutSet>, session: List<WorkoutSet>): List<NewRecord> {
        val byExercise = session.filter { it.counts() }.groupBy { it.exerciseId }
        return byExercise.flatMap { (id, sets) ->
            val before = history.filter { it.exerciseId == id }
            if (before.none { it.counts() }) return@flatMap emptyList()
            val old = records(before)
            val now = records(sets)
            val name = sets.first().exerciseName
            buildList {
                if (now.maxWeightKg != null && now.maxWeightKg > (old.maxWeightKg ?: 0.0) + EPS) add(NewRecord(id, name, PrKind.WEIGHT, now.maxWeightKg))
                if (now.bestOneRepMaxKg != null && now.bestOneRepMaxKg > (old.bestOneRepMaxKg ?: 0.0) + EPS) add(NewRecord(id, name, PrKind.ONE_REP_MAX, now.bestOneRepMaxKg))
                // Rep records only for unweighted work, otherwise weight records already cover it.
                if (old.maxWeightKg == null && now.maxReps != null && now.maxReps > (old.maxReps ?: 0)) add(NewRecord(id, name, PrKind.REPS, now.maxReps.toDouble()))
            }
        }
    }

    /** Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    data class WeekStat(val weekStart: LocalDate, val sessions: Int, val volumeKg: Double, val sets: Int)

    /**
     * Weekly totals for [weeks] weeks ending with the week of [today]. Weeks with no sessions are included
     * with zeros so charts show the real gap.
     */
    fun weeklyStats(sessions: List<Pair<LocalDate, List<WorkoutSet>>>, today: LocalDate, weeks: Int): List<WeekStat> {
        val thisWeek = weekStart(today)
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val inWeek = sessions.filter { weekStart(it.first) == start }
            WeekStat(start, inWeek.size, inWeek.sumOf { volumeKg(it.second) }, inWeek.sumOf { workingSetCount(it.second) })
        }
    }

    /** Working sets per primary muscle group. Exercises are mapped by [muscleOf]; unknown exercises are skipped. */
    fun setsPerMuscle(sets: List<WorkoutSet>, muscleOf: (Long) -> MuscleGroup?): Map<MuscleGroup, Int> =
        sets.filter { it.counts() }.mapNotNull { muscleOf(it.exerciseId) }.groupingBy { it }.eachCount()

    const val MAX_E1RM_REPS = 12
    private const val EPS = 0.0001
}
