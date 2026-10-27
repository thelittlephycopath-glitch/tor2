package org.torproject.vpn.ui.bridgebot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.torproject.vpn.R
import org.torproject.vpn.ui.bridgebot.model.BotState
import org.torproject.vpn.ui.bridgebot.model.BridgeBotViewModel
import org.torproject.vpn.ui.bridgebot.model.BridgeRequestTransport
import org.torproject.vpn.ui.composable.TopAppBar
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.bridgeBotIconSize
import org.torproject.vpn.ui.theme.heightProgressIndicator
import org.torproject.vpn.ui.theme.spacingSizeBig
import org.torproject.vpn.ui.theme.spacingSizeNormal

@Composable
fun BridgeBotScreen(
    state: BridgeBotViewModel.State,
    onEvent: (BridgeBotViewModel.Event) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.bridge_bot),
                onNavigateUp = onNavigateUp,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val screenHeight = maxHeight
            val contentHeightFraction = if (
                state.botState == BotState.IDLE || state.botState == BotState.ERROR
            ) {
                0.62f
            } else {
                0.8f
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.botState == BotState.FETCHING) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightProgressIndicator)
                    )
                }

                if (state.botState == BotState.IDLE || state.botState == BotState.ERROR) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacingSizeBig, vertical = spacingSizeNormal),
                        verticalArrangement = Arrangement.spacedBy(spacingSizeNormal)
                    ) {
                        Text(
                            text = stringResource(R.string.bridge_transport_select_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacingSizeNormal)
                        ) {
                            Button(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (
                                        state.selectedTransport == BridgeRequestTransport.OBFS4
                                    ) {
                                        Color.White
                                    } else {
                                        Color(0xFF252525)
                                    },
                                    contentColor = if (
                                        state.selectedTransport == BridgeRequestTransport.OBFS4
                                    ) {
                                        Color.Black
                                    } else {
                                        Color.White
                                    }
                                ),
                                onClick = {
                                    onEvent(BridgeBotViewModel.Event.SetTransport(BridgeRequestTransport.OBFS4))
                                }
                            ) {
                                Text(stringResource(R.string.bridge_transport_obfs4), maxLines = 1)
                            }
                            Button(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (
                                        state.selectedTransport == BridgeRequestTransport.WEBTUNNEL
                                    ) {
                                        Color.White
                                    } else {
                                        Color(0xFF252525)
                                    },
                                    contentColor = if (
                                        state.selectedTransport == BridgeRequestTransport.WEBTUNNEL
                                    ) {
                                        Color.Black
                                    } else {
                                        Color.White
                                    }
                                ),
                                onClick = {
                                    onEvent(BridgeBotViewModel.Event.SetTransport(BridgeRequestTransport.WEBTUNNEL))
                                }
                            ) {
                                Text(stringResource(R.string.bridge_transport_webtunnel), maxLines = 1)
                            }
                        }
                        if (state.selectedTransport == BridgeRequestTransport.WEBTUNNEL) {
                            Text(
                                text = stringResource(R.string.bridge_transport_webtunnel_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = screenHeight * contentHeightFraction),
                    verticalArrangement = Arrangement.spacedBy(
                        spacingSizeNormal,
                        Alignment.CenterVertically
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state.botState) {
                        BotState.IDLE, BotState.FETCHING, BotState.ERROR -> {
                            Image(
                                modifier = Modifier
                                    .requiredSize(bridgeBotIconSize)
                                    .padding(vertical = spacingSizeNormal),
                                painter = painterResource(
                                    if (state.botState == BotState.ERROR) {
                                        R.drawable.bridge_bot_error_icon
                                    } else {
                                        R.drawable.ic_bridge_bot
                                    }
                                ),
                                contentDescription = null
                            )

                            Text(
                                text = when (state.botState) {
                                    BotState.IDLE -> stringResource(R.string.bot_msg_welcome)
                                    BotState.FETCHING -> stringResource(R.string.fetching_bridges)
                                    BotState.ERROR -> stringResource(
                                        state.errorMessage ?: R.string.error_fetching_bridges
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.botState == BotState.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .testTag("botMessageText")
                                    .padding(
                                        horizontal = spacingSizeBig,
                                        vertical = spacingSizeNormal
                                    )
                            )
                        }

                        BotState.SUCCESS -> {
                            BridgeResultCard(
                                bridges = state.bridges,
                                onSaveBridges = {
                                    onEvent(BridgeBotViewModel.Event.SaveBridges)
                                    onNavigateUp()
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = spacingSizeBig,
                            vertical = spacingSizeBig
                        )
                ) {
                    when (state.botState) {
                        BotState.IDLE -> {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = CircleShape,
                                onClick = { onEvent(BridgeBotViewModel.Event.RequestBridges) }
                            ) {
                                Text(
                                    text = stringResource(R.string.new_bridges),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        BotState.ERROR -> {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = CircleShape,
                                onClick = { onEvent(BridgeBotViewModel.Event.RequestBridges) }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_try_again),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        BotState.FETCHING, BotState.SUCCESS -> {
                            FilledTonalButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = CircleShape,
                                onClick = { onEvent(BridgeBotViewModel.Event.CancelRequest) }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BridgeResultCard(
    bridges: List<String>,
    onSaveBridges: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacingSizeNormal),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = spacingSizeBig,
                    vertical = spacingSizeBig
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_bridge),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White)
            )

            Text(
                text = stringResource(R.string.bot_msg_results),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = spacingSizeNormal)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(bottom = spacingSizeNormal)
            ) {
                items(items = bridges, key = { it }) { bridge ->
                    Text(
                        text = bridge,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Button(
                    onClick = onSaveBridges,
                    contentPadding = PaddingValues(
                        horizontal = spacingSizeNormal,
                        vertical = 16.dp
                    )
                ) {
                    Text(
                        text = stringResource(R.string.use_bridges),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun BridgeBotScreenPreviewIdle() {
    BridgeBotScreen(state = BridgeBotViewModel.State())
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun BridgeBotScreenPreviewFetching() {
    BridgeBotScreen(state = BridgeBotViewModel.State(botState = BotState.FETCHING))
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun BridgeBotScreenPreviewSuccess() {
    BridgeBotScreen(
        state = BridgeBotViewModel.State(
            botState = BotState.SUCCESS,
            bridges = listOf(
                "obfs4 192.168.1.105 E63C75D8CA27794B034D18163C80A4DC7C9621B6 cert=e3b0c442 iat-mode=0",
                "obfs4 203.0.113.195 FBA329BA4F5FE648456C3A28BBAA3C7EBD118E08 cert=da39a3e iat-mode=0"
            )
        )
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun BridgeBotScreenPreviewError() {
    BridgeBotScreen(
        state = BridgeBotViewModel.State(
            botState = BotState.ERROR,
            errorMessage = R.string.error_fetching_bridges
        )
    )
}
