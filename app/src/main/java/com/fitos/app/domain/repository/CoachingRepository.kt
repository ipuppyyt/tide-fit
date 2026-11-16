package com.fitos.app.domain.repository

import com.fitos.app.domain.coaching.CoachingImport
import com.fitos.app.domain.coaching.TargetsDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class CoachingStatus { REVIEWED, APPLIED, ROLLED_BACK }

data class CoachingSession(
    val id: Long,
    val importedAt: Instant,
    val summary: String?,
    val status: CoachingStatus,
    val previousTargets: TargetsDto?,
    val appliedTargets: TargetsDto?,
    val rolledBackAt: Instant?
) {
    val canRollBack: Boolean get() = status == CoachingStatus.APPLIED && previousTargets != null
}

/** Outcome of reading a pasted response: what it contains plus how it relates to what's already stored. */
sealed interface CoachingPreview {
    data class Ready(
        val parsed: CoachingImport.Result.Valid,
        val currentTargets: TargetsDto?
    ) : CoachingPreview

    data class Rejected(val errors: List<String>) : CoachingPreview

    /** The exact same response was already imported. */
    data class Duplicate(val session: CoachingSession) : CoachingPreview
}

interface CoachingRepository {
    /** The complete text to paste into ChatGPT: instructions followed by the JSON export of the last [days] days. */
    suspend fun buildExportText(days: Int): String
    suspend fun buildExportJson(days: Int): String

    suspend fun preview(raw: String): CoachingPreview

    /**
     * Records the import and, when [applyTargets] and the response has targets, appends them as the new current targets
     * in the same transaction. Returns the session id.
     */
    suspend fun confirm(raw: String, applyTargets: Boolean): Long

    fun observeHistory(): Flow<List<CoachingSession>>

    /** Appends the targets that were in force before the import as the new current targets. */
    suspend fun rollback(sessionId: Long): Boolean
}
