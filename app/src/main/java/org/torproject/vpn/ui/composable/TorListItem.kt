package org.torproject.vpn.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.ui.preview.AppPreviewProvider
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.appIconContainerSize
import org.torproject.vpn.ui.theme.appIconSizeMDPI
import org.torproject.vpn.utils.getIconForPackage

/**
 * Composable to display details about an app which is powered by Tor
 * @see AppListItem
 */
@Composable
fun TorListItem(modifier: Modifier = Modifier, app: App, onClick: () -> Unit = {}) {
    Column(
        modifier = modifier
            .width(appIconContainerSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = dimensionResource(R.dimen.compact_padding)
        )
    ) {
        Card(
            modifier = Modifier.requiredSize(appIconContainerSize),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    modifier = Modifier
                        .requiredSize(appIconSizeMDPI)
                        .clip(CircleShape),
                    bitmap = getIconForPackage(packageName = app.packageName),
                    contentDescription = null
                )
            }
        }

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = app.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun TorListItemPreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    TorListItem(app = app)
}
