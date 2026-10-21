package org.torproject.vpn.ui.composable

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.iconsetting.model.Launcher
import org.torproject.vpn.ui.iconsetting.model.LauncherDefault
import org.torproject.vpn.ui.preview.ThemePreviewProvider
import org.torproject.vpn.ui.theme.appIconContainerSize
import org.torproject.vpn.ui.theme.appIconSizeMDPI
import org.torproject.vpn.ui.theme.widthLauncherBorder

/**
 * Composable to display launcher icons in a list
 */
@Composable
fun LauncherListItem(
    modifier: Modifier = Modifier,
    launcher: Launcher,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .width(appIconContainerSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = dimensionResource(R.dimen.compact_padding)
        )
    ) {
        OutlinedCard(
            modifier = Modifier.requiredSize(appIconContainerSize),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(
                width = widthLauncherBorder,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
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
                    painter = painterResource(launcher.drawableBackground),
                    contentDescription = null
                )
                Image(
                    painter = painterResource(launcher.drawableForeground),
                    contentDescription = null
                )
            }
        }

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(launcher.label),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = when {
                isSelected -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun LauncherListItemSelectedPreview() {
    LauncherListItem(launcher = LauncherDefault, isSelected = true)
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun LauncherListItemUnselectedPreview() {
    LauncherListItem(launcher = LauncherDefault)
}
