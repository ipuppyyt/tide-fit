package com.artishorizon.tidefit.feature.nutrition

import com.artishorizon.tidefit.core.designsystem.components.lucide
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import com.artishorizon.tidefit.core.designsystem.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.artishorizon.tidefit.core.designsystem.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Sizes
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.MacroIndicator
import com.artishorizon.tidefit.core.designsystem.components.ProgressRing
import com.artishorizon.tidefit.core.util.friendly
import com.artishorizon.tidefit.core.util.rounded
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.FoodEntry
import com.artishorizon.tidefit.domain.model.Meal
import com.artishorizon.tidefit.domain.model.Nutrition
import java.time.LocalDate

@Composable
fun NutritionScreen(
    contentPadding: PaddingValues,
    onAddFood: (LocalDate, Meal) -> Unit,
    onEditEntry: (LocalDate, FoodEntry) -> Unit,
    onOpenWater: () -> Unit,
    onSetTargets: () -> Unit,
    viewModel: NutritionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                com.artishorizon.tidefit.core.designsystem.components.PhotoCard(com.artishorizon.tidefit.R.drawable.photo_bowl, height = 170.dp) {
                    Column {
                        Text("Fuel", style = MaterialTheme.typography.bodyMedium)
                        Text("Nutrition", style = MaterialTheme.typography.headlineLarge)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(viewModel::previousDay) { Icon(lucide("chevron-left"), contentDescription = "Previous day") }
                    Text(
                        state.day.friendly(state.today),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = viewModel::goToToday).padding(Spacing.sm)
                    )
                    IconButton(viewModel::nextDay) { Icon(lucide("chevron-right"), contentDescription = "Next day") }
                }
            }
        }
        item(key = "summary") { DaySummary(state.totals, state.targets, state.limits, onSetTargets) }
        item(key = "water") { WaterCard(state.waterMl, state.targets?.waterMl, onOpenWater) }
        Meal.entries.forEach { meal ->
            val entries = state.entriesByMeal[meal].orEmpty()
            item(key = "meal-${meal.name}") {
                MealHeader(meal, entries.sumOf { it.total.calories }, hasEntries = entries.isNotEmpty()) { onAddFood(state.day, meal) }
            }
            items(entries, key = { "entry-${it.id}" }) { entry ->
                EntryRow(entry) { onEditEntry(state.day, entry) }
            }
        }
    }
}

@Composable
private fun DaySummary(totals: Nutrition, targets: DailyTargets?, limits: com.artishorizon.tidefit.domain.calc.CalorieLimits?, onSetTargets: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                val target = targets?.caloriesKcal
                ProgressRing(progress = if (target != null && target > 0) (totals.calories / target).toFloat() else 0f, size = 112.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (target != null) {
                            val eaten = totals.calories.toInt()
                            val (num, word) = when (limits?.kind) {
                                com.artishorizon.tidefit.domain.calc.LimitKind.FLOOR -> if (eaten < limits.low) (limits.low - eaten) to "to min" else 0 to "min met"
                                com.artishorizon.tidefit.domain.calc.LimitKind.CEILING -> if (eaten <= limits.high) (limits.high - eaten) to "left" else (eaten - limits.high) to "over max"
                                else -> kotlin.math.abs(target - eaten) to (if (target >= eaten) "left" else "over")
                            }
                            Text(num.toString(), style = MaterialTheme.typography.titleLarge)
                            Text(word, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(totals.calories.rounded(), style = MaterialTheme.typography.titleLarge)
                            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("${totals.calories.rounded()} kcal eaten", style = MaterialTheme.typography.titleMedium)
                    Text(
                        limits?.ruleText ?: if (target != null) "Target $target kcal" else "No calorie target set",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (limits != null) {
                        val st = limits.status(totals.calories.toInt())
                        val bad = st == com.artishorizon.tidefit.domain.calc.LimitStatus.OVER && limits.kind != com.artishorizon.tidefit.domain.calc.LimitKind.FLOOR ||
                            st == com.artishorizon.tidefit.domain.calc.LimitStatus.OVER
                        Text(
                            limits.headline(totals.calories.toInt()), style = MaterialTheme.typography.labelLarge,
                            color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    if (targets == null) TextButton(onSetTargets) { Text("Set targets") }
                }
            }
            MacroIndicator("Protein", macroText(totals.protein, targets?.proteinG), progress(totals.protein, targets?.proteinG), colors.protein)
            MacroIndicator("Carbohydrates", macroText(totals.carbs, targets?.carbohydratesG), progress(totals.carbs, targets?.carbohydratesG), colors.carbs)
            MacroIndicator("Fat", macroText(totals.fat, targets?.fatG), progress(totals.fat, targets?.fatG), colors.fat)
            MacroIndicator("Fibre", macroText(totals.fibre ?: 0.0, targets?.fibreG), progress(totals.fibre ?: 0.0, targets?.fibreG), colors.fibre)
        }
    }
}

private fun macroText(value: Double, target: Int?) = if (target != null) "${value.rounded()} / $target g" else "${value.rounded()} g"
private fun progress(value: Double, target: Int?) = if (target != null && target > 0) (value / target).toFloat() else 0f

@Composable
private fun WaterCard(waterMl: Int, targetMl: Int?, onClick: () -> Unit) {
    FitCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(lucide("droplet"), contentDescription = null, tint = FitTheme.colors.water)
            Column(Modifier.weight(1f)) {
                Text("Water", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (targetMl != null) "$waterMl / $targetMl ml" else "$waterMl ml",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(lucide("chevron-right"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MealHeader(meal: Meal, kcal: Double, hasEntries: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(meal.label, style = MaterialTheme.typography.titleMedium)
            if (hasEntries) Text("${kcal.rounded()} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onAdd) { Icon(lucide("plus"), contentDescription = "Add food to ${meal.label}", tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    FitCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(entry.quantityLabel, "P ${entry.total.protein.rounded()} · C ${entry.total.carbs.rounded()} · F ${entry.total.fat.rounded()}").joinToString("  |  "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(Spacing.sm))
            Text("${entry.total.calories.rounded()} kcal", style = MaterialTheme.typography.titleSmall)
        }
    }
}
