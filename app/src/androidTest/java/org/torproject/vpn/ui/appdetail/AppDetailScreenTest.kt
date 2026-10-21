package org.torproject.vpn.ui.appdetail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.torproject.onionmasq.circuit.CircuitCountryCodes
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.ui.appdetail.model.AppDetailViewModel
import org.torproject.vpn.utils.getString

@OptIn(ExperimentalTestApi::class)
class AppDetailScreenTest {

    @Test
    fun nullApp_rendersScreen() = runComposeUiTest {
        val state = AppDetailViewModel.State(app = null)

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {}
            )
        }
    }

    @Test
    fun loadedApp_displaysAppName() = runComposeUiTest {
        val testApp = App(
            packageName = "com.example.test",
            name = "Test Application",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10001
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithText("Test Application").assertIsDisplayed()
    }

    @Test
    fun navigateUp_clicksBackButton_triggersOnNavigateUpCallback() = runComposeUiTest {
        var navigatedUp = false
        val testApp = App(
            packageName = "com.example.test",
            name = "Test App",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10001
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {},
                onNavigateUp = { navigatedUp = true }
            )
        }

        onNodeWithContentDescription(getString(R.string.accessibility_back_button)).performClick()

        assertTrue(navigatedUp)
    }

    @Test
    fun torSupportedApp_displaysTorSupportDetails() = runComposeUiTest {
        val testApp = App(
            packageName = "org.torproject.torbrowser",
            name = "Tor Browser",
            category = Category.TOR,
            isRoutingEnabled = true,
            uid = 10002
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {}
            )
        }

        waitForIdle()

        onNodeWithText(getString(R.string.tor_powered_app)).assertIsDisplayed()
        onNodeWithText(getString(R.string.action_open_app, testApp.name)).assertIsDisplayed()
    }

    @Test
    fun openAppAction_canOpenAppTrue_emitsOpenAppEvent() = runComposeUiTest {
        var receivedEvent: AppDetailViewModel.Event? = null
        val testApp = App(
            packageName = "org.torproject.torbrowser",
            name = "Tor Browser",
            category = Category.TOR,
            isRoutingEnabled = true,
            uid = 10002
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            canOpenApp = true,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        onNodeWithText(getString(R.string.action_open_app, testApp.name)).performClick()

        assertTrue(receivedEvent is AppDetailViewModel.Event.OpenApp)
        assertEquals(testApp, (receivedEvent as AppDetailViewModel.Event.OpenApp).app)
    }

    @Test
    fun torSupportedApp_refreshMenuButtonNotShown() = runComposeUiTest {
        val testApp = App(
            packageName = "org.torproject.torbrowser",
            name = "Tor Browser",
            category = Category.TOR,
            isRoutingEnabled = true,
            uid = 10002
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithContentDescription(getString(R.string.action_refresh)).assertDoesNotExist()
    }

    @Test
    fun nonTorApp_displaysDataUsageAndProtectionOption() = runComposeUiTest {
        val testApp = App(
            packageName = "com.example.nontor",
            name = "Non Tor App",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10003
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithText(getString(R.string.up)).assertIsDisplayed()
        onNodeWithText(getString(R.string.down)).assertIsDisplayed()
        onNodeWithText(getString(R.string.action_protect_this_app)).assertIsDisplayed()
    }

    @Test
    fun nonTorApp_withCircuits_displaysCircuitsSection() = runComposeUiTest {
        val testApp = App(
            packageName = "com.example.nontor",
            name = "Non Tor App",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10004
        )
        val circuits = listOf(
            CircuitCountryCodes(arrayListOf("US", "DE", "FR"))
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            circuits = circuits,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithText(getString(R.string.circuits)).assertIsDisplayed()
    }

    @Test
    fun toggleProtection_emitsUpdateRoutingEvent() = runComposeUiTest {
        var receivedEvent: AppDetailViewModel.Event? = null
        val testApp = App(
            packageName = "com.example.test",
            name = "Test App",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10005
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        waitForIdle()
        onNodeWithText(getString(R.string.action_protect_this_app)).performClick()
        waitForIdle()

        assertTrue(receivedEvent is AppDetailViewModel.Event.UpdateRouting)
        assertEquals(false, (receivedEvent as AppDetailViewModel.Event.UpdateRouting).enabled)
    }

    @Test
    fun refreshCircuitAction_showsDialogAndEmitsRefreshCircuitEvent() = runComposeUiTest {
        var receivedEvent: AppDetailViewModel.Event? = null
        val testApp = App(
            packageName = "com.example.test",
            name = "Test App",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10006
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        // Click refresh icon button in top bar menu
        onNodeWithContentDescription(getString(R.string.action_refresh)).performClick()

        // Verify refresh confirmation dialog appears
        onNodeWithText(getString(R.string.reload_app_circuits)).assertIsDisplayed()

        // Confirm dialog
        onNodeWithText(getString(R.string.action_refresh)).performClick()

        // Verify RefreshCircuit event was emitted
        assertEquals(AppDetailViewModel.Event.RefreshCircuit, receivedEvent)
    }

    @Test
    fun refreshCircuitDialog_cancelDismissesDialogWithoutEvent() = runComposeUiTest {
        var receivedEvent: AppDetailViewModel.Event? = null
        val testApp = App(
            packageName = "com.example.test",
            name = "Test App",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10007
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = true
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = { event -> receivedEvent = event }
            )
        }

        // Click refresh icon button in top bar menu
        onNodeWithContentDescription(getString(R.string.action_refresh)).performClick()

        // Click Cancel in dialog
        onNodeWithText(getString(R.string.action_cancel)).performClick()

        // Verify no event was emitted
        assertNull(receivedEvent)
    }

    @Test
    fun vpnInactive_refreshMenuButtonNotShown() = runComposeUiTest {
        val testApp = App(
            packageName = "com.example.test",
            name = "Test App",
            category = Category.OTHER,
            isRoutingEnabled = true,
            uid = 10008
        )
        val state = AppDetailViewModel.State(
            app = testApp,
            isVPNActive = false
        )

        setContent {
            AppDetailScreen(
                state = state,
                onEvent = {}
            )
        }

        onNodeWithContentDescription(getString(R.string.action_refresh)).assertDoesNotExist()
    }
}
