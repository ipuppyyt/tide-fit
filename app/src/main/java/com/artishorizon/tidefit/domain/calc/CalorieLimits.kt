package com.artishorizon.tidefit.domain.calc

import com.artishorizon.tidefit.domain.model.FitnessGoalType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which side of the calorie line matters for this goal. */
enum class LimitKind(val label: String) {
    /** Cutting: eating more than the target slows or stops fat loss. */
    CEILING("Daily maximum"),
    /** Bulking: eating less than the target means you are not in a surplus. */
    FLOOR("Daily minimum"),
    /** Maintenance and recomposition: stay inside a band around the target. */
    RANGE("Daily range")
}

enum class LimitStatus { UNDER, ON_TRACK, OVER }

/**
 * The calorie rule for a goal. [low]/[high] are the edges of the acceptable zone and [target] is the aim.
 *
 * - Cut: [high] = target (a hard ceiling); [low] = safety floor, below which intake is too aggressive.
 * - Bulk: [low] = target (a floor); [high] = ceiling past which extra surplus is mostly stored as fat.
 * - Range: ±[RANGE_PCT] around the target.
 */
data class CalorieLimits(
    val kind: LimitKind,
    val target: Int,
    val low: Int,
    val high: Int,
    /** Expected weight change per week at the target intake, in kg (negative = loss). Null if maintenance is unknown. */
    val expectedWeeklyChangeKg: Double?,
    val notes: List<String>
) {
    /** Where [eaten] sits against the rule. */
    fun status(eaten: Int): LimitStatus = when {
        eaten > high -> LimitStatus.OVER
        eaten < low -> LimitStatus.UNDER
        else -> LimitStatus.ON_TRACK
    }

    /** One line a person can act on, e.g. "412 kcal left before your maximum". */
    fun headline(eaten: Int): String = when (kind) {
        LimitKind.CEILING ->
            if (eaten <= high) "${high - eaten} kcal left before your maximum" else "${eaten - high} kcal over your maximum"
        LimitKind.FLOOR ->
            if (eaten < low) "${low - eaten} kcal to reach your minimum" else "Minimum reached"
        LimitKind.RANGE ->
            when {
                eaten < low -> "${low - eaten} kcal to reach your range"
                eaten > high -> "${eaten - high} kcal over your range"
                else -> "Inside your range"
            }
    }

    /** Short label for the number the user is steering by. */
    val ruleText: String
        get() = when (kind) {
            LimitKind.CEILING -> "Eat no more than $high kcal"
            LimitKind.FLOOR -> "Eat at least $low kcal"
            LimitKind.RANGE -> "Stay between $low and $high kcal"
        }

    companion object {
        const val RANGE_PCT = 0.05
        private const val KCAL_PER_KG_FAT = 7700.0

        /** Lowest intake we will present as a normal cutting target (common clinical convention, not a prescription). */
        fun safetyFloor(sex: com.artishorizon.tidefit.domain.model.BiologicalSex?, bmr: Double): Int {
            val byRule = when (sex) {
                com.artishorizon.tidefit.domain.model.BiologicalSex.MALE -> 1500
                com.artishorizon.tidefit.domain.model.BiologicalSex.FEMALE -> 1200
                null -> 1350
            }
            return max(byRule, (bmr * 1.0).roundToInt().coerceAtMost(byRule + 300))
        }

        /**
         * Builds the rule from the stored calorie target. [tdee] (maintenance) is optional: with it the edges are
         * grounded in real maintenance, without it they fall back to percentages of the target.
         */
        fun forGoal(goal: FitnessGoalType, targetKcal: Int, tdee: Int?, floorKcal: Int): CalorieLimits {
            val weekly = tdee?.let { (targetKcal - it) * 7 / KCAL_PER_KG_FAT }
            return when (goal) {
                FitnessGoalType.FAT_LOSS -> CalorieLimits(
                    LimitKind.CEILING, targetKcal, low = min(floorKcal, targetKcal), high = targetKcal, expectedWeeklyChangeKg = weekly,
                    notes = listOf(
                        "Staying at or under $targetKcal kcal keeps you in a deficit.",
                        "Going far below ${min(floorKcal, targetKcal)} kcal costs muscle and is hard to sustain.",
                        "A healthy loss rate is about 0.5–1% of body weight per week."
                    )
                )
                FitnessGoalType.MUSCLE_BUILDING -> {
                    val ceiling = tdee?.let { (it * 1.20).roundToInt() } ?: (targetKcal * 1.10).roundToInt()
                    CalorieLimits(
                        LimitKind.FLOOR, targetKcal, low = targetKcal, high = max(ceiling, targetKcal), expectedWeeklyChangeKg = weekly,
                        notes = listOf(
                            "Eating at least $targetKcal kcal keeps you in a surplus so muscle can grow.",
                            "Above ${max(ceiling, targetKcal)} kcal, extra surplus is mostly stored as fat.",
                            "A lean gain is about 0.25–0.5% of body weight per week."
                        )
                    )
                }
                FitnessGoalType.RECOMPOSITION, FitnessGoalType.MAINTENANCE, FitnessGoalType.GENERAL_FITNESS -> {
                    val pad = (targetKcal * RANGE_PCT).roundToInt()
                    CalorieLimits(
                        LimitKind.RANGE, targetKcal, low = targetKcal - pad, high = targetKcal + pad, expectedWeeklyChangeKg = weekly,
                        notes = listOf("Staying within ${(RANGE_PCT * 100).roundToInt()}% of $targetKcal kcal holds your weight steady.")
                    )
                }
            }
        }

        /** Convenience used by the UI. */
        fun weeklyPercent(weeklyKg: Double?, weightKg: Double): Double? = weeklyKg?.let { abs(it) / weightKg * 100 }
    }
}

/** Builds the rule for a stored profile and calorie target (maintenance and safety floor come from the profile). */
fun calorieLimitsFor(p: com.artishorizon.tidefit.domain.model.UserProfile, targetKcal: Int): CalorieLimits {
    val input = TargetInput(p.age, p.sex, p.heightCm, p.currentWeightKg, p.activityLevel, p.goal)
    val bmr = TargetCalculator.bmr(input)
    val tdee = (bmr * p.activityLevel.factor).roundToInt()
    return CalorieLimits.forGoal(p.goal, targetKcal, tdee, CalorieLimits.safetyFloor(p.sex, bmr))
}
