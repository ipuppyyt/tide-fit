package com.fitos.app.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fitos.app.MainActivity
import com.fitos.app.R
import com.fitos.app.domain.calc.ReminderRules
import com.fitos.app.domain.calc.ReminderTime
import com.fitos.app.domain.model.BeverageKind
import com.fitos.app.domain.model.ReminderKind
import com.fitos.app.domain.model.ReminderSetting
import com.fitos.app.domain.repository.FoodLogRepository
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.domain.repository.ReminderSettings
import com.fitos.app.domain.repository.WaterRepository
import com.fitos.app.domain.repository.WeightRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val REMINDER_CHANNEL_ID = "reminders"

fun ensureReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = context.getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
        nm.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Optional daily reminders you switch on in FITOS" }
        )
    }
}

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Schedules each enabled reminder as a one-time job for its next occurrence; the job re-schedules itself after it runs.
 * (Periodic work can't promise a time of day, so a self-renewing chain is used.) WorkManager keeps the jobs across reboots.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) {
    private val work get() = WorkManager.getInstance(context)

    /** [replace] true when the user just changed the time; false on app start so a pending job isn't reset. */
    fun apply(setting: ReminderSetting, replace: Boolean = true) {
        if (!setting.enabled) { work.cancelUniqueWork(uniqueName(setting.kind)); return }
        val delay = ReminderTime.delayUntilNext(ZonedDateTime.now(clock), setting.minutes)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_KIND, setting.kind.name).build())
            .build()
        work.enqueueUniqueWork(uniqueName(setting.kind), if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val KEY_KIND = "kind"
        fun uniqueName(kind: ReminderKind) = "reminder_${kind.name}"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun settings(): ReminderSettings
    fun scheduler(): ReminderScheduler
    fun foodLog(): FoodLogRepository
    fun water(): WaterRepository
    fun weights(): WeightRepository
    fun profiles(): ProfileRepository
    fun clock(): Clock
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(ReminderScheduler.KEY_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() } ?: return Result.success()
        val ep = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)
        val setting = ep.settings().get(kind)
        if (!setting.enabled) return Result.success() // switched off while the job was pending

        try {
            val today = java.time.LocalDate.now(ep.clock())
            val waterMl = ep.water().observeDay(today).first().filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl }
            val foodCount = ep.foodLog().observeDay(today).first().size
            val weighedIn = ep.weights().observeAll().first().any { it.date == today }
            val target = ep.profiles().observeCurrentTargets().first()?.waterMl
            if (ReminderRules.shouldNotify(kind, waterMl, target, foodCount, weighedIn)) notify(kind)
        } finally {
            ep.scheduler().apply(setting, replace = true) // always queue tomorrow's, even if today's check failed
        }
        return Result.success()
    }

    private fun notify(kind: ReminderKind) {
        if (!hasNotificationPermission(applicationContext)) return
        ensureReminderChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext, kind.ordinal, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(kind.notificationTitle)
            .setContentText(kind.notificationText)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(applicationContext).notify(1000 + kind.ordinal, n)
    }
}
