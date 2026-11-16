package com.fitos.app.domain.repository

import com.fitos.app.domain.model.ReminderKind
import com.fitos.app.domain.model.ReminderSetting
import kotlinx.coroutines.flow.Flow

interface ReminderSettings {
    fun observe(): Flow<List<ReminderSetting>>
    suspend fun get(kind: ReminderKind): ReminderSetting
    suspend fun set(setting: ReminderSetting)
}
