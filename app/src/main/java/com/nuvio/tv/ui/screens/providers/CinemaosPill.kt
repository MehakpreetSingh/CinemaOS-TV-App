package com.nuvio.tv.ui.screens.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * CinemaOS pill button used by the Provider page (tabs, sort) and the Calendar header. Selected pills
 * are solid white; the focused pill gets a white edge and a small scale so it reads on any backdrop.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CinemaosPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.White.copy(alpha = 0.08f),
            contentColor = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
            focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.22f),
            focusedContentColor = if (selected) Color.Black else Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.3f),
            pressedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), 0.dp, shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), 0.dp, shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (text.isEmpty()) 10.dp else 16.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription ?: text.ifEmpty { null },
                    modifier = Modifier.size(18.dp)
                )
            }
            if (text.isNotEmpty()) {
                Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
