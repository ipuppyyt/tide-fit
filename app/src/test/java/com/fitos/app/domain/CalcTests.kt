package com.fitos.app.domain

import com.fitos.app.domain.calc.TargetCalculator
import com.fitos.app.domain.calc.TargetInput
import com.fitos.app.domain.calc.Units
import com.fitos.app.domain.model.ActivityLevel
import com.fitos.app.domain.model.BiologicalSex
import com.fitos.app.domain.model.FitnessGoalType
import com.fitos.app.domain.model.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetCalculatorTest {
    private val base = TargetInput(30, BiologicalSex.MALE, 175.0, 80.0, ActivityLevel.MODERATE, FitnessGoalType.MAINTENANCE)

    @Test fun `mifflin st jeor for male`() {
        // 10*80 + 6.25*175 - 5*30 + 5 = 1748.75
        assertEquals(1748.75, TargetCalculator.bmr(base), 0.001)
    }

    @Test fun `mifflin st jeor for female and unspecified`() {
        assertEquals(1748.75 - 166, TargetCalculator.bmr(base.copy(sex = BiologicalSex.FEMALE)), 0.001)
        assertEquals(1748.75 - 83, TargetCalculator.bmr(base.copy(sex = null)), 0.001)
    }

    @Test fun `maintenance calories equal tdee and are marked as estimate`() {
        val s = TargetCalculator.suggest(base)
        assertEquals(Math.round(1748.75 * 1.55).toInt(), s.tdee)
        assertEquals(s.tdee, s.targets.caloriesKcal)
        assertEquals(ValueSource.FORMULA_ESTIMATE, s.targets.source)
    }

    @Test fun `fat loss applies a 20 percent deficit and higher protein`() {
        val s = TargetCalculator.suggest(base.copy(goal = FitnessGoalType.FAT_LOSS))
        assertEquals(Math.round(1748.75 * 1.55 * 0.8).toInt(), s.targets.caloriesKcal)
        assertEquals(160, s.targets.proteinG) // 2.0 g/kg
    }

    @Test fun `macros roughly reconstruct the calorie target`() {
        val t = TargetCalculator.suggest(base.copy(goal = FitnessGoalType.MUSCLE_BUILDING)).targets
        val kcal = t.proteinG * 4 + t.carbohydratesG * 4 + t.fatG * 9
        assertTrue("macro kcal $kcal vs ${t.caloriesKcal}", kotlin.math.abs(kcal - t.caloriesKcal) <= 12)
    }

    @Test fun `calories never drop below the safety floor`() {
        val tiny = TargetInput(60, BiologicalSex.FEMALE, 140.0, 38.0, ActivityLevel.SEDENTARY, FitnessGoalType.FAT_LOSS)
        assertEquals(TargetCalculator.MIN_CALORIES, TargetCalculator.suggest(tiny).targets.caloriesKcal)
    }

    @Test fun `water is rounded to 50 ml`() {
        assertEquals(0, TargetCalculator.suggest(base).targets.waterMl % 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid inputs are rejected`() {
        TargetCalculator.suggest(base.copy(weightKg = 0.0))
    }
}

class UnitsTest {
    @Test fun `lb and kg round trip`() {
        assertEquals(100.0, Units.kgToLb(Units.lbToKg(100.0)), 1e-9)
        assertEquals(45.359237, Units.lbToKg(100.0), 1e-6)
    }

    @Test fun `feet and inches convert to cm`() {
        assertEquals(177.8, Units.feetInchesToCm(5.0, 10.0), 1e-9)
        assertEquals(5 to 10, Units.cmToFeetInches(177.8))
    }

    @Test fun `inches carry into feet`() {
        // 182 cm = 71.65 in -> 72 in -> 6 ft 0 in (not 5 ft 12 in)
        assertEquals(6 to 0, Units.cmToFeetInches(182.0))
    }
}
