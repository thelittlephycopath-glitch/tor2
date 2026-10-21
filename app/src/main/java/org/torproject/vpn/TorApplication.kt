package org.torproject.vpn

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.torproject.onionmasq.OnionMasq
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.ui.approuting.data.AppQueryReceiver
import org.torproject.vpn.utils.PreferenceHelper

class TorApplication : Application() {
    val appContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        PreferenceHelper(this).runMigrationsIfRequired()

        OnionMasq.init(this)
        AppQueryReceiver.register(this)
        val appManager = AppManager(this)
        CoroutineScope(Dispatchers.IO).launch {
            OnionMasq.setExcludedUids(appManager.getInstalledTorPoweredAppsUid())
            appManager.updateAppDatabase()
        }
    }
}
