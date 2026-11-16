package com.artishorizon.tidefit.feature.profile

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
fun ProfileScreen(onEditProfile: () -> Unit, onOpenData: () -> Unit, contentPadding: PaddingValues, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineLarge)

        state.profile?.let { p ->
            ProfileSummary(p, onEditProfile)
            TargetsCard(state.targets)
        }

        FitCard(onClick = onOpenData) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Data, reminders & backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Daily reminders, Health Connect import, and backup or restore of all your data.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FitCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Everything is stored on this device. No account, no telemetry, no uploads, and the app has no internet permission.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProfileSummary(p: UserProfile, onEdit: () -> Unit) {
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(p.name, style = MaterialTheme.typography.titleLarge)
            Text(p.goal.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xs))
            val u = p.weightUnit
            fun w(kg: Double) = "%.1f %s".format(Locale.US, Units.fromKg(kg, u), u.label)
            SummaryRow("Age", "${p.age}")
            SummaryRow("Height", "%.0f cm".format(Locale.US, p.heightCm))
            SummaryRow("Started at", w(p.initialWeightKg))
            SummaryRow("Current", w(p.currentWeightKg))
            SummaryRow("Target", w(p.targetWeightKg))
            SummaryRow("Training", "${p.trainingDaysPerWeek} days/week · ${p.experience.label}")
            if (p.allergies.isNotEmpty()) SummaryRow("Allergies", p.allergies.joinToString())
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton("Edit profile", onEdit, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TargetsCard(targets: DailyTargets?) {
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader("Daily targets") {
                if (targets?.source == ValueSource.FORMULA_ESTIMATE) EstimateBadge()
            }
            if (targets == null) {
                Text(
                    "No targets yet. Open Nutrition to calculate an estimate from your profile.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SummaryRow("Calories", "${targets.caloriesKcal} kcal")
                SummaryRow("Protein", "${targets.proteinG} g")
                SummaryRow("Carbohydrates", "${targets.carbohydratesG} g")
                SummaryRow("Fat", "${targets.fatG} g")
                SummaryRow("Fibre", "${targets.fibreG} g")
                SummaryRow("Water", "${targets.waterMl} ml")
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
                    IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
