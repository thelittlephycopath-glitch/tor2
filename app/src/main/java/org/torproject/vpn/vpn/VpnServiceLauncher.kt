package org.torproject.vpn.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.Handler.Callback
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.vpn.TorVpnService.Companion.ACTION_START_VPN
import org.torproject.vpn.vpn.TorVpnService.Companion.ACTION_STOP_VPN

/**
 * VpnServiceLauncher is a Worker that reduces the risks of running into ForegroundService related exceptions
 * when starting the VPNService.
 * It binds the VPNService before it calls startForegroundService and unbinds it after ServiceCompat.startForeground
 * was called within the VPNService.
 * VpnServiceLauncher is restricted to sequential job execution.
 */
class VpnServiceLauncher(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val COMMAND = "command"
        val TAG = VpnServiceLauncher::class.simpleName
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO.limitedParallelism(1)) {
        val command = inputData.getString(COMMAND) ?: ""
        if (command != ACTION_START_VPN && command != ACTION_STOP_VPN) {
            return@withContext Result.success()
        }

        Log.d(TAG, "VpnLauncher called with action $command")
        var vpnServiceConnection: TorVpnServiceConnection? = null
        val waitForForegroundSet = CountDownLatch(1)
        val taskID = Random(System.currentTimeMillis()).nextInt()
        try {
            val appContext = context.applicationContext

            // bind service, blocks until service was created and was bound
            vpnServiceConnection = TorVpnServiceConnection(appContext)

            // start service as foreground service
            Log.d(TAG, "start vpn service as foreground service...")
            val intent = Intent(appContext, TorVpnService::class.java)
            val callback = Callback { msg ->
                if (msg.what == TorVpnService.FOREGROUND_SERVICE_SET) {
                    waitForForegroundSet.countDown()
                    return@Callback true
                }
                return@Callback false
            }
            val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper(), callback)
            intent.putExtra("handler", Messenger(handler))
            intent.putExtra("taskID", taskID)
            intent.action = command
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }

            // wait with closing of bound service until ServiceCompat.startForeground was called in TorVpnService
            if (waitForForegroundSet.await(3, TimeUnit.SECONDS)) {
                Log.d(TAG, "$taskID: foreground service was set in TorVpnService")
            } else {
                Log.w(TAG, "$taskID: Timeout for ForegroundService call reached.")
            }
        } catch (e: Exception) {
            LogObservable.getInstance().addLog("Failed to start VPN service: ${e.message}")
            e.printStackTrace()
        } finally {
            // always try to unbind service
            vpnServiceConnection?.close()
        }

        Result.success()
    }

    private class TorVpnServiceConnection(context: Context) : Closeable {
        private var context: Context?
        private var serviceConnection: ServiceConnection? = null
        private var service: WeakReference<TorVpnService> = WeakReference(null)

        init {
            this.context = context
            initSynchronizedServiceConnection(context)
        }

        override fun close() {
            Log.d(TAG, "unbinding service connection")
            serviceConnection?.let { connection ->
                context!!.unbindService(connection)
                serviceConnection = null
                service.clear()
                context = null
                Log.d(TAG, "unbinding service connection: cleaned up resources")
            }
        }

        @Throws(InterruptedException::class)
        private fun initSynchronizedServiceConnection(context: Context) {
            Log.d(TAG, "initSynchronizedServiceConnection...")
            val blockingQueue: BlockingQueue<TorVpnService?> =
                LinkedBlockingQueue<TorVpnService?>(1)
            this.serviceConnection = object : ServiceConnection {
                @Volatile
                var serviceBound: Boolean = false

                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (!serviceBound) {
                        serviceBound = true
                        try {
                            Log.d(TAG, "service connection bound...")
                            val binder: TorVpnServiceBinder =
                                service as TorVpnServiceBinder
                            blockingQueue.put(binder.service)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Log.d(TAG, "service connection disconnected...")
                }
            }
            serviceConnection?.let {
                val intent = Intent(context, TorVpnService::class.java)
                Log.d(TAG, "bind service connection...")
                context.bindService(intent, it, Context.BIND_AUTO_CREATE)
                service = WeakReference(blockingQueue.take())
            }
        }
    }
}
