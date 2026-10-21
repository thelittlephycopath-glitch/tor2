package org.torproject.vpn.ui.configure

import android.view.View

interface ClickHandler {

    fun onAppsClicked(v: View)

    fun onAppIconClicked(v: View)

    fun onTorLogsClicked(v: View)

    fun onAlwaysOnClicked(v: View)

    fun onBridgeSettingsClicked(v: View)

    fun onExitLocationClicked(v: View)

    fun onAboutClicked(v: View)

    fun onPrivacyPolicyClicked(v: View)

    fun onLicencesClicked(v: View)
}
