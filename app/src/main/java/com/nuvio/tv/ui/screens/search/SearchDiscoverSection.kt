package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.home.HeroBackdropState

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.nuvio.tv.ui.util.contentTextDirection
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.LocalCardDepthStyle
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.components.nuvioCardDepth
import com.nuvio.tv.domain.model.CardDepthSurface
import com.nuvio.tv.ui.util.dpadVerticalFastScroll
import com.nuvio.tv.ui.util.formatAddonTypeLabel
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun DiscoverSection(
    uiState: SearchUiState,
    posterCardStyle: PosterCardStyle,
    watchedMovieIds: Set<String> = emptySet(),
    watchedSeriesIds: Set<String> = emptySet(),
    focusResults: Boolean,
    showBuiltInHeader: Boolean = true,
    firstItemFocusRequester: FocusRequester,
    focusedItemIndex: Int,
    shouldRestoreFocusedItem: Boolean,
    blockFilterFocus: Boolean = false,
    onRestoreFocusedItemHandled: () -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    onDiscoverItemFocused: (Int) -> Unit,
    onSelectType: (String) -> Unit,
    onSelectCatalog: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onSelectFilter: (DiscoverFilterKey, String?) -> Unit = { _, _ -> },
    onResetFilters: () -> Unit = {},
    onLoadMore: () -> Unit,
    onItemLongPress: (MetaPreview, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val selectedCatalog = uiState.discoverCatalogs.firstOrNull { it.key == uiState.selectedDiscoverCatalogKey }
    val filteredCatalogs = uiState.discoverCatalogs.filter { it.type == uiState.selectedDiscoverType }
    // Genres only: a catalog whose `genre` extra lists years exposes them under the Year pill.
    // TMDB catalogs use TMDB's own genre list; an addon catalog without genres borrows those of
    // the other catalogs of its type so the picker is never empty.
    val genreOptions: List<DiscoverOption> = if (selectedCatalog?.isTmdbDiscover == true) {
        uiState.discoverTmdbGenres.map { DiscoverOption(localizedGenreLabel(it.label), it.value) }
    } else {
        discoverAddonGenreOptions(uiState.discoverCatalogs, selectedCatalog)
            .map { DiscoverOption(localizedGenreLabel(it), it) }
    }
    val selectedGenreValue: String? = if (selectedCatalog?.isTmdbDiscover == true) {
        uiState.discoverTmdbFilters.genre
    } else {
        uiState.selectedDiscoverGenre
    }
    // A required genre has no "Any" row: the catalog cannot be queried without one.
    val genreIsRequired = selectedCatalog != null &&
        selectedCatalog.genreExtraRequired &&
        selectedCatalog.genres.isNotEmpty()

    var expandedPicker by remember { mutableStateOf<String?>(null) }
    val filterRowRequester = remember { FocusRequester() }
    val segmentRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var gridHasFocus by remember { mutableStateOf(false) }
    // The grid leaves composition while loading; focus is no longer inside it then.
    LaunchedEffect(uiState.discoverResults.isEmpty()) {
        if (uiState.discoverResults.isEmpty()) gridHasFocus = false
    }
    val gridColumns by remember(gridState) {
        derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1 }
    }
    // The header + filter pills give their space to the posters while focus is below the first
    // row, and come back as soon as focus returns to it.
    val headerVisible = !(gridHasFocus && focusedItemIndex >= gridColumns)

    // Down from the filter pills: scroll the grid to the top and focus its first result.
    fun focusFirstResult(): Boolean {
        if (uiState.discoverResults.isEmpty()) return false
        scope.launch {
            if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
                gridState.scrollToItem(0)
            }
            firstItemFocusRequester.requestFocusAfterFrames(frames = 2)
        }
        return true
    }

    // Back from the grid returns to the filter pills instead of leaving the screen.
    androidx.activity.compose.BackHandler(enabled = gridHasFocus) {
        scope.launch {
            gridState.scrollToItem(0)
            firstItemFocusRequester.requestFocusAfterFrames(frames = 2)
            filterRowRequester.requestFocusAfterFrames(frames = 3)
        }
    }

    val localContext = LocalContext.current
    fun localizedTypeLabel(type: String): String = localizedContentType(localContext, type)

    val moviesLabel = stringResource(R.string.type_movies)
    val seriesLabel = stringResource(R.string.type_series_plural)
    val animeLabel = stringResource(R.string.discover_type_anime)
    val typeOptions = uiState.discoverCatalogs.map { it.type }.distinct()
        .sortedBy { type ->
            when (type) {
                DISCOVER_TYPE_MOVIE -> 0
                DISCOVER_TYPE_SERIES -> 1
                DISCOVER_TYPE_ANIME -> 2
                else -> 3
            }
        }
        .map { type ->
            DiscoverOption(
                label = when (type) {
                    DISCOVER_TYPE_MOVIE -> moviesLabel
                    DISCOVER_TYPE_SERIES -> seriesLabel
                    DISCOVER_TYPE_ANIME -> animeLabel
                    else -> localizedTypeLabel(type)
                },
                value = type
            )
        }

    val anyValueLabel = stringResource(R.string.discover_filter_value_any)
    val selectedCatalogLabel = selectedCatalog?.catalogName ?: stringResource(R.string.discover_select_catalog)
    val catalogTitle = stringResource(R.string.discover_filter_catalog)
    val genreTitle = stringResource(R.string.discover_filter_genre)
    val selectedGenreLabel = selectedGenreValue?.let { value ->
        genreOptions.firstOrNull { it.value == value }?.label ?: localizedGenreLabel(value)
    } ?: anyValueLabel

    val leadingPills = if (selectedCatalog != null) {
        discoverFilterPills(discoverLeadingFilterKeys(selectedCatalog), uiState, selectedCatalog, onSelectFilter)
    } else {
        emptyList()
    }
    val extraPills = if (selectedCatalog != null) {
        discoverFilterPills(
            discoverExtraFilterKeys(selectedCatalog, uiState.discoverTmdbFilters),
            uiState,
            selectedCatalog,
            onSelectFilter
        )
    } else {
        emptyList()
    }
    val pills = buildList {
        if (filteredCatalogs.size > 1) {
            add(
                DiscoverPillSpec(
                    id = "catalog",
                    title = catalogTitle,
                    valueLabel = selectedCatalogLabel,
                    active = false,
                    options = filteredCatalogs.map { DiscoverOption(it.catalogName, it.key) },
                    selectedValue = uiState.selectedDiscoverCatalogKey,
                    onSelect = { option -> onSelectCatalog(option.value) }
                )
            )
        }
        addAll(leadingPills)
        if (selectedCatalog != null) {
            add(
                DiscoverPillSpec(
                    id = "genre",
                    title = genreTitle,
                    valueLabel = selectedGenreLabel,
                    active = selectedGenreValue != null && !genreIsRequired,
                    options = buildList {
                        if (!genreIsRequired) add(DiscoverOption(anyValueLabel, DISCOVER_ANY_VALUE))
                        addAll(genreOptions)
                    },
                    selectedValue = selectedGenreValue ?: DISCOVER_ANY_VALUE,
                    onSelect = { option ->
                        onSelectGenre(option.value.takeUnless { it == DISCOVER_ANY_VALUE })
                    }
                )
            )
        }
        addAll(extraPills)
    }
    val selectedSegmentRequester = segmentRequesters.getOrPut(uiState.selectedDiscoverType) { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTheme.spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                // The floating navigation pill overlays the top edge in that layout.
                if (!showBuiltInHeader) Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.discover_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = NuvioTheme.colors.TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.discover_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    DiscoverTypeSwitch(
                        types = typeOptions,
                        selectedType = uiState.selectedDiscoverType,
                        requesterFor = { type -> segmentRequesters.getOrPut(type) { FocusRequester() } },
                        downRequester = filterRowRequester,
                        blockFocus = blockFilterFocus,
                        onSelectType = { type ->
                            expandedPicker = null
                            onSelectType(type)
                        }
                    )
                }

                DiscoverFilterPillRow(
                    pills = pills,
                    expandedPicker = expandedPicker,
                    onExpandedChange = { expandedPicker = it },
                    onReset = onResetFilters,
                    rowRequester = filterRowRequester,
                    upRequester = selectedSegmentRequester,
                    blockFocus = blockFilterFocus,
                    onMoveDown = ::focusFirstResult
                )
            }
        }

        when {
            uiState.discoverLoading && uiState.discoverResults.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            uiState.discoverResults.isNotEmpty() -> {
                Box(modifier = Modifier.onFocusChanged { gridHasFocus = it.hasFocus }) {
                DiscoverGrid(
                    items = uiState.discoverResults,
                    posterCardStyle = posterCardStyle,
                    gridState = gridState,
                    upFocusRequester = filterRowRequester,
                    watchedMovieIds = watchedMovieIds,
                    watchedSeriesIds = watchedSeriesIds,
                    focusResults = focusResults,
                    firstItemFocusRequester = firstItemFocusRequester,
                    focusedItemIndex = focusedItemIndex,
                    shouldRestoreFocusedItem = shouldRestoreFocusedItem,
                    onRestoreFocusedItemHandled = onRestoreFocusedItemHandled,
                    onItemFocused = onDiscoverItemFocused,
                    pendingCount = uiState.pendingDiscoverResults.size,
                    canLoadMore = uiState.discoverHasMore,
                    isLoadingMore = uiState.discoverLoadingMore,
                    onLoadMore = onLoadMore,
                    onItemClick = { _, item ->
                        HeroBackdropState.update(item.backdropUrl)
                        onNavigateToDetail(
                            item.id,
                            item.apiType,
                            selectedCatalog?.addonBaseUrl ?: ""
                        )
                    },
                    onItemLongPress = { item ->
                        onItemLongPress(item, selectedCatalog?.addonBaseUrl ?: "")
                    },
                    filterKey = "${uiState.selectedDiscoverType}|${uiState.selectedDiscoverCatalogKey}|" +
                        "${uiState.selectedDiscoverGenre}|${uiState.selectedDiscoverYear}|${uiState.discoverTmdbFilters}"
                )
                }
            }

            uiState.discoverInitialized && selectedCatalog == null -> {
                EmptyScreenState(
                    title = stringResource(R.string.discover_empty_no_catalog_title),
                    subtitle = stringResource(R.string.discover_empty_no_catalog_subtitle),
                    icon = Icons.Default.Search
                )
            }

            uiState.discoverInitialized && !uiState.discoverLoading && selectedCatalog != null -> {
                EmptyScreenState(
                    title = stringResource(R.string.discover_empty_no_content_title),
                    subtitle = stringResource(R.string.discover_empty_no_content_subtitle),
                    icon = Icons.Default.Search
                )
            }
        }
    }
}

internal data class DiscoverOption(
    val label: String,
    val value: String
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DiscoverGrid(
    items: List<MetaPreview>,
    posterCardStyle: PosterCardStyle,
    gridState: LazyGridState,
    upFocusRequester: FocusRequester? = null,
    watchedMovieIds: Set<String> = emptySet(),
    watchedSeriesIds: Set<String> = emptySet(),
    focusResults: Boolean,
    firstItemFocusRequester: FocusRequester,
    focusedItemIndex: Int,
    shouldRestoreFocusedItem: Boolean,
    onRestoreFocusedItemHandled: () -> Unit,
    onItemFocused: (Int) -> Unit,
    pendingCount: Int,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onItemClick: (Int, MetaPreview) -> Unit,
    onItemLongPress: (MetaPreview) -> Unit = {},
    filterKey: String = ""
) {
    val restoreFocusRequester = remember { FocusRequester() }
    // Items of the first grid row send Up to the filter pill row.
    val columnCount by remember(gridState) {
        derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1 }
    }
    var pendingFocusOnNewItemIndex by remember { mutableStateOf<Int?>(null) }

    // Scroll to top only when the filter combination actually changes (not on initial composition / back navigation).
    var previousFilterKey by remember { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (filterKey != previousFilterKey) {
            gridState.scrollToItem(0, 0)
            previousFilterKey = filterKey
        }
    }
    var localRestoreFocusedItemIndex by remember { mutableStateOf(-1) }
    var localShouldRestoreFocusedItem by remember { mutableStateOf(false) }
    val effectiveFocusedItemIndex = if (localShouldRestoreFocusedItem) {
        localRestoreFocusedItemIndex
    } else {
        focusedItemIndex
    }
    val effectiveShouldRestoreFocusedItem = shouldRestoreFocusedItem || localShouldRestoreFocusedItem
    val actionType = when {
        pendingCount > 0 -> DiscoverGridAction.ShowMore
        isLoadingMore -> DiscoverGridAction.Loading
        canLoadMore -> DiscoverGridAction.LoadMore
        else -> DiscoverGridAction.None
    }
    val totalCells = items.size + if (actionType != DiscoverGridAction.None) 1 else 0
    val hasActionCell = actionType != DiscoverGridAction.None

    val globalLandscape = com.nuvio.tv.ui.components.LocalLandscapePosterMode.current
    val adaptiveStyle = remember(posterCardStyle) {
        val cardWidth = posterCardStyle.width
        posterCardStyle.copy(
            width = cardWidth,
            height = cardWidth * 1.5f
        )
    }

    LaunchedEffect(effectiveShouldRestoreFocusedItem, effectiveFocusedItemIndex, totalCells) {
        if (!effectiveShouldRestoreFocusedItem) return@LaunchedEffect
        if (effectiveFocusedItemIndex !in 0 until totalCells) {
            if (localShouldRestoreFocusedItem) {
                localShouldRestoreFocusedItem = false
                localRestoreFocusedItemIndex = -1
            } else {
                onRestoreFocusedItemHandled()
            }
            return@LaunchedEffect
        }
        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        repeat(2) { withFrameNanos { } }
        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        if (localShouldRestoreFocusedItem) {
            localShouldRestoreFocusedItem = false
            localRestoreFocusedItemIndex = -1
        } else {
            onRestoreFocusedItemHandled()
        }
    }

    LaunchedEffect(items.size, pendingFocusOnNewItemIndex) {
        val targetIndex = pendingFocusOnNewItemIndex ?: return@LaunchedEffect
        if (items.size <= targetIndex) return@LaunchedEffect
        pendingFocusOnNewItemIndex = null
        localRestoreFocusedItemIndex = targetIndex
        localShouldRestoreFocusedItem = true
    }

    // Infinite scroll: auto-load more when the user scrolls near the end.
    val shouldAutoLoad = canLoadMore && !isLoadingMore && items.isNotEmpty()
    LaunchedEffect(gridState.firstVisibleItemIndex, items.size, shouldAutoLoad) {
        if (!shouldAutoLoad) return@LaunchedEffect
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= items.size - 6) {
            onLoadMore()
        }
    }

    // Per-item FocusRequesters for focusRestorer on the grid.
    // Pre-create the requester for the focused item so focusRestorer can use it
    // even before the grid items are composed (first return from navigation).
    // Index 0 uses the shared first-item requester so the pill row can jump straight to it.
    val itemFocusRequesters = remember(firstItemFocusRequester) {
        mutableMapOf<Int, FocusRequester>(0 to firstItemFocusRequester)
    }
    val focusedItemRequester = remember(focusedItemIndex) {
        itemFocusRequesters.getOrPut(focusedItemIndex) { FocusRequester() }
    }

    // Column the user was navigating in at the moment fast-scroll engaged.
    // Captured on drag-start (see onFastScrollingChanged below) because the
    // originally-focused card will have scrolled out of visibleItemsInfo by
    // the time landing runs — looking it up at landing time is unreliable
    // and was causing focus to jump to a different column after long drags.
    var fastScrollStartColumn by remember { mutableStateOf<Int?>(null) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(
            minSize = if (globalLandscape) posterCardStyle.height else adaptiveStyle.width
        ),
        modifier = Modifier.fillMaxSize()
            .focusRestorer { focusedItemRequester }
            .dpadVerticalFastScroll(
                scrollableState = gridState,
                onFastScrollingChanged = { active ->
                    // Only snapshot on drag-start. The modifier fires
                    // active=false BEFORE it calls resolveVerticalLanding,
                    // so clearing here would wipe the column right before
                    // landing reads it. Next drag-start will overwrite.
                    if (active) {
                        fastScrollStartColumn = gridState.layoutInfo
                            .visibleItemsInfo
                            .firstOrNull { it.index == focusedItemIndex }
                            ?.column
                    }
                },
                resolveVerticalLanding = { sign ->
                    // Keep the user on the column they were navigating in
                    // so fast-scroll feels like a single vertical strip,
                    // not a teleport to an arbitrary cell in the viewport.
                    // Exclude the action (Load More / Loading) cell up front
                    // — it lives at index == items.size and when items.size
                    // % columns == 0 it lands at column 0 of a brand-new
                    // tail row, which would otherwise hijack every column-0
                    // landing and bounce focus to the last content cell.
                    val contentVisible = gridState.layoutInfo.visibleItemsInfo
                        .filter { it.index < items.size }
                    if (contentVisible.isEmpty()) {
                        null
                    } else {
                        val col = fastScrollStartColumn
                        val sameColumn = if (col != null) {
                            contentVisible.filter { it.column == col }
                        } else {
                            emptyList()
                        }
                        val target = when {
                            sameColumn.isNotEmpty() && sign > 0 -> sameColumn.last()
                            sameColumn.isNotEmpty() && sign < 0 -> sameColumn.first()
                            sign > 0 -> contentVisible.last()
                            else -> contentVisible.first()
                        }
                        val requester = itemFocusRequesters[target.index]
                        runCatching { requester?.requestFocus() }
                        null // Discover uses imperative requestFocus for now
                    }
                }
            ),
        contentPadding = PaddingValues(bottom = NuvioTheme.spacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        itemsIndexed(
            items = items,
            key = { index, item -> item.id.ifEmpty { "discover_$index" } },
            contentType = { _, _ -> "content_card" }
        ) { index, item ->
            val itemFocusReq = remember(index) { itemFocusRequesters.getOrPut(index) { FocusRequester() } }
            val focusReq = when {
                effectiveShouldRestoreFocusedItem && index == effectiveFocusedItemIndex -> restoreFocusRequester
                focusResults && index == 0 -> firstItemFocusRequester
                else -> itemFocusReq
            }
            val itemRating = (item.imdbRating ?: item.tmdbRating)?.takeIf { it > 0f }
            val itemYear = item.releaseInfo?.take(4)?.takeIf { it.isNotBlank() }
            val itemMeta = listOfNotNull(
                itemYear,
                itemRating?.let { "\u2605 " + String.format(java.util.Locale.US, "%.1f", it) }
            ).joinToString("  \u00B7  ")
            Column(modifier = Modifier.width(adaptiveStyle.width)) {
                GridContentCard(
                    item = item,
                    onClick = { onItemClick(index, item) },
                    onLongPress = { onItemLongPress(item) },
                    posterCardStyle = adaptiveStyle,
                    isWatched = run {
                        val isSeries = item.apiType.equals("series", ignoreCase = true) || item.apiType.equals("tv", ignoreCase = true)
                        if (isSeries) item.id in watchedSeriesIds else item.id in watchedMovieIds
                    },
                    modifier = Modifier.padding(top = 3.dp),
                    focusRequester = focusReq,
                    upFocusRequester = if (index < columnCount) upFocusRequester else null,
                    onFocused = {
                        onItemFocused(index)
                    }
                )
                // Year and rating under every title; always one line so rows stay the same height.
                Text(
                    text = itemMeta.ifEmpty { " " },
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxs)
                )
            }
        }

        if (hasActionCell) {
            item(
                key = "discover_action",
                contentType = "action_card"
            ) {
                val actionIndex = items.size
                val focusReq = when {
                    effectiveShouldRestoreFocusedItem && actionIndex == effectiveFocusedItemIndex -> restoreFocusRequester
                    focusResults && items.isEmpty() -> firstItemFocusRequester
                    else -> null
                }
                DiscoverActionCard(
                    actionType = actionType,
                    posterCardStyle = adaptiveStyle,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(adaptiveStyle.width),
                    focusRequester = focusReq,
                    onFocused = { onItemFocused(actionIndex) },
                    onClick = {
                        when (actionType) {
                            DiscoverGridAction.ShowMore -> {
                                pendingFocusOnNewItemIndex = items.size
                                onLoadMore()
                            }
                            DiscoverGridAction.LoadMore -> {
                                pendingFocusOnNewItemIndex = items.size
                                onLoadMore()
                            }
                            DiscoverGridAction.Loading -> Unit
                            DiscoverGridAction.None -> Unit
                        }
                    }
                )
            }
        }
    }
}

private sealed class DiscoverGridAction {
    object None : DiscoverGridAction()
    object ShowMore : DiscoverGridAction()
    object LoadMore : DiscoverGridAction()
    object Loading : DiscoverGridAction()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoverActionCard(
    actionType: DiscoverGridAction,
    posterCardStyle: PosterCardStyle,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadius)
    val title = when (actionType) {
        DiscoverGridAction.ShowMore -> stringResource(R.string.discover_load_more)
        DiscoverGridAction.LoadMore -> stringResource(R.string.discover_load_more)
        DiscoverGridAction.Loading -> stringResource(R.string.discover_loading)
        DiscoverGridAction.None -> ""
    }
    val cardDepthStyle = LocalCardDepthStyle.current
    val globalLandscape = com.nuvio.tv.ui.components.LocalLandscapePosterMode.current

    Column(
        modifier = modifier.then(if (globalLandscape) Modifier.fillMaxWidth() else Modifier.width(posterCardStyle.width))
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .then(
                    if (globalLandscape) Modifier.fillMaxWidth().aspectRatio(com.nuvio.tv.domain.model.PosterShape.LANDSCAPE.aspectRatio())
                    else Modifier.width(posterCardStyle.width).height(posterCardStyle.height)
                )
                .focusProperties { canFocus = actionType != DiscoverGridAction.Loading }
                .onPreviewKeyEvent { event ->
                    actionType != DiscoverGridAction.None &&
                        event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                }
                .onFocusChanged { state -> if (state.isFocused) onFocused() }
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                ),
            shape = CardDefaults.shape(shape = cardShape),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(posterCardStyle.focusedBorderWidth),
                    shape = cardShape
                )
            ),
            scale = CardDefaults.scale(focusedScale = posterCardStyle.focusedScale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .nuvioCardDepth(
                        shape = cardShape,
                        surface = CardDepthSurface.POSTERS,
                        style = cardDepthStyle
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (actionType == DiscoverGridAction.Loading) {
                    LoadingIndicator()
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        // Reserve space for label to match GridContentCard height
        Spacer(
            modifier = Modifier
                .then(if (globalLandscape) Modifier.fillMaxWidth() else Modifier.width(posterCardStyle.width))
                .padding(top = NuvioTheme.spacing.sm)
                .height(
                    MaterialTheme.typography.titleMedium.lineHeight.value.dp +
                        MaterialTheme.typography.labelMedium.lineHeight.value.dp
                )
        )
    }
}
