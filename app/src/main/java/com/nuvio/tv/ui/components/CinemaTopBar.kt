package com.nuvio.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.tv.material3.Icon as M3Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.util.rememberRawSvgPainter

data class CinemaTopBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
)

private val NavSpec = tween<Float>(320, easing = FastOutSlowInEasing)

/**
 * CinemaOS TV top bar, ported from Nuvio Desktop's TabletFloatingTopBar: a floating frosted pill
 * (translucent fill + sheen + subtle border) centred at the top. Collapsed (icons only, active item
 * in a lighter circle) while focus is in page content; expands with labels while the bar has focus.
 * True backdrop blur is skipped (needs API 31+ and a haze source); the fill is translucent instead.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CinemaTopBar(
    items: List<CinemaTopBarItem>,
    selectedRoute: String?,
    focusRequesters: Map<String, FocusRequester>,
    onItemClick: (String) -> Unit,
    onBarFocusChanged: (Boolean) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    profileName: String? = null,
    onProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var barFocused by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(java.util.Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.util.Date()
            kotlinx.coroutines.delay(20_000)
        }
    }
    val clock = remember(now) {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(now)
    }

    val surfaceColor by animateColorAsState(
        targetValue = if (barFocused) Color(0xFF1C1C1E).copy(alpha = 0.62f) else Color(0xFF0F0F11).copy(alpha = 0.42f),
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "topBarSurface"
    )
    val sheenAlpha by animateFloatAsState(if (barFocused) 1f else 0f, NavSpec, label = "topBarSheen")
    val itemSpacing by animateDpAsState(if (barFocused) 8.dp else 6.dp, tween(320), label = "topBarSpacing")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 48.dp)
            .padding(top = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clip(CircleShape)
                .background(surfaceColor)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f * sheenAlpha),
                            Color.White.copy(alpha = 0.03f * sheenAlpha),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .selectableGroup()
                    .focusProperties {
                        // Coming back up from content lands on the current page's nav item.
                        enter = { selectedRoute?.let { focusRequesters[it] } ?: FocusRequester.Default }
                    }
                    .focusGroup()
                    .onFocusChanged {
                        barFocused = it.hasFocus
                        onBarFocusChanged(it.hasFocus)
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                            val ok = contentFocusRequester != null &&
                                runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                            if (!ok) focusManager.moveFocus(FocusDirection.Down)
                            true
                        } else false
                    },
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val requester = focusRequesters[item.route]
                    CinemaTopBarPill(
                        item = item,
                        selected = item.route == selectedRoute,
                        expanded = barFocused,
                        onClick = { onItemClick(item.route) },
                        modifier = if (requester != null) Modifier.focusRequester(requester) else Modifier
                    )
                }
                if (!profileName.isNullOrBlank() && onProfileClick != null) {
                    CinemaTopBarPill(
                        item = CinemaTopBarItem("__profile", profileName),
                        selected = false,
                        expanded = barFocused,
                        onClick = onProfileClick
                    )
                }
            }
        }
        Text(
            text = clock,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp),
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f
                )
            )
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CinemaTopBarPill(
    item: CinemaTopBarItem,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val hPad: Dp by animateDpAsState(if (expanded) 14.dp else 11.dp, tween(320), label = "navPad")
    val tint = if (selected || focused) Color.White else Color.White.copy(alpha = 0.70f)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = item.label
                role = Role.Tab
                this.selected = selected
            },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = tint,
            focusedContainerColor = Color.White.copy(alpha = 0.30f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.30f),
            pressedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                0.dp,
                CircleShape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1.04f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = hPad).height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                item.icon != null -> M3Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                item.iconRes != null -> M3Icon(
                    painter = rememberRawSvgPainter(item.iconRes, 19.dp),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) + fadeIn(tween(250)),
                exit = shrinkHorizontally(tween(320, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) + fadeOut(tween(200))
            ) {
                Text(
                    text = item.label,
                    modifier = Modifier.padding(start = 8.dp),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
