package com.fitos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitos.app.core.database.AppDatabase
import com.fitos.app.core.database.UserProfileEntity
import com.fitos.app.domain.model.MeasurementType
import com.fitos.app.domain.model.RecoveryEntry
import com.fitos.app.domain.model.SleepEntry
import com.fitos.app.domain.model.Supplement
import com.fitos.app.domain.repository.SaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class BodyRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 16)
    private lateinit var db: AppDatabase
    private lateinit var weights: WeightRepositoryImpl
    private lateinit var measurements: MeasurementRepositoryImpl
    private lateinit var sleep: SleepRepositoryImpl
    private lateinit var recovery: RecoveryRepositoryImpl
    private lateinit var supplements: SupplementRepositoryImpl

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        weights = WeightRepositoryImpl(db, clock)
        measurements = MeasurementRepositoryImpl(db, clock)
        sleep = SleepRepositoryImpl(db, clock)
        recovery = RecoveryRepositoryImpl(db, clock)
        supplements = SupplementRepositoryImpl(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun seedProfile(kg: Double) {
        val now = Instant.now(clock)
        db.userProfileDao().upsert(
            UserProfileEntity(
                name = "T", age = 30, sex = null, heightCm = 180.0, initialWeightKg = kg, currentWeightKg = kg,
                targetWeightKg = 75.0, experience = "BEGINNER", trainingDaysPerWeek = 3, activityLevel = "MODERATE",
                goal = "FAT_LOSS", dietaryPreferences = emptyList(), foodRestrictions = emptyList(), allergies = emptyList(),
                equipment = emptyList(), weightUnit = "KG", heightUnit = "CM", createdAt = now, updatedAt = now
            )
        )
    }

    @Test fun weightRejectsFutureAndImplausibleValues() = runBlocking {
        assertTrue(weights.add(today.plusDays(1), 80.0) is SaveResult.Invalid)
        assertTrue(weights.add(today, 5.0) is SaveResult.Invalid)
        assertTrue(weights.add(today, 600.0) is SaveResult.Invalid)
        assertTrue(weights.observeAll().first().isEmpty())
    }

    @Test fun profileWeightFollowsLatestEntryAcrossAddEditDelete() = runBlocking {
        seedProfile(90.0)
        weights.add(today.minusDays(5), 88.0)
        weights.add(today, 86.0)
        assertEquals(86.0, db.userProfileDao().get()!!.currentWeightKg, 0.001)

        // A back-dated entry must not override the newer one.
        weights.add(today.minusDays(2), 87.0)
        assertEquals(86.0, db.userProfileDao().get()!!.currentWeightKg, 0.001)

        val latest = weights.observeAll().first().last()
        weights.update(latest.id, latest.date, 85.5)
        assertEquals(85.5, db.userProfileDao().get()!!.currentWeightKg, 0.001)

        weights.delete(latest.id)
        assertEquals(87.0, db.userProfileDao().get()!!.currentWeightKg, 0.001)
    }

    @Test fun measurementValidatesByType() = runBlocking {
        assertTrue(measurements.add(today, MeasurementType.BODY_FAT, 85.0) is SaveResult.Invalid)
        assertTrue(measurements.add(today, MeasurementType.WAIST, 2.0) is SaveResult.Invalid)
        assertEquals(SaveResult.Saved, measurements.add(today, MeasurementType.WAIST, 84.5))
        assertEquals(SaveResult.Saved, measurements.add(today, MeasurementType.BODY_FAT, 18.0))
        val all = measurements.observeAll().first()
        assertEquals(2, all.size)
        measurements.update(all.first { it.type == MeasurementType.WAIST }.id, today, 84.0)
        assertEquals(84.0, measurements.observeAll().first().first { it.type == MeasurementType.WAIST }.value, 0.001)
    }

    @Test fun sleepIsOneEntryPerDay() = runBlocking {
        sleep.save(SleepEntry(date = today, durationMinutes = 400, quality = 3))
        sleep.save(SleepEntry(date = today, durationMinutes = 450, quality = 5))
        val rows = sleep.observeRange(today.minusDays(7), today).first()
        assertEquals(1, rows.size)
        assertEquals(450, rows[0].durationMinutes)
        assertTrue(sleep.save(SleepEntry(date = today, durationMinutes = 0)) is SaveResult.Invalid)
        assertTrue(sleep.save(SleepEntry(date = today, durationMinutes = 60, quality = 9)) is SaveResult.Invalid)
        sleep.delete(today)
        assertTrue(sleep.observeRange(today.minusDays(7), today).first().isEmpty())
    }

    @Test fun recoveryValidatesRatingsAndUpserts() = runBlocking {
        assertTrue(recovery.save(RecoveryEntry(date = today, soreness = 0, energy = 3, stress = 3)) is SaveResult.Invalid)
        recovery.save(RecoveryEntry(date = today, soreness = 2, energy = 4, stress = 3))
        recovery.save(RecoveryEntry(date = today, soreness = 3, energy = 4, stress = 2))
        val rows = recovery.observeRange(today.minusDays(7), today).first()
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].soreness)
    }

    @Test fun supplementLogsToggleAndCascadeOnDelete() = runBlocking {
        assertTrue(supplements.save(Supplement(name = "  ")) is SaveResult.Invalid)
        supplements.save(Supplement(name = "Creatine", dose = "5 g"))
        val s = supplements.observeAll().first().single()
        assertEquals(today, s.createdOn)

        supplements.setTaken(s.id, today, true)
        supplements.setTaken(s.id, today, true) // idempotent
        assertEquals(1, supplements.observeLogs(today, today).first().size)
        supplements.setTaken(s.id, today, false)
        assertTrue(supplements.observeLogs(today, today).first().isEmpty())

        supplements.setTaken(s.id, today, true)
        supplements.delete(s.id)
        assertTrue(supplements.observeLogs(today, today).first().isEmpty())
    }
}
