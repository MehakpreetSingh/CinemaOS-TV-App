package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.ui.theme.NuvioMotion

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.ui.util.LocalRecompositionHighlighterEnabled
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.recompositionHighlighter
import coil3.request.transitionFactory
import com.nuvio.tv.R
import kotlinx.coroutines.delay
import com.nuvio.tv.ui.components.ImdbRatingSourceLabel
import com.nuvio.tv.ui.components.TrailerPlayer
import com.nuvio.tv.ui.components.AutoScrollingText
import androidx.compose.ui.res.stringResource

private data class ModernHeroSecondaryMeta(
    val highlightText: String?,
    val ageRating: String?,
    val status: String?,
    val details: List<String>
)

@Composable
internal fun ModernHeroScene(
    state: () -> ModernHeroSceneState,
    isFullScreen: () -> Boolean,
    bgColor: Color,
    modifier: Modifier,
    requestWidthPx: Int,
    requestHeightPx: Int,
    onTrailerEnded: () -> Unit,
    onFirstFrameRendered: () -> Unit
) {
    val gradientModifier = modifier.cinemaHeroBottomFadeMask()
    val modifier = modifier.cinemaHeroBottomFadeMask(horizontalFeather = { !isFullScreen() })
    ModernHeroMediaLayer(
        heroBackdrop = { state().heroBackdrop },
        enrichmentActive = { state().enrichmentActive },
        shouldPlayHeroTrailer = { state().shouldPlayTrailer },
        heroTrailerFirstFrameRendered = { state().trailerFirstFrameRendered },
        heroTrailerUrl = { state().trailerUrl },
        heroTrailerAudioUrl = { state().trailerAudioUrl },
        heroTrailerPlaybackKey = { state().trailerPlaybackKey },
        muted = { state().trailerMuted },
        onTrailerEnded = onTrailerEnded,
        onFirstFrameRendered = onFirstFrameRendered,
        modifier = modifier,
        requestWidthPx = requestWidthPx,
        requestHeightPx = requestHeightPx
    )
    val isTrailerPlayingFullScreen = {
        val s = state()
        s.fullScreenBackdrop && s.shouldPlayTrailer && s.trailerFirstFrameRendered
    }
    ModernHeroGradientLayer(
        bgColor = bgColor,
        isFullScreen = isFullScreen,
        isTrailerPlayingFullScreen = isTrailerPlayingFullScreen,
        modifier = gradientModifier
    )
}

/** CinemaOS: hero image fades out into the ambient page background (bottom ~35% mask). */
internal fun Modifier.cinemaHeroBottomFadeMask(horizontalFeather: () -> Boolean = { false }): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // Prototype: backdrop feathers in from the left (transparent -> opaque by 40% of its width).
        if (horizontalFeather()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.40f to Color.Black,
                        1.0f to Color.Black
                    )
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black,
                    0.62f to Color.Black,
                    1.0f to Color.Transparent
                )
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
    }

@Composable
internal fun ModernHeroMediaLayer(
    heroBackdrop: () -> String?,
    enrichmentActive: () -> Boolean,
    shouldPlayHeroTrailer: () -> Boolean,
    heroTrailerFirstFrameRendered: () -> Boolean,
    heroTrailerUrl: () -> String?,
    heroTrailerAudioUrl: () -> String?,
    heroTrailerPlaybackKey: () -> String?,
    muted: () -> Boolean,
    onTrailerEnded: () -> Unit,
    onFirstFrameRendered: () -> Unit,
    modifier: Modifier,
    requestWidthPx: Int,
    requestHeightPx: Int
) {
    val shouldPlay by remember { derivedStateOf { shouldPlayHeroTrailer() } }
    val trailerRendered by remember { derivedStateOf { heroTrailerFirstFrameRendered() } }
    val transitionProgressState = animateFloatAsState(
        targetValue = if (shouldPlay && trailerRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "heroBackdropTrailerCrossfadeProgress"
    )
    val localContext = LocalContext.current

    // Backdrop URL is managed upstream (heroSceneStateLambda freezes it
    // during rapid nav / scroll). Only update when enrichment is not active
    val rawBackdrop by remember { derivedStateOf { heroBackdrop() } }
    val enriching by remember { derivedStateOf { enrichmentActive() } }
    var displayedBackdrop by remember { mutableStateOf(HeroBackdropState.lastDisplayedUrl ?: heroBackdrop()) }
    if (rawBackdrop != null && rawBackdrop != displayedBackdrop && !enriching) {
        displayedBackdrop = rawBackdrop!!
    }
    val imageModel = remember(
        localContext,
        displayedBackdrop,
        requestWidthPx,
        requestHeightPx
    ) {
        displayedBackdrop?.let {
            ImageRequest.Builder(localContext)
                .data(it)
                .size(width = requestWidthPx, height = requestHeightPx)
                .build()
        }
    }
    // Keep HeroBackdropState in sync for navigation transitions.
    LaunchedEffect(displayedBackdrop) {
        displayedBackdrop?.let { HeroBackdropState.update(it) }
    }

    Box(modifier = modifier) {
        androidx.compose.animation.Crossfade(
            targetState = imageModel,
            animationSpec = tween(durationMillis = NuvioMotion.tokens.durations.overlay),
            label = "heroBackdropCrossfade"
        ) { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = 1f - transitionProgressState.value
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd
            )
        }
        if (shouldPlay) {
            val trailerUrlVal = heroTrailerUrl()
            val playbackKeyVal = heroTrailerPlaybackKey()
            val audioUrlVal = heroTrailerAudioUrl()
            val mutedVal = muted()
            key(playbackKeyVal ?: trailerUrlVal) {
                TrailerPlayer(
                    trailerUrl = trailerUrlVal,
                    trailerAudioUrl = audioUrlVal,
                    isPlaying = true,
                    onEnded = onTrailerEnded,
                    onFirstFrameRendered = onFirstFrameRendered,
                    muted = mutedVal,
                    cropToFill = true,
                    overscanZoom = MODERN_TRAILER_OVERSCAN_ZOOM,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = transitionProgressState.value
                        }
                )
            }
        }
    }
}

@Composable
internal fun ModernHeroGradientLayer(
    bgColor: Color,
    isFullScreen: () -> Boolean,
    isTrailerPlayingFullScreen: () -> Boolean = { false },
    modifier: Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = if (isTrailerPlayingFullScreen()) 0f else 1f
            }
            .drawWithCache {
                val fullScreen = isFullScreen()
                val horizontalFadeEndX = size.width * if (fullScreen) 0.85f else 0.60f
                // CinemaOS scrim: black 85% -> 40% (mid) -> transparent.
                val colorStops = arrayOf(
                    0.0f to Color.Black.copy(alpha = 0.30f),
                    0.5f to Color.Black.copy(alpha = 0.10f),
                    1.0f to Color.Transparent
                )
                val horizontalGradient = if (isRtl) {
                    Brush.horizontalGradient(
                        colorStops = colorStops,
                        startX = size.width,
                        endX = size.width - horizontalFadeEndX
                    )
                } else {
                    Brush.horizontalGradient(
                        colorStops = colorStops,
                        startX = 0f,
                        endX = horizontalFadeEndX
                    )
                }

                val bottomStripStartY = size.height * if (fullScreen) 0.64f else 0.82f
                val verticalGradient = Brush.verticalGradient(
                    colorStops = if (fullScreen) {
                        arrayOf(
                            0.0f to Color.Transparent,
                            0.30f to bgColor.copy(alpha = 0.35f),
                            0.60f to bgColor.copy(alpha = 0.75f),
                            1.0f to bgColor
                        )
                    } else {
                        arrayOf(
                            0.0f to Color.Transparent,
                            0.40f to bgColor.copy(alpha = 0.25f),
                            0.75f to bgColor.copy(alpha = 0.65f),
                            1.0f to bgColor
                        )
                    },
                    startY = bottomStripStartY,
                    endY = size.height
                )

                onDrawBehind {
                    // 1. Horizontal fade (reversed in RTL)
                    val rectLeft = if (isRtl) size.width - horizontalFadeEndX else 0f
                    drawRect(
                        brush = horizontalGradient,
                        topLeft = Offset(rectLeft, 0f),
                        size = Size(horizontalFadeEndX, size.height)
                    )
                    
                    // 2. Bottom vertical strip
                    drawRect(
                        brush = verticalGradient,
                        topLeft = Offset(0f, bottomStripStartY),
                        size = Size(size.width, size.height - bottomStripStartY)
                    )
                }
            }
    )
}

@Composable
internal fun HeroTitleBlock(
    previewProvider: () -> HeroPreview?,
    enrichmentActive: () -> Boolean = { false },
    portraitMode: Boolean,
    showImdbRatings: Boolean,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    trailerPlaying: () -> Boolean = { false },
    descriptionAutoScrollEnabled: Boolean = false,
    badgeProvider: () -> String? = { null },
    modifier: Modifier = Modifier
) {
    // The providers read hero state that is rewritten on every focus move, often with an equal
    // value. Deriving the pair recomposes this block only when what it shows actually changes.
    val latestPreviewProvider = rememberUpdatedState(previewProvider)
    val latestEnrichmentActive = rememberUpdatedState(enrichmentActive)
    val displayInputs by remember {
        derivedStateOf { latestPreviewProvider.value() to latestEnrichmentActive.value() }
    }
    val currentPreview = displayInputs.first
    val isEnriching = displayInputs.second
    
    var stablePreview by remember { mutableStateOf<HeroPreview?>(null) }

    LaunchedEffect(Unit) {
        snapshotFlow { Pair(previewProvider(), enrichmentActive()) }.collect { (p, e) ->
            if (!e && p != null) {
                if (stablePreview != p) stablePreview = p
            } else if (e) {
                if (stablePreview != null) stablePreview = null
            }
        }
    }

    val displayPreview = if (!isEnriching && currentPreview != null) currentPreview else stablePreview
    if (displayPreview == null) return
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart
    ) {
        androidx.compose.animation.Crossfade(
            targetState = displayPreview,
            animationSpec = tween(durationMillis = 300),
            label = "heroTextCrossfade"
        ) { shownPreview ->
            HeroTitleContent(
                previewProvider = { shownPreview },
                portraitMode = portraitMode,
                showImdbRatings = showImdbRatings,
                mdbListShowOnHero = mdbListShowOnHero,
                mdbListRatingOrder = mdbListRatingOrder,
                trailerPlaying = trailerPlaying,
                descriptionAutoScrollEnabled = descriptionAutoScrollEnabled,
                badgeProvider = badgeProvider
            )
        }
    }
}

@Composable
private fun HeroTitleContent(
    previewProvider: () -> HeroPreview?,
    portraitMode: Boolean,
    showImdbRatings: Boolean,
    mdbListShowOnHero: Boolean = false,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    trailerPlaying: () -> Boolean = { false },
    descriptionAutoScrollEnabled: Boolean = false,
    badgeProvider: () -> String? = { null }
) {
    val preview = previewProvider() ?: return
    val highlighterEnabled = LocalRecompositionHighlighterEnabled.current
    val descriptionMaxLines = 4
    val descriptionScale = if (portraitMode) 0.90f else 1f
    val titleScale = if (portraitMode) 0.92f else 1f
    val metaScale = 1f
    val titleSpacing = NuvioTheme.spacing.sm * titleScale
    val metaSpacing = NuvioTheme.spacing.sm * metaScale
    val imdbMetaSpacing = NuvioTheme.spacing.xs * metaScale
    val context = LocalContext.current
    val density = LocalDensity.current
    val headlineLarge = MaterialTheme.typography.headlineLarge
    val labelMedium = MaterialTheme.typography.labelMedium
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val logoMaxWidthPx = remember(density) { with(density) { 220.dp.roundToPx() } }
    val logoHeightPx = remember(density) { with(density) { 100.dp.roundToPx() } }

    val logoModel = remember(context, preview.logo, logoMaxWidthPx, logoHeightPx) {
        preview.logo?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .size(width = logoMaxWidthPx, height = logoHeightPx)
                .build()
        }
    }
    // Same idea as in HeroTitleBlock: only a flip of the result should recompose this content.
    val latestTrailerPlaying = rememberUpdatedState(trailerPlaying)
    val trailerPlayingValue by remember { derivedStateOf { latestTrailerPlaying.value() } }
    val metaAlpha by animateFloatAsState(
        targetValue = if (trailerPlayingValue) 0f else 1f,
        animationSpec = tween(durationMillis = 480),
        label = "heroMetaFade"
    )
    val scaledTitleStyle = remember(headlineLarge, titleScale) {
        headlineLarge.copy(
            fontSize = headlineLarge.fontSize * titleScale,
            lineHeight = headlineLarge.lineHeight * titleScale
        )
    }
    val scaledDescriptionStyle = remember(bodyMedium, descriptionScale) {
        bodyMedium.copy(
            fontSize = bodyMedium.fontSize * descriptionScale,
            lineHeight = bodyMedium.lineHeight * descriptionScale
        )
    }

    val badge by remember { derivedStateOf { badgeProvider() } }
    val sg = com.nuvio.tv.ui.theme.SpaceGroteskFamily
    val c = com.nuvio.tv.ui.components.cinemaScale()
    fun cdpv(px: Float) = (px * c).dp
    fun cspv(px: Float) = (px * c).sp
    Column(
        modifier = if (highlighterEnabled) Modifier.recompositionHighlighter() else Modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        var logoLoadFailed by remember(preview.logo) { mutableStateOf(false) }
        val showLogo = !preview.logo.isNullOrBlank() && !logoLoadFailed
        // "#n in Top 10 ..." / "Continue S1E2 - 40% watched" pill
        badge?.takeIf { it.isNotBlank() }?.let { text ->
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = metaAlpha }
                    .padding(bottom = cdpv(12f))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
                    .padding(horizontal = cdpv(14f), vertical = cdpv(5f))
            ) {
                Text(
                    text = text,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = sg,
                        fontSize = cspv(16f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
        if (showLogo) {
            AsyncImage(
                model = logoModel,
                contentDescription = preview.title,
                onError = { logoLoadFailed = true },
                modifier = Modifier
                    .height(cdpv(150f))
                    .widthIn(min = cdpv(200f), max = cdpv(560f))
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart
            )
        } else if (preview.title.isNotBlank()) {
            Text(
                text = preview.title,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = sg,
                    fontSize = cspv(84f),
                    lineHeight = cspv(86f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = cspv(-1.7f),
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 4f),
                        blurRadius = 30f
                    ),
                    textDirection = preview.title.contentTextDirection()
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Film . Genre . Genre . 2024 . 2h 46m . star 8.3
        val context2 = LocalContext.current
        val genreText = remember(preview.genres, context2) {
            preview.genres.take(2)
                .filter { it.isNotBlank() }
                .joinToString(" \u2022 ") { com.nuvio.tv.ui.util.localizedGenreLabel(context2, it) }
        }
        val metaParts = remember(preview, genreText) {
            buildList {
                add(if (preview.isSeries) "Series" else "Film")
                if (genreText.isNotBlank()) add(genreText)
                preview.yearText?.takeIf { it.isNotBlank() }?.let(::add)
                preview.runtimeText?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        val rating = if (showImdbRatings) preview.imdbText?.takeIf { it.isNotBlank() } else null
        val metaStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = sg,
            fontSize = cspv(22f),
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .padding(top = cdpv(18f))
                .fillMaxWidth()
                .graphicsLayer { alpha = metaAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cdpv(12f))
        ) {
            metaParts.forEachIndexed { i, part ->
                if (i > 0) {
                    Box(
                        Modifier
                            .size(cdpv(4f))
                            .clip(CircleShape)
                            .background(Color(0xFFA1A1AA))
                    )
                }
                Text(text = part, style = metaStyle, color = Color(0xFFF4F4F5), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (rating != null) {
                if (metaParts.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(cdpv(4f))
                            .clip(CircleShape)
                            .background(Color(0xFFA1A1AA))
                    )
                }
                Text(text = "\u2605", style = metaStyle, color = Color(0xFFFACC15), maxLines = 1)
                Text(text = rating, style = metaStyle, color = Color(0xFFF4F4F5), maxLines = 1)
            }
        }

        preview.description?.takeIf { it.isNotBlank() }?.let { description ->
            AutoScrollingText(
                text = description,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = sg,
                    fontSize = cspv(23f),
                    lineHeight = cspv(34.5f),
                    fontWeight = FontWeight.Normal,
                    textDirection = description.contentTextDirection()
                ),
                color = Color(0xFFE4E4E7),
                maxLines = 3,
                autoScrollEnabled = descriptionAutoScrollEnabled,
                paused = trailerPlaying,
                modifier = Modifier
                    .padding(top = cdpv(14f))
                    .widthIn(max = cdpv(780f))
                    .graphicsLayer { alpha = metaAlpha }
            )
        }
    }
}

@Composable
private fun HeroImdbMeta(
    imdbText: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    textColor: Color,
    logoSize: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    visible: Boolean = true
) {
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        ImdbRatingSourceLabel(
            logoModifier = Modifier.size(logoSize),
            textStyle = textStyle,
            textColor = textColor
        )
        Text(
            text = imdbText,
            style = textStyle,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroCombinedMetaBadge(
    leftText: String,
    rightText: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    contentColor: Color
) {
    val dividerColor = contentColor.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                border = BorderStroke(NuvioTheme.spacing.hairline, dividerColor),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = NuvioTheme.spacing.sm, vertical = NuvioTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        val semiBoldStyle = remember(textStyle) { textStyle.copy(fontWeight = FontWeight.SemiBold) }
        Text(
            text = leftText,
            style = semiBoldStyle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .width(NuvioTheme.spacing.hairline)
                .height(NuvioTheme.spacing.md)
                .background(dividerColor)
        )
        Text(
            text = rightText,
            style = semiBoldStyle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroMetaBadge(
    text: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                border = BorderStroke(NuvioTheme.spacing.hairline, contentColor.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = NuvioTheme.spacing.sm, vertical = NuvioTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = remember(textStyle) { textStyle.copy(fontWeight = FontWeight.SemiBold) },
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroMetaDivider(
    scale: Float,
    visible: Boolean = true
) {
    Box(
        modifier = Modifier
            .size((NuvioTheme.spacing.xs * scale).coerceAtLeast(NuvioTheme.spacing.xxs))
            .clip(RoundedCornerShape(percent = 50))
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .background(NuvioTheme.colors.TextTertiary.copy(alpha = 0.78f))
    )
}
