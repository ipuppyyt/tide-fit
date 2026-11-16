package com.artishorizon.tidefit.domain.calc

import com.artishorizon.tidefit.domain.model.Nutrition
import com.artishorizon.tidefit.domain.model.RecipeIngredient

object PortionCalculator {
    /** Nutrition for [grams] of a food with [per100] values, or null for negative / non-finite input. */
    fun forGrams(per100: Nutrition, grams: Double): Nutrition? =
        if (!grams.isFinite() || grams < 0) null else per100 * (grams / 100.0)

    fun gramsForServings(servingWeightG: Double, servings: Double): Double? =
        if (!servings.isFinite() || servings < 0 || !servingWeightG.isFinite() || servingWeightG <= 0) null
        else servingWeightG * servings

    /** Converts a total for [grams] back to per-100 g values; null if [grams] is not positive. */
    fun per100From(total: Nutrition, grams: Double): Nutrition? =
        if (!grams.isFinite() || grams <= 0) null else total * (100.0 / grams)

    /** Re-scales a logged total from its old quantity to a new one (used when editing an entry). */
    fun rescale(total: Nutrition, oldGrams: Double, newGrams: Double): Nutrition? =
        per100From(total, oldGrams)?.let { forGrams(it, newGrams) }

    fun sum(items: List<Nutrition>): Nutrition = items.fold(Nutrition.ZERO) { acc, n -> acc + n }
}

data class RecipeResult(
    val total: Nutrition,
    val totalWeightG: Double,
    val servings: Int,
    val perServing: Nutrition,
    val per100: Nutrition,
    val servingWeightG: Double
)

object RecipeCalculator {
    /**
     * @param cookedWeightG weight after cooking; when null the ingredient weights are summed.
     * Returns null for an empty recipe, non-positive servings/weights, or invalid ingredient grams.
     */
    fun calculate(ingredients: List<RecipeIngredient>, servings: Int, cookedWeightG: Double?): RecipeResult? {
        if (ingredients.isEmpty() || servings <= 0) return null
        val parts = ingredients.map { PortionCalculator.forGrams(it.per100, it.grams) ?: return null }
        val total = PortionCalculator.sum(parts)
        val rawWeight = ingredients.sumOf { it.grams }
        val weight = cookedWeightG ?: rawWeight
        if (!weight.isFinite() || weight <= 0) return null
        return RecipeResult(
            total = total,
            totalWeightG = weight,
            servings = servings,
            perServing = total * (1.0 / servings),
            per100 = total * (100.0 / weight),
            servingWeightG = weight / servings
        )
    }
}
