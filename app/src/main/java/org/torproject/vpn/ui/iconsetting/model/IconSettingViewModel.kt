package org.torproject.vpn.ui.iconsetting.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.torproject.vpn.extensions.observe
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.LAUNCHER_CLASS

class IconSettingViewModel(
    private val preferenceHelper: PreferenceHelper,
    private val setLauncherIcon: (Launcher) -> Unit
) : ViewModel() {

    private val currentLauncher = preferenceHelper.observe(LAUNCHER_CLASS, viewModelScope) {
        val selected = preferenceHelper.launcherClass
        Launcher.entries.find { launcher ->
            // Preserve compatibility with old logic that saved class reference path instead of id
            (selected == launcher.id || selected == launcher::class.java.name)
        } ?: LauncherDefault
    }

    private val _state = MutableStateFlow(State(currentLauncher = currentLauncher.value))
    val state: StateFlow<State> = _state.asStateFlow()

    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            currentLauncher.collect { launcher ->
                _state.update { it.copy(currentLauncher = launcher) }
            }
        }

        events.filterIsInstance<Event.SetLauncherIconEvent>()
            .onEach { (launcher) -> setLauncherIcon(launcher) }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: Event) = events.tryEmit(event)

    data class State(
        val currentLauncher: Launcher = LauncherDefault,
        val launchers: List<Launcher> = Launcher.entries
    )

    sealed interface Event {
        data class SetLauncherIconEvent(val launcher: Launcher) : Event
    }
}
