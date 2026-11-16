package com.artishorizon.tidefit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Month calendar in TIDE's style. Dates after [maxDate] are shown dimmed and cannot be chosen. */
@Composable
fun CalendarPicker(selected: LocalDate, maxDate: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
            )
            IconButton({ month = month.plusMonths(1) }, enabled = month.isBefore(YearMonth.from(maxDate))) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
        }
        val first = DayOfWeek.MONDAY
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                Text(
                    first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
        }
        val offset = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val rows = (offset + month.lengthOfMonth() + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val enabled = !date.isAfter(maxDate)
                            val isSel = date == selected
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .pressable(enabled, Role.Button) { onSelect(date) }
                                    .semantics { contentDescription = date.toString() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day", style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isSel -> MaterialTheme.colorScheme.onPrimary
                                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Two big stepper columns for hours and minutes (24 h). */
@Composable
fun TimeStepper(hour: Int, minute: Int, onChange: (hour: Int, minute: Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        StepColumn("hour", hour, 24, 1) { onChange(it, minute) }
        Text(":", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.displayLarge)
        StepColumn("minute", minute, 60, 5) { onChange(hour, it) }
    }
}

@Composable
private fun StepColumn(label: String, value: Int, range: Int, step: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton({ onChange((value + step) % range) }) { Icon(Icons.Filled.KeyboardArrowUp, "Increase $label") }
        Box(
            Modifier.size(width = 92.dp, height = 84.dp).clip(com.artishorizon.tidefit.core.designsystem.Radii.lg)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) { Text("%02d".format(value), style = MaterialTheme.typography.displayMedium) }
        IconButton({ onChange((value - step + range) % range) }) { Icon(Icons.Filled.KeyboardArrowDown, "Decrease $label") }
    }
}
