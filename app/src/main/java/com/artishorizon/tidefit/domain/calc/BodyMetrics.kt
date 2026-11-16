package com.artishorizon.tidefit.domain.calc

import com.artishorizon.tidefit.domain.model.BiologicalSex
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class BmiCategory(val label: String) {
    UNDERWEIGHT("Underweight"), HEALTHY("Healthy range"), OVERWEIGHT("Overweight"), OBESE("Obese")
}

/** Everything is derived on the fly from stored values; nothing here is persisted. All inputs metric. */
object BodyMetrics {
    /** Standard WHO adult cut-offs. BMI ignores muscle, so it is a screen, not a verdict. */
    fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / (heightCm / 100.0).pow(2)

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.UNDERWEIGHT
        bmi < 25.0 -> BmiCategory.HEALTHY
        bmi < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }

    /** Weight range (kg) that falls in the healthy BMI band for this height. */
    fun healthyWeightRangeKg(heightCm: Double): ClosedFloatingPointRange<Double> {
        val m2 = (heightCm / 100.0).pow(2)
        return 18.5 * m2..24.9 * m2
    }

    /** Waist-to-height ratio; keeping waist under half of height is a widely used risk marker. */
    fun waistToHeight(waistCm: Double, heightCm: Double): Double = waistCm / heightCm

    fun waistToHip(waistCm: Double, hipCm: Double): Double = waistCm / hipCm

    /**
     * US Navy body-fat estimate (cm inputs). Accuracy is a few percentage points at best, so the UI must label it an estimate.
     * Women need hips; returns null if a required measurement is missing or the numbers make no geometric sense.
     */
    fun navyBodyFatPercent(sex: BiologicalSex?, heightCm: Double, waistCm: Double?, neckCm: Double?, hipCm: Double?): Double? {
        if (waistCm == null || neckCm == null || sex == null) return null
        val result = when (sex) {
            BiologicalSex.MALE -> {
                if (waistCm <= neckCm) return null
                495.0 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450.0
            }
            BiologicalSex.FEMALE -> {
                if (hipCm == null || waistCm + hipCm <= neckCm) return null
                495.0 / (1.29579 - 0.35004 * log10(waistCm + hipCm - neckCm) + 0.22100 * log10(heightCm)) - 450.0
            }
        }
        return result.takeIf { it in 2.0..60.0 }
    }

    fun leanMassKg(weightKg: Double, bodyFatPercent: Double): Double = weightKg * (1 - bodyFatPercent / 100.0)

    fun fatMassKg(weightKg: Double, bodyFatPercent: Double): Double = weightKg * bodyFatPercent / 100.0

    /** Rounded to one decimal for display. */
    fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0
}
