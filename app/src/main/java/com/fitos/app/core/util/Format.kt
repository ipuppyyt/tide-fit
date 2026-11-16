package com.fitos.app.core.util

import com.fitos.app.domain.model.FoodEntry
import com.fitos.app.domain.model.Nutrition
import com.fitos.app.domain.calc.PortionCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Parses "72.5" or "72,5"; rejects blanks, negatives, NaN and infinity. */
fun String.parseDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

fun Double.rounded(): String = roundToInt().toString()
fun Double?.roundedOrDash(): String = this?.roundToInt()?.toString() ?: "–"
fun Double.oneDecimal(): String = "%.1f".format(Locale.US, this)

fun List<FoodEntry>.totals(): Nutrition = PortionCalculator.sum(map { it.total })

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

fun LocalDate.friendly(today: LocalDate): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> format(dayFormatter)
}
