package com.artishorizon.tidefit.feature

import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.ThemeMode
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.repository.AppSettings
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.feature.onboarding.OnboardingStep
import com.artishorizon.tidefit.feature.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class FakeSettings : AppSettings {
    val done = MutableStateFlow(false)
    override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
    override val dynamicColor: Flow<Boolean> = MutableStateFlow(false)
    override val onboardingDone: Flow<Boolean> = done
    override val defaultGlassMl: Flow<Int> = MutableStateFlow(250)
    override suspend fun setDefaultGlassMl(ml: Int) {}
    override suspend fun setThemeMode(mode: ThemeMode) {}
    override suspend fun setDynamicColor(enabled: Boolean) {}
    override suspend fun setOnboardingDone(done: Boolean) { this.done.value = done }
}

private class FakeProfileRepository : ProfileRepository {
    var savedProfile: UserProfile? = null
    var savedTargets: DailyTargets? = null
    override fun observeProfile(): Flow<UserProfile?> = MutableStateFlow(savedProfile)
    override fun observeCurrentTargets(): Flow<DailyTargets?> = MutableStateFlow(savedTargets)
    override suspend fun completeOnboarding(profile: UserProfile, startingTargets: DailyTargets?) {
        savedProfile = profile; savedTargets = startingTargets
    }
    override suspend fun updateProfile(profile: UserProfile) { savedProfile = profile }
    override suspend fun saveTargets(targets: DailyTargets) { savedTargets = targets }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

    private fun vm() = OnboardingViewModel(FakeProfileRepository(), settings = FakeSettings(), clock = clock)

    @Test fun `cannot advance past basics with invalid input`() {
        val vm = vm()
        vm.next()
        assertEquals(OnboardingStep.BASICS, vm.state.value.step)
        assertNotNull(vm.state.value.error)
    }

    @Test fun `valid input advances and review step computes an estimate`() {
        val vm = vm()
        vm.updateForm { it.copy(name = "Asha", age = "29") }
        vm.next()
        vm.updateForm { it.copy(heightCm = "165", initialWeight = "80", currentWeight = "78", targetWeight = "68") }
        vm.next(); vm.next(); vm.next(); vm.next(); vm.next()

        assertEquals(OnboardingStep.REVIEW, vm.state.value.step)
        assertNotNull(vm.state.value.suggestion)
        assertNull(vm.state.value.error)
    }

    @Test fun `back returns to the previous step and stops at the first`() {
        val vm = vm()
        vm.updateForm { it.copy(name = "Asha", age = "29") }
        vm.next()
        assertEquals(true, vm.back())
        assertEquals(false, vm.back())
    }
}
