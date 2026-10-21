package org.torproject.vpn.ui.home.model

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.vpn.R
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.BridgeType

@RunWith(AndroidJUnit4::class)
@Suppress("ktlint:standard:max-line-length")
class ConnectFragmentViewModelTest {

    private lateinit var viewModel: ConnectFragmentViewModel
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var context: android.app.Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferenceHelper = PreferenceHelper(context)
        preferenceHelper.clear()

        // Ensure we are on the main thread for ViewModel creation if it touches LiveData
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ConnectFragmentViewModel(context)
        }
    }

    @Test
    fun getConnectionString_none_returnsDirectToTorString() {
        preferenceHelper.bridgeType = BridgeType.None

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.updateConnectionLabel()
        }

        val expected = context.getString(R.string.connect_direct_to_tor)
        var actual: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            actual = viewModel.connectionHint.value
        }
        assertEquals(expected, actual)
    }

    @Test
    fun getConnectionString_snowflake_returnsSnowflakeBuiltInString() {
        preferenceHelper.bridgeType = BridgeType.Snowflake

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.updateConnectionLabel()
        }

        val expected = context.getString(R.string.snowflake_built_in)
        var actual: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            actual = viewModel.connectionHint.value
        }
        assertEquals(expected, actual)
    }

    @Test
    fun getConnectionString_obfs4_returnsObfs4BuiltInString() {
        preferenceHelper.bridgeType = BridgeType.Obfs4

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.updateConnectionLabel()
        }

        val expected = context.getString(R.string.obfs4_built_in)
        var actual: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            actual = viewModel.connectionHint.value
        }
        assertEquals(expected, actual)
    }

    @Test
    fun getConnectionString_manualSingleObfs4_returnsObfs4AddedByYouString() {
        val obfs4Bridge = "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgO0aRSMvjkL8GucHAnHpc9S2V63K9SPS969D"
        preferenceHelper.bridgeType = BridgeType.Manual
        preferenceHelper.bridgeLines = listOf(obfs4Bridge)

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.updateConnectionLabel()
        }

        val expected = context.getString(
            R.string.bridge_type_added_by_you,
            context.getString(R.string.obfs4_label)
        ).trim()
        var actual: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            actual = viewModel.connectionHint.value
        }
        assertEquals(expected, actual)
    }

    @Test
    fun getConnectionString_manualSingleVanilla_returnsBridgesAddedByYouString() {
        val vanillaBridge = "192.0.2.1:443"
        preferenceHelper.bridgeType = BridgeType.Manual
        preferenceHelper.bridgeLines = listOf(vanillaBridge)

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.updateConnectionLabel()
        }

        val expected = context.getString(
            R.string.bridge_type_added_by_you,
            context.getString(R.string.bridges)
        ).trim()
        var actual: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            actual = viewModel.connectionHint.value
        }
        assertEquals(expected, actual)
    }

    @Test
    fun getConnectionString_multipleBridgeTypes_returnsMultipleBridgesString() {
        // Given multiple bridge types are added manually
        val obfs4Bridge = "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgO0aRSMvjkL8GucHAnHpc9S2V63K9SPS969D"
        val vanillaBridge = "192.0.2.1:443"

        preferenceHelper.bridgeType = BridgeType.Manual
        preferenceHelper.bridgeLines = listOf(obfs4Bridge, vanillaBridge)

        // When updating connection label
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.updateConnectionLabel()
        }

        // Then connection hint should be "Multiple bridges (added by you)"
        val expected = context.getString(R.string.multiple_added_by_you)

        // Wait a bit for postValue to settle or use a simple observer
        var actual: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            actual = viewModel.connectionHint.value
        }

        assertEquals(expected, actual)
    }
}
