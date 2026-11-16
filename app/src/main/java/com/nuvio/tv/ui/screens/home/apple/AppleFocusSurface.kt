package com.nuvio.tv.ui.screens.home.apple

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface

/**
 * Apple tvOS style focusable surface: focused scales to 1.08 with a spring, lifts slightly and
 * gets a soft drop shadow plus a subtle 1dp edge. No thick focus ring.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AppleFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    onFocusChange: ((Boolean) -> Unit)? = null,
    containerColor: Color = Color(0xFF1C1C1E),
    focusedContainerColor: Color = containerColor,
    contentColor: Color = Color.White,
    focusedContentColor: Color = contentColor,
    focusedScale: Float = 1.08f,
    content: @Composable BoxScope.() -> Unit
) {
    val cardShape = shape
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "appleScale"
    )
    val lift by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180),
        label = "appleLift"
    )
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .onFocusChanged {
                val nowFocused = it.isFocused
                if (nowFocused != focused) {
                    focused = nowFocused
                    onFocusChange?.invoke(nowFocused)
                    if (nowFocused) onFocused()
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -4.dp.toPx() * lift
                shadowElevation = 12.dp.toPx() * lift
                this.shape = cardShape
                clip = false
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
            pressedContainerColor = focusedContainerColor,
            pressedContentColor = focusedContentColor
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), 0.dp, shape)
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}
