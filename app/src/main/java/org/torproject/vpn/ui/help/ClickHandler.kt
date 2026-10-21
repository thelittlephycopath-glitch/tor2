package org.torproject.vpn.ui.help

import android.view.View

interface ClickHandler {
    fun onOfflineHelpClicked(v: View)
    fun onReportBugClicked(v: View)
    fun onContactSupportClicked(v: View)
}
