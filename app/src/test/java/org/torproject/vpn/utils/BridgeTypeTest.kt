package org.torproject.vpn.utils

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.torproject.vpn.R
import org.torproject.vpn.utils.PreferenceHelper.Companion.BridgeType

class BridgeTypeTest {

    @Test
    fun getDisplayLabelRes_None() {
        val (resId, argResId) = BridgeType.None.getDisplayLabelRes(emptyList())
        assertEquals(R.string.connect_direct_to_tor, resId)
        assertNull(argResId)
    }

    @Test
    fun getDisplayLabelRes_Snowflake() {
        val (resId, argResId) = BridgeType.Snowflake.getDisplayLabelRes(emptyList())
        assertEquals(R.string.snowflake_built_in, resId)
        assertNull(argResId)
    }

    @Test
    fun getDisplayLabelRes_Obfs4() {
        val (resId, argResId) = BridgeType.Obfs4.getDisplayLabelRes(emptyList())
        assertEquals(R.string.obfs4_built_in, resId)
        assertNull(argResId)
    }

    @Test
    fun getDisplayLabelRes_Manual_MultipleTypes() {
        val bridgeLines = listOf(
            "obfs4 1.2.3.4:1234 1234567890ABCDEF1234567890ABCDEF12345678 cert=abc iat-mode=0",
            "snowflake 1.2.3.4:1234 1234567890ABCDEF1234567890ABCDEF12345678"
        )

        val (resId, argResId) = BridgeType.Manual.getDisplayLabelRes(bridgeLines)
        assertEquals(R.string.multiple_added_by_you, resId)
        assertNull(argResId)
    }

    @Test
    fun getDisplayLabelRes_Manual_SingleType_Obfs4() {
        val bridgeLines = listOf(
            "obfs4 1.2.3.4:1234 1234567890ABCDEF1234567890ABCDEF12345678 cert=abc iat-mode=0"
        )

        val (resId, argResId) = BridgeType.Manual.getDisplayLabelRes(bridgeLines)
        assertEquals(R.string.bridge_type_added_by_you, resId)
        assertEquals(R.string.obfs4_label, argResId)
    }

    @Test
    fun getDisplayLabelRes_Manual_SingleType_Obfs4_MultipleLines() {
        val bridgeLines = listOf(
            "obfs4 1.2.3.4:1234 1234567890ABCDEF1234567890ABCDEF12345678 cert=abc iat-mode=0",
            "obfs4 5.6.7.8:5678 1234567890ABCDEF1234567890ABCDEF12345678 cert=def iat-mode=0"
        )

        val (resId, argResId) = BridgeType.Manual.getDisplayLabelRes(bridgeLines)
        assertEquals(R.string.bridge_type_added_by_you, resId)
        assertEquals(R.string.obfs4_label, argResId)
    }

    @Test
    fun getDisplayLabelRes_Manual_NoLines() {
        val (resId, argResId) = BridgeType.Manual.getDisplayLabelRes(emptyList())
        assertEquals(R.string.bridge_type_added_by_you, resId)
        assertEquals(R.string.bridges, argResId)
    }
}
