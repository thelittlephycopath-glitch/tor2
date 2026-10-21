package org.torproject.vpn.vpn

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.torproject.vpn.circumvention.Bridge
import org.torproject.vpn.circumvention.Bridges
import org.torproject.vpn.circumvention.CircumventionApiManager
import org.torproject.vpn.circumvention.Error
import org.torproject.vpn.circumvention.SettingsResponse

class GetBridgeLinesTest {

    private val apiManager = mockk<CircumventionApiManager>()
    private val getBridgeLines = GetBridgeLines(apiManager)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun invoke_returnsBridgeStrings() = runTest {
        val onResultSlot = slot<(SettingsResponse?) -> Unit>()
        every {
            apiManager.getDefaults(any(), capture(onResultSlot), any())
        } answers {
            onResultSlot.captured.invoke(
                SettingsResponse(
                    settings = listOf(
                        Bridges(
                            bridges = Bridge(
                                type = "obfs4",
                                source = "bridgedb",
                                bridge_strings = listOf("obfs4 1.2.3.4:80", "obfs4 5.6.7.8:80")
                            )
                        )
                    ),
                    errors = null
                )
            )
        }

        val result = getBridgeLines()
        assertEquals(listOf("obfs4 1.2.3.4:80", "obfs4 5.6.7.8:80"), result)
    }

    @Test
    fun invoke_fallbackToSettingsList_whenBridgeDBListIsEmpty() = runTest {
        val onResultSlot = slot<(SettingsResponse?) -> Unit>()
        every {
            apiManager.getDefaults(any(), capture(onResultSlot), any())
        } answers {
            onResultSlot.captured.invoke(
                SettingsResponse(
                    settings = listOf(
                        Bridges(
                            bridges = Bridge(
                                type = "obfs4",
                                source = "builtin",
                                bridge_strings = listOf("obfs4 9.9.9.9:80")
                            )
                        )
                    ),
                    errors = null
                )
            )
        }

        val result = getBridgeLines()
        assertEquals(listOf("obfs4 9.9.9.9:80"), result)
    }

    @Test
    fun invoke_nullResponse_throwsIOException() = runTest {
        val onResultSlot = slot<(SettingsResponse?) -> Unit>()
        every {
            apiManager.getDefaults(any(), capture(onResultSlot), any())
        } answers {
            onResultSlot.captured.invoke(null)
        }

        assertFailsWith<IOException> {
            getBridgeLines()
        }
    }

    @Test
    fun invoke_errorsResponse_throwsIOException() = runTest {
        val onResultSlot = slot<(SettingsResponse?) -> Unit>()
        every {
            apiManager.getDefaults(any(), capture(onResultSlot), any())
        } answers {
            onResultSlot.captured.invoke(
                SettingsResponse(
                    settings = null,
                    errors = listOf(Error(code = 400, detail = "Rate limited"))
                )
            )
        }

        val exception = assertFailsWith<IOException> {
            getBridgeLines()
        }
        assertEquals("Error fetching bridges: Rate limited", exception.message)
    }

    @Test
    fun invoke_apiError_throwsException() = runTest {
        val onErrorSlot = slot<(Throwable) -> Unit>()
        every {
            apiManager.getDefaults(any(), any(), capture(onErrorSlot))
        } answers {
            onErrorSlot.captured.invoke(IOException("Connection failed"))
        }

        val exception = assertFailsWith<IOException> {
            getBridgeLines()
        }
        assertEquals("Connection failed", exception.message)
    }
}
