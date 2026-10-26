package org.torproject.vpn.autonomy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Feeds battery state into the optional-traffic budget only.
 *
 * It does not change VPN routing, start traffic generation, or expose battery data outside the
 * process. If the platform cannot supply a valid reading, optional traffic remains suppressed.
 */
class AndroidResourceMonitor(
    context: Context,
    private val coordinator: AutonomyCoordinator
) {
    private val applicationContext = context.applicationContext

    @Volatile
    private var registered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            updateBattery(intent)
        }
    }

    @Synchronized
    fun register() {
        if (registered) return
        try {
            val stickyIntent = ContextCompat.registerReceiver(
                applicationContext,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
            if (stickyIntent != null) {
                updateBattery(stickyIntent)
            } else {
                recordUnavailable()
            }
        } catch (_: RuntimeException) {
            // Never allow optional cover traffic when the battery budget cannot be read.
            recordUnavailable()
        }
    }

    @Synchronized
    fun unregister() {
        if (!registered) return
        runCatching { applicationContext.unregisterReceiver(batteryReceiver) }
        registered = false
    }

    private fun recordUnavailable() {
        coordinator.observe(
            AutonomyEventType.RESOURCE_MONITOR_UNAVAILABLE,
            source = AutonomyEventSource.RESOURCE_MONITOR
        )
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            recordUnavailable()
            return
        }

        val percent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
        coordinator.updateBatteryPercent(percent)
    }
}
