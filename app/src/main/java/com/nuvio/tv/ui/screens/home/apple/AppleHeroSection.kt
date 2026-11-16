package com.nuvio.tv.ui.screens.home.apple

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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

@Composable
internal fun AppleHeroSection(
    items: List<MetaPreview>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    height: Dp,
    inLibrary: Boolean,
    canToggleLibrary: Boolean,
    backgroundColor: Color,
    onPlay: (MetaPreview) -> Unit,
    onInfo: (MetaPreview) -> Unit,
    onToggleLibrary: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val item = items.getOrNull(safeIndex) ?: return
    val count = items.size
    fun goPrev() = onIndexChange(if (safeIndex == 0) count - 1 else safeIndex - 1)
    fun goNext() = onIndexChange(if (safeIndex == count - 1) 0 else safeIndex + 1)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        // Backdrop crossfade
        Crossfade(
            targetState = item.backdropUrl,
            animationSpec = tween(400),
            label = "heroBackdrop",
            modifier = Modifier.fillMaxSize()
        ) { url ->
            val context = LocalContext.current
            val cfg = LocalConfiguration.current
            val density = LocalDensity.current
            val widthPx = with(density) { cfg.screenWidthDp.dp.roundToPx() }
            val heightPx = (widthPx * 9) / 16
            val request = remember(url, widthPx) {
                ImageRequest.Builder(context)
                    .data(url)
                    .size(width = widthPx, height = heightPx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Left-bottom scrim + bottom fade into page background
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.85f),
                    0.5f to Color.Black.copy(alpha = 0.4f),
                    1f to Color.Transparent
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to backgroundColor
                )
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 52.dp)
                .fillMaxWidth(0.5f)
        ) {
            Crossfade(
                targetState = item,
                animationSpec = tween(400),
                label = "heroText"
            ) { current ->
                HeroTextBlock(current)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppleFocusSurface(
                    onClick = { onPlay(item) },
                    shape = CircleShape,
                    containerColor = HeroButtonBg,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color(0xFF09090B),
                    modifier = Modifier
                        .width(112.dp)
                        .height(40.dp)
                        .focusRequester(com.nuvio.tv.LocalContentFocusRequester.current)
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && count > 1) {
                                goPrev(); true
                            } else false
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (canToggleLibrary) {
                    HeroCircleButton(
                        icon = if (inLibrary) Icons.Filled.Check else Icons.Filled.Add,
                        onClick = { onToggleLibrary(item) }
                    )
                }
                HeroCircleButton(icon = Icons.Filled.Info, onClick = { onInfo(item) })
                if (count > 1) {
                    HeroCircleButton(
                        icon = Icons.Filled.ChevronRight,
                        onClick = { goNext() },
                        modifier = Modifier.onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                                goNext(); true
                            } else false
                        }
                    )
                }
            }
        }

        // Page indicator dots
        if (count > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until minOf(count, 10)) {
                    val w by animateDpAsState(if (i == safeIndex) 20.dp else 6.dp, tween(200), label = "dot")
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == safeIndex) 0.95f else 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppleFocusSurface(
        onClick = onClick,
        shape = CircleShape,
        containerColor = HeroButtonBg,
        focusedContainerColor = Color.White,
        focusedContentColor = Color(0xFF09090B),
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp).align(Alignment.Center)
        )
    }
}

@Composable
private fun HeroTextBlock(item: MetaPreview) {
    val context = LocalContext.current
    var logoFailed by remember(item.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.height(64.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (!item.logo.isNullOrBlank() && !logoFailed) {
                val request = remember(item.logo) {
                    ImageRequest.Builder(context)
                        .data(item.logo)
                        .size(width = 640, height = 256)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.7f)
                )
            } else {
                Text(
                    text = item.name,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Meta line
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val type = if (item.apiType.equals("movie", true)) "Movie" else "TV Show"
            val parts = buildList {
                add(type)
                addAll(item.genres.take(2))
                item.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(it.take(4)) }
            }
            Text(
                text = parts.joinToString(" · "),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val rating = item.imdbRating
            if (rating != null && rating > 0f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", rating),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        val synopsis = item.description
        if (!synopsis.isNullOrBlank()) {
            Text(
                text = synopsis,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
