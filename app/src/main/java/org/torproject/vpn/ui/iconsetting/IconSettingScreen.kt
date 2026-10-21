package org.torproject.vpn.ui.iconsetting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.util.fastForEach
import org.torproject.vpn.R
import org.torproject.vpn.ui.composable.LauncherListItem
import org.torproject.vpn.ui.composable.TopAppBar
import org.torproject.vpn.ui.iconsetting.model.IconSettingViewModel
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.spacingSizeNormal
import org.torproject.vpn.ui.theme.spacingSizeVerySmall

@Composable
fun IconSettingScreen(
    state: IconSettingViewModel.State = IconSettingViewModel.State(),
    onEvent: (IconSettingViewModel.Event) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                onNavigateUp = onNavigateUp,
                title = stringResource(R.string.app_icon)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = spacingSizeNormal),
            verticalArrangement = Arrangement.spacedBy(spacingSizeVerySmall)
        ) {
            Text(
                text = stringResource(R.string.launcher_hint),
                style = MaterialTheme.typography.bodyMedium
            )

            FlowRow(
                modifier = Modifier.padding(vertical = spacingSizeVerySmall),
                horizontalArrangement = Arrangement.spacedBy(spacingSizeVerySmall)
            ) {
                state.launchers.fastForEach { launcher ->
                    LauncherListItem(
                        launcher = launcher,
                        isSelected = launcher == state.currentLauncher,
                        onClick = {
                            onEvent(IconSettingViewModel.Event.SetLauncherIconEvent(launcher))
                        }
                    )
                }
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun IconSettingScreenPreview() {
    IconSettingScreen()
}
