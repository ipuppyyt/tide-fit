package com.fitos.app.domain.calc

import java.time.Duration
import java.time.ZonedDateTime

/** Decides whether a reminder is still useful: nothing is sent for something the user already did today. */
object ReminderRules {
    fun shouldNotify(
        kind: com.fitos.app.domain.model.ReminderKind,
        waterMlToday: Int,
        waterTargetMl: Int?,
        foodEntriesToday: Int,
        weighedInToday: Boolean
    ): Boolean = when (kind) {
        com.fitos.app.domain.model.ReminderKind.WATER -> if (waterTargetMl != null) waterMlToday < waterTargetMl else waterMlToday == 0
        com.fitos.app.domain.model.ReminderKind.MEALS -> foodEntriesToday == 0
        com.fitos.app.domain.model.ReminderKind.WEIGH_IN -> !weighedInToday
    }
}

object ReminderTime {
    /**
     * Time from [now] until the next occurrence of [minutesOfDay] (minutes after midnight, local time).
     * If that time is now or already passed today, the next occurrence is tomorrow. Uses calendar days, so DST
     * changes shift the delay rather than the wall-clock time.
     */
    fun delayUntilNext(now: ZonedDateTime, minutesOfDay: Int): Duration {
        require(minutesOfDay in 0..1439) { "minutesOfDay out of range: $minutesOfDay" }
        // Built from local date + time (not start-of-day + minutes) so a DST change doesn't move the wall-clock time.
        var next = now.toLocalDate().atTime(minutesOfDay / 60, minutesOfDay % 60).atZone(now.zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(minutesOfDay / 60, minutesOfDay % 60).atZone(now.zone)
        return Duration.between(now, next)
    }
}
