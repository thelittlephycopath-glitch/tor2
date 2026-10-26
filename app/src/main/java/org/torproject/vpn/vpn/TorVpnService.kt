package org.torproject.vpn.vpn

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
import android.net.VpnService
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.SystemClock
import android.os.Messenger
import android.system.OsConstants
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.lifecycle.Observer
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.torproject.onionmasq.ConnectivityHandler
import org.torproject.onionmasq.OnionMasq
import org.torproject.onionmasq.errors.CountryCodeException
import org.torproject.onionmasq.events.BootstrapEvent
import org.torproject.onionmasq.events.ClosedConnectionEvent
import org.torproject.onionmasq.events.ConnectivityEvent
import org.torproject.onionmasq.events.DNSConnectivityEvent
import org.torproject.onionmasq.events.FailedConnectionEvent
import org.torproject.onionmasq.events.NewConnectionEvent
import org.torproject.onionmasq.events.NewDirectoryEvent
import org.torproject.onionmasq.events.OnionmasqEvent
import org.torproject.onionmasq.logging.LogHelper
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.BuildConfig
import org.torproject.vpn.R
import org.torproject.vpn.autonomy.AndroidEnvironmentMonitor
import org.torproject.vpn.autonomy.AndroidResourceMonitor
import org.torproject.vpn.autonomy.AutonomyCoordinator
import org.torproject.vpn.autonomy.AutonomyEventSource
import org.torproject.vpn.autonomy.AutonomyEventType
import org.torproject.vpn.autonomy.Transport
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.Country
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.BridgeType
import org.torproject.vpn.utils.Priority
import org.torproject.vpn.utils.TaskDebouncer
import org.torproject.vpn.utils.VpnNotificationManager
import org.torproject.vpn.utils.readAsset

class TorVpnService : VpnService() {

    companion object {
        private val TAG: String = TorVpnService::class.java.getSimpleName()
        val ACTION_START_VPN = "$TAG.start"
        val ACTION_STOP_VPN = "$TAG.stop"
        const val FOREGROUND_SERVICE_SET = 1
    }

    private lateinit var notificationManager: VpnNotificationManager
    private lateinit var logHelper: LogHelper
    private lateinit var appManager: AppManager
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var connectivityHandler: ConnectivityHandler
    private val autonomyCoordinator = AutonomyCoordinator()
    private var autonomyTunnelAttemptStartedAtMillis: Long? = null
    private var autonomyEnvironmentMonitor: AndroidEnvironmentMonitor? = null
    private var autonomyResourceMonitor: AndroidResourceMonitor? = null

    private var logObservable: LogObservable? = null

    private val useBridge: Boolean
        get() = preferenceHelper.bridgeType != BridgeType.None
    private val binder: IBinder = TorVpnServiceBinder(WeakReference(this))

    private val job = SupervisorJob()
    private var collectionJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)
    private var startCoroutineScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob(job))

    @Volatile
    private var actionDebouncer = TaskDebouncer()
    private var observer: Observer<OnionmasqEvent>? = null
    private var vpnStateFlow = combine(
        VpnStatusObservable.statusLiveData,
        VpnStatusObservable.dataUsage,
        VpnStatusObservable.hasInternetConnectivity
    ) { connectionState, dataUsage, hasInternetConnectivity ->
        return@combine Triple(connectionState, dataUsage, hasInternetConnectivity)
    }.stateIn(
        scope = this.coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = Triple(
            VpnStatusObservable.statusLiveData.value,
            VpnStatusObservable.dataUsage.value,
            VpnStatusObservable.hasInternetConnectivity.value
        )
    )
    private val mainHandler: Handler by lazy {
        Handler(mainLooper)
    }
    private var timer: Timer? = null

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "service: onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "service: onUnbind")
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service: onCreate")
        notificationManager = VpnNotificationManager(this)
        logHelper = LogHelper()
        appManager = AppManager(this)
        preferenceHelper = PreferenceHelper(this)
        logObservable = LogObservable.getInstance()
        connectivityHandler = ConnectivityHandler(this)
        connectivityHandler.register()
        observeAutonomyEvent(AutonomyEventType.SERVICE_CREATED)
        autonomyEnvironmentMonitor = AndroidEnvironmentMonitor(this, autonomyCoordinator).also {
            it.register()
        }
        autonomyResourceMonitor = AndroidResourceMonitor(this, autonomyCoordinator).also {
            it.register()
        }
        OnionMasq.bindVPNService(TorVpnService::class.java)
        try {
            when (val country = preferenceHelper.exitNodeCountry) {
                Country.RANDOM -> OnionMasq.setCountryCode(null)
                else -> OnionMasq.setCountryCode(country.code)
            }
        } catch (exception: CountryCodeException) {
            Log.e(TAG, "Failed to use last saved exit node", exception)
            throw RuntimeException(exception)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "service: onStartCommand")

        val notification: Notification = notificationManager.buildForegroundServiceNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    VpnNotificationManager.NOTIFICATION_ID,
                    notification,
                    FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(VpnNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to start foregroundservice: ${e.message}")
            VpnStatusObservable.update(ConnectionState.CONNECTION_ERROR)
            stopSelf()
            return START_NOT_STICKY
        }

        val handler = intent?.let {
            IntentCompat.getParcelableExtra(it, "handler", Messenger::class.java)
        }
        val taskID = intent?.let {
            intent.getIntExtra("taskID", 0)
        } ?: 0
        val message = Message()
        message.what = FOREGROUND_SERVICE_SET
        try {
            handler?.send(message)
        } catch (e: DeadObjectException) {
            Log.e(
                TAG,
                "service ($taskID): launcher handler doesn't exist anymore. Timeout reached?",
                e
            )
        }

        val action = if (intent != null) intent.action else ""
        Log.v(TAG, "service ($taskID): enqueue task")
        if (intent?.component == null || intent.component!!.packageName != packageName) {
            actionDebouncer.enqueueTask(taskID, ACTION_START_VPN) {
                Log.v(TAG, "service ($taskID): enqueued task launched ACTION_START_VPN")
                VpnStatusObservable.isAlwaysOnBooting.set(true)
                establishVpn(taskID)
            }
        } else if (action == ACTION_START_VPN) {
            actionDebouncer.enqueueTask(taskID, ACTION_START_VPN, Priority.LOW) {
                Log.v(TAG, "service ($taskID): enqueued task launched ACTION_START_VPN")
                VpnStatusObservable.isAlwaysOnBooting.set(false)
                establishVpn(taskID)
            }
        } else if (action == ACTION_STOP_VPN) {
            actionDebouncer.enqueueTask(taskID, ACTION_STOP_VPN, Priority.HIGH) {
                Log.v(TAG, "service ($taskID): enqueued task launched ACTION_STOP_VPN")
                stop(ConnectionState.DISCONNECTING, taskID)
            }
        } else {
            Log.v(TAG, "service ($taskID): unknown action: $action")
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // do not call super here, stop(onError: Boolean) calls stopSelf() which is the default
        // implementation of super.onRevoke
        Log.d(TAG, "service: onRevoke")
        actionDebouncer.enqueueTask(-1, ACTION_STOP_VPN, Priority.HIGH) {
            stop(ConnectionState.DISCONNECTING, -1)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "service: onDestroy")
        observeAutonomyEvent(AutonomyEventType.SERVICE_STOPPED)
        // TaskDebouncer owns an independent scope, so cancelling the service job alone
        // does not terminate its consumer or discard queued callbacks.
        actionDebouncer.cancel()
        startCoroutineScope.cancel()
        timer?.cancel()
        timer = null
        removeObservers()
        collectionJob?.cancel()
        job.cancel()

        notificationManager.cancelNotifications()
        val errors = listOf(ConnectionState.CONNECTION_ERROR, ConnectionState.PRIVATE_DNS_ERROR)
        if (VpnStatusObservable.statusLiveData.value !in errors) {
            VpnStatusObservable.update(ConnectionState.DISCONNECTED)
        }
        connectivityHandler.unregister()
        autonomyEnvironmentMonitor?.unregister()
        autonomyEnvironmentMonitor = null
        autonomyResourceMonitor?.unregister()
        autonomyResourceMonitor = null
        logObservable = null
        super.onDestroy()
    }

    private fun stop(state: ConnectionState, taskID: Int) {
        observeAutonomyEvent(AutonomyEventType.TUNNEL_STOPPED)
        Log.v(TAG, "service ($taskID): stopping")
        VpnStatusObservable.update(state)
        actionDebouncer.cancel()
        // TaskDebouncer.cancel() is terminal; allow a later service start to enqueue work.
        actionDebouncer = TaskDebouncer()
        startCoroutineScope.cancel()
        OnionMasq.stop()
        removeObservers()
        logHelper.stopLog()
        timer?.cancel()
        timer = null
        VpnStatusObservable.reset()
        try {
            OnionMasq.unbindVPNService()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createObservers() {
        observer = Observer<OnionmasqEvent> { event: OnionmasqEvent ->
            when (event) {
                is BootstrapEvent -> {
                    if (event.isReadyForTraffic && event.bootstrapPercent == 100) {
                        recordAutonomyBridgeOutcome(succeeded = true)
                        VpnStatusObservable.update(ConnectionState.CONNECTED)
                        observeAutonomyEvent(AutonomyEventType.TUNNEL_READY)
                    } else {
                        observeAutonomyEvent(AutonomyEventType.TUNNEL_BOOTSTRAPPING)
                    }
                    LogObservable.getInstance()
                        .addLog(getString(R.string.bootstrap_at, event.bootstrapStatus))
                }
                is NewConnectionEvent -> {
                    if (preferenceHelper.verboseLogging) {
                        LogObservable.getInstance().addLog(
                            getString(
                                R.string.new_connection,
                                event.proxySrc,
                                event.proxyDst,
                                event.torDst,
                                event.appId
                            )
                        )

                        var verboseLogContent = ""
                        for ((i, relay) in event.circuit.withIndex()) {
                            val identity = relay.ed_identity ?: relay.rsa_identity ?: "unknown"
                            val address =
                                if (relay.addresses.size > 0) relay.addresses[0] else "unknown"

                            verboseLogContent = "$verboseLogContent\n${getString(
                                R.string.new_connection_hop,
                                event.proxySrc,
                                event.proxyDst,
                                i,
                                address,
                                identity
                            )}"
                        }
                        LogObservable.getInstance().addLog(verboseLogContent, true)
                    } else {
                        LogObservable.getInstance().addLog("New connection established")
                    }
                }
                is FailedConnectionEvent -> {
                    // A failed app stream is not proof that the Tor tunnel or bridge failed.
                    if (preferenceHelper.verboseLogging) {
                        LogObservable.getInstance().addLog(
                            getString(
                                R.string.failed_connection,
                                event.proxySrc,
                                event.proxyDst,
                                event.torDst,
                                event.error,
                                event.appId
                            )
                        )
                    } else {
                        LogObservable.getInstance().addLog("Connection failed")
                    }
                }
                is ClosedConnectionEvent -> {
                    if (preferenceHelper.verboseLogging) {
                        event.error?.let {
                            LogObservable.getInstance().addLog(
                                getString(
                                    R.string.failed_connection_closed,
                                    event.proxySrc,
                                    event.proxyDst,
                                    event.error
                                ),
                                true
                            )
                        } ?: run {
                            LogObservable.getInstance().addLog(
                                getString(
                                    R.string.closed_connection,
                                    event.proxySrc,
                                    event.proxyDst
                                ),
                                true
                            )
                        }
                    } else {
                        LogObservable.getInstance().addLog("Connection closed")
                    }
                }
                is NewDirectoryEvent -> {
                    var relaysByCountry = event.relaysByCountry.entries.toList()
                    relaysByCountry = relaysByCountry.sortedBy { (_, count) -> count }
                    val countriesWithSufficientExitNodes: ArrayList<String> = ArrayList()
                    for ((countryCode, count) in relaysByCountry) {
                        if (count > 3) {
                            countriesWithSufficientExitNodes.add(countryCode)
                        }
                    }
                    preferenceHelper.relayCountries = countriesWithSufficientExitNodes.toSet()
                }
                is ConnectivityEvent -> {
                    // Onionmasq connectivity is not authoritative Android default-network state.
                    VpnStatusObservable.updateInternetConnectivity(event.hasInternetConnectivity)
                }
                is DNSConnectivityEvent -> {
                    if (connectivityHandler.isPrivateDnsEnforced()) {
                        observeAutonomyEvent(AutonomyEventType.DNS_CONFIGURATION_CONFLICT)
                        notificationManager.notifyPrivateDnsError()
                        stop(ConnectionState.PRIVATE_DNS_ERROR, -1)
                        LogObservable.getInstance().addLog(
                            getString(R.string.error_private_dns_message)
                        )
                    }
                }
            }
        }
    }

    private fun startListeningObservers() {
        observer?.let {
            mainHandler.post { OnionMasq.getEventObservable().observeForever(it) }
        }

        collectionJob = this.coroutineScope.launch {
            vpnStateFlow.collect { triple ->
                notificationManager.updateNotification(
                    state = triple.first,
                    dataUsage = triple.second,
                    hasConnectivity = triple.third
                )
            }
        }
    }

    private fun removeObservers() {
        observer?.let {
            mainHandler.post { OnionMasq.getEventObservable().removeObserver(it) }
        }
        collectionJob?.cancel()
    }

    private fun prepareVpnProfile(): Builder {
        val builder = Builder()
        applyAppFilter(builder)
        builder.setSession("TorVPN session")
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)
        builder.addAddress("169.254.42.1", 16)
        builder.addAddress("fc00::", 7)
        if (connectivityHandler.isPrivateDnsEnforced()) {
            throw SecurityException("Private DNS is enforced on the device with specific hostname")
        } else {
            builder.addDnsServer("169.254.42.53")
            builder.addDnsServer("fe80::53")
        }
        builder.allowFamily(OsConstants.AF_INET)
        builder.allowFamily(OsConstants.AF_INET6)
        builder.setMtu(1500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        return builder
    }

    private fun establishVpn(taskID: Int) {
        try {
            autonomyTunnelAttemptStartedAtMillis = SystemClock.elapsedRealtime()
            observeAutonomyEvent(AutonomyEventType.TUNNEL_START_REQUESTED)
            Log.v(TAG, "service ($taskID): starting vpn... ")
            if (OnionMasq.isRunning()) {
                Log.v(TAG, "service ($taskID): stopping previous Tor session... ")
                OnionMasq.stop()
                Log.v(TAG, "service ($taskID): previous Tor session hopefully stopped...")
            } else {
                Log.v(TAG, "service ($taskID): Tor session not yet running... starting")
                logHelper.readLog()
                createObservers()
                startListeningObservers()
                timer?.cancel()
                timer = Timer()
                timer?.schedule(
                    object : TimerTask() {
                        override fun run() {
                            VpnStatusObservable.updateDataUsage(
                                OnionMasq.getBytesReceived(),
                                OnionMasq.getBytesSent()
                            )
                        }
                    },
                    1000,
                    1000
                )
            }
            Log.v(TAG, "service ($taskID): prepare startCoroutineScope")

            val builder = prepareVpnProfile()
            val fd = builder.establish()
            // stop() cancels this scope; a later start in the same service instance needs a fresh one.
            if (!startCoroutineScope.isActive) {
                startCoroutineScope =
                    CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob(job))
            }
            val waitCoroutineStarted = CountDownLatch(1)
            val startupJob = startCoroutineScope.launch {
                waitCoroutineStarted.countDown()
                Log.v(TAG, "service ($taskID): startCoroutineScope - start Onionmasq")
                val tunInterface = fd
                if (tunInterface == null) {
                    handleException(
                        IllegalStateException("Android did not establish the VPN interface."),
                        "Onionmasq error: Android did not establish the VPN interface.",
                        taskID
                    )
                    return@launch
                }
                val descriptorDetached = AtomicBoolean(false)
                try {
                    val nativeFd = tunInterface.detachFd()
                    descriptorDetached.set(true)
                    OnionMasq.start(nativeFd, getBridgeLines())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleException(e, "Onionmasq error: ${e.message}", taskID)
                } finally {
                    // If ownership was not transferred to native code, close the Android descriptor.
                    if (!descriptorDetached.get()) {
                        runCatching { tunInterface.close() }
                    }
                }
            }
            // Also cover cancellation before the coroutine body starts; its finally block would not run.
            startupJob.invokeOnCompletion {
                // ParcelFileDescriptor has no isClosed property; close is safe after detachFd(),
                // because detachFd() invalidates the Java-side descriptor without closing native fd.
                fd?.let { descriptor -> runCatching { descriptor.close() } }
            }
            Log.v(TAG, "service ($taskID): await startCoroutineScope")
            val success = waitCoroutineStarted.await(15, TimeUnit.SECONDS)
            if (success) {
                Log.v(TAG, "service ($taskID): coroutine started in time")
            } else {
                Log.v(TAG, "service ($taskID): timeout reached")
            }
            if (!success) {
                // throw an error and stop the VPN
                throw IllegalStateException("startCoroutine exceeded timeout to launch")
            }
        } catch (e: Exception) {
            handleException(e, "Failed to establish VPN.", taskID)
        }
    }

    private fun handleException(e: Exception, msg: String, taskID: Int) {
        recordAutonomyBridgeOutcome(succeeded = false)
        observeAutonomyEvent(AutonomyEventType.TUNNEL_FAILED)
        Log.e(TAG, "($taskID): $msg", e)
        when (e) {
            is SecurityException -> stop(ConnectionState.PRIVATE_DNS_ERROR, taskID)
            else -> stop(ConnectionState.CONNECTION_ERROR, taskID)
        }
    }

    private fun recordAutonomyBridgeOutcome(succeeded: Boolean) {
        val startedAt = autonomyTunnelAttemptStartedAtMillis ?: return
        autonomyTunnelAttemptStartedAtMillis = null
        val transport = when (preferenceHelper.bridgeType) {
            BridgeType.Obfs4 -> Transport.OBFS4
            BridgeType.Snowflake -> Transport.SNOWFLAKE
            // Manual bridge definitions may use different transports, so do not guess.
            BridgeType.Manual, BridgeType.None -> null
        } ?: return
        val now = SystemClock.elapsedRealtime()
        autonomyCoordinator.recordBridgeOutcome(
            transport = transport,
            succeeded = succeeded,
            latencyMillis = (now - startedAt).coerceAtLeast(0L),
            elapsedRealtimeMillis = now
        )
    }

    private fun observeAutonomyEvent(type: AutonomyEventType) {
        // Observe-only in this branch: the coordinator must never replace service enforcement.
        autonomyCoordinator.observe(type, AutonomyEventSource.VPN_SERVICE)
    }

    private fun getBridgeLines(): String? {
        if (!useBridge) {
            return null
        }

        return when (preferenceHelper.bridgeType) {
            BridgeType.None -> null
            BridgeType.Manual -> getManualBridgeLines()
            BridgeType.Snowflake -> readAsset(this, "snowflake.txt")
            BridgeType.Obfs4 -> readAsset(this, "obfs4.txt")
        }
    }

    private fun getManualBridgeLines(): String? {
        val bridgeList = preferenceHelper.bridgeLines
        if (bridgeList.isEmpty()) {
            return null
        }
        var bridgeLines = ""
        for (line in bridgeList) {
            bridgeLines += "$line\n"
        }

        return bridgeLines
    }

    /**
     * Adds selected app into 'allowed apps' for current vpn connection. Only selected apps will use VPN.
     * @param builder VPN Builder
     */
    private fun applyAppFilter(builder: Builder) {
        val packageManager = packageManager
        // all apps will be routed over the VPN's tun interface, tor-powered apps will be routed outside
        // the tor-circuits though
        if (runBlocking { appManager.globalRouting.first().isProtectingAllApps }) {
            // exclude tor-vpn in case  we use bridges
            if (useBridge) {
                builder.addDisallowedApplication(BuildConfig.APPLICATION_ID)
            }
            return
        }

        // included apps are apps that have not been manually disabled as well as all tor-powered apps
        // the latter are additionally set to be routed outside the tor-circuits
        val includedApps = runBlocking { appManager.protectedApps }
        // filtering, allow a subset of installed apps
        if (includedApps.isNotEmpty()) {
            for (app in includedApps) {
                try {
                    packageManager.getPackageInfo(app.packageName, 0)
                    if (BuildConfig.APPLICATION_ID == app.packageName && useBridge) {
                        // always exclude TorVPN in case we use bridges
                        continue
                    }
                    builder.addAllowedApplication(app.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    // The app is selected but isn't installed anymore.
                }
            }
        } else {
            // no app selected and protect all apps disabled: disallow all installed apps
            // in combination with Androids lockdown mode this blocks all apps internet connectivity
            val installedPackages = packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA
            )
            for (appPackage in installedPackages) {
                try {
                    builder.addDisallowedApplication(appPackage.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
