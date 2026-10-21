package org.torproject.vpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.torproject.vpn.MainActivity
import org.torproject.vpn.utils.PreferenceHelper

class OnBootReceiver : BroadcastReceiver() {

    val TAG = OnBootReceiver::class.java.simpleName
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        context?.let {
            if (PreferenceHelper(context).startOnBoot &&
                !VpnStatusObservable.isAlwaysOnBooting.get()
            ) {
                if (VpnServiceCommand.prepareVpn(context) == null) {
                    VpnServiceCommand.startVpn(context)
                } else {
                    // retry to prepare and start the VPN but with some UI to show possible errors
                    val startIntent = Intent(context, MainActivity::class.java)
                    startIntent.action = MainActivity.ACTION_REQUEST_VPN_PERMISSON
                    startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(startIntent)
                }
            }
        }
    }
}
