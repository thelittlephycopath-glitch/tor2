package org.torproject.vpn.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import org.torproject.vpn.ui.iconsetting.model.LauncherDefault

private const val TOR_VPN_SP: String = "tor-vpn"

class PreferenceHelper(context: Context) {

    companion object {
        const val PROTECTED_APPS: String = "protected_apps"
        const val PROTECT_ALL_APPS: String = "protect_all_apps"
        const val SHOULD_SHOW_GUIDE: String = "should_show_guide"
        const val START_ON_BOOT: String = "start_on_boot"
        const val EXIT_NODE_COUNTRY: String = "exit_node_country"
        const val BRIDGE_TYPE = "bridge_type"
        const val BRIDGE_LINES = "bridge_lines"
        const val LAUNCHER_CLASS = "launcher_class"
        const val EXIT_NODE_COUNTRIES = "exit_node_countries"
        const val VERBOSE_LOGGING = "verbose_logging"
        const val RECENT_SEARCHES = "recent_searches"

        private const val PREF_VERSION = 1 // BUMP THIS MANUALLY ON ADDING NEW MIGRATION STEP
        private const val MIGRATION_VERSION = "migration_version"
        private const val USE_BRIDGE = "use_bridge"
        private const val SHOULD_MIGRATE_PROTECTED_APPS = "migrate_protected_apps"

        enum class BridgeType {
            Obfs4,
            Snowflake,
            Manual,
            None
        }
    }

    private val TAG = PreferenceHelper::class.java.simpleName

    private val sharedPreference =
        context.applicationContext.getSharedPreferences(TOR_VPN_SP, Context.MODE_PRIVATE)

    /**
     * Whether guide in the connect screen need to be shown.
     *
     * */
    var shouldShowGuide
        get() = sharedPreference.getBoolean(SHOULD_SHOW_GUIDE, true)
        set(value) = sharedPreference.edit { putBoolean(SHOULD_SHOW_GUIDE, value) }

    var launcherClass: String
        get() = sharedPreference.getString(LAUNCHER_CLASS, LauncherDefault.id)!!
        set(value) = sharedPreference.edit { putString(LAUNCHER_CLASS, value) }

    var protectAllApps
        get() = sharedPreference.getBoolean(PROTECT_ALL_APPS, true)
        set(value) = sharedPreference.edit { putBoolean(PROTECT_ALL_APPS, value) }

    var protectedApps: MutableSet<String>
        get() = sharedPreference.getStringSet(PROTECTED_APPS, mutableSetOf<String>())
            .orEmpty()
            .toMutableSet()

        // setter used in tests
        set(value) = sharedPreference.edit(commit = !isRunningOnMainThread()) {
            putStringSet(PROTECTED_APPS, value)
        }

    var startOnBoot
        get() = sharedPreference.getBoolean(START_ON_BOOT, false)
        set(value) = sharedPreference.edit { putBoolean(START_ON_BOOT, value) }

    var exitNodeCountry
        get() = Country.fromCode(
            sharedPreference.getString(EXIT_NODE_COUNTRY, Country.RANDOM.code)
        ) ?: Country.RANDOM
        set(value) = sharedPreference.edit { putString(EXIT_NODE_COUNTRY, value.code) }

    var bridgeType: BridgeType
        get() = BridgeType.valueOf(sharedPreference.getString(BRIDGE_TYPE, BridgeType.None.name)!!)
        set(bridgeType) = sharedPreference.edit { putString(BRIDGE_TYPE, bridgeType.name) }

    var bridgeLines: List<String>
        get() {
            try {
                val json = sharedPreference.getString(BRIDGE_LINES, null) ?: return emptyList()
                return Json.decodeFromString<List<String>>(json)
            } catch (_: ClassCastException) {
                // Legacy StringSet stored under this key — migrate to JSON list
                val legacySet =
                    sharedPreference.getStringSet(BRIDGE_LINES, null) ?: return emptyList()
                val list = legacySet.toList()
                sharedPreference.edit { remove(BRIDGE_LINES) }
                sharedPreference.edit { putString(BRIDGE_LINES, Json.encodeToString(list)) }
                return list
            }
        }
        set(value) = sharedPreference.edit { putString(BRIDGE_LINES, Json.encodeToString(value)) }

    var relayCountries
        get() = sharedPreference.getStringSet(EXIT_NODE_COUNTRIES, emptySet()) as Set<String>
        set(value) = sharedPreference.edit { putStringSet(EXIT_NODE_COUNTRIES, value) }

    var verboseLogging
        get() = sharedPreference.getBoolean(VERBOSE_LOGGING, false)
        set(value) = sharedPreference.edit { putBoolean(VERBOSE_LOGGING, value) }

    var shouldMigrateProtectedApps
        get() = sharedPreference.getBoolean(SHOULD_MIGRATE_PROTECTED_APPS, true)
        set(value) = sharedPreference.edit { putBoolean(SHOULD_MIGRATE_PROTECTED_APPS, value) }

    var recentSearches: List<String>
        get() {
            val jsonString = sharedPreference.getString(RECENT_SEARCHES, null) ?: return emptyList()
            return Json.decodeFromString(jsonString)
        }
        set(value) = sharedPreference.edit {
            putString(RECENT_SEARCHES, Json.encodeToString(value))
        }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreference.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreference.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @VisibleForTesting
    fun clear() {
        sharedPreference.edit(commit = true) { clear() }
    }

    fun runMigrationsIfRequired() {
        val oldVersion = sharedPreference.getInt(MIGRATION_VERSION, 0)

        // Run required migrations on version upgrade for existing installs
        if (oldVersion != PREF_VERSION) {
            val newVersion = upgradeIfNeeded(oldVersion)
            sharedPreference.edit { putInt(MIGRATION_VERSION, newVersion) }
        } else {
            Log.i(TAG, "No need to run migrations")
        }
    }

    private fun upgradeIfNeeded(oldVersion: Int): Int {
        Log.i(TAG, "Upgrading from version $oldVersion to version $PREF_VERSION")
        var currentVersion = oldVersion

        // Add new migrations / defaults below this point
        // Always bump currentVersion at the end of migration for next release

        // 21 (1.7.0Beta) -> 22 (1.8.0Beta)
        if (currentVersion == 0) {
            // If user selected a bridge but turned off global switch, migrate it to none
            if (!sharedPreference.getBoolean(USE_BRIDGE, false) && bridgeType != BridgeType.None) {
                bridgeType = BridgeType.None
            }
            currentVersion++
        }

        // Add new migrations / defaults above this point.
        if (currentVersion != PREF_VERSION) {
            Log.e(TAG, "Upgrading to version $PREF_VERSION left it at $currentVersion instead")
        } else {
            Log.i(TAG, "Finished running required migrations!")
        }

        return currentVersion
    }
}
