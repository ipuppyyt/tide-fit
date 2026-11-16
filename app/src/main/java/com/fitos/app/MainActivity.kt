package com.fitos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitos.app.domain.repository.AppSettings
import com.fitos.app.core.designsystem.FitOSTheme
import com.fitos.app.domain.model.ThemeMode
import com.fitos.app.feature.onboarding.OnboardingScreen
import com.fitos.app.navigation.MainNavigation
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Null [onboardingDone] means settings are still loading; the splash screen stays up until it resolves. */
data class RootState(
    val onboardingDone: Boolean? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false
)

@HiltViewModel
class RootViewModel @Inject constructor(settings: AppSettings) : ViewModel() {
    val state: StateFlow<RootState> = combine(settings.onboardingDone, settings.themeMode, settings.dynamicColor) { done, theme, dyn ->
        RootState(done, theme, dyn)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState())
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val root: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { root.state.value.onboardingDone == null }
        enableEdgeToEdge()
        setContent {
            val state = root.state.collectAsStateWithLifecycle().value
            FitOSTheme(state.themeMode, state.dynamicColor) {
                when (state.onboardingDone) {
                    null -> Unit
                    false -> OnboardingScreen()
                    true -> MainNavigation()
                }
            }
        }
    }
}
