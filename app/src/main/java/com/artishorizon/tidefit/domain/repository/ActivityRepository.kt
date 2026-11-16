package com.artishorizon.tidefit.domain.repository

import com.artishorizon.tidefit.domain.model.ActivityDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ActivityRepository {
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ActivityDay>>

    /** Replaces the stored value for each given day. Days with no data are simply not passed in. */
    suspend fun upsert(days: List<ActivityDay>)
}
