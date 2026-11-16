package com.fitos.app.domain.model

import java.time.Instant

enum class BiologicalSex(val label: String) { MALE("Male"), FEMALE("Female") }

enum class FitnessExperience(val label: String, val hint: String) {
    BEGINNER("Beginner", "Under 1 year of consistent training"),
    INTERMEDIATE("Intermediate", "1–3 years"),
    ADVANCED("Advanced", "3+ years")
}

enum class ActivityLevel(val label: String, val hint: String, val factor: Double) {
    SEDENTARY("Sedentary", "Desk job, little exercise", 1.2),
    LIGHT("Lightly active", "Light exercise 1–3 days/week", 1.375),
    MODERATE("Moderately active", "Exercise 3–5 days/week", 1.55),
    VERY_ACTIVE("Very active", "Hard exercise 6–7 days/week", 1.725),
    EXTREME("Extremely active", "Physical job plus training", 1.9)
}

enum class FitnessGoalType(val label: String, val hint: String) {
    FAT_LOSS("Fat loss", "Lose fat, keep muscle"),
    MUSCLE_BUILDING("Muscle building", "Gain muscle with a small surplus"),
    RECOMPOSITION("Body recomposition", "Lose fat and build muscle"),
    MAINTENANCE("Maintenance", "Hold current weight"),
    GENERAL_FITNESS("General fitness", "Move better, feel better")
}

enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }
enum class HeightUnit(val label: String) { CM("cm"), FT_IN("ft / in") }
enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

/** Where a stored number came from. Keeps user input distinguishable from estimates. */
enum class ValueSource { USER, FORMULA_ESTIMATE, COACHING_IMPORT, HEALTH_CONNECT }

/** All body values are stored in metric (kg / cm); units only affect display and entry. */
data class UserProfile(
    val name: String,
    val age: Int,
    val sex: BiologicalSex?,
    val heightCm: Double,
    val initialWeightKg: Double,
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val experience: FitnessExperience,
    val trainingDaysPerWeek: Int,
    val activityLevel: ActivityLevel,
    val goal: FitnessGoalType,
    val dietaryPreferences: List<String>,
    val foodRestrictions: List<String>,
    val allergies: List<String>,
    val equipment: List<String>,
    val weightUnit: WeightUnit,
    val heightUnit: HeightUnit,
    val updatedAt: Instant
)

data class DailyTargets(
    val caloriesKcal: Int,
    val proteinG: Int,
    val carbohydratesG: Int,
    val fatG: Int,
    val fibreG: Int,
    val waterMl: Int,
    val source: ValueSource
)

object Options {
    val dietaryPreferences = listOf("Vegetarian", "Eggetarian", "Non-vegetarian", "Vegan", "Pescatarian", "High protein", "Low carb")
    val restrictions = listOf("No beef", "No pork", "No dairy", "No gluten", "No red meat", "Low sodium")
    val allergies = listOf("Peanuts", "Tree nuts", "Shellfish", "Fish", "Eggs", "Milk", "Soy", "Wheat")
    val equipment = listOf(
        "Barbell", "Dumbbells", "Cables", "Machines", "Bench", "Squat rack",
        "Pull-up bar", "Kettlebells", "Resistance bands", "Bodyweight only"
    )
}
