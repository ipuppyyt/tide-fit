package com.fitos.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitos.app.domain.repository.AppSettings
import com.fitos.app.domain.model.DailyTargets
import com.fitos.app.domain.model.ThemeMode
import com.fitos.app.domain.model.UserProfile
import com.fitos.app.domain.repository.ProfileRepository
import com.fitos.app.feature.profile.form.ProfileForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val targets: DailyTargets? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val settings: AppSettings
) : ViewModel() {

    val state: StateFlow<ProfileUiState> = combine(
        profiles.observeProfile(),
        profiles.observeCurrentTargets(),
        settings.themeMode,
        settings.dynamicColor
    ) { profile, targets, theme, dynamic ->
        ProfileUiState(false, profile, targets, theme, dynamic)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
}

data class ProfileEditUiState(
    val loading: Boolean = true,
    val form: ProfileForm = ProfileForm(),
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val clock: Clock
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = profiles.observeProfile().first()
            _state.update { it.copy(loading = false, form = existing?.let(ProfileForm::fromProfile) ?: ProfileForm()) }
        }
    }

    fun updateForm(transform: (ProfileForm) -> ProfileForm) =
        _state.update { it.copy(form = transform(it.form), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        s.form.validateAll()?.let { (_, message) ->
            _state.update { it.copy(error = message) }
            return
        }
        val profile = s.form.toProfile(Instant.now(clock)) ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                profiles.updateProfile(profile)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = "Couldn't save changes. Please try again.") }
            }
        }
    }
}
