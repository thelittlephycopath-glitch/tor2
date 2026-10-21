package org.torproject.vpn.ui.appdetail.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.torproject.onionmasq.OnionMasq
import org.torproject.onionmasq.circuit.CircuitCountryCodes
import org.torproject.onionmasq.errors.ProxyStoppedException
import org.torproject.vpn.database.app.App
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.AppLauncher
import org.torproject.vpn.utils.updateDataUsage
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.DataUsage
import org.torproject.vpn.vpn.VpnStatusObservable

class AppDetailViewModel(
    private val appManager: AppManager,
    private val packageName: String,
    private val startVpn: () -> Unit,
    private val appLauncher: AppLauncher
) : ViewModel() {

    private val TAG = AppDetailViewModel::class.java.simpleName

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            appManager.getApp(packageName).collect { app ->
                _state.update {
                    it.copy(
                        app = app,
                        canOpenApp = appLauncher.canLaunch(app)
                    )
                }
            }
        }
        viewModelScope.launch {
            VpnStatusObservable.statusLiveData.collect { connectionState ->
                _state.update {
                    it.copy(isVPNActive = connectionState == ConnectionState.CONNECTED)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                _state.value.app?.let { app ->
                    val dataUsage = updateDataUsage(
                        _state.value.dataUsage,
                        OnionMasq.getBytesReceivedForApp(app.uid.toLong()),
                        OnionMasq.getBytesSentForApp(app.uid.toLong())
                    )
                    val circuits = OnionMasq.getCircuitCountryCodesForAppUid(app.uid)
                    _state.update { it.copy(dataUsage = dataUsage, circuits = circuits) }
                }
                delay(1.seconds)
            }
        }

        events
            .filterIsInstance<Event.UpdateRouting>()
            .onEach { updateRouting(it.enabled) }
            .launchIn(viewModelScope)

        events
            .filterIsInstance<Event.OpenApp>()
            .onEach { appLauncher.invoke(it.app) }
            .launchIn(viewModelScope)

        events
            .filterIsInstance<Event.RefreshCircuit>()
            .onEach { refreshCircuit() }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: Event) = events.tryEmit(event)

    private suspend fun updateRouting(enabled: Boolean) {
        appManager.protectApp(enabled, packageName)
        startVpn()
    }

    private fun refreshCircuit() {
        try {
            _state.value.app?.let { app ->
                OnionMasq.refreshCircuitsForApp(app.uid.toLong())
            }
        } catch (exception: ProxyStoppedException) {
            Log.e(TAG, "Failed to refresh circuit", exception)
        }
    }

    data class State(
        val app: App? = null,
        val circuits: List<CircuitCountryCodes> = emptyList(),
        val dataUsage: DataUsage = DataUsage(),
        val isVPNActive: Boolean = false,
        val canOpenApp: Boolean = false
    )

    sealed interface Event {
        data class UpdateRouting(val enabled: Boolean) : Event
        data class OpenApp(val app: App) : Event
        data object RefreshCircuit : Event
    }
}
