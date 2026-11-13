package com.nuvio.tv.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

import com.nuvio.tv.ui.theme.NuvioTheme

import android.graphics.ColorSpace
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.util.StableList
import com.nuvio.tv.ui.util.recompositionHighlighter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.colorSpace
import coil3.request.crossfade
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.formatHeroRuntime
import com.nuvio.tv.ui.util.LocalRecompositionHighlighterEnabled
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

private const val AUTO_ADVANCE_INTERVAL_MS = 7000L
private val HERO_HEIGHT = 440.dp
private val YEAR_REGEX = Regex("""\b\d{4}\b""")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroCarousel(
    items: StableList<MetaPreview>,
    onItemClick: (MetaPreview) -> Unit,
    onItemFocus: (MetaPreview) -> Unit = {},
    onActiveItemChanged: (MetaPreview) -> Unit = {},
    focusRequester: FocusRequester? = null,
    showImdbRatings: Boolean = true,
    showBackdrop: Boolean = true,
    fullWidth: Dp = Dp.Unspecified,
    initialActiveIndex: Int = 0,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    descriptionAutoScrollEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentOnItemFocus by rememberUpdatedState(onItemFocus)
    val currentOnActiveItemChanged by rememberUpdatedState(onActiveItemChanged)
    var activeIndex by remember { mutableIntStateOf(initialActiveIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var isFocused by remember { mutableStateOf(false) }
    // Slide whose description is still on its first scroll pass, or -1.
    var descriptionScrollHoldIndex by remember { mutableIntStateOf(-1) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(activeIndex, isFocused) {
        if (!isFocused) return@LaunchedEffect
        items.getOrNull(activeIndex)?.let { currentOnItemFocus(it) }
    }

    LaunchedEffect(activeIndex, items) {
        items.getOrNull(activeIndex)?.let { currentOnActiveItemChanged(it) }
    }

    // Auto-advance every ~7s; the active segment fills left to right while it waits.
    val slideProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(activeIndex, items.size) {
        slideProgress.snapTo(0f)
        if (items.size <= 1) return@LaunchedEffect
        slideProgress.animateTo(
            1f,
            androidx.compose.animation.core.tween(
                durationMillis = AUTO_ADVANCE_INTERVAL_MS.toInt(),
                easing = androidx.compose.animation.core.LinearEasing
            )
        )
        // Let a long description finish scrolling before the slide is replaced.
        snapshotFlow { descriptionScrollHoldIndex == activeIndex }.first { !it }
        activeIndex = (activeIndex + 1) % items.size
    }

    Box(
        modifier = modifier
            .then(
                if (fullWidth != Dp.Unspecified)
                    Modifier.requiredWidth(fullWidth)
                else
                    Modifier.fillMaxWidth()
            )
            .height(HERO_HEIGHT)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.hasFocus || it.isFocused
            }
            .semantics { this.contentDescription = items.getOrNull(activeIndex)?.name.orEmpty() }
            .focusable()
            .semantics {
                role = Role.Button
                contentDescription = items[activeIndex].name
                onClick {
                    currentOnItemClick(items[activeIndex])
                    true
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (isRtl) {
                                if (activeIndex < items.size - 1) { activeIndex++; true } else false
                            } else {
                                if (activeIndex > 0) { activeIndex--; true } else false
                            }
                        }
                        Key.DirectionRight -> {
                            if (isRtl) {
                                if (activeIndex > 0) { activeIndex--; true } else false
                            } else {
                                if (activeIndex < items.size - 1) { activeIndex++; true } else false
                            }
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    currentOnItemClick(items[activeIndex])
                    true
                } else {
                    false
                }
            }
    ) {
        // Crossfade between slides
        Crossfade(
            targetState = activeIndex,
            animationSpec = tween(300),
            label = "heroSlide"
        ) { index ->
            val item = items.getOrNull(index) ?: return@Crossfade
            HeroCarouselSlide(
                item = item,
                showImdbRatings = showImdbRatings,
                showBackdrop = showBackdrop,
                mdbListShowOnHero = mdbListShowOnHero,
                mdbListRatingOrder = mdbListRatingOrder,
                descriptionAutoScrollEnabled = descriptionAutoScrollEnabled,
                heroFocused = isFocused,
                onDescriptionScrollHoldChanged = { hold ->
                    // A slide can only clear its own hold, so the outgoing slide in the
                    // crossfade can't release the new one, and a slide without a
                    // description never blocks the advance.
                    when {
                        hold -> descriptionScrollHoldIndex = index
                        descriptionScrollHoldIndex == index -> descriptionScrollHoldIndex = -1
                    }
                }
            )
        }

        // Segmented progress bars (CinemaOS): track white@20%, active fill animates over the interval.
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = NuvioTheme.spacing.xxxl, bottom = NuvioTheme.spacing.lg)
                    .fillMaxWidth(0.34f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(items.size) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val frac = when {
                                        index < activeIndex -> 1f
                                        index == activeIndex -> slideProgress.value
                                        else -> 0f
                                    }
                                    scaleX = frac
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                }
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroCarouselSlide(
    item: MetaPreview,
    showImdbRatings: Boolean,
    showBackdrop: Boolean,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    descriptionAutoScrollEnabled: Boolean = false,
    heroFocused: Boolean = false,
    onDescriptionScrollHoldChanged: (Boolean) -> Unit = {}
) {
    val highlighterEnabled = LocalRecompositionHighlighterEnabled.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val logoRequestWidthPx = remember(density) {
        with(density) { 220.dp.roundToPx() }.coerceAtLeast(1)
    }
    val logoRequestHeightPx = remember(density) { with(density) { 100.dp.roundToPx() }.coerceAtLeast(1) }

    val logoModel = remember(context, item.logo, logoRequestWidthPx, logoRequestHeightPx) {
        item.logo?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .size(width = logoRequestWidthPx, height = logoRequestHeightPx)
                .build()
        }
    }
    var logoLoadFailed by remember(item.logo) { mutableStateOf(false) }
    val showLogo = !item.logo.isNullOrBlank() && !logoLoadFailed
    val contentTypeText = remember(context, item.apiType) {
        localizedContentType(context, item.apiType).takeIf { it.isNotBlank() }
    }
    val primaryGenreText = remember(context, item.genres) {
        item.genres.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { localizedGenreLabel(context, it) }
    }
    val runtimeText = remember(item.runtime) { formatHeroRuntime(item.runtime) }
    val releaseYear = remember(item.releaseInfo) {
        item.releaseInfo?.let { releaseInfo ->
            YEAR_REGEX.find(releaseInfo)?.value ?: releaseInfo.split("-").firstOrNull()
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
    val leadingMetaText = remember(contentTypeText, primaryGenreText) {
        listOfNotNull(contentTypeText, primaryGenreText).joinToString(separator = " • ")
    }
    val trailingMetadata = remember(runtimeText, releaseYear) {
        listOfNotNull(runtimeText, releaseYear)
    }
    val ratingText = remember(item.imdbRating, showImdbRatings) {
        item.imdbRating
            ?.takeIf { showImdbRatings }
            ?.let { String.format("%.1f", it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (highlighterEnabled) Modifier.recompositionHighlighter() else Modifier)
    ) {
        if (showBackdrop) {
            HeroCarouselBackdrop(
                item = item,
                fullPage = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = NuvioTheme.spacing.xxxl, bottom = 44.dp, end = NuvioTheme.spacing.xxxl)
                .fillMaxWidth(0.48f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Eyebrow: dot + TYPE . GENRE . YEAR
            val eyebrowText = remember(contentTypeText, primaryGenreText, releaseYear) {
                listOfNotNull(contentTypeText, primaryGenreText, releaseYear).joinToString("  \u2022  ")
            }
            if (eyebrowText.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Text(
                        text = eyebrowText.uppercase(),
                        style = NuvioTheme.textStyles.eyebrow,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showLogo) {
                AsyncImage(
                    model = logoModel,
                    contentDescription = item.name,
                    onError = { logoLoadFailed = true },
                    modifier = Modifier
                        .height(100.dp)
                        .widthIn(min = 100.dp, max = 320.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Meta: yellow star + rating . runtime . genre glass chips
            val genreChips = remember(context, item.genres) {
                item.genres.filter { it.isNotBlank() }.take(3).map { localizedGenreLabel(context, it) }
            }
            if (ratingText != null || runtimeText != null || genreChips.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ratingText != null && !(mdbListShowOnHero && item.mdbListRatings != null && !item.mdbListRatings.isEmpty())) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("\u2605", style = MaterialTheme.typography.labelMedium, color = CinemaGlass.Star)
                            Text(
                                text = ratingText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    if (runtimeText != null) {
                        Text(
                            text = runtimeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                    genreChips.forEach { GlassChip(text = it) }
                }
            }

            if (mdbListShowOnHero && item.mdbListRatings != null && !item.mdbListRatings.isEmpty()) {
                MDBListRatingsRow(
                    ratings = item.mdbListRatings,
                    order = mdbListRatingOrder,
                    maxItems = 6
                )
            }

            item.description?.takeIf { it.isNotBlank() }?.let { description ->
                AutoScrollingText(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = description.contentTextDirection()
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 3,
                    autoScrollEnabled = descriptionAutoScrollEnabled,
                    onScrollHoldChanged = onDescriptionScrollHoldChanged
                )
            }

        }
    }
}

@Composable
private fun HeroCarouselMetaDivider() {
    Box(
        modifier = Modifier
            .size(NuvioTheme.spacing.xs.coerceAtLeast(NuvioTheme.spacing.xxs))
            .clip(RoundedCornerShape(percent = 50))
            .background(NuvioTheme.colors.TextTertiary.copy(alpha = 0.78f))
    )
}

@Composable
internal fun HeroCarouselBackdrop(
    item: MetaPreview,
    fullPage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val requestWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    }
    val requestHeightPx = remember(configuration.screenHeightDp, density, fullPage) {
        val height = if (fullPage) configuration.screenHeightDp.dp else HERO_HEIGHT
        with(density) { height.roundToPx() }.coerceAtLeast(1)
    }
    val backdropUrl = item.backdropUrl
    val backgroundModel = remember(context, backdropUrl, requestWidthPx, requestHeightPx, fullPage) {
        ImageRequest.Builder(context)
            .data(backdropUrl)
            .crossfade(false)
            .size(width = requestWidthPx, height = requestHeightPx)
            .apply {
                if (fullPage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                }
            }
            .build()
    }
    val bgColor = NuvioTheme.colors.Background

    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
            val bottomStartFraction = if (fullPage) 0.55f else 0.30f
            val leftEndFraction = if (fullPage) 0.85f else 0.90f
            val bottomStartY = size.height * bottomStartFraction
            val leftEndX = size.width * leftEndFraction
            val bottomGradient = Brush.verticalGradient(
                colorStops = if (fullPage) {
                    arrayOf(
                        0.0f to Color.Transparent,
                        0.4222f to bgColor.copy(alpha = 0.32f),
                        0.7778f to bgColor.copy(alpha = 0.62f),
                        1.0f to bgColor.copy(alpha = 0.78f)
                    )
                } else {
                    arrayOf(
                        0.0f to Color.Transparent,
                        0.4286f to bgColor.copy(alpha = 0.5f),
                        0.7143f to bgColor.copy(alpha = 0.85f),
                        1.0f to bgColor
                    )
                },
                startY = bottomStartY,
                endY = size.height
            )
            // CinemaOS hero scrim: left->right black 85% / 40% / 0.
            val leftGradient = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black.copy(alpha = 0.85f),
                    0.5f to Color.Black.copy(alpha = 0.40f),
                    1.0f to Color.Transparent
                ),
                startX = 0f,
                endX = leftEndX
            )
            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = bottomGradient,
                    topLeft = Offset(0f, bottomStartY),
                    size = Size(size.width, size.height - bottomStartY)
                )
                drawRect(
                    brush = leftGradient,
                    size = Size(leftEndX, size.height)
                )
            }
        }
    ) {
        AsyncImage(
            model = backgroundModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )
    }
}
