package com.artishorizon.tidefit.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET currentWeightKg = :kg, updatedAt = :at WHERE id = 1")
    suspend fun updateCurrentWeight(kg: Double, at: java.time.Instant)
}

@Dao
interface FitnessGoalDao {
    @Query("UPDATE fitness_goal SET active = 0 WHERE active = 1")
    suspend fun deactivateAll()

    @Insert
    suspend fun insert(goal: FitnessGoalEntity): Long

    @Query("SELECT * FROM fitness_goal WHERE active = 1 ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<FitnessGoalEntity?>
}

@Dao
interface DailyTargetsDao {
    @Insert
    suspend fun insert(targets: DailyTargetsEntity): Long

    @Query("SELECT * FROM daily_targets ORDER BY effectiveFrom DESC, id DESC LIMIT 1")
    fun observeCurrent(): Flow<DailyTargetsEntity?>

    @Query("SELECT * FROM daily_targets ORDER BY effectiveFrom DESC, id DESC LIMIT 1")
    suspend fun latest(): DailyTargetsEntity?

    @Query("SELECT * FROM daily_targets ORDER BY effectiveFrom DESC, id DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<DailyTargetsEntity>>
}

@Dao
interface WeightEntryDao {
    @Insert
    suspend fun insert(entry: WeightEntryEntity): Long

    @Query("SELECT * FROM weight_entry ORDER BY date DESC, recordedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry WHERE date BETWEEN :from AND :to ORDER BY date ASC, recordedAt ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry ORDER BY date ASC, recordedAt ASC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry WHERE id = :id")
    suspend fun get(id: Long): WeightEntryEntity?

    @Query("SELECT * FROM weight_entry ORDER BY date DESC, recordedAt DESC LIMIT 1")
    suspend fun latest(): WeightEntryEntity?

    @Update
    suspend fun update(entry: WeightEntryEntity)

    @Query("DELETE FROM weight_entry WHERE id = :id")
    suspend fun delete(id: Long)
}
