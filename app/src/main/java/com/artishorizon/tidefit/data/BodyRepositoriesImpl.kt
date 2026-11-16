package com.artishorizon.tidefit.data

import androidx.room.withTransaction
import com.artishorizon.tidefit.core.database.AppDatabase
import com.artishorizon.tidefit.core.database.MeasurementEntity
import com.artishorizon.tidefit.core.database.RecoveryEntryEntity
import com.artishorizon.tidefit.core.database.SleepEntryEntity
import com.artishorizon.tidefit.core.database.SupplementEntity
import com.artishorizon.tidefit.core.database.SupplementLogEntity
import com.artishorizon.tidefit.core.database.WeightEntryEntity
import com.artishorizon.tidefit.domain.model.Measurement
import com.artishorizon.tidefit.domain.model.MeasurementType
import com.artishorizon.tidefit.domain.model.RecoveryEntry
import com.artishorizon.tidefit.domain.model.SleepEntry
import com.artishorizon.tidefit.domain.model.Supplement
import com.artishorizon.tidefit.domain.model.SupplementLog
import com.artishorizon.tidefit.domain.model.TimeOfDay
import com.artishorizon.tidefit.domain.model.WeightEntry
import com.artishorizon.tidefit.domain.repository.MeasurementRepository
import com.artishorizon.tidefit.domain.repository.RecoveryRepository
import com.artishorizon.tidefit.domain.repository.SaveResult
import com.artishorizon.tidefit.domain.repository.SleepRepository
import com.artishorizon.tidefit.domain.repository.SupplementRepository
import com.artishorizon.tidefit.domain.repository.WeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock
) : WeightRepository {

    override fun observeAll(): Flow<List<WeightEntry>> = db.weightEntryDao().observeAll().map { l -> l.map { it.toDomain() } }

    override suspend fun add(date: LocalDate, weightKg: Double): SaveResult {
        validate(date, weightKg)?.let { return SaveResult.Invalid(it) }
        db.withTransaction {
            db.weightEntryDao().insert(WeightEntryEntity(date = date, weightKg = weightKg, source = "MANUAL", recordedAt = Instant.now(clock)))
            syncProfileWeight()
        }
        return SaveResult.Saved
    }

    override suspend fun update(id: Long, date: LocalDate, weightKg: Double): SaveResult {
        validate(date, weightKg)?.let { return SaveResult.Invalid(it) }
        db.withTransaction {
            val existing = db.weightEntryDao().get(id) ?: return@withTransaction
            db.weightEntryDao().update(existing.copy(date = date, weightKg = weightKg))
            syncProfileWeight()
        }
        return SaveResult.Saved
    }

    override suspend fun delete(id: Long) {
        db.withTransaction {
            db.weightEntryDao().delete(id)
            syncProfileWeight()
        }
    }

    override suspend fun importExternal(daily: Map<LocalDate, Double>, source: String): Int = db.withTransaction {
        val today = LocalDate.now(clock)
        val existing = db.weightEntryDao().observeAll().first()
        var changed = 0
        daily.forEach { (date, kg) ->
            if (date.isAfter(today) || kg < WeightRepository.MIN_KG || kg > WeightRepository.MAX_KG) return@forEach
            val onDate = existing.filter { it.date == date }
            when {
                onDate.any { it.source != source } -> Unit // the user's own entry wins
                onDate.isEmpty() -> {
                    db.weightEntryDao().insert(WeightEntryEntity(date = date, weightKg = kg, source = source, recordedAt = Instant.now(clock)))
                    changed++
                }
                onDate.last().weightKg != kg -> {
                    db.weightEntryDao().update(onDate.last().copy(weightKg = kg))
                    changed++
                }
            }
        }
        if (changed > 0) syncProfileWeight()
        changed
    }

    private fun validate(date: LocalDate, kg: Double): String? = when {
        date.isAfter(LocalDate.now(clock)) -> "The date can't be in the future."
        kg.isNaN() || kg < WeightRepository.MIN_KG || kg > WeightRepository.MAX_KG -> "Enter a weight between 20 and 500 kg."
        else -> null
    }

    /** The profile's current weight always mirrors the most recent entry, so targets and goals use real data. */
    private suspend fun syncProfileWeight() {
        val latest = db.weightEntryDao().latest() ?: return
        db.userProfileDao().updateCurrentWeight(latest.weightKg, Instant.now(clock))
    }
}

@Singleton
class MeasurementRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock
) : MeasurementRepository {

    override fun observeAll(): Flow<List<Measurement>> = db.measurementDao().observeAll().map { l -> l.mapNotNull { it.toDomain() } }

    override suspend fun add(date: LocalDate, type: MeasurementType, value: Double): SaveResult {
        validate(date, type, value)?.let { return SaveResult.Invalid(it) }
        db.measurementDao().insert(MeasurementEntity(date = date, type = type.name, value = value, recordedAt = Instant.now(clock)))
        return SaveResult.Saved
    }

    override suspend fun update(id: Long, date: LocalDate, value: Double): SaveResult {
        val existing = db.measurementDao().get(id) ?: return SaveResult.Invalid("That entry no longer exists.")
        val type = runCatching { MeasurementType.valueOf(existing.type) }.getOrNull() ?: return SaveResult.Invalid("Unknown measurement type.")
        validate(date, type, value)?.let { return SaveResult.Invalid(it) }
        db.measurementDao().update(existing.copy(date = date, value = value))
        return SaveResult.Saved
    }

    override suspend fun delete(id: Long) = db.measurementDao().delete(id)

    private fun validate(date: LocalDate, type: MeasurementType, value: Double): String? = when {
        date.isAfter(LocalDate.now(clock)) -> "The date can't be in the future."
        type.isPercent && (value.isNaN() || value < 2.0 || value > 70.0) -> "Body fat must be between 2 and 70 %."
        !type.isPercent && (value.isNaN() || value < 5.0 || value > 300.0) -> "Enter a measurement between 5 and 300 cm."
        else -> null
    }
}

@Singleton
class SleepRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock
) : SleepRepository {

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>> =
        db.sleepDao().observeRange(from, to).map { l -> l.map { it.toDomain() } }

    override suspend fun save(entry: SleepEntry): SaveResult {
        when {
            entry.date.isAfter(LocalDate.now(clock)) -> return SaveResult.Invalid("The date can't be in the future.")
            entry.durationMinutes <= 0 || entry.durationMinutes > SleepRepository.MAX_MINUTES -> return SaveResult.Invalid("Enter a sleep duration between 1 minute and 24 hours.")
            entry.quality != null && entry.quality !in 1..5 -> return SaveResult.Invalid("Quality must be 1 to 5.")
        }
        db.withTransaction {
            val existing = db.sleepDao().getByDate(entry.date)
            val row = SleepEntryEntity(
                id = existing?.id ?: 0, date = entry.date, durationMinutes = entry.durationMinutes,
                quality = entry.quality, note = entry.note?.trim()?.ifBlank { null }, recordedAt = Instant.now(clock)
            )
            if (existing == null) db.sleepDao().insert(row) else db.sleepDao().update(row)
        }
        return SaveResult.Saved
    }

    override suspend fun delete(date: LocalDate) = db.sleepDao().deleteByDate(date)

    override suspend fun importExternal(nights: Map<LocalDate, Int>): Int = db.withTransaction {
        val today = LocalDate.now(clock)
        var added = 0
        nights.forEach { (date, minutes) ->
            if (date.isAfter(today) || minutes <= 0 || minutes > SleepRepository.MAX_MINUTES) return@forEach
            if (db.sleepDao().getByDate(date) != null) return@forEach
            db.sleepDao().insert(SleepEntryEntity(date = date, durationMinutes = minutes, quality = null, note = null, recordedAt = Instant.now(clock)))
            added++
        }
        added
    }
}

@Singleton
class RecoveryRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock
) : RecoveryRepository {

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<RecoveryEntry>> =
        db.recoveryDao().observeRange(from, to).map { l -> l.map { it.toDomain() } }

    override suspend fun save(entry: RecoveryEntry): SaveResult {
        when {
            entry.date.isAfter(LocalDate.now(clock)) -> return SaveResult.Invalid("The date can't be in the future.")
            listOf(entry.soreness, entry.energy, entry.stress).any { it !in 1..5 } -> return SaveResult.Invalid("Ratings must be 1 to 5.")
        }
        db.withTransaction {
            val existing = db.recoveryDao().getByDate(entry.date)
            val row = RecoveryEntryEntity(
                id = existing?.id ?: 0, date = entry.date, soreness = entry.soreness, energy = entry.energy,
                stress = entry.stress, note = entry.note?.trim()?.ifBlank { null }, recordedAt = Instant.now(clock)
            )
            if (existing == null) db.recoveryDao().insert(row) else db.recoveryDao().update(row)
        }
        return SaveResult.Saved
    }

    override suspend fun delete(date: LocalDate) = db.recoveryDao().deleteByDate(date)
}

@Singleton
class SupplementRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock
) : SupplementRepository {

    override fun observeAll(): Flow<List<Supplement>> = db.supplementDao().observeAll().map { l -> l.map { it.toDomain() } }

    override fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<SupplementLog>> =
        db.supplementDao().observeLogs(from, to).map { l -> l.map { SupplementLog(it.supplementId, it.date) } }

    override suspend fun save(supplement: Supplement): SaveResult {
        val name = supplement.name.trim()
        if (name.isEmpty()) return SaveResult.Invalid("Give the supplement a name.")
        val dose = supplement.dose?.trim()?.ifBlank { null }
        if (supplement.id == 0L) {
            db.supplementDao().insert(
                SupplementEntity(name = name, dose = dose, timeOfDay = supplement.timeOfDay.name, active = supplement.active, createdOn = LocalDate.now(clock))
            )
        } else {
            val existing = db.supplementDao().get(supplement.id) ?: return SaveResult.Invalid("That supplement no longer exists.")
            db.supplementDao().update(existing.copy(name = name, dose = dose, timeOfDay = supplement.timeOfDay.name, active = supplement.active))
        }
        return SaveResult.Saved
    }

    override suspend fun setTaken(supplementId: Long, date: LocalDate, taken: Boolean) {
        if (taken) db.supplementDao().insertLog(SupplementLogEntity(supplementId = supplementId, date = date, takenAt = Instant.now(clock)))
        else db.supplementDao().deleteLog(supplementId, date)
    }

    override suspend fun delete(id: Long) = db.supplementDao().delete(id)
}

private fun WeightEntryEntity.toDomain() = WeightEntry(id, date, weightKg, source, recordedAt)

private fun MeasurementEntity.toDomain(): Measurement? =
    runCatching { MeasurementType.valueOf(type) }.getOrNull()?.let { Measurement(id, date, it, value) }

private fun SleepEntryEntity.toDomain() = SleepEntry(id, date, durationMinutes, quality, note)
private fun RecoveryEntryEntity.toDomain() = RecoveryEntry(id, date, soreness, energy, stress, note)
private fun SupplementEntity.toDomain() = Supplement(
    id, name, dose, runCatching { TimeOfDay.valueOf(timeOfDay) }.getOrDefault(TimeOfDay.ANYTIME), active, createdOn
)
