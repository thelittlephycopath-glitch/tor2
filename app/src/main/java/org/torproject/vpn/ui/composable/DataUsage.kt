package org.torproject.vpn.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.preview.ThemePreviewProvider

/**
 * Composable to display data usage for an app
 */
@Composable
fun DataUsage(modifier: Modifier = Modifier, title: String, dataUsage: String, icon: Painter) {
    ListItem(
        modifier = modifier.width(IntrinsicSize.Max),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        leadingContent = {
            Image(
                painter = icon,
                contentDescription = null
            )
        },
        overlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        headlineContent = {
            Text(
                text = dataUsage,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun DataUsagePreview() {
    DataUsage(
        title = stringResource(R.string.down),
        dataUsage = "5.4 MB",
        icon = painterResource(R.drawable.ic_trend_down)
    )
}
