package com.artishorizon.tidefit.data

import androidx.room.withTransaction
import com.artishorizon.tidefit.core.database.AppDatabase
import com.artishorizon.tidefit.core.database.ExerciseEntity
import com.artishorizon.tidefit.core.database.RoutineEntity
import com.artishorizon.tidefit.core.database.RoutineExerciseEntity
import com.artishorizon.tidefit.core.database.WorkoutSessionEntity
import com.artishorizon.tidefit.core.database.WorkoutSetEntity
import com.artishorizon.tidefit.domain.calc.TrainingCalc
import com.artishorizon.tidefit.domain.model.EquipmentType
import com.artishorizon.tidefit.domain.model.Exercise
import com.artishorizon.tidefit.domain.model.MuscleGroup
import com.artishorizon.tidefit.domain.model.Routine
import com.artishorizon.tidefit.domain.model.RoutineExercise
import com.artishorizon.tidefit.domain.model.Workout
import com.artishorizon.tidefit.domain.model.WorkoutSet
import com.artishorizon.tidefit.domain.model.WorkoutSummary
import com.artishorizon.tidefit.domain.repository.ExerciseRepository
import com.artishorizon.tidefit.domain.repository.FinishResult
import com.artishorizon.tidefit.domain.repository.RoutineRepository
import com.artishorizon.tidefit.domain.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

internal fun ExerciseEntity.toDomain() = Exercise(
    id = id, name = name, primary = enumOr(primaryMuscle, MuscleGroup.FULL_BODY),
    secondary = secondaryMuscles.mapNotNull { s -> MuscleGroup.entries.firstOrNull { it.name == s } },
    equipment = enumOr(equipment, EquipmentType.OTHER), notes = notes, isCustom = isCustom, isArchived = isArchived
)

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback

internal fun WorkoutSetEntity.toDomain() = WorkoutSet(
    id, exerciseId, exerciseName, exerciseOrder, setNumber, weightKg, reps, rpe, isWarmup, completed, restSeconds, sessionId
)

@Singleton
class ExerciseRepositoryImpl @Inject constructor(private val db: AppDatabase, private val clock: Clock) : ExerciseRepository {
    private val dao get() = db.exerciseDao()

    override fun search(query: String, muscle: MuscleGroup?): Flow<List<Exercise>> =
        dao.search(query.trim(), muscle?.name).map { l -> l.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Exercise>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override suspend fun get(id: Long): Exercise? = dao.get(id)?.toDomain()

    override suspend fun save(exercise: Exercise): Long {
        require(exercise.name.isNotBlank()) { "Enter a name." }
        val name = exercise.name.trim()
        val secondary = exercise.secondary.filter { it != exercise.primary }.map { it.name }.distinct()
        return if (exercise.id == 0L) {
            dao.insert(
                ExerciseEntity(
                    name = name, primaryMuscle = exercise.primary.name, secondaryMuscles = secondary, equipment = exercise.equipment.name,
                    notes = exercise.notes?.trim()?.ifEmpty { null }, isCustom = true, isSeed = false, isArchived = false, createdAt = Instant.now(clock)
                )
            )
        } else {
            val old = dao.get(exercise.id) ?: throw IllegalArgumentException("Exercise no longer exists.")
            dao.update(
                old.copy(
                    name = name, primaryMuscle = exercise.primary.name, secondaryMuscles = secondary,
                    equipment = exercise.equipment.name, notes = exercise.notes?.trim()?.ifEmpty { null }
                )
            )
            old.id
        }
    }

    override suspend fun archive(id: Long) {
        val old = dao.get(id) ?: return
        dao.update(old.copy(isArchived = true))
    }
}

@Singleton
class RoutineRepositoryImpl @Inject constructor(private val db: AppDatabase, private val clock: Clock) : RoutineRepository {
    private val dao get() = db.routineDao()

    private fun RoutineExerciseEntity.toDomain() = RoutineExercise(exerciseId, exerciseName, targetSets, repsMin, repsMax, restSeconds)

    override fun observeAll(): Flow<List<Routine>> = combine(dao.observeAll(), dao.observeAllExercises()) { routines, exercises ->
        val byRoutine = exercises.groupBy { it.routineId }
        routines.map { r -> Routine(r.id, r.name, r.notes, byRoutine[r.id].orEmpty().map { it.toDomain() }) }
    }

    override suspend fun get(id: Long): Routine? {
        val r = dao.get(id) ?: return null
        return Routine(r.id, r.name, r.notes, dao.getExercises(id).map { it.toDomain() })
    }

    override suspend fun save(routine: Routine): Long {
        require(routine.name.isNotBlank()) { "Enter a routine name." }
        require(routine.exercises.isNotEmpty()) { "Add at least one exercise." }
        routine.exercises.forEach {
            require(it.targetSets in 1..20) { "Sets must be between 1 and 20." }
            require(it.repsMin in 1..100 && it.repsMax >= it.repsMin && it.repsMax <= 100) { "Check the rep range." }
            require(it.restSeconds in 0..900) { "Rest must be between 0 and 900 seconds." }
        }
        val now = Instant.now(clock)
        return db.withTransaction {
            val id = if (routine.id == 0L) {
                dao.insert(RoutineEntity(name = routine.name.trim(), notes = routine.notes?.trim()?.ifEmpty { null }, createdAt = now, updatedAt = now))
            } else {
                val old = dao.get(routine.id) ?: throw IllegalArgumentException("Routine no longer exists.")
                dao.update(old.copy(name = routine.name.trim(), notes = routine.notes?.trim()?.ifEmpty { null }, updatedAt = now))
                dao.deleteExercises(old.id)
                old.id
            }
            dao.insertExercises(
                routine.exercises.mapIndexed { i, e ->
                    RoutineExerciseEntity(
                        routineId = id, exerciseId = e.exerciseId, exerciseName = e.exerciseName, position = i,
                        targetSets = e.targetSets, repsMin = e.repsMin, repsMax = e.repsMax, restSeconds = e.restSeconds
                    )
                }
            )
            id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class WorkoutRepositoryImpl @Inject constructor(private val db: AppDatabase, private val clock: Clock) : WorkoutRepository {
    private val dao get() = db.workoutDao()

    private fun WorkoutSessionEntity.toDomain(sets: List<WorkoutSetEntity>) =
        Workout(id, routineId, name, startedAt, endedAt, notes, sets.map { it.toDomain() })

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActive(): Flow<Workout?> = dao.observeActive().flatMapLatest { s ->
        if (s == null) flowOf(null) else dao.observeSets(s.id).map { s.toDomain(it) }
    }

    override fun observe(id: Long): Flow<Workout?> = combine(dao.observeSession(id), dao.observeSets(id)) { s, sets -> s?.toDomain(sets) }

    override fun observeHistory(): Flow<List<WorkoutSummary>> = combine(dao.observeFinished(), dao.observeAllFinishedSets()) { sessions, sets ->
        val bySession = sets.map { it.toDomain() to it.sessionId }.groupBy({ it.second }, { it.first })
        sessions.map { s ->
            val ss = bySession[s.id].orEmpty()
            WorkoutSummary(s.id, s.name, s.startedAt, s.endedAt, ss.map { it.exerciseId }.distinct().size, TrainingCalc.workingSetCount(ss) + ss.count { it.isWarmup }, TrainingCalc.volumeKg(ss))
        }
    }

    override fun observeFinishedSets(): Flow<List<WorkoutSet>> = dao.observeAllFinishedSets().map { l -> l.map { it.toDomain() } }

    override suspend fun start(routineId: Long?): Long = db.withTransaction {
        val existing = dao.getActive()
        if (existing != null) return@withTransaction existing.id
        val now = Instant.now(clock)
        val routine = routineId?.let { db.routineDao().get(it) }
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(routineId = routine?.id, name = routine?.name ?: "Workout", startedAt = now, endedAt = null, notes = null)
        )
        if (routine != null) {
            val sets = db.routineDao().getExercises(routine.id).flatMapIndexed { order, re ->
                planSets(sessionId, re.exerciseId, re.exerciseName, order, re.targetSets, re.repsMin, re.restSeconds)
            }
            dao.insertSets(sets)
        }
        sessionId
    }

    /** Planned sets prefilled from the last time this exercise was done, so progressing is a one-tap change. */
    private suspend fun planSets(sessionId: Long, exerciseId: Long, name: String, order: Int, count: Int, defaultReps: Int, rest: Int): List<WorkoutSetEntity> {
        val last = dao.lastPerformance(exerciseId, sessionId)
        return (1..count).map { n ->
            val ref = last.getOrNull(n - 1) ?: last.lastOrNull()
            WorkoutSetEntity(
                sessionId = sessionId, exerciseId = exerciseId, exerciseName = name, exerciseOrder = order, setNumber = n,
                weightKg = ref?.weightKg, reps = ref?.reps ?: defaultReps, rpe = null, isWarmup = false, completed = false,
                restSeconds = rest, completedAt = null
            )
        }
    }

    override suspend fun addExercise(sessionId: Long, exercise: Exercise) = db.withTransaction {
        val order = (dao.getSets(sessionId).maxOfOrNull { it.exerciseOrder } ?: -1) + 1
        dao.insertSets(planSets(sessionId, exercise.id, exercise.name, order, 3, 8, 90))
    }

    override suspend fun addSet(sessionId: Long, exerciseId: Long) = db.withTransaction {
        val sets = dao.getSets(sessionId).filter { it.exerciseId == exerciseId }
        val last = sets.lastOrNull() ?: return@withTransaction
        dao.insertSet(last.copy(id = 0, setNumber = last.setNumber + 1, completed = false, completedAt = null, isWarmup = false))
    }

    override suspend fun updateSet(set: WorkoutSet) {
        require(set.weightKg == null || (set.weightKg >= 0 && set.weightKg <= 1000)) { "Weight out of range." }
        require(set.reps == null || (set.reps in 0..1000)) { "Reps out of range." }
        require(set.rpe == null || set.rpe in 1.0..10.0) { "RPE must be between 1 and 10." }
        val old = dao.getSet(set.id) ?: return
        val completedAt = if (set.completed) (old.completedAt ?: Instant.now(clock)) else null
        dao.updateSet(old.copy(weightKg = set.weightKg, reps = set.reps, rpe = set.rpe, isWarmup = set.isWarmup, completed = set.completed, completedAt = completedAt))
    }

    override suspend fun deleteSet(setId: Long) = dao.deleteSet(setId)
    override suspend fun removeExercise(sessionId: Long, exerciseId: Long) = dao.deleteExerciseSets(sessionId, exerciseId)

    override suspend fun updateNotes(sessionId: Long, notes: String?) {
        val s = dao.getSession(sessionId) ?: return
        dao.updateSession(s.copy(notes = notes?.trim()?.ifEmpty { null }))
    }

    override suspend fun finish(sessionId: Long): FinishResult = db.withTransaction {
        val s = dao.getSession(sessionId) ?: return@withTransaction FinishResult.DISCARDED_EMPTY
        dao.deleteIncompleteSets(sessionId)
        if (dao.getSets(sessionId).isEmpty()) {
            dao.deleteSession(sessionId)
            FinishResult.DISCARDED_EMPTY
        } else {
            dao.updateSession(s.copy(endedAt = Instant.now(clock)))
            FinishResult.SAVED
        }
    }

    override suspend fun discard(sessionId: Long) = dao.deleteSession(sessionId)
    override suspend fun delete(sessionId: Long) = dao.deleteSession(sessionId)
}
