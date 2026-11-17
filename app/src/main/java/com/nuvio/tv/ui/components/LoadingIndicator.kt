package com.nuvio.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Circular arc with a white sweep gradient (transparent tail -> solid white head), rotating smoothly.
 */
@Composable
fun GradientRingSpinner(
    diameter: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val spin = rememberInfiniteTransition(label = "gradientRing")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "gradientRingAngle"
    )
    Canvas(modifier = modifier.size(diameter)) {
        val strokeWidth = (diameter.value / 9f).coerceAtLeast(2f).dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        // Faint track
        drawArc(
            color = color.copy(alpha = 0.10f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = strokeWidth)
        )
        rotate(angle) {
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.35f to color.copy(alpha = 0.0f),
                    1.0f to color,
                    center = center
                ),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        GradientRingSpinner(
            diameter = NuvioTheme.spacing.xxxl,
            color = if (color == Color.Unspecified) Color.White else color
        )
    }
}
