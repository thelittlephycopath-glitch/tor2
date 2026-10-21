package org.torproject.vpn.database.app

import androidx.annotation.StringRes
import org.torproject.vpn.R
import org.torproject.vpn.paging.PagingItem

/**
 * Categories for [App]
 */
enum class Category(
    @StringRes val localizedTitle: Int,
    val canExpand: Boolean = false,
    val isExpandedByDefault: Boolean = true
) : PagingItem {
    TOR(localizedTitle = R.string.app_routing_tor_apps),
    BROWSER(localizedTitle = R.string.app_routing_browsers),
    OTHER(localizedTitle = R.string.app_routing_other_apps),
    SYSTEM(
        localizedTitle = R.string.app_routing_system_apps,
        canExpand = true,
        isExpandedByDefault = false
    );

    override val itemKey: String
        get() = this.name

    override val contentType: String
        get() = "Category"
}
