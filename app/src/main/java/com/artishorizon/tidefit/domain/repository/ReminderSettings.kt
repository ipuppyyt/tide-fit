package com.artishorizon.tidefit.domain.repository

import com.artishorizon.tidefit.domain.model.ReminderKind
import com.artishorizon.tidefit.domain.model.ReminderSetting
import kotlinx.coroutines.flow.Flow

interface ReminderSettings {
    fun observe(): Flow<List<ReminderSetting>>
    suspend fun get(kind: ReminderKind): ReminderSetting
    suspend fun set(setting: ReminderSetting)
}
