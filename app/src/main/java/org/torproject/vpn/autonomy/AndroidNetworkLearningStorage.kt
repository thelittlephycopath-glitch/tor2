package org.torproject.vpn.autonomy

import android.content.Context

/**
 * Private app-local persistence adapter. Only keyed digests and timestamps are written; raw network
 * identifiers are not accepted by this API. No backup/export path is added here.
 */
class AndroidNetworkLearningStorage(context: Context) : NetworkLearningStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun read(): String? = preferences.getString(RECORDS_KEY, null)

    override fun write(value: String?) {
        val editor = preferences.edit()
        if (value == null) {
            editor.remove(RECORDS_KEY)
        } else {
            editor.putString(RECORDS_KEY, value)
        }
        check(editor.commit()) { "Could not persist network-learning records" }
    }

    private companion object {
        const val PREFERENCES_NAME = "autonomy_network_learning_v1"
        const val RECORDS_KEY = "keyed_fingerprints"
    }
}
