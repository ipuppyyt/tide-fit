package com.artishorizon.tidefit

import android.app.Application
import com.artishorizon.tidefit.data.ExerciseSeeder
import com.artishorizon.tidefit.data.FoodSeeder
import com.artishorizon.tidefit.data.health.HealthSyncScheduler
import com.artishorizon.tidefit.data.reminders.ReminderScheduler
import com.artishorizon.tidefit.data.reminders.ensureReminderChannel
import com.artishorizon.tidefit.domain.repository.ReminderSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TideFitApp : Application() {
    @Inject lateinit var foodSeeder: FoodSeeder
    @Inject lateinit var exerciseSeeder: ExerciseSeeder
    @Inject lateinit var reminderSettings: ReminderSettings
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var healthSync: HealthSyncScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureReminderChannel(this)
        // One-time, off the main thread; each is a no-op once its starter data exists.
        appScope.launch { foodSeeder.seedIfNeeded() }
        appScope.launch { exerciseSeeder.seedIfNeeded() }
        // Make sure every enabled reminder has a pending job (KEEP: don't reset one that is already queued).
        appScope.launch { reminderSettings.observe().first().filter { it.enabled }.forEach { reminderScheduler.apply(it, replace = false) } }
        healthSync.restore()
    }
}
