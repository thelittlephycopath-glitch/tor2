package org.torproject.vpn.ui.exitselection

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.composable.ExitListItem
import org.torproject.vpn.ui.composable.TopAppBar
import org.torproject.vpn.ui.exitselection.model.ExitSelectionViewModel
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.spacingSizeNormal
import org.torproject.vpn.ui.theme.spacingSizeVerySmall
import org.torproject.vpn.utils.Country

@Composable
fun ExitSelectionScreen(
    state: ExitSelectionViewModel.State,
    onEvent: (ExitSelectionViewModel.Event) -> Unit,
    onNavigateUp: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                onNavigateUp = onNavigateUp,
                title = stringResource(R.string.exit_location),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
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
            item(key = "header") {
                Text(
                    modifier = Modifier.padding(
                        horizontal = spacingSizeNormal,
                        vertical = spacingSizeVerySmall
                    ),
                    text = stringResource(R.string.exit_location_description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            items(items = state.availableExits, key = { it.code }) { country ->
                ExitListItem(
                    country = country,
                    isSelected = country == state.currentExit,
                    onClick = { onEvent(ExitSelectionViewModel.Event.SelectExit(country)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = spacingSizeNormal)
                )
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun ExitSelectionScreenPreview() {
    ExitSelectionScreen(
        state = ExitSelectionViewModel.State(
            currentExit = Country.GERMANY,
            availableExits = listOf(
                Country.RANDOM,
                Country.GERMANY,
                Country.JAPAN,
                Country.UNITED_STATES,
                Country.FRANCE
            )
        ),
        onEvent = {}
    )
}
