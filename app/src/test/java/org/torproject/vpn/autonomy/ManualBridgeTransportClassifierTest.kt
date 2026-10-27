package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualBridgeTransportClassifierTest {
    @Test
    fun classifiesExplicitObfs4AndSnowflakeLines() {
        assertEquals(
            Transport.OBFS4,
            ManualBridgeTransportClassifier.classify(
                listOf(
                    "# preferred bridges",
                    "",
                    "Bridge obfs4 192.0.2.1:443 ABCDEF cert=example",
                    "obfs4 192.0.2.2:443 FEDCBA cert=example"
                )
            )
        )
        assertEquals(
            Transport.SNOWFLAKE,
            ManualBridgeTransportClassifier.classify(
                listOf("snowflake 192.0.2.3:80 ABCDEF fingerprint=example")
            )
        )
    }

    @Test
    fun rejectsMixedUnknownAndUnspecifiedTransports() {
        assertNull(
            ManualBridgeTransportClassifier.classify(
                listOf("obfs4 192.0.2.1:443 ABCDEF", "snowflake 192.0.2.2:80 FEDCBA")
            )
        )
        assertNull(
            ManualBridgeTransportClassifier.classify(
                listOf("webtunnel 192.0.2.1:443 ABCDEF")
            )
        )
        assertNull(
            ManualBridgeTransportClassifier.classify(
                listOf("192.0.2.1:443 ABCDEF")
            )
        )
        assertNull(ManualBridgeTransportClassifier.classify(listOf("", "# comment only")))
    }
}
