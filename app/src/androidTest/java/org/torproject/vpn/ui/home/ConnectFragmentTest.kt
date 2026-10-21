package org.torproject.vpn.ui.home

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import androidx.appcompat.widget.AppCompatImageButton
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runEmptyComposeUiTest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withResourceName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.vpn.ComposeActivity
import org.torproject.vpn.Constants
import org.torproject.vpn.MainActivity
import org.torproject.vpn.R
import org.torproject.vpn.ui.navigation.Destination
import org.torproject.vpn.utils.ConnectHelper
import org.torproject.vpn.utils.Country
import org.torproject.vpn.utils.CustomInteractions.tryResolve
import org.torproject.vpn.utils.Json
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.VpnStatusObservable
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
@LargeTest
class ConnectFragmentTest {
    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    private var device: UiDevice? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        val instrumentation = getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
    }

    @After
    fun tearDown() {
        try {
            scenario?.close()
        } catch (_: Exception) {
            // We don't really care about it as we are trying to wind down
        } finally {
            scenario = null
        }
    }

    private fun arrangeMainActivity(showGuide: Boolean = false) {
        val preferenceHelper = PreferenceHelper(ApplicationProvider.getApplicationContext())
        preferenceHelper.clear()
        preferenceHelper.shouldShowGuide = showGuide
        VpnStatusObservable.update(ConnectionState.INIT)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun test01ExperimentalDialog() {
        arrangeMainActivity(showGuide = true)
        val preferenceHelper = PreferenceHelper(ApplicationProvider.getApplicationContext())
        assertTrue(
            "PreferenceHelper.shouldShowGuide should be true",
            preferenceHelper.shouldShowGuide
        )

        tryResolve(onView(withId(R.id.content_container)), matches(isDisplayed()))
        Screengrab.screenshot("connect_fragment_experimental_hint")
        onView(withId(R.id.btn_start_testing)).perform(click())

        tryResolve(onView(withId(R.id.tv_connect_action_btn)), matches(isCompletelyDisplayed()))
        assertFalse(
            "PreferenceHelper.shouldShowGuide should be false",
            preferenceHelper.shouldShowGuide
        )
    }

    @Test
    fun test02InitialState() {
        arrangeMainActivity()

        onView(withId(R.id.tv_connect_action_btn)).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.toolbar)).check(matches(isCompletelyDisplayed()))

        onView(withId(R.id.toolbar)).check(matches(hasDescendant(withText(R.string.app_name))))
        onView(withId(R.id.tv_connect_action_btn)).check(matches(withText(R.string.action_connect)))
        onView(withId(R.id.cl_apps_card)).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.cl_connection_card)).check(matches(isCompletelyDisplayed()))
        Screengrab.screenshot("connect_fragment_initial_state")
    }

    @Test
    fun test03ClickAppsCardAndBack() {
        arrangeMainActivity()
        val composeActivity = ComponentName(
            getInstrumentation().targetContext,
            ComposeActivity::class.java
        )
        val appRoutingIntent = Matchers.allOf(
            hasComponent(composeActivity),
            hasExtra(
                Constants.INTENT_SCREEN_KEY,
                Json.encodeToString<Destination>(Destination.AppRouting)
            )
        )

        Intents.init()
        try {
            intending(appRoutingIntent).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    null
                )
            )
            onView(withId(R.id.cl_apps_card)).perform(click())
            intended(appRoutingIntent)
        } finally {
            Intents.release()
        }

        tryResolve(
            onView(withId(R.id.tv_connect_action_btn)),
            matches(isCompletelyDisplayed()),
            5
        )
    }

    @Test
    fun test04ClickConnectionCardAndBack() {
        arrangeMainActivity()

        onView(withId(R.id.cl_connection_card)).perform(click())

        onView(withId(R.id.toolbar)).check(
            matches(
                Matchers.allOf(
                    isDisplayed(),
                    hasDescendant(withText(R.string.bridges))
                )
            )
        )
        Screengrab.screenshot("connection_settings")

        onView(
            Matchers.allOf(
                instanceOf(AppCompatImageButton::class.java),
                withParent(withId(R.id.toolbar))
            )
        ).perform(click())

        onView(withId(R.id.tv_connect_action_btn)).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.tv_connect_action_btn)).check(matches(withText(R.string.action_connect)))
    }

    @Test
    fun test05Connect() {
        arrangeMainActivity()

        ConnectHelper.connect(device, true)
    }

    @Test
    fun test06ReinitializeConnectedUIState() {
        arrangeMainActivity()
        ConnectHelper.connect(device, false)

        onView(
            Matchers.allOf(
                withText(R.string.action_configure),
                withResourceName("navigation_bar_item_small_label_view")
            )
        ).perform(click())

        onView(
            Matchers.allOf(
                withText(R.string.action_connect),
                withResourceName("navigation_bar_item_small_label_view")
            )
        ).perform(click())

        onView(
            withId(R.id.toolbar)
        ).check(matches(hasDescendant(withText(R.string.state_connected))))
        Screengrab.screenshot("connect_fragment_reinitialized_connected_state")
    }

    @Test
    fun test07Disconnect() {
        arrangeMainActivity()
        ConnectHelper.connect(device, false)

        onView(withId(R.id.tv_connect_action_btn)).perform(click())

        tryResolve(
            onView(withId(R.id.toolbar)),
            matches(hasDescendant(withText(R.string.state_disconnected))),
            5
        )
        assertFalse("VPN should be inactive after disconnect", VpnStatusObservable.isVPNActive())
        onView(withId(R.id.cl_apps_card)).check(matches(not(isDisplayed())))
        onView(withId(R.id.cl_connection_card)).check(matches(not(isDisplayed())))
        Screengrab.screenshot("connect_fragment_disconnected_state")
    }

    @Test
    fun test08ReinitializeDisconnectedState() {
        arrangeMainActivity()

        ConnectHelper.connect(device, false)
        onView(withId(R.id.tv_connect_action_btn)).perform(click())
        tryResolve(
            onView(withId(R.id.toolbar)),
            matches(hasDescendant(withText(R.string.state_disconnected))),
            5
        )

        onView(
            Matchers.allOf(
                withText(R.string.action_configure),
                withResourceName("navigation_bar_item_small_label_view")
            )
        ).perform(click())
        onView(
            Matchers.allOf(
                withText(R.string.action_connect),
                withResourceName("navigation_bar_item_small_label_view")
            )
        ).perform(click())

        onView(
            withId(R.id.toolbar)
        ).check(matches(hasDescendant(withText(R.string.state_disconnected))))
        onView(withId(R.id.cl_apps_card)).check(matches(not(isDisplayed())))
        onView(withId(R.id.cl_connection_card)).check(matches(not(isDisplayed())))
        Screengrab.screenshot("connect_fragment_reinitialized_disconnected_state")
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun test09CountrySelection() = runEmptyComposeUiTest {
        val country = Country.UNITED_STATES

        arrangeMainActivity()
        ConnectHelper.connect(device, false)

        onView(withId(R.id.cl_selection_exit_inner)).perform(click())
        Screengrab.screenshot("exit_selection_all_apps_selected")

        onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(country.locale()!!.displayCountry))

        onNodeWithText(country.locale()!!.displayCountry)
            .performClick()

        Screengrab.screenshot("exit_selection_US_selected")
        pressBack()

        tryResolve(
            onView(withId(R.id.cl_selection_exit_inner)),
            matches(isCompletelyDisplayed()),
            3
        )
        tryResolve(
            onView(withId(R.id.imageView6)),
            matches(
                Matchers.allOf(
                    isCompletelyDisplayed(),
                    withContentDescription(country.locale()!!.displayCountry)
                )
            ),
            3
        )

        Screengrab.screenshot("connect_fragment_US_selected")

        assertEquals(
            "selected exit country should be the United States",
            country.code,
            PreferenceHelper(ApplicationProvider.getApplicationContext()).exitNodeCountry.code
        )
    }
}
