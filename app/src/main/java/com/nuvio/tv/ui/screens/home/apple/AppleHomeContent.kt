package com.nuvio.tv.ui.screens.home.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.domain.model.stableKey
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
import kotlinx.coroutines.delay

private const val HERO_AUTO_ADVANCE_MS = 8_000L
private val HeroPeekHeight = 118.dp
private val RowInset = 48.dp
private val CardShape = RoundedCornerShape(12.dp)

/**
 * Apple TV style Home. Renders the state HomeViewModel already produces: hero/featured items,
 * continue watching and catalog rows. All actions map to the existing callbacks / view-model API.
 */
@Composable
fun AppleHomeContent(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit
) {
    val background = NuvioTheme.colors.Background
    val listState = rememberLazyListState()

    val visibleRows = remember(uiState.homeRows, uiState.catalogRows) {
        if (uiState.homeRows.isNotEmpty()) {
            uiState.homeRows.mapNotNull { (it as? HomeRow.Catalog)?.row }
        } else {
            uiState.catalogRows.filter { it.items.isNotEmpty() }
        }
    }

    val heroItems = remember(uiState.heroItems, uiState.heroSectionEnabled, visibleRows) {
        val featured = if (uiState.heroSectionEnabled) uiState.heroItems else emptyList()
        if (featured.isNotEmpty()) featured.take(10)
        else visibleRows.firstOrNull { r -> r.items.any { !it.id.startsWith("__placeholder_") } }
            ?.items?.filter { !it.id.startsWith("__placeholder_") }?.take(8).orEmpty()
    }
    val addonBaseById = remember(visibleRows) {
        buildMap<String, String> {
            visibleRows.forEach { row -> row.items.forEach { putIfAbsent(it.id, row.addonBaseUrl) } }
        }
    }

    var heroIndex by rememberSaveable { mutableStateOf(0) }
    val heroVisible by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 }
    }
    LaunchedEffect(heroIndex, heroVisible, heroItems.size) {
        if (heroItems.size > 1 && heroVisible) {
            delay(HERO_AUTO_ADVANCE_MS)
            heroIndex = (heroIndex + 1) % heroItems.size
        }
    }
    val currentHero = heroItems.getOrNull(heroIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
    LaunchedEffect(currentHero?.id) {
        currentHero?.let { viewModel.refreshPosterLibraryStatus(it) }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeight - HeroPeekHeight
    val heroHeightPx = with(LocalDensity.current) { heroHeight.toPx() }

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (currentHero != null) {
                item(key = "apple_hero", contentType = "hero") {
                    val statusKey = homeItemStatusKey(currentHero.id, currentHero.apiType)
                    AppleHeroSection(
                        items = heroItems,
                        index = heroIndex,
                        onIndexChange = { heroIndex = it },
                        height = heroHeight,
                        inLibrary = uiState.posterLibraryMembership[statusKey] == true,
                        canToggleLibrary = true,
                        backgroundColor = background,
                        onPlay = { item ->
                            // No direct play action exists on Home: open details (same as Nuvio's hero click).
                            HeroBackdropState.update(item.backdropUrl)
                            onNavigateToDetail(item.id, item.apiType, addonBaseById[item.id] ?: item.sourceAddonBaseUrl.orEmpty())
                        },
                        onInfo = { item ->
                            HeroBackdropState.update(item.backdropUrl)
                            onNavigateToDetail(item.id, item.apiType, addonBaseById[item.id] ?: item.sourceAddonBaseUrl.orEmpty())
                        },
                        onToggleLibrary = { item ->
                            val base = addonBaseById[item.id] ?: item.sourceAddonBaseUrl
                            if (uiState.librarySourceMode != LibrarySourceMode.LOCAL) {
                                viewModel.openPosterListPicker(item, base)
                            } else {
                                viewModel.togglePosterLibrary(item, base)
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            // Dim and shrink the hero as the rows scroll over it (read in the layer: no recomposition).
                            val f = if (listState.firstVisibleItemIndex > 0) 1f
                            else (listState.firstVisibleItemScrollOffset / heroHeightPx).coerceIn(0f, 1f)
                            alpha = 1f - 0.65f * f
                            scaleX = 1f - 0.05f * f
                            scaleY = 1f - 0.05f * f
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                    )
                }
            }

            if (uiState.continueWatchingEnabled && uiState.continueWatchingItems.isNotEmpty()) {
                item(key = "apple_cw", contentType = "cw") {
                    Column {
                        AppleRowTitle("Continue Watching")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = RowInset, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = uiState.continueWatchingItems,
                                key = { it.appleKey() },
                                contentType = { "cwCard" }
                            ) { cw ->
                                AppleContinueCard(cw, onClick = { onContinueWatchingClick(cw) })
                            }
                        }
                    }
                }
            }

            items(
                items = visibleRows,
                key = { it.stableKey() },
                contentType = { "catalogRow" }
            ) { row ->
                AppleCatalogRow(
                    row = row,
                    viewModel = viewModel,
                    onItemClick = { item ->
                        HeroBackdropState.update(item.backdropUrl)
                        onNavigateToDetail(item.id, item.apiType, row.addonBaseUrl)
                    },
                    onItemLongPress = { item -> onCatalogItemLongPress(item, row.addonBaseUrl) }
                )
            }
        }
    }
}

@Composable
private fun AppleRowTitle(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.92f),
        modifier = Modifier.padding(start = RowInset, top = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun ContinueWatchingItem.appleKey(): String = when (this) {
    is ContinueWatchingItem.InProgress -> "ip_${progress.contentId}_${progress.season}_${progress.episode}"
    is ContinueWatchingItem.NextUp -> "nu_${info.contentId}_${info.season}_${info.episode}"
}

@Composable
private fun AppleContinueCard(item: ContinueWatchingItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthDp = 240.dp
    val heightDp = 135.dp
    val wPx = with(density) { widthDp.roundToPx() }
    val hPx = with(density) { heightDp.roundToPx() }

    val title: String
    val subtitle: String?
    val image: String?
    val fraction: Float
    when (item) {
        is ContinueWatchingItem.InProgress -> {
            val p = item.progress
            title = p.name
            subtitle = if (p.season != null && p.episode != null) "S${p.season} E${p.episode}" else null
            image = item.customLandscapePoster ?: item.episodeThumbnail ?: p.backdrop ?: p.poster
            fraction = p.progressPercentage
        }
        is ContinueWatchingItem.NextUp -> {
            val i = item.info
            title = i.name
            subtitle = "S${i.season} E${i.episode}"
            image = item.customLandscapePoster ?: i.thumbnail ?: i.backdrop ?: i.poster
            fraction = 0f
        }
    }
    val request = remember(image, wPx, hPx) {
        ImageRequest.Builder(context).data(image).size(width = wPx, height = hPx).crossfade(true).build()
    }
    Column(modifier = Modifier.width(widthDp)) {
        AppleFocusSurface(
            onClick = onClick,
            shape = CardShape,
            modifier = Modifier.width(widthDp).height(heightDp)
        ) {
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(Color.White)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
        }
    }
}

@Composable
private fun AppleCatalogRow(
    row: CatalogRow,
    viewModel: HomeViewModel,
    onItemClick: (MetaPreview) -> Unit,
    onItemLongPress: (MetaPreview) -> Unit
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
    Column {
        AppleRowTitle(row.catalogName)
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
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongPress(item) }
                )
            }
        }
    }
}

@Composable
private fun ApplePosterCard(item: MetaPreview, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val landscape = item.posterShape == PosterShape.LANDSCAPE
    val widthDp = if (landscape) 240.dp else 130.dp
    val heightDp = if (landscape) 135.dp else 195.dp
    val wPx = with(density) { widthDp.roundToPx() }
    val hPx = with(density) { heightDp.roundToPx() }
    val url = if (landscape) (item.landscapePoster ?: item.background ?: item.poster) else item.poster
    val request = remember(url, wPx, hPx) {
        ImageRequest.Builder(context).data(url).size(width = wPx, height = hPx).crossfade(true).build()
    }
    Column(modifier = Modifier.width(widthDp)) {
        AppleFocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = CardShape,
            modifier = Modifier.width(widthDp).height(heightDp)
        ) {
            AsyncImage(
                model = request,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.name,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
