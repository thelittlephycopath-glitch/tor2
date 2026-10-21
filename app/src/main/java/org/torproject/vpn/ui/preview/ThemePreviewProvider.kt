package org.torproject.vpn.ui.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import org.torproject.vpn.ui.theme.AppTheme

/**
 * Default preview provider for composables
 */
class ThemePreviewProvider : PreviewWrapperProvider {

    @Composable
    override fun Wrap(content: @Composable (() -> Unit)) {
        AppTheme {
            Surface(content = content)
        }
    }
}
