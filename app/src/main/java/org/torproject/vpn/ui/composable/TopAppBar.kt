package org.torproject.vpn.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar as MaterialTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.torproject.vpn.R
import org.torproject.vpn.ui.preview.ThemePreviewProvider

/**
 * A top app bar composable to be used with Scaffold in different Screen
 */
@Composable
fun TopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    navigationIcon: Painter = painterResource(R.drawable.ic_arrow_left),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    MaterialTopAppBar(
        modifier = modifier,
        title = {
            Column {
                title?.let { Text(text = it) }
                subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        painter = navigationIcon,
                        contentDescription = stringResource(R.string.accessibility_back_button)
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        actions = actions
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun TopAppBarPreview() {
    TopAppBar(
        title = stringResource(R.string.apps),
        onNavigateUp = {}
    )
}
