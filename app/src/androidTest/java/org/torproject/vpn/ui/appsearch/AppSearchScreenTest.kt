package org.torproject.vpn.ui.appsearch

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.extensions.emptyPagingItems
import org.torproject.vpn.ui.appsearch.model.AppSearchViewModel
import org.torproject.vpn.utils.getString

@OptIn(ExperimentalTestApi::class)
class AppSearchScreenTest {

    @Test
    fun rendersScreen_defaultState() = runComposeUiTest {
        val state = AppSearchViewModel.State()

        setContent {
            AppSearchScreen(
                state = state,
                queryState = TextFieldState(),
                searchResults = emptyPagingItems(),
                onEvent = {},
                onNavigateUp = {},
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithText(getString(R.string.search_hint)).assertIsDisplayed()
        onNodeWithContentDescription(
            getString(R.string.accessibility_back_button)
        ).assertIsDisplayed()
    }

    @Test
    fun navigateUp_clicksBackButton_triggersOnNavigateUpCallback() = runComposeUiTest {
        var navigatedUp = false
        val state = AppSearchViewModel.State()

        setContent {
            AppSearchScreen(
                state = state,
                queryState = TextFieldState(),
                searchResults = emptyPagingItems(),
                onEvent = {},
                onNavigateUp = { navigatedUp = true },
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithContentDescription(
            getString(R.string.accessibility_back_button)
        ).performClick()

        assertTrue(navigatedUp)
    }

    @Test
    fun recentSearches_displayedWhenQueryIsBlank() = runComposeUiTest {
        val state = AppSearchViewModel.State(
            recentQueries = listOf("Tor VPN", "Tor Browser")
        )

        setContent {
            AppSearchScreen(
                state = state,
                queryState = TextFieldState(),
                searchResults = emptyPagingItems(),
                onEvent = {},
                onNavigateUp = {},
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithText(
            getString(R.string.category_header_recent)
        ).assertIsDisplayed()
        onNodeWithText("Tor VPN").assertIsDisplayed()
        onNodeWithText("Tor Browser").assertIsDisplayed()
        onNodeWithText(
            getString(R.string.action_clear_all)
        ).assertIsDisplayed()
    }

    @Test
    fun clearAllButton_emitsClearSearchQueriesEvent() = runComposeUiTest {
        var receivedEvent: AppSearchViewModel.Event? = null
        val state = AppSearchViewModel.State(
            recentQueries = listOf("Tor VPN", "Tor Browser")
        )

        setContent {
            AppSearchScreen(
                state = state,
                queryState = TextFieldState(),
                searchResults = emptyPagingItems(),
                onEvent = { event -> receivedEvent = event },
                onNavigateUp = {},
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithText(
            getString(R.string.action_clear_all)
        ).performClick()

        assertEquals(AppSearchViewModel.Event.ClearSearchQueries, receivedEvent)
    }

    @Test
    fun recentSearchItemClick_emitsSubmitSearchEvent() = runComposeUiTest {
        val events = mutableListOf<AppSearchViewModel.Event>()
        val state = AppSearchViewModel.State(
            recentQueries = listOf("Tor VPN")
        )

        setContent {
            AppSearchScreen(
                state = state,
                queryState = TextFieldState(),
                searchResults = emptyPagingItems(),
                onEvent = { event -> events.add(event) },
                onNavigateUp = {},
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithText("Tor VPN").performClick()

        assertEquals(listOf(AppSearchViewModel.Event.SubmitSearch("Tor VPN")), events)
    }

    @Test
    fun searchQueryWithNoResults_displaysErrorState() = runComposeUiTest {
        setContent {
            TestSearchResultsContainer(initialQuery = "NonExistentApp", apps = emptyList())
        }

        onNodeWithText(
            getString(R.string.search_error_title)
        ).assertIsDisplayed()
        onNodeWithText(
            getString(R.string.search_error_message)
        ).assertIsDisplayed()
    }

    @Test
    fun searchQueryWithResults_displaysResultsList() = runComposeUiTest {
        val testApp = App(
            packageName = "org.torproject.torbrowser",
            name = "Tor Browser",
            category = Category.TOR,
            isRoutingEnabled = true,
            uid = 10001
        )

        setContent {
            TestSearchResultsContainer(initialQuery = "Tor", apps = listOf(testApp))
        }

        onNodeWithText(
            getString(R.string.category_header_results)
        ).assertIsDisplayed()
        onNodeWithText("Tor Browser").assertIsDisplayed()
    }

    @Test
    fun clickingResultItem_emitsOpenAppAndNavigatesToDetail() = runComposeUiTest {
        var navigatedPackageName: String? = null
        val events = mutableListOf<AppSearchViewModel.Event>()
        val testApp = App(
            packageName = "org.torproject.torbrowser",
            name = "Tor Browser",
            category = Category.TOR,
            isRoutingEnabled = true,
            uid = 10001
        )

        setContent {
            TestSearchResultsContainer(
                initialQuery = "Tor",
                apps = listOf(testApp),
                onEvent = { events.add(it) },
                onNavigateToAppDetail = { navigatedPackageName = it }
            )
        }

        onNodeWithText("Tor Browser").performClick()

        assertEquals("org.torproject.torbrowser", navigatedPackageName)
        assertTrue(events.any { it == AppSearchViewModel.Event.OpenApp("Tor Browser") })
    }

    @Test
    fun typeQueryInSearchField_updatesQueryState() = runComposeUiTest {
        val queryState = TextFieldState()

        setContent {
            AppSearchScreen(
                state = AppSearchViewModel.State(),
                queryState = queryState,
                searchResults = emptyPagingItems(),
                onEvent = {},
                onNavigateUp = {},
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithText(
            getString(R.string.search_hint)
        ).performTextInput("Tor")

        assertEquals("Tor", queryState.text.toString())
    }

    @Test
    fun clearButton_emitsClearQueryEvent() = runComposeUiTest {
        val events = mutableListOf<AppSearchViewModel.Event>()

        setContent {
            AppSearchScreen(
                state = AppSearchViewModel.State(),
                queryState = TextFieldState("Tor"),
                searchResults = emptyPagingItems(),
                onEvent = { events.add(it) },
                onNavigateUp = {},
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithContentDescription(
            getString(R.string.action_clear)
        ).performClick()

        assertEquals(listOf(AppSearchViewModel.Event.ClearQuery), events)
    }

    @Test
    fun voiceButton_shownOnlyWhenAvailable() = runComposeUiTest {
        setContent {
            AppSearchScreen(
                state = AppSearchViewModel.State(isVoiceSearchAvailable = true),
                queryState = TextFieldState(),
                searchResults = emptyPagingItems(),
                onEvent = {},
                onNavigateUp = {},
                onNavigateToAppDetail = {}
            )
        }

        onNodeWithContentDescription(
            getString(R.string.voice_recognition)
        ).assertIsDisplayed()
    }

    @Composable
    private fun TestSearchResultsContainer(
        initialQuery: String,
        apps: List<App>,
        onEvent: (AppSearchViewModel.Event) -> Unit = {},
        onNavigateToAppDetail: (String) -> Unit = {}
    ) {
        val results = MutableStateFlow(PagingData.from(apps)).collectAsLazyPagingItems()
        AppSearchScreen(
            state = AppSearchViewModel.State(),
            queryState = remember { TextFieldState(initialQuery) },
            searchResults = results,
            onEvent = onEvent,
            onNavigateUp = {},
            onNavigateToAppDetail = onNavigateToAppDetail
        )
    }
}
