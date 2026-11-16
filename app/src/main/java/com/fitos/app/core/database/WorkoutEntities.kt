package com.fitos.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "exercise", indices = [Index("name"), Index("primaryMuscle")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val notes: String?,
    val isCustom: Boolean,
    val isSeed: Boolean,
    val isArchived: Boolean,
    val createdAt: Instant
)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** Exercise name is snapshotted so a routine still reads correctly if the exercise is renamed or deleted. */
@Entity(
    tableName = "routine_exercise",
    foreignKeys = [ForeignKey(RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")]
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val targetSets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int
)

/** A workout. `endedAt == null` means it is in progress; it survives app restarts. */
@Entity(
    tableName = "workout_session",
    foreignKeys = [ForeignKey(RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("startedAt"), Index("routineId")]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val notes: String?
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [ForeignKey(WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val exerciseOrder: Int,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double?,
    val isWarmup: Boolean,
    val completed: Boolean,
    val restSeconds: Int,
    val completedAt: Instant?
)
