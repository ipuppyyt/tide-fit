package com.artishorizon.tidefit.domain.repository

import com.artishorizon.tidefit.domain.model.Exercise
import com.artishorizon.tidefit.domain.model.MuscleGroup
import com.artishorizon.tidefit.domain.model.Routine
import com.artishorizon.tidefit.domain.model.Workout
import com.artishorizon.tidefit.domain.model.WorkoutSet
import com.artishorizon.tidefit.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun search(query: String, muscle: MuscleGroup?): Flow<List<Exercise>>

    /** Includes archived exercises so history can still resolve them. */
    fun observeAll(): Flow<List<Exercise>>
    suspend fun get(id: Long): Exercise?

    /** Inserts when [Exercise.id] is 0, otherwise updates. Throws IllegalArgumentException when invalid. */
    suspend fun save(exercise: Exercise): Long

    /** Hides an exercise from the library; past workouts keep it. */
    suspend fun archive(id: Long)
}

interface RoutineRepository {
    fun observeAll(): Flow<List<Routine>>
    suspend fun get(id: Long): Routine?
    suspend fun save(routine: Routine): Long
    suspend fun delete(id: Long)
}

enum class FinishResult { SAVED, DISCARDED_EMPTY }

interface WorkoutRepository {
    fun observeActive(): Flow<Workout?>
    fun observe(id: Long): Flow<Workout?>
    fun observeHistory(): Flow<List<WorkoutSummary>>

    /** Completed sets of finished workouts, oldest first. */
    fun observeFinishedSets(): Flow<List<WorkoutSet>>

    /** Starts a workout (from a routine, or empty when null). If one is already in progress its id is returned instead. */
    suspend fun start(routineId: Long?): Long
    suspend fun addExercise(sessionId: Long, exercise: Exercise)
    suspend fun addSet(sessionId: Long, exerciseId: Long)
    suspend fun updateSet(set: WorkoutSet)
    suspend fun deleteSet(setId: Long)
    suspend fun removeExercise(sessionId: Long, exerciseId: Long)
    suspend fun updateNotes(sessionId: Long, notes: String?)

    /** Ends the workout, dropping sets that were never completed. A workout with no completed sets is discarded. */
    suspend fun finish(sessionId: Long): FinishResult
    suspend fun discard(sessionId: Long)
    suspend fun delete(sessionId: Long)
}
