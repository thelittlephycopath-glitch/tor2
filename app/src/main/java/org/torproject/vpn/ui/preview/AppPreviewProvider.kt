package org.torproject.vpn.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category

/**
 * Preview provider for composables working with apps
 */
class AppPreviewProvider : PreviewParameterProvider<App> {

    override val values: Sequence<App>
        get() = sequenceOf(
            App(
                name = "Tor VPN",
                packageName = "org.torproject.vpn",
                isRoutingEnabled = false,
                category = Category.TOR,
                uid = 1097
            )
        )
}
