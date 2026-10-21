package org.torproject.vpn.ui.appdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import kotlin.uuid.Uuid
import org.torproject.onionmasq.circuit.CircuitCountryCodes
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.ui.appdetail.menu.AppDetailMenu
import org.torproject.vpn.ui.appdetail.menu.MenuItem
import org.torproject.vpn.ui.appdetail.model.AppDetailViewModel
import org.torproject.vpn.ui.composable.CircuitListItem
import org.torproject.vpn.ui.composable.ContainedLoadingIndicator
import org.torproject.vpn.ui.composable.DataUsage
import org.torproject.vpn.ui.composable.TopAppBar
import org.torproject.vpn.ui.preview.AppPreviewProvider
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.appIconSizeMDPI
import org.torproject.vpn.ui.theme.heightDataCard
import org.torproject.vpn.ui.theme.spacingSizeNormal
import org.torproject.vpn.ui.theme.spacingSizeUltraSmall
import org.torproject.vpn.ui.theme.widthDivider
import org.torproject.vpn.utils.getIconForPackage
import org.torproject.vpn.vpn.DataUsage

private const val TAG = "AppDetailScreen"

@Composable
fun AppDetailScreen(
    state: AppDetailViewModel.State,
    onEvent: (AppDetailViewModel.Event) -> Unit,
    onNavigateUp: () -> Unit = {}
) {
    val snackBarHostState = remember { SnackbarHostState() }

    when (val app = state.app) {
        null -> ContainedLoadingIndicator()
        else -> {
            ScreenContent(
                app = app,
                dataUsage = state.dataUsage,
                circuits = state.circuits,
                snackBarHostState = snackBarHostState,
                isVPNActive = state.isVPNActive,
                canOpenApp = state.canOpenApp,
                onNavigateUp = onNavigateUp,
                onRoutingToggled = { enabled ->
                    onEvent(AppDetailViewModel.Event.UpdateRouting(enabled))
                },
                onOpenApp = { onEvent(AppDetailViewModel.Event.OpenApp(app)) },
                onRefreshCircuit = {
                    onEvent(AppDetailViewModel.Event.RefreshCircuit)
                }
            )
        }
    }
}

@Composable
fun ScreenContent(
    app: App,
    hasTorSupport: Boolean = app.category == Category.TOR,
    dataUsage: DataUsage = DataUsage(),
    snackBarHostState: SnackbarHostState = SnackbarHostState(),
    circuits: List<CircuitCountryCodes> = emptyList(),
    isVPNActive: Boolean = false,
    canOpenApp: Boolean = false,
    onNavigateUp: () -> Unit = {},
    onRoutingToggled: (enabled: Boolean) -> Unit = {},
    onOpenApp: () -> Unit = {},
    onRefreshCircuit: () -> Unit = {}
) {
    var shouldShowRefreshDialog by rememberSaveable { mutableStateOf(false) }

    if (shouldShowRefreshDialog) {
        RefreshDialog(
            onDismiss = { shouldShowRefreshDialog = false },
            onConfirm = {
                shouldShowRefreshDialog = false
                onRefreshCircuit()
            }
        )
    }

    @Composable
    fun Menu() {
        AppDetailMenu(hasTorSupport = hasTorSupport, isVPNActive = isVPNActive) { menuItem ->
            when (menuItem) {
                MenuItem.REFRESH_CIRCUIT -> {
                    shouldShowRefreshDialog = true
                }
            }
        }
    }

    @Composable
    fun DetailsTorSupport() {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensionResource(R.dimen.activity_horizontal_margin),
                        vertical = dimensionResource(R.dimen.activity_vertical_margin)
                    )
            ) {
                Text(
                    text = stringResource(R.string.tor_powered_app),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    modifier = Modifier.padding(vertical = spacingSizeNormal),
                    text = stringResource(
                        R.string.description_independent_tor_powered_app,
                        app.name
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacingSizeUltraSmall),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    TextButton(
                        onClick = onOpenApp,
                        enabled = canOpenApp
                    ) {
                        Text(
                            text = stringResource(R.string.action_open_app, app.name),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DetailsNoTorSupport() {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = heightDataCard)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DataUsage(
                    modifier = Modifier.weight(1F),
                    icon = painterResource(R.drawable.ic_trend_up),
                    title = stringResource(R.string.up),
                    dataUsage = dataUsage.formattedUpstreamData
                )

                VerticalDivider(thickness = widthDivider)

                DataUsage(
                    modifier = Modifier.weight(1F),
                    icon = painterResource(R.drawable.ic_trend_down),
                    title = stringResource(R.string.down),
                    dataUsage = dataUsage.formattedDownstreamData
                )
            }
        }

        ListItem(
            onClick = { onRoutingToggled(!app.isRoutingEnabled) },
            content = {
                Text(text = stringResource(R.string.action_protect_this_app))
            },
            trailingContent = {
                Switch(
                    checked = app.isRoutingEnabled,
                    onCheckedChange = { onRoutingToggled(!app.isRoutingEnabled) }
                )
            }
        )

        if (circuits.isNotEmpty()) {
            Text(
                text = stringResource(R.string.circuits),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = circuits, key = { Uuid.random() }) { circuitCountryCodes ->
                    CircuitListItem(
                        appName = app.name,
                        circuitCountryCodes = circuitCountryCodes
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackBarHostState)
        },
        topBar = {
            TopAppBar(
                onNavigateUp = onNavigateUp,
                actions = { Menu() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.default_padding)),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.default_padding)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.compact_padding)
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    modifier = Modifier
                        .requiredSize(appIconSizeMDPI)
                        .clip(CircleShape),
                    bitmap = getIconForPackage(packageName = app.packageName),
                    contentDescription = null
                )

                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                hasTorSupport -> DetailsTorSupport()
                else -> DetailsNoTorSupport()
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppDetailScreenPreviewWithTor(@PreviewParameter(AppPreviewProvider::class) app: App) {
    ScreenContent(
        app = app,
        isVPNActive = true
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppDetailScreenPreviewNoTor(@PreviewParameter(AppPreviewProvider::class) app: App) {
    ScreenContent(
        app = app.copy(isRoutingEnabled = true),
        hasTorSupport = false,
        isVPNActive = true,
        circuits = listOf(CircuitCountryCodes(arrayListOf("US", "JP", "DE")))
    )
}
