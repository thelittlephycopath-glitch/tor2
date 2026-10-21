package org.torproject.vpn.utils

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import org.hamcrest.Matchers
import org.torproject.vpn.R
import org.torproject.vpn.utils.CustomInteractions.tryResolve
import org.torproject.vpn.vpn.VpnStatusObservable
import tools.fastlane.screengrab.Screengrab

class ConnectHelper {

    companion object {
        fun connect(device: UiDevice?, takeScreenshots: Boolean) {
            if (VpnStatusObservable.isVPNActive()) {
                return
            }
            val preferenceHelper = PreferenceHelper(ApplicationProvider.getApplicationContext())
            preferenceHelper.shouldShowGuide = false
            // determine required permissions
            var needsNotificationPermission = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        ApplicationProvider.getApplicationContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    needsNotificationPermission = true
                }
            }
            val needsVpnPermission =
                VpnService.prepare(ApplicationProvider.getApplicationContext()) != null

            // start VPN
            onView(withId(R.id.tv_connect_action_btn)).perform(click())

            if (needsNotificationPermission) {
                // handle Notification permission dialog
                val okButton: UiObject = device!!.findObject(UiSelector().text("Allow"))
                okButton.click()
            }

            if (needsVpnPermission) {
                // handle VPN permission dialog
                val okButton = device!!.findObject(
                    UiSelector().packageName(
                        "com.android.vpndialogs"
                    ).resourceId("android:id/button1")
                )
                okButton.click()
            }

            tryResolve(
                onView(withId(R.id.toolbar)),
                matches(
                    Matchers.anyOf(
                        hasDescendant(withText(R.string.state_connecting)),
                        hasDescendant(withText(R.string.state_connected))
                    )
                ),
                5
            )

            val isStillConnecting = runCatching {
                onView(withId(R.id.tv_connect_action_btn)).check(
                    matches(withText(R.string.action_cancel))
                )
            }.isSuccess
            if (takeScreenshots && isStillConnecting) {
                Screengrab.screenshot("connect_fragment_connecting_state")
            }

            tryResolve(
                onView(withId(R.id.toolbar)),
                matches(hasDescendant(withText(R.string.state_connected))),
                120
            )
            if (takeScreenshots) {
                Screengrab.screenshot("connect_fragment_connected_state")
            }
        }
    }
}
