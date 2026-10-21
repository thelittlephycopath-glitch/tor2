package org.torproject.vpn.ui.appsearch.model

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.torproject.vpn.database.app.App
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.utils.PreferenceHelper

@OptIn(ExperimentalCoroutinesApi::class)
class AppSearchViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var vpnStarted = false

    private val pagingSource = mockk<PagingSource<Int, App>>(relaxed = true)
    private val appManager = mockk<AppManager> {
        every { search(any()) } returns pagingSource
        coEvery { protectApp(any(), any()) } returns Unit
    }

    private var recentSearchesStorage = emptyList<String>()
    private val preferenceHelper = mockk<PreferenceHelper>(relaxed = true) {
        every { recentSearches } answers { recentSearchesStorage }
        every { recentSearches = any() } answers {
            recentSearchesStorage = firstArg()
        }
    }

    private lateinit var viewModel: AppSearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun createViewModel(isVoiceSearchAvailable: Boolean = false) = AppSearchViewModel(
        appManager = appManager,
        preferenceHelper = preferenceHelper,
        startVpn = { vpnStarted = true },
        isVoiceSearchAvailable = isVoiceSearchAvailable,
        savedStateHandle = SavedStateHandle()
    )

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun initialState() = runViewModelTest {
        val state = viewModel.state.value
        assertTrue(state.recentQueries.isEmpty())
        assertFalse(state.isVoiceSearchAvailable)
        assertTrue(viewModel.queryState.text.isEmpty())
    }

    @Test
    fun initialState_voiceSearchAvailable() = runViewModelTest {
        viewModel.viewModelScope.cancel()
        viewModel = createViewModel(isVoiceSearchAvailable = true)

        assertTrue(viewModel.state.value.isVoiceSearchAvailable)
    }

    @Test
    fun updateRouting() = runViewModelTest {
        viewModel.onEvent(AppSearchViewModel.Event.UpdateRouting("com.example.app", true))
        runCurrent()

        coVerify { appManager.protectApp(true, "com.example.app") }
        assertTrue(vpnStarted)
    }

    @Test
    fun submitSearch_trimsQuerySetsTextAndSaves() = runViewModelTest {
        viewModel.onEvent(AppSearchViewModel.Event.SubmitSearch("  Tor Browser  "))
        runCurrent()

        assertEquals("Tor Browser", viewModel.queryState.text.toString())
        assertEquals(listOf("Tor Browser"), recentSearchesStorage)
    }

    @Test
    fun submitSearch_ignoresBlankAndDuplicates() = runViewModelTest {
        viewModel.onEvent(AppSearchViewModel.Event.SubmitSearch("Tor Browser"))
        runCurrent()
        assertEquals(listOf("Tor Browser"), recentSearchesStorage)

        viewModel.onEvent(AppSearchViewModel.Event.SubmitSearch("   "))
        runCurrent()
        assertEquals(listOf("Tor Browser"), recentSearchesStorage)

        viewModel.onEvent(AppSearchViewModel.Event.SubmitSearch("tor browser "))
        runCurrent()
        assertEquals(listOf("Tor Browser"), recentSearchesStorage)
    }

    @Test
    fun submitSearch_keepsAtMostFiveQueries() = runViewModelTest {
        (1..6).forEach { viewModel.onEvent(AppSearchViewModel.Event.SubmitSearch("query $it")) }
        runCurrent()

        assertEquals(
            listOf("query 6", "query 5", "query 4", "query 3", "query 2"),
            recentSearchesStorage
        )
    }

    @Test
    fun openApp_savesAppName() = runViewModelTest {
        viewModel.onEvent(AppSearchViewModel.Event.OpenApp("Tor Browser"))
        runCurrent()

        assertEquals(listOf("Tor Browser"), recentSearchesStorage)
    }

    @Test
    fun clearQuery_clearsText() = runViewModelTest {
        viewModel.queryState.setTextAndPlaceCursorAtEnd("Tor")

        viewModel.onEvent(AppSearchViewModel.Event.ClearQuery)

        assertTrue(viewModel.queryState.text.isEmpty())
    }

    @Test
    fun clearSearchQueries() = runViewModelTest {
        viewModel.onEvent(AppSearchViewModel.Event.SubmitSearch("Tor Browser"))
        runCurrent()

        viewModel.onEvent(AppSearchViewModel.Event.ClearSearchQueries)
        runCurrent()

        assertTrue(recentSearchesStorage.isEmpty())
    }
}
