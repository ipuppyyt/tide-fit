package com.fitos.app.domain

import com.fitos.app.domain.calc.PortionCalculator
import com.fitos.app.domain.calc.RecipeCalculator
import com.fitos.app.domain.model.Nutrition
import com.fitos.app.domain.model.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PortionCalculatorTest {
    private val per100 = Nutrition(calories = 200.0, protein = 20.0, carbs = 0.0, fat = 10.0)

    @Test fun `150 g of a 100 g profile scales exactly`() {
        val n = PortionCalculator.forGrams(per100, 150.0)!!
        assertEquals(300.0, n.calories, 1e-9)
        assertEquals(30.0, n.protein, 1e-9)
        assertEquals(15.0, n.fat, 1e-9)
    }

    @Test fun `missing optional nutrients stay null`() {
        val n = PortionCalculator.forGrams(per100, 50.0)!!
        assertNull(n.fibre); assertNull(n.sodiumMg)
        assertEquals(1.0, PortionCalculator.forGrams(per100.copy(fibre = 2.0), 50.0)!!.fibre!!, 1e-9)
    }

    @Test fun `negative and non finite quantities are rejected`() {
        assertNull(PortionCalculator.forGrams(per100, -1.0))
        assertNull(PortionCalculator.forGrams(per100, Double.NaN))
        assertNull(PortionCalculator.forGrams(per100, Double.POSITIVE_INFINITY))
        assertNull(PortionCalculator.gramsForServings(40.0, -1.0))
        assertNull(PortionCalculator.gramsForServings(0.0, 1.0))
    }

    @Test fun `zero grams gives zero nutrition`() {
        assertEquals(0.0, PortionCalculator.forGrams(per100, 0.0)!!.calories, 0.0)
    }

    @Test fun `servings convert to grams`() {
        assertEquals(120.0, PortionCalculator.gramsForServings(40.0, 3.0)!!, 1e-9)
        assertEquals(20.0, PortionCalculator.gramsForServings(40.0, 0.5)!!, 1e-9)
    }

    @Test fun `rescale uses the entry's own snapshot`() {
        val total = PortionCalculator.forGrams(per100, 150.0)!!
        val doubled = PortionCalculator.rescale(total, 150.0, 300.0)!!
        assertEquals(600.0, doubled.calories, 1e-9)
        assertNull(PortionCalculator.rescale(total, 0.0, 100.0))
    }

    @Test fun `sum treats unknown optional nutrients honestly`() {
        val a = Nutrition(100.0, 1.0, 1.0, 1.0, fibre = 2.0)
        val b = Nutrition(50.0, 1.0, 1.0, 1.0)
        val s = PortionCalculator.sum(listOf(a, b))
        assertEquals(150.0, s.calories, 1e-9)
        assertEquals(2.0, s.fibre!!, 1e-9) // only known values are summed
        assertNull(s.sugar)
        assertEquals(0.0, PortionCalculator.sum(emptyList()).calories, 0.0)
    }
}

class RecipeCalculatorTest {
    private val rice = RecipeIngredient(1, "Rice", 200.0, Nutrition(130.0, 3.0, 28.0, 0.3))
    private val chicken = RecipeIngredient(2, "Chicken", 100.0, Nutrition(165.0, 31.0, 0.0, 3.6))

    @Test fun `totals per serving and per 100 g`() {
        val r = RecipeCalculator.calculate(listOf(rice, chicken), servings = 2, cookedWeightG = null)!!
        assertEquals(425.0, r.total.calories, 1e-9) // 260 + 165
        assertEquals(212.5, r.perServing.calories, 1e-9)
        assertEquals(300.0, r.totalWeightG, 1e-9)
        assertEquals(150.0, r.servingWeightG, 1e-9)
        assertEquals(425.0 / 3.0, r.per100.calories, 1e-9)
    }

    @Test fun `cooked weight changes density but not totals`() {
        val raw = RecipeCalculator.calculate(listOf(rice, chicken), 2, null)!!
        val cooked = RecipeCalculator.calculate(listOf(rice, chicken), 2, 400.0)!!
        assertEquals(raw.total.calories, cooked.total.calories, 1e-9)
        assertEquals(425.0 / 4.0, cooked.per100.calories, 1e-9)
        assertEquals(200.0, cooked.servingWeightG, 1e-9)
    }

    @Test fun `a custom portion of the recipe scales from per 100 g`() {
        val r = RecipeCalculator.calculate(listOf(rice, chicken), 2, null)!!
        val portion = PortionCalculator.forGrams(r.per100, 75.0)!!
        assertEquals(r.perServing.calories / 2, portion.calories, 1e-9)
    }

    @Test fun `invalid recipes return null`() {
        assertNull(RecipeCalculator.calculate(emptyList(), 2, null))
        assertNull(RecipeCalculator.calculate(listOf(rice), 0, null))
        assertNull(RecipeCalculator.calculate(listOf(rice), 2, 0.0))
        assertNull(RecipeCalculator.calculate(listOf(rice.copy(grams = -5.0)), 2, null))
        assertNotNull(RecipeCalculator.calculate(listOf(rice), 1, null))
    }
}
