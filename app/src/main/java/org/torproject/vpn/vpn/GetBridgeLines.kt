package org.torproject.vpn.vpn

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.torproject.vpn.circumvention.CircumventionApiManager
import org.torproject.vpn.circumvention.SettingsRequest

/**
 * Get bridge lines from the API.
 */
class GetBridgeLines(
    private val apiManager: CircumventionApiManager = CircumventionApiManager()
) {

    suspend operator fun invoke(
        request: SettingsRequest = SettingsRequest(transports = listOf("obfs4"))
    ): List<String> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            // Coroutine was cancelled
        }

        apiManager.getDefaults(
            request,
            onResult = { settingsResponse ->
                if (!continuation.isActive) return@getDefaults

                if (settingsResponse == null) {
                    continuation.resumeWithException(IOException("Response was null"))
                    return@getDefaults
                }

                if (!settingsResponse.errors.isNullOrEmpty()) {
                    val errorMessage = settingsResponse.errors.joinToString(", ") { it.detail }
                    continuation.resumeWithException(
                        IOException("Error fetching bridges: $errorMessage")
                    )
                    return@getDefaults
                }

                val results = mutableListOf<String>()
                val settingsList = settingsResponse.settings
                val bridgeDBList = settingsList?.filter { it.bridges.source == "bridgedb" }
                val bridgesToUse = if (!bridgeDBList.isNullOrEmpty()) bridgeDBList else settingsList

                bridgesToUse?.forEach { bridges ->
                    bridges.bridges.bridge_strings?.let { results.addAll(it) }
                }

                if (results.isNotEmpty()) {
                    continuation.resume(results)
                } else {
                    continuation.resumeWithException(IOException("No bridge lines found"))
                }
            },
            onError = { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        )
    }
}
