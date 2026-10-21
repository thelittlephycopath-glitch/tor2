package org.torproject.vpn.ui.approuting.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.torproject.onionmasq.OnionMasq
import org.torproject.onionmasq.errors.ProxyStoppedException
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.paging.PagingItem
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.distinctPagedFlow
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.VpnStatusObservable

class AppRoutingViewModel(
    private val appManager: AppManager,
    private val startVpn: () -> Unit
) : ViewModel() {

    private val TAG = AppRoutingViewModel::class.java.simpleName

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            appManager.categoryCount.collect { count ->
                _state.update { it.copy(categoryCount = count) }
            }
        }
        viewModelScope.launch {
            appManager.globalRouting.collect { routing ->
                _state.update { it.copy(isProtectAllAppsEnabled = routing.isProtectingAllApps) }
            }
        }
        viewModelScope.launch {
            VpnStatusObservable.statusLiveData.collect { connectionState ->
                _state.update {
                    it.copy(isVPNActive = connectionState == ConnectionState.CONNECTED)
                }
            }
        }

        events.filterIsInstance<Event.ProtectAllApps>()
            .onEach { protectAllApps(it.enabled) }
            .launchIn(viewModelScope)

        events.filterIsInstance<Event.UpdateRouting>()
            .onEach { updateRouting(it.packageName, it.enabled) }
            .launchIn(viewModelScope)

        events.filterIsInstance<Event.RefreshCircuits>()
            .onEach { refreshCircuits() }
            .launchIn(viewModelScope)

        events.filterIsInstance<Event.UpdateExpansionState>()
            .onEach { updateExpansionState(it.category, it.isExpanded) }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: Event) = events.tryEmit(event)

    val torApps = distinctPagedFlow { appManager.pagedTorApps }
        .cachedIn(viewModelScope)

    val nonTorApps = distinctPagedFlow { appManager.pagedNonTorApps }
        .map { pagingData ->
            pagingData.insertSeparators<App, PagingItem> { before, after ->
                if (after == null) return@insertSeparators null
                if (before?.category != after.category) after.category else null
            }
        }
        .cachedIn(viewModelScope)
        .combine(
            state.map { it.categoryExpansionState }.distinctUntilChanged()
        ) { pagingData, expansionMap ->
            pagingData.filter { pagingItem ->
                if (pagingItem !is App) return@filter true
                !pagingItem.category.canExpand || expansionMap.getValue(pagingItem.category)
            }
        }

    private suspend fun protectAllApps(enabled: Boolean) {
        appManager.protectAllApps(enabled)
    }

    private suspend fun updateRouting(packageName: String, enabled: Boolean) {
        appManager.protectApp(enabled, packageName)
        if (VpnStatusObservable.isVPNActive()) {
            startVpn()
        }
    }

    private fun refreshCircuits() {
        try {
            OnionMasq.refreshCircuits()
        } catch (exception: ProxyStoppedException) {
            Log.e(TAG, "Failed to refresh circuit", exception)
        }
    }

    private fun updateExpansionState(category: Category, isExpanded: Boolean) {
        _state.update { state ->
            state.copy(
                categoryExpansionState = state.categoryExpansionState + (category to isExpanded)
            )
        }
    }

    data class State(
        val categoryCount: Map<Category, Int> = emptyMap(),
        val isProtectAllAppsEnabled: Boolean = true,
        val isVPNActive: Boolean = false,
        val categoryExpansionState: Map<Category, Boolean> = Category.entries.associateWith {
            it.isExpandedByDefault
        }
    )

    sealed interface Event {
        data class ProtectAllApps(val enabled: Boolean) : Event
        data class UpdateRouting(val packageName: String, val enabled: Boolean) : Event
        data object RefreshCircuits : Event
        data class UpdateExpansionState(val category: Category, val isExpanded: Boolean) : Event
    }
}
