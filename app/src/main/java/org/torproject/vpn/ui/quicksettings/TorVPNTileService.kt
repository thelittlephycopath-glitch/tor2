package org.torproject.vpn.ui.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.torproject.vpn.MainActivity
import org.torproject.vpn.MainActivity.Companion.ACTION_REQUEST_VPN_PERMISSON
import org.torproject.vpn.R
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.ConnectionState.CONNECTED
import org.torproject.vpn.vpn.ConnectionState.CONNECTING
import org.torproject.vpn.vpn.ConnectionState.CONNECTION_ERROR
import org.torproject.vpn.vpn.ConnectionState.DISCONNECTED
import org.torproject.vpn.vpn.ConnectionState.DISCONNECTING
import org.torproject.vpn.vpn.ConnectionState.INIT
import org.torproject.vpn.vpn.VpnServiceCommand
import org.torproject.vpn.vpn.VpnStatusObservable

class TorVPNTileService : TileService() {

    private val observerCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnStateFlow: StateFlow<Pair<ConnectionState, Boolean>> = combine(
        VpnStatusObservable.statusLiveData,
        VpnStatusObservable.hasInternetConnectivity
    ) { status, hasInternet ->
        return@combine Pair(status, hasInternet)
    }.stateIn(
        scope = observerCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = Pair(
            VpnStatusObservable.statusLiveData.value,
            VpnStatusObservable.hasInternetConnectivity.value
        )
    )
    private var collectionJob: Job? = null

    @SuppressLint("Override")
    override fun onClick() {
        super.onClick()
        if (!isLocked) {
            onTileTap()
        } else {
            unlockAndRun { onTileTap() }
        }
    }

    private fun onTileTap() {
        when (VpnStatusObservable.statusLiveData.value) {
            CONNECTING, CONNECTED -> {
                VpnServiceCommand.stopVpn(this)
            }
            CONNECTION_ERROR -> {
                startMainActivity(ACTION_MAIN)
            }
            else -> {
                if (VpnServiceCommand.prepareVpn(this) == null) {
                    VpnServiceCommand.startVpn(this)
                    VpnStatusObservable.update(CONNECTING)
                } else {
                    startMainActivity(ACTION_REQUEST_VPN_PERMISSON)
                }
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startMainActivity(action: String) {
        // retry to prepare and start the VPN but with some UI to show possible errors
        val startIntent = Intent(this.applicationContext, MainActivity::class.java)
        startIntent.action = action
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            this.startActivityAndCollapse(startIntent)
        } else {
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(this, 0, startIntent, flags)
            this.startActivityAndCollapse(pendingIntent)
        }
    }

    override fun onTileAdded() {
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TILE_TOR_VPN", "On Create")
    }

    private fun onStatusChanged(connectionState: ConnectionState, hasInternet: Boolean) {
        val t = qsTile ?: return
        Log.d("TILE_TOR_VPN", "CONNECTION STATE CHANGED: ${connectionState.name}")
        when (connectionState) {
            INIT,
            DISCONNECTED -> {
                t.state = Tile.STATE_INACTIVE
                t.label = getString(R.string.app_name)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    t.subtitle = getString(R.string.state_disconnected)
                }
            }
            CONNECTING -> {
                t.state = Tile.STATE_ACTIVE
                t.label = getString(R.string.app_name)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    t.subtitle = if (hasInternet) {
                        getString(R.string.state_connecting)
                    } else {
                        getString(R.string.no_internet)
                    }
                }
            }
            DISCONNECTING,
            CONNECTED -> {
                t.state = Tile.STATE_ACTIVE
                t.label = getString(R.string.app_name)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    t.subtitle = if (hasInternet) {
                        getString(R.string.state_connected)
                    } else {
                        getString(R.string.no_internet)
                    }
                }
            }
            CONNECTION_ERROR -> {
                t.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    t.label = getString(R.string.app_name)
                    t.subtitle = getString(R.string.qs_error)
                } else {
                    t.label = getString(R.string.qs_show_error)
                }
            }
            else -> {
                t.state = Tile.STATE_UNAVAILABLE
                t.label = getString(R.string.app_name)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    t.subtitle = ""
                }
            }
        }
        t.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground_qs)
        t.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d("TILE_TOR_VPN", "onStartListening")
        collectionJob?.cancel()
        collectionJob = this.observerCoroutineScope.launch {
            vpnStateFlow.collect {
                onStatusChanged(it.first, it.second)
            }
        }
        onStatusChanged(
            VpnStatusObservable.statusLiveData.value,
            VpnStatusObservable.hasInternetConnectivity.value
        )
    }

    override fun onStopListening() {
        collectionJob?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        collectionJob?.cancel()
        observerCoroutineScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
        Log.d("TILE_TOR_VPN", "onDestroy")
    }
}
