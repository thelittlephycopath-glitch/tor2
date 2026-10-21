package org.torproject.vpn.extensions

import android.content.Context
import android.content.Intent
import org.torproject.vpn.ComposeActivity
import org.torproject.vpn.Constants
import org.torproject.vpn.ui.navigation.Destination
import org.torproject.vpn.utils.Json

/**
 * Navigates to a given compose destination
 */
fun Context.navigateTo(destination: Destination): Intent {
    return Intent(this, ComposeActivity::class.java).also { intent ->
        intent.putExtra(Constants.INTENT_SCREEN_KEY, Json.encodeToString(destination))
        startActivity(intent)
    }
}
