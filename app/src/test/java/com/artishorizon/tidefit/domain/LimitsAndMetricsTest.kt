package com.artishorizon.tidefit.domain

import com.artishorizon.tidefit.domain.calc.BmiCategory
import com.artishorizon.tidefit.domain.calc.BodyMetrics
import com.artishorizon.tidefit.domain.calc.CalorieLimits
import com.artishorizon.tidefit.domain.calc.LimitKind
import com.artishorizon.tidefit.domain.calc.LimitStatus
import com.artishorizon.tidefit.domain.calc.TargetCalculator
import com.artishorizon.tidefit.domain.calc.TargetInput
import com.artishorizon.tidefit.domain.model.ActivityLevel
import com.artishorizon.tidefit.domain.model.BiologicalSex
import com.artishorizon.tidefit.domain.model.FitnessGoalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieLimitsTest {
    private fun input(goal: FitnessGoalType, sex: BiologicalSex? = BiologicalSex.MALE, weight: Double = 80.0) =
        TargetInput(30, sex, 175.0, weight, ActivityLevel.MODERATE, goal)

    @Test fun `cutting gives a ceiling equal to the target`() {
        val s = TargetCalculator.suggest(input(FitnessGoalType.FAT_LOSS))
        assertEquals(LimitKind.CEILING, s.limits.kind)
        assertEquals(s.targets.caloriesKcal, s.limits.high)
        assertTrue(s.limits.expectedWeeklyChangeKg!! < 0)
        assertEquals(LimitStatus.ON_TRACK, s.limits.status(s.limits.high))
        assertEquals(LimitStatus.OVER, s.limits.status(s.limits.high + 1))
    }

    @Test fun `bulking gives a floor equal to the target and a higher ceiling`() {
        val s = TargetCalculator.suggest(input(FitnessGoalType.MUSCLE_BUILDING))
        assertEquals(LimitKind.FLOOR, s.limits.kind)
        assertEquals(s.targets.caloriesKcal, s.limits.low)
        assertTrue(s.limits.high > s.limits.low)
        assertTrue(s.limits.expectedWeeklyChangeKg!! > 0)
        assertEquals(LimitStatus.UNDER, s.limits.status(s.limits.low - 1))
        assertEquals(LimitStatus.ON_TRACK, s.limits.status(s.limits.low))
        assertEquals(LimitStatus.OVER, s.limits.status(s.limits.high + 1))
    }

    @Test fun `maintenance is a five percent band`() {
        val s = TargetCalculator.suggest(input(FitnessGoalType.MAINTENANCE))
        assertEquals(LimitKind.RANGE, s.limits.kind)
        val t = s.targets.caloriesKcal
        assertEquals(t * 0.95, s.limits.low.toDouble(), 1.0)
        assertEquals(t * 1.05, s.limits.high.toDouble(), 1.0)
    }

    @Test fun `cut never goes below the sex based safety floor`() {
        val small = TargetCalculator.suggest(input(FitnessGoalType.FAT_LOSS, BiologicalSex.FEMALE, weight = 45.0))
        assertTrue(small.targets.caloriesKcal >= 1200)
        val man = TargetCalculator.suggest(TargetInput(60, BiologicalSex.MALE, 160.0, 50.0, ActivityLevel.SEDENTARY, FitnessGoalType.FAT_LOSS))
        assertTrue(man.targets.caloriesKcal >= 1500)
    }

    @Test fun `deficit is capped to about one percent of body weight per week`() {
        val s = TargetCalculator.suggest(TargetInput(25, BiologicalSex.MALE, 190.0, 120.0, ActivityLevel.EXTREME, FitnessGoalType.FAT_LOSS))
        val weeklyPct = CalorieLimits.weeklyPercent(s.limits.expectedWeeklyChangeKg, 120.0)!!
        assertTrue("loss rate $weeklyPct", weeklyPct <= 1.05)
    }

    @Test fun `headlines say what to do`() {
        val cut = CalorieLimits.forGoal(FitnessGoalType.FAT_LOSS, 2000, 2500, 1500)
        assertEquals("500 kcal left before your maximum", cut.headline(1500))
        assertEquals("100 kcal over your maximum", cut.headline(2100))
        val bulk = CalorieLimits.forGoal(FitnessGoalType.MUSCLE_BUILDING, 3000, 2700, 1500)
        assertEquals("800 kcal to reach your minimum", bulk.headline(2200))
        assertEquals("Minimum reached", bulk.headline(3200))
        assertEquals("Eat no more than 2000 kcal", cut.ruleText)
        assertEquals("Eat at least 3000 kcal", bulk.ruleText)
    }
}

class BodyMetricsTest {
    @Test fun `bmi and category`() {
        val bmi = BodyMetrics.bmi(70.0, 175.0)
        assertEquals(22.86, bmi, 0.01)
        assertEquals(BmiCategory.HEALTHY, BodyMetrics.bmiCategory(bmi))
        assertEquals(BmiCategory.UNDERWEIGHT, BodyMetrics.bmiCategory(18.4))
        assertEquals(BmiCategory.OVERWEIGHT, BodyMetrics.bmiCategory(25.0))
        assertEquals(BmiCategory.OBESE, BodyMetrics.bmiCategory(30.0))
    }

    @Test fun `healthy range spans the 18_5 to 24_9 band`() {
        val r = BodyMetrics.healthyWeightRangeKg(170.0)
        assertEquals(53.5, r.start, 0.1)
        assertEquals(72.0, r.endInclusive, 0.1)
    }

    @Test fun `navy body fat needs the right measurements`() {
        assertNull(BodyMetrics.navyBodyFatPercent(BiologicalSex.MALE, 178.0, null, 38.0, null))
        assertNull(BodyMetrics.navyBodyFatPercent(BiologicalSex.FEMALE, 165.0, 75.0, 32.0, null))
        assertNull(BodyMetrics.navyBodyFatPercent(null, 178.0, 85.0, 38.0, null))
        val male = BodyMetrics.navyBodyFatPercent(BiologicalSex.MALE, 178.0, 85.0, 38.0, null)
        assertNotNull(male)
        assertEquals(17.0, male!!, 3.0)
    }

    @Test fun `ratios and lean mass`() {
        assertEquals(0.5, BodyMetrics.waistToHeight(85.0, 170.0), 0.001)
        assertEquals(0.85, BodyMetrics.waistToHip(85.0, 100.0), 0.001)
        assertEquals(60.0, BodyMetrics.leanMassKg(75.0, 20.0), 0.001)
    }
}
