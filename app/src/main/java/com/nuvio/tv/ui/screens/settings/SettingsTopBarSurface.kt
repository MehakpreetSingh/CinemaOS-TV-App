package com.nuvio.tv.ui.screens.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlin.math.roundToInt

/** Settings' focus pill and positioning, shared by horizontal page navigation. */
@Composable
internal fun SettingsTopBarSurface(
    focused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.((LayoutCoordinates) -> Unit) -> Unit,
) {
    var topBarCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focusedTabBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    Box(modifier.fillMaxWidth().onGloballyPositioned { topBarCoordinates = it }) {
        focusedTabBounds?.let { bounds ->
            val glideSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)
            val pillLeft by animateFloatAsState(bounds.left, glideSpec, label = "pillLeft")
            val pillTop by animateFloatAsState(bounds.top, glideSpec, label = "pillTop")
            val pillWidth by animateFloatAsState(bounds.width, glideSpec, label = "pillWidth")
            val pillHeight by animateFloatAsState(bounds.height, glideSpec, label = "pillHeight")
            val pillAlpha by animateFloatAsState(
                targetValue = if (focused) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "pillAlpha"
            )
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset(pillLeft.roundToInt(), pillTop.roundToInt()) }
                    .size(
                        width = with(density) { pillWidth.toDp() },
                        height = with(density) { pillHeight.toDp() }
                    )
                    .graphicsLayer { alpha = pillAlpha }
                    .clip(RoundedCornerShape(SettingsPillRadius))
                    .background(NuvioTheme.colors.Secondary)
            )
        }
        content { coordinates ->
            topBarCoordinates?.let { container ->
                focusedTabBounds = container.localBoundingBoxOf(coordinates, clipBounds = false)
            }
        }
    }
}
