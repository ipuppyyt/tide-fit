package com.fitos.app.domain.calc

import com.fitos.app.domain.model.ActivityLevel
import com.fitos.app.domain.model.BiologicalSex
import com.fitos.app.domain.model.DailyTargets
import com.fitos.app.domain.model.FitnessGoalType
import com.fitos.app.domain.model.ValueSource
import kotlin.math.max
import kotlin.math.roundToInt

data class TargetInput(
    val age: Int,
    val sex: BiologicalSex?,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val goal: FitnessGoalType
)

data class TargetSuggestion(
    val bmr: Int,
    val tdee: Int,
    val targets: DailyTargets,
    /** Human-readable explanation of each step so the user can see exactly where numbers came from. */
    val explanation: List<String>
)

/**
 * Transparent starting-point estimates. These are NOT medical advice and are only a first guess;
 * the user is expected to adjust them from real weight-trend data.
 */
object TargetCalculator {

    fun bmr(input: TargetInput): Double {
        val base = 10.0 * input.weightKg + 6.25 * input.heightCm - 5.0 * input.age
        val sexTerm = when (input.sex) {
            BiologicalSex.MALE -> 5.0
            BiologicalSex.FEMALE -> -161.0
            null -> -78.0 // midpoint when not specified
        }
        return base + sexTerm
    }

    private fun calorieAdjustment(goal: FitnessGoalType): Double = when (goal) {
        FitnessGoalType.FAT_LOSS -> -0.20
        FitnessGoalType.RECOMPOSITION -> -0.10
        FitnessGoalType.MUSCLE_BUILDING -> 0.10
        FitnessGoalType.MAINTENANCE, FitnessGoalType.GENERAL_FITNESS -> 0.0
    }

    private fun proteinPerKg(goal: FitnessGoalType): Double = when (goal) {
        FitnessGoalType.FAT_LOSS, FitnessGoalType.RECOMPOSITION -> 2.0
        FitnessGoalType.MUSCLE_BUILDING -> 1.8
        FitnessGoalType.MAINTENANCE -> 1.6
        FitnessGoalType.GENERAL_FITNESS -> 1.4
    }

    fun suggest(input: TargetInput): TargetSuggestion {
        require(input.weightKg > 0 && input.heightCm > 0 && input.age > 0) { "Body inputs must be positive" }

        val bmr = bmr(input)
        val tdee = bmr * input.activityLevel.factor
        val adjustment = calorieAdjustment(input.goal)
        val floor = MIN_CALORIES
        val calories = max(floor.toDouble(), tdee * (1 + adjustment)).roundToInt()

        val protein = (proteinPerKg(input.goal) * input.weightKg).roundToInt()
        // Fat: 25% of calories, but never below 0.6 g/kg for hormonal health.
        val fat = max((calories * 0.25 / 9.0), 0.6 * input.weightKg).roundToInt()
        val carbKcal = calories - protein * 4 - fat * 9
        val carbs = max(0, (carbKcal / 4.0).roundToInt())
        val fibre = (calories / 1000.0 * 14.0).roundToInt()
        val water = ((input.weightKg * 35.0) / 50.0).roundToInt() * 50

        val explanation = buildList {
            add("BMR ${bmr.roundToInt()} kcal (Mifflin–St Jeor${if (input.sex == null) ", sex not given: midpoint used" else ""}).")
            add("Maintenance ≈ ${tdee.roundToInt()} kcal (BMR × ${input.activityLevel.factor} activity factor).")
            val pct = (adjustment * 100).roundToInt()
            add(
                when {
                    pct < 0 -> "Goal adjustment: $pct% for ${input.goal.label.lowercase()}."
                    pct > 0 -> "Goal adjustment: +$pct% for ${input.goal.label.lowercase()}."
                    else -> "No calorie adjustment for ${input.goal.label.lowercase()}."
                }
            )
            if (tdee * (1 + adjustment) < floor) add("Calories raised to a $floor kcal minimum.")
            add("Protein ${proteinPerKg(input.goal)} g per kg body weight.")
            add("Fat 25% of calories (at least 0.6 g/kg); carbohydrates fill the remainder.")
            add("Fibre 14 g per 1000 kcal; water 35 ml per kg.")
            add("These are estimates. Adjust them using your actual weight trend over 2–3 weeks.")
        }

        return TargetSuggestion(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            targets = DailyTargets(
                caloriesKcal = calories,
                proteinG = protein,
                carbohydratesG = carbs,
                fatG = fat,
                fibreG = fibre,
                waterMl = water,
                source = ValueSource.FORMULA_ESTIMATE
            ),
            explanation = explanation
        )
    }

    const val MIN_CALORIES = 1200
}
