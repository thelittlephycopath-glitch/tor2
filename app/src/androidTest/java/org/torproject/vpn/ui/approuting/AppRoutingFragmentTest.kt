package org.torproject.vpn.ui.approuting

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.vpn.MainActivity
import org.torproject.vpn.R
import org.torproject.vpn.utils.ConnectHelper
import org.torproject.vpn.utils.NetworkUtils
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.VpnStatusObservable
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
@LargeTest
class AppRoutingFragmentTest {

    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    // The app routing screen is Compose-based, so Espresso's onView can't see its
    // individual nodes (they all live inside one AndroidComposeView). This rule is
    // unbound to a specific activity since ComposeActivity sets its own content and
    // is launched later, mid-test, via an intent rather than by this rule.
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private var device: UiDevice? = null
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        val instrumentation = getInstrumentation()
        device = UiDevice.getInstance(instrumentation)

        // Set the guide preference before the activity is created, otherwise the
        // beta welcome guide covers the connect screen and blocks the connect button.
        val preferenceHelper = PreferenceHelper(ApplicationProvider.getApplicationContext())
        preferenceHelper.clear()
        preferenceHelper.shouldShowGuide = false
        VpnStatusObservable.update(ConnectionState.INIT)

        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun testChangeIPsGlobally() {
        if (!VpnStatusObservable.isVPNActive()) {
            ConnectHelper.connect(device, false)
        }
        openAppFragment()
        val currentIP = NetworkUtils.getExitIP()
        assertNotNull("previous IP was not null: $currentIP", currentIP)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // menu item click
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.action_refresh_circuits)
        ).performClick()
        // dialog action click
        composeTestRule.onNodeWithText(context.getString(R.string.action_refresh)).performClick()

        // Tor needs time to rebuild circuits after the refresh request, so poll
        // for the exit IP to change instead of checking it immediately.
        val newIP = waitForNewExitIP(currentIP)

        assertNotNull("new IP is not null: $newIP", newIP)
        assertFalse(
            "IP changed after global IP switch (currentIP $currentIP - new IP $newIP)",
            newIP == currentIP
        )
    }

    private fun waitForNewExitIP(previousIP: String?, maxTries: Int = 10): String? {
        var latestIP: String? = null
        repeat(maxTries) {
            Thread.sleep(3000)
            latestIP = NetworkUtils.getExitIP()
            if (latestIP != null && latestIP != previousIP) {
                return latestIP
            }
        }
        return latestIP
    }

    private fun openAppFragment() {
        onView(
            CoreMatchers.allOf(
                *arrayOf(
                    withText(R.string.action_configure),
                    ViewMatchers.withResourceName("navigation_bar_item_small_label_view")
                )
            )
        ).perform(click())

        onView(withId(R.id.apps)).perform(click())
    }
}
