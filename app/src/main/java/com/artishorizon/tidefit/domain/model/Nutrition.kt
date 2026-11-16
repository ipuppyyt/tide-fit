package com.artishorizon.tidefit.domain.model

import java.time.Instant
import java.time.LocalDate

/** How trustworthy a food's numbers are. Estimated values must never be shown as lab-accurate. */
enum class NutritionSource(val label: String, val description: String) {
    VERIFIED("Verified", "Copied from the product label or measured"),
    SOURCED("Sourced", "Taken from a named reference database"),
    ESTIMATED("Estimated", "Approximate typical values")
}

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    MORNING_SNACK("Morning snack"),
    LUNCH("Lunch"),
    EVENING_SNACK("Evening snack"),
    DINNER("Dinner"),
    OTHER("Other")
}

/** Nutrition values for some quantity of food. Optional nutrients are null when unknown. */
data class Nutrition(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val saturatedFat: Double? = null,
    val fibre: Double? = null,
    val sugar: Double? = null,
    val sodiumMg: Double? = null
) {
    operator fun times(factor: Double) = Nutrition(
        calories * factor, protein * factor, carbs * factor, fat * factor,
        saturatedFat?.times(factor), fibre?.times(factor), sugar?.times(factor), sodiumMg?.times(factor)
    )

    /** Sums values; an optional nutrient stays null only if neither side knows it. */
    operator fun plus(o: Nutrition) = Nutrition(
        calories + o.calories, protein + o.protein, carbs + o.carbs, fat + o.fat,
        addOpt(saturatedFat, o.saturatedFat), addOpt(fibre, o.fibre), addOpt(sugar, o.sugar), addOpt(sodiumMg, o.sodiumMg)
    )

    companion object {
        val ZERO = Nutrition(0.0, 0.0, 0.0, 0.0)
        private fun addOpt(a: Double?, b: Double?): Double? = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
    }
}

data class Food(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val servingSize: Double,
    val servingUnit: String,
    /** Weight of one serving in grams (liquids: 1 ml is treated as 1 g). */
    val servingWeightG: Double,
    /** Nutrition per 100 g, the canonical basis for all portion maths. */
    val per100: Nutrition,
    val source: NutritionSource,
    val sourceNote: String? = null,
    val isFavourite: Boolean = false,
    val isCustom: Boolean = false,
    val isRecipe: Boolean = false
) {
    val servingLabel: String get() = "${formatAmount(servingSize)} $servingUnit (${formatAmount(servingWeightG)} g)"
}

/** One logged food. [total] is a snapshot, so later edits to the food never change history. */
data class FoodEntry(
    val id: Long = 0,
    val date: LocalDate,
    val meal: Meal,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val quantityG: Double?,
    val quantityLabel: String?,
    val total: Nutrition,
    val source: NutritionSource,
    val loggedAt: Instant
)

enum class BeverageKind(val label: String) { WATER("Water"), OTHER("Other beverage") }

data class WaterEntry(
    val id: Long = 0,
    val date: LocalDate,
    val amountMl: Int,
    val kind: BeverageKind,
    val label: String?,
    val loggedAt: Instant
)

data class RecipeIngredient(
    val sourceFoodId: Long?,
    val name: String,
    val grams: Double,
    val per100: Nutrition
)

data class RecipeDraft(
    val foodId: Long = 0,
    val name: String,
    val servings: Int,
    /** Cooked weight; null means "sum of ingredient weights". */
    val cookedWeightG: Double?,
    val ingredients: List<RecipeIngredient>
)

fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(java.util.Locale.US, value)
