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
import androidx.compose.ui.draw.drawWithContent
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
    year: String? = null,
    season: Int? = null,
    episode: Int? = null,
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
        CinemaLoadingScreen(
            backdropUrl = backdropUrl,
            logoUrl = logoUrl,
            title = title,
            statusText = message?.takeIf { it.isNotBlank() } ?: "Starting playback",
            subText = if (progress != null) "Buffering" else "This usually takes a few seconds",
            year = year,
            season = season,
            episode = episode,
            episodeTitle = episodeTitle,
            progress = progress
        )
    }
}
