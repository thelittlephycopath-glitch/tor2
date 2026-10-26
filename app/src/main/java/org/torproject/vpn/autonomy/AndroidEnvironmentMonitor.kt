package org.torproject.vpn.autonomy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Observes Android's default-network capabilities only. It does not inspect SSIDs or query
 * destinations, change routes, or create a VPN exception. Unknown networks remain UNKNOWN.
 *
 * This monitor is advisory: callback results update policy facts and invalidate old verification,
 * but cannot enforce an Android lockdown/kill switch.
 */
class AndroidEnvironmentMonitor(
    context: Context,
    private val coordinator: AutonomyCoordinator
) {
    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val lock = Any()

    @Volatile
    private var registered = false

    private var activeNetwork: Network? = null
    private var networkAvailable = false
    private var captivePortalDetected = false
    private var mobileData = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            var shouldInvalidate = false
            var shouldClearPortal = false
            synchronized(lock) {
                if (activeNetwork != network) {
                    shouldInvalidate = networkAvailable
                    shouldClearPortal = captivePortalDetected
                    activeNetwork = network
                    networkAvailable = false
                    captivePortalDetected = false
                    mobileData = false
                }
            }
            if (shouldInvalidate) {
                coordinator.observe(
                    AutonomyEventType.NETWORK_CONNECTIVITY_LOST,
                    source = AutonomyEventSource.NETWORK_MONITOR
                )
            }
            if (shouldClearPortal) {
                coordinator.observe(
                    AutonomyEventType.CAPTIVE_PORTAL_CLEARED,
                    source = AutonomyEventSource.NETWORK_MONITOR
                )
            }
            publishEnvironment()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            var connectivityEvent: AutonomyEventType? = null
            var portalEvent: AutonomyEventType? = null
            synchronized(lock) {
                if (activeNetwork != null && activeNetwork != network) return
                activeNetwork = network
                val portalNow = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
                )
                val validatedNow = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    !portalNow
                val wasAvailable = networkAvailable
                val wasPortal = captivePortalDetected
                networkAvailable = validatedNow
                captivePortalDetected = portalNow
                mobileData = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                if (!wasAvailable && validatedNow) {
                    connectivityEvent = AutonomyEventType.NETWORK_CONNECTIVITY_RESTORED
                } else if (wasAvailable && !validatedNow) {
                    connectivityEvent = AutonomyEventType.NETWORK_CONNECTIVITY_LOST
                }
                if (!wasPortal && portalNow) {
                    portalEvent = AutonomyEventType.CAPTIVE_PORTAL_DETECTED
                } else if (wasPortal && !portalNow) {
                    portalEvent = AutonomyEventType.CAPTIVE_PORTAL_CLEARED
                }
            }
            connectivityEvent?.let {
                coordinator.observe(it, source = AutonomyEventSource.NETWORK_MONITOR)
            }
            portalEvent?.let {
                coordinator.observe(it, source = AutonomyEventSource.NETWORK_MONITOR)
            }
            publishEnvironment()
        }

        override fun onLost(network: Network) {
            val wasActive = synchronized(lock) {
                if (activeNetwork != network) {
                    false
                } else {
                    activeNetwork = null
                    networkAvailable = false
                    captivePortalDetected = false
                    mobileData = false
                    true
                }
            }
            if (wasActive) {
                coordinator.observe(
                    AutonomyEventType.NETWORK_CONNECTIVITY_LOST,
                    source = AutonomyEventSource.NETWORK_MONITOR
                )
                coordinator.observe(
                    AutonomyEventType.CAPTIVE_PORTAL_CLEARED,
                    source = AutonomyEventSource.NETWORK_MONITOR
                )
                publishEnvironment()
            }
        }

        override fun onUnavailable() {
            synchronized(lock) {
                activeNetwork = null
                networkAvailable = false
                captivePortalDetected = false
                mobileData = false
            }
            coordinator.observe(
                AutonomyEventType.NETWORK_CONNECTIVITY_LOST,
                source = AutonomyEventSource.NETWORK_MONITOR
            )
            coordinator.observe(
                AutonomyEventType.CAPTIVE_PORTAL_CLEARED,
                source = AutonomyEventSource.NETWORK_MONITOR
            )
            publishEnvironment()
        }
    }

    @Synchronized
    fun register() {
        if (registered) return
        val manager = connectivityManager
        if (manager == null) {
            coordinator.observe(
                AutonomyEventType.NETWORK_MONITOR_UNAVAILABLE,
                source = AutonomyEventSource.NETWORK_MONITOR
            )
            return
        }
        try {
            manager.registerDefaultNetworkCallback(networkCallback)
            registered = true
            publishEnvironment()
        } catch (_: RuntimeException) {
            registered = false
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            // A monitor failure is recorded without crashing the VPN service or failing open.
            coordinator.observe(
                AutonomyEventType.NETWORK_MONITOR_UNAVAILABLE,
                source = AutonomyEventSource.NETWORK_MONITOR
            )
        }
    }

    @Synchronized
    fun unregister() {
        if (!registered) return
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        registered = false
        synchronized(lock) {
            activeNetwork = null
            networkAvailable = false
            captivePortalDetected = false
            mobileData = false
        }
        publishEnvironment()
    }

    private fun publishEnvironment() {
        val state = synchronized(lock) {
            Triple(networkAvailable, captivePortalDetected, mobileData)
        }
        coordinator.updateNetworkEnvironment(
            networkAvailable = state.first,
            captivePortalDetected = state.second,
            mobileData = state.third
        )
    }
}
