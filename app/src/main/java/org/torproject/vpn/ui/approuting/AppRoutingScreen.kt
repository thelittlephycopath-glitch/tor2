package org.torproject.vpn.ui.approuting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.extensions.emptyPagingItems
import org.torproject.vpn.paging.PagingItem
import org.torproject.vpn.ui.approuting.menu.AppRoutingMenu
import org.torproject.vpn.ui.approuting.menu.MenuItem
import org.torproject.vpn.ui.approuting.model.AppRoutingViewModel
import org.torproject.vpn.ui.composable.AppListItem
import org.torproject.vpn.ui.composable.CategoryHeader
import org.torproject.vpn.ui.composable.ContainedLoadingIndicator
import org.torproject.vpn.ui.composable.TopAppBar
import org.torproject.vpn.ui.composable.TorListItem
import org.torproject.vpn.ui.preview.AppPreviewProvider
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.heightProtectHeader
import org.torproject.vpn.ui.theme.spacingSizeVerySmall

@Composable
fun AppRoutingScreen(
    state: AppRoutingViewModel.State,
    nonTorApps: LazyPagingItems<PagingItem>,
    torApps: LazyPagingItems<App>,
    onEvent: (AppRoutingViewModel.Event) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToAppDetail: (packageName: String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    ScreenContent(
        nonTorApps = nonTorApps,
        torApps = torApps,
        categoryCount = state.categoryCount,
        isProtectAllAppsEnabled = state.isProtectAllAppsEnabled,
        onProtectAllAppsToggled = { enabled ->
            onEvent(AppRoutingViewModel.Event.ProtectAllApps(enabled))
        },
        onNavigateUp = onNavigateUp,
        onNavigateToAppDetail = onNavigateToAppDetail,
        onNavigateToSearch = onNavigateToSearch,
        isVPNActive = state.isVPNActive,
        isCategoryExpanded = { category -> state.categoryExpansionState.getValue(category) },
        onRefreshCircuits = { onEvent(AppRoutingViewModel.Event.RefreshCircuits) },
        onUpdateRoutingStatus = { packageName, enabled ->
            onEvent(AppRoutingViewModel.Event.UpdateRouting(packageName, enabled))
        },
        onExpandCategory = { category, expanded ->
            onEvent(AppRoutingViewModel.Event.UpdateExpansionState(category, expanded))
        }
    )
}

@Composable
fun ScreenContent(
    nonTorApps: LazyPagingItems<PagingItem> = emptyPagingItems(),
    torApps: LazyPagingItems<App> = emptyPagingItems(),
    categoryCount: Map<Category, Int> = emptyMap(),
    isVPNActive: Boolean = false,
    isProtectAllAppsEnabled: Boolean = false,
    onProtectAllAppsToggled: (enabled: Boolean) -> Unit = {},
    onRefreshCircuits: () -> Unit = {},
    onUpdateRoutingStatus: (packageName: String, enabled: Boolean) -> Unit = { _, _ -> },
    onNavigateUp: () -> Unit = {},
    onNavigateToAppDetail: (packageName: String) -> Unit = {},
    isCategoryExpanded: (category: Category) -> Boolean = { true },
    onExpandCategory: (category: Category, expanded: Boolean) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {}
) {
    /*
     * For some reason paging3 frequently out-of-nowhere invalidates the list which causes
     * the loading animation to play again even if the keys are same causing a glitching effect.
     *
     * Save the initial loading state to make sure we don't replay the loading animation again.
     */
    var initialLoad by rememberSaveable { mutableStateOf(true) }
    var shouldShowRefreshDialog by rememberSaveable { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current

    if (shouldShowRefreshDialog) {
        RefreshDialog(
            onDismiss = { shouldShowRefreshDialog = false },
            onConfirm = {
                shouldShowRefreshDialog = false
                onRefreshCircuits()
            }
        )
    }

    @Composable
    fun Menu() {
        AppRoutingMenu(isVPNActive = isVPNActive) { menuItem ->
            when (menuItem) {
                MenuItem.REFRESH_CIRCUITS -> shouldShowRefreshDialog = true
                MenuItem.SEARCH -> onNavigateToSearch()
            }
        }
    }

    @Composable
    fun ProtectHeader() {
        Card(
            modifier = Modifier.padding(dimensionResource(R.dimen.default_padding)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = heightProtectHeader)
                    .padding(
                        horizontal = dimensionResource(R.dimen.super_toggle_padding),
                        vertical = dimensionResource(R.dimen.compact_padding)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.action_protect_all_apps),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Switch(
                    checked = isProtectAllAppsEnabled,
                    onCheckedChange = onProtectAllAppsToggled
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.apps),
                onNavigateUp = onNavigateUp,
                actions = { Menu() }
            )
        }
    ) { paddingValues ->
        when {
            nonTorApps.loadState.refresh is LoadState.Loading && initialLoad -> {
                ContainedLoadingIndicator()
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            end = paddingValues.calculateEndPadding(layoutDirection)
                        ),
                    contentPadding = PaddingValues(
                        bottom = paddingValues.calculateBottomPadding()
                    )
                ) {
                    initialLoad = false

                    item(key = "ProtectHeader") {
                        ProtectHeader()
                    }

                    if (torApps.itemCount > 0) {
                        item(key = "torapps") {
                            CategoryHeader(category = stringResource(R.string.app_routing_tor_apps))

                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = dimensionResource(R.dimen.default_padding),
                                        vertical = dimensionResource(R.dimen.compact_padding)
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(spacingSizeVerySmall)
                            ) {
                                items(
                                    count = torApps.itemCount,
                                    key = torApps.itemKey { it.packageName },
                                    contentType = { "app" }
                                ) { index ->
                                    torApps[index]?.let { app ->
                                        TorListItem(
                                            app = app,
                                            onClick = { onNavigateToAppDetail(app.packageName) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(
                        count = nonTorApps.itemCount,
                        key = nonTorApps.itemKey { it.itemKey },
                        contentType = nonTorApps.itemContentType { it.contentType }
                    ) { index ->
                        nonTorApps[index]?.let { pagingItem ->
                            when (pagingItem) {
                                is Category -> {
                                    CategoryHeader(
                                        category = stringResource(pagingItem.localizedTitle),
                                        canExpand = pagingItem.canExpand,
                                        isExpanded = isCategoryExpanded(pagingItem),
                                        listSize = categoryCount.getOrElse(pagingItem) { 0 },
                                        onExpand = { expand ->
                                            onExpandCategory(pagingItem, expand)
                                        }
                                    )
                                }

                                is App -> {
                                    AppListItem(
                                        app = pagingItem,
                                        onClick = { onNavigateToAppDetail(pagingItem.packageName) },
                                        onRoutingToggled = { enabled ->
                                            onUpdateRoutingStatus(pagingItem.packageName, enabled)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppRoutingScreenPreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    val torAppsList = List(2) { app.copy(packageName = Random.nextInt().toString()) }
    val torAppsFlow = MutableStateFlow(PagingData.from(torAppsList))

    val nonTorAppsList = Category.entries
        .filter { it != Category.TOR }
        .flatMap { category ->
            val header = listOf(category as PagingItem)
            val apps = List(4) {
                app.copy(
                    packageName = Random.nextInt().toString(),
                    isRoutingEnabled = true,
                    category = category
                )
            }
            header + apps
        }

    val nonTorAppsFlow = MutableStateFlow(PagingData.from(nonTorAppsList))
    ScreenContent(
        torApps = torAppsFlow.collectAsLazyPagingItems(),
        nonTorApps = nonTorAppsFlow.collectAsLazyPagingItems(),
        isProtectAllAppsEnabled = true,
        isVPNActive = true
    )
}
