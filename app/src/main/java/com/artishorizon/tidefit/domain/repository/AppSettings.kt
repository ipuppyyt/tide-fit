package com.artishorizon.tidefit.domain.repository

import com.artishorizon.tidefit.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface AppSettings {
    val themeMode: Flow<ThemeMode>
    val dynamicColor: Flow<Boolean>
    val onboardingDone: Flow<Boolean>
    val defaultGlassMl: Flow<Int>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setDefaultGlassMl(ml: Int)
}
