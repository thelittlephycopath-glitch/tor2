package org.torproject.vpn.ui.betainfo

import android.view.View

interface ClickHandler {
    fun onReportBugsClicked()
    fun onLearnMoreClicked(v: View)
    fun onStartTestingClicked(v: View)
}
