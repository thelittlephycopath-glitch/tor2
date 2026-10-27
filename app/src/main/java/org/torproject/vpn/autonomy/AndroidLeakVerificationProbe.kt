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
 * Diagnostic facts visible through Android's public network APIs.
 *
 * Route/DNS configuration is not proof of leak resistance: this snapshot deliberately makes no
 * claim about packet enforcement, active DNS resolution, IPv6 leakage, firewall behavior, or
 * per-app allow/disallow rules. Use the separately verified facts to decide what a real verifier
 * still needs to measure.
 */
data class AndroidVpnPathSnapshot(
    val observedAtElapsedRealtimeMillis: Long,
    val vpnTransportActive: Boolean,
    val ipv4DefaultRoutePresent: Boolean,
    val ipv6DefaultRoutePresent: Boolean,
    val expectedTorDnsServersPresent: Boolean,
    val alwaysOnVpnPackageMatches: Boolean,
    val lockdownSettingEnabled: Boolean
) {
    init {
        require(observedAtElapsedRealtimeMillis >= 0L) {
            "Observation timestamp must not be negative"
        }
    }

    val alwaysOnLockdownSettingsConfigured: Boolean
        get() = alwaysOnVpnPackageMatches && lockdownSettingEnabled
}

/**
 * Read-only diagnostic probe. It does not generate network traffic, perform external DNS probes,
 * change routes, or inspect another app's private state.
 */
class AndroidLeakVerificationProbe(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun collectSnapshot(
        observedAtElapsedRealtimeMillis: Long = SystemClock.elapsedRealtime()
    ): AndroidVpnPathSnapshot {
        require(observedAtElapsedRealtimeMillis >= 0L) {
            "Observation timestamp must not be negative"
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

        val alwaysOnPackage = runCatching {
            Settings.Secure.getString(appContext.contentResolver, ALWAYS_ON_VPN_APP_SETTING)
        }.getOrNull()
        val lockdownEnabled = runCatching {
            Settings.Secure.getInt(
                appContext.contentResolver,
                ALWAYS_ON_VPN_LOCKDOWN_SETTING,
                0
            ) == 1
        }.getOrDefault(false)

        return AndroidVpnPathSnapshot(
            observedAtElapsedRealtimeMillis = observedAtElapsedRealtimeMillis,
            vpnTransportActive = vpnNetworkActive,
            ipv4DefaultRoutePresent = vpnNetworkActive && routes.hasDefaultRoute<Inet4Address>(),
            ipv6DefaultRoutePresent = vpnNetworkActive && routes.hasDefaultRoute<Inet6Address>(),
            expectedTorDnsServersPresent = vpnNetworkActive && properties?.hasExpectedTorDnsServers() == true,
            alwaysOnVpnPackageMatches = alwaysOnPackage == appContext.packageName,
            lockdownSettingEnabled = lockdownEnabled
        )
    }

    /**
     * Returns deliberately non-authoritative evidence until independent leak tests are implemented.
     *
     * A route table and the presence of expected DNS server addresses only describe configuration;
     * they cannot prove that packets are contained or that app routing/system lockdown are enforced.
     * Keeping every protection flag false prevents this diagnostic probe from authorizing traffic.
     */
    fun collectEvidence(
        checkedAtElapsedRealtimeMillis: Long = SystemClock.elapsedRealtime()
    ): LeakVerificationEvidence {
        collectSnapshot(checkedAtElapsedRealtimeMillis)
        return LeakVerificationEvidence(
            checkedAtElapsedRealtimeMillis = checkedAtElapsedRealtimeMillis,
            ipv4DefaultRouteProtected = false,
            ipv6ContainedOrBlocked = false,
            dnsProtectedByTunnel = false,
            systemLockdownVerified = false,
            appRoutingVerified = false
        )
    }

    private inline fun <reified T : InetAddress> List<RouteInfo>.hasDefaultRoute(): Boolean =
        any { route ->
            route.destination.prefixLength == 0 && route.destination.address is T
        }

    private fun LinkProperties.hasExpectedTorDnsServers(): Boolean {
        val servers = dnsServers
        if (servers.size != 2) return false
        val expectedIpv4 = byteArrayOf(169.toByte(), 254.toByte(), 42, 53)
        // fe80::53 is exactly 16 bytes: FE 80, thirteen zero bytes, then 53.
        val expectedIpv6 = byteArrayOf(
            0xfe.toByte(), 0x80.toByte(),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x53
        )
        return servers.any { it.address.contentEquals(expectedIpv4) } &&
            servers.any { it.address.contentEquals(expectedIpv6) }
    }

    companion object {
        private const val ALWAYS_ON_VPN_APP_SETTING = "always_on_vpn_app"
        private const val ALWAYS_ON_VPN_LOCKDOWN_SETTING = "always_on_vpn_lockdown"
    }
}
