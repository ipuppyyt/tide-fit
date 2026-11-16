package com.fitos.app

import android.app.Application
import com.fitos.app.data.ExerciseSeeder
import com.fitos.app.data.FoodSeeder
import com.fitos.app.data.reminders.ReminderScheduler
import com.fitos.app.data.reminders.ensureReminderChannel
import com.fitos.app.domain.repository.ReminderSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FitOsApp : Application() {
    @Inject lateinit var foodSeeder: FoodSeeder
    @Inject lateinit var exerciseSeeder: ExerciseSeeder
    @Inject lateinit var reminderSettings: ReminderSettings
    @Inject lateinit var reminderScheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureReminderChannel(this)
        // One-time, off the main thread; each is a no-op once its starter data exists.
        appScope.launch { foodSeeder.seedIfNeeded() }
        appScope.launch { exerciseSeeder.seedIfNeeded() }
        // Make sure every enabled reminder has a pending job (KEEP: don't reset one that is already queued).
        appScope.launch { reminderSettings.observe().first().filter { it.enabled }.forEach { reminderScheduler.apply(it, replace = false) } }
    }
}
