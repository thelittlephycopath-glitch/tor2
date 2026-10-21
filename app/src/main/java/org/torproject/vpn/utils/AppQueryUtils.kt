package org.torproject.vpn.utils

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri

fun resolveActivityForUri(packageManager: PackageManager, uriString: String): ActivityInfo? {
    val intent =
        Intent(Intent.ACTION_VIEW, uriString.toUri())
    try {
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        resolveInfo?.let { return it.activityInfo }
    } catch (uoe: UnsupportedOperationException) {
        uoe.printStackTrace()
    }
    return null
}
