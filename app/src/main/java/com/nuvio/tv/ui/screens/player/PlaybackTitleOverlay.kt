@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Keyed by content, never by stream URL, so retrying or switching sources keeps the intro consumed. */
@Composable
internal fun rememberPlaybackTitleIntro(contentKey: String, ready: Boolean): Pair<Boolean, Boolean> {
    var complete by rememberSaveable(contentKey) { mutableStateOf(false) }
    var visible by remember(contentKey) { mutableStateOf(false) }
    LaunchedEffect(contentKey, ready) {
        if (ready && !complete) {
            visible = true
            delay(5_000)
            visible = false
            delay(300) // finish the title fade before starting the warnings
            complete = true
        } else visible = false
    }
    return visible to complete
}

@Composable
internal fun PlaybackTitleOverlay(title: String, episode: String?, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(300)), modifier = modifier) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("NOW PLAYING", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (!episode.isNullOrBlank()) Text(episode, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 2)
        }
    }
}
