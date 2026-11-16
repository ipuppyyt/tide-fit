package com.artishorizon.tidefit.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Motion
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.EstimateBadge
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitLinearProgress
import com.artishorizon.tidefit.core.designsystem.components.MacroIndicator
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.domain.calc.TargetSuggestion
import com.artishorizon.tidefit.feature.profile.form.BasicsSection
import com.artishorizon.tidefit.feature.profile.form.BodySection
import com.artishorizon.tidefit.feature.profile.form.DietSection
import com.artishorizon.tidefit.feature.profile.form.EquipmentSection
import com.artishorizon.tidefit.feature.profile.form.GoalSection
import com.artishorizon.tidefit.feature.profile.form.TrainingSection

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.stepIndex > 0) { viewModel.back() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.screenHorizontal)
    ) {
        Spacer(Modifier.height(Spacing.lg))
        FitLinearProgress((state.stepIndex + 1f) / state.stepCount)
        Spacer(Modifier.height(Spacing.xl))

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(Motion.Medium)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(Motion.Medium))) togetherWith
                    (slideOutHorizontally(tween(Motion.Short)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(Motion.Short)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding-step"
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(step.title, style = MaterialTheme.typography.headlineLarge)
                Text(step.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xl))
                when (step) {
                    OnboardingStep.BASICS -> BasicsSection(state.form, viewModel::updateForm)
                    OnboardingStep.BODY -> BodySection(state.form, viewModel::updateForm)
                    OnboardingStep.GOAL -> GoalSection(state.form, viewModel::updateForm)
                    OnboardingStep.TRAINING -> TrainingSection(state.form, viewModel::updateForm)
                    OnboardingStep.DIET -> DietSection(state.form, viewModel::updateForm)
                    OnboardingStep.EQUIPMENT -> EquipmentSection(state.form, viewModel::updateForm)
                    OnboardingStep.REVIEW -> SuggestionReview(state.suggestion)
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.sm))
        }

        if (state.step == OnboardingStep.REVIEW) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton("Use these as my starting targets", { viewModel.finish(true) }, Modifier.fillMaxWidth(), enabled = !state.saving && state.suggestion != null)
                SecondaryButton("Skip, I'll set targets later", { viewModel.finish(false) }, Modifier.fillMaxWidth(), enabled = !state.saving)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.stepIndex > 0) SecondaryButton("Back", { viewModel.back() }, Modifier.weight(1f))
                PrimaryButton("Continue", viewModel::next, Modifier.weight(2f))
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun SuggestionReview(suggestion: TargetSuggestion?) {
    if (suggestion == null) {
        Text("We couldn't compute estimates from your details.", color = MaterialTheme.colorScheme.error)
        return
    }
    val t = suggestion.targets
    val c = FitTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        FitCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${t.caloriesKcal} kcal / day", style = MaterialTheme.typography.headlineMedium)
                    EstimateBadge()
                }
                val total = (t.proteinG * 4 + t.carbohydratesG * 4 + t.fatG * 9).coerceAtLeast(1)
                MacroIndicator("Protein", "${t.proteinG} g", t.proteinG * 4f / total, c.protein)
                MacroIndicator("Carbohydrates", "${t.carbohydratesG} g", t.carbohydratesG * 4f / total, c.carbs)
                MacroIndicator("Fat", "${t.fatG} g", t.fatG * 9f / total, c.fat)
                Text("Fibre ${t.fibreG} g · Water ${t.waterMl} ml", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("How these were calculated", style = MaterialTheme.typography.titleSmall)
        suggestion.explanation.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
