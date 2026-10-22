package org.torproject.vpn.ui.appsearch

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
import android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL
import android.speech.RecognizerIntent.EXTRA_RESULTS
import android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import org.torproject.vpn.R
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.Category
import org.torproject.vpn.extensions.emptyPagingItems
import org.torproject.vpn.ui.appsearch.model.AppSearchViewModel
import org.torproject.vpn.ui.composable.AppListItem
import org.torproject.vpn.ui.composable.CategoryHeader
import org.torproject.vpn.ui.preview.AppPreviewProvider
import org.torproject.vpn.ui.preview.ThemePreviewProvider

@Composable
fun AppSearchScreen(
    state: AppSearchViewModel.State,
    queryState: TextFieldState,
    searchResults: LazyPagingItems<App>,
    onEvent: (AppSearchViewModel.Event) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToAppDetail: (packageName: String) -> Unit
) {
    // Search results are shown in a BasicEdgeToEdgeDialog which for some reason tries to guess
    // status and navigation bar colors from this composition local instead of calling standard APIs
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        ScreenContent(
            state = state,
            queryState = queryState,
            searchResults = searchResults,
            onEvent = onEvent,
            onNavigateUp = onNavigateUp,
            onNavigateToAppDetail = onNavigateToAppDetail
        )
    }
}

@Composable
fun ScreenContent(
    state: AppSearchViewModel.State,
    queryState: TextFieldState,
    searchResults: LazyPagingItems<App> = emptyPagingItems(),
    onEvent: (AppSearchViewModel.Event) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    onNavigateToAppDetail: (packageName: String) -> Unit = {}
) {
    val searchBarState = rememberContainedSearchBarState(
        initialValue = SearchBarValue.Expanded,
        animationSpecForCollapse = snap()
    )

    // For clearing focus when search button is tapped
    var view: View? by remember { mutableStateOf(null) }

    LaunchedEffect(key1 = searchBarState.targetValue) {
        if (searchBarState.targetValue == SearchBarValue.Collapsed) onNavigateUp()
    }

    fun onRequestSearch(query: String) {
        view?.clearFocus()
        onEvent(AppSearchViewModel.Event.SubmitSearch(query))
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val query = result.data?.getStringArrayListExtra(EXTRA_RESULTS)?.firstOrNull()
            if (!query.isNullOrBlank()) onRequestSearch(query)
        }
    }

    fun onRequestVoiceSearch() {
        Intent(ACTION_RECOGNIZE_SPEECH).also { intent ->
            intent.putExtra(EXTRA_LANGUAGE_MODEL, LANGUAGE_MODEL_FREE_FORM)
            speechLauncher.launch(intent)
        }
    }

    val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = SearchBarDefaults.containedColors(state = searchBarState)
    )
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
            searchBarState = searchBarState,
            textFieldState = queryState,
            onSearch = { query -> onRequestSearch(query) },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        // This is due to overriding LocalContentColor above
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        painter = painterResource(R.drawable.ic_arrow_left),
                        contentDescription = stringResource(R.string.accessibility_back_button)
                    )
                }
            },
            trailingIcon = {
                if (queryState.text.isNotBlank()) {
                    IconButton(onClick = { onEvent(AppSearchViewModel.Event.ClearQuery) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_x),
                            contentDescription = stringResource(R.string.action_clear)
                        )
                    }
                } else {
                    if (state.isVoiceSearchAvailable) {
                        IconButton(onClick = ::onRequestVoiceSearch) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = stringResource(R.string.voice_recognition)
                            )
                        }
                    }
                }
            }
        )
    }

    ExpandedFullScreenContainedSearchBar(
        state = searchBarState,
        inputField = inputField,
        colors = appBarWithSearchColors.searchBarColors.copy(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        val currentView = LocalView.current
        SideEffect { view = currentView }

        when {
            queryState.text.isBlank() -> {
                // We aren't searching for anything but have past search queries
                if (state.recentQueries.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CategoryHeader(
                            category = stringResource(R.string.category_header_recent)
                        )

                        state.recentQueries.forEach { query ->
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRequestSearch(query) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_history),
                                        contentDescription = null
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = query,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(onClick = {
                                onEvent(AppSearchViewModel.Event.ClearSearchQueries)
                            }) {
                                Text(text = stringResource(R.string.action_clear_all))
                            }
                        }
                    }
                } else {
                    // Nothing to show
                }
            }

            searchResults.itemCount == 0 -> {
                // Avoid flashing the "no results" state while the first page is still loading
                if (searchResults.loadState.refresh is LoadState.NotLoading) {
                    // We are searching for something but have no matching results
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            dimensionResource(R.dimen.compact_padding),
                            Alignment.CenterVertically
                        )
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_binoculars),
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(R.string.search_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.search_error_message),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        CategoryHeader(
                            category = stringResource(R.string.category_header_results)
                        )
                    }
                    items(
                        count = searchResults.itemCount,
                        key = searchResults.itemKey { it.packageName }
                    ) { index ->
                        searchResults[index]?.let { app ->
                            AppListItem(
                                app = app,
                                onClick = {
                                    onEvent(AppSearchViewModel.Event.OpenApp(app.name))
                                    onNavigateToAppDetail(app.packageName)
                                },
                                onRoutingToggled = { enabled ->
                                    onEvent(
                                        AppSearchViewModel.Event.UpdateRouting(
                                            app.packageName,
                                            enabled
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val previewState = AppSearchViewModel.State(isVoiceSearchAvailable = true)

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppSearchScreenDefaultPreview() {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenContent(
            state = previewState,
            queryState = remember { TextFieldState() }
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppSearchScreenResultsPreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    val apps = List(5) {
        app.copy(
            packageName = Random.nextInt().toString(),
            category = Category.entries.random()
        )
    }
    val results = MutableStateFlow(PagingData.from(apps)).collectAsLazyPagingItems()
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenContent(
            state = previewState,
            queryState = remember { TextFieldState("Tor VPN") },
            searchResults = results
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppSearchScreenNoResultsPreview() {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenContent(
            state = previewState,
            queryState = remember { TextFieldState("Tor VPN") }
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
private fun AppSearchScreenSavedQueriesPreview() {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenContent(
            state = previewState.copy(recentQueries = listOf("Tor VPN", "Tor Browser")),
            queryState = remember { TextFieldState() }
        )
    }
}
