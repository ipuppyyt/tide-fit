package com.artishorizon.tidefit.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppSettings {
    val palette: Flow<String?>
    val avatarVersion: Flow<Long>
    val onboardingDone: Flow<Boolean>
    val defaultGlassMl: Flow<Int>
    suspend fun setPalette(id: String?)
    suspend fun setAvatarVersion(version: Long)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setDefaultGlassMl(ml: Int)
}
