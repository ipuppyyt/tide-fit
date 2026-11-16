package com.artishorizon.tidefit.data

import com.artishorizon.tidefit.core.database.FoodDao
import com.artishorizon.tidefit.core.database.FoodEntity
import com.artishorizon.tidefit.domain.model.Nutrition
import com.artishorizon.tidefit.domain.model.NutritionSource
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starter food list (common Indian and Kerala foods plus staples). Values are rounded typical
 * per-100 g figures (cooked unless the name says raw), so every item is marked ESTIMATED. Users should
 * edit a food to match their own recipe or package label and may then mark it Verified.
 */
@Singleton
class FoodSeeder @Inject constructor(private val dao: FoodDao, private val clock: Clock) {

    suspend fun seedIfNeeded() {
        if (dao.countSeed() > 0) return
        val now = Instant.now(clock)
        dao.insertAll(SEED.map { it.toEntity(now) })
    }

    internal class Seed(
        val name: String, val size: Double, val unit: String, val weightG: Double,
        val n: Nutrition
    ) {
        fun toEntity(now: Instant) = FoodEntity(
            name = name, brand = null, servingSize = size, servingUnit = unit, servingWeightG = weightG,
            per100 = n, source = NutritionSource.ESTIMATED.name,
            sourceNote = "Typical approximate values per 100 g; edit to match your portion or package.",
            isFavourite = false, isCustom = false, isSeed = true, isRecipe = false,
            lastUsedAt = null, useCount = 0, createdAt = now, updatedAt = now
        )
    }

    companion object {
        @Suppress("LongParameterList")
        private fun f(
            name: String, size: Double, unit: String, weightG: Double,
            kcal: Double, p: Double, c: Double, fat: Double,
            fibre: Double? = null, sugar: Double? = null, sat: Double? = null, sodium: Double? = null
        ) = Seed(name, size, unit, weightG, Nutrition(kcal, p, c, fat, sat, fibre, sugar, sodium))

        internal val SEED: List<Seed> = listOf(
            // Rice, tiffin and Kerala staples
            f("Matta rice (Kerala red rice), cooked", 1.0, "cup", 150.0, 125.0, 2.8, 26.0, 0.9, fibre = 1.8),
            f("White rice, cooked", 1.0, "cup", 150.0, 130.0, 2.7, 28.0, 0.3, fibre = 0.4),
            f("Idli", 1.0, "idli", 40.0, 130.0, 4.0, 27.0, 0.4, fibre = 1.0),
            f("Dosa, plain", 1.0, "dosa", 90.0, 168.0, 3.9, 29.0, 3.7, fibre = 1.0),
            f("Appam", 1.0, "appam", 60.0, 135.0, 2.6, 27.0, 1.8, fibre = 0.8),
            f("Puttu (rice, with coconut)", 1.0, "piece", 100.0, 160.0, 3.0, 30.0, 2.5, fibre = 1.5),
            f("Kadala curry (black chickpea)", 1.0, "cup", 150.0, 135.0, 7.0, 17.0, 4.5, fibre = 5.0),
            f("Sambar", 1.0, "cup", 200.0, 55.0, 2.7, 8.0, 1.4, fibre = 2.0),
            f("Chapati / roti", 1.0, "roti", 40.0, 300.0, 9.5, 50.0, 7.0, fibre = 4.0),
            f("Parotta (Kerala)", 1.0, "parotta", 100.0, 330.0, 7.0, 45.0, 14.0, fibre = 2.0),
            f("Upma", 1.0, "cup", 200.0, 120.0, 3.0, 18.0, 4.0, fibre = 1.5),
            f("Poha", 1.0, "cup", 150.0, 110.0, 2.5, 22.0, 2.0, fibre = 1.2),
            f("Chicken biryani", 1.0, "cup", 200.0, 170.0, 8.0, 22.0, 5.0),
            f("Bread, whole wheat", 1.0, "slice", 30.0, 247.0, 13.0, 41.0, 3.4, fibre = 7.0),
            // Protein foods
            f("Egg, whole, boiled", 1.0, "egg", 50.0, 155.0, 13.0, 1.1, 11.0, sat = 3.3, sodium = 124.0),
            f("Egg white, boiled", 1.0, "white", 33.0, 52.0, 11.0, 0.7, 0.2),
            f("Chicken breast, cooked, skinless", 100.0, "g", 100.0, 165.0, 31.0, 0.0, 3.6, sat = 1.0),
            f("Chicken curry", 1.0, "serving", 150.0, 150.0, 14.0, 5.0, 8.0),
            f("Fish, white, grilled or steamed", 100.0, "g", 100.0, 120.0, 24.0, 0.0, 2.5),
            f("Fish curry (Kerala style)", 1.0, "serving", 150.0, 110.0, 12.0, 3.0, 5.0),
            f("Fish fry (Kerala style)", 100.0, "g", 100.0, 190.0, 20.0, 4.0, 10.0),
            f("Paneer", 50.0, "g", 50.0, 296.0, 18.0, 1.2, 22.0, sat = 14.0),
            f("Whey protein powder", 1.0, "scoop", 30.0, 400.0, 80.0, 8.0, 6.0),
            // Dairy
            f("Milk, whole", 1.0, "cup", 244.0, 61.0, 3.2, 4.8, 3.3, sugar = 5.0, sat = 1.9, sodium = 43.0),
            f("Milk, skimmed", 1.0, "cup", 244.0, 34.0, 3.4, 5.0, 0.1, sugar = 5.0),
            f("Curd / plain yoghurt", 1.0, "cup", 245.0, 61.0, 3.5, 4.7, 3.3, sugar = 4.7, sat = 2.1),
            f("Buttermilk", 1.0, "glass", 200.0, 40.0, 3.3, 4.8, 0.9),
            f("Tea with milk and sugar", 1.0, "cup", 150.0, 45.0, 1.2, 7.5, 1.2, sugar = 7.0),
            f("Coffee, black", 1.0, "cup", 240.0, 1.0, 0.1, 0.0, 0.0),
            // Pulses
            f("Lentils (dal), cooked", 1.0, "cup", 150.0, 116.0, 9.0, 20.0, 0.4, fibre = 7.9),
            f("Moong dal, cooked", 1.0, "cup", 150.0, 105.0, 7.0, 19.0, 0.4, fibre = 7.6),
            f("Chickpeas, cooked", 100.0, "g", 100.0, 164.0, 8.9, 27.4, 2.6, fibre = 7.6, sugar = 4.8),
            f("Rajma (kidney beans), cooked", 1.0, "cup", 150.0, 127.0, 8.7, 22.8, 0.5, fibre = 6.4),
            f("Sprouts, moong, raw", 1.0, "cup", 100.0, 30.0, 3.0, 6.0, 0.2, fibre = 1.8),
            // Grains
            f("Oats, rolled, raw", 40.0, "g", 40.0, 389.0, 16.9, 66.3, 6.9, fibre = 10.6, sugar = 1.0, sat = 1.2),
            // Fruit
            f("Banana", 1.0, "medium", 118.0, 89.0, 1.1, 22.8, 0.3, fibre = 2.6, sugar = 12.2),
            f("Apple", 1.0, "medium", 180.0, 52.0, 0.3, 13.8, 0.2, fibre = 2.4, sugar = 10.4),
            f("Mango", 1.0, "cup", 165.0, 60.0, 0.8, 15.0, 0.4, fibre = 1.6, sugar = 13.7),
            // Nuts, fats
            f("Almonds", 28.0, "g", 28.0, 579.0, 21.2, 21.6, 49.9, fibre = 12.5, sugar = 4.4, sat = 3.8),
            f("Cashews", 28.0, "g", 28.0, 553.0, 18.2, 30.2, 43.9, fibre = 3.3, sat = 7.8),
            f("Peanuts", 28.0, "g", 28.0, 567.0, 25.8, 16.1, 49.2, fibre = 8.5, sat = 6.8),
            f("Walnuts", 28.0, "g", 28.0, 654.0, 15.2, 13.7, 65.2, fibre = 6.7, sat = 6.1),
            f("Peanut butter", 1.0, "tbsp", 16.0, 588.0, 25.0, 20.0, 50.0, fibre = 6.0, sugar = 9.0, sat = 10.0),
            f("Coconut, grated, fresh", 30.0, "g", 30.0, 354.0, 3.3, 15.2, 33.5, fibre = 9.0, sat = 29.7),
            f("Coconut oil", 1.0, "tsp", 5.0, 862.0, 0.0, 0.0, 100.0, sat = 82.5),
            f("Ghee", 1.0, "tsp", 5.0, 900.0, 0.0, 0.0, 100.0, sat = 62.0),
            f("Cooking oil", 1.0, "tsp", 5.0, 884.0, 0.0, 0.0, 100.0, sat = 14.0),
            f("Sugar", 1.0, "tsp", 4.0, 387.0, 0.0, 100.0, 0.0, sugar = 100.0),
            // Vegetables
            f("Spinach, raw", 1.0, "cup", 30.0, 23.0, 2.9, 3.6, 0.4, fibre = 2.2),
            f("Broccoli", 1.0, "cup", 90.0, 34.0, 2.8, 6.6, 0.4, fibre = 2.6),
            f("Carrot", 1.0, "medium", 60.0, 41.0, 0.9, 9.6, 0.2, fibre = 2.8, sugar = 4.7),
            f("Tomato", 1.0, "medium", 120.0, 18.0, 0.9, 3.9, 0.2, fibre = 1.2, sugar = 2.6),
            f("Onion", 1.0, "medium", 110.0, 40.0, 1.1, 9.3, 0.1, fibre = 1.7, sugar = 4.2),
            f("Cucumber", 1.0, "cup", 100.0, 15.0, 0.7, 3.6, 0.1, fibre = 0.5),
            f("Cabbage, raw", 1.0, "cup", 90.0, 25.0, 1.3, 5.8, 0.1, fibre = 2.5),
            f("Okra (ladies finger), cooked", 1.0, "cup", 100.0, 33.0, 1.9, 7.5, 0.2, fibre = 3.2),
            f("Potato, boiled", 1.0, "medium", 150.0, 87.0, 1.9, 20.1, 0.1, fibre = 1.8),
            f("Sweet potato, boiled", 1.0, "medium", 130.0, 86.0, 1.6, 20.0, 0.1, fibre = 3.0, sugar = 6.5),
            f("Mixed vegetable curry", 1.0, "serving", 150.0, 70.0, 2.0, 8.0, 3.5, fibre = 2.5)
        )
    }
}
