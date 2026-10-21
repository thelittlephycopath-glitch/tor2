package org.torproject.vpn.ui.appdetail.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.preview.ThemePreviewProvider

@Composable
fun AppDetailMenu(
    modifier: Modifier = Modifier,
    hasTorSupport: Boolean = false,
    isVPNActive: Boolean = false,
    onMenuItemClicked: (menuItem: MenuItem) -> Unit = {}
) {
    fun onClick(menuItem: MenuItem) {
        onMenuItemClicked(menuItem)
    }

    Box(modifier = modifier) {
        if (!hasTorSupport && isVPNActive) {
            IconButton(onClick = { onClick(MenuItem.REFRESH_CIRCUIT) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_circuit),
                    contentDescription = stringResource(R.string.action_refresh)
                )
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppDetailMenuPreview() {
    AppDetailMenu(isVPNActive = true)
}
