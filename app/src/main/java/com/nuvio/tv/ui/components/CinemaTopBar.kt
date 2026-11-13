package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
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
import com.nuvio.tv.ui.theme.SpaceGroteskFamily
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
 * muted gear + clock on the right.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinemaTopBar(
    items: List<CinemaTopBarItem>,
    selectedRoute: String?,
    focusRequesters: Map<String, FocusRequester>,
    onItemClick: (String) -> Unit,
    onBarFocusChanged: (Boolean) -> Unit,
    profileName: String? = null,
    onProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(cdp(100))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xC709090B),
                    0.55f to Color(0x7309090B),
                    1f to Color.Transparent
                )
            )
            .onFocusChanged { onBarFocusChanged(it.hasFocus) }
            .padding(horizontal = cdp(80))
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_cinema_clapper),
            contentDescription = null,
            modifier = Modifier.size(cdp(30))
        )
        Spacer(Modifier.width(cdp(52)))
        Row(
            horizontalArrangement = Arrangement.spacedBy(cdp(8)),
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
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = Color(0xFFA1A1AA).copy(alpha = 0.8f),
            modifier = Modifier.size(cdp(24))
        )
        Spacer(Modifier.width(cdp(22)))
        Text(
            text = clock,
            style = TextStyle(
                fontFamily = SpaceGroteskFamily,
                fontSize = csp(20),
                fontWeight = FontWeight.Medium,
                letterSpacing = csp(0.8),
                color = Color(0xFFA1A1AA)
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
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(
                if (selected) Modifier.shadow(cdp(10), CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                else Modifier
            )
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = if (selected) Color(0xFF09090B) else Color(0xFFB4B4BB),
            focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.15f),
            focusedContentColor = if (selected) Color(0xFF09090B) else Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.25f),
            pressedContentColor = if (selected) Color(0xFF09090B) else Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (selected) {
                androidx.tv.material3.Border(
                    BorderStroke(cdp(3), Color.White.copy(alpha = 0.28f)),
                    cdp(0),
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
                .padding(horizontal = cdp(24), vertical = cdp(9)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cdp(9))
        ) {
            if (showSearchIcon) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(cdp(19))
                )
            }
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = SpaceGroteskFamily,
                    fontSize = csp(20),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = csp(0.4)
                ),
                maxLines = 1
            )
        }
    }
}
