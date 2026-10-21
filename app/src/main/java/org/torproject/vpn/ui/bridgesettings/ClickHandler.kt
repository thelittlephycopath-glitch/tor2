package org.torproject.vpn.ui.bridgesettings

import android.view.View

interface ClickHandler {
    fun onManualBridgeSelectionClicked(v: View)
    fun onTorBridgeBotClicked(v: View)
    fun onTelegramBridgeBotClicked(v: View)
    fun onEmailBridgeBotClicked(v: View)
    fun onWebBridgeBotClicked(v: View)
}
