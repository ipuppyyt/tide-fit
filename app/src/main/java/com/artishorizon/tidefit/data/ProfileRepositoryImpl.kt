package com.artishorizon.tidefit.data

import androidx.room.withTransaction
import com.artishorizon.tidefit.core.database.AppDatabase
import com.artishorizon.tidefit.core.database.DailyTargetsEntity
import com.artishorizon.tidefit.core.database.FitnessGoalEntity
import com.artishorizon.tidefit.core.database.UserProfileEntity
import com.artishorizon.tidefit.core.database.WeightEntryEntity
import com.artishorizon.tidefit.domain.model.ActivityLevel
import com.artishorizon.tidefit.domain.model.BiologicalSex
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.FitnessExperience
import com.artishorizon.tidefit.domain.model.FitnessGoalType
import com.artishorizon.tidefit.domain.model.HeightUnit
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.model.ValueSource
import com.artishorizon.tidefit.domain.model.WeightUnit
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock
) : ProfileRepository {

    override fun observeProfile(): Flow<UserProfile?> = db.userProfileDao().observe().map { it?.toDomain() }

    override fun observeCurrentTargets(): Flow<DailyTargets?> =
        db.dailyTargetsDao().observeCurrent().map { it?.toDomain() }

    override suspend fun completeOnboarding(profile: UserProfile, startingTargets: DailyTargets?) {
        val now = Instant.now(clock)
        val today = LocalDate.now(clock)
        db.withTransaction {
            db.userProfileDao().upsert(profile.toEntity(createdAt = now, updatedAt = now))
            recordGoal(profile, today, now)
            db.weightEntryDao().insert(
                WeightEntryEntity(date = today, weightKg = profile.currentWeightKg, source = "MANUAL", recordedAt = now)
            )
            startingTargets?.let { db.dailyTargetsDao().insert(it.toEntity(today, now)) }
        }
    }

    override suspend fun updateProfile(profile: UserProfile) {
        val now = Instant.now(clock)
        val today = LocalDate.now(clock)
        db.withTransaction {
            val existing = db.userProfileDao().get()
            db.userProfileDao().upsert(profile.toEntity(createdAt = existing?.createdAt ?: now, updatedAt = now))
            if (existing == null || existing.goal != profile.goal.name || existing.targetWeightKg != profile.targetWeightKg) {
                recordGoal(profile, today, now)
            }
            if (existing != null && existing.currentWeightKg != profile.currentWeightKg) {
                db.weightEntryDao().insert(
                    WeightEntryEntity(date = today, weightKg = profile.currentWeightKg, source = "MANUAL", recordedAt = now)
                )
            }
        }
    }

    override suspend fun saveTargets(targets: DailyTargets) {
        val now = Instant.now(clock)
        db.dailyTargetsDao().insert(targets.toEntity(LocalDate.now(clock), now))
    }

    private suspend fun recordGoal(profile: UserProfile, today: LocalDate, now: Instant) {
        db.fitnessGoalDao().deactivateAll()
        db.fitnessGoalDao().insert(
            FitnessGoalEntity(
                goalType = profile.goal.name,
                targetWeightKg = profile.targetWeightKg,
                startedOn = today,
                active = true,
                createdAt = now
            )
        )
    }
}

private fun UserProfileEntity.toDomain() = UserProfile(
    name = name,
    age = age,
    sex = sex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() },
    heightCm = heightCm,
    initialWeightKg = initialWeightKg,
    currentWeightKg = currentWeightKg,
    targetWeightKg = targetWeightKg,
    experience = runCatching { FitnessExperience.valueOf(experience) }.getOrDefault(FitnessExperience.BEGINNER),
    trainingDaysPerWeek = trainingDaysPerWeek,
    activityLevel = runCatching { ActivityLevel.valueOf(activityLevel) }.getOrDefault(ActivityLevel.MODERATE),
    goal = runCatching { FitnessGoalType.valueOf(goal) }.getOrDefault(FitnessGoalType.GENERAL_FITNESS),
    dietaryPreferences = dietaryPreferences,
    foodRestrictions = foodRestrictions,
    allergies = allergies,
    equipment = equipment,
    weightUnit = runCatching { WeightUnit.valueOf(weightUnit) }.getOrDefault(WeightUnit.KG),
    heightUnit = runCatching { HeightUnit.valueOf(heightUnit) }.getOrDefault(HeightUnit.CM),
    updatedAt = updatedAt
)

private fun UserProfile.toEntity(createdAt: Instant, updatedAt: Instant) = UserProfileEntity(
    name = name,
    age = age,
    sex = sex?.name,
    heightCm = heightCm,
    initialWeightKg = initialWeightKg,
    currentWeightKg = currentWeightKg,
    targetWeightKg = targetWeightKg,
    experience = experience.name,
    trainingDaysPerWeek = trainingDaysPerWeek,
    activityLevel = activityLevel.name,
    goal = goal.name,
    dietaryPreferences = dietaryPreferences,
    foodRestrictions = foodRestrictions,
    allergies = allergies,
    equipment = equipment,
    weightUnit = weightUnit.name,
    heightUnit = heightUnit.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun DailyTargetsEntity.toDomain() = DailyTargets(
    caloriesKcal = caloriesKcal,
    proteinG = proteinG,
    carbohydratesG = carbohydratesG,
    fatG = fatG,
    fibreG = fibreG,
    waterMl = waterMl,
    source = runCatching { ValueSource.valueOf(source) }.getOrDefault(ValueSource.USER)
)

private fun DailyTargets.toEntity(effectiveFrom: LocalDate, createdAt: Instant) = DailyTargetsEntity(
    caloriesKcal = caloriesKcal,
    proteinG = proteinG,
    carbohydratesG = carbohydratesG,
    fatG = fatG,
    fibreG = fibreG,
    waterMl = waterMl,
    source = source.name,
    effectiveFrom = effectiveFrom,
    createdAt = createdAt
)
