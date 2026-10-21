package org.torproject.vpn.ui.home.model

import org.torproject.vpn.vpn.ConnectionState

/**
 * Past and current connection state for UI decisions
 */
data class State(
    val previous: ConnectionState = ConnectionState.INIT,
    val current: ConnectionState = ConnectionState.INIT
)
