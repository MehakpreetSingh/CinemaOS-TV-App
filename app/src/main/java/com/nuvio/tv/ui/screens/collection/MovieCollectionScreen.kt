package com.nuvio.tv.ui.screens.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.dpadRepeatThrottle

/**
 * Full-screen page for one TMDB movie collection: name, backdrop and overview on top, then a
 * focusable grid of every movie in release order. Opening a movie goes to its details; Back returns
 * to the details screen that opened the page. Named so it does not clash with the app's own
 * [com.nuvio.tv.domain.model.Collection] (home/addon collections).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCollectionScreen(
    viewModel: MovieCollectionViewModel = hiltViewModel(),
    onBackPress: () -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val cornerRadiusDp by viewModel.posterCardCornerRadiusDp.collectAsState()

    BackHandler { onBackPress() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        when (val state = uiState) {
            is MovieCollectionUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        viewModel.routeName?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = NuvioTheme.colors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))
                        }
                        LoadingIndicator()
                    }
                }
            }

            is MovieCollectionUiState.Error -> {
                MovieCollectionError(message = state.message, onRetry = viewModel::retry)
            }

            is MovieCollectionUiState.Success -> {
                MovieCollectionContent(
                    collection = state.collection,
                    cornerRadiusDp = cornerRadiusDp,
                    onNavigateToDetail = onNavigateToDetail
                )
            }
        }
    }
}

@Composable
private fun MovieCollectionContent(
    collection: MovieCollectionData,
    cornerRadiusDp: Int,
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit
) {
    val movies = collection.movies
    val gridState = rememberLazyGridState()
    // Survives the trip to a movie's details and back, so Back lands on the card you left.
    var lastFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val targetId = lastFocusedId?.takeIf { id -> movies.any { it.id == id } } ?: movies.firstOrNull()?.id
    val targetFocusRequester = remember { FocusRequester() }

    val cardStyle = remember(cornerRadiusDp) {
        PosterCardStyle(
            width = 260.dp,
            height = 146.dp,
            cornerRadius = cornerRadiusDp.dp,
            focusedBorderWidth = NuvioTheme.spacing.xxs,
            focusedScale = 1.02f
        )
    }

    LaunchedEffect(movies) {
        val targetIndex = movies.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            // +1: the header is the first grid item.
            val visible = gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex + 1 }
            if (!visible) gridState.scrollToItem(targetIndex + 1)
        }
        targetFocusRequester.requestFocusAfterFrames(frames = 3)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CollectionBackdrop(imageUrl = collection.backdrop ?: collection.poster)

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = cardStyle.width),
            modifier = Modifier
                .fillMaxSize()
                .dpadRepeatThrottle(),
            contentPadding = PaddingValues(
                start = NuvioTheme.spacing.xxxl,
                end = NuvioTheme.spacing.xxxl,
                top = NuvioTheme.spacing.xl,
                bottom = NuvioTheme.spacing.xxl
            ),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
        ) {
            item(key = "collection_header", span = { GridItemSpan(maxLineSpan) }) {
                CollectionHeader(collection = collection)
            }
            itemsIndexed(
                items = movies,
                key = { index, movie -> "${movie.id}_$index" }
            ) { _, movie ->
                Column {
                    GridContentCard(
                        item = movie,
                        posterCardStyle = cardStyle,
                        showLabel = true,
                        showReleaseInfo = true,
                        focusRequester = if (movie.id == targetId) targetFocusRequester else null,
                        onFocused = { lastFocusedId = movie.id },
                        onClick = {
                            lastFocusedId = movie.id
                            onNavigateToDetail(movie.id, movie.apiType, null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionBackdrop(imageUrl: String?) {
    val context = LocalContext.current
    val background = NuvioTheme.colors.Background
    Box(modifier = Modifier.fillMaxSize()) {
        if (!imageUrl.isNullOrBlank()) {
            val request = remember(imageUrl) {
                ImageRequest.Builder(context).data(imageUrl).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .alpha(0.55f)
            )
        }
        // Fade the art into the page background, then darken the left edge behind the text.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.30f to background.copy(alpha = 0.55f),
                        0.62f to background
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = 0.85f),
                        0.55f to Color.Transparent
                    )
                )
        )
    }
}

@Composable
private fun CollectionHeader(collection: MovieCollectionData) {
    val context = LocalContext.current
    val count = collection.movies.size
    val years = remember(collection.movies) {
        val parsed = collection.movies.mapNotNull { movie ->
            (movie.released ?: movie.releaseInfo)?.take(4)?.toIntOrNull()
        }
        if (parsed.isEmpty()) null else parsed.minOf { it } to parsed.maxOf { it }
    }
    val metaLine = remember(count, years, context) {
        buildList {
            add(context.resources.getQuantityString(R.plurals.movie_collection_movie_count, count, count))
            years?.let { (first, last) -> add(if (first == last) "$first" else "$first - $last") }
        }.joinToString(" • ")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NuvioTheme.spacing.xxl, bottom = NuvioTheme.spacing.xl)
    ) {
        Text(
            text = collection.name,
            style = MaterialTheme.typography.displaySmall,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
        Text(
            text = metaLine,
            style = MaterialTheme.typography.titleSmall,
            color = NuvioTheme.colors.TextSecondary
        )
        collection.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.85f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieCollectionError(message: String, onRetry: () -> Unit) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocusRequester.requestFocusAfterFrames(frames = 2) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xl))
            Button(
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Secondary,
                    contentColor = NuvioTheme.colors.OnSecondary,
                    focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                    focusedContentColor = NuvioTheme.colors.OnSecondaryVariant
                )
            ) {
                Text(stringResource(R.string.cast_detail_retry))
            }
        }
    }
}
