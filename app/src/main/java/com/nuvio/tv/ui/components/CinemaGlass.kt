package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon as M3Icon
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioPrimitives
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * CinemaOS glass primitives (TV adaptation of the web "glass" look).
 * No real backdrop blur on TV: translucent fills / zinc950 scrims are used instead.
 */
object CinemaGlass {
    val Fill = Color.White.copy(alpha = 0.10f)
    val FillStrong = Color.White.copy(alpha = 0.15f)
    val FillFocused = Color.White.copy(alpha = 0.25f)
    val Border = Color.White.copy(alpha = 0.15f)
    val BorderSubtle = Color.White.copy(alpha = 0.10f)
    val BorderStrong = Color.White.copy(alpha = 0.20f)
    val FocusRing = Color.White
    val Star = Color(0xFFFACC15)
    /** zinc950 at 88%: stands in for backdrop-blur surfaces on TV. */
    val Scrim = NuvioPrimitives.zinc950.copy(alpha = 0.88f)
    const val FocusScale = 1.05f
}

/** Static glass pill / chip (genre chips, type pills). Not focusable. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    fill: Color = CinemaGlass.Fill,
    borderColor: Color = CinemaGlass.Border,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(BorderStroke(1.dp, borderColor), shape = CircleShape)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier) {
    GlassPill(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** MOVIE / SERIES style pill: primary@15% fill, 10sp semibold uppercase tracked text. */
@Composable
fun TypePill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(NuvioPrimitives.zinc50.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = NuvioPrimitives.zinc50,
            maxLines = 1
        )
    }
}

/** 3dp white vertical bar + uppercase 11sp eyebrow (muted) + bold title. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (eyebrow != null) 40.dp else 26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow.uppercase(),
                    style = NuvioTheme.textStyles.eyebrow,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

/** White pill with black bold text and a play icon (CinemaOS primary CTA). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WhitePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.PlayArrow
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White,
            contentColor = Color.Black,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            pressedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                BorderStroke(2.dp, CinemaGlass.FocusRing), shape = CircleShape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = CinemaGlass.FocusScale)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                M3Icon(imageVector = icon, contentDescription = null, tint = Color.Black, modifier = Modifier.width(18.dp).height(18.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = Color.Black
            )
        }
    }
}

/** Translucent glass pill button: white@15% fill, white@20% border; focus = ring + scale. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CinemaGlass.FillStrong,
            contentColor = Color.White,
            focusedContainerColor = CinemaGlass.FillFocused,
            focusedContentColor = Color.White,
            pressedContainerColor = CinemaGlass.FillFocused,
            pressedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(BorderStroke(1.dp, CinemaGlass.BorderStrong), shape = CircleShape),
            focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, CinemaGlass.FocusRing), shape = CircleShape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = CinemaGlass.FocusScale)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                M3Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.width(18.dp).height(18.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}


/** Non-focusable visual twin of [WhitePillButton], for hero CTAs inside an already-focusable container. */
@Composable
fun WhitePillLabel(text: String, highlighted: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = Icons.Filled.PlayArrow) {
    val ring = if (highlighted) Modifier.border(BorderStroke(2.dp, CinemaGlass.FocusRing), shape = CircleShape) else Modifier
    Row(
        modifier = modifier
            .height(44.dp)
            .then(ring)
            .clip(CircleShape)
            .background(Color.White, CircleShape)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) M3Icon(imageVector = icon, contentDescription = null, tint = Color.Black, modifier = Modifier.width(18.dp).height(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = Color.Black
        )
    }
}

@Composable
fun GlassPillLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(CinemaGlass.FillStrong, CircleShape)
            .border(BorderStroke(1.dp, CinemaGlass.BorderStrong), shape = CircleShape)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

/**
 * Ambient page background: the current backdrop, blurred, ~60% opacity, 1s crossfade.
 * Modifier.blur only works on API 31+; below that only a dark scrim is drawn.
 */
@Composable
fun CinemaAmbientBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    Box(modifier = modifier.background(NuvioPrimitives.zinc950)) {
        if (canBlur && !imageUrl.isNullOrBlank()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.animation.Crossfade(
                targetState = imageUrl,
                animationSpec = androidx.compose.animation.core.tween(1000),
                label = "cinemaAmbient"
            ) { url ->
                val model = androidx.compose.runtime.remember(url) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(url)
                        .size(480, 270)
                        .build()
                }
                coil3.compose.AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(60.dp)
                        .alpha(0.6f)
                )
            }
        }
        Box(Modifier.matchParentSize().background(NuvioPrimitives.zinc950.copy(alpha = 0.35f)))
    }
}
