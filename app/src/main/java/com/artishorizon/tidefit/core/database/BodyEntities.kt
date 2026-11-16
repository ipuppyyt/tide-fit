package com.artishorizon.tidefit.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "measurement", indices = [Index("date"), Index("type")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val type: String,
    /** Centimetres, or percent for body fat. */
    val value: Double,
    val recordedAt: Instant
)

/** One row per day (keyed by the morning the sleep ended). */
@Entity(tableName = "sleep_entry", indices = [Index("date", unique = true)])
data class SleepEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val durationMinutes: Int,
    val quality: Int?,
    val note: String?,
    val recordedAt: Instant
)

@Entity(tableName = "recovery_entry", indices = [Index("date", unique = true)])
data class RecoveryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val soreness: Int,
    val energy: Int,
    val stress: Int,
    val note: String?,
    val recordedAt: Instant
)

@Entity(tableName = "supplement")
data class SupplementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String?,
    val timeOfDay: String,
    val active: Boolean,
    val createdOn: LocalDate
)

@Entity(
    tableName = "supplement_log",
    foreignKeys = [ForeignKey(SupplementEntity::class, parentColumns = ["id"], childColumns = ["supplementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("supplementId", "date", unique = true), Index("date")]
)
data class SupplementLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val date: LocalDate,
    val takenAt: Instant
)
