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

    /**
     * New Home icon — filled house with roof chevron + body. 36x36 viewport.
     * From the user's SVG (Clarity solid house):
     *   Path 1 (roof): M33,19 a1,1 0 0 1 -.71,-.29 L18,4.41 L3.71,18.71 A1,1 0 0 1 2.3,17.3 l15,-15 a1,1 0 0 1 1.41,0 l15,15 A1,1 0 0 1 33,19
     *   Path 2 (body): M18,7.79 L6,19.83 V32 a2,2 0 0 0 2,2 h7 V24 h6 v10 h7 a2,2 0 0 0 2,-2 V19.76 Z
     */
    val Home: ImageVector = filled("Home", viewportWidth = 36f, viewportHeight = 36f) {
        // Roof chevron
        moveTo(33f, 19f)
        arcTo(1f, 1f, 0f, false, true, 32.29f, 18.71f)
        lineTo(18f, 4.41f)
        lineTo(3.71f, 18.71f)
        arcTo(1f, 1f, 0f, false, true, 2.3f, 17.3f)
        lineTo(17.3f, 2.3f)
        arcTo(1f, 1f, 0f, false, true, 18.71f, 2.3f)
        lineTo(33.71f, 17.3f)
        arcTo(1f, 1f, 0f, false, true, 33f, 19f)
        close()
        // House body
        moveTo(18f, 7.79f)
        lineTo(6f, 19.83f)
        verticalLineTo(32f)
        arcTo(2f, 2f, 0f, false, false, 8f, 34f)
        horizontalLineTo(15f)
        verticalLineTo(24f)
        horizontalLineTo(21f)
        verticalLineTo(34f)
        horizontalLineTo(28f)
        arcTo(2f, 2f, 0f, false, false, 30f, 32f)
        verticalLineTo(19.76f)
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

    /** Effect icon — simple speaker with sound waves. Filled. */
    val Effect: ImageVector = filled("Effect", viewportWidth = 24f, viewportHeight = 24f) {
        // Speaker body (rounded rect at bottom)
        moveTo(17.755f, 14f)
        arcTo(2.25f, 2.25f, 0f, false, true, 20.003f, 16.25f)
        verticalLineTo(17.168f)
        arcTo(2.75f, 2.75f, 0f, false, true, 19.491f, 18.766f)
        relativeCubicTo(-1.546f, 2.164f, -4.07f, 3.235f, -7.49f, 3.235f)
        relativeCubicTo(-3.422f, 0f, -5.945f, -1.072f, -7.487f, -3.236f)
        arcTo(2.75f, 2.75f, 0f, false, true, 4.004f, 16.169f)
        verticalLineTo(15.249f)
        arcTo(2.25f, 2.25f, 0f, false, true, 6.253f, 14f)
        close()
        // Head circle
        moveTo(12f, 2.005f)
        arcTo(5f, 5f, 0f, true, false, 12f, 12.005f)
        arcTo(5f, 5f, 0f, true, false, 12f, 2.005f)
        close()
        // Right sound wave (outer)
        moveTo(20.877f, 12.126f)
        relativeCubicTo(0.265f, 0.319f, 0.742f, 0.317f, 1.035f, 0.024f)
        relativeCubicTo(0.293f, -0.293f, 0.29f, -0.765f, 0.032f, -1.09f)
        relativeCubicTo(-0.89f, -1.109f, -1.405f, -2.512f, -1.405f, -4.037f)
        relativeCubicTo(0f, -1.525f, 0.525f, -2.928f, 1.405f, -4.036f)
        relativeCubicTo(0.257f, -0.325f, 0.261f, -0.797f, -0.032f, -1.09f)
        relativeCubicTo(-0.293f, -0.293f, -0.77f, -0.295f, -1.035f, 0.024f)
        relativeCubicTo(-1.148f, 1.385f, -1.838f, 3.163f, -1.838f, 5.103f)
        relativeCubicTo(0f, 1.939f, 0.69f, 3.717f, 1.838f, 5.102f)
        close()
        // Right sound wave (inner)
        moveTo(23.015f, 4.056f)
        relativeCubicTo(0.246f, -0.334f, 0.726f, -0.33f, 1.019f, -0.038f)
        relativeCubicTo(0.293f, 0.293f, 0.284f, 0.765f, 0.06f, 1.113f)
        arcTo(3.5f, 3.5f, 0f, false, false, 23.504f, 7f)
        relativeCubicTo(0f, 0.697f, 0.204f, 1.347f, 0.555f, 1.893f)
        relativeCubicTo(0.224f, 0.348f, 0.233f, 0.82f, -0.06f, 1.113f)
        relativeCubicTo(-0.293f, 0.292f, -0.773f, 0.295f, -1.02f, -0.038f)
        arcTo(4.98f, 4.98f, 0f, false, true, 22.004f, 7f)
        relativeCubicTo(0f, -1.11f, 0.362f, -2.137f, 0.976f, -2.967f)
        close()
        // Left sound wave (outer)
        moveTo(3.1f, 11.036f)
        relativeCubicTo(-0.258f, 0.325f, -0.262f, 0.797f, 0.031f, 1.09f)
        relativeCubicTo(0.293f, 0.293f, 0.77f, 0.295f, 1.035f, 0.024f)
        arcTo(7.97f, 7.97f, 0f, false, false, 6.004f, 7f)
        relativeCubicTo(0f, -1.94f, -0.69f, -3.717f, -1.838f, -5.102f)
        relativeCubicTo(-0.264f, -0.32f, -0.742f, -0.317f, -1.035f, -0.024f)
        relativeCubicTo(-0.293f, 0.293f, -0.289f, 0.765f, -0.031f, 1.09f)
        arcTo(6.47f, 6.47f, 0f, false, true, 4.504f, 7f)
        arcTo(6.47f, 6.47f, 0f, false, true, 3.1f, 11.036f)
        close()
        // Left sound wave (inner)
        moveTo(1.01f, 10.005f)
        relativeCubicTo(0.293f, 0.293f, 0.773f, 0.296f, 1.019f, -0.038f)
        relativeCubicTo(0.613f, -0.83f, 0.975f, -1.856f, 0.975f, -2.967f)
        arcTo(4.98f, 4.98f, 0f, false, false, 2.029f, 4.033f)
        relativeCubicTo(-0.246f, -0.334f, -0.726f, -0.33f, -1.02f, -0.038f)
        relativeCubicTo(-0.292f, 0.293f, -0.284f, 0.765f, -0.06f, 1.113f)
        relativeCubicTo(0.352f, 0.546f, 0.555f, 1.195f, 0.555f, 1.892f)
        relativeCubicTo(0f, 0.697f, -0.203f, 1.346f, -0.555f, 1.892f)
        relativeCubicTo(-0.224f, 0.348f, -0.232f, 0.82f, 0.06f, 1.113f)
        close()
    }
}}
