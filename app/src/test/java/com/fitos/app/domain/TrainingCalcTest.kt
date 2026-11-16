package com.fitos.app.domain

import com.fitos.app.domain.calc.TrainingCalc
import com.fitos.app.domain.calc.TrainingCalc.PrKind
import com.fitos.app.domain.model.MuscleGroup
import com.fitos.app.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingCalcTest {
    private fun set(
        weight: Double?, reps: Int?, ex: Long = 1, name: String = "Bench", warmup: Boolean = false, done: Boolean = true, n: Int = 1
    ) = WorkoutSet(exerciseId = ex, exerciseName = name, exerciseOrder = 0, setNumber = n, weightKg = weight, reps = reps, isWarmup = warmup, completed = done)

    @Test fun `volume ignores warmups and incomplete sets`() {
        val sets = listOf(set(100.0, 5), set(60.0, 10, warmup = true), set(100.0, 5, done = false), set(80.0, 8))
        assertEquals(100.0 * 5 + 80.0 * 8, TrainingCalc.volumeKg(sets), 0.001)
        assertEquals(2, TrainingCalc.workingSetCount(sets))
    }

    @Test fun `bodyweight sets add no volume but count as sets`() {
        val sets = listOf(set(null, 12), set(null, 10))
        assertEquals(0.0, TrainingCalc.volumeKg(sets), 0.001)
        assertEquals(2, TrainingCalc.workingSetCount(sets))
    }

    @Test fun `epley estimate`() {
        assertEquals(100.0, TrainingCalc.estimatedOneRepMaxKg(100.0, 1)!!, 0.001)
        assertEquals(100.0 * (1 + 5 / 30.0), TrainingCalc.estimatedOneRepMaxKg(100.0, 5)!!, 0.001)
    }

    @Test fun `estimate is withheld when unreliable`() {
        assertNull(TrainingCalc.estimatedOneRepMaxKg(null, 5))
        assertNull(TrainingCalc.estimatedOneRepMaxKg(0.0, 5))
        assertNull(TrainingCalc.estimatedOneRepMaxKg(50.0, 0))
        assertNull(TrainingCalc.estimatedOneRepMaxKg(50.0, 13))
    }

    @Test fun `first time doing an exercise is not a record`() {
        assertTrue(TrainingCalc.newRecords(emptyList(), listOf(set(100.0, 5))).isEmpty())
    }

    @Test fun `heavier weight is a record`() {
        val history = listOf(set(100.0, 5))
        val prs = TrainingCalc.newRecords(history, listOf(set(105.0, 5)))
        assertTrue(prs.any { it.kind == PrKind.WEIGHT && it.value == 105.0 })
        assertTrue(prs.any { it.kind == PrKind.ONE_REP_MAX })
    }

    @Test fun `equal performance is not a record`() {
        assertTrue(TrainingCalc.newRecords(listOf(set(100.0, 5)), listOf(set(100.0, 5))).isEmpty())
    }

    @Test fun `warmups never set records`() {
        assertTrue(TrainingCalc.newRecords(listOf(set(100.0, 5)), listOf(set(200.0, 5, warmup = true))).isEmpty())
    }

    @Test fun `records are per exercise`() {
        val history = listOf(set(100.0, 5, ex = 1), set(50.0, 5, ex = 2, name = "Row"))
        val prs = TrainingCalc.newRecords(history, listOf(set(60.0, 5, ex = 2, name = "Row"), set(90.0, 5, ex = 1)))
        assertTrue(prs.all { it.exerciseId == 2L })
    }

    @Test fun `rep record only for unweighted exercises`() {
        val prs = TrainingCalc.newRecords(listOf(set(null, 10, ex = 3, name = "Push-up")), listOf(set(null, 12, ex = 3, name = "Push-up")))
        assertTrue(prs.any { it.kind == PrKind.REPS && it.value == 12.0 })
    }

    @Test fun `week starts on monday`() {
        assertEquals(LocalDate.of(2026, 11, 16), TrainingCalc.weekStart(LocalDate.of(2026, 11, 22)))
        assertEquals(LocalDate.of(2026, 11, 16), TrainingCalc.weekStart(LocalDate.of(2026, 11, 16)))
    }

    @Test fun `weekly stats include empty weeks`() {
        val today = LocalDate.of(2026, 11, 18)
        val sessions = listOf(LocalDate.of(2026, 11, 17) to listOf(set(100.0, 5)), LocalDate.of(2026, 11, 3) to listOf(set(50.0, 10)))
        val stats = TrainingCalc.weeklyStats(sessions, today, 3)
        assertEquals(3, stats.size)
        assertEquals(1, stats[0].sessions) // week of Nov 2
        assertEquals(0, stats[1].sessions) // week of Nov 9: a real gap
        assertEquals(500.0, stats[2].volumeKg, 0.001)
    }

    @Test fun `sets per muscle skips unknown exercises`() {
        val sets = listOf(set(100.0, 5, ex = 1), set(100.0, 5, ex = 1), set(20.0, 10, ex = 2), set(10.0, 10, ex = 99))
        val m = TrainingCalc.setsPerMuscle(sets) { id -> mapOf(1L to MuscleGroup.CHEST, 2L to MuscleGroup.BACK)[id] }
        assertEquals(2, m[MuscleGroup.CHEST])
        assertEquals(1, m[MuscleGroup.BACK])
        assertEquals(2, m.size)
    }
}
