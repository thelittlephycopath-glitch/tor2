package org.torproject.vpn.extensions

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import org.torproject.vpn.utils.PreferenceHelper

/**
 * Reusable flow for observing changes to shared preferences
 */
fun <T> PreferenceHelper.observe(
    key: String,
    scope: CoroutineScope,
    getValue: () -> T
): StateFlow<T> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (key == changedKey) {
            trySend(getValue())
        }
    }

    trySend(getValue())
    registerListener(listener)
    awaitClose { unregisterListener(listener) }
}.distinctUntilChanged().stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(),
    initialValue = getValue()
)
