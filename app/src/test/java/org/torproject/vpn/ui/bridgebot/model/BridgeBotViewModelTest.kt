package org.torproject.vpn.ui.bridgebot.model

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.R
import org.torproject.vpn.circumvention.SettingsRequest
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.BridgeType
import org.torproject.vpn.vpn.GetBridgeLines

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeBotViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val preferenceHelper = mockk<PreferenceHelper>(relaxed = true)
    private val getBridgeLines = mockk<GetBridgeLines>()

    private lateinit var viewModel: BridgeBotViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(LogObservable::class)
        every { LogObservable.getInstance() } returns mockk(relaxed = true)

        viewModel = BridgeBotViewModel(
            preferenceHelper = preferenceHelper,
            getBridgeLines = getBridgeLines
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState() {
        val state = viewModel.state.value
        assertEquals(BotState.IDLE, state.botState)
        assertEquals(emptyList(), state.bridges)
        assertNull(state.errorMessage)
    }

    @Test
    fun requestBridges_success() = runTest(dispatcher) {
        val expectedBridges = listOf("obfs4 192.168.1.1:80", "obfs4 10.0.0.1:80")
        coEvery { getBridgeLines(any()) } returns expectedBridges

        viewModel.onEvent(BridgeBotViewModel.Event.RequestBridges)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(BotState.SUCCESS, state.botState)
        assertEquals(expectedBridges, state.bridges)
        assertNull(state.errorMessage)
    }

    @Test
    fun requestBridges_emptyResult_showsError() = runTest(dispatcher) {
        coEvery { getBridgeLines(any()) } returns emptyList()

        viewModel.onEvent(BridgeBotViewModel.Event.RequestBridges)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(BotState.ERROR, state.botState)
        assertEquals(emptyList(), state.bridges)
        assertEquals(R.string.error_no_bridges_found, state.errorMessage)
    }

    @Test
    fun requestBridges_exception_showsError() = runTest(dispatcher) {
        coEvery { getBridgeLines(any()) } throws IOException("Network timeout")

        viewModel.onEvent(BridgeBotViewModel.Event.RequestBridges)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(BotState.ERROR, state.botState)
        assertEquals(emptyList(), state.bridges)
        assertEquals(R.string.error_fetching_bridges, state.errorMessage)
    }

    @Test
    fun cancelRequest_resetsToIdle() = runTest(dispatcher) {
        viewModel.onEvent(BridgeBotViewModel.Event.CancelRequest)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(BotState.IDLE, state.botState)
        assertNull(state.errorMessage)
    }

    @Test
    fun saveBridges_savesToPreferenceHelper() = runTest(dispatcher) {
        val expectedBridges = listOf("obfs4 192.168.1.1:80")
        coEvery { getBridgeLines(any()) } returns expectedBridges

        viewModel.onEvent(BridgeBotViewModel.Event.RequestBridges)
        runCurrent()

        viewModel.onEvent(BridgeBotViewModel.Event.SaveBridges)
        runCurrent()

        verify { preferenceHelper.bridgeLines = expectedBridges }
        verify { preferenceHelper.bridgeType = BridgeType.Manual }
    }


    @Test
    fun requestBridges_webTunnelRequestsOnlyWebTunnelAndSavesItAsManualBridgeLines() = runTest(dispatcher) {
        val expectedBridges = listOf(
            "webtunnel 192.0.2.1:443 0123456789012345678901234567890123456789 url=https://example.invalid/path"
        )
        coEvery { getBridgeLines(any()) } returns expectedBridges

        viewModel.onEvent(
            BridgeBotViewModel.Event.SetTransport(BridgeRequestTransport.WEBTUNNEL)
        )
        runCurrent()
        viewModel.onEvent(BridgeBotViewModel.Event.RequestBridges)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(BotState.SUCCESS, state.botState)
        assertEquals(BridgeRequestTransport.WEBTUNNEL, state.selectedTransport)
        assertEquals(expectedBridges, state.bridges)
        coVerify {
            getBridgeLines(SettingsRequest(transports = listOf("webtunnel")))
        }

        viewModel.onEvent(BridgeBotViewModel.Event.SaveBridges)
        runCurrent()
        verify { preferenceHelper.bridgeLines = expectedBridges }
        // Manual bridge storage preserves WebTunnel's private URL/path from the bridge line.
        verify { preferenceHelper.bridgeType = BridgeType.Manual }
    }

    @Test
    fun saveBridges_emptyBridges_setsBridgeTypeNone() = runTest(dispatcher) {
        viewModel.onEvent(BridgeBotViewModel.Event.SaveBridges)
        runCurrent()

        verify { preferenceHelper.bridgeLines = emptyList() }
        verify { preferenceHelper.bridgeType = BridgeType.None }
    }
}
