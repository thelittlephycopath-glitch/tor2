package org.torproject.vpn.vpn

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.utils.updateDataUsage

enum class ConnectionState {
    // shows 'connect
    INIT,

    // shows 'cancel'
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    CONNECTION_ERROR,
    PRIVATE_DNS_ERROR
}

object VpnStatusObservable {

    val TAG: String = VpnStatusObservable::class.java.simpleName

    private var _statusLiveData = MutableStateFlow(ConnectionState.INIT)
    val statusLiveData: StateFlow<ConnectionState> = _statusLiveData
    private val _dataUsage: MutableStateFlow<DataUsage> = MutableStateFlow(DataUsage())
    val dataUsage: StateFlow<DataUsage> = _dataUsage
    private var _hasInternetConnectivity: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val hasInternetConnectivity: StateFlow<Boolean> = _hasInternetConnectivity

    private var startTime = 0L

    var isAlwaysOnBooting = AtomicBoolean(false)

    fun update(status: ConnectionState) {
        Log.d(TAG, "status update: $status")
        if (status == ConnectionState.CONNECTED) {
            startTime = SystemClock.elapsedRealtime()
        } else if (status == ConnectionState.DISCONNECTED ||
            status == ConnectionState.CONNECTION_ERROR
        ) {
            startTime = 0L
        }

        _statusLiveData.update { status }
        LogObservable.getInstance().addLog(status.toString())
    }

    fun updateInternetConnectivity(isConnected: Boolean) {
        _hasInternetConnectivity.update { isConnected }
    }

    fun updateDataUsage(downstream: Long, upstream: Long) {
        val updatedDataUsage = updateDataUsage(dataUsage.value, downstream, upstream)
        _dataUsage.update { updatedDataUsage }
    }

    fun getStartTimeBase() = startTime
    fun reset() {
        _dataUsage.update { DataUsage() }
    }

    fun isVPNActive(): Boolean {
        val status = _statusLiveData.value
        return status == ConnectionState.CONNECTING || status == ConnectionState.CONNECTED
    }
}
