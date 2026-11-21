package com.nuvio.tv.ui.screens.providers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.dpadRepeatThrottle
import kotlinx.coroutines.flow.distinctUntilChanged

private const val HEADER_ITEMS = 2 // header + controls rows before the first card

/**
 * Provider page: logo + name header, Movies / Series tabs, sort pills and a paging poster grid of
 * what the service streams. Initial focus lands on the selected tab; Back from a title's details
 * returns to the card that was opened; Back from this page returns to Home (which re-focuses the tile).
 */
@Composable
fun ProviderScreen(
    onBackPress: () -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit,
    viewModel: ProviderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cornerRadiusDp by viewModel.posterCardCornerRadiusDp.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val movieTabRequester = remember { FocusRequester() }
    val seriesTabRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    val cardRequester = remember { FocusRequester() }

    // Survives the trip to details and back.
    var lastFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    var lastGeneration by rememberSaveable { mutableStateOf(-1) }

    BackHandler { onBackPress() }

    val cardStyle = remember(cornerRadiusDp) {
        PosterCardStyle(
            width = 132.dp,
            height = 198.dp,
            cornerRadius = cornerRadiusDp.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.04f
        )
    }

    // Entry focus: the selected tab on first open; the last opened card when coming back.
    LaunchedEffect(state.isLoading, state.items.isNotEmpty()) {
        if (state.isLoading) return@LaunchedEffect
        val restoreId = lastFocusedId?.takeIf { id -> state.items.any { it.id == id } }
        if (restoreId != null && initialFocusDone) {
            val index = state.items.indexOfFirst { it.id == restoreId }
            val visible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index + HEADER_ITEMS }
            if (!visible) gridState.scrollToItem(index + HEADER_ITEMS)
            if (cardRequester.requestFocusAfterFrames(frames = 3)) return@LaunchedEffect
        }
        if (!initialFocusDone) {
            initialFocusDone = true
            val tabRequester = if (state.tab == ProviderMediaTab.SERIES) seriesTabRequester else movieTabRequester
            tabRequester.requestFocusAfterFrames(frames = 2)
        } else if (state.error) {
            retryRequester.requestFocusAfterFrames(frames = 2)
        }
    }

    // A new tab / sort result starts at the top.
    LaunchedEffect(state.generation) {
        if (lastGeneration != -1 && lastGeneration != state.generation) {
            lastFocusedId = null
            gridState.scrollToItem(0)
        }
        lastGeneration = state.generation
    }

    // Paging: ask for the next page when the last rows come into view.
    LaunchedEffect(gridState, state.items.size, state.hasMore) {
        if (!state.hasMore) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last >= state.items.size + HEADER_ITEMS - 12) viewModel.loadMore()
            }
    }

    val background = NuvioTheme.colors.Background
    Box(modifier = Modifier.fillMaxSize().background(background)) {
        // Soft brand glow behind the header.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.07f),
                        1f to Color.Transparent
                    )
                )
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = cardStyle.width),
            modifier = Modifier.fillMaxSize().dpadRepeatThrottle(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "provider_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                ProviderHeader(name = state.provider.name, logoUrl = state.logoUrl)
            }
            item(key = "provider_controls", span = { GridItemSpan(maxLineSpan) }, contentType = "controls") {
                ProviderControls(
                    tab = state.tab,
                    sort = state.sort,
                    movieTabRequester = movieTabRequester,
                    seriesTabRequester = seriesTabRequester,
                    onTab = viewModel::selectTab,
                    onSort = viewModel::selectSort
                )
            }
            when {
                state.isLoading -> item(key = "provider_loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                state.error -> item(key = "provider_error", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.cinemaos_provider_error),
                            fontSize = 16.sp,
                            color = NuvioTheme.colors.TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        CinemaosPill(
                            text = stringResource(R.string.cinemaos_provider_retry),
                            selected = true,
                            onClick = viewModel::retry,
                            modifier = Modifier.focusRequester(retryRequester)
                        )
                    }
                }
                state.items.isEmpty() -> item(key = "provider_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.cinemaos_provider_empty),
                        fontSize = 16.sp,
                        color = NuvioTheme.colors.TextSecondary,
                        modifier = Modifier.padding(top = 48.dp)
                    )
                }
                else -> {
                    val restoreId = lastFocusedId
                    items(items = state.items, key = { it.id }, contentType = { "posterCard" }) { item ->
                        GridContentCard(
                            item = item,
                            posterCardStyle = cardStyle,
                            showLabel = true,
                            showReleaseInfo = true,
                            focusRequester = if (item.id == restoreId) cardRequester else null,
                            onFocused = { lastFocusedId = item.id },
                            onClick = {
                                lastFocusedId = item.id
                                onNavigateToDetail(item.id, item.apiType, null)
                            }
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "provider_more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderHeader(name: String, logoUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.take(1),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(
                text = stringResource(R.string.cinemaos_provider_eyebrow),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Text(
                text = name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ProviderControls(
    tab: ProviderMediaTab,
    sort: ProviderSort,
    movieTabRequester: FocusRequester,
    seriesTabRequester: FocusRequester,
    onTab: (ProviderMediaTab) -> Unit,
    onSort: (ProviderSort) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CinemaosPill(
            text = stringResource(R.string.cinemaos_provider_tab_movies),
            selected = tab == ProviderMediaTab.MOVIES,
            onClick = { onTab(ProviderMediaTab.MOVIES) },
            modifier = Modifier.focusRequester(movieTabRequester)
        )
        CinemaosPill(
            text = stringResource(R.string.cinemaos_provider_tab_series),
            selected = tab == ProviderMediaTab.SERIES,
            onClick = { onTab(ProviderMediaTab.SERIES) },
            modifier = Modifier.focusRequester(seriesTabRequester)
        )
        Spacer(Modifier.width(24.dp))
        Text(
            text = stringResource(R.string.cinemaos_provider_sort_label),
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        ProviderSort.entries.forEach { option ->
            CinemaosPill(
                text = stringResource(
                    when (option) {
                        ProviderSort.POPULAR -> R.string.cinemaos_provider_sort_popular
                        ProviderSort.NEWEST -> R.string.cinemaos_provider_sort_newest
                        ProviderSort.TOP_RATED -> R.string.cinemaos_provider_sort_top_rated
                    }
                ),
                selected = sort == option,
                onClick = { onSort(option) }
            )
        }
    }
}
