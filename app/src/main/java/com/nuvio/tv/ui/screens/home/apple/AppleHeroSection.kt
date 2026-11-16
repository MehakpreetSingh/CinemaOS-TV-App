package com.nuvio.tv.ui.screens.home.apple

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.domain.model.MetaPreview

private val HeroButtonBg = Color.White.copy(alpha = 0.2f)

/** What the hero area shows: the carousel item, or whatever card is focused in the rows. */
internal data class HeroDisplay(
    val key: String,
    val backdrops: List<String>,
    val name: String,
    val logo: String?,
    val meta: String,
    val rating: Float?,
    val synopsis: String?,
    val ageRating: String? = null,
    val status: String? = null,
    val language: String? = null
)

internal fun MetaPreview.toHeroDisplay(): HeroDisplay {
    val type = if (apiType.equals("movie", true)) "Movie" else "TV Show"
    val parts = buildList {
        add(type)
        addAll(genres.take(1))
        runtime?.takeIf { it.isNotBlank() }?.let { add(it) }
        releaseInfo?.takeIf { it.isNotBlank() }?.let { add(it.take(4)) }
    }
    return HeroDisplay(
        key = "$id|$apiType",
        backdrops = listOfNotNull(background, landscapePoster, poster).filter { it.isNotBlank() }.distinct(),
        name = name,
        logo = logo,
        meta = parts.joinToString(" \u2022 "),
        rating = imdbRating,
        synopsis = description,
        ageRating = ageRating,
        status = status?.takeIf { it.isNotBlank() },
        language = language
    )
}

@Composable
internal fun AppleHeroSection(
    display: HeroDisplay,
    count: Int,
    index: Int,
    onIndexChange: (Int) -> Unit,
    height: Dp,
    inLibrary: Boolean,
    canToggleLibrary: Boolean,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onToggleLibrary: () -> Unit,
    onHeroFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    fun goPrev() = onIndexChange(if (safeIndex == 0) count - 1 else safeIndex - 1)
    fun goNext() = onIndexChange(if (safeIndex == count - 1) 0 else safeIndex + 1)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = com.nuvio.tv.LocalRootTopInset.current + 14.dp)
                .widthIn(max = 560.dp)
        ) {
            Crossfade(
                targetState = display,
                animationSpec = tween(300),
                label = "heroText"
            ) { current ->
                HeroTextBlock(current)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Desktop home has a single white "View Details" pill; Left/Right cycle the carousel.
                AppleFocusSurface(
                    onClick = onInfo,
                    onFocused = onHeroFocused,
                    shape = CircleShape,
                    containerColor = Color.White,
                    contentColor = Color(0xFF09090B),
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color(0xFF09090B),
                    focusedScale = 1.06f,
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = 128.dp)
                        .focusRequester(com.nuvio.tv.LocalContentFocusRequester.current)
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && count > 1) {
                                when (e.key) {
                                    Key.DirectionLeft -> { goPrev(); true }
                                    Key.DirectionRight -> { goNext(); true }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    Text(
                        "View Details",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 22.dp)
                    )
                }
            }
        }
        if (count > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until count) {
                    val active = i == safeIndex
                    val w by animateDpAsState(if (active) 22.dp else 6.dp, tween(250), label = "dotW")
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (active) 1f else 0.7f))
                    )
                }
            }
        }
    }
}

/** Full-screen backdrop; falls back to the next candidate image when one fails to load. */
@Composable
internal fun AppleBackdrop(display: HeroDisplay?, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = display,
        animationSpec = tween(300),
        label = "appleBackdrop",
        modifier = modifier
    ) { d ->
        if (d == null) return@Crossfade
        var idx by remember(d.key) { mutableStateOf(0) }
        val url = d.backdrops.getOrNull(idx)
        if (url != null) {
            val context = LocalContext.current
            val cfg = LocalConfiguration.current
            val density = LocalDensity.current
            val widthPx = with(density) { cfg.screenWidthDp.dp.roundToPx() }
            val heightPx = with(density) { cfg.screenHeightDp.dp.roundToPx() }
            val request = remember(url, widthPx) {
                ImageRequest.Builder(context).data(url).size(width = widthPx, height = heightPx).crossfade(false).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                onError = { if (idx < d.backdrops.lastIndex) idx++ },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun HeroTextBlock(item: HeroDisplay) {
    val context = LocalContext.current
    var logoFailed by remember(item.key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.height(96.dp).widthIn(max = 340.dp), contentAlignment = Alignment.CenterStart) {
            if (!item.logo.isNullOrBlank() && !logoFailed) {
                val request = remember(item.logo) {
                    ImageRequest.Builder(context)
                        .data(item.logo)
                        .size(width = 600, height = 180)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = item.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = item.meta,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val rating = item.rating?.takeIf { it > 0f }
        val tag = listOfNotNull(item.ageRating?.takeIf { it.isNotBlank() }, item.status?.takeIf { it.isNotBlank() }?.uppercase())
        if (tag.isNotEmpty() || rating != null || !item.language.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tag.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tag.forEachIndexed { i, t ->
                            if (i > 0) Box(Modifier.width(1.dp).height(10.dp).background(Color.White.copy(alpha = 0.6f)))
                            Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        }
                    }
                }
                if (rating != null) {
                    Text("\u2022", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFF5C518))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("IMDb", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    }
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", rating),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF5C518)
                    )
                }
                if (!item.language.isNullOrBlank()) {
                    Text("\u2022", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    Text(item.language.take(3).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        val synopsis = item.synopsis
        if (!synopsis.isNullOrBlank()) {
            Text(
                text = synopsis,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
