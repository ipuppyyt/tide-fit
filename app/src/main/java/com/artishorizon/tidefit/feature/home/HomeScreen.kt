package com.artishorizon.tidefit.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.MacroIndicator
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.ProgressRing
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.designsystem.components.StatCard
import com.artishorizon.tidefit.core.util.oneDecimal
import com.artishorizon.tidefit.core.util.rounded
import com.artishorizon.tidefit.core.util.totals
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.model.BeverageKind
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.Nutrition
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.repository.AppSettings
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.WaterRepository
import com.artishorizon.tidefit.feature.profile.TargetsCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val targets: DailyTargets? = null,
    val totals: Nutrition = Nutrition.ZERO,
    val entryCount: Int = 0,
    val waterMl: Int = 0,
    val glassMl: Int = 250
) {
    /** Daily completion counts only goals that exist: a missing target is not a failed one. */
    val goals: List<Pair<String, Boolean>>
        get() = buildList {
            targets?.let {
                add("Calories logged" to (entryCount > 0))
                add("Protein target" to (totals.protein >= it.proteinG))
                add("Water target" to (waterMl >= it.waterMl))
            }
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    profiles: ProfileRepository,
    log: FoodLogRepository,
    private val water: WaterRepository,
    settings: AppSettings,
    private val clock: Clock
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<HomeUiState> = combine(
        profiles.observeProfile(), profiles.observeCurrentTargets(), log.observeDay(today), water.observeDay(today), settings.defaultGlassMl
    ) { p, t, entries, drinks, glass ->
        HomeUiState(
            loading = false, profile = p, targets = t, totals = entries.totals(), entryCount = entries.size,
            waterMl = drinks.filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl }, glassMl = glass
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun addGlass() {
        viewModelScope.launch { runCatching { water.add(today, state.value.glassMl, BeverageKind.WATER, null) } }
    }
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onAddFood: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenNutrition: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }
    val profile = state.profile
    val targets = state.targets
    val c = FitTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(if (profile != null) "Hello, ${profile.name}" else "Today", style = MaterialTheme.typography.headlineLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton("Add food", onAddFood, Modifier.weight(1f))
            SecondaryButton("Add water (${state.glassMl} ml)", viewModel::addGlass, Modifier.weight(1f))
        }

        FitCard(onClick = onOpenNutrition) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionHeader("Today's nutrition")
                if (targets == null) {
                    Text(
                        "${state.totals.calories.rounded()} kcal logged. Set daily targets in Nutrition to see progress.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val remaining = targets.caloriesKcal - state.totals.calories.toInt()
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        ProgressRing((state.totals.calories / targets.caloriesKcal).toFloat(), size = 104.dp) {
                            Text(state.totals.calories.rounded(), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("of ${targets.caloriesKcal} kcal", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (remaining >= 0) "$remaining kcal left" else "${-remaining} kcal over",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    MacroIndicator("Protein", "${state.totals.protein.rounded()} / ${targets.proteinG} g", (state.totals.protein / targets.proteinG.coerceAtLeast(1)).toFloat(), c.protein)
                    MacroIndicator("Carbs", "${state.totals.carbs.rounded()} / ${targets.carbohydratesG} g", (state.totals.carbs / targets.carbohydratesG.coerceAtLeast(1)).toFloat(), c.carbs)
                    MacroIndicator("Fat", "${state.totals.fat.rounded()} / ${targets.fatG} g", (state.totals.fat / targets.fatG.coerceAtLeast(1)).toFloat(), c.fat)
                    MacroIndicator("Fibre", "${(state.totals.fibre ?: 0.0).oneDecimal()} / ${targets.fibreG} g", ((state.totals.fibre ?: 0.0) / targets.fibreG.coerceAtLeast(1)).toFloat(), c.fibre)
                    if (state.entryCount > 0 && state.totals.fibre == null) {
                        Text("Fibre is unknown for some logged foods.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        FitCard(onClick = onOpenWater) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader("Hydration")
                Text(
                    "${state.waterMl} ml" + (targets?.let { " / ${it.waterMl} ml" } ?: ""),
                    style = MaterialTheme.typography.titleLarge
                )
                targets?.let { com.artishorizon.tidefit.core.designsystem.components.FitLinearProgress((state.waterMl.toFloat() / it.waterMl), color = c.water) }
            }
        }

        if (state.goals.isNotEmpty()) {
            val done = state.goals.count { it.second }
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SectionHeader("Daily completion")
                    Text("$done of ${state.goals.size} done", style = MaterialTheme.typography.titleMedium)
                    state.goals.forEach { (label, ok) ->
                        Text(
                            (if (ok) "✓ " else "○ ") + label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("Workouts, sleep and supplements will join this list in later phases.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (profile != null) {
            val u = profile.weightUnit
            fun w(kg: Double) = "%.1f %s".format(Locale.US, Units.fromKg(kg, u), u.label)
            val change = profile.currentWeightKg - profile.initialWeightKg
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatCard("Current weight", w(profile.currentWeightKg), Modifier.weight(1f), caption = "Target ${w(profile.targetWeightKg)}")
                StatCard(
                    "Since start",
                    "%+.1f %s".format(Locale.US, Units.fromKg(change, u), u.label),
                    Modifier.weight(1f),
                    caption = "From ${w(profile.initialWeightKg)}"
                )
            }
        }

        TargetsCard(targets)
    }
}
