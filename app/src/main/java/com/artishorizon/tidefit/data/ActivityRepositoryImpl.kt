package com.artishorizon.tidefit.data

import com.artishorizon.tidefit.core.database.ActivityDayDao
import com.artishorizon.tidefit.core.database.ActivityDayEntity
import com.artishorizon.tidefit.domain.model.ActivityDay
import com.artishorizon.tidefit.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val dao: ActivityDayDao,
    private val clock: Clock
) : ActivityRepository {
    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ActivityDay>> =
        dao.observeRange(from, to).map { rows -> rows.map { ActivityDay(it.date, it.steps, it.activeKcal, it.exerciseMinutes) } }

    override suspend fun upsert(days: List<ActivityDay>) {
        val now = Instant.now(clock)
        dao.upsert(days.map { ActivityDayEntity(it.date, it.steps, it.activeKcal, it.exerciseMinutes, now) })
    }
}
