package org.torproject.vpn.autonomy

import java.util.Locale

/**
 * Conservative parser used only to attribute measured connection outcomes to known bridge transports.
 *
 * This never changes bridge configuration. It recognizes only explicit obfs4/Snowflake tokens;
 * mixed, missing, and unknown transport types return null rather than contaminating learned data.
 */
internal object ManualBridgeTransportClassifier {
    fun classify(lines: List<String>): Transport? {
        val activeLines = lines.mapNotNull { raw ->
            raw.substringBefore('#').trim().takeIf { it.isNotEmpty() }
        }
        if (activeLines.isEmpty()) return null

        val transports = activeLines.map { line ->
            val tokens = line.split(WHITESPACE)
            val transportToken = if (tokens.first().equals("Bridge", ignoreCase = true)) {
                tokens.getOrNull(1)
            } else {
                tokens.first()
            } ?: return null

            when (transportToken.lowercase(Locale.ROOT)) {
                "obfs4" -> Transport.OBFS4
                "snowflake" -> Transport.SNOWFLAKE
                else -> return null
            }
        }
        return transports.distinct().singleOrNull()
    }

    private val WHITESPACE = Regex("\\s+")
}
