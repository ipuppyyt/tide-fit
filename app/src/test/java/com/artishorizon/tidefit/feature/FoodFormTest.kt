package com.artishorizon.tidefit.feature

import com.artishorizon.tidefit.feature.nutrition.FoodForm
import com.artishorizon.tidefit.feature.nutrition.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodFormTest {
    private fun valid() = FoodForm(name = "Test", calories = "200", protein = "10", carbs = "20", fat = "5", servingWeightG = "50")

    private fun ok(f: FoodForm) = (f.toFood(null) as FoodForm.Result.Ok).food

    @Test fun blankNameIsRejected() {
        assertTrue(valid().copy(name = " ").toFood(null) is FoodForm.Result.Error)
    }

    @Test fun missingCaloriesIsRejected() {
        assertTrue(valid().copy(calories = "").toFood(null) is FoodForm.Result.Error)
    }

    @Test fun zeroServingWeightIsRejected() {
        assertTrue(valid().copy(servingWeightG = "0").toFood(null) is FoodForm.Result.Error)
    }

    @Test fun per100gValuesAreStoredAsEntered() {
        assertEquals(200.0, ok(valid()).per100.calories, 0.001)
    }

    @Test fun perServingValuesAreConvertedToPer100g() {
        // 200 kcal in a 50 g serving = 400 kcal per 100 g.
        val food = ok(valid().copy(basis = NutritionBasis.PER_SERVING))
        assertEquals(400.0, food.per100.calories, 0.001)
        assertEquals(20.0, food.per100.protein, 0.001)
    }

    @Test fun blankOptionalNutrientsStayUnknown() {
        val food = ok(valid())
        assertNull(food.per100.fibre)
        assertNull(food.per100.sodiumMg)
    }

    @Test fun invalidOptionalNutrientIsRejected() {
        assertTrue(valid().copy(fibre = "abc").toFood(null) is FoodForm.Result.Error)
    }

    @Test fun commaDecimalIsAccepted() {
        assertEquals(12.5, ok(valid().copy(protein = "12,5")).per100.protein, 0.001)
    }
}
