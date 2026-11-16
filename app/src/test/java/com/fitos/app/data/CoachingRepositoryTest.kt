package com.fitos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitos.app.core.database.AppDatabase
import com.fitos.app.core.database.DailyTargetsEntity
import com.fitos.app.domain.repository.CoachingPreview
import com.fitos.app.domain.repository.CoachingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class CoachingRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: AppDatabase
    private lateinit var repo: CoachingRepositoryImpl

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = CoachingRepositoryImpl(
            db, clock, ProfileRepositoryImpl(db, clock), WeightRepositoryImpl(db, clock),
            FoodLogRepositoryImpl(db, clock), WaterRepositoryImpl(db, clock), WorkoutRepositoryImpl(db, clock),
            SleepRepositoryImpl(db, clock), RecoveryRepositoryImpl(db, clock), SupplementRepositoryImpl(db, clock)
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun seedTargets() {
        db.dailyTargetsDao().insert(
            DailyTargetsEntity(
                caloriesKcal = 2200, proteinG = 150, carbohydratesG = 220, fatG = 70, fibreG = 30, waterMl = 3000,
                source = "USER", effectiveFrom = LocalDate.of(2026, 11, 1), createdAt = Instant.parse("2026-11-01T00:00:00Z")
            )
        )
    }

    private val reply = """{"schema_version":"1.0","type":"fitness_coaching_update","analysis":{"summary":"Eat a bit less."},
        "updated_targets":{"calories_kcal":2000,"protein_g":160,"carbs_g":180,"fat_g":65,"reason":"cut"}}"""

    private suspend fun current() = db.dailyTargetsDao().observeCurrent().first()!!

    @Test fun previewDoesNotChangeAnything() = runBlocking {
        seedTargets()
        assertTrue(repo.preview(reply) is CoachingPreview.Ready)
        assertEquals(2200, current().caloriesKcal)
        assertTrue(repo.observeHistory().first().isEmpty())
    }

    @Test fun confirmAppliesTargetsAndRollbackRestoresThemAppendOnly() = runBlocking {
        seedTargets()
        val id = repo.confirm(reply, applyTargets = true)
        assertEquals(2000, current().caloriesKcal)
        assertEquals("COACHING_IMPORT", current().source)
        val s = repo.observeHistory().first().single()
        assertEquals(CoachingStatus.APPLIED, s.status)
        assertTrue(s.canRollBack)
        assertEquals(2200, s.previousTargets!!.caloriesKcal)

        assertTrue(repo.rollback(id))
        assertEquals(2200, current().caloriesKcal)
        assertEquals(3, db.dailyTargetsDao().observeHistory(10).first().size) // original, applied, restored
        assertEquals(CoachingStatus.ROLLED_BACK, repo.observeHistory().first().single().status)
        assertFalse(repo.rollback(id)) // can't roll back twice
    }

    @Test fun confirmWithoutApplyingOnlyRecordsHistory() = runBlocking {
        seedTargets()
        repo.confirm(reply, applyTargets = false)
        assertEquals(2200, current().caloriesKcal)
        val s = repo.observeHistory().first().single()
        assertEquals(CoachingStatus.REVIEWED, s.status)
        assertFalse(s.canRollBack)
    }

    @Test fun sameResponseIsDetectedAsDuplicateAndCannotBeConfirmedTwice() = runBlocking {
        seedTargets()
        repo.confirm(reply, true)
        assertTrue(repo.preview(reply) is CoachingPreview.Duplicate)
        val failed = runCatching { repo.confirm(reply, true) }.isFailure
        assertTrue(failed)
        assertEquals(1, repo.observeHistory().first().size)
    }

    @Test fun invalidReplyIsRejectedAndConfirmThrows() = runBlocking {
        seedTargets()
        assertTrue(repo.preview("nonsense") is CoachingPreview.Rejected)
        assertTrue(runCatching { repo.confirm("nonsense", true) }.isFailure)
        assertEquals(2200, current().caloriesKcal)
    }

    @Test fun exportContainsProfileFreeJsonEvenWithNoData() = runBlocking {
        val text = repo.buildExportText(7)
        assertTrue(text.contains("\"schema_version\": \"1.0\""))
        assertTrue(text.contains("fitness_coaching_update"))
        assertTrue(text.contains("days_with_food_logged\": 0"))
    }
}
