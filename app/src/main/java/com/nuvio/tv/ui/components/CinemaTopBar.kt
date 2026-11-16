package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.nuvio.tv.R
import androidx.tv.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

data class CinemaTopBarItem(val route: String, val label: String)

/**
 * CinemaOS TV top bar (prototype v3): small white clapperboard mark, text nav items (active = white
 * pill with black text, focused-inactive = white@15% pill), frosted gradient strip fading downward,
 * clock on the right.
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .onFocusChanged { onBarFocusChanged(it.hasFocus) }
            .padding(horizontal = 48.dp)
            .padding(top = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .padding(4.dp)
                .selectableGroup()
                .focusProperties {
                    // Coming back up from content lands on the current page's nav item.
                    enter = { selectedRoute?.let { focusRequesters[it] } ?: FocusRequester.Default }
                }
                .focusGroup()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                        val ok = contentFocusRequester != null &&
                            runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                        if (!ok) focusManager.moveFocus(FocusDirection.Down)
                        true
                    } else false
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                val requester = focusRequesters[item.route]
                CinemaTopBarPill(
                    label = item.label,
                    selected = selected,
                    showSearchIcon = item.route.contains("search", ignoreCase = true),
                    onClick = { onItemClick(item.route) },
                    modifier = if (requester != null) Modifier.focusRequester(requester) else Modifier
                )
            }
            if (!profileName.isNullOrBlank() && onProfileClick != null) {
                CinemaTopBarPill(
                    label = profileName,
                    selected = false,
                    showSearchIcon = false,
                    onClick = onProfileClick
                )
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
    label: String,
    selected: Boolean,
    showSearchIcon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (focused) 1.1f else 1f, tween(150), label = "navFocusScale")
    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
            .then(
                if (selected) Modifier
                else Modifier
            )
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.85f) else Color.Transparent,
            contentColor = if (selected) Color(0xFF09090B) else Color(0xFFD4D4D8),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF09090B),
            pressedContainerColor = Color.White.copy(alpha = 0.25f),
            pressedContentColor = if (selected) Color(0xFF09090B) else Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (selected) {
                androidx.tv.material3.Border(
                    BorderStroke(3.dp, Color.White.copy(alpha = 0.28f)),
                    0.dp,
                    CircleShape
                )
            } else {
                androidx.tv.material3.Border.None
            }
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (showSearchIcon) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                maxLines = 1
            )
        }
    }
}
