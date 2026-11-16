package com.artishorizon.tidefit.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.artishorizon.tidefit.core.database.AppDatabase
import com.artishorizon.tidefit.domain.model.ActivityLevel
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.FitnessExperience
import com.artishorizon.tidefit.domain.model.FitnessGoalType
import com.artishorizon.tidefit.domain.model.HeightUnit
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.model.ValueSource
import com.artishorizon.tidefit.domain.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ProfileRepositoryImpl
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)

    private val profile = UserProfile(
        name = "Asha", age = 29, sex = null, heightCm = 165.0,
        initialWeightKg = 80.0, currentWeightKg = 78.0, targetWeightKg = 68.0,
        experience = FitnessExperience.BEGINNER, trainingDaysPerWeek = 4,
        activityLevel = ActivityLevel.MODERATE, goal = FitnessGoalType.FAT_LOSS,
        dietaryPreferences = listOf("Vegetarian"), foodRestrictions = emptyList(),
        allergies = listOf("Peanuts"), equipment = listOf("Dumbbells"),
        weightUnit = WeightUnit.KG, heightUnit = HeightUnit.CM, updatedAt = Instant.EPOCH
    )

    private val targets = DailyTargets(2000, 150, 200, 60, 28, 2800, ValueSource.FORMULA_ESTIMATE)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ProfileRepositoryImpl(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun `onboarding stores profile, weight and goal but no targets unless accepted`() = runBlocking {
        repo.completeOnboarding(profile, startingTargets = null)

        assertEquals("Asha", repo.observeProfile().first()?.name)
        assertEquals(listOf("Peanuts"), repo.observeProfile().first()?.allergies)
        assertNull("targets must not be assigned silently", repo.observeCurrentTargets().first())
        assertEquals(1, db.weightEntryDao().observeRecent(10).first().size)
        assertNotNull(db.fitnessGoalDao().observeActive().first())
    }

    @Test fun `accepted targets are stored with their estimate source`() = runBlocking {
        repo.completeOnboarding(profile, targets)
        val stored = repo.observeCurrentTargets().first()!!
        assertEquals(2000, stored.caloriesKcal)
        assertEquals(ValueSource.FORMULA_ESTIMATE, stored.source)
    }

    @Test fun `saving targets appends history instead of overwriting`() = runBlocking {
        repo.completeOnboarding(profile, targets)
        repo.saveTargets(targets.copy(caloriesKcal = 1900, source = ValueSource.USER))

        assertEquals(1900, repo.observeCurrentTargets().first()!!.caloriesKcal)
        assertEquals(2, db.dailyTargetsDao().observeHistory(10).first().size)
    }

    @Test fun `changing goal records new goal and deactivates the old one`() = runBlocking {
        repo.completeOnboarding(profile, null)
        repo.updateProfile(profile.copy(goal = FitnessGoalType.MAINTENANCE))

        assertEquals("MAINTENANCE", db.fitnessGoalDao().observeActive().first()!!.goalType)
    }

    @Test fun `updating current weight adds a weight entry without touching history`() = runBlocking {
        repo.completeOnboarding(profile, null)
        repo.updateProfile(profile.copy(currentWeightKg = 77.0))

        val entries = db.weightEntryDao().observeRecent(10).first()
        assertEquals(2, entries.size)
        assertEquals(setOf(78.0, 77.0), entries.map { it.weightKg }.toSet())
    }
}
