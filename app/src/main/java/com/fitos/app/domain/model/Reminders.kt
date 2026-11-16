package com.fitos.app.domain.model

/** A daily reminder the user can switch on. All are off by default; nothing is scheduled until the user opts in. */
enum class ReminderKind(
    val label: String,
    val description: String,
    val defaultMinutes: Int,
    val notificationTitle: String,
    val notificationText: String
) {
    WATER("Water", "Nudge to drink if you're behind on your water target", 15 * 60, "Time for some water", "You're behind on your water target today."),
    MEALS("Log meals", "Reminder to log food if nothing is logged yet today", 20 * 60, "Log today's meals", "Nothing logged yet today. It only takes a minute."),
    WEIGH_IN("Weigh-in", "Reminder to weigh in if you haven't today", 8 * 60, "Morning weigh-in", "You haven't logged your weight today.")
}

/** [minutes] is the time of day as minutes after midnight (0..1439). */
data class ReminderSetting(val kind: ReminderKind, val enabled: Boolean, val minutes: Int) {
    val hour: Int get() = minutes / 60
    val minute: Int get() = minutes % 60
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}
