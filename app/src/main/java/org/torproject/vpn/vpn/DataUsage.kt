package org.torproject.vpn.vpn

import org.torproject.vpn.utils.formatByteRateToBitRate
import org.torproject.vpn.utils.formatBytes

data class DataUsage(
    val upstreamData: Long = 0,
    val downstreamData: Long = 0,
    val upstreamDataPerSec: Long = 0,
    val downstreamDataPerSec: Long = 0,
    val timeStamp: Long = System.currentTimeMillis()
) {
    val formattedUpstreamData: String
        get() = formatBytes(upstreamData)

    val formattedDownstreamData: String
        get() = formatBytes(downstreamData)

    val formattedUpstreamDataPerSec: String
        get() = formatByteRateToBitRate(upstreamDataPerSec)

    val formattedDownstreamDataPerSec: String
        get() = formatByteRateToBitRate(downstreamDataPerSec)
}
