package org.torproject.vpn.ui.composable

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.ui.preview.AppPreviewProvider
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.appIconSizeLDPIPlus
import org.torproject.vpn.ui.theme.widthDivider
import org.torproject.vpn.utils.getIconForPackage

/**
 * Composable to display details about an app which is not powered by Tor
 * @see TorListItem
 */
@Composable
fun AppListItem(
    modifier: Modifier = Modifier,
    app: App,
    onClick: () -> Unit = {},
    onRoutingToggled: (enabled: Boolean) -> Unit = {}
) {
    ListItem(
        modifier = modifier
            .animateContentSize()
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Image(
                modifier = Modifier
                    .requiredSize(appIconSizeLDPIPlus)
                    .clip(CircleShape),
                bitmap = getIconForPackage(packageName = app.packageName),
                contentDescription = null
            )
        },
        trailingContent = {
            if (app.category == Category.TOR) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up_right),
                    contentDescription = null
                )
            } else {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        space = dimensionResource(R.dimen.default_padding)
                    )
                ) {
                    AnimatedVisibility(
                        visible = app.isRoutingEnabled,
                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.circuit),
                            contentDescription = null
                        )
                    }
                    VerticalDivider(thickness = widthDivider)
                    Switch(
                        checked = app.isRoutingEnabled,
                        onCheckedChange = { onRoutingToggled(!app.isRoutingEnabled) }
                    )
                }
            }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppListItemPreviewNoTor(@PreviewParameter(AppPreviewProvider::class) app: App) {
    AppListItem(
        app = app.copy(
            category = Category.OTHER,
            isRoutingEnabled = true
        )
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppListItemPreviewWithTor(@PreviewParameter(AppPreviewProvider::class) app: App) {
    AppListItem(app = app)
}
