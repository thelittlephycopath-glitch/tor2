package org.torproject.vpn.extensions

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.flowOf

/**
 * Empty lazy paging item flow for optional methods
 */
@Composable
fun <T : Any> emptyPagingItems(): LazyPagingItems<T> = flowOf(PagingData.empty<T>())
    .collectAsLazyPagingItems()
