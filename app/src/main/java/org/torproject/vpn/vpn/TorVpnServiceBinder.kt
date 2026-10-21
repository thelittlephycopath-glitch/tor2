package org.torproject.vpn.vpn

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.lang.ref.WeakReference
import org.torproject.onionmasq.ISocketProtect

class TorVpnServiceBinder(
    private val torVpnServiceReference: WeakReference<TorVpnService>
) : Binder(), ISocketProtect {

    override fun protect(socket: Int): Boolean {
        torVpnServiceReference.get()?.let {
            return it.protect(socket)
        }
        return false
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == IBinder.LAST_CALL_TRANSACTION) {
            torVpnServiceReference.get()?.let {
                it.onRevoke()
                return true
            }
        }
        return false
    }

    val service: TorVpnService?
        get() = torVpnServiceReference.get()
}
