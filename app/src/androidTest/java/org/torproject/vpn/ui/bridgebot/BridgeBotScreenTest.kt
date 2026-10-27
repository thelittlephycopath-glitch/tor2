package org.torproject.vpn.ui.bridgebot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.torproject.vpn.R
import org.torproject.vpn.ui.bridgebot.model.BotState
import org.torproject.vpn.ui.bridgebot.model.BridgeBotViewModel
import org.torproject.vpn.ui.bridgebot.model.BridgeRequestTransport
import org.torproject.vpn.utils.getString

@OptIn(ExperimentalTestApi::class)
class BridgeBotScreenTest {

    @Test
    fun idleState_rendersWelcomeMessageAndNewBridgesButton() = runComposeUiTest {
        val state = BridgeBotViewModel.State(botState = BotState.IDLE)

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithText(getString(R.string.bridge_bot)).assertIsDisplayed()
        onNodeWithTag(
            "botMessageText"
        ).assertTextEquals(getString(R.string.bot_msg_welcome)).assertIsDisplayed()
        onNodeWithText(getString(R.string.new_bridges)).assertIsDisplayed()
    }


    @Test
    fun idleState_clickWebTunnel_emitsWebTunnelTransportSelection() = runComposeUiTest {
        var receivedEvent: BridgeBotViewModel.Event? = null

        setContent {
            BridgeBotScreen(
                state = BridgeBotViewModel.State(botState = BotState.IDLE),
                onEvent = { event -> receivedEvent = event }
            )
        }

        onNodeWithText(getString(R.string.bridge_transport_webtunnel)).performClick()

        assertEquals(
            BridgeBotViewModel.Event.SetTransport(BridgeRequestTransport.WEBTUNNEL),
            receivedEvent
        )
    }

    @Test
    fun idleState_clickNewBridges_emitsRequestBridgesEvent() = runComposeUiTest {
        var receivedEvent: BridgeBotViewModel.Event? = null
        val state = BridgeBotViewModel.State(botState = BotState.IDLE)

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        onNodeWithText(getString(R.string.new_bridges)).performClick()

        assertEquals(BridgeBotViewModel.Event.RequestBridges, receivedEvent)
    }

    @Test
    fun fetchingState_rendersFetchingMessageAndCancelButton() = runComposeUiTest {
        val state = BridgeBotViewModel.State(botState = BotState.FETCHING)

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithTag(
            "botMessageText"
        ).assertTextEquals(getString(R.string.fetching_bridges)).assertIsDisplayed()
        onNodeWithText(getString(R.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    fun fetchingState_clickCancel_emitsCancelRequestEvent() = runComposeUiTest {
        var receivedEvent: BridgeBotViewModel.Event? = null
        val state = BridgeBotViewModel.State(botState = BotState.FETCHING)

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        onNodeWithText(getString(R.string.action_cancel)).performClick()

        assertEquals(BridgeBotViewModel.Event.CancelRequest, receivedEvent)
    }

    @Test
    fun successState_rendersBridgeListAndSaveButton() = runComposeUiTest {
        val bridges = listOf("obfs4 1.2.3.4:80 cert=xyz", "obfs4 5.6.7.8:80 cert=abc")
        val state = BridgeBotViewModel.State(
            botState = BotState.SUCCESS,
            bridges = bridges
        )

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithText(getString(R.string.bot_msg_results)).assertIsDisplayed()
        onNodeWithText("obfs4 1.2.3.4:80 cert=xyz").assertIsDisplayed()
        onNodeWithText("obfs4 5.6.7.8:80 cert=abc").assertIsDisplayed()
        onNodeWithText(getString(R.string.use_bridges)).assertIsDisplayed()
    }

    @Test
    fun successState_clickSaveBridges_emitsSaveBridgesAndNavigatesUp() = runComposeUiTest {
        var receivedEvent: BridgeBotViewModel.Event? = null
        var navigatedUp = false
        val bridges = listOf("obfs4 1.2.3.4:80 cert=xyz")
        val state = BridgeBotViewModel.State(
            botState = BotState.SUCCESS,
            bridges = bridges
        )

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = { event -> receivedEvent = event },
                onNavigateUp = { navigatedUp = true }
            )
        }

        onNodeWithText(getString(R.string.use_bridges)).performClick()

        assertEquals(BridgeBotViewModel.Event.SaveBridges, receivedEvent)
        assertTrue(navigatedUp)
    }

    @Test
    fun successState_clickCancel_emitsCancelRequestEvent() = runComposeUiTest {
        var receivedEvent: BridgeBotViewModel.Event? = null
        val state = BridgeBotViewModel.State(
            botState = BotState.SUCCESS,
            bridges = listOf("obfs4 1.2.3.4:80")
        )

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        waitForIdle()
        onNodeWithText(getString(R.string.action_cancel)).performClick()
        waitForIdle()

        assertEquals(BridgeBotViewModel.Event.CancelRequest, receivedEvent)
    }

    @Test
    fun errorState_rendersErrorMessage() = runComposeUiTest {
        val state = BridgeBotViewModel.State(
            botState = BotState.ERROR,
            errorMessage = R.string.error_fetching_bridges
        )

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithTag(
            "botMessageText"
        ).assertTextEquals(getString(R.string.error_fetching_bridges)).assertIsDisplayed()
        onNodeWithText(getString(R.string.action_try_again)).assertIsDisplayed()
    }


    @Test
    fun webTunnelErrorStateShowsOfficialBridgeDBFallback() = runComposeUiTest {
        val state = BridgeBotViewModel.State(
            botState = BotState.ERROR,
            errorMessage = R.string.error_fetching_bridges,
            selectedTransport = BridgeRequestTransport.WEBTUNNEL
        )

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithText(getString(R.string.webtunnel_fetch_fallback)).assertIsDisplayed()
        onNodeWithText(getString(R.string.open_bridges_website)).assertIsDisplayed()
    }

    @Test
    fun errorState_clickTryAgain_emitsRequestBridgesEvent() = runComposeUiTest {
        var receivedEvent: BridgeBotViewModel.Event? = null
        val state = BridgeBotViewModel.State(
            botState = BotState.ERROR,
            errorMessage = R.string.error_fetching_bridges
        )

        setContent {
            BridgeBotScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        waitForIdle()
        onNodeWithText(getString(R.string.action_try_again)).performClick()
        waitForIdle()

        assertEquals(BridgeBotViewModel.Event.RequestBridges, receivedEvent)
    }

    @Test
    fun navigateUp_clicksBackButton_triggersOnNavigateUpCallback() = runComposeUiTest {
        var navigatedUp = false

        setContent {
            BridgeBotScreen(
                state = BridgeBotViewModel.State(),
                onEvent = {},
                onNavigateUp = { navigatedUp = true }
            )
        }

        onNodeWithContentDescription(getString(R.string.accessibility_back_button)).performClick()

        assertTrue(navigatedUp)
    }
}
