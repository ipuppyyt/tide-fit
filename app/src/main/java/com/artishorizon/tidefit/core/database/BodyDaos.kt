package com.artishorizon.tidefit.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement ORDER BY date, recordedAt")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE id = :id")
    suspend fun get(id: Long): MeasurementEntity?

    @Insert suspend fun insert(m: MeasurementEntity): Long
    @Update suspend fun update(m: MeasurementEntity)

    @Query("DELETE FROM measurement WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SleepEntryEntity>>

    @Query("SELECT * FROM sleep_entry WHERE date = :date")
    suspend fun getByDate(date: LocalDate): SleepEntryEntity?

    @Insert suspend fun insert(e: SleepEntryEntity): Long
    @Update suspend fun update(e: SleepEntryEntity)

    @Query("DELETE FROM sleep_entry WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)
}

@Dao
interface RecoveryDao {
    @Query("SELECT * FROM recovery_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<RecoveryEntryEntity>>

    @Query("SELECT * FROM recovery_entry WHERE date = :date")
    suspend fun getByDate(date: LocalDate): RecoveryEntryEntity?

    @Insert suspend fun insert(e: RecoveryEntryEntity): Long
    @Update suspend fun update(e: RecoveryEntryEntity)

    @Query("DELETE FROM recovery_entry WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)
}

@Dao
interface SupplementDao {
    @Query("SELECT * FROM supplement ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplement WHERE id = :id")
    suspend fun get(id: Long): SupplementEntity?

    @Insert suspend fun insert(s: SupplementEntity): Long
    @Update suspend fun update(s: SupplementEntity)

    @Query("DELETE FROM supplement WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM supplement_log WHERE date BETWEEN :from AND :to")
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<SupplementLogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: SupplementLogEntity): Long

    @Query("DELETE FROM supplement_log WHERE supplementId = :supplementId AND date = :date")
    suspend fun deleteLog(supplementId: Long, date: LocalDate)
}

@Dao
interface ActivityDayDao {
    @Query("SELECT * FROM activity_day WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ActivityDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<ActivityDayEntity>)
}
