package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
 * CinemaOS floating glass pill navigation bar (TV): logo left, nav items as pills,
 * active = white pill with black text, focus = white ring + 1.05 scale.
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
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(CinemaGlass.Scrim, CircleShape)
            .border(BorderStroke(1.dp, CinemaGlass.BorderSubtle), CircleShape)
            .onFocusChanged { onBarFocusChanged(it.hasFocus) }
            .padding(horizontal = 20.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrandWordmark(
            modifier = Modifier.height(22.dp).widthIn(max = 120.dp),
            alpha = 0.9f
        )
        Spacer(Modifier.width(14.dp))
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val requester = focusRequesters[item.route]
            CinemaTopBarPill(
                label = item.label,
                selected = selected,
                onClick = { onItemClick(item.route) },
                modifier = if (requester != null) Modifier.focusRequester(requester) else Modifier
            )
        }
        if (!profileName.isNullOrBlank() && onProfileClick != null) {
            Spacer(Modifier.width(8.dp))
            CinemaTopBarPill(
                label = profileName,
                selected = false,
                onClick = onProfileClick
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CinemaTopBarPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(38.dp)
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
            focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.2f),
            focusedContentColor = if (selected) Color.Black else Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.3f),
            pressedContentColor = if (selected) Color.Black else Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                BorderStroke(2.dp, CinemaGlass.FocusRing), CircleShape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = CinemaGlass.FocusScale)
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}
