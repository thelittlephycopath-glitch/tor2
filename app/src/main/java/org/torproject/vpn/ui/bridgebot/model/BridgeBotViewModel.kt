package org.torproject.vpn.ui.bridgebot.model

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.R
import org.torproject.vpn.circumvention.SettingsRequest
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.BridgeType
import org.torproject.vpn.vpn.GetBridgeLines

enum class BridgeRequestTransport(val moatName: String, val labelRes: Int) {
    OBFS4("obfs4", R.string.bridge_transport_obfs4),
    WEBTUNNEL("webtunnel", R.string.bridge_transport_webtunnel)
}

class BridgeBotViewModel(
    private val preferenceHelper: PreferenceHelper,
    private val getBridgeLines: GetBridgeLines
) : ViewModel() {

    private val TAG: String = BridgeBotViewModel::class.java.simpleName

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    private var fetchJob: Job? = null

    init {
        events.filterIsInstance<Event.RequestBridges>()
            .onEach { requestBridges() }
            .launchIn(viewModelScope)

        events.filterIsInstance<Event.SetTransport>()
            .onEach { selectTransport(it.transport) }
            .launchIn(viewModelScope)

        events.filterIsInstance<Event.CancelRequest>()
            .onEach { cancelRequest() }
            .launchIn(viewModelScope)

        events.filterIsInstance<Event.SaveBridges>()
            .onEach { saveBridges() }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: Event) = events.tryEmit(event)

    private fun requestBridges() {
        if (_state.value.botState == BotState.FETCHING) return
        _state.update {
            it.copy(
                botState = BotState.FETCHING,
                errorMessage = null
            )
        }

        val requestedTransport = _state.value.selectedTransport
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val fetchedBridges = getBridgeLines(
                    SettingsRequest(transports = listOf(requestedTransport.moatName))
                )
                if (fetchedBridges.isEmpty()) {
                    _state.update {
                        it.copy(
                            bridges = emptyList(),
                            errorMessage = R.string.error_no_bridges_found,
                            botState = BotState.ERROR
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            bridges = fetchedBridges,
                            botState = BotState.SUCCESS
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Error fetching bridges: ${error.message}", error)
                LogObservable.getInstance().addLog("Error fetching bridges: ${error.message}")
                _state.update {
                    it.copy(
                        errorMessage = R.string.error_fetching_bridges,
                        botState = BotState.ERROR
                    )
                }
            }
        }
    }

    private fun selectTransport(transport: BridgeRequestTransport) {
        if (_state.value.botState == BotState.FETCHING ||
            _state.value.botState == BotState.SUCCESS
        ) {
            return
        }
        _state.update {
            it.copy(
                selectedTransport = transport,
                botState = BotState.IDLE,
                bridges = emptyList(),
                errorMessage = null
            )
        }
    }

    private fun cancelRequest() {
        fetchJob?.cancel()
        fetchJob = null
        _state.update {
            it.copy(
                botState = BotState.IDLE,
                errorMessage = null
            )
        }
    }

    private fun saveBridges() {
        val bridgesList = _state.value.bridges
        preferenceHelper.bridgeLines = bridgesList
        preferenceHelper.bridgeType = when {
            bridgesList.isEmpty() -> BridgeType.None
            else -> BridgeType.Manual
        }
    }

    data class State(
        val botState: BotState = BotState.IDLE,
        val bridges: List<String> = emptyList(),
        @StringRes val errorMessage: Int? = null,
        val selectedTransport: BridgeRequestTransport = BridgeRequestTransport.OBFS4
    )

    sealed interface Event {
        data object RequestBridges : Event
        data class SetTransport(val transport: BridgeRequestTransport) : Event
        data object CancelRequest : Event
        data object SaveBridges : Event
    }
}
