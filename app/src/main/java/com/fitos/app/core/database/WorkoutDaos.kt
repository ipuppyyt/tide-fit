package com.fitos.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ExerciseDao {
    @Query(
        "SELECT * FROM exercise WHERE isArchived = 0 AND name LIKE '%' || :q || '%' " +
            "AND (:muscle IS NULL OR primaryMuscle = :muscle) ORDER BY name COLLATE NOCASE"
    )
    fun search(q: String, muscle: String?): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercise WHERE isSeed = 1")
    suspend fun countSeed(): Int

    @Insert suspend fun insert(e: ExerciseEntity): Long
    @Insert suspend fun insertAll(list: List<ExerciseEntity>)
    @Update suspend fun update(e: ExerciseEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_exercise ORDER BY routineId, position")
    fun observeAllExercises(): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun get(id: Long): RoutineEntity?

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY position")
    suspend fun getExercises(routineId: Long): List<RoutineExerciseEntity>

    @Insert suspend fun insert(r: RoutineEntity): Long
    @Update suspend fun update(r: RoutineEntity)
    @Insert suspend fun insertExercises(list: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercise WHERE routineId = :routineId")
    suspend fun deleteExercises(routineId: Long)

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WorkoutDao {
    @Insert suspend fun insertSession(s: WorkoutSessionEntity): Long
    @Update suspend fun updateSession(s: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeSession(id: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId ORDER BY exerciseOrder, setNumber")
    fun observeSets(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId ORDER BY exerciseOrder, setNumber")
    suspend fun getSets(sessionId: Long): List<WorkoutSetEntity>

    /** Completed sets of finished workouts, for history, records and analytics. */
    @Query(
        "SELECT s.* FROM workout_set s JOIN workout_session w ON w.id = s.sessionId " +
            "WHERE w.endedAt IS NOT NULL AND s.completed = 1 ORDER BY w.startedAt, s.exerciseOrder, s.setNumber"
    )
    fun observeAllFinishedSets(): Flow<List<WorkoutSetEntity>>

    @Query(
        "SELECT s.* FROM workout_set s JOIN workout_session w ON w.id = s.sessionId " +
            "WHERE w.endedAt IS NOT NULL AND s.completed = 1 AND s.exerciseId = :exerciseId AND w.id != :excludeSession " +
            "AND w.startedAt = (SELECT MAX(w2.startedAt) FROM workout_session w2 JOIN workout_set s2 ON s2.sessionId = w2.id " +
            "WHERE w2.endedAt IS NOT NULL AND s2.completed = 1 AND s2.exerciseId = :exerciseId AND w2.id != :excludeSession) " +
            "ORDER BY s.setNumber"
    )
    suspend fun lastPerformance(exerciseId: Long, excludeSession: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSetEntity?

    @Insert suspend fun insertSet(s: WorkoutSetEntity): Long
    @Insert suspend fun insertSets(list: List<WorkoutSetEntity>)
    @Update suspend fun updateSet(s: WorkoutSetEntity)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM workout_set WHERE sessionId = :sessionId AND completed = 0")
    suspend fun deleteIncompleteSets(sessionId: Long)

    @Query("DELETE FROM workout_set WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun deleteExerciseSets(sessionId: Long, exerciseId: Long)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: Long)
}
