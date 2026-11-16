package com.fitos.app.data

import androidx.room.withTransaction
import com.fitos.app.core.database.AppDatabase
import com.fitos.app.core.database.CoachingSessionEntity
import com.fitos.app.core.database.DailyTargetsEntity
import com.fitos.app.domain.coaching.CoachingExport
import com.fitos.app.domain.coaching.CoachingImport
import com.fitos.app.domain.coaching.TargetsDto
import com.fitos.app.domain.model.DailyTargets
import com.fitos.app.domain.model.ValueSource
import com.fitos.app.domain.repository.CoachingPreview
import com.fitos.app.domain.repository.CoachingRepository
import com.fitos.app.domain.repository.CoachingSession
import com.fitos.app.domain.repository.CoachingStatus
import com.fitos.app.domain.repository.FoodLogRepository
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.domain.repository.RecoveryRepository
import com.fitos.app.domain.repository.SleepRepository
import com.fitos.app.domain.repository.SupplementRepository
import com.fitos.app.domain.repository.WaterRepository
import com.fitos.app.domain.repository.WeightRepository
import com.fitos.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachingRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val profiles: ProfileRepository,
    private val weights: WeightRepository,
    private val foodLog: FoodLogRepository,
    private val water: WaterRepository,
    private val workouts: WorkoutRepository,
    private val sleep: SleepRepository,
    private val recovery: RecoveryRepository,
    private val supplements: SupplementRepository
) : CoachingRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun buildExportJson(days: Int): String {
        val today = LocalDate.now(clock)
        val from = today.minusDays(days.toLong() - 1)
        // Supplement adherence and the weight trend need a bit more history than the export window.
        val doc = CoachingExport.build(
            today = today, days = days,
            profile = profiles.observeProfile().first(),
            targets = profiles.observeCurrentTargets().first(),
            weights = weights.observeAll().first(),
            food = foodLog.observeRange(from, today).first(),
            water = water.observeRange(from, today).first(),
            workouts = workouts.observeHistory().first(),
            sleep = sleep.observeRange(from, today).first(),
            recovery = recovery.observeRange(from, today).first(),
            supplements = supplements.observeAll().first(),
            supplementLogs = supplements.observeLogs(from, today).first()
        )
        return CoachingExport.toJson(doc)
    }

    override suspend fun buildExportText(days: Int): String =
        CoachingExport.PROMPT + "\n\nMY DATA:\n" + buildExportJson(days)

    private suspend fun currentTargets(): TargetsDto? = profiles.observeCurrentTargets().first()?.toDto()

    override suspend fun preview(raw: String): CoachingPreview {
        val current = currentTargets()
        return when (val r = CoachingImport.parse(raw, current)) {
            is CoachingImport.Result.Invalid -> CoachingPreview.Rejected(r.errors)
            is CoachingImport.Result.Valid -> {
                val dup = db.coachingDao().findByHash(r.hash)
                if (dup != null) CoachingPreview.Duplicate(dup.toDomain()) else CoachingPreview.Ready(r, current)
            }
        }
    }

    override suspend fun confirm(raw: String, applyTargets: Boolean): Long {
        // Re-validate here: never trust that the UI previewed exactly this text.
        val parsed = when (val r = CoachingImport.parse(raw, currentTargets())) {
            is CoachingImport.Result.Valid -> r
            is CoachingImport.Result.Invalid -> throw IllegalArgumentException(r.errors.first())
        }
        val now = Instant.now(clock)
        return db.withTransaction {
            if (db.coachingDao().findByHash(parsed.hash) != null) throw IllegalStateException("This response was already imported.")
            val proposed = parsed.targets.takeIf { applyTargets }
            val previous = if (proposed != null) db.dailyTargetsDao().latest()?.toDto() else null
            if (proposed != null) {
                db.dailyTargetsDao().insert(proposed.toEntity(ValueSource.COACHING_IMPORT, LocalDate.now(clock), now))
            }
            db.coachingDao().insert(
                CoachingSessionEntity(
                    importedAt = now, contentHash = parsed.hash,
                    summary = parsed.update.analysis?.summary?.trim()?.take(400),
                    rawJson = raw, status = if (proposed != null) CoachingStatus.APPLIED.name else CoachingStatus.REVIEWED.name,
                    previousTargetsJson = previous?.let { json.encodeToString(TargetsDto.serializer(), it) },
                    appliedTargetsJson = proposed?.let { json.encodeToString(TargetsDto.serializer(), it) },
                    rolledBackAt = null
                )
            )
        }
    }

    override fun observeHistory(): Flow<List<CoachingSession>> = db.coachingDao().observeAll().map { l -> l.map { it.toDomain() } }

    override suspend fun rollback(sessionId: Long): Boolean {
        val now = Instant.now(clock)
        return db.withTransaction {
            val s = db.coachingDao().get(sessionId) ?: return@withTransaction false
            val previous = s.previousTargetsJson?.let { json.decodeFromString(TargetsDto.serializer(), it) }
            if (s.status != CoachingStatus.APPLIED.name || previous == null) return@withTransaction false
            // Append, never delete: the target history stays an audit trail.
            db.dailyTargetsDao().insert(previous.toEntity(ValueSource.USER, LocalDate.now(clock), now))
            db.coachingDao().markRolledBack(sessionId, CoachingStatus.ROLLED_BACK.name, now)
            true
        }
    }

    private fun CoachingSessionEntity.toDomain(): CoachingSession {
        fun dto(s: String?) = s?.let { runCatching { json.decodeFromString(TargetsDto.serializer(), it) }.getOrNull() }
        return CoachingSession(
            id, importedAt, summary,
            runCatching { CoachingStatus.valueOf(status) }.getOrDefault(CoachingStatus.REVIEWED),
            dto(previousTargetsJson), dto(appliedTargetsJson), rolledBackAt
        )
    }
}

private fun DailyTargets.toDto() = TargetsDto(caloriesKcal, proteinG, carbohydratesG, fatG, fibreG, waterMl)
private fun DailyTargetsEntity.toDto() = TargetsDto(caloriesKcal, proteinG, carbohydratesG, fatG, fibreG, waterMl)
private fun TargetsDto.toEntity(source: ValueSource, effectiveFrom: LocalDate, createdAt: Instant) = DailyTargetsEntity(
    caloriesKcal = caloriesKcal, proteinG = proteinG, carbohydratesG = carbsG, fatG = fatG, fibreG = fibreG, waterMl = waterMl,
    source = source.name, effectiveFrom = effectiveFrom, createdAt = createdAt
)
