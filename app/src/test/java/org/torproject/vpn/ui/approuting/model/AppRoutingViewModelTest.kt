package org.torproject.vpn.ui.approuting.model

import android.util.Log
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.torproject.onionmasq.OnionMasq
import org.torproject.onionmasq.errors.ProxyStoppedException
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.database.app.Routing
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.VpnStatusObservable

@OptIn(ExperimentalCoroutinesApi::class)
class AppRoutingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val categoryCountFlow = MutableStateFlow<Map<Category, Int>>(emptyMap())
    private val globalRoutingFlow = MutableStateFlow(Routing.ALL)
    private val vpnStatus = MutableStateFlow(ConnectionState.INIT)
    private var vpnStarted = false

    private val appManager = mockk<AppManager> {
        every { categoryCount } returns categoryCountFlow
        every { globalRouting } returns globalRoutingFlow
        coEvery { protectAllApps(any()) } returns Unit
        coEvery { protectApp(any(), any()) } returns Unit
    }

    private lateinit var viewModel: AppRoutingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        mockkObject(VpnStatusObservable)
        every { VpnStatusObservable.statusLiveData } returns vpnStatus
        every { VpnStatusObservable.isVPNActive() } returns false

        mockkStatic(OnionMasq::class)
        every { OnionMasq.refreshCircuits() } returns Unit

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        viewModel = AppRoutingViewModel(
            appManager = appManager,
            startVpn = { vpnStarted = true }
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
    fun initialState() = runViewModelTest {
        val state = viewModel.state.value
        assertTrue(state.categoryCount.isEmpty())
        assertTrue(state.isProtectAllAppsEnabled)
        assertFalse(state.isVPNActive)
    }

    @Test
    fun categoryCountUpdate() = runViewModelTest {
        val counts = mapOf(Category.OTHER to 5)
        categoryCountFlow.value = counts

        assertEquals(counts, viewModel.state.value.categoryCount)
    }

    @Test
    fun globalRoutingUpdate() = runViewModelTest {
        globalRoutingFlow.value = Routing.SOME
        assertFalse(viewModel.state.value.isProtectAllAppsEnabled)
    }

    @Test
    fun vpnStatusUpdate() = runViewModelTest {
        vpnStatus.value = ConnectionState.CONNECTED
        assertTrue(viewModel.state.value.isVPNActive)
    }

    @Test
    fun protectAllAppsEvent() = runViewModelTest {
        viewModel.onEvent(AppRoutingViewModel.Event.ProtectAllApps(false))
        runCurrent()

        coVerify { appManager.protectAllApps(false) }
    }

    @Test
    fun updateRoutingEvent() = runViewModelTest {
        every { VpnStatusObservable.isVPNActive() } returns true
        viewModel.onEvent(AppRoutingViewModel.Event.UpdateRouting("com.test", true))
        runCurrent()

        coVerify { appManager.protectApp(true, "com.test") }
        assertTrue(vpnStarted)
    }

    @Test
    fun refreshCircuitsEvent_success_refreshesCircuitsOnOnionMasq() = runViewModelTest {
        viewModel.onEvent(AppRoutingViewModel.Event.RefreshCircuits)
        runCurrent()

        verify(exactly = 1) { OnionMasq.refreshCircuits() }
        verify(exactly = 0) { Log.e(any(), any(), any()) }
    }

    @Test
    fun refreshCircuitsEvent_whenProxyStopped_catchesExceptionAndLogsError() = runViewModelTest {
        val exception = ProxyStoppedException("Proxy stopped")
        every { OnionMasq.refreshCircuits() } throws exception

        viewModel.onEvent(AppRoutingViewModel.Event.RefreshCircuits)
        runCurrent()

        // Verify that OnionMasq.refreshCircuits() was called, and ProxyStoppedException was caught and logged without crashing
        verify(exactly = 1) { OnionMasq.refreshCircuits() }
        verify(exactly = 1) { Log.e("AppRoutingViewModel", "Failed to refresh circuit", exception) }
    }

    @Test
    fun updateExpansionStateEvent() = runViewModelTest {
        val category = Category.SYSTEM
        viewModel.onEvent(AppRoutingViewModel.Event.UpdateExpansionState(category, true))
        runCurrent()

        assertTrue(viewModel.state.value.categoryExpansionState.getValue(category))
    }
}
