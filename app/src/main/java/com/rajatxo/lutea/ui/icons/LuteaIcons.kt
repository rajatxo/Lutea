package com.rajatxo.lutea.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lutea's custom icon set — ported from Coral X's CoralXIcons.
 * All glyphs are Lucide (MIT) vector paths.
 */
object LuteaIcons {

    private fun stroke(
        name: String,
        pathBuilder: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f,
            pathBuilder = pathBuilder
        )
    }.build()

    /** Lucide search icon (MIT). */
    val Search: ImageVector = stroke("Search") {
        moveTo(19f, 11f)
        arcTo(8f, 8f, 0f, true, true, 3f, 11f)
        arcTo(8f, 8f, 0f, true, true, 19f, 11f)
        moveTo(21f, 21f)
        lineTo(16.7f, 16.7f)
    }

    /** Lucide settings/cog icon (MIT). Ported from Coral X. */
    val Cog: ImageVector = stroke("Cog") {
        // Spokes
        moveTo(11f, 10.27f)
        lineTo(7f, 3.34f)
        moveTo(11f, 13.73f)
        lineTo(7f, 20.66f)
        moveTo(12f, 2f)
        lineTo(12f, 4f)
        moveTo(12f, 20f)
        lineTo(12f, 22f)
        moveTo(14f, 12f)
        lineTo(22f, 12f)
        moveTo(17f, 3.34f)
        lineTo(16f, 5.07f)
        moveTo(17f, 20.66f)
        lineTo(16f, 18.93f)
        moveTo(2f, 12f)
        lineTo(4f, 12f)
        moveTo(20.66f, 17f)
        lineTo(18.93f, 16f)
        moveTo(20.66f, 7f)
        lineTo(18.93f, 8f)
        moveTo(3.34f, 17f)
        lineTo(5.07f, 16f)
        moveTo(3.34f, 7f)
        lineTo(5.07f, 8f)
        // Inner circle
        moveTo(14f, 12f)
        curveTo(14f, 13.105f, 13.105f, 14f, 12f, 14f)
        curveTo(10.895f, 14f, 10f, 13.105f, 10f, 12f)
        curveTo(10f, 10.895f, 10.895f, 10f, 12f, 10f)
        curveTo(13.105f, 10f, 14f, 10.895f, 14f, 12f)
        close()
        // Outer circle
        moveTo(20f, 12f)
        curveTo(20f, 16.418f, 16.418f, 20f, 12f, 20f)
        curveTo(7.582f, 20f, 4f, 16.418f, 4f, 12f)
        curveTo(4f, 7.582f, 7.582f, 4f, 12f, 4f)
        curveTo(16.418f, 4f, 20f, 7.582f, 20f, 12f)
        close()
    }

    /** Lucide circle-user icon (MIT). Ported from Coral X. */
    val CircleUser: ImageVector = stroke("CircleUser") {
        // Outer circle
        moveTo(22f, 12f)
        curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
        curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
        curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
        curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
        close()
        // Head circle
        moveTo(15f, 10f)
        curveTo(15f, 11.657f, 13.657f, 13f, 12f, 13f)
        curveTo(10.343f, 13f, 9f, 11.657f, 9f, 10f)
        curveTo(9f, 8.343f, 10.343f, 7f, 12f, 7f)
        curveTo(13.657f, 7f, 15f, 8.343f, 15f, 10f)
        close()
        // Shoulders
        moveTo(7f, 20.662f)
        lineTo(7f, 19f)
        curveTo(7f, 17.895f, 7.895f, 17f, 9f, 17f)
        lineTo(15f, 17f)
        curveTo(16.105f, 17f, 17f, 17.895f, 17f, 19f)
        lineTo(17f, 20.662f)
    }
}
