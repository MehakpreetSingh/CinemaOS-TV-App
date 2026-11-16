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
import androidx.compose.foundation.layout.Box
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
