package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

private val CinemaBg = Color(0xFF06060C)
private val CinemaScrim = Color(0xFF06060C)

/**
 * Port of the CinemaOS React Native LoadingScreen (components/player/LoadingScreen.tsx) without
 * the scraper "sources" panel: blurred + slow-zoom backdrop, gradients, chips, logo/title,
 * spinner with status text and a sweeping progress bar.
 */
@Composable
fun CinemaLoadingScreen(
    backdropUrl: String?,
    logoUrl: String?,
    title: String?,
    statusText: String,
    subText: String,
    modifier: Modifier = Modifier,
    year: String? = null,
    season: Int? = null,
    episode: Int? = null,
    episodeTitle: String? = null,
    progress: Float? = null
) {
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "cinemaLoading")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val zoom by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom"
    )
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    val showLogo = !logoUrl.isNullOrBlank() && !logoFailed
    val backdropRequest = remember(context, backdropUrl) {
        backdropUrl?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context).data(it).crossfade(true).build()
        }
    }
    val logoRequest = remember(context, logoUrl) {
        logoUrl?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context).data(it).crossfade(true).build()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBg)) {
        if (backdropRequest != null) {
            AsyncImage(
                model = backdropRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.5f; scaleX = 1.2f; scaleY = 1.2f }
                    .blur(25.dp),
                contentScale = ContentScale.Crop
            )
            AsyncImage(
                model = backdropRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.6f; scaleX = zoom; scaleY = zoom },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(CinemaScrim.copy(alpha = 0.9f), CinemaScrim.copy(alpha = 0.45f), CinemaScrim.copy(alpha = 0.1f))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(CinemaScrim.copy(alpha = 0.4f), CinemaScrim.copy(alpha = 0.3f), CinemaScrim)
                )
            )
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.6f)
                .padding(start = 48.dp),
            verticalArrangement = Arrangement_Center
        ) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                GlassChip(
                    if (season != null && episode != null) {
                        "S%02d · E%02d".format(season, episode)
                    } else {
                        "FILM"
                    }
                )
                if (!year.isNullOrBlank()) GlassChip(year)
            }
            if (showLogo) {
                AsyncImage(
                    model = logoRequest,
                    contentDescription = null,
                    onError = { logoFailed = true },
                    modifier = Modifier.padding(top = 16.dp).width(300.dp).height(72.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = title?.takeIf { it.isNotBlank() } ?: "Loading…",
                    modifier = Modifier.padding(top = 16.dp),
                    style = TextStyle(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!episodeTitle.isNullOrBlank()) {
                Text(
                    text = episodeTitle,
                    modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val barW = 420.dp
            Column(modifier = Modifier.padding(top = 24.dp).width(barW)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    CinemaSpinner(18.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            statusText,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            subText,
                            style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    if (progress != null) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(Color.White)
                        )
                    } else {
                        val seg = barW / 3
                        val x = barW * (-0.4f) + (barW * 1.5f) * sweep
                        Box(
                            Modifier
                                .padding(start = x.coerceAtLeast(0.dp))
                                .width(seg)
                                .fillMaxHeight()
                                .graphicsLayer { translationX = if (x < 0.dp) x.toPx() else 0f }
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

private val Arrangement_Center = androidx.compose.foundation.layout.Arrangement.Center

@Composable
private fun GlassChip(text: String) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        )
    }
}

@Composable
private fun CinemaSpinner(size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "spinner")
    val angle by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinnerAngle"
    )
    Canvas(Modifier.size(size).rotate(angle)) {
        val sw = 2.dp.toPx()
        val inset = sw / 2
        val s = Size(this.size.width - sw, this.size.height - sw)
        drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), s, style = Stroke(sw))
        drawArc(Color.White, -90f, 90f, false, Offset(inset, inset), s, style = Stroke(sw, cap = StrokeCap.Round))
    }
}
