package org.torproject.vpn.ui.appsearch.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.torproject.vpn.database.app.App
import org.torproject.vpn.extensions.observe
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.RECENT_SEARCHES
import org.torproject.vpn.utils.distinctPagedFlow

@OptIn(ExperimentalCoroutinesApi::class, SavedStateHandleSaveableApi::class)
class AppSearchViewModel(
    private val appManager: AppManager,
    private val preferenceHelper: PreferenceHelper,
    private val startVpn: () -> Unit,
    isVoiceSearchAvailable: Boolean,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Text of the search field. Owned by the ViewModel so the screen stays stateless, and
     * persisted through [SavedStateHandle].
     */
    val queryState: TextFieldState = savedStateHandle.saveable(
        key = QUERY_KEY,
        saver = TextFieldState.Saver
    ) { TextFieldState() }

    val searchResults: Flow<PagingData<App>> = snapshotFlow { queryState.text.toString().trim() }
        .distinctUntilChanged()
        .flatMapLatest { query -> distinctPagedFlow { appManager.search(query) } }
        .cachedIn(viewModelScope)

    val state: StateFlow<State> = preferenceHelper
        .observe(RECENT_SEARCHES, viewModelScope) { preferenceHelper.recentSearches }
        .map { queries ->
            State(recentQueries = queries, isVoiceSearchAvailable = isVoiceSearchAvailable)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = State(
                recentQueries = preferenceHelper.recentSearches,
                isVoiceSearchAvailable = isVoiceSearchAvailable
            )
        )

    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    init {
        events
            .filterIsInstance<Event.SubmitSearch>()
            .onEach { submitSearch(it.query) }
            .launchIn(viewModelScope)

        events
            .filterIsInstance<Event.ClearQuery>()
            .onEach { queryState.clearText() }
            .launchIn(viewModelScope)

        events
            .filterIsInstance<Event.OpenApp>()
            .onEach { saveSearchQuery(it.appName) }
            .launchIn(viewModelScope)

        events
            .filterIsInstance<Event.UpdateRouting>()
            .onEach { updateRouting(it.packageName, it.enabled) }
            .launchIn(viewModelScope)

        events
            .filterIsInstance<Event.ClearSearchQueries>()
            .onEach { clearSearchQueries() }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: Event) = events.tryEmit(event)

    private fun submitSearch(rawQuery: String) {
        val query = rawQuery.trim()
        queryState.setTextAndPlaceCursorAtEnd(query)
        saveSearchQuery(query)
    }

    private suspend fun updateRouting(packageName: String, enabled: Boolean) {
        appManager.protectApp(enabled, packageName)
        startVpn()
    }

    private fun saveSearchQuery(rawQuery: String) {
        val query = rawQuery.trim()
        val currentQueries = preferenceHelper.recentSearches
        // We don't want to save empty or duplicate queries
        if (query.isBlank() || currentQueries.any { it.equals(query, ignoreCase = true) }) {
            return
        }

        val recentSearches = currentQueries.toMutableList().apply {
            add(0, query)
        }
        val limitedSearches = when {
            recentSearches.size >= MAX_RECENT_SEARCHES -> recentSearches.take(MAX_RECENT_SEARCHES)
            else -> recentSearches
        }

        preferenceHelper.recentSearches = limitedSearches
    }

    private fun clearSearchQueries() {
        preferenceHelper.recentSearches = emptyList()
    }

    data class State(
        val recentQueries: List<String> = emptyList(),
        val isVoiceSearchAvailable: Boolean = false
    )

    sealed interface Event {
        data class SubmitSearch(val query: String) : Event
        data object ClearQuery : Event
        data class OpenApp(val appName: String) : Event
        data class UpdateRouting(val packageName: String, val enabled: Boolean) : Event
        data object ClearSearchQueries : Event
    }

    private companion object {
        const val QUERY_KEY = "query"
        const val MAX_RECENT_SEARCHES = 5
    }
}
