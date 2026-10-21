package org.torproject.vpn.circumvention

import kotlinx.serialization.Serializable

@Serializable
data class SettingsRequest(val country: String? = null, val transports: List<String>? = null)
