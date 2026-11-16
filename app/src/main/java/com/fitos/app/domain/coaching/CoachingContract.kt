package com.fitos.app.domain.coaching

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The JSON contract between FITOS and ChatGPT (schema 1.0).
 *
 * Export: FITOS -> user -> ChatGPT. Import: ChatGPT -> user -> FITOS. Nothing here talks to a network; the user
 * copies text between the two apps. Imports are parsed as data only: no text field is ever interpreted as an instruction.
 */
object Contract {
    const val SCHEMA_VERSION = "1.0"
    const val EXPORT_TYPE = "fitness_data_export"
    const val IMPORT_TYPE = "fitness_coaching_update"
}

// ------------------------------------------------------------------ export

@Serializable
data class ExportDocument(
    @SerialName("schema_version") val schemaVersion: String = Contract.SCHEMA_VERSION,
    val type: String = Contract.EXPORT_TYPE,
    @SerialName("generated_on") val generatedOn: String,
    val period: ExportPeriod,
    val profile: ExportProfile?,
    @SerialName("current_targets") val currentTargets: TargetsDto?,
    val weight: ExportWeight,
    val nutrition: List<ExportNutritionDay>,
    val training: List<ExportWorkout>,
    val sleep: List<ExportSleep>,
    val recovery: List<ExportRecovery>,
    @SerialName("supplement_adherence_percent") val supplementAdherencePercent: Int?,
    @SerialName("data_quality") val dataQuality: ExportDataQuality
)

@Serializable data class ExportPeriod(val from: String, val to: String, val days: Int)

@Serializable
data class ExportProfile(
    val age: Int,
    val sex: String?,
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("current_weight_kg") val currentWeightKg: Double,
    @SerialName("target_weight_kg") val targetWeightKg: Double,
    val goal: String,
    val experience: String,
    @SerialName("training_days_per_week") val trainingDaysPerWeek: Int,
    @SerialName("activity_level") val activityLevel: String,
    @SerialName("dietary_preferences") val dietaryPreferences: List<String>,
    @SerialName("food_restrictions") val foodRestrictions: List<String>,
    val allergies: List<String>,
    val equipment: List<String>
)

@Serializable
data class TargetsDto(
    @SerialName("calories_kcal") val caloriesKcal: Int,
    @SerialName("protein_g") val proteinG: Int,
    @SerialName("carbs_g") val carbsG: Int,
    @SerialName("fat_g") val fatG: Int,
    @SerialName("fibre_g") val fibreG: Int,
    @SerialName("water_ml") val waterMl: Int
)

@Serializable
data class ExportWeight(
    val entries: List<ExportWeightEntry>,
    @SerialName("seven_day_average_kg") val sevenDayAverageKg: Double?,
    @SerialName("weekly_rate_kg") val weeklyRateKg: Double?
)

@Serializable data class ExportWeightEntry(val date: String, val kg: Double)

/** Only days with at least one logged food appear; days without food are *not logged*, not zero intake. */
@Serializable
data class ExportNutritionDay(
    val date: String,
    @SerialName("calories_kcal") val caloriesKcal: Int,
    @SerialName("protein_g") val proteinG: Int,
    @SerialName("carbs_g") val carbsG: Int,
    @SerialName("fat_g") val fatG: Int,
    @SerialName("water_ml") val waterMl: Int?
)

@Serializable
data class ExportWorkout(val date: String, val name: String, val exercises: Int, val sets: Int, @SerialName("volume_kg") val volumeKg: Int)

@Serializable
data class ExportSleep(val date: String, val minutes: Int, val quality: Int?)

@Serializable
data class ExportRecovery(val date: String, val soreness: Int, val energy: Int, val stress: Int)

@Serializable
data class ExportDataQuality(
    @SerialName("days_in_period") val daysInPeriod: Int,
    @SerialName("days_with_food_logged") val daysWithFoodLogged: Int,
    @SerialName("days_with_weight") val daysWithWeight: Int,
    @SerialName("workouts_logged") val workoutsLogged: Int,
    @SerialName("nights_with_sleep") val nightsWithSleep: Int,
    val notes: String = "Days that are missing were not logged. They are not zero intake, zero sleep or rest days."
)

// ------------------------------------------------------------------ import

@Serializable
data class CoachingUpdate(
    @SerialName("schema_version") val schemaVersion: String? = null,
    val type: String? = null,
    val analysis: Analysis? = null,
    @SerialName("updated_targets") val updatedTargets: ProposedTargets? = null,
    val recommendations: List<Suggestion> = emptyList(),
    @SerialName("workout_suggestions") val workoutSuggestions: List<Suggestion> = emptyList(),
    @SerialName("habit_suggestions") val habitSuggestions: List<Suggestion> = emptyList(),
    @SerialName("coach_notes") val coachNotes: String? = null,
    @SerialName("requires_user_confirmation") val requiresUserConfirmation: Boolean? = null
)

@Serializable
data class Analysis(
    val summary: String? = null,
    val observations: List<String> = emptyList()
)

/** Fibre and water are optional: a missing value keeps the current target. */
@Serializable
data class ProposedTargets(
    @SerialName("calories_kcal") val caloriesKcal: Int? = null,
    @SerialName("protein_g") val proteinG: Int? = null,
    @SerialName("carbs_g") val carbsG: Int? = null,
    @SerialName("fat_g") val fatG: Int? = null,
    @SerialName("fibre_g") val fibreG: Int? = null,
    @SerialName("water_ml") val waterMl: Int? = null,
    val reason: String? = null
)

@Serializable
data class Suggestion(val title: String? = null, val detail: String? = null)
