package com.nuvio.tv.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.SportsAddon
import com.nuvio.tv.ui.components.ContentCard
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.LocalLandscapePosterMode
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.SourceStatusFilterChip
import com.nuvio.tv.ui.screens.collection.NuvioTextField
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

private const val CHIP_LIVE_TV = "chip|live_tv"
private const val CHIP_ALL = "chip|all"
private fun chipKey(sportId: String) = "chip|$sportId"
private fun rowCardKey(rowKey: String, cardKey: String) = "card|$rowKey|$cardKey"
private fun channelKey(cardKey: String) = "tv|$cardKey"

/** Frames the entry focus keeps retrying while the page composes / the sidebar lets go. */
private const val ENTRY_FOCUS_ATTEMPTS = 12
/** Attempts spent on the remembered target before falling back to the first chip / card. */
private const val RESTORE_ONLY_ATTEMPTS = 4

/**
 * Sports tab: live, upcoming, per-sport, 24/7 TV and replay rows from the built-in Sports addon.
 * Selecting a card opens the regular details screen for that addon item, whose play action goes
 * through the normal streams screen to the player.
 *
 * Focus: drawer navigation to a different root route does not move focus into the page (the
 * sidebar only does that for a re-selected route), so this screen claims it itself whenever it
 * is shown with the sidebar closed and nothing inside it focused: the last focused chip/card
 * (also after returning from details), else the selected chip, else the first card.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SportsScreen(
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit,
    viewModel: SportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Live Now refreshes only while this screen is started (visible).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenHidden()
        }
    }

    val horizontalPadding = NuvioTheme.spacing.xxl

    // ---- Focus bookkeeping -------------------------------------------------------------------
    val focusRequesters = remember { HashMap<String, FocusRequester>() }
    fun requesterFor(key: String): FocusRequester = focusRequesters.getOrPut(key) { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pageHasFocus by remember { mutableStateOf(false) }
    val sidebarExpanded = LocalSidebarExpanded.current

    val showChips = uiState.filters.isNotEmpty() || uiState.hasLiveTv
    val selectedChipKey = when {
        uiState.isLiveTvSelected -> CHIP_LIVE_TV
        uiState.selectedSportId != null -> chipKey(uiState.selectedSportId!!)
        else -> CHIP_ALL
    }
    val firstChipKey = if (uiState.hasLiveTv) CHIP_LIVE_TV else CHIP_ALL
    val firstCardKey = if (uiState.isLiveTvSelected) {
        uiState.liveTv.items.firstOrNull()?.let { channelKey(it.key) }
    } else {
        uiState.rows.firstOrNull()?.let { row -> row.items.firstOrNull()?.let { rowCardKey(row.key, it.key) } }
    }
    val contentReady = showChips || firstCardKey != null

    LaunchedEffect(sidebarExpanded, contentReady) {
        if (sidebarExpanded || !contentReady) return@LaunchedEffect
        fun tryFocus(key: String?): Boolean {
            if (key == null) return false
            val requester = focusRequesters[key] ?: return false
            return runCatching { requester.requestFocus() }.getOrDefault(false)
        }
        repeat(ENTRY_FOCUS_ATTEMPTS) { attempt ->
            withFrameNanos { }
            if (pageHasFocus) return@LaunchedEffect
            val restored = tryFocus(lastFocusedKey)
            val focused = restored || (
                (lastFocusedKey == null || attempt >= RESTORE_ONLY_ATTEMPTS) &&
                    (tryFocus(selectedChipKey) || tryFocus(firstChipKey) || tryFocus(firstCardKey))
                )
            if (focused) return@LaunchedEffect
            delay(40)
        }
    }

    val onFocusKey: (String) -> Unit = { key -> lastFocusedKey = key }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gap = NuvioTheme.spacing.md
        val channelCardWidth = channelCardWidth(uiState.liveTv.items.firstOrNull()?.preview?.posterShape)
        val channelColumns = ((maxWidth - horizontalPadding * 2 + gap) / (channelCardWidth + gap))
            .toInt()
            .coerceAtLeast(1)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { pageHasFocus = it.hasFocus },
            contentPadding = PaddingValues(top = NuvioTheme.spacing.xl, bottom = NuvioTheme.spacing.huge),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xl)
        ) {
            item(key = "sports_header", contentType = "header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.sports_title),
                        style = NuvioTheme.textStyles.headline,
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.padding(horizontal = horizontalPadding)
                    )
                    if (showChips) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NuvioTheme.spacing.md)
                                .focusRestorer(),
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
                        ) {
                            if (uiState.hasLiveTv) {
                                item(key = "filter_live_tv") {
                                    SourceStatusFilterChip(
                                        name = stringResource(R.string.sports_filter_live_tv),
                                        isSelected = uiState.isLiveTvSelected,
                                        onClick = viewModel::selectLiveTv,
                                        modifier = Modifier.chipFocus(CHIP_LIVE_TV, ::requesterFor, onFocusKey)
                                    )
                                }
                            }
                            item(key = "filter_all") {
                                SourceStatusFilterChip(
                                    name = stringResource(R.string.sports_filter_all),
                                    isSelected = !uiState.isLiveTvSelected && uiState.selectedSportId == null,
                                    onClick = { viewModel.selectSport(null) },
                                    modifier = Modifier.chipFocus(CHIP_ALL, ::requesterFor, onFocusKey)
                                )
                            }
                            items(uiState.filters, key = { "filter_${it.id}" }) { filter ->
                                SourceStatusFilterChip(
                                    name = filter.label,
                                    isSelected = !uiState.isLiveTvSelected && uiState.selectedSportId == filter.id,
                                    onClick = { viewModel.selectSport(filter.id) },
                                    modifier = Modifier.chipFocus(chipKey(filter.id), ::requesterFor, onFocusKey)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLiveTvSelected) {
                liveTvContent(
                    state = uiState.liveTv,
                    searchSupported = uiState.liveTvSearchSupported,
                    columns = channelColumns,
                    horizontalPadding = horizontalPadding,
                    gap = gap,
                    requesterFor = ::requesterFor,
                    onFocusKey = onFocusKey,
                    onQueryChange = viewModel::onLiveTvQueryChange,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMoreLiveTv,
                    onItemClick = { card ->
                        onNavigateToDetail(card.preview.id, card.preview.apiType, SportsAddon.BASE_URL)
                    }
                )
            } else {
                val errorRes = uiState.errorRes
                when {
                    uiState.rows.isEmpty() && errorRes != null -> item(key = "sports_error", contentType = "state") {
                        ErrorState(
                            message = stringResource(errorRes),
                            onRetry = viewModel::retry,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
                        )
                    }
                    uiState.rows.isEmpty() && uiState.isLoading -> item(key = "sports_loading", contentType = "state") {
                        CenteredLoading()
                    }
                    uiState.rows.isEmpty() -> item(key = "sports_empty", contentType = "state") {
                        EmptyScreenState(
                            title = stringResource(R.string.sports_empty_title),
                            subtitle = stringResource(R.string.sports_empty_subtitle),
                            icon = Icons.Default.SportsSoccer
                        )
                    }
                    else -> items(uiState.rows, key = { it.key }, contentType = { "sports_row" }) { row ->
                        SportsRow(
                            row = row,
                            horizontalPadding = horizontalPadding,
                            requesterFor = ::requesterFor,
                            onFocusKey = onFocusKey,
                            onItemClick = { card ->
                                onNavigateToDetail(card.preview.id, card.preview.apiType, SportsAddon.BASE_URL)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.chipFocus(
    key: String,
    requesterFor: (String) -> FocusRequester,
    onFocusKey: (String) -> Unit
): Modifier = this
    .focusRequester(requesterFor(key))
    .onFocusChanged { if (it.isFocused) onFocusKey(key) }

@Composable
private fun channelCardWidth(shape: PosterShape?): Dp {
    val style = PosterCardDefaults.Style
    val effective = if (LocalLandscapePosterMode.current) PosterShape.LANDSCAPE else (shape ?: PosterShape.LANDSCAPE)
    return when (effective) {
        PosterShape.POSTER -> style.width
        PosterShape.LANDSCAPE -> style.height
        PosterShape.SQUARE -> 170.dp
    }
}

/** The Live TV chip's view: an optional search field, then the channels as a grid of rows. */
@OptIn(ExperimentalTvMaterial3Api::class)
private fun androidx.compose.foundation.lazy.LazyListScope.liveTvContent(
    state: LiveTvState,
    searchSupported: Boolean,
    columns: Int,
    horizontalPadding: Dp,
    gap: Dp,
    requesterFor: (String) -> FocusRequester,
    onFocusKey: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (SportsCardModel) -> Unit
) {
    item(key = "live_tv_search", contentType = "search") {
        NuvioTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(
                if (searchSupported) R.string.sports_live_tv_search_hint else R.string.sports_live_tv_filter_hint
            ),
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
        )
    }

    val errorRes = state.errorRes
    when {
        state.items.isEmpty() && errorRes != null -> item(key = "live_tv_error", contentType = "state") {
            ErrorState(
                message = stringResource(errorRes),
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
            )
        }
        state.items.isEmpty() && (state.isLoading || !state.loaded) -> item(key = "live_tv_loading", contentType = "state") {
            CenteredLoading()
        }
        state.items.isEmpty() -> item(key = "live_tv_empty", contentType = "state") {
            EmptyScreenState(
                title = stringResource(
                    if (state.query.isBlank()) R.string.sports_live_tv_empty_title else R.string.sports_live_tv_no_results
                ),
                icon = Icons.Default.LiveTv
            )
        }
        else -> {
            val chunks = state.items.chunked(columns)
            itemsIndexed(
                chunks,
                key = { _, chunk -> "live_tv_row_${chunk.first().key}" },
                contentType = { _, _ -> "live_tv_row" }
            ) { index, chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    chunk.forEach { card ->
                        val key = channelKey(card.key)
                        ContentCard(
                            item = card.preview,
                            focusRequester = requesterFor(key),
                            showImdbRatings = false,
                            onFocus = {
                                onFocusKey(key)
                                // Page in more channels as focus nears the end of the list.
                                if (index >= chunks.size - 2) onLoadMore()
                            },
                            onClick = { onItemClick(card) }
                        )
                    }
                }
            }
            if (state.isLoadingMore) {
                item(key = "live_tv_loading_more", contentType = "state") {
                    CenteredLoading(topPadding = NuvioTheme.spacing.md)
                }
            }
        }
    }
}

@Composable
private fun CenteredLoading(topPadding: Dp = NuvioTheme.spacing.huge) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SportsRow(
    row: SportsRowModel,
    horizontalPadding: Dp,
    requesterFor: (String) -> FocusRequester,
    onFocusKey: (String) -> Unit,
    onItemClick: (SportsCardModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.titleRes?.let { stringResource(it) } ?: row.title.orEmpty(),
            style = NuvioTheme.textStyles.sectionTitle,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = NuvioTheme.spacing.sm)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            items(row.items, key = { it.key }, contentType = { "sports_card" }) { card ->
                val key = rowCardKey(row.key, card.key)
                Box {
                    ContentCard(
                        item = card.preview,
                        focusRequester = requesterFor(key),
                        showImdbRatings = false,
                        onFocus = { onFocusKey(key) },
                        onClick = { onItemClick(card) }
                    )
                    if (card.isLive) {
                        LiveBadge(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(NuvioTheme.spacing.sm)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.sports_live_badge),
        color = NuvioTheme.colors.OnPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(NuvioTheme.colors.Error, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
