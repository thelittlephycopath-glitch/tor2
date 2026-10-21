package org.torproject.vpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import org.torproject.vpn.AppContainer
import org.torproject.vpn.ui.about.AboutScreen
import org.torproject.vpn.ui.appdetail.AppDetailScreen
import org.torproject.vpn.ui.approuting.AppRoutingScreen
import org.torproject.vpn.ui.appsearch.AppSearchScreen
import org.torproject.vpn.ui.bridgebot.BridgeBotScreen
import org.torproject.vpn.ui.exitselection.ExitSelectionScreen
import org.torproject.vpn.ui.iconsetting.IconSettingScreen

/**
 * Navigation display for compose screens
 * @param startDestination Starting destination for the app
 * @param onCloseRequest Callback to close app when there are no more screens left in backstack
 */
@Composable
fun NavDisplay(
    startDestination: Destination,
    onCloseRequest: () -> Unit,
    appContainer: AppContainer
) {
    val backstack = rememberNavBackStack(startDestination)

    fun onNavigateUp() = if (backstack.size > 1) backstack.removeLastOrNull() else onCloseRequest()

    NavDisplay(
        backStack = backstack,
        onBack = ::onNavigateUp,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Destination.BridgeBot> {
                val viewModel = viewModel {
                    appContainer.bridgeBotViewModel()
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                BridgeBotScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onNavigateUp = ::onNavigateUp
                )
            }

            entry<Destination.AppDetail> { destination ->
                val viewModel = viewModel(key = destination.packageName) {
                    appContainer.appDetailViewModel(destination.packageName)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                AppDetailScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onNavigateUp = ::onNavigateUp
                )
            }

            entry<Destination.AppRouting> {
                val viewModel = viewModel {
                    appContainer.appRoutingViewModel()
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                val nonTorApps = viewModel.nonTorApps.collectAsLazyPagingItems()
                val torApps = viewModel.torApps.collectAsLazyPagingItems()
                AppRoutingScreen(
                    state = state,
                    nonTorApps = nonTorApps,
                    torApps = torApps,
                    onEvent = viewModel::onEvent,
                    onNavigateUp = ::onNavigateUp,
                    onNavigateToSearch = { backstack.add(Destination.AppSearch) },
                    onNavigateToAppDetail = { packageName ->
                        backstack.add(Destination.AppDetail(packageName))
                    }
                )
            }

            entry<Destination.AppSearch> {
                val viewModel = viewModel {
                    appContainer.appSearchViewModel(createSavedStateHandle())
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                val searchResults = viewModel.searchResults.collectAsLazyPagingItems()
                AppSearchScreen(
                    state = state,
                    queryState = viewModel.queryState,
                    searchResults = searchResults,
                    onEvent = viewModel::onEvent,
                    onNavigateUp = ::onNavigateUp,
                    onNavigateToAppDetail = { packageName ->
                        backstack.add(Destination.AppDetail(packageName))
                    }
                )
            }

            entry<Destination.About> {
                AboutScreen(onNavigateUp = ::onNavigateUp)
            }

            entry<Destination.IconSetting> {
                val viewModel = viewModel {
                    appContainer.iconSettingViewModel()
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                IconSettingScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onNavigateUp = ::onNavigateUp
                )
            }

            entry<Destination.ExitSelection> {
                val viewModel = viewModel {
                    appContainer.exitSelectionViewModel()
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                ExitSelectionScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onNavigateUp = ::onNavigateUp
                )
            }
        }
    )
}
