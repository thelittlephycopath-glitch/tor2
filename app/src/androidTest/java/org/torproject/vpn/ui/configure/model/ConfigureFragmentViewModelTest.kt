package org.torproject.vpn.ui.configure.model

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
class ConfigureFragmentViewModelTest {

    private lateinit var viewModel: ConfigureFragmentViewModel
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var context: android.app.Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferenceHelper = PreferenceHelper(context)
        preferenceHelper.clear()
        viewModel = ConfigureFragmentViewModel(context)
    }

    @Test
    fun selectedBridgeType_none_returnsDirectToTorString() {
        preferenceHelper.bridgeType = BridgeType.None
        val actual = viewModel.selectedBridgeType
        val expected = context.getString(R.string.connect_direct_to_tor)
        assertEquals(expected, actual)
    }

    @Test
    fun selectedBridgeType_snowflake_returnsSnowflakeBuiltInString() {
        preferenceHelper.bridgeType = BridgeType.Snowflake
        val actual = viewModel.selectedBridgeType
        val expected = context.getString(R.string.snowflake_built_in)
        assertEquals(expected, actual)
    }

    @Test
    fun selectedBridgeType_obfs4_returnsObfs4BuiltInString() {
        preferenceHelper.bridgeType = BridgeType.Obfs4
        val actual = viewModel.selectedBridgeType
        val expected = context.getString(R.string.obfs4_built_in)
        assertEquals(expected, actual)
    }

    @Test
    fun selectedBridgeType_manualSingleObfs4_returnsObfs4AddedByYouString() {
        val obfs4Bridge = "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgO0aRSMvjkL8GucHAnHpc9S2V63K9SPS969D"
        preferenceHelper.bridgeType = BridgeType.Manual
        preferenceHelper.bridgeLines = listOf(obfs4Bridge)

        val actual = viewModel.selectedBridgeType
        val expected = context.getString(
            R.string.bridge_type_added_by_you,
            context.getString(R.string.obfs4_label)
        ).trim()
        assertEquals(expected, actual)
    }

    @Test
    fun selectedBridgeType_manualSingleVanilla_returnsBridgesAddedByYouString() {
        val vanillaBridge = "192.0.2.1:443"
        preferenceHelper.bridgeType = BridgeType.Manual
        preferenceHelper.bridgeLines = listOf(vanillaBridge)

        val actual = viewModel.selectedBridgeType
        val expected = context.getString(
            R.string.bridge_type_added_by_you,
            context.getString(R.string.bridges)
        ).trim()
        assertEquals(expected, actual)
    }

    @Test
    fun selectedBridgeType_multipleBridgeTypes_returnsMultipleBridgesString() {
        preferenceHelper.bridgeType = BridgeType.Manual
        preferenceHelper.bridgeLines = listOf(
            "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgO0aRSMvjkL8GucHAnHpc9S2V63K9SPS969D",
            "obfs4 209.148.46.65:443 74FAD13168806246602538555B5521A0383A1875 cert=ssH+9rP8dG2NLDN2XuFw63hIO/9MNNSR6Y66yXl9pYm6e40yZ1/I/Oq8f5uObeR",
            "webtunnel 10.0.0.2:443 10A6CD36A537FCE513A322361547444B393989F0 url=https://example.com/webtunnel"

        )

        // When getting selectedBridgeType
        val actual = viewModel.selectedBridgeType

        // Then it should be "Multiple bridges (added by you)"
        val expected = context.getString(R.string.multiple_added_by_you)
        assertEquals(expected, actual)
    }
}
