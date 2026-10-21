package org.torproject.vpn.ui.exitselection.model

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.torproject.onionmasq.OnionMasq
import org.torproject.onionmasq.errors.CountryCodeException
import org.torproject.onionmasq.errors.ProxyStoppedException
import org.torproject.vpn.utils.Country
import org.torproject.vpn.utils.PreferenceHelper

@OptIn(ExperimentalCoroutinesApi::class)
class ExitSelectionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val preferenceHelper = mockk<PreferenceHelper>(relaxed = true) {
        every { exitNodeCountry } returns Country.RANDOM
        every { relayCountries } returns emptySet()
    }

    private lateinit var viewModel: ExitSelectionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(OnionMasq::class)
        every { OnionMasq.setCountryCode(any()) } returns Unit
        every { OnionMasq.refreshCircuits() } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_whenRelayCountriesEmpty_containsAllCountries() = runTest(dispatcher) {
        viewModel = ExitSelectionViewModel(preferenceHelper)

        val (currentExit, availableExits) = viewModel.state.value
        assertEquals(Country.RANDOM, currentExit)
        assertEquals(Country.entries, availableExits)
    }

    @Test
    fun initialState_whenRelayCountriesAvailable_filtersCountries() = runTest(dispatcher) {
        every { preferenceHelper.relayCountries } returns setOf("DE", "US")
        every { preferenceHelper.exitNodeCountry } returns Country.GERMANY

        viewModel = ExitSelectionViewModel(preferenceHelper)

        val (currentExit, availableExits) = viewModel.state.value
        assertEquals(Country.GERMANY, currentExit)
        val expectedExits = listOf(Country.RANDOM, Country.GERMANY, Country.UNITED_STATES)
        assertEquals(expectedExits, availableExits)
    }

    @Test
    fun selectExit_specificCountry_setsCountryCodeAndRefreshesCircuits() = runTest(dispatcher) {
        viewModel = ExitSelectionViewModel(preferenceHelper)

        viewModel.onEvent(ExitSelectionViewModel.Event.SelectExit(Country.JAPAN))
        runCurrent()

        verify { OnionMasq.setCountryCode("JP") }
        verify { OnionMasq.refreshCircuits() }
        verify { preferenceHelper.exitNodeCountry = Country.JAPAN }
    }

    @Test
    fun selectExit_randomCountry_setsNullCountryCodeAndRefreshesCircuits() = runTest(dispatcher) {
        viewModel = ExitSelectionViewModel(preferenceHelper)

        viewModel.onEvent(ExitSelectionViewModel.Event.SelectExit(Country.RANDOM))
        runCurrent()

        verify { OnionMasq.setCountryCode(null) }
        verify { OnionMasq.refreshCircuits() }
        verify { preferenceHelper.exitNodeCountry = Country.RANDOM }
    }

    @Test
    fun selectExit_whenOnionMasqThrowsCountryCodeException_handlesExceptionWithoutCrashing() =
        runTest(dispatcher) {
            every { OnionMasq.setCountryCode("JP") } throws
                CountryCodeException("Invalid country code")
            viewModel = ExitSelectionViewModel(preferenceHelper)

            viewModel.onEvent(ExitSelectionViewModel.Event.SelectExit(Country.JAPAN))
            runCurrent()

            verify { OnionMasq.setCountryCode("JP") }
            verify(exactly = 0) { OnionMasq.refreshCircuits() }
            verify(exactly = 0) { preferenceHelper.exitNodeCountry = Country.JAPAN }
        }

    @Test
    fun selectExit_whenOnionMasqThrowsProxyStoppedException_handlesExceptionWithoutCrashing() =
        runTest(dispatcher) {
            every { OnionMasq.refreshCircuits() } throws ProxyStoppedException("Proxy stopped")
            viewModel = ExitSelectionViewModel(preferenceHelper)

            viewModel.onEvent(ExitSelectionViewModel.Event.SelectExit(Country.JAPAN))
            runCurrent()

            verify { OnionMasq.setCountryCode("JP") }
            verify { OnionMasq.refreshCircuits() }
            verify(exactly = 0) { preferenceHelper.exitNodeCountry = Country.JAPAN }
        }

    @Test
    fun selectExit_whenOnionMasqThrowsGenericException_handlesExceptionWithoutCrashing() =
        runTest(dispatcher) {
            every { OnionMasq.setCountryCode(any()) } throws RuntimeException("Unexpected error")
            viewModel = ExitSelectionViewModel(preferenceHelper)

            viewModel.onEvent(ExitSelectionViewModel.Event.SelectExit(Country.JAPAN))
            runCurrent()

            verify { OnionMasq.setCountryCode("JP") }
            verify(exactly = 0) { OnionMasq.refreshCircuits() }
        }
}
