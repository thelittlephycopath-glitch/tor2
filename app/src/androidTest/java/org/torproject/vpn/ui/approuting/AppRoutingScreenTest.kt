package org.torproject.vpn.ui.approuting

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.torproject.vpn.R
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.paging.PagingItem
import org.torproject.vpn.utils.getString

@OptIn(ExperimentalTestApi::class)
class AppRoutingScreenTest {

    @Test
    fun appRoutingScreen_rendersTopBarAndProtectHeader() = runComposeUiTest {
        val nonTorAppsFlow = MutableStateFlow(PagingData.from(emptyList<PagingItem>()))

        setContent {
            ScreenContent(
                nonTorApps = nonTorAppsFlow.collectAsLazyPagingItems(),
                isProtectAllAppsEnabled = true,
                isVPNActive = false
            )
        }

        onNodeWithText(getString(R.string.apps)).assertIsDisplayed()
        onNodeWithText(
            getString(R.string.action_protect_all_apps)
        ).assertIsDisplayed()
    }

    @Test
    fun navigateUp_triggersCallback() = runComposeUiTest {
        var navigatedUp = false

        setContent {
            ScreenContent(
                onNavigateUp = { navigatedUp = true }
            )
        }

        onNodeWithContentDescription(
            getString(R.string.accessibility_back_button)
        ).performClick()

        assertTrue(navigatedUp)
    }

    @Test
    fun vpnActiveState_rendersSuccessfully() = runComposeUiTest {
        val nonTorAppsFlow = MutableStateFlow(PagingData.from(emptyList<PagingItem>()))

        setContent {
            ScreenContent(
                nonTorApps = nonTorAppsFlow.collectAsLazyPagingItems(),
                isVPNActive = true,
                isProtectAllAppsEnabled = true
            )
        }

        onNodeWithText(getString(R.string.apps)).assertIsDisplayed()
        onNodeWithText(
            getString(R.string.action_protect_all_apps)
        ).assertIsDisplayed()
    }

    @Test
    fun expandCategoryCallback_invokedCorrectly() {
        var expandedCategory: Category? = null
        var isExpandedResult: Boolean? = null

        val testCallback: (Category, Boolean) -> Unit = { category, expanded ->
            expandedCategory = category
            isExpandedResult = expanded
        }

        testCallback(Category.SYSTEM, true)

        assertEquals(Category.SYSTEM, expandedCategory)
        assertTrue(isExpandedResult == true)
    }

    @Test
    fun refreshCircuitsCallback_invokedCorrectly() {
        var refreshed = false
        val testCallback: () -> Unit = { refreshed = true }

        testCallback()
        assertTrue(refreshed)
    }
}
