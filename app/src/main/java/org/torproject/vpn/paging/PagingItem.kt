package org.torproject.vpn.paging

/**
 * A simple interface to allow marking classes as related for paging transformations
 */
interface PagingItem {
    val itemKey: String
    val contentType: String
}
