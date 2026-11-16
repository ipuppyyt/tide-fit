package com.artishorizon.tidefit.feature.home

import com.artishorizon.tidefit.core.designsystem.components.lucide
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitChip
import com.artishorizon.tidefit.core.designsystem.components.FitLinearProgress
import com.artishorizon.tidefit.core.designsystem.components.HeroCard
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.MacroIndicator
import com.artishorizon.tidefit.core.designsystem.components.ProgressRing
import com.artishorizon.tidefit.core.designsystem.components.QuickTile
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
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
import java.time.format.DateTimeFormatter
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
    onOpenWorkouts: () -> Unit,
    onOpenWeight: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }
    val profile = state.profile
    val targets = state.targets
    val c = FitTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = Spacing.md, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Header: date, greeting and an initial avatar.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge, color = muted
                )
                Text(if (profile != null) "Hello, ${profile.name}" else "Today", style = MaterialTheme.typography.headlineLarge)
            }
            if (profile != null && profile.name.isNotBlank()) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = "Profile: ${profile.name}" },
                    contentAlignment = Alignment.Center
                ) { Text(profile.name.first().uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }

        // Hero: calories left today.
        HeroCard(onClick = onOpenNutrition) {
            if (targets == null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Today's nutrition", style = MaterialTheme.typography.labelLarge)
                    Text("${state.totals.calories.rounded()} kcal", style = MaterialTheme.typography.displayMedium)
                    Text("Set daily targets in Nutrition to see how much is left.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val remaining = targets.caloriesKcal - state.totals.calories.toInt()
                val ink = c.onHero
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Text(if (remaining >= 0) "Calories left" else "Calories over", style = MaterialTheme.typography.labelLarge)
                            Text(kotlin.math.abs(remaining).toString(), style = MaterialTheme.typography.displayLarge)
                            Text("${state.totals.calories.rounded()} of ${targets.caloriesKcal} kcal eaten", style = MaterialTheme.typography.bodyMedium)
                        }
                        ProgressRing(
                            (state.totals.calories / targets.caloriesKcal).toFloat(), size = 96.dp, stroke = 12.dp,
                            color = ink, track = ink.copy(alpha = 0.16f)
                        ) { Icon(lucide("utensils"), contentDescription = null, tint = ink, modifier = Modifier.size(28.dp)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        HeroMacro("Protein", state.totals.protein.rounded(), targets.proteinG, Modifier.weight(1f))
                        HeroMacro("Carbs", state.totals.carbs.rounded(), targets.carbohydratesG, Modifier.weight(1f))
                        HeroMacro("Fat", state.totals.fat.rounded(), targets.fatG, Modifier.weight(1f))
                    }
                }
            }
        }

        // Quick actions.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QuickTile(lucide("utensils"), "Add food", onAddFood, Modifier.weight(1f))
            QuickTile(lucide("droplet"), "Water", onOpenWater, Modifier.weight(1f))
            QuickTile(lucide("dumbbell"), "Workout", onOpenWorkouts, Modifier.weight(1f))
            QuickTile(lucide("scale"), "Weigh in", onOpenWeight, Modifier.weight(1f))
        }

        // Hydration and weight side by side.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FitCard(Modifier.weight(1f).fillMaxHeight(), onClick = onOpenWater) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Hydration", style = MaterialTheme.typography.labelLarge, color = muted)
                    Text("${state.waterMl} ml", style = MaterialTheme.typography.titleLarge)
                    targets?.let {
                        FitLinearProgress(state.waterMl.toFloat() / it.waterMl, color = c.water)
                        Text("of ${it.waterMl} ml", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    FitChip("+${state.glassMl} ml", selected = false, onClick = viewModel::addGlass)
                }
            }
            if (profile != null) {
                val u = profile.weightUnit
                fun w(kg: Double) = "%.1f %s".format(Locale.US, Units.fromKg(kg, u), u.label)
                val change = profile.currentWeightKg - profile.initialWeightKg
                FitCard(Modifier.weight(1f).fillMaxHeight(), onClick = onOpenWeight) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Weight", style = MaterialTheme.typography.labelLarge, color = muted)
                        Text(w(profile.currentWeightKg), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "%+.1f %s since start".format(Locale.US, Units.fromKg(change, u), u.label),
                            style = MaterialTheme.typography.bodySmall, color = muted
                        )
                        Text("Target ${w(profile.targetWeightKg)}", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        }

        if (targets != null) {
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader("Macros")
                    MacroIndicator("Protein", "${state.totals.protein.rounded()} / ${targets.proteinG} g", (state.totals.protein / targets.proteinG.coerceAtLeast(1)).toFloat(), c.protein)
                    MacroIndicator("Carbs", "${state.totals.carbs.rounded()} / ${targets.carbohydratesG} g", (state.totals.carbs / targets.carbohydratesG.coerceAtLeast(1)).toFloat(), c.carbs)
                    MacroIndicator("Fat", "${state.totals.fat.rounded()} / ${targets.fatG} g", (state.totals.fat / targets.fatG.coerceAtLeast(1)).toFloat(), c.fat)
                    MacroIndicator("Fibre", "${(state.totals.fibre ?: 0.0).oneDecimal()} / ${targets.fibreG} g", ((state.totals.fibre ?: 0.0) / targets.fibreG.coerceAtLeast(1)).toFloat(), c.fibre)
                    if (state.entryCount > 0 && state.totals.fibre == null) {
                        Text("Fibre is unknown for some logged foods.", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        }

        if (state.goals.isNotEmpty()) {
            val done = state.goals.count { it.second }
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader("Daily completion") { Text("$done of ${state.goals.size}", style = MaterialTheme.typography.labelLarge, color = muted) }
                    state.goals.forEach { (label, ok) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(if (ok) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(1.5.dp, if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { if (ok) Icon(lucide("check"), contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (ok) MaterialTheme.colorScheme.onSurface else muted)
                        }
                    }
                    Text("Workouts, sleep and supplements will join this list in later phases.", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
        }

        TargetsCard(targets)
    }
}

@Composable
private fun HeroMacro(label: String, value: String, target: Int, modifier: Modifier = Modifier) {
    val ink = FitTheme.colors.onHero
    Column(
        modifier.clip(Radii.md).background(ink.copy(alpha = 0.10f)).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text("$value / $target g", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}
