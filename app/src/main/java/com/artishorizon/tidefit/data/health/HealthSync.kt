package com.artishorizon.tidefit.data.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.artishorizon.tidefit.domain.repository.HealthSyncSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps Health Connect in sync: a periodic background job, plus a catch-up whenever the app is opened. */
@Singleton
class HealthSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: HealthConnectSource,
    private val settings: HealthSyncSettings,
    private val clock: Clock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val work get() = WorkManager.getInstance(context)

    /** Queues (or cancels) the periodic job to match the setting. Safe to call repeatedly. */
    fun apply(enabled: Boolean) {
        if (!enabled) { work.cancelUniqueWork(UNIQUE); return }
        work.enqueueUniquePeriodicWork(
            UNIQUE, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS).build()
        )
    }

    /** Called when the app comes to the foreground; skips if it already synced in the last [MIN_GAP_MINUTES] minutes. */
    fun syncOnOpen() {
        scope.launch {
            val prefs = settings.current()
            if (!prefs.autoSync) return@launch
            val last = prefs.lastSyncAt
            if (last != null && Duration.between(last, Instant.now(clock)).toMinutes() < MIN_GAP_MINUTES) return@launch
            runCatching { source.sync(background = false) }
        }
    }

    /** Applies the saved setting at app start so the job survives updates and restores. */
    fun restore() { scope.launch { apply(settings.current().autoSync) } }

    private companion object {
        const val UNIQUE = "health-sync"
        const val MIN_GAP_MINUTES = 15L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HealthSyncEntryPoint {
    fun healthSource(): HealthConnectSource
    fun healthSettings(): HealthSyncSettings
}

class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, HealthSyncEntryPoint::class.java)
        if (!ep.healthSettings().current().autoSync) return Result.success()
        return runCatching { ep.healthSource().sync(background = true) }.fold({ Result.success() }, { Result.retry() })
    }
}
