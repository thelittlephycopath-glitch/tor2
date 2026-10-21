package org.torproject.vpn.ui.exitselection.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.torproject.onionmasq.OnionMasq
import org.torproject.vpn.extensions.observe
import org.torproject.vpn.utils.Country
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.EXIT_NODE_COUNTRY

class ExitSelectionViewModel(private val preferenceHelper: PreferenceHelper) : ViewModel() {

    private val TAG = ExitSelectionViewModel::class.java.simpleName

    private val currentExit = preferenceHelper.observe(EXIT_NODE_COUNTRY, viewModelScope) {
        preferenceHelper.exitNodeCountry
    }

    private val relayCountries = preferenceHelper.relayCountries
    private val availableExits: StateFlow<List<Country>> = MutableStateFlow(
        if (relayCountries.isEmpty()) {
            Country.entries
        } else {
            Country.entries.filter { country ->
                country == Country.RANDOM || country.code in relayCountries
            }
        }
    )

    val state: StateFlow<State> = combine(
        currentExit,
        availableExits
    ) { currentExit, availableExits ->
        State(currentExit = currentExit, availableExits = availableExits)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = State()
    )

    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    init {
        events
            .filterIsInstance<Event.SelectExit>()
            .onEach { selectExit -> setExit(selectExit.country) }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: Event) = events.tryEmit(event)

    private fun setExit(country: Country) {
        try {
            when (country) {
                Country.RANDOM -> OnionMasq.setCountryCode(null)
                else -> OnionMasq.setCountryCode(country.code)
            }
            OnionMasq.refreshCircuits()
            preferenceHelper.exitNodeCountry = country
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to set exit for ${country.code}", exception)
        }
    }

    data class State(
        val currentExit: Country = Country.RANDOM,
        val availableExits: List<Country> = emptyList()
    )

    sealed interface Event {
        data class SelectExit(val country: Country) : Event
    }
}
