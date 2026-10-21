package org.torproject.vpn.utils

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import org.torproject.vpn.ui.iconsetting.model.Launcher

/**
 * Updates and persists the application's launcher icon.
 *
 * @property application The application instance.
 * @property preferenceHelper Helper to persist preferences.
 */
class SetLauncherIcon(val application: Application, val preferenceHelper: PreferenceHelper) {
    /**
     * Sets the active launcher icon to [launcher].
     *
     * @param launcher The target [Launcher] icon.
     */
    operator fun invoke(launcher: Launcher) {
        preferenceHelper.launcherClass = launcher.id
        Launcher.entries.forEach { l ->
            application.packageManager.setComponentEnabledSetting(
                ComponentName(application, l::class.java.name),
                when {
                    l == launcher -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
