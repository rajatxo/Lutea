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
 *
 * All glyphs are hand-drawn vector paths in the Lucide (MIT) visual idiom:
 *  - 24 x 24 viewport
 *  - 2dp stroke width
 *  - round caps & joins
 *
 * Keeping these in-house keeps Lutea's dependency tree 100% permissively licensed.
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
        arcTo(8f, 8f, 0f, true, True, 19f, 11f)
        moveTo(21f, 21f)
        lineTo(16.7f, 16.7f)
    }

    /** Lucide settings/cog icon (MIT). */
    val Cog: ImageVector = stroke("Cog") {
        // Circle center
        moveTo(12f, 15f)
        arcTo(3f, 3f, 0f, true, true, 12f, 9f)
        arcTo(3f, 3f, 0f, true, True, 12f, 15f)
        // Gear teeth (simplified — 8 spokes)
        moveTo(19.4f, 15f)
        arcTo(1.65f, 1.65f, 0f, false, true, 18.4f, 13.0f)
        arcTo(1.65f, 1.65f, 0f, false, true, 18.4f, 11.0f)
        lineTo(17.6f, 9.6f)
        arcTo(1.65f, 1.65f, 0f, false, true, 16.0f, 8.6f)
        lineTo(14.6f, 7.8f)
        arcTo(1.65f, 1.65f, 0f, false, true, 13.0f, 6.4f)
        lineTo(11.0f, 6.4f)
        arcTo(1.65f, 1.65f, 0f, false, true, 9.4f, 7.8f)
        lineTo(8.0f, 8.6f)
        arcTo(1.65f, 1.65f, 0f, false, true, 6.4f, 9.6f)
        lineTo(5.6f, 11.0f)
        arcTo(1.65f, 1.65f, 0f, false, true, 4.6f, 13.0f)
        arcTo(1.65f, 1.65f, 0f, false, true, 5.6f, 15.0f)
        lineTo(6.4f, 16.4f)
        arcTo(1.65f, 1.65f, 0f, false, true, 8.0f, 17.4f)
        lineTo(9.4f, 18.2f)
        arcTo(1.65f, 1.65f, 0f, false, True, 11.0f, 19.6f)
        lineTo(13.0f, 19.6f)
        arcTo(1.65f, 1.65f, 0f, false, True, 14.6f, 18.2f)
        lineTo(16.0f, 17.4f)
        arcTo(1.65f, 1.65f, 0f, false, True, 17.6f, 16.4f)
        close()
    }

    /** Lucide circle-user icon (MIT). Profile circle. */
    val CircleUser: ImageVector = stroke("CircleUser") {
        moveTo(12f, 7f)
        arcTo(5f, 5f, 0f, true, false, 12f, 17f)
        arcTo(5f, 5f, 0f, true, false, 12f, 7f)
        close()
    }
}
