package com.artishorizon.tidefit.feature.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.feature.workouts.friendlyDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** A labelled button that opens a date picker; future dates are not selectable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: LocalDate, today: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SecondaryButton(friendlyDate(date, today), { open = true }, modifier)
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

data class ChartPoint(val date: LocalDate, val value: Double)

data class ChartSeries(val points: List<ChartPoint>, val color: Color, val dotsOnly: Boolean = false)

/**
 * Minimal date-scaled line chart. Needs at least two distinct days to draw a line; a single reading is a dot.
 * The Y range is fitted to the data so small changes remain visible.
 */
@Composable
fun LineChart(series: List<ChartSeries>, modifier: Modifier = Modifier, unitLabel: String = "") {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) return
    val minDate = all.minOf { it.date }
    val maxDate = all.maxOf { it.date }
    val spanDays = ChronoUnit.DAYS.between(minDate, maxDate).coerceAtLeast(1).toFloat()
    var lo = all.minOf { it.value }
    var hi = all.maxOf { it.value }
    if (hi - lo < 1.0) { val mid = (hi + lo) / 2; lo = mid - 0.5; hi = mid + 0.5 }
    val pad = (hi - lo) * 0.1
    lo -= pad; hi += pad
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val summary = "Line chart from $minDate to $maxDate, ${all.size} readings, lowest ${"%.1f".format(java.util.Locale.US, all.minOf { it.value })}, " +
            "highest ${"%.1f".format(java.util.Locale.US, all.maxOf { it.value })} $unitLabel"
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = summary }) {
            fun x(d: LocalDate) = if (maxDate == minDate) size.width / 2 else size.width * ChronoUnit.DAYS.between(minDate, d) / spanDays
            fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
            for (i in 0..2) { val gy = size.height * i / 2f; drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()) }
            series.forEach { s ->
                if (!s.dotsOnly && s.points.size > 1) {
                    val path = Path()
                    s.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.date), y(p.value)) else path.lineTo(x(p.date), y(p.value)) }
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(x(s.points.last().date), size.height)
                        lineTo(x(s.points.first().date), size.height)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), s.color.copy(alpha = 0f))))
                    drawPath(path, s.color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (s.dotsOnly || s.points.size == 1) {
                    s.points.forEach { p -> drawCircle(s.color.copy(alpha = 0.6f), 3.dp.toPx(), Offset(x(p.date), y(p.value))) }
                }
            }
        }
        val fmt = { v: Double -> "%.1f".format(java.util.Locale.US, v) }
        Text(
            "${minDate} → ${maxDate}  ·  low ${fmt(all.minOf { it.value })}, high ${fmt(all.maxOf { it.value })} $unitLabel".trim(),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
