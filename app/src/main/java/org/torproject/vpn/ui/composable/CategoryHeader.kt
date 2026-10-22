package org.torproject.vpn.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.preview.ThemePreviewProvider

/**
 * Category header for apps showed in app routing screen
 */
@Composable
fun CategoryHeader(
    modifier: Modifier = Modifier,
    category: String,
    canExpand: Boolean = false,
    isExpanded: Boolean = false,
    listSize: Int = 0,
    onExpand: (expand: Boolean) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.default_padding),
                vertical = dimensionResource(R.dimen.compact_padding)
            ),
            text = category,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.primary
        )

        if (canExpand) {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand(!isExpanded) },
                headlineContent = {
                    Text(
                        text = if (isExpanded) {
                            stringResource(R.string.app_routing_hide_apps, listSize)
                        } else {
                            stringResource(R.string.app_routing_show_apps, listSize)
                        }
                    )
                },
                trailingContent = {
                    Icon(
                        painter = if (isExpanded) {
                            painterResource(R.drawable.carat_up)
                        } else {
                            painterResource(R.drawable.carat_down)
                        },
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun CategoryHeaderPreviewDefault() {
    CategoryHeader(
        category = stringResource(R.string.app_routing_tor_apps)
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun CategoryHeaderPreviewExpanded() {
    CategoryHeader(
        category = stringResource(R.string.app_routing_system_apps),
        canExpand = true,
        listSize = 150
    )
}
