package com.pinta.phone.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 24×24 outline icon: a three-spoke cockpit wheel.
 * Rendered by Material3 Icon at 17dp in the bottom bar, same size as before.
 */
val CockpitIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Cockpit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M12,3.5 " +
                    "A8.5,8.5 0 1,0 12,20.5 " +
                    "A8.5,8.5 0 1,0 12,3.5 " +
                    "M12,9.8 " +
                    "A2.2,2.2 0 1,0 12,14.2 " +
                    "A2.2,2.2 0 1,0 12,9.8 " +
                    "M12,3.5 L12,9.8 " +
                    "M4.3,15.1 L9.9,13.2 " +
                    "M19.7,15.1 L14.1,13.2",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
