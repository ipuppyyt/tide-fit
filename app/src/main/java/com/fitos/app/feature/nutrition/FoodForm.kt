package com.fitos.app.feature.nutrition

import com.fitos.app.core.util.parseDecimal
import com.fitos.app.domain.model.Food
import com.fitos.app.domain.model.Nutrition
import com.fitos.app.domain.model.NutritionSource
import com.fitos.app.domain.model.formatAmount

enum class NutritionBasis(val label: String) { PER_100G("Per 100 g"), PER_SERVING("Per serving") }

/** Text-backed form for creating/editing a food. Validation and unit conversion live here, not in composables. */
data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val servingSize: String = "1",
    val servingUnit: String = "serving",
    val servingWeightG: String = "100",
    val basis: NutritionBasis = NutritionBasis.PER_100G,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val saturatedFat: String = "",
    val fibre: String = "",
    val sugar: String = "",
    val sodiumMg: String = "",
    val source: NutritionSource = NutritionSource.VERIFIED,
    val sourceNote: String = ""
) {
    sealed interface Result {
        data class Ok(val food: Food) : Result
        data class Error(val message: String) : Result
    }

    fun toFood(existing: Food?): Result {
        if (name.isBlank()) return Result.Error("Enter a name.")
        val size = servingSize.parseDecimal()?.takeIf { it > 0 } ?: return Result.Error("Serving size must be greater than zero.")
        if (servingUnit.isBlank()) return Result.Error("Enter a serving unit (e.g. g, cup, slice).")
        val weight = servingWeightG.parseDecimal()?.takeIf { it > 0 } ?: return Result.Error("Serving weight in grams must be greater than zero.")
        val kcal = calories.parseDecimal() ?: return Result.Error("Enter calories (a number, 0 or more).")
        fun required(s: String, label: String): Double? = if (s.isBlank()) 0.0 else s.parseDecimal()
        val p = required(protein, "Protein") ?: return Result.Error("Protein must be a number.")
        val c = required(carbs, "Carbohydrates") ?: return Result.Error("Carbohydrates must be a number.")
        val f = required(fat, "Fat") ?: return Result.Error("Fat must be a number.")
        // Optional nutrients: blank means "unknown" (null), not zero; non-blank must parse.
        fun optional(s: String): Pair<Boolean, Double?> = if (s.isBlank()) true to null else s.parseDecimal().let { (it != null) to it }
        val (okSat, sat) = optional(saturatedFat)
        val (okFib, fib) = optional(fibre)
        val (okSug, sug) = optional(sugar)
        val (okSod, sod) = optional(sodiumMg)
        if (!(okSat && okFib && okSug && okSod)) return Result.Error("Optional nutrients must be numbers or left blank.")

        val entered = Nutrition(kcal, p, c, f, sat, fib, sug, sod)
        val per100 = if (basis == NutritionBasis.PER_100G) entered else entered * (100.0 / weight)
        return Result.Ok(
            Food(
                id = existing?.id ?: 0, name = name.trim(), brand = brand.trim().ifEmpty { null }, servingSize = size,
                servingUnit = servingUnit.trim(), servingWeightG = weight, per100 = per100, source = source,
                sourceNote = sourceNote.trim().ifEmpty { null }, isFavourite = existing?.isFavourite ?: false,
                isCustom = existing?.isCustom ?: true, isRecipe = existing?.isRecipe ?: false
            )
        )
    }

    companion object {
        /** Existing foods are always shown per 100 g, which is how they are stored. */
        fun from(food: Food): FoodForm {
            fun s(v: Double?) = v?.let { formatAmount(Math.round(it * 100) / 100.0) }.orEmpty()
            val n = food.per100
            return FoodForm(
                name = food.name, brand = food.brand.orEmpty(), servingSize = formatAmount(food.servingSize), servingUnit = food.servingUnit,
                servingWeightG = formatAmount(Math.round(food.servingWeightG * 100) / 100.0), basis = NutritionBasis.PER_100G,
                calories = s(n.calories), protein = s(n.protein), carbs = s(n.carbs), fat = s(n.fat),
                saturatedFat = s(n.saturatedFat), fibre = s(n.fibre), sugar = s(n.sugar), sodiumMg = s(n.sodiumMg),
                source = food.source, sourceNote = food.sourceNote.orEmpty()
            )
        }
    }
}
