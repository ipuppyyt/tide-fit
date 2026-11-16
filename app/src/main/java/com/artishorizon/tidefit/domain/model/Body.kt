package com.artishorizon.tidefit.domain.model

import java.time.Instant
import java.time.LocalDate

data class WeightEntry(val id: Long = 0, val date: LocalDate, val weightKg: Double, val source: String = "MANUAL", val recordedAt: Instant = Instant.EPOCH)

/** Body measurements are stored in centimetres; body fat is a percentage. */
enum class MeasurementType(val label: String, val isPercent: Boolean = false) {
    WAIST("Waist"), HIPS("Hips"), CHEST("Chest"), NECK("Neck"), ARM("Upper arm"), THIGH("Thigh"), BODY_FAT("Body fat", isPercent = true)
}

data class Measurement(val id: Long = 0, val date: LocalDate, val type: MeasurementType, val value: Double)

data class SleepEntry(
    val id: Long = 0,
    /** The morning the sleep ended on. */
    val date: LocalDate,
    val durationMinutes: Int,
    /** 1 (poor) to 5 (great); optional. */
    val quality: Int? = null,
    val note: String? = null
)

/** Subjective daily check-in, each rated 1–5. No combined score is computed: it would imply precision that isn't there. */
data class RecoveryEntry(
    val id: Long = 0,
    val date: LocalDate,
    /** 1 = none, 5 = severe. */
    val soreness: Int,
    /** 1 = drained, 5 = energised. */
    val energy: Int,
    /** 1 = calm, 5 = very stressed. */
    val stress: Int,
    val note: String? = null
)

enum class TimeOfDay(val label: String) { MORNING("Morning"), AFTERNOON("Afternoon"), EVENING("Evening"), ANYTIME("Any time") }

data class Supplement(
    val id: Long = 0,
    val name: String,
    val dose: String? = null,
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
    val active: Boolean = true,
    val createdOn: LocalDate = LocalDate.EPOCH
)

data class SupplementLog(val supplementId: Long, val date: LocalDate)

/** Daily movement imported from Health Connect. */
data class ActivityDay(val date: LocalDate, val steps: Int, val activeKcal: Int, val exerciseMinutes: Int)
