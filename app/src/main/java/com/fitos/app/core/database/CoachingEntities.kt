package com.fitos.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** One imported ChatGPT response and what the user did with it. The raw JSON is kept for audit. */
@Entity(tableName = "coaching_session", indices = [Index("contentHash", unique = true), Index("importedAt")])
data class CoachingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val importedAt: Instant,
    val contentHash: String,
    val summary: String?,
    val rawJson: String,
    /** REVIEWED (nothing applied), APPLIED or ROLLED_BACK. */
    val status: String,
    /** JSON of the targets that were in force before this import changed them; null when targets weren't applied. */
    val previousTargetsJson: String?,
    /** JSON of the targets this import applied; null when targets weren't applied. */
    val appliedTargetsJson: String?,
    val rolledBackAt: Instant?
)

@Dao
interface CoachingDao {
    @Query("SELECT * FROM coaching_session ORDER BY importedAt DESC, id DESC")
    fun observeAll(): Flow<List<CoachingSessionEntity>>

    @Query("SELECT * FROM coaching_session WHERE id = :id")
    suspend fun get(id: Long): CoachingSessionEntity?

    @Query("SELECT * FROM coaching_session WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): CoachingSessionEntity?

    @Insert
    suspend fun insert(s: CoachingSessionEntity): Long

    @Query("UPDATE coaching_session SET status = :status, rolledBackAt = :at WHERE id = :id")
    suspend fun markRolledBack(id: Long, status: String, at: Instant)
}
