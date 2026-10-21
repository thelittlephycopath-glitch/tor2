package org.torproject.vpn.ui.configure.model

import android.app.Application
import android.content.SharedPreferences
import android.widget.CompoundButton
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.torproject.vpn.R
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.getDisplayLabel
import org.torproject.vpn.vpn.ConnectionState.CONNECTED
import org.torproject.vpn.vpn.VpnStatusObservable

class ConfigureFragmentViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        val TAG = ConfigureFragmentViewModel::class.java.simpleName
    }
    private val appManager = AppManager(application)
    private val preferenceHelper = PreferenceHelper(application)

    val startOnBoot: StateFlow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (PreferenceHelper.START_ON_BOOT == changedKey) {
                trySend(preferenceHelper.startOnBoot)
            }
        }
        trySend(preferenceHelper.startOnBoot)
        preferenceHelper.registerListener(listener)
        awaitClose { preferenceHelper.unregisterListener(listener) }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        preferenceHelper.startOnBoot
    )

    val verboseLogs: StateFlow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (PreferenceHelper.VERBOSE_LOGGING == changedKey) {
                trySend(preferenceHelper.verboseLogging)
            }
        }
        trySend(preferenceHelper.verboseLogging)
        preferenceHelper.registerListener(listener)
        awaitClose { preferenceHelper.unregisterListener(listener) }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        preferenceHelper.verboseLogging
    )

    val exitNodeCountry: String
        get() = preferenceHelper.exitNodeCountry.locale()?.displayCountry
            ?: application.getString(R.string.exit_location_automatic)

    val selectedBridgeType: String
        get() = preferenceHelper.bridgeType.getDisplayLabel(
            application,
            preferenceHelper.bridgeLines
        )

    val connectionState = VpnStatusObservable.statusLiveData
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = VpnStatusObservable.statusLiveData.value
        )

    val exitNodeSelectionEnabled = connectionState.map { state ->
        return@map state == CONNECTED
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        false
    )

    fun onStartOnBootChanged(compoundButton: CompoundButton, isChecked: Boolean) {
        if (compoundButton.isPressed) {
            preferenceHelper.startOnBoot = isChecked
        }
    }

    private val allProtectedLabel = application.getString(R.string.apps_description_all_protected)
    val appProtectionLabel = appManager.globalRouting.map { routing ->
        application.getString(routing.localizedTitle)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), allProtectedLabel)
}
