package org.torproject.vpn.ui.iconsetting.model

import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.torproject.vpn.utils.PreferenceHelper

@OptIn(ExperimentalCoroutinesApi::class)
class IconSettingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val preferenceHelper = mockk<PreferenceHelper> {
        every { launcherClass } returns "Default"
        every { registerListener(any()) } answers {}
        every { unregisterListener(any()) } answers {}
    }

    private var invokedLauncher: Launcher? = null
    private val setLauncherIcon: (Launcher) -> Unit = { launcher ->
        invokedLauncher = launcher
    }

    private lateinit var viewModel: IconSettingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = IconSettingViewModel(
            preferenceHelper = preferenceHelper,
            setLauncherIcon = setLauncherIcon
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun initialState_defaultsToDefaultLauncher() = runViewModelTest {
        val state = viewModel.state.value
        assertEquals(LauncherDefault, state.currentLauncher)
        assertEquals(Launcher.entries, state.launchers)
    }

    @Test
    fun legacyLauncherClassPreference_resolvesCorrectly() = runViewModelTest {
        every { preferenceHelper.launcherClass } returns LauncherWeather::class.java.name
        val legacyViewModel = IconSettingViewModel(
            preferenceHelper = preferenceHelper,
            setLauncherIcon = setLauncherIcon
        )
        assertEquals(LauncherWeather, legacyViewModel.state.value.currentLauncher)
        legacyViewModel.viewModelScope.cancel()
    }

    @Test
    fun unknownLauncherPreference_fallsBackToDefault() = runViewModelTest {
        every { preferenceHelper.launcherClass } returns "UnknownLauncher"
        val unknownViewModel = IconSettingViewModel(
            preferenceHelper = preferenceHelper,
            setLauncherIcon = setLauncherIcon
        )
        assertEquals(LauncherDefault, unknownViewModel.state.value.currentLauncher)
        unknownViewModel.viewModelScope.cancel()
    }

    @Test
    fun setLauncherIconEvent_invokesCallback() = runViewModelTest {
        invokedLauncher = null
        viewModel.onEvent(IconSettingViewModel.Event.SetLauncherIconEvent(LauncherWeather))
        runCurrent()

        assertEquals(LauncherWeather, invokedLauncher)
    }
}
