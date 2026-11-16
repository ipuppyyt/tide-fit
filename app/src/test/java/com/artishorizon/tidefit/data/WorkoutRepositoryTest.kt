package com.artishorizon.tidefit.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.artishorizon.tidefit.core.database.AppDatabase
import com.artishorizon.tidefit.domain.model.EquipmentType
import com.artishorizon.tidefit.domain.model.Exercise
import com.artishorizon.tidefit.domain.model.MuscleGroup
import com.artishorizon.tidefit.domain.model.Routine
import com.artishorizon.tidefit.domain.model.RoutineExercise
import com.artishorizon.tidefit.domain.repository.FinishResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var exercises: ExerciseRepositoryImpl
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var workouts: WorkoutRepositoryImpl
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        exercises = ExerciseRepositoryImpl(db, clock); routines = RoutineRepositoryImpl(db, clock); workouts = WorkoutRepositoryImpl(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun bench(): Exercise {
        val id = exercises.save(Exercise(name = "Bench", primary = MuscleGroup.CHEST, equipment = EquipmentType.BARBELL))
        return exercises.get(id)!!
    }

    private suspend fun doneSet(sessionId: Long, weight: Double, reps: Int) {
        val set = workouts.observe(sessionId).first()!!.sets.first { !it.completed }
        workouts.updateSet(set.copy(weightKg = weight, reps = reps, completed = true))
    }

    @Test fun `seeding inserts the library once`() = runBlocking {
        val seeder = ExerciseSeeder(db.exerciseDao(), clock)
        seeder.seedIfNeeded(); seeder.seedIfNeeded()
        val all = exercises.search("", null).first()
        assertTrue(all.size >= 50)
        assertTrue(exercises.search("bench", null).first().isNotEmpty())
        assertTrue(exercises.search("", MuscleGroup.CHEST).first().all { it.primary == MuscleGroup.CHEST })
    }

    @Test fun `exercise needs a name`() = runBlocking {
        try {
            exercises.save(Exercise(name = " ", primary = MuscleGroup.CHEST, equipment = EquipmentType.BARBELL))
            fail("expected failure")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun `archived exercises leave the library but still resolve`() = runBlocking {
        val e = bench()
        exercises.archive(e.id)
        assertTrue(exercises.search("", null).first().isEmpty())
        assertNotNull(exercises.get(e.id))
    }

    @Test fun `routine round trip keeps order and validates`() = runBlocking {
        val e = bench()
        val id = routines.save(Routine(name = "Push", exercises = listOf(RoutineExercise(e.id, e.name, 3, 5, 8, 120))))
        val r = routines.get(id)!!
        assertEquals("Push", r.name)
        assertEquals(1, r.exercises.size)
        assertEquals(120, r.exercises[0].restSeconds)
        try {
            routines.save(Routine(name = "Bad", exercises = listOf(RoutineExercise(e.id, e.name, 3, 10, 5, 60))))
            fail("expected failure")
        } catch (_: IllegalArgumentException) {}
        try { routines.save(Routine(name = "Empty")); fail("expected failure") } catch (_: IllegalArgumentException) {}
    }

    @Test fun `starting from a routine plans the sets`() = runBlocking {
        val e = bench()
        val rid = routines.save(Routine(name = "Push", exercises = listOf(RoutineExercise(e.id, e.name, 3, 5, 8, 90))))
        val sid = workouts.start(rid)
        val w = workouts.observe(sid).first()!!
        assertEquals(3, w.sets.size)
        assertTrue(w.sets.none { it.completed })
        assertEquals(5, w.sets[0].reps)
        assertNull(w.sets[0].weightKg)
    }

    @Test fun `only one workout can be active`() = runBlocking {
        val a = workouts.start(null)
        val b = workouts.start(null)
        assertEquals(a, b)
        assertEquals(a, workouts.observeActive().first()!!.id)
    }

    @Test fun `finishing drops unfinished sets and keeps history`() = runBlocking {
        val e = bench()
        val sid = workouts.start(null)
        workouts.addExercise(sid, e)
        doneSet(sid, 100.0, 5)
        assertEquals(FinishResult.SAVED, workouts.finish(sid))
        val w = workouts.observe(sid).first()!!
        assertEquals(1, w.sets.size)
        assertNotNull(w.endedAt)
        assertNull(workouts.observeActive().first())
        val history = workouts.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals(500.0, history[0].volumeKg, 0.001)
    }

    @Test fun `empty workout is discarded on finish`() = runBlocking {
        val e = bench()
        val sid = workouts.start(null)
        workouts.addExercise(sid, e)
        assertEquals(FinishResult.DISCARDED_EMPTY, workouts.finish(sid))
        assertNull(workouts.observe(sid).first())
        assertTrue(workouts.observeHistory().first().isEmpty())
    }

    @Test fun `next workout is prefilled from the last performance`() = runBlocking {
        val e = bench()
        val first = workouts.start(null)
        workouts.addExercise(first, e)
        doneSet(first, 100.0, 5)
        workouts.finish(first)
        val second = workouts.start(null)
        workouts.addExercise(second, e)
        val sets = workouts.observe(second).first()!!.sets
        assertEquals(100.0, sets[0].weightKg!!, 0.001)
        assertEquals(5, sets[0].reps)
    }

    @Test fun `add and remove sets and exercises`() = runBlocking {
        val e = bench()
        val sid = workouts.start(null)
        workouts.addExercise(sid, e)
        workouts.addSet(sid, e.id)
        assertEquals(4, workouts.observe(sid).first()!!.sets.size)
        workouts.deleteSet(workouts.observe(sid).first()!!.sets.last().id)
        assertEquals(3, workouts.observe(sid).first()!!.sets.size)
        workouts.removeExercise(sid, e.id)
        assertTrue(workouts.observe(sid).first()!!.sets.isEmpty())
    }

    @Test fun `invalid set values are rejected`() = runBlocking {
        val e = bench()
        val sid = workouts.start(null)
        workouts.addExercise(sid, e)
        val set = workouts.observe(sid).first()!!.sets[0]
        try { workouts.updateSet(set.copy(weightKg = -5.0)); fail("expected failure") } catch (_: IllegalArgumentException) {}
        try { workouts.updateSet(set.copy(rpe = 11.0)); fail("expected failure") } catch (_: IllegalArgumentException) {}
    }

    @Test fun `deleting a routine keeps past workouts`() = runBlocking {
        val e = bench()
        val rid = routines.save(Routine(name = "Push", exercises = listOf(RoutineExercise(e.id, e.name, 1, 5, 8, 90))))
        val sid = workouts.start(rid)
        doneSet(sid, 100.0, 5)
        workouts.finish(sid)
        routines.delete(rid)
        val w = workouts.observe(sid).first()!!
        assertNull(w.routineId)
        assertEquals("Push", w.name)
        assertEquals(1, w.sets.size)
    }
}
