package com.artishorizon.tidefit.domain.calc

import com.artishorizon.tidefit.domain.model.WeightUnit
import kotlin.math.roundToInt

object Units {
    private const val KG_PER_LB = 0.45359237
    private const val CM_PER_INCH = 2.54

    fun lbToKg(lb: Double): Double = lb * KG_PER_LB
    fun kgToLb(kg: Double): Double = kg / KG_PER_LB

    fun toKg(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) value else lbToKg(value)
    fun fromKg(kg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) kg else kgToLb(kg)

    fun feetInchesToCm(feet: Double, inches: Double): Double = (feet * 12.0 + inches) * CM_PER_INCH

    /** Whole feet and remaining inches (rounded to nearest inch, carrying 12" into feet). */
    fun cmToFeetInches(cm: Double): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    fun cmToInches(cm: Double): Double = cm / CM_PER_INCH
    fun inchesToCm(inches: Double): Double = inches * CM_PER_INCH

    fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0
}
