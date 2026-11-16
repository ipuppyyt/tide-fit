package com.artishorizon.tidefit.data.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.artishorizon.tidefit.MainActivity
import com.artishorizon.tidefit.R
import com.artishorizon.tidefit.data.StreakTracker
import com.artishorizon.tidefit.domain.calc.StreakCalc
import com.artishorizon.tidefit.domain.model.ReminderKind
import com.artishorizon.tidefit.domain.repository.ReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces streak milestones (3, 7, 14, 30 ... days) the moment the day that reaches one is logged. Follows the Streak
 * reminder switch, so nothing is sent unless the user opted in.
 */
@Singleton
class StreakMilestones @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tracker: StreakTracker,
    private val settings: ReminderSettings
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            tracker.observe().distinctUntilChanged().collect { streak ->
                var last = settings.lastMilestone()
                // A broken streak starts over, so earlier milestones can be celebrated again.
                if (streak.current < last) { last = 0; settings.setLastMilestone(0) }
                val hit = StreakCalc.milestoneHit(streak, last) ?: return@collect
                settings.setLastMilestone(hit)
                if (settings.get(ReminderKind.STREAK).enabled) notify(hit)
            }
        }
    }

    private fun notify(days: Int) {
        if (!hasNotificationPermission(context)) return
        ensureReminderChannel(context)
        val open = PendingIntent.getActivity(context, 77, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$days-day streak!")
            .setContentText(if (days < 14) "Nice start. Keep it going tomorrow." else "You've logged $days days in a row. Keep it up.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(2000, n)
    }
}
