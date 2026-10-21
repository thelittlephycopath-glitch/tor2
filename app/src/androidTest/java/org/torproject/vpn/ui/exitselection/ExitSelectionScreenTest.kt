package org.torproject.vpn.ui.exitselection

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.torproject.vpn.R
import org.torproject.vpn.ui.exitselection.model.ExitSelectionViewModel
import org.torproject.vpn.utils.Country
import org.torproject.vpn.utils.getString

@OptIn(ExperimentalTestApi::class)
class ExitSelectionScreenTest {

    /**
     * Helper to set the content of the screen with a provided state and event handler.
     */
    private fun ComposeUiTest.setContent(
        state: ExitSelectionViewModel.State = ExitSelectionViewModel.State(),
        onEvent: (ExitSelectionViewModel.Event) -> Unit = {},
        onNavigateUp: () -> Unit = {}
    ) {
        setContent {
            ExitSelectionScreen(
                state = state,
                onEvent = onEvent,
                onNavigateUp = onNavigateUp
            )
        }
    }

    @Test
    fun rendersScreen_displaysTopBarTitleAndDescription() = runComposeUiTest {
        setContent()

        onNodeWithText(
            getString(R.string.exit_location)
        ).assertIsDisplayed()

        onNodeWithText(
            getString(R.string.exit_location_description)
        ).assertIsDisplayed()
    }

    @Test
    fun rendersScreen_displaysAutomaticAndSafestLabelForRandomCountry() = runComposeUiTest {
        val state = ExitSelectionViewModel.State(
            currentExit = Country.RANDOM,
            availableExits = listOf(Country.RANDOM)
        )

        setContent(state = state)

        onNodeWithText(
            getString(R.string.exit_location_automatic)
        ).assertIsDisplayed()

        onNodeWithText(
            getString(R.string.exit_location_safest)
        ).assertIsDisplayed()
    }

    @Test
    fun rendersScreen_displaysAvailableExits() = runComposeUiTest {
        val state = ExitSelectionViewModel.State(
            currentExit = Country.GERMANY,
            availableExits = listOf(
                Country.RANDOM,
                Country.GERMANY,
                Country.JAPAN
            )
        )

        setContent(state = state)

        onNodeWithText(
            Country.GERMANY.locale()!!.displayCountry
        ).assertIsDisplayed()

        onNodeWithText(
            Country.JAPAN.locale()!!.displayCountry
        ).assertIsDisplayed()
    }

    @Test
    fun rendersScreen_emptyExits_rendersHeaderOnlyWithoutCrashing() = runComposeUiTest {
        val state = ExitSelectionViewModel.State(
            currentExit = Country.RANDOM,
            availableExits = emptyList()
        )

        setContent(state = state)

        onNodeWithText(
            getString(R.string.exit_location_description)
        ).assertIsDisplayed()
    }

    @Test
    fun navigateUp_clicksBackButton_triggersOnNavigateUpCallback() = runComposeUiTest {
        var navigatedUp = false

        setContent(
            state = ExitSelectionViewModel.State(),
            onEvent = {}
        ) {
            navigatedUp = true
        }

        onNodeWithContentDescription(
            getString(R.string.accessibility_back_button)
        ).performClick()

        assertTrue(navigatedUp)
    }

    @Test
    fun selectExit_clicksCountryItem_emitsSelectExitEvent() = runComposeUiTest {
        var receivedEvent: ExitSelectionViewModel.Event? = null
        val state = ExitSelectionViewModel.State(
            currentExit = Country.RANDOM,
            availableExits = listOf(
                Country.RANDOM,
                Country.GERMANY
            )
        )

        setContent(
            state = state,
            onEvent = { event -> receivedEvent = event }
        )

        waitForIdle()
        onNodeWithText(
            Country.GERMANY.locale()!!.displayCountry
        ).performClick()
        waitForIdle()

        assertEquals(
            ExitSelectionViewModel.Event.SelectExit(Country.GERMANY),
            receivedEvent
        )
    }
}
