package com.nuvio.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Broadcast screen, shared by the main navigation and Live TV filter. */
internal val LiveTvIcon: ImageVector by lazy {
    ImageVector.Builder("Live broadcast", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString("M 4 5 L 20 5 C 21.1 5 22 5.9 22 7 L 22 17 C 22 18.1 21.1 19 20 19 L 4 19 C 2.9 19 2 18.1 2 17 L 2 7 C 2 5.9 2.9 5 4 5 Z M 8 22 L 16 22 M 10 19 L 10 22 M 14 19 L 14 22 M 12 8 C 15.3 8 18 10.7 18 14 M 12 11 C 13.7 11 15 12.3 15 14 M 12 14 L 12 14.05").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
