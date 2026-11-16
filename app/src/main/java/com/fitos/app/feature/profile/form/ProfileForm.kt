package com.fitos.app.feature.profile.form

import com.fitos.app.domain.calc.Units
import com.fitos.app.domain.model.ActivityLevel
import com.fitos.app.domain.model.BiologicalSex
import com.fitos.app.domain.model.FitnessExperience
import com.fitos.app.domain.model.FitnessGoalType
import com.fitos.app.domain.model.HeightUnit
import com.fitos.app.domain.model.Options
import com.fitos.app.domain.model.UserProfile
import com.fitos.app.domain.model.WeightUnit
import java.time.Instant

enum class FormSection { BASICS, BODY, GOAL, TRAINING, DIET, EQUIPMENT }

/**
 * Text-field backed editing state shared by onboarding and the profile editor.
 * Numbers stay as strings while typing and are validated/converted only in [toProfile].
 */
data class ProfileForm(
    val name: String = "",
    val age: String = "",
    val sex: BiologicalSex? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val heightCm: String = "",
    val heightFeet: String = "",
    val heightInches: String = "",
    val initialWeight: String = "",
    val currentWeight: String = "",
    val targetWeight: String = "",
    val experience: FitnessExperience = FitnessExperience.BEGINNER,
    val trainingDays: Int = 3,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val goal: FitnessGoalType = FitnessGoalType.FAT_LOSS,
    val diet: Set<String> = emptySet(),
    val restrictions: Set<String> = emptySet(),
    val restrictionsOther: String = "",
    val allergies: Set<String> = emptySet(),
    val allergiesOther: String = "",
    val equipment: Set<String> = emptySet()
) {
    /** Parsed height in cm or null when the entry is missing/invalid. */
    fun heightCmOrNull(): Double? = when (heightUnit) {
        HeightUnit.CM -> heightCm.toNumberOrNull()
        HeightUnit.FT_IN -> {
            val feet = heightFeet.toNumberOrNull()
            val inches = if (heightInches.isBlank()) 0.0 else heightInches.toNumberOrNull()
            if (feet == null || inches == null || inches < 0 || inches >= 12) null else Units.feetInchesToCm(feet, inches)
        }
    }

    fun validate(section: FormSection): String? = when (section) {
        FormSection.BASICS -> {
            val a = age.toIntOrNull()
            when {
                name.isBlank() -> "Enter your name."
                a == null || a !in 13..100 -> "Enter an age between 13 and 100."
                else -> null
            }
        }
        FormSection.BODY -> {
            val h = heightCmOrNull()
            val cur = currentWeight.toNumberOrNull()?.let { Units.toKg(it, weightUnit) }
            val ini = initialWeight.toNumberOrNull()?.let { Units.toKg(it, weightUnit) }
            val tgt = targetWeight.toNumberOrNull()?.let { Units.toKg(it, weightUnit) }
            when {
                h == null || h !in 100.0..250.0 -> "Enter a valid height (100–250 cm)."
                ini == null || ini !in WEIGHT_RANGE_KG -> "Enter a valid initial weight."
                cur == null || cur !in WEIGHT_RANGE_KG -> "Enter a valid current weight."
                tgt == null || tgt !in WEIGHT_RANGE_KG -> "Enter a valid target weight."
                else -> null
            }
        }
        FormSection.GOAL, FormSection.TRAINING, FormSection.DIET, FormSection.EQUIPMENT -> null
    }

    fun validateAll(): Pair<FormSection, String>? =
        FormSection.entries.firstNotNullOfOrNull { s -> validate(s)?.let { s to it } }

    /** Returns null when any required field is invalid; call [validateAll] first to explain why. */
    fun toProfile(updatedAt: Instant): UserProfile? {
        if (validateAll() != null) return null
        return UserProfile(
            name = name.trim(),
            age = age.toInt(),
            sex = sex,
            heightCm = Units.round1(heightCmOrNull()!!),
            initialWeightKg = Units.toKg(initialWeight.toNumberOrNull()!!, weightUnit),
            currentWeightKg = Units.toKg(currentWeight.toNumberOrNull()!!, weightUnit),
            targetWeightKg = Units.toKg(targetWeight.toNumberOrNull()!!, weightUnit),
            experience = experience,
            trainingDaysPerWeek = trainingDays,
            activityLevel = activity,
            goal = goal,
            dietaryPreferences = diet.toList(),
            foodRestrictions = (restrictions + splitList(restrictionsOther)).toList(),
            allergies = (allergies + splitList(allergiesOther)).toList(),
            equipment = equipment.toList(),
            weightUnit = weightUnit,
            heightUnit = heightUnit,
            updatedAt = updatedAt
        )
    }

    companion object {
        val WEIGHT_RANGE_KG = 25.0..400.0

        fun fromProfile(p: UserProfile): ProfileForm {
            val (feet, inches) = Units.cmToFeetInches(p.heightCm)
            fun w(kg: Double) = Units.round1(Units.fromKg(kg, p.weightUnit)).toString()
            return ProfileForm(
                name = p.name,
                age = p.age.toString(),
                sex = p.sex,
                weightUnit = p.weightUnit,
                heightUnit = p.heightUnit,
                heightCm = Units.round1(p.heightCm).toString(),
                heightFeet = feet.toString(),
                heightInches = inches.toString(),
                initialWeight = w(p.initialWeightKg),
                currentWeight = w(p.currentWeightKg),
                targetWeight = w(p.targetWeightKg),
                experience = p.experience,
                trainingDays = p.trainingDaysPerWeek,
                activity = p.activityLevel,
                goal = p.goal,
                diet = p.dietaryPreferences.toSet(),
                restrictions = p.foodRestrictions.filter { it in Options.restrictions }.toSet(),
                restrictionsOther = p.foodRestrictions.filter { it !in Options.restrictions }.joinToString(", "),
                allergies = p.allergies.filter { it in Options.allergies }.toSet(),
                allergiesOther = p.allergies.filter { it !in Options.allergies }.joinToString(", "),
                equipment = p.equipment.toSet()
            )
        }

        private fun splitList(text: String): List<String> =
            text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Accepts both "72.5" and "72,5"; rejects NaN/infinity and negatives. */
internal fun String.toNumberOrNull(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
