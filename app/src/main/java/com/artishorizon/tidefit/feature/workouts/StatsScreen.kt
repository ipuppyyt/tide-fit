package com.artishorizon.tidefit.feature.workouts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.designsystem.components.StatCard
import com.artishorizon.tidefit.domain.calc.TrainingCalc
import com.artishorizon.tidefit.domain.model.MuscleGroup
import com.artishorizon.tidefit.domain.model.WeightUnit
import com.artishorizon.tidefit.domain.repository.ExerciseRepository
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class ExerciseBest(val name: String, val maxWeightKg: Double?, val oneRepMaxKg: Double?, val maxReps: Int?, val sessions: Int)

data class StatsUiState(
    val loading: Boolean = true,
    val unit: WeightUnit = WeightUnit.KG,
    val totalWorkouts: Int = 0,
    val weeks: List<TrainingCalc.WeekStat> = emptyList(),
    val muscleSets: List<Pair<MuscleGroup, Int>> = emptyList(),
    val bests: List<ExerciseBest> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    workouts: WorkoutRepository,
    exercises: ExerciseRepository,
    profiles: ProfileRepository,
    private val clock: Clock
) : ViewModel() {
    val state: StateFlow<StatsUiState> = combine(
        workouts.observeHistory(), workouts.observeFinishedSets(), exercises.observeAll(),
        profiles.observeProfile().map { it?.weightUnit ?: WeightUnit.KG }
    ) { history, sets, library, unit ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock)
        val dateOf = history.associate { it.id to it.startedAt.atZone(zone).toLocalDate() }
        val bySession = sets.groupBy { it.sessionId }
        val weeks = TrainingCalc.weeklyStats(bySession.mapNotNull { (id, s) -> dateOf[id]?.let { it to s } }, today, WEEKS)

        val muscleOf = library.associate { it.id to it.primary }
        val recent = sets.filter { s -> dateOf[s.sessionId]?.let { !it.isBefore(today.minusDays(27)) } == true }
        val muscleSets = TrainingCalc.setsPerMuscle(recent) { muscleOf[it] }.toList().sortedByDescending { it.second }

        val bests = sets.groupBy { it.exerciseId }.map { (_, list) ->
            val r = TrainingCalc.records(list)
            ExerciseBest(list.first().exerciseName, r.maxWeightKg, r.bestOneRepMaxKg, r.maxReps, list.map { it.sessionId }.distinct().size)
        }.sortedBy { it.name.lowercase() }

        StatsUiState(false, unit, history.size, weeks, muscleSets, bests)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private companion object { const val WEEKS = 8 }
}

@Composable
fun StatsScreen(onBack: () -> Unit, vm: StatsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { FitTopBar("Training stats", onBack) }) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            if (state.totalWorkouts == 0) {
                com.artishorizon.tidefit.core.designsystem.components.EmptyState(com.artishorizon.tidefit.core.designsystem.components.lucide("chart-no-axes-combined"), "No stats yet", "Finish a workout and your volume, sets and personal records appear here.")
                return@Column
            }
            val thisWeek = state.weeks.lastOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatCard("Workouts", state.totalWorkouts.toString(), Modifier.weight(1f), caption = "all time")
                StatCard("This week", (thisWeek?.sessions ?: 0).toString(), Modifier.weight(1f), caption = formatVolume(thisWeek?.volumeKg ?: 0.0, state.unit))
            }

            SectionHeader("Weekly volume")
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    WeekBars(state.weeks)
                    val active = state.weeks.count { it.sessions > 0 }
                    Text(
                        "$active of ${state.weeks.size} weeks with training. Empty weeks show as a dot.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeader("Sets per muscle (last 4 weeks)")
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (state.muscleSets.isEmpty()) Text("No sets in the last 4 weeks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val max = (state.muscleSets.maxOfOrNull { it.second } ?: 1).toFloat()
                    state.muscleSets.forEach { (m, n) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                            Text(plural(n, "set"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.artishorizon.tidefit.core.designsystem.components.FitLinearProgress(n / max)
                    }
                }
            }

            SectionHeader("Personal bests")
            Text(
                "Estimated 1RM uses the Epley formula and only for sets of 12 reps or fewer. It is an estimate, not a tested max.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.bests.forEach { b ->
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall)
                        val parts = buildList {
                            b.maxWeightKg?.let { add("Heaviest ${formatWeight(it, state.unit)}") }
                            b.oneRepMaxKg?.let { add("Est. 1RM ${formatWeight(it, state.unit)}") }
                            if (b.maxWeightKg == null) b.maxReps?.let { add("Most reps $it") }
                            add(plural(b.sessions, "workout"))
                        }
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(weeks: List<TrainingCalc.WeekStat>) {
    val bar = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.outline
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (weeks.maxOfOrNull { it.volumeKg } ?: 0.0).coerceAtLeast(1.0).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / weeks.size.coerceAtLeast(1)
            val w = slot * 0.5f
            weeks.forEachIndexed { i, wk ->
                val x = i * slot + (slot - w) / 2
                if (wk.sessions > 0) {
                    val h = (size.height * (wk.volumeKg.toFloat() / max)).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
                } else {
                    drawCircle(dot, 3.dp.toPx(), Offset(x + w / 2, size.height - 3.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weeks.forEach { wk ->
                Text(
                    "${wk.weekStart.dayOfMonth} ${wk.weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = label, textAlign = TextAlign.Center, maxLines = 1
                )
            }
        }
    }
}
