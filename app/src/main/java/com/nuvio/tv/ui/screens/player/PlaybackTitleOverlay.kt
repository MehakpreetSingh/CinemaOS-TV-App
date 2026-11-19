@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.tv.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Keyed by content, never by stream URL, so retrying or switching sources keeps the intro consumed. */
@Composable
internal fun rememberPlaybackTitleIntro(contentKey: String, ready: Boolean): Pair<Boolean, Boolean> {
    var complete by rememberSaveable(contentKey) { mutableStateOf(false) }
    var visible by remember(contentKey) { mutableStateOf(false) }
    LaunchedEffect(contentKey, ready) {
        if (ready && !complete) {
            visible = true
            delay(6_000)
            visible = false
            delay(400) // finish the title slide-out before starting the warnings
            complete = true
        } else visible = false
    }
    return visible to complete
}

/** Year, up to two genres and runtime, joined like the React Native card ("2024  •  Action, Drama  •  2h 5m"). */
internal fun playbackTitleMetaLine(year: String?, genres: List<String>, runtime: String?): String {
    val minutes = runtime?.trim()?.removeSuffix("min")?.trim()?.toIntOrNull()
    val runtimeText = if (minutes != null) {
        (if (minutes / 60 > 0) "${minutes / 60}h " else "") + "${minutes % 60}m"
    } else runtime?.takeIf { it.isNotBlank() }
    return listOfNotNull(
        year?.take(4)?.takeIf { it.isNotBlank() },
        genres.take(2).joinToString(", ").takeIf { it.isNotBlank() },
        runtimeText,
    ).joinToString("  •  ")
}

/** CinemaOS React Native "Now playing" card: slides in top-left, no backdrop, then slides out. */
@Composable
internal fun PlaybackTitleOverlay(
    title: String,
    episode: String?,
    visible: Boolean,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    metaLine: String = "",
    rating: Float? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(450)) + slideInHorizontally(tween(450)) { -it / 4 },
        exit = fadeOut(tween(400)) + slideOutHorizontally(tween(400)) { -it / 4 },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.46f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("NOW PLAYING", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 2.4.sp)
            var logoFailed by remember(logoUrl) { mutableStateOf(false) }
            if (!logoUrl.isNullOrBlank() && !logoFailed) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.size(width = 260.dp, height = 80.dp),
                )
            } else {
                Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            if (!episode.isNullOrBlank()) Text(episode, color = Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ratingValue = rating?.toDouble()?.takeIf { it > 0 }
            if (metaLine.isNotBlank() || ratingValue != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (metaLine.isNotBlank()) Text(metaLine, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    if (ratingValue != null) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(12.dp))
                        Text(((ratingValue * 10).roundToInt() / 10.0).toString(), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
