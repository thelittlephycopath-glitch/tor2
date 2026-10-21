package org.torproject.vpn.ui.help

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.CollapsingToolbarLayout
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.vpn.MainActivity
import org.torproject.vpn.R
import org.torproject.vpn.utils.CustomMatchers
import org.torproject.vpn.utils.PreferenceHelper
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
@LargeTest
class HelpFragmentTest {

    @get:Rule
    val rule = activityScenarioRule<MainActivity>()

    @get:Rule
    val intentsRule = IntentsRule()

    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    @Before
    fun setup() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())

        val context: Context = ApplicationProvider.getApplicationContext()
        val sharedPreference =
            context.applicationContext.getSharedPreferences("tor-vpn", Context.MODE_PRIVATE)
        sharedPreference.edit().putBoolean(PreferenceHelper.SHOULD_SHOW_GUIDE, false).commit()
    }

    @Test
    fun testHelpNavigation() {
        openHelpFragment()

        onView(isAssignableFrom(CollapsingToolbarLayout::class.java)).check(
            matches(
                CustomMatchers.withCollapsibleToolbarTitle(
                    containsString(
                        InstrumentationRegistry.getInstrumentation().targetContext.getString(
                            R.string.help
                        )
                    )
                )
            )
        )

        onView(withId(R.id.ll_faq_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_reort_bugs_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_contact_support_container)).check(matches(isDisplayed()))

        onView(withText(R.string.view_support_docs)).check(matches(isDisplayed()))
        onView(withText(R.string.report_bug)).check(matches(isDisplayed()))
        onView(withText(R.string.contact_support)).check(matches(isDisplayed()))

        onView(withText(R.string.help_description_wip)).check(matches(isDisplayed()))
        onView(withText(R.string.help_description_bug_report)).check(matches(isDisplayed()))

        Screengrab.screenshot("help")
    }

    @Test
    fun testHelpNavigationFAQ() {
        openHelpFragment()
        onView(withId(R.id.ll_faq_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_faq_container)).perform(click())
        onView(withId(R.id.wv_help)).check(matches(isCompletelyDisplayed()))
        Screengrab.screenshot("support-docs")
    }

    @Test
    fun testHelpNavigationReportBug() {
        openReportBugBottomSheet()

        Screengrab.screenshot("report-bug-sheet")
    }

    @Test
    fun testHelpNavigationReportBugForum() {
        openReportBugBottomSheet()
        onView(withId(R.id.ll_tor_forum)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_tor_forum)).perform(click())

        val webpage: Uri = Uri.parse("https://forum.torproject.org")
        intended(
            allOf(
                *arrayOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData(webpage)
                )
            )
        )
    }

    @Test
    fun testHelpNavigationReportBugGitlab() {
        openReportBugBottomSheet()
        onView(withId(R.id.ll_gitlab)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_gitlab)).perform(click())

        val webpage: Uri = Uri.parse("https://gitlab.torproject.org/tpo/applications/vpn/-/issues")
        intended(
            allOf(
                *arrayOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData(webpage)
                )
            )
        )
    }

    @Test
    fun testHelpNavigationContactReport() {
        openContactSupportBottomSheet()
        Screengrab.screenshot("contact-support-sheet")
    }

    private fun openReportBugBottomSheet() {
        openHelpFragment()
        onView(withId(R.id.ll_reort_bugs_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_reort_bugs_container)).perform(click())

        onView(withId(R.id.cl_report_bug_container)).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.ll_tor_forum)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_gitlab)).check(matches(isDisplayed()))
        onView(withText(R.string.bug_tor_forum)).check(matches(isDisplayed()))
        onView(withText(R.string.bug_gitlab)).check(matches(isDisplayed()))
    }

    private fun openContactSupportBottomSheet() {
        openHelpFragment()
        onView(withId(R.id.ll_contact_support_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_contact_support_container)).perform(click())

        onView(withId(R.id.cl_contact_support_container)).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.ll_email_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_telegram_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_whatsapp_container)).check(matches(isDisplayed()))
        onView(withId(R.id.ll_signal_container)).check(matches(isDisplayed()))
        onView(withId(R.id.tvAppTitle_email)).check(matches(withText(R.string.email)))
        onView(withId(R.id.tvAppTitle_telegram)).check(matches(withText(R.string.telegram)))
        onView(withId(R.id.tvAppTitle_whats_app)).check(matches(withText(R.string.whatsapp)))
        onView(withId(R.id.tvAppTitle_signal)).check(matches(withText(R.string.signal)))
    }

    private fun openHelpFragment() {
        rule.scenario.onActivity { activity ->
            val navHost = activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            if (navHost.navController.currentDestination?.id != R.id.navigation_help) {
                navHost.navController.navigate(R.id.navigation_help)
            }
        }

        onView(withId(R.id.ll_faq_container)).check(matches(isDisplayed()))
    }
}
