package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.theme.NuvioMotion

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.ui.components.LoadingIndicator
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R

@Composable
fun LoadingOverlay(
    visible: Boolean,
    backdropUrl: String?,
    logoUrl: String?,
    title: String? = null,
    message: String? = null,
    progress: Float? = null,
    subText: String? = null,
    // CinemaOS: RN-style minimal loading (dimmed backdrop, title/logo, small spinner ring, no text lines)
    minimal: Boolean = false,
    // CinemaOS: provider search status shown under the spinner (minimal mode only)
    providerChips: List<com.nuvio.tv.ui.components.SourceChipItem> = emptyList(),
    providerCounts: Map<String, Int> = emptyMap(),
    foundCount: Int = 0,
    startingText: String? = null,
    // CinemaOS glass loader extras (minimal mode): "S01 · E02", year chip and episode title
    seasonEpisodeText: String? = null,
    yearText: String? = null,
    episodeTitle: String? = null,
    modifier: Modifier = Modifier
) {
    var logoLoadFailed by remember(logoUrl) { mutableStateOf(false) }
    val showLogo = !logoUrl.isNullOrBlank() && !logoLoadFailed

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val context = LocalContext.current
        val logoAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, delayMillis = 400, easing = LinearEasing),
            label = "loadingLogoAlpha"
        )
        val infiniteTransition = rememberInfiniteTransition(label = "loadingLogoPulse")
        val logoScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loadingLogoScale"
        )
        val backdropRequest = remember(context, backdropUrl) {
            backdropUrl?.takeIf { it.isNotBlank() }?.let { url ->
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build()
            }
        }
        val logoRequest = remember(context, logoUrl) {
            logoUrl?.takeIf { it.isNotBlank() }?.let { url ->
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build()
            }
        }
        val overlayBrush = remember {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color(0x4D000000),
                    0.35f to Color(0x99000000),
                    0.7f to Color(0xCC000000),
                    1f to Color(0xE6000000)
                )
            )
        }

        if (minimal) {
            GlassLoadingScreen(
                backdropRequest = backdropRequest,
                logoRequest = logoRequest,
                showLogo = showLogo,
                onLogoError = { logoLoadFailed = true },
                title = title,
                seasonEpisodeText = seasonEpisodeText,
                yearText = yearText,
                episodeTitle = episodeTitle,
                chips = providerChips,
                counts = providerCounts,
                foundCount = foundCount,
                startingText = startingText
            )
            return@AnimatedVisibility
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (backdropRequest != null) {
                AsyncImage(
                    model = backdropRequest,
                    contentDescription = stringResource(R.string.cd_loading_backdrop),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayBrush)
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showLogo) {
                        val isLogoFillActive = progress != null
                        val targetFill = (progress ?: 0f).coerceIn(0f, 1f)
                        val animatedFill by animateFloatAsState(
                            targetValue = targetFill,
                            // When completing (target ≈ 1.0), animate faster so the
                            // fill finishes before the overlay's 200ms fade-out;
                            // otherwise use the smoother 400ms tween for buffering.
                            animationSpec = tween(
                                durationMillis = if (targetFill >= 0.999f) 160 else 400,
                                easing = LinearEasing
                            ),
                            label = "loadingLogoFill"
                        )
                        Box(
                            modifier = Modifier
                                .width(320.dp)
                                .height(180.dp)
                        ) {
                            // Base layer: semi-transparent logo (always visible).
                            // When the fill effect is active we hold a steady low
                            // alpha; otherwise fall back to the original fade+pulse.
                            AsyncImage(
                                model = logoRequest,
                                contentDescription = stringResource(R.string.cd_loading_logo),
                                onError = { logoLoadFailed = true },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (isLogoFillActive) 0.25f else logoAlpha
                                        if (!isLogoFillActive) {
                                            scaleX = logoScale
                                            scaleY = logoScale
                                        }
                                    },
                                contentScale = ContentScale.Fit
                            )
                            // Foreground layer: full-alpha logo clipped from left
                            // to right by progress, giving the illusion that the
                            // base logo is filling up as buffering proceeds.
                            if (isLogoFillActive) {
                                AsyncImage(
                                    model = logoRequest,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawWithContent {
                                            val clipWidth = size.width * animatedFill
                                            if (clipWidth > 0f) {
                                                clipRect(right = clipWidth) {
                                                    this@drawWithContent.drawContent()
                                                }
                                            }
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    } else if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier
                                .padding(horizontal = NuvioTheme.spacing.xl)
                                .graphicsLayer {
                                    alpha = logoAlpha
                                    scaleX = logoScale
                                    scaleY = logoScale
                                }
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }

                }

                if (minimal) {
                    RnLoadingSpinner(
                        diameter = 28.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = if (showLogo || !title.isNullOrBlank()) 120.dp else 0.dp)
                    )
                }

                if (minimal && (providerChips.isNotEmpty() || startingText != null)) {
                    ProviderSearchStatus(
                        chips = providerChips,
                        counts = providerCounts,
                        foundCount = foundCount,
                        startingText = startingText,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = if (showLogo || !title.isNullOrBlank()) 190.dp else 70.dp)
                            .padding(horizontal = NuvioTheme.spacing.xl)
                    )
                }

                // The horizontal progress bar is suppressed when the show logo
                // is acting as the fill indicator. The text message stays visible.
                val showHorizontalBar = progress != null && !showLogo
                if (!minimal && (!message.isNullOrBlank() || showHorizontalBar || !subText.isNullOrBlank())) {
                    val messageOffset = if (showLogo || !title.isNullOrBlank()) 120.dp else 86.dp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = messageOffset)
                            .padding(horizontal = NuvioTheme.spacing.xl)
                    ) {
                        Crossfade(
                            targetState = message?.takeIf { it.isNotBlank() },
                            animationSpec = tween(durationMillis = 260),
                            label = "loadingMessageCrossfade",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) { loadingMessage ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loadingMessage != null) {
                                    Text(
                                        text = loadingMessage,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.72f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        if (!subText.isNullOrBlank()) {
                            Text(
                                text = subText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (progress == null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val sweepTransition = rememberInfiniteTransition(label = "loadingSweep")
                            val sweep by sweepTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 1400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "loadingSweepX"
                            )
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(40.dp)
                                        .offset(x = (-40).dp + (160 * sweep).dp)
                                        .background(Color.White.copy(alpha = 0.7f))
                                )
                            }
                        }
                        if (showHorizontalBar) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val animatedProgress by animateFloatAsState(
                                targetValue = progress.coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = NuvioMotion.tokens.durations.overlay),
                                label = "loadingProgress"
                            )
                            Box(
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(NuvioTheme.spacing.xs)
                                    .background(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(NuvioTheme.radii.xxs)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedProgress)
                                        .height(NuvioTheme.spacing.xs)
                                        .background(
                                            color = Color.White.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(NuvioTheme.radii.xxs)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * RN loader spinner: white arc over a faint ring (LoadingScreen.tsx Spinner, 900ms linear;
 * VideoPlayer.tsx:1278 uses the same white ActivityIndicator while the video starts).
 */
@Composable
fun RnLoadingSpinner(diameter: androidx.compose.ui.unit.Dp = 28.dp, modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "rnSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rnSpinAngle"
    )
    androidx.compose.foundation.Canvas(modifier = modifier.size(diameter)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = (diameter.value / 11f).dp.toPx())
        drawArc(color = Color.White.copy(alpha = 0.15f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
        drawArc(color = Color.White, startAngle = angle - 90f, sweepAngle = 90f, useCenter = false, style = stroke)
    }
}

private val GlassBg = Color(0xFF06060C)

/**
 * Loading screen styled like the title's details hero: same full-screen TopEnd-cropped backdrop with
 * the details left/bottom gradients, same logo size (100dp, 40% width) and left inset, and below the
 * logo a frosted-glass card (CinemaOS web LoadingScreen) with status, provider rows and sweep bar.
 */
@Composable
private fun GlassLoadingScreen(
    backdropRequest: ImageRequest?,
    logoRequest: ImageRequest?,
    showLogo: Boolean,
    onLogoError: () -> Unit,
    title: String?,
    seasonEpisodeText: String?,
    yearText: String?,
    episodeTitle: String?,
    chips: List<com.nuvio.tv.ui.components.SourceChipItem>,
    counts: Map<String, Int>,
    foundCount: Int,
    startingText: String?
) {
    val hazeState = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) dev.chrisbanes.haze.HazeState() else null
    }
    val bg = NuvioTheme.colors.Background
    val running = chips.filter { it.status == com.nuvio.tv.ui.components.SourceChipStatus.LOADING }
    var cycle by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(running.size) {
        while (true) {
            kotlinx.coroutines.delay(1200)
            cycle++
        }
    }
    val headline = when {
        startingText != null -> startingText
        running.isNotEmpty() -> "Searching ${running[cycle % running.size].name}\u2026"
        foundCount > 0 -> "Preparing playback"
        else -> "Finding sources"
    }
    val subText = when {
        startingText != null -> "Almost there"
        foundCount > 0 -> "$foundCount source${if (foundCount == 1) "" else "s"} found" +
            if (running.isNotEmpty()) " \u00B7 still searching" else ""
        else -> "This usually takes a few seconds"
    }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
        ) {
            if (backdropRequest != null) {
                AsyncImage(
                    model = backdropRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd
                )
            }
            // Details-screen left fade (same stops, spread over 78% of the width).
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to bg,
                        0.078f to bg.copy(alpha = 0.95f),
                        0.172f to bg.copy(alpha = 0.84f),
                        0.281f to bg.copy(alpha = 0.70f),
                        0.406f to bg.copy(alpha = 0.52f),
                        0.515f to bg.copy(alpha = 0.34f),
                        0.608f to bg.copy(alpha = 0.18f),
                        0.702f to bg.copy(alpha = 0.07f),
                        0.78f to bg.copy(alpha = 0f),
                        1f to bg.copy(alpha = 0f)
                    )
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to bg.copy(alpha = 0f),
                        0.55f to bg.copy(alpha = 0.35f),
                        1f to bg.copy(alpha = 0.92f)
                    )
                )
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = NuvioTheme.spacing.xxxl,
                    end = NuvioTheme.spacing.xxxl,
                    bottom = 40.dp
                )
        ) {
            if (showLogo) {
                AsyncImage(
                    model = logoRequest,
                    contentDescription = title,
                    onError = { onLogoError() },
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth(0.4f)
                        .padding(bottom = NuvioTheme.spacing.lg),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = title?.takeIf { it.isNotBlank() } ?: "Loading\u2026",
                    style = MaterialTheme.typography.displayMedium,
                    color = NuvioTheme.colors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
                )
            }
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassChip(seasonEpisodeText ?: "Film")
                if (!yearText.isNullOrBlank()) GlassChip(yearText)
                if (!episodeTitle.isNullOrBlank()) {
                    Text(
                        text = episodeTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            val cardShape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .clip(cardShape)
                    .then(if (hazeState != null) Modifier.dev_hazeEffect(hazeState) else Modifier)
                    .background(Color.White.copy(alpha = if (hazeState != null) 0.06f else 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), cardShape)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RnLoadingSpinner(diameter = 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Crossfade(targetState = headline, animationSpec = tween(220), label = "glassHeadline") { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Crossfade(targetState = subText, animationSpec = tween(220), label = "glassSub") { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val sweepTransition = rememberInfiniteTransition(label = "glassSweep")
                val sweep by sweepTransition.animateFloat(
                    initialValue = -1f,
                    targetValue = 3f,
                    animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
                    label = "glassSweepX"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.33f)
                            .graphicsLayer { translationX = size.width * sweep }
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    chips.take(6).forEach { chip ->
                        val count = counts[chip.name] ?: 0
                        val (color, status) = when (chip.status) {
                            com.nuvio.tv.ui.components.SourceChipStatus.LOADING -> Color(0xFFE0B341) to "Connecting"
                            com.nuvio.tv.ui.components.SourceChipStatus.SUCCESS ->
                                Color(0xFF4CAF50) to (if (count > 0) "Ready \u00B7 $count" else "None")
                            com.nuvio.tv.ui.components.SourceChipStatus.ERROR -> Color(0xFFE05555) to "Failed"
                        }
                        GlassRow(dot = color, name = chip.name, status = status)
                    }
                    if (chips.size > 6) {
                        Text(
                            text = "+${chips.size - 6} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.dev_hazeEffect(state: dev.chrisbanes.haze.HazeState): Modifier =
    this.hazeEffect(state = state) {
        blurRadius = 28.dp
        noiseFactor = 0.04f
    }

@Composable
private fun GlassChip(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
            letterSpacing = 1.6.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun GlassRow(dot: Color, name: String, status: String?) {
    val animatedDot by androidx.compose.animation.animateColorAsState(
        targetValue = dot,
        animationSpec = tween(300),
        label = "glassRowDot"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(animatedDot))
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (status != null) {
            Crossfade(targetState = status, animationSpec = tween(250), label = "glassRowStatus") { st ->
                Text(
                    text = st,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }
    }
}

/** Centred "Searching X…" line, found-count and a compact row of provider status dots. */
@Composable
private fun ProviderSearchStatus(
    chips: List<com.nuvio.tv.ui.components.SourceChipItem>,
    counts: Map<String, Int>,
    foundCount: Int,
    startingText: String?,
    modifier: Modifier = Modifier
) {
    val running = chips.filter { it.status == com.nuvio.tv.ui.components.SourceChipStatus.LOADING }
    var cycle by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(running.size) {
        while (true) {
            kotlinx.coroutines.delay(1200)
            cycle++
        }
    }
    val headline = when {
        startingText != null -> startingText
        running.isNotEmpty() -> "Searching ${running[cycle % running.size].name}\u2026"
        foundCount > 0 -> "Found $foundCount sources"
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (headline != null) {
            Text(
                text = headline,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (startingText == null && running.isNotEmpty() && foundCount > 0) {
            Text(
                text = "Found $foundCount sources",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (startingText == null && chips.isNotEmpty()) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                chips.take(8).forEach { chip ->
                    val color = when (chip.status) {
                        com.nuvio.tv.ui.components.SourceChipStatus.LOADING -> Color(0xFFE0B341)
                        com.nuvio.tv.ui.components.SourceChipStatus.SUCCESS -> Color(0xFF4CAF50)
                        com.nuvio.tv.ui.components.SourceChipStatus.ERROR -> Color(0xFFE05555)
                    }
                    val count = counts[chip.name] ?: 0
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
                        Text(
                            text = if (chip.status == com.nuvio.tv.ui.components.SourceChipStatus.SUCCESS && count > 0)
                                "${chip.name} ($count)" else chip.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
