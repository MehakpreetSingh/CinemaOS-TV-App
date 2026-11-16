package com.nuvio.tv.ui.screens.home.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.core.poster.patternForScreen
import com.nuvio.tv.core.poster.withCustomPosterUrls
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.domain.model.stableKey
import com.nuvio.tv.ui.components.ContinueWatchingOptionsDialog
import com.nuvio.tv.ui.components.continueWatchingImageModel
import com.nuvio.tv.ui.components.continueWatchingShouldBlur
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HeroBackdropState
import com.nuvio.tv.ui.screens.home.HomeEvent
import com.nuvio.tv.ui.screens.home.HomeRow
import com.nuvio.tv.ui.screens.home.HomeUiState
import com.nuvio.tv.ui.screens.home.HomeViewModel
import com.nuvio.tv.ui.screens.home.homeItemStatusKey
import com.nuvio.tv.ui.screens.home.openPosterListPicker
import com.nuvio.tv.ui.screens.home.refreshPosterLibraryStatus
import com.nuvio.tv.ui.screens.home.togglePosterLibrary
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.localizedContentType
import kotlinx.coroutines.delay

private const val HERO_AUTO_ADVANCE_MS = 8_000L
private val RowInset = 48.dp
private val HeroBlockMinHeight = 290.dp

private data class AppleCardStyle(
    val scale: Float,
    val corner: Dp,
    val landscape: Boolean,
    val showLabels: Boolean,
    val alwaysLogo: Boolean,
    val showRatings: Boolean,
    val expandFocused: Boolean = false,
    val expandDelayMs: Long = 3000L
)

/**
 * Apple TV style Home. Renders the state HomeViewModel already produces and respects Nuvio's home
 * settings (hero on/off, landscape/size/radius, labels, ratings, catalog titles, Continue Watching
 * options, collections). All actions map to the existing callbacks / view-model API.
 */
@Composable
fun AppleHomeContent(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> },
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit = onContinueWatchingClick,
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit = onContinueWatchingClick,
    showContinueWatchingManualPlayOption: Boolean = false
) {
    val background = NuvioTheme.colors.Background
    val listState = rememberLazyListState()

    // Rows in the user's configured order (catalog ordering/visibility is applied upstream).
    val homeRowList = remember(uiState.homeRows, uiState.catalogRows) {
        if (uiState.homeRows.isNotEmpty()) {
            uiState.homeRows.filter { it is HomeRow.Catalog || it is HomeRow.CollectionRow }
        } else {
            uiState.catalogRows.filter { it.items.isNotEmpty() }.map { HomeRow.Catalog(it) }
        }
    }
    val visibleRows = remember(homeRowList) {
        homeRowList.mapNotNull { (it as? HomeRow.Catalog)?.row }
    }

    val heroEnabled = uiState.heroSectionEnabled
    val heroItems = remember(uiState.heroItems, heroEnabled, visibleRows) {
        val featured = if (heroEnabled) uiState.heroItems else emptyList()
        if (featured.isNotEmpty()) featured.take(10)
        else visibleRows.firstOrNull { r -> r.items.any { !it.id.startsWith("__placeholder_") } }
            ?.items?.filter { !it.id.startsWith("__placeholder_") }
            ?.take(if (heroEnabled) 8 else 1).orEmpty()
    }
    val addonBaseById = remember(visibleRows) {
        buildMap<String, String> {
            visibleRows.forEach { row -> row.items.forEach { putIfAbsent(it.id, row.addonBaseUrl) } }
        }
    }
    val metaById = remember(visibleRows, heroItems) {
        buildMap<String, MetaPreview> {
            heroItems.forEach { putIfAbsent(it.id, it) }
            visibleRows.forEach { row -> row.items.forEach { putIfAbsent(it.id, it) } }
        }
    }
    val showRatings = uiState.homeImdbRatingsVisibility.showRatings

    var heroIndex by rememberSaveable { mutableStateOf(0) }
    var rowFocus by remember { mutableStateOf<HeroDisplay?>(null) }
    LaunchedEffect(heroIndex, rowFocus == null, heroItems.size, heroEnabled) {
        if (heroEnabled && heroItems.size > 1 && rowFocus == null) {
            delay(HERO_AUTO_ADVANCE_MS)
            heroIndex = (heroIndex + 1) % heroItems.size
        }
    }
    val currentHero = heroItems.getOrNull(heroIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
    LaunchedEffect(currentHero?.id) {
        currentHero?.let { viewModel.refreshPosterLibraryStatus(it) }
    }

    val cardStyle = remember(
        uiState.posterCardWidthDp,
        uiState.posterCardCornerRadiusDp,
        uiState.modernLandscapePostersEnabled,
        uiState.posterLabelsEnabled,
        uiState.alwaysShowLandscapeClearlogo,
        showRatings,
        uiState.focusedPosterBackdropExpandEnabled,
        uiState.focusedPosterBackdropExpandDelaySeconds
    ) {
        AppleCardStyle(
            scale = (uiState.posterCardWidthDp / 126f).coerceIn(0.8f, 1.4f),
            corner = uiState.posterCardCornerRadiusDp.dp,
            landscape = uiState.modernLandscapePostersEnabled,
            showLabels = uiState.posterLabelsEnabled,
            alwaysLogo = uiState.alwaysShowLandscapeClearlogo,
            showRatings = showRatings,
            expandFocused = uiState.focusedPosterBackdropExpandEnabled,
            expandDelayMs = uiState.focusedPosterBackdropExpandDelaySeconds.coerceAtLeast(0) * 1000L
        )
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // Tallest row drives the window: catalog cards, plus Continue Watching poster cards (title + episode label below).
    val cwPosterRow = uiState.continueWatchingCardStyle == ContinueWatchingCardStyle.POSTER &&
        uiState.continueWatchingItems.isNotEmpty()
    val catalogRowHeight = if (cardStyle.landscape) {
        135.dp * cardStyle.scale + 28.dp + 36.dp + 24.dp
    } else {
        195.dp * cardStyle.scale + 26.dp + 28.dp + 36.dp + 24.dp + 20.dp
    }
    val cwRowHeight = if (cwPosterRow) {
        195.dp * cardStyle.scale + 42.dp + 28.dp + 36.dp + 24.dp
    } else if (uiState.continueWatchingItems.isNotEmpty()) {
        (if (uiState.continueWatchingCardStyle == ContinueWatchingCardStyle.WIDE) 320.dp else 300.dp) * cardStyle.scale * 9f / 16f +
            28.dp + 36.dp + 24.dp
    } else {
        0.dp
    }
    val baseRowsHeight = if (catalogRowHeight > cwRowHeight) catalogRowHeight else cwRowHeight
    val rowsHeight = baseRowsHeight.coerceAtMost(screenHeight - HeroBlockMinHeight)
    val heroHeight = screenHeight - rowsHeight

    // Focused card in any row drives the hero/background (debounced), like Nuvio's Modern home.
    var shownRowFocus by remember { mutableStateOf<HeroDisplay?>(null) }
    LaunchedEffect(rowFocus) {
        delay(150)
        shownRowFocus = rowFocus
    }
    val carouselDisplay = remember(currentHero, showRatings) {
        currentHero?.toHeroDisplay()?.let { if (showRatings) it else it.copy(rating = null) }
    }
    val display = shownRowFocus ?: carouselDisplay

    // Continue Watching: apply custom posters like Classic does, then render.
    val cwPattern = patternForScreen(
        uiState.customPosterUrlPattern,
        CustomPosterScreen.CONTINUE_WATCHING,
        uiState.customPosterEnabledScreens
    )
    val cwItems = remember(uiState.continueWatchingItems, cwPattern) {
        uiState.continueWatchingItems.withCustomPosterUrls(cwPattern)
    }
    val upcomingItems = remember(uiState.upcomingItems, cwPattern) {
        uiState.upcomingItems.withCustomPosterUrls(cwPattern)
    }

    var cwOptionsItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }

    // Launch / return-to-Home focus: land on the hero button (like Nuvio focuses its first row),
    // not on the Home nav item.
    val contentFocus = com.nuvio.tv.LocalContentFocusRequester.current
    val heroReady = display != null && currentHero != null
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(heroReady) {
        if (heroReady && !initialFocusDone) {
            initialFocusDone = true
            repeat(10) {
                androidx.compose.runtime.withFrameNanos { }
                if (runCatching { contentFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                delay(60)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        AppleBackdrop(display = display, modifier = Modifier.fillMaxSize())
        // Left scrim only behind the text column
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
        )
        // Bottom fade only where the rows sit
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(rowsHeight + 40.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
        )

        if (display != null && currentHero != null) {
            val statusKey = homeItemStatusKey(currentHero.id, currentHero.apiType)
            AppleHeroSection(
                display = display,
                count = if (heroEnabled) heroItems.size else 1,
                index = heroIndex,
                onIndexChange = { heroIndex = it },
                height = heroHeight,
                inLibrary = uiState.posterLibraryMembership[statusKey] == true,
                canToggleLibrary = true,
                onPlay = {
                    HeroBackdropState.update(currentHero.backdropUrl)
                    onNavigateToDetail(currentHero.id, currentHero.apiType, addonBaseById[currentHero.id] ?: currentHero.sourceAddonBaseUrl.orEmpty())
                },
                onInfo = {
                    HeroBackdropState.update(currentHero.backdropUrl)
                    onNavigateToDetail(currentHero.id, currentHero.apiType, addonBaseById[currentHero.id] ?: currentHero.sourceAddonBaseUrl.orEmpty())
                },
                onToggleLibrary = {
                    val base = addonBaseById[currentHero.id] ?: currentHero.sourceAddonBaseUrl
                    if (uiState.librarySourceMode != LibrarySourceMode.LOCAL) {
                        viewModel.openPosterListPicker(currentHero, base)
                    } else {
                        viewModel.togglePosterLibrary(currentHero, base)
                    }
                },
                onHeroFocused = { rowFocus = null },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(rowsHeight),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (uiState.continueWatchingEnabled && cwItems.isNotEmpty()) {
                item(key = "apple_cw", contentType = "cw") {
                    AppleContinueRow(
                        title = stringResource(R.string.continue_watching),
                        items = cwItems,
                        uiState = uiState,
                        style = cardStyle,
                        metaById = metaById,
                        showRatings = showRatings,
                        onClick = onContinueWatchingClick,
                        onLongClick = { cwOptionsItem = it },
                        onFocused = { rowFocus = it }
                    )
                }
            }
            if (uiState.continueWatchingEnabled && upcomingItems.isNotEmpty()) {
                item(key = "apple_upcoming", contentType = "cw") {
                    AppleContinueRow(
                        title = stringResource(R.string.upcoming_section_title),
                        items = upcomingItems,
                        uiState = uiState,
                        style = cardStyle,
                        metaById = metaById,
                        showRatings = showRatings,
                        onClick = onContinueWatchingClick,
                        onLongClick = { cwOptionsItem = it },
                        onFocused = { rowFocus = it }
                    )
                }
            }

            items(
                items = homeRowList,
                key = {
                    when (it) {
                        is HomeRow.Catalog -> it.row.stableKey()
                        is HomeRow.CollectionRow -> "collection_${it.collection.id}"
                        is HomeRow.PlaceholderCatalog -> it.stableCatalogKey
                    }
                },
                contentType = { if (it is HomeRow.CollectionRow) "collectionRow" else "catalogRow" }
            ) { homeRow ->
                when (homeRow) {
                    is HomeRow.Catalog -> {
                        val row = homeRow.row
                        AppleCatalogRow(
                            row = row,
                            viewModel = viewModel,
                            style = cardStyle,
                            showAddonName = uiState.catalogAddonNameEnabled,
                            showTypeSuffix = uiState.catalogTypeSuffixEnabled,
                            onItemClick = { item ->
                                HeroBackdropState.update(item.backdropUrl)
                                onNavigateToDetail(item.id, item.apiType, row.addonBaseUrl)
                            },
                            onItemLongPress = { item -> onCatalogItemLongPress(item, row.addonBaseUrl) },
                            onItemFocused = { item ->
                                viewModel.onItemFocus(item)
                                rowFocus = item.toHeroDisplay().let { if (showRatings) it else it.copy(rating = null) }
                            }
                        )
                    }
                    is HomeRow.CollectionRow -> AppleCollectionRow(
                        collection = homeRow.collection,
                        style = cardStyle,
                        onFolderClick = onNavigateToFolderDetail,
                        onFocused = { rowFocus = it }
                    )
                    is HomeRow.PlaceholderCatalog -> Unit
                }
            }
        }

        val optionsItem = cwOptionsItem
        if (optionsItem != null) {
            val id = when (optionsItem) {
                is ContinueWatchingItem.InProgress -> optionsItem.progress.contentId
                is ContinueWatchingItem.NextUp -> optionsItem.info.contentId
            }
            val type = when (optionsItem) {
                is ContinueWatchingItem.InProgress -> optionsItem.progress.contentType
                is ContinueWatchingItem.NextUp -> optionsItem.info.contentType
            }
            ContinueWatchingOptionsDialog(
                item = optionsItem,
                onDismiss = { cwOptionsItem = null },
                onRemove = {
                    val season = when (optionsItem) {
                        is ContinueWatchingItem.InProgress -> optionsItem.progress.season
                        is ContinueWatchingItem.NextUp -> optionsItem.info.seedSeason
                    }
                    val episode = when (optionsItem) {
                        is ContinueWatchingItem.InProgress -> optionsItem.progress.episode
                        is ContinueWatchingItem.NextUp -> optionsItem.info.seedEpisode
                    }
                    viewModel.onEvent(
                        HomeEvent.OnRemoveContinueWatching(id, season, episode, optionsItem is ContinueWatchingItem.NextUp)
                    )
                    cwOptionsItem = null
                },
                onDetails = {
                    cwOptionsItem = null
                    onNavigateToDetail(id, type, "")
                },
                onStartFromBeginning = {
                    cwOptionsItem = null
                    onContinueWatchingStartFromBeginning(optionsItem)
                },
                showPlayManually = showContinueWatchingManualPlayOption,
                onPlayManually = {
                    cwOptionsItem = null
                    onContinueWatchingPlayManually(optionsItem)
                },
                onMarkWatched = {
                    cwOptionsItem = null
                    viewModel.onEvent(HomeEvent.OnMarkContinueWatchingWatched(optionsItem))
                }
            )
        }
    }
}

private fun distinctNonBlank(vararg urls: String?): List<String> =
    urls.filter { !it.isNullOrBlank() }.map { it!!.trim() }.distinct()

/** Hero info for a Continue Watching / Up Next card, topped up from the catalog meta when known. */
private fun ContinueWatchingItem.toHeroDisplay(meta: MetaPreview?, showRatings: Boolean): HeroDisplay {
    val year = { r: String? -> r?.takeIf { it.isNotBlank() }?.take(4) }
    return when (this) {
        is ContinueWatchingItem.InProgress -> {
            val p = progress
            val episodeLabel = if (p.season != null && p.episode != null) {
                "S${p.season} E${p.episode}" + (p.episodeTitle?.takeIf { it.isNotBlank() }?.let { " \u00b7 $it" } ?: "")
            } else null
            val parts = buildList {
                add(episodeLabel ?: "Continue Watching")
                (genres.ifEmpty { meta?.genres.orEmpty() }).take(2).forEach { add(it) }
                year(releaseInfo ?: meta?.releaseInfo)?.let { add(it) }
            }
            HeroDisplay(
                key = "cw_${p.contentId}_${p.season}_${p.episode}",
                backdrops = distinctNonBlank(
                    p.backdrop, meta?.background, customLandscapePoster, episodeThumbnail, meta?.landscapePoster, p.poster
                ),
                name = p.name,
                logo = p.logo?.takeIf { it.isNotBlank() } ?: meta?.logo,
                meta = parts.joinToString(" \u00b7 "),
                rating = (episodeImdbRating ?: meta?.imdbRating).takeIf { showRatings },
                synopsis = episodeDescription?.takeIf { it.isNotBlank() } ?: meta?.description
            )
        }
        is ContinueWatchingItem.NextUp -> {
            val i = info
            val parts = buildList {
                add("Up next \u00b7 S${i.season} E${i.episode}" + (i.episodeTitle?.takeIf { it.isNotBlank() }?.let { " \u00b7 $it" } ?: ""))
                (i.genres.ifEmpty { meta?.genres.orEmpty() }).take(2).forEach { add(it) }
                year(i.releaseInfo ?: meta?.releaseInfo)?.let { add(it) }
            }
            HeroDisplay(
                key = "nu_${i.contentId}_${i.season}_${i.episode}",
                backdrops = distinctNonBlank(
                    i.backdrop, meta?.background, customLandscapePoster, i.thumbnail, meta?.landscapePoster, i.poster
                ),
                name = i.name,
                logo = i.logo?.takeIf { it.isNotBlank() } ?: meta?.logo,
                meta = parts.joinToString(" \u00b7 "),
                rating = (i.imdbRating ?: meta?.imdbRating).takeIf { showRatings },
                synopsis = i.episodeDescription?.takeIf { it.isNotBlank() } ?: meta?.description
            )
        }
    }
}

private fun CollectionFolder.toHeroDisplay(collection: Collection): HeroDisplay = HeroDisplay(
    key = "folder_${collection.id}_$id",
    backdrops = distinctNonBlank(heroBackdropUrl, collection.backdropImageUrl, coverImageUrl),
    name = title,
    logo = titleLogoUrl,
    meta = collection.title,
    rating = null,
    synopsis = null
)

private fun ContinueWatchingItem.appleKey(): String = when (this) {
    is ContinueWatchingItem.InProgress -> "ip_${progress.contentId}_${progress.season}_${progress.episode}"
    is ContinueWatchingItem.NextUp -> "nu_${info.contentId}_${info.season}_${info.episode}"
}

@Composable
private fun AppleRowTitle(title: String, from: String? = null) {
    Row(
        modifier = Modifier.padding(start = RowInset, top = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 600.dp)
        )
        if (!from.isNullOrBlank()) {
            Text(
                text = from,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun AppleContinueRow(
    title: String,
    items: List<ContinueWatchingItem>,
    uiState: HomeUiState,
    style: AppleCardStyle,
    metaById: Map<String, MetaPreview>,
    showRatings: Boolean,
    onClick: (ContinueWatchingItem) -> Unit,
    onLongClick: (ContinueWatchingItem) -> Unit,
    onFocused: (HeroDisplay) -> Unit
) {
    Column {
        AppleRowTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = RowInset, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = items,
                key = { it.appleKey() },
                contentType = { "cwCard" }
            ) { cw ->
                AppleContinueCard(
                    item = cw,
                    uiState = uiState,
                    style = style,
                    onClick = { onClick(cw) },
                    onLongClick = { onLongClick(cw) },
                    onFocused = {
                        val id = when (cw) {
                            is ContinueWatchingItem.InProgress -> cw.progress.contentId
                            is ContinueWatchingItem.NextUp -> cw.info.contentId
                        }
                        onFocused(cw.toHeroDisplay(metaById[id], showRatings))
                    }
                )
            }
        }
    }
}

@Composable
private fun AppleContinueCard(
    item: ContinueWatchingItem,
    uiState: HomeUiState,
    style: AppleCardStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit
) {
    val cardKind = uiState.continueWatchingCardStyle
    val posterCard = cardKind == ContinueWatchingCardStyle.POSTER
    val useThumbs = uiState.useEpisodeThumbnailsInCw && !posterCard
    val s = style.scale
    val widthDp = when {
        posterCard -> 130.dp * s
        cardKind == ContinueWatchingCardStyle.WIDE -> 320.dp * s
        else -> 300.dp * s
    }
    val heightDp = when {
        posterCard -> 195.dp * s
        else -> widthDp * 9f / 16f // desktop: 16:9 cards
    }
    var badge: String? = null
    var kicker: String? = null
    var detail: String? = null
    val title: String
    val subtitle: String?
    val logo: String?
    val fraction: Float
    when (item) {
        is ContinueWatchingItem.InProgress -> {
            val p = item.progress
            title = p.name
            subtitle = if (p.season != null && p.episode != null) "S${p.season} E${p.episode}" else null
            logo = p.logo
            fraction = p.progressPercentage
            if (p.season != null && p.episode != null) {
                kicker = "S${p.season} E${p.episode}"
                detail = p.episodeTitle?.takeIf { it.isNotBlank() }
            }
            val mins = (p.remainingTime / 60_000L).coerceAtLeast(1L)
            badge = if (mins >= 60) "${mins / 60}h ${mins % 60}m left" else "${mins}m left"
        }
        is ContinueWatchingItem.NextUp -> {
            val i = item.info
            title = i.name
            subtitle = (if (i.hasAired) "Up next" else (i.airDateLabel ?: "Upcoming")) + " \u00b7 S${i.season} E${i.episode}"
            logo = i.logo
            fraction = 0f
            kicker = "S${i.season} E${i.episode}"
            detail = i.episodeTitle?.takeIf { it.isNotBlank() }
            badge = if (i.hasAired) "Next Up" else (i.airDateLabel ?: "Upcoming")
        }
    }
    val image = remember(item, useThumbs, posterCard) {
        continueWatchingImageModel(item, useThumbs, posterCard)
    }
    val blur = continueWatchingShouldBlur(item, uiState.blurUnwatchedEpisodes, useThumbs, posterCard)
    AppleArtCard(
        width = widthDp,
        height = heightDp,
        corner = style.corner,
        imageUrl = image,
        title = title,
        logoUrl = logo,
        subtitle = subtitle,
        overlay = !posterCard,
        alwaysLogo = style.alwaysLogo,
        rating = null,
        progress = fraction,
        blur = blur,
        belowLabel = if (posterCard) title else null,
        belowSubtitle = if (posterCard) subtitle else null,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        badge = if (posterCard) null else badge,
        kicker = if (posterCard) null else kicker,
        detail = if (posterCard) null else detail
    )
}

@Composable
private fun AppleCatalogRow(
    row: CatalogRow,
    viewModel: HomeViewModel,
    style: AppleCardStyle,
    showAddonName: Boolean,
    showTypeSuffix: Boolean,
    onItemClick: (MetaPreview) -> Unit,
    onItemLongPress: (MetaPreview) -> Unit,
    onItemFocused: (MetaPreview) -> Unit
) {
    val isPlaceholder = row.isLoading && row.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
    if (isPlaceholder) {
        // Same trigger Nuvio's classic home uses once a lazy row scrolls into composition.
        LaunchedEffect(row.legacyKey()) { viewModel.requestLazyCatalogLoad(row.legacyKey()) }
    }
    val rowState = rememberLazyListState()
    val rowItems = remember(row.items) { row.items.distinctBy { "${it.id}_${it.apiType}" } }
    val itemCount = row.items.size
    LaunchedEffect(rowState, itemCount, row.hasMore, row.isLoading) {
        if (isPlaceholder || !row.hasMore) return@LaunchedEffect
        snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= 0 && last >= itemCount - 4 && !row.isLoading) {
                    viewModel.onEvent(HomeEvent.OnLoadMoreCatalog(row.catalogId, row.addonId, row.apiType))
                }
            }
    }
    val context = LocalContext.current
    val title = remember(row.catalogName, row.rawType, row.apiType, showTypeSuffix) {
        val name = row.catalogName.replaceFirstChar { it.uppercase() }
        val typeLabel = localizedContentType(context, row.rawType.takeIf { it.isNotBlank() } ?: row.apiType)
        if (name.isBlank()) "" else if (showTypeSuffix && typeLabel.isNotEmpty()) "$name - $typeLabel" else name
    }
    Column {
        AppleRowTitle(
            title = title,
            from = if (showAddonName && title.isNotBlank()) stringResource(R.string.catalog_from_addon, row.addonName) else null
        )
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = RowInset, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = rowItems,
                key = { "${it.id}_${it.apiType}" },
                contentType = { "posterCard" }
            ) { item ->
                ApplePosterCard(
                    item = item,
                    style = style,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongPress(item) },
                    onFocused = { onItemFocused(item) }
                )
            }
        }
    }
}

@Composable
private fun ApplePosterCard(
    item: MetaPreview,
    style: AppleCardStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit
) {
    val s = style.scale
    val landscape = style.landscape
    var focusedNow by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(focusedNow, style.expandFocused, style.expandDelayMs) {
        if (focusedNow && style.expandFocused && !landscape) {
            delay(style.expandDelayMs.coerceAtLeast(150L))
            expanded = true
        } else {
            expanded = false
        }
    }
    val expandedNow = expanded && !landscape
    val targetWidth = if (landscape) 240.dp * s else if (expandedNow) 240.dp * s else 130.dp * s
    val widthDp by androidx.compose.animation.core.animateDpAsState(targetWidth, androidx.compose.animation.core.tween(300), label = "posterExpand")
    val heightDp = (if (landscape) 135.dp else 195.dp) * s
    val url = if (landscape || expandedNow) (item.landscapePoster ?: item.background ?: item.poster) else item.poster
    AppleArtCard(
        width = widthDp,
        height = heightDp,
        corner = style.corner,
        imageUrl = url,
        title = item.name,
        logoUrl = item.logo,
        subtitle = null,
        overlay = (landscape || expandedNow) && style.showLabels,
        alwaysLogo = style.alwaysLogo,
        rating = item.imdbRating.takeIf { style.showRatings },
        progress = 0f,
        blur = false,
        belowLabel = if (!landscape && style.showLabels) item.name else null,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        onFocusChange = { focusedNow = it }
    )
}

@Composable
private fun AppleCollectionRow(
    collection: Collection,
    style: AppleCardStyle,
    onFolderClick: (String, String) -> Unit,
    onFocused: (HeroDisplay) -> Unit
) {
    Column {
        AppleRowTitle(collection.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = RowInset, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = collection.folders,
                key = { "${collection.id}_${it.id}" },
                contentType = { "folderCard" }
            ) { folder ->
                val s = style.scale
                val (w, h) = when (folder.tileShape) {
                    PosterShape.LANDSCAPE -> 240.dp * s to 135.dp * s
                    PosterShape.POSTER -> 130.dp * s to 195.dp * s
                    PosterShape.SQUARE -> 135.dp * s to 135.dp * s
                }
                AppleArtCard(
                    width = w,
                    height = h,
                    corner = style.corner,
                    imageUrl = folder.coverImageUrl ?: folder.heroBackdropUrl,
                    title = folder.title,
                    logoUrl = folder.titleLogoUrl,
                    subtitle = null,
                    overlay = !folder.hideTitle && style.showLabels,
                    alwaysLogo = style.alwaysLogo,
                    rating = null,
                    progress = 0f,
                    blur = false,
                    belowLabel = null,
                    emoji = folder.coverEmoji,
                    onClick = { onFolderClick(collection.id, folder.id) },
                    onLongClick = null,
                    onFocused = { onFocused(folder.toHeroDisplay(collection)) }
                )
            }
        }
    }
}

/**
 * One Apple-style card: art, optional bottom-left logo/title overlay (with subtitle), optional
 * rating chip and progress bar, optional label under the card.
 */
@Composable
private fun AppleArtCard(
    width: Dp,
    height: Dp,
    corner: Dp,
    imageUrl: String?,
    title: String,
    logoUrl: String?,
    subtitle: String?,
    overlay: Boolean,
    alwaysLogo: Boolean,
    rating: Float?,
    progress: Float,
    blur: Boolean,
    belowLabel: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocused: () -> Unit,
    emoji: String? = null,
    belowSubtitle: String? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    badge: String? = null,
    kicker: String? = null,
    detail: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val shape: Shape = RoundedCornerShape(corner)
    val wPx = with(density) { width.roundToPx() }
    val hPx = with(density) { height.roundToPx() }
    val request = remember(imageUrl, wPx, hPx) {
        ImageRequest.Builder(context).data(imageUrl).size(width = wPx, height = hPx).crossfade(true).build()
    }
    Column(modifier = Modifier.width(width)) {
        AppleFocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            onFocusChange = onFocusChange,
            shape = shape,
            modifier = Modifier.width(width).height(height)
        ) {
            if (imageUrl.isNullOrBlank() && !emoji.isNullOrBlank()) {
                Text(
                    text = emoji,
                    fontSize = 40.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (blur) Modifier.blur(18.dp) else Modifier)
                )
            }
            if (overlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f)
                            )
                        )
                        .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = if (progress > 0f) 12.dp else 9.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!kicker.isNullOrBlank()) {
                            Text(
                                text = kicker,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1
                            )
                        }
                        var logoFailed by remember(logoUrl) { mutableStateOf(false) }
                        if (alwaysLogo && !logoUrl.isNullOrBlank() && !logoFailed) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                onError = { logoFailed = true },
                                modifier = Modifier.height(height * 0.22f).widthIn(max = width * 0.6f)
                            )
                        } else {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val sub = detail ?: subtitle
                        if (!sub.isNullOrBlank()) {
                            Text(
                                text = sub,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
            }
            if (rating != null && rating > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", rating),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = if (kicker != null) 10.dp else 0.dp, vertical = if (kicker != null) 6.dp else 0.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(Color.White)
                    )
                }
            }
        }
        if (!belowLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = belowLabel,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!belowSubtitle.isNullOrBlank()) {
                Text(
                    text = belowSubtitle,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
