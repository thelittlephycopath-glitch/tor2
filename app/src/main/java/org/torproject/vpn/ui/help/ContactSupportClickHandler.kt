package org.torproject.vpn.ui.help

import android.view.View

interface ContactSupportClickHandler {
    fun onWhatsappClicked(v: View)
    fun onSignalClicked(v: View)
    fun onTelegramClicked(v: View)
    fun onEmailClicked(v: View)
}
