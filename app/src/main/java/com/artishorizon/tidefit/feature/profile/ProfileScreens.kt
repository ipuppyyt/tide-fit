package com.artishorizon.tidefit.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.components.IconBadge
import com.artishorizon.tidefit.core.designsystem.components.PhotoCard
import com.artishorizon.tidefit.core.designsystem.components.pressable
import com.artishorizon.tidefit.core.designsystem.components.lucide
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.artishorizon.tidefit.core.designsystem.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.artishorizon.tidefit.core.designsystem.components.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.ChipGroup
import com.artishorizon.tidefit.core.designsystem.components.EstimateBadge
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.ThemeMode
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.model.ValueSource
import com.artishorizon.tidefit.feature.profile.form.BasicsSection
import com.artishorizon.tidefit.feature.profile.form.BodySection
import com.artishorizon.tidefit.feature.profile.form.DietSection
import com.artishorizon.tidefit.feature.profile.form.EquipmentSection
import com.artishorizon.tidefit.feature.profile.form.GoalSection
import com.artishorizon.tidefit.feature.profile.form.TrainingSection
import java.util.Locale

@Composable
fun ProfileScreen(onBack: () -> Unit, onEditProfile: () -> Unit, onOpenData: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }

    Column(Modifier.fillMaxSize()) {
        com.artishorizon.tidefit.core.designsystem.components.FitTopBar("Profile", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            state.profile?.let { p ->
                ProfileHero(p, onEditProfile)
                JourneyCard(p)
                TargetsCard(state.targets)
            }

            FitCard(onClick = onOpenData) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    IconBadge(lucide("bell"), MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Data, reminders & backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reminders, Health Connect, backup and restore.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(lucide("chevron-right"), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        IconBadge(lucide("moon"), FitTheme.colors.water)
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    }
                    ChipGroup(
                        ThemeMode.entries.map { it.label },
                        setOf(state.themeMode.label),
                        { label -> viewModel.setTheme(ThemeMode.entries.first { it.label == label }) }
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Dynamic colour", style = MaterialTheme.typography.titleSmall)
                            Text("Use your wallpaper's colours (Android 12+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(state.dynamicColor, viewModel::setDynamicColor)
                    }
                }
            }

            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    IconBadge(lucide("heart-pulse"), FitTheme.colors.protein)
                    Column(Modifier.weight(1f)) {
                        Text("Private by design", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Stored on this device only. No account, no telemetry, no uploads, no internet permission.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun ProfileHero(p: UserProfile, onEdit: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    PhotoCard(com.artishorizon.tidefit.R.drawable.photo_gym, height = 260.dp) {
        Box(Modifier.fillMaxSize().padding(20.dp)) {
            Box(
                Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                    .pressable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(lucide("sparkles"), null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(68.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text(p.name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
                Text(p.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(CircleShape).background(accent).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(p.goal.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text(
                        "${p.age} yrs · %.0f cm · ${p.trainingDaysPerWeek}d/wk".format(Locale.US, p.heightCm),
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(p: UserProfile) {
    val u = p.weightUnit
    fun w(kg: Double) = "%.1f".format(Locale.US, Units.fromKg(kg, u))
    val total = p.initialWeightKg - p.targetWeightKg
    val done = p.initialWeightKg - p.currentWeightKg
    val progress = if (kotlin.math.abs(total) < 0.05) 1f else (done / total).toFloat().coerceIn(0f, 1f)
    val remaining = kotlin.math.abs(p.currentWeightKg - p.targetWeightKg)
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IconBadge(lucide("target"), MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Your journey", style = MaterialTheme.typography.titleMedium)
                    Text("${(progress * 100).toInt()}% of the way · ${"%.1f".format(Locale.US, Units.fromKg(remaining, u))} ${u.label} to go",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(progress).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                JourneyStat("Start", w(p.initialWeightKg), u.label)
                JourneyStat("Now", w(p.currentWeightKg), u.label, highlight = true)
                JourneyStat("Goal", w(p.targetWeightKg), u.label)
            }
        }
    }
}

@Composable
private fun JourneyStat(label: String, value: String, unit: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text("$label · $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TargetsCard(targets: DailyTargets?) {
    val c = FitTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Daily targets") {
            if (targets?.source == ValueSource.FORMULA_ESTIMATE) EstimateBadge()
        }
        if (targets == null) {
            FitCard {
                Text(
                    "No targets yet. Open Nutrition to calculate an estimate from your profile.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val tiles = listOf(
                Triple("flame", MaterialTheme.colorScheme.primary, "${targets.caloriesKcal}" to "kcal · calories"),
                Triple("dumbbell", c.protein, "${targets.proteinG}" to "g · protein"),
                Triple("zap", c.carbs, "${targets.carbohydratesG}" to "g · carbs"),
                Triple("utensils", c.fat, "${targets.fatG}" to "g · fat"),
                Triple("sparkles", MaterialTheme.colorScheme.tertiary, "${targets.fibreG}" to "g · fibre"),
                Triple("droplet", c.water, "${targets.waterMl}" to "ml · water")
            )
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { (icon, tint, vl) ->
                        Column(
                            Modifier.weight(1f).clip(Radii.lg).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconBadge(lucide(icon), tint, size = 36.dp)
                            Column {
                                Text(vl.first, style = MaterialTheme.typography.headlineSmall)
                                Text(vl.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onDone: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onDone) { Icon(lucide("arrow-left"), contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            val f = state.form
            val u = viewModel::updateForm
            Text("About you", style = MaterialTheme.typography.titleLarge); BasicsSection(f, u)
            Text("Body", style = MaterialTheme.typography.titleLarge); BodySection(f, u)
            Text("Goal", style = MaterialTheme.typography.titleLarge); GoalSection(f, u)
            Text("Training", style = MaterialTheme.typography.titleLarge); TrainingSection(f, u)
            Text("Food", style = MaterialTheme.typography.titleLarge); DietSection(f, u)
            Text("Equipment", style = MaterialTheme.typography.titleLarge); EquipmentSection(f, u)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save changes", viewModel::save, Modifier.fillMaxWidth(), enabled = !state.saving)
        }
    }
}
