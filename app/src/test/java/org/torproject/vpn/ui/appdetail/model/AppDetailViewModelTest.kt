package org.torproject.vpn.ui.appdetail.model

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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.torproject.onionmasq.OnionMasq
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.AppLauncher
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.VpnStatusObservable

@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val packageName = "com.example.testapp"
    private val app = App(
        packageName = packageName,
        name = "Test App",
        uid = 10001,
        category = Category.OTHER,
        isRoutingEnabled = false
    )

    private val appFlow = MutableSharedFlow<App>(replay = 1)
    private val vpnStatus = MutableStateFlow(ConnectionState.INIT)
    private var vpnStarted = false

    private val appManager = mockk<AppManager> {
        every { getApp(packageName) } returns appFlow
        coEvery { protectApp(any(), any()) } returns Unit
    }

    private val appLauncher = mockk<AppLauncher> {
        every { canLaunch(any()) } returns true
    }

    private lateinit var viewModel: AppDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        mockkObject(VpnStatusObservable)
        every { VpnStatusObservable.statusLiveData } returns vpnStatus

        mockkStatic(OnionMasq::class)
        every { OnionMasq.getBytesReceivedForApp(any()) } returns 0L
        every { OnionMasq.getBytesSentForApp(any()) } returns 0L
        every { OnionMasq.getCircuitCountryCodesForAppUid(any()) } returns arrayListOf()
        every { OnionMasq.refreshCircuitsForApp(any()) } returns Unit

        viewModel = AppDetailViewModel(
            appManager = appManager,
            packageName = packageName,
            startVpn = { vpnStarted = true },
            appLauncher = appLauncher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    /**
     * The view model polls traffic stats in an endless loop on the main dispatcher, which shares
     * the test scheduler. runTest drains every pending task once the body ends, so the view model
     * must be cancelled inside the body or the test never finishes.
     */
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
        assertNull(state.app)
        assertFalse(state.isVPNActive)
        assertTrue(state.circuits.isEmpty())
    }

    @Test
    fun loadsApp_canOpenAppTrue() = runViewModelTest {
        every { appLauncher.canLaunch(app) } returns true
        appFlow.tryEmit(app)

        assertEquals(app, viewModel.state.value.app)
        assertTrue(viewModel.state.value.canOpenApp)
    }

    @Test
    fun loadsApp_canOpenAppFalse() = runViewModelTest {
        every { appLauncher.canLaunch(app) } returns false
        appFlow.tryEmit(app)

        assertEquals(app, viewModel.state.value.app)
        assertFalse(viewModel.state.value.canOpenApp)
    }

    @Test
    fun openApp_invokesAppLauncher() = runViewModelTest {
        coEvery { appLauncher.invoke(app) } returns Unit

        viewModel.onEvent(AppDetailViewModel.Event.OpenApp(app))
        runCurrent()

        coVerify { appLauncher.invoke(app) }
    }

    @Test
    fun vpnStatus() = runViewModelTest {
        vpnStatus.value = ConnectionState.CONNECTED
        assertTrue(viewModel.state.value.isVPNActive)

        vpnStatus.value = ConnectionState.DISCONNECTED
        assertFalse(viewModel.state.value.isVPNActive)
    }

    @Test
    fun pollsDataUsage() = runViewModelTest {
        every { OnionMasq.getBytesReceivedForApp(app.uid.toLong()) } returns 100L
        every { OnionMasq.getBytesSentForApp(app.uid.toLong()) } returns 50L
        appFlow.tryEmit(app)
        advanceTimeBy(1001.milliseconds)

        assertEquals(100L, viewModel.state.value.dataUsage.downstreamData)
        assertEquals(50L, viewModel.state.value.dataUsage.upstreamData)

        every { OnionMasq.getBytesReceivedForApp(app.uid.toLong()) } returns 300L
        every { OnionMasq.getBytesSentForApp(app.uid.toLong()) } returns 150L
        advanceTimeBy(1000.milliseconds)

        assertEquals(300L, viewModel.state.value.dataUsage.downstreamData)
        assertEquals(150L, viewModel.state.value.dataUsage.upstreamData)
    }

    @Test
    fun updateRouting() = runViewModelTest {
        viewModel.onEvent(AppDetailViewModel.Event.UpdateRouting(enabled = true))
        runCurrent()

        coVerify { appManager.protectApp(true, packageName) }
        assertTrue(vpnStarted)
    }

    @Test
    fun refreshCircuit() = runViewModelTest {
        appFlow.tryEmit(app)

        viewModel.onEvent(AppDetailViewModel.Event.RefreshCircuit)
        runCurrent()

        verify { OnionMasq.refreshCircuitsForApp(app.uid.toLong()) }
    }

    @Test
    fun refreshCircuit_withoutApp() = runViewModelTest {
        viewModel.onEvent(AppDetailViewModel.Event.RefreshCircuit)
        runCurrent()

        verify(exactly = 0) { OnionMasq.refreshCircuitsForApp(any()) }
    }
}
