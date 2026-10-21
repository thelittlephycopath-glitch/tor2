package org.torproject.vpn.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Destinations for navigation in compose
 */
@Serializable
sealed interface Destination : NavKey {

    @Serializable
    data object BridgeBot : Destination

    @Serializable
    data object AppRouting : Destination

    @Serializable
    data class AppDetail(val packageName: String) : Destination

    @Serializable
    data object AppSearch : Destination

    @Serializable
    data object About : Destination

    @Serializable
    data object IconSetting : Destination

    @Serializable
    data object ExitSelection : Destination
}
