package com.artishorizon.tidefit.feature.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.lucide
import com.artishorizon.tidefit.data.StreakTracker
import com.artishorizon.tidefit.domain.calc.Streak
import com.artishorizon.tidefit.domain.calc.StreakCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

private val Flame = Color(0xFFFF8A3D)

@HiltViewModel
class StreakViewModel @Inject constructor(tracker: StreakTracker) : ViewModel() {
    val streak: StateFlow<Streak> = tracker.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Streak.EMPTY)
}

@Composable
fun StreakScreen(onBack: () -> Unit, vm: StreakViewModel = hiltViewModel()) {
    val streak by vm.streak.collectAsStateWithLifecycle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Scaffold(topBar = { FitTopBar("Streak", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(lucide("flame"), contentDescription = null, tint = if (streak.todayDone) Flame else muted, modifier = Modifier.size(72.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    streak.current.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold,
                    color = if (streak.todayDone) Flame else MaterialTheme.colorScheme.onSurface
                )
                Text("day streak", style = MaterialTheme.typography.titleMedium, color = muted)
            }
            Text(
                when {
                    streak.todayDone -> "You're done for today. Come back tomorrow."
                    streak.current > 0 -> "Log a meal or finish a workout today to keep your streak."
                    else -> "Log a meal or finish a workout to start a streak."
                },
                style = MaterialTheme.typography.bodyMedium, color = muted, textAlign = TextAlign.Center
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Stat("Best", "${streak.best}", Modifier.weight(1f))
                Stat("Days logged", "${streak.activeDays.size}", Modifier.weight(1f))
                Stat("Next goal", StreakCalc.nextMilestone(streak.current)?.toString() ?: "-", Modifier.weight(1f))
            }

            MonthCalendar(streak.activeDays)
            Text("A day counts when you log food or finish a workout.", style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    FitCard(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthCalendar(active: Set<LocalDate>) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val earliest = active.minOrNull()?.let { YearMonth.from(it) } ?: YearMonth.from(today)
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = month.minusMonths(1) }, enabled = month > earliest) {
                    Icon(lucide("chevron-left"), contentDescription = "Previous month")
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                IconButton({ month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) {
                    Icon(lucide("chevron-right"), contentDescription = "Next month")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                (1..7).forEach { d ->
                    Text(
                        java.time.DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val rows = (offset + month.lengthOfMonth() + 6) / 7
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val day = row * 7 + col - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                val done = date in active
                                Box(
                                    Modifier.fillMaxSize().clip(CircleShape).background(if (done) Flame else Color.Transparent)
                                        .semantics { contentDescription = "$date: ${if (done) "logged" else "not logged"}" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        day.toString(), style = MaterialTheme.typography.labelMedium,
                                        color = if (done) Color.White else if (date.isAfter(today)) muted.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
