package org.torproject.vpn.ui.approuting.menu

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.preview.ThemePreviewProvider

@Composable
fun AppRoutingMenu(
    modifier: Modifier = Modifier,
    isVPNActive: Boolean = false,
    onMenuItemClicked: (menuItem: MenuItem) -> Unit = {}
) {
    fun onClick(menuItem: MenuItem) {
        onMenuItemClicked(menuItem)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isVPNActive) {
            IconButton(onClick = { onClick(MenuItem.REFRESH_CIRCUITS) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_all),
                    contentDescription = stringResource(R.string.action_refresh_circuits)
                )
            }
        }
        IconButton(onClick = { onClick(MenuItem.SEARCH) }) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.action_search)
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppRoutingMenuPreview() {
    AppRoutingMenu(isVPNActive = true)
}
