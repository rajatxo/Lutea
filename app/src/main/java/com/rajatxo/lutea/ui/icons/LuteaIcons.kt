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

    /**
     * Filled-icon builder for Material Symbols glyphs whose path data lives in
     * a 960×960 viewport (Material coordinate space). The 24dp display size is
     * preserved; Compose scales the viewport to fit.
     */
    private fun filled(
        name: String,
        viewportWidth: Float = 960f,
        viewportHeight: Float = 960f,
        pathBuilder: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathBuilder = pathBuilder
        )
    }.build()

    /** Lucide search icon (MIT). circle cx=11 cy=11 r=8 + handle line. */
    val Search: ImageVector = stroke("Search") {
        moveTo(19f, 11f)
        arcTo(8f, 8f, 0f, true, true, 3f, 11f)
        arcTo(8f, 8f, 0f, true, true, 19f, 11f)
        moveTo(21f, 21f)
        lineTo(16.66f, 16.66f)
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

    /** Lucide layout-grid-circles icon (MIT) — 4 circles in a 2x2 grid. */
    val GridCircles: ImageVector = ImageVector.Builder(
        name = "GridCircles",
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
        ) {
            // Top-left circle: cx=6.5, cy=6.5, r=3.5
            moveTo(10f, 6.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 3f, 6.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 10f, 6.5f)
            close()
            // Top-right circle: cx=17.5, cy=6.5, r=3.5
            moveTo(21f, 6.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 14f, 6.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 21f, 6.5f)
            close()
            // Bottom-left circle: cx=6.5, cy=17.5, r=3.5
            moveTo(10f, 17.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 3f, 17.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 10f, 17.5f)
            close()
            // Bottom-right circle: cx=17.5, cy=17.5, r=3.5
            moveTo(21f, 17.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 14f, 17.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 21f, 17.5f)
            close()
        }
    }.build()

    /** Lucide house icon (MIT) — exact provided SVG. Curved roof + arched door. */
    val Home: ImageVector = stroke("Home") {
        // Door: M15,21 v-8 a1,1 0 0 0 -1,-1 h-4 a1,1 0 0 0 -1,1 v8
        moveTo(15f, 21f)
        verticalLineTo(13f)
        arcTo(1f, 1f, 0f, false, false, 14f, 12f)
        horizontalLineTo(10f)
        arcTo(1f, 1f, 0f, false, false, 9f, 13f)
        verticalLineTo(21f)
        // House outline: M3,10 a2,2 0 0 1 .709,-1.528 l7,-6 a2,2 0 0 1 2.582,0 l7,6 A2,2 0 0 1 21,10 v9 a2,2 0 0 1 -2,2 H5 a2,2 0 0 1 -2,-2 z
        moveTo(3f, 10f)
        arcTo(2f, 2f, 0f, false, true, 3.709f, 8.472f)
        lineTo(10.709f, 2.472f)
        arcTo(2f, 2f, 0f, false, true, 13.291f, 2.472f)
        lineTo(20.291f, 8.472f)
        arcTo(2f, 2f, 0f, false, true, 21f, 10f)
        verticalLineTo(19f)
        arcTo(2f, 2f, 0f, false, true, 19f, 21f)
        horizontalLineTo(5f)
        arcTo(2f, 2f, 0f, false, true, 3f, 19f)
        close()
    }

    /**
     * New Library icon — two overlapping rounded squares (albums) each with a
     * dot and a toggle line. Filled glyph, 24dp, from the user's SVG.
     *
     * SVG path (viewBox 0 0 24 24):
     *   Front square: M18.75 8 A3.25 3.25 0 0 1 22 11.25 v7.5 A3.25 3.25 0 0 1 18.75 22 h-7.5
     *                 A3.25 3.25 0 0 1 8 18.75 v-7.5 A3.25 3.25 0 0 1 11.25 8 z
     *   Front dot:    m-6.25 7.5 a2 2 0 1 0 0 4 a2 2 0 0 0 0-4
     *   Front toggle: m4.25 1.5 a.75.75 0 0 0 0 1.5 h2.5 a.75.75 0 0 0 0-1.5 z
     *   Front dot2:   m-4.25 0 a.5.5 0 1 1 0 1 a.5.5 0 0 1 0-1
     *   Back square:  m-.846-14.888 a3.25 3.25 0 0 1 3.98 2.298 l.694 2.591 H11.25 A4.25 4.25 0 0 0 7 11.251 v6.43
     *                 a3.25 3.25 0 0 1-2.947-2.403 L2.11 8.034 a3.25 3.25 0 0 1 2.298-3.98 z
     *   Back dot:     M12.5 10.5 a2 2 0 1 0 0 4 a2 2 0 0 0 0-4
     *   Back toggle:  m4.25 1.5 a.75.75 0 0 0 0 1.5 h2.5 a.75.75 0 0 0 0-1.5 z
     *   Back dot2:    m-4.25 0 a.5.5 0 1 1 0 1 a.5.5 0 0 1 0-1
     */
    val Library: ImageVector = filled("Library", viewportWidth = 24f, viewportHeight = 24f) {
        // Front rounded square (18.75,8 → 22,11.25 → 22,18.75 → 18.75,22 → 11.25,22 → 8,18.75 → 8,11.25 → 11.25,8 → close)
        moveTo(18.75f, 8f)
        arcToRelative(3.25f, 3.25f, 0f, false, true, 3.25f, 3.25f)
        verticalLineTo(18.75f)
        arcToRelative(3.25f, 3.25f, 0f, false, true, -3.25f, 3.25f)
        horizontalLineTo(11.25f)
        arcToRelative(3.25f, 3.25f, 0f, false, true, -3.25f, -3.25f)
        verticalLineTo(11.25f)
        arcToRelative(3.25f, 3.25f, 0f, false, true, 3.25f, -3.25f)
        close()
        // Front dot (relative m -6.25 7.5 → circle r=2 at (12.5, 15.5))
        moveTo(12.5f, 15.5f)
        arcToRelative(2f, 2f, 0f, true, false, 0f, 4f)
        arcToRelative(2f, 2f, 0f, true, false, 0f, -4f)
        close()
        // Front toggle line (relative m 4.25 1.5 → rect at (16.75, 17) w=2.5 h=1.5)
        moveTo(16.75f, 17f)
        arcToRelative(0.75f, 0.75f, 0f, false, false, 0f, 1.5f)
        horizontalLineToRelative(2.5f)
        arcToRelative(0.75f, 0.75f, 0f, false, false, 0f, -1.5f)
        close()
        // Front small dot (relative m -4.25 0 → .5 circle at (12.5, 17))
        moveTo(12.5f, 17f)
        arcToRelative(0.5f, 0.5f, 0f, true, true, 0f, 1f)
        arcToRelative(0.5f, 0.5f, 0f, true, true, 0f, -1f)
        close()
        // Back rounded square (relative m -.846 -14.888 → starts at (11.654, 2.112))
        moveTo(11.654f, 2.112f)
        arcToRelative(3.25f, 3.25f, 0f, false, true, 3.98f, 2.298f)
        lineToRelative(0.694f, 2.591f)
        horizontalLineTo(11.25f)
        arcToRelative(4.25f, 4.25f, 0f, false, false, -4.25f, 4.25f)
        verticalLineToRelative(6.43f)
        arcToRelative(3.25f, 3.25f, 0f, false, true, -2.947f, -2.403f)
        lineTo(2.11f, 8.034f)
        arcToRelative(3.25f, 3.25f, 0f, false, true, 2.298f, -3.98f)
        close()
        // Back dot (M12.5 10.5 → circle r=2)
        moveTo(12.5f, 10.5f)
        arcToRelative(2f, 2f, 0f, true, false, 0f, 4f)
        arcToRelative(2f, 2f, 0f, true, false, 0f, -4f)
        close()
        // Back toggle line (relative m 4.25 1.5 → rect at (16.75, 12) w=2.5 h=1.5)
        moveTo(16.75f, 12f)
        arcToRelative(0.75f, 0.75f, 0f, false, false, 0f, 1.5f)
        horizontalLineToRelative(2.5f)
        arcToRelative(0.75f, 0.75f, 0f, false, false, 0f, -1.5f)
        close()
        // Back small dot (relative m -4.25 0 → .5 circle at (12.5, 12))
        moveTo(12.5f, 12f)
        arcToRelative(0.5f, 0.5f, 0f, true, true, 0f, 1f)
        arcToRelative(0.5f, 0.5f, 0f, true, true, 0f, -1f)
        close()
    }

    /** Lucide list icon (MIT) — bulleted list, for the playlist-view toggle. */
    val ListIcon: ImageVector = stroke("ListIcon") {
        // Three horizontal lines
        moveTo(8f, 6f)
        lineTo(21f, 6f)
        moveTo(8f, 12f)
        lineTo(21f, 12f)
        moveTo(8f, 18f)
        lineTo(21f, 18f)
        // Three dots (round-capped micro-lines render as dots)
        moveTo(3f, 6f)
        lineTo(3.01f, 6f)
        moveTo(3f, 12f)
        lineTo(3.01f, 12f)
        moveTo(3f, 18f)
        lineTo(3.01f, 18f)
    }

    /** Lucide square icon (MIT) — rounded square outline, for the tag-view toggle. */
    val Square: ImageVector = stroke("Square") {
        moveTo(3f, 5f)
        arcTo(2f, 2f, 0f, false, true, 5f, 3f)
        horizontalLineTo(19f)
        arcTo(2f, 2f, 0f, false, true, 21f, 5f)
        verticalLineTo(19f)
        arcTo(2f, 2f, 0f, false, true, 19f, 21f)
        horizontalLineTo(5f)
        arcTo(2f, 2f, 0f, false, true, 3f, 19f)
        close()
    }
}
