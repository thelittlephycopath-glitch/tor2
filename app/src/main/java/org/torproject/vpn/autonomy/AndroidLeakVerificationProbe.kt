package org.torproject.vpn.autonomy

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.SystemClock
import android.provider.Settings
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Read-only snapshot of facts the Android public APIs expose about the current VPN network.
 *
 * It does not generate network traffic, perform external DNS tests, change routes, or inspect
 * another app's private state. App-specific routing is left unverified because this app cannot
 * independently prove per-app routing through these APIs alone.
 */
class AndroidLeakVerificationProbe(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun collectEvidence(
        checkedAtElapsedRealtimeMillis: Long = SystemClock.elapsedRealtime()
    ): LeakVerificationEvidence {
        require(checkedAtElapsedRealtimeMillis >= 0L) {
            "Verification timestamp must not be negative"
        }

        val manager = connectivityManager
        val activeNetwork = runCatching { manager?.activeNetwork }.getOrNull()
        val capabilities = activeNetwork?.let { network ->
            runCatching { manager?.getNetworkCapabilities(network) }.getOrNull()
        }
        val properties = activeNetwork?.let { network ->
            runCatching { manager?.getLinkProperties(network) }.getOrNull()
        }
        val vpnNetworkActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val routes = properties?.routes.orEmpty()
        val ipv4DefaultRouteProtected = vpnNetworkActive && routes.hasDefaultRoute<Inet4Address>()
        val ipv6ContainedOrBlocked = vpnNetworkActive && routes.hasDefaultRoute<Inet6Address>()
        val dnsProtectedByTunnel = vpnNetworkActive &&
            ipv4DefaultRouteProtected &&
            properties?.hasExpectedTorDnsServers() == true

        val lockdownVerified = readPlatformLockdownState()

        return LeakVerificationEvidence(
            checkedAtElapsedRealtimeMillis = checkedAtElapsedRealtimeMillis,
            ipv4DefaultRouteProtected = ipv4DefaultRouteProtected,
            ipv6ContainedOrBlocked = ipv6ContainedOrBlocked,
            dnsProtectedByTunnel = dnsProtectedByTunnel,
            systemLockdownVerified = lockdownVerified,
            // Android doesn't expose a reliable read-back of this app's full allow/disallow list.
            // Do not infer per-app routing success from the existence of default routes.
            appRoutingVerified = false
        )
    }

    private fun readPlatformLockdownState(): Boolean {
        val alwaysOnPackage = runCatching {
            Settings.Secure.getString(
                appContext.contentResolver,
                ALWAYS_ON_VPN_APP_SETTING
            )
        }.getOrNull()
        val lockdownEnabled = runCatching {
            Settings.Secure.getInt(
                appContext.contentResolver,
                ALWAYS_ON_VPN_LOCKDOWN_SETTING,
                0
            ) == 1
        }.getOrDefault(false)
        return alwaysOnPackage == appContext.packageName && lockdownEnabled
    }

    private inline fun <reified T : InetAddress> List<RouteInfo>.hasDefaultRoute(): Boolean =
        any { route ->
            route.destination.prefixLength == 0 && route.destination.address is T
        }

    private fun LinkProperties.hasExpectedTorDnsServers(): Boolean {
        val servers = dnsServers
        if (servers.size != 2) return false
        val expectedIpv4 = byteArrayOf(169.toByte(), 254.toByte(), 42, 53)
        val expectedIpv6 = byteArrayOf(
            0xfe.toByte(), 0x80.toByte(),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0x53
        )
        return servers.any { it.address.contentEquals(expectedIpv4) } &&
            servers.any { it.address.contentEquals(expectedIpv6) }
    }

    companion object {
        private const val ALWAYS_ON_VPN_APP_SETTING = "always_on_vpn_app"
        private const val ALWAYS_ON_VPN_LOCKDOWN_SETTING = "always_on_vpn_lockdown"
    }
}
