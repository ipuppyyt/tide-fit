package com.fitos.app.domain.model

import java.time.Instant

enum class MuscleGroup(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), BICEPS("Biceps"), TRICEPS("Triceps"), FOREARMS("Forearms"),
    QUADS("Quads"), HAMSTRINGS("Hamstrings"), GLUTES("Glutes"), CALVES("Calves"), CORE("Core"), FULL_BODY("Full body"), CARDIO("Cardio")
}

enum class EquipmentType(val label: String) {
    BARBELL("Barbell"), DUMBBELL("Dumbbell"), MACHINE("Machine"), CABLE("Cable"), BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell"), BAND("Band"), OTHER("Other")
}

data class Exercise(
    val id: Long = 0,
    val name: String,
    val primary: MuscleGroup,
    val secondary: List<MuscleGroup> = emptyList(),
    val equipment: EquipmentType,
    val notes: String? = null,
    val isCustom: Boolean = true,
    val isArchived: Boolean = false
) {
    /** Bodyweight exercises can be logged with reps only. */
    val allowsNoWeight: Boolean get() = equipment == EquipmentType.BODYWEIGHT
}

data class RoutineExercise(
    val exerciseId: Long,
    val exerciseName: String,
    val targetSets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int
)

data class Routine(
    val id: Long = 0,
    val name: String,
    val notes: String? = null,
    val exercises: List<RoutineExercise> = emptyList()
)

/** One set. Weight is always stored in kilograms; the UI converts to the user's unit. */
data class WorkoutSet(
    val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val exerciseOrder: Int,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val completed: Boolean = false,
    val restSeconds: Int = 90,
    val sessionId: Long = 0
)

data class Workout(
    val id: Long = 0,
    val routineId: Long? = null,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val notes: String? = null,
    val sets: List<WorkoutSet> = emptyList()
) {
    val isActive: Boolean get() = endedAt == null
}

/** A summary row for history lists. */
data class WorkoutSummary(
    val id: Long,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double
)
