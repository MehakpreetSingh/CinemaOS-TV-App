package com.nuvio.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Cat-ear face from the CinemaOS phone app's bottom dock, used for the Anime tab. */
internal val AnimeIcon: ImageVector by lazy {
    ImageVector.Builder("Anime", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M 4 3.5 L 9 6.2 C 11 5.8 13 5.8 15 6.2 L 20 3.5 L 20.5 12 C 20.5 17 17 20.5 12 20.5 C 7 20.5 3.5 17 3.5 12 Z"
            ).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = PathParser().parsePathString(
                "M 9.9 12 A 0.9 0.9 0 1 1 8.1 12 A 0.9 0.9 0 1 1 9.9 12 Z M 15.9 12 A 0.9 0.9 0 1 1 14.1 12 A 0.9 0.9 0 1 1 15.9 12 Z"
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
