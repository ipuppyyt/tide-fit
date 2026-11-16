package com.artishorizon.tidefit.domain.repository

import com.artishorizon.tidefit.domain.model.Measurement
import com.artishorizon.tidefit.domain.model.MeasurementType
import com.artishorizon.tidefit.domain.model.RecoveryEntry
import com.artishorizon.tidefit.domain.model.SleepEntry
import com.artishorizon.tidefit.domain.model.Supplement
import com.artishorizon.tidefit.domain.model.SupplementLog
import com.artishorizon.tidefit.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Result of a validated save; [error] is a user-facing message when the input was rejected. */
sealed interface SaveResult {
    data object Saved : SaveResult
    data class Invalid(val error: String) : SaveResult
}

interface WeightRepository {
    fun observeAll(): Flow<List<WeightEntry>>
    suspend fun add(date: LocalDate, weightKg: Double): SaveResult
    suspend fun update(id: Long, date: LocalDate, weightKg: Double): SaveResult
    suspend fun delete(id: Long)

    /**
     * Adds one weight per date from an external source (Health Connect). A date the user already logged by hand is
     * never touched; a date previously imported is updated if the value changed. Returns how many rows were added or changed.
     */
    suspend fun importExternal(daily: Map<LocalDate, Double>, source: String): Int

    companion object {
        const val MIN_KG = 20.0
        const val MAX_KG = 500.0
    }
}

interface MeasurementRepository {
    fun observeAll(): Flow<List<Measurement>>
    suspend fun add(date: LocalDate, type: MeasurementType, value: Double): SaveResult
    suspend fun update(id: Long, date: LocalDate, value: Double): SaveResult
    suspend fun delete(id: Long)
}

interface SleepRepository {
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>>
    /** One entry per date: saving an existing date replaces it. */
    suspend fun save(entry: SleepEntry): SaveResult
    suspend fun delete(date: LocalDate)

    /** Adds nights from an external source only for dates with no entry yet; never overwrites what the user logged. Returns the number added. */
    suspend fun importExternal(nights: Map<LocalDate, Int>): Int

    companion object {
        const val MAX_MINUTES = 24 * 60
    }
}

interface RecoveryRepository {
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<RecoveryEntry>>
    suspend fun save(entry: RecoveryEntry): SaveResult
    suspend fun delete(date: LocalDate)
}

interface SupplementRepository {
    fun observeAll(): Flow<List<Supplement>>
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<SupplementLog>>
    suspend fun save(supplement: Supplement): SaveResult
    suspend fun setTaken(supplementId: Long, date: LocalDate, taken: Boolean)
    suspend fun delete(id: Long)
}
