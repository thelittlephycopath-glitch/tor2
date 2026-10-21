package org.torproject.vpn.ui.iconsetting

import android.content.Context
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.torproject.vpn.R
import org.torproject.vpn.ui.iconsetting.model.IconSettingViewModel
import org.torproject.vpn.ui.iconsetting.model.Launcher
import org.torproject.vpn.ui.iconsetting.model.LauncherGarden
import org.torproject.vpn.ui.iconsetting.model.LauncherPingPong
import org.torproject.vpn.ui.iconsetting.model.LauncherWeather

@OptIn(ExperimentalTestApi::class)
class IconSettingScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * Helper to set the content of the screen with a provided state and event handler.
     */
    private fun ComposeUiTest.setTestContent(
        state: IconSettingViewModel.State = IconSettingViewModel.State(),
        onEvent: (IconSettingViewModel.Event) -> Unit = {},
        onNavigateUp: () -> Unit = {}
    ) {
        setContent {
            IconSettingScreen(
                state = state,
                onEvent = onEvent,
                onNavigateUp = onNavigateUp
            )
        }
    }

    @Test
    fun iconSettingScreen_rendersTitleAndHint() = runComposeUiTest {
        setTestContent()

        onNodeWithText(context.getString(R.string.app_icon)).assertIsDisplayed()
        onNodeWithText(
            context.getString(R.string.launcher_hint)
        ).assertIsDisplayed()
    }

    @Test
    fun navigateUp_triggersCallback() = runComposeUiTest {
        var navigatedUp = false
        setTestContent(onNavigateUp = { navigatedUp = true })

        onNodeWithContentDescription(
            context.getString(R.string.accessibility_back_button)
        ).performClick()

        assertTrue(navigatedUp)
    }

    @Test
    fun allLaunchersRendered_withCorrectSelectionState() = runComposeUiTest {
        val state = IconSettingViewModel.State(
            currentLauncher = LauncherGarden,
            launchers = Launcher.entries
        )
        setTestContent(state = state)

        onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        onNodeWithText(
            context.getString(R.string.app_name_garden)
        ).assertIsDisplayed()
        onNodeWithText(
            context.getString(R.string.app_name_weather)
        ).assertIsDisplayed()
        onNodeWithText(
            context.getString(R.string.app_name_pingpong)
        ).assertIsDisplayed()
    }

    @Test
    fun clickingLauncherItem_emitsSetLauncherIconEvent() = runComposeUiTest {
        var receivedEvent: IconSettingViewModel.Event? = null
        setTestContent(onEvent = { event -> receivedEvent = event })

        onNodeWithText(context.getString(R.string.app_name_weather)).performClick()

        assertTrue(receivedEvent is IconSettingViewModel.Event.SetLauncherIconEvent)
        assertEquals(
            LauncherWeather,
            (receivedEvent as IconSettingViewModel.Event.SetLauncherIconEvent).launcher
        )
    }

    @Test
    fun clickingGardenLauncher_emitsGardenEvent() = runComposeUiTest {
        var receivedEvent: IconSettingViewModel.Event? = null
        setTestContent(onEvent = { event -> receivedEvent = event })

        onNodeWithText(context.getString(R.string.app_name_garden)).performClick()

        val event = receivedEvent as? IconSettingViewModel.Event.SetLauncherIconEvent
        assertEquals(LauncherGarden, event?.launcher)
    }

    @Test
    fun clickingPingPongLauncher_emitsPingPongEvent() = runComposeUiTest {
        var receivedEvent: IconSettingViewModel.Event? = null
        setTestContent(onEvent = { event -> receivedEvent = event })

        onNodeWithText(context.getString(R.string.app_name_pingpong)).performClick()

        val event = receivedEvent as? IconSettingViewModel.Event.SetLauncherIconEvent
        assertEquals(LauncherPingPong, event?.launcher)
    }
}
