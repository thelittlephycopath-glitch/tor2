package org.torproject.vpn.ui.home.model

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.torproject.onionmasq.ConnectivityHandler
import org.torproject.vpn.BuildConfig
import org.torproject.vpn.R
import org.torproject.vpn.extensions.observe
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.Country
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.EXIT_NODE_COUNTRY
import org.torproject.vpn.utils.PreferenceHelper.Companion.SHOULD_SHOW_GUIDE
import org.torproject.vpn.utils.getDisplayLabel
import org.torproject.vpn.vpn.ConnectionState.CONNECTED
import org.torproject.vpn.vpn.ConnectionState.CONNECTING
import org.torproject.vpn.vpn.ConnectionState.CONNECTION_ERROR
import org.torproject.vpn.vpn.ConnectionState.DISCONNECTED
import org.torproject.vpn.vpn.ConnectionState.DISCONNECTING
import org.torproject.vpn.vpn.ConnectionState.INIT
import org.torproject.vpn.vpn.ConnectionState.PRIVATE_DNS_ERROR
import org.torproject.vpn.vpn.DataUsage
import org.torproject.vpn.vpn.VpnServiceCommand
import org.torproject.vpn.vpn.VpnStatusObservable

/**
 * ViewModel for slider fragment, mostly place holder at this point
 */
const val ACTION_LOGS = 110
const val ACTION_REQUEST_NOTIFICATION_PERMISSION = 111
const val ACTION_EXIT_NODE_SELECTION = 113
const val ACTION_APPS = 114
const val ACTION_CONNECTION = 115

class ConnectFragmentViewModel(
    private val application: Application
) : AndroidViewModel(application) {

    private val _prepareVpn = MutableLiveData<Intent?>()
    private val appManager = AppManager(application)
    private val preferenceHelper = PreferenceHelper(application)
    private val connectivityHandler = ConnectivityHandler(application)

    val prepareVpn: LiveData<Intent?> = _prepareVpn

    private val dataUsage = VpnStatusObservable.dataUsage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = DataUsage()
        )

    // Total data
    val dataUsageDownstream: StateFlow<String> = dataUsage.map { data ->
        return@map application.getString(R.string.stats_down, data.formattedDownstreamData)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ""
    )

    val dataUsageUpstream: StateFlow<String> = dataUsage.map { data ->
        application.getString(R.string.stats_up, data.formattedUpstreamData)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ""
    )

    // Rate
    val dataUsageDownstreamRate: StateFlow<String> = dataUsage.map { data ->
        return@map "+${data.formattedDownstreamDataPerSec}"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ""
    )

    val dataUsageUpstreamRate: StateFlow<String> = dataUsage.map { data ->
        return@map "+${data.formattedUpstreamDataPerSec}"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ""
    )

    // bit silly, but the views contain these confusing names:
    val dataUsageDiffUpstream: StateFlow<String>
        get() = dataUsageUpstreamRate

    val dataUsageDiffDownstream: StateFlow<String>
        get() = dataUsageDownstreamRate

    val connectionState = VpnStatusObservable.statusLiveData
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = VpnStatusObservable.statusLiveData.value
        )

    private val initState = State()
    val state = connectionState.scan(initState) { past, current ->
        State(previous = past.current, current = current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initState)

    fun checkIsPrivateDnsEnforced() {
        if (connectivityHandler.isPrivateDnsEnforced) {
            VpnStatusObservable.update(PRIVATE_DNS_ERROR)
        } else {
            if (state.value.current == PRIVATE_DNS_ERROR) {
                when (state.value.previous) {
                    INIT -> VpnStatusObservable.update(INIT)
                    else -> VpnStatusObservable.update(DISCONNECTED)
                }
            }
        }
    }

    init {
        checkIsPrivateDnsEnforced()
    }

    val internetConnectivity = VpnStatusObservable.hasInternetConnectivity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = VpnStatusObservable.hasInternetConnectivity.value
        )

    val toolBarTitle: StateFlow<String> = combine(
        state,
        internetConnectivity
    ) { state, inetConnectivity ->
        return@combine when (state.current) {
            INIT -> application.getString(R.string.idle_toolbar_title)
            CONNECTING -> if (inetConnectivity) {
                application.getString(R.string.state_connecting)
            } else {
                application.getString(R.string.no_internet)
            }

            CONNECTED -> if (inetConnectivity) {
                application.getString(R.string.state_connected)
            } else {
                application.getString(R.string.no_internet)
            }

            DISCONNECTING -> if (inetConnectivity) {
                application.getString(R.string.state_disconnecting)
            } else {
                application.getString(R.string.no_internet)
            }

            DISCONNECTED -> application.getString(R.string.state_disconnected)
            PRIVATE_DNS_ERROR -> {
                if (state.previous == INIT) {
                    application.getString(R.string.idle_toolbar_title)
                } else {
                    application.getString(R.string.state_disconnected)
                }
            }

            else -> null
        }
    }.filterNotNull().stateIn(viewModelScope, SharingStarted.Lazily, "")

    val connectButtonText: StateFlow<String> = state.mapNotNull { state ->
        when (state.current) {
            INIT -> application.getString(R.string.action_connect)
            CONNECTING -> application.getString(R.string.action_cancel)
            DISCONNECTING -> application.getString(R.string.action_reconnect)
            DISCONNECTED -> application.getString(R.string.action_reconnect)
            CONNECTION_ERROR -> application.getString(R.string.action_try_again)
            PRIVATE_DNS_ERROR -> {
                if (state.previous == INIT) {
                    application.getString(R.string.action_connect)
                } else {
                    application.getString(R.string.action_reconnect)
                }
            }
            else -> null
        }
    }.stateIn(scope = viewModelScope, SharingStarted.WhileSubscribed(), initialValue = "")

    val connectButtonContentDescription: StateFlow<String> = state.map { state ->
        when (state.current) {
            INIT -> application.getString(R.string.action_connect) + ". " +
                application.getString(R.string.current_state) +
                " " +
                application.getString(R.string.state_disconnected)

            CONNECTING -> application.getString(R.string.action_cancel) + ". " +
                application.getString(R.string.current_state) +
                " " +
                application.getString(R.string.state_connecting)

            DISCONNECTING -> application.getString(R.string.action_reconnect) + ". " +
                application.getString(R.string.current_state) +
                " " +
                application.getString(R.string.state_disconnecting)

            DISCONNECTED -> application.getString(R.string.action_reconnect) + ". " +
                application.getString(R.string.current_state) +
                " " +
                application.getString(R.string.state_disconnected)

            CONNECTION_ERROR -> application.getString(R.string.action_try_again) + ". " +
                application.getString(R.string.current_state) +
                " " +
                application.getString(R.string.state_disconnected)

            CONNECTED -> application.getString(R.string.action_stop) + ". " +
                application.getString(R.string.current_state) +
                " " +
                application.getString(R.string.state_connected)

            PRIVATE_DNS_ERROR -> {
                if (state.previous == INIT) {
                    application.getString(R.string.action_connect) + ". " +
                        application.getString(R.string.current_state) +
                        " " +
                        application.getString(R.string.state_disconnected)
                } else {
                    application.getString(R.string.action_reconnect) + ". " +
                        application.getString(R.string.current_state) +
                        " " +
                        application.getString(R.string.state_disconnected)
                }
            }
        }
    }.stateIn(scope = viewModelScope, SharingStarted.WhileSubscribed(), initialValue = "")

    private val selectedCountry = preferenceHelper.observe(EXIT_NODE_COUNTRY, viewModelScope) {
        preferenceHelper.exitNodeCountry
    }

    val countryLabel = selectedCountry.map { country ->
        return@map country.locale()?.displayCountry
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val countryDrawable = selectedCountry.map { country ->
        return@map when (country) {
            Country.RANDOM -> null
            else -> ContextCompat.getDrawable(application, country.flag)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _connectionHint: MutableLiveData<String> = MutableLiveData(getConnectionString())
    val connectionHint: LiveData<String> = _connectionHint

    // these are static one-time-fetch values on viewModel init. Don't need to be LiveData or StateFlow.
    val flavor = "Pre-alpha"
    val version = BuildConfig.VERSION_NAME

    private val _action = MutableSharedFlow<Int>(replay = 0)

    val action: SharedFlow<Int>
        get() = _action

    val guideScreenVisibility = callbackFlow {
        val listener = OnSharedPreferenceChangeListener { _, changedKey ->
            if (SHOULD_SHOW_GUIDE == changedKey) {
                trySend(preferenceHelper.shouldShowGuide)
            }
        }
        trySend(preferenceHelper.shouldShowGuide)
        preferenceHelper.registerListener(listener)
        awaitClose { preferenceHelper.unregisterListener(listener) }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        preferenceHelper.shouldShowGuide
    )

    val appCardVisibility: StateFlow<Boolean> = connectionState.map { connectionState ->
        connectionState == INIT || connectionState == CONNECTED
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            false
        )

    val connectionCardVisibility: StateFlow<Boolean> = connectionState.map { connectionState ->
        connectionState == INIT
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            false
        )

    val exitNodeSelectionVisibility = connectionState.map { state ->
        return@map if (state == CONNECTED) View.VISIBLE else View.GONE
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        View.GONE
    )
    val exitNodeSelectionContentDescription = selectedCountry.map { country ->
        if (country == Country.RANDOM) {
            application.getString(R.string.exit_location) + ", " +
                application.getString(R.string.exit_location_automatic)
        } else {
            application.getString(R.string.exit_location) + ", " +
                country.locale()!!.displayCountry
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        application.getString(R.string.exit_location)
    )

    fun connectStateButtonClicked() {
        when (VpnStatusObservable.statusLiveData.value) {
            INIT -> attemptConnect()
            CONNECTING -> attemptCancel()
            CONNECTED -> attemptDisconnect()
            CONNECTION_ERROR -> attemptConnect()
            DISCONNECTING -> attemptConnect()
            PRIVATE_DNS_ERROR, DISCONNECTED -> attemptConnect()
        }
    }

    fun exitNodeSelectionButtonClicked() {
        viewModelScope.launch {
            _action.emit(ACTION_EXIT_NODE_SELECTION)
        }
    }

    fun viewLogsClicked() {
        viewModelScope.launch {
            _action.emit(ACTION_LOGS)
        }
    }

    fun appCardClicked() {
        viewModelScope.launch {
            _action.emit(ACTION_APPS)
        }
    }

    fun connectionCardClicked() {
        viewModelScope.launch {
            _action.emit(ACTION_CONNECTION)
        }
    }

    val allAppsProtected = appManager.globalRouting.map { routing ->
        routing.isProtectingAllApps
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    private val allProtectedLabel = application.getString(R.string.apps_description_all_protected)
    val appProtectionLabel = appManager.globalRouting.map { routing ->
        application.getString(routing.localizedTitle)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), allProtectedLabel)

    fun onProtectAppsChanged() {
        viewModelScope.launch {
            appManager.protectAllApps(!allAppsProtected.value)
            if (VpnStatusObservable.isVPNActive()) {
                VpnServiceCommand.startVpn(getApplication())
            }
        }
    }

    private fun attemptCancel() {
        VpnStatusObservable.update(DISCONNECTING)
        VpnServiceCommand.stopVpn(getApplication())
    }

    private fun attemptConnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            VpnStatusObservable.update(CONNECTING)
            prepareToStartVPN()
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            VpnStatusObservable.update(CONNECTING)
            prepareToStartVPN()
        } else {
            viewModelScope.launch {
                _action.emit(ACTION_REQUEST_NOTIFICATION_PERMISSION)
            }
        }
    }

    private fun attemptDisconnect() {
        VpnStatusObservable.update(DISCONNECTING)
        VpnServiceCommand.stopVpn(getApplication())
    }

    fun prepareToStartVPN() {
        val vpnIntent: Intent? = VpnServiceCommand.prepareVpn(getApplication())
        if (vpnIntent != null) {
            _prepareVpn.postValue(vpnIntent)
        } else {
            VpnServiceCommand.startVpn(getApplication())
        }
    }

    fun updateConnectionLabel() {
        _connectionHint.postValue(getConnectionString())
    }

    private fun getConnectionString(): String {
        return preferenceHelper.bridgeType.getDisplayLabel(
            application,
            preferenceHelper.bridgeLines
        )
    }

    fun onVpnPrepared() {
        _prepareVpn.value = null
    }

    fun onNotificationPermissionResult() {
        VpnStatusObservable.update(CONNECTING)
        prepareToStartVPN()
    }

    fun openSettings() = Intent(Settings.ACTION_WIRELESS_SETTINGS).also { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
}
