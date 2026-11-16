package com.artishorizon.tidefit.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** User choices and bookkeeping for Health Connect sync. */
data class HealthSyncPrefs(
    /** Keep importing in the background and whenever the app opens. */
    val autoSync: Boolean = false,
    /** Also write TIDE Fit weight, water, meals and workouts into Health Connect. */
    val writeBack: Boolean = false,
    val lastSyncAt: Instant? = null,
    /** Plain-language result of the last sync, shown on the Data screen. */
    val lastSummary: String? = null
)

interface HealthSyncSettings {
    fun observePrefs(): Flow<HealthSyncPrefs>
    suspend fun current(): HealthSyncPrefs
    suspend fun setAutoSync(enabled: Boolean)
    suspend fun setWriteBack(enabled: Boolean)
    suspend fun recordSync(at: Instant, summary: String)

    /** What was last written to Health Connect: client record id to a fingerprint of its content. */
    suspend fun exported(): Map<String, String>
    suspend fun setExported(map: Map<String, String>)
}
