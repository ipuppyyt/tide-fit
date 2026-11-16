package com.fitos.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitos.app.domain.repository.AppSettings
import com.fitos.app.domain.calc.TargetCalculator
import com.fitos.app.domain.calc.TargetInput
import com.fitos.app.domain.calc.TargetSuggestion
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.feature.profile.form.FormSection
import com.fitos.app.feature.profile.form.ProfileForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class OnboardingStep(val section: FormSection?, val title: String, val subtitle: String) {
    BASICS(FormSection.BASICS, "Let's get to know you", "This stays on your device."),
    BODY(FormSection.BODY, "Your body measurements", "Used for estimates and to track progress."),
    GOAL(FormSection.GOAL, "What's your main goal?", "You can change this any time."),
    TRAINING(FormSection.TRAINING, "How do you train?", "Helps size your starting estimates."),
    DIET(FormSection.DIET, "Food preferences", "Optional. Helps keep suggestions relevant and safe."),
    EQUIPMENT(FormSection.EQUIPMENT, "Your equipment", "Pick everything you have access to."),
    REVIEW(null, "Starting estimates", "A first guess, not a prescription.")
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.BASICS,
    val form: ProfileForm = ProfileForm(),
    val error: String? = null,
    val suggestion: TargetSuggestion? = null,
    val saving: Boolean = false
) {
    val stepIndex get() = step.ordinal
    val stepCount get() = OnboardingStep.entries.size
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: AppSettings,
    private val clock: Clock
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun updateForm(transform: (ProfileForm) -> ProfileForm) =
        _state.update { it.copy(form = transform(it.form), error = null) }

    fun next() {
        val s = _state.value
        val section = s.step.section
        val error = section?.let { s.form.validate(it) }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val nextStep = OnboardingStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        _state.update {
            it.copy(step = nextStep, error = null, suggestion = if (nextStep == OnboardingStep.REVIEW) computeSuggestion(it.form) else null)
        }
    }

    fun back(): Boolean {
        val prev = OnboardingStep.entries.getOrNull(_state.value.step.ordinal - 1) ?: return false
        _state.update { it.copy(step = prev, error = null) }
        return true
    }

    /** [acceptTargets] = true stores the suggestion as starting targets; false stores none. */
    fun finish(acceptTargets: Boolean) {
        val s = _state.value
        if (s.saving) return
        val profile = s.form.toProfile(Instant.now(clock))
        if (profile == null) {
            _state.update { it.copy(error = "Some details are missing or invalid. Go back and check them.") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                profiles.completeOnboarding(profile, if (acceptTargets) s.suggestion?.targets else null)
                settings.setOnboardingDone(true)
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = "Couldn't save your profile. Please try again.") }
            }
        }
    }

    private fun computeSuggestion(form: ProfileForm): TargetSuggestion? {
        val profile = form.toProfile(Instant.now(clock)) ?: return null
        return TargetCalculator.suggest(
            TargetInput(profile.age, profile.sex, profile.heightCm, profile.currentWeightKg, profile.activityLevel, profile.goal)
        )
    }
}
