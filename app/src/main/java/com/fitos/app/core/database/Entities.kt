package com.fitos.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** Single-row table (id is always [UserProfileEntity.SINGLETON_ID]). Body values are metric. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val name: String,
    val age: Int,
    val sex: String?,
    val heightCm: Double,
    val initialWeightKg: Double,
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val experience: String,
    val trainingDaysPerWeek: Int,
    val activityLevel: String,
    val goal: String,
    val dietaryPreferences: List<String>,
    val foodRestrictions: List<String>,
    val allergies: List<String>,
    val equipment: List<String>,
    val weightUnit: String,
    val heightUnit: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** History of goals; at most one row has [active] = true. */
@Entity(tableName = "fitness_goal", indices = [Index("active"), Index("startedOn")])
data class FitnessGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalType: String,
    val targetWeightKg: Double?,
    val startedOn: LocalDate,
    val active: Boolean,
    val createdAt: Instant
)

/**
 * Append-only target history: the current targets are the row with the latest [effectiveFrom]
 * (ties broken by id). Older rows are kept for audit and rollback.
 */
@Entity(tableName = "daily_targets", indices = [Index("effectiveFrom")])
data class DailyTargetsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caloriesKcal: Int,
    val proteinG: Int,
    val carbohydratesG: Int,
    val fatG: Int,
    val fibreG: Int,
    val waterMl: Int,
    val source: String,
    val effectiveFrom: LocalDate,
    val createdAt: Instant
)

@Entity(tableName = "weight_entry", indices = [Index("date"), Index("source")])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local calendar day the measurement belongs to. */
    val date: LocalDate,
    val weightKg: Double,
    /** MANUAL or HEALTH_CONNECT; manual entries must stay distinguishable. */
    val source: String,
    val recordedAt: Instant
)
