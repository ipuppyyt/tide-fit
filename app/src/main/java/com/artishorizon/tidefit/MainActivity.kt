package com.artishorizon.tidefit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.domain.repository.AppSettings
import com.artishorizon.tidefit.core.designsystem.TideFitTheme
import com.artishorizon.tidefit.feature.onboarding.OnboardingScreen
import com.artishorizon.tidefit.navigation.MainNavigation
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
    val paletteId: String? = null
)

@HiltViewModel
class RootViewModel @Inject constructor(settings: AppSettings) : ViewModel() {
    val state: StateFlow<RootState> = combine(settings.onboardingDone, settings.palette) { done, palette ->
        RootState(done, palette)
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
            TideFitTheme(state.paletteId) {
                when (state.onboardingDone) {
                    null -> Unit
                    false -> OnboardingScreen()
                    true -> MainNavigation()
                }
            }
        }
    }
}
