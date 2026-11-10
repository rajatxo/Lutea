package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import com.rajatxo.lutea.ui.screens.blendStops
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val Cream = Color(0xFFF5F0E6)

/**
 * Effect tab — Behance-style dot sphere with concentric rings + outward arrows.
 *
 * Layers, innermost to outermost:
 *  1. Dot sphere (Fibonacci-spiral points, depth-shaded, slowly rotating)
 *  2. Four triangular arrows at top/bottom/left/right, tips pointing outward
 *  3. Thin full circle enclosing sphere + arrows
 *  4. Four thick arc segments — only at the four arrow directions, the rest
 *     of the circle stays thin so the thin ring shows through
 *  5. Dotted circle (outermost, made of small dots)
 *
 * Below all this sits the 3D head image, bottom-anchored, blending into black.
 *
 * Universal Layout Law #7: BoxWithConstraints at root, fractional sizing.
 */
@Composable
fun EffectVisual(
    modifier: Modifier = Modifier,
    headRes: Int = R.drawable.effect_head,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalLuteaWindowInsets.current),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // ── Sphere composition at top ──
        val compositionSizeDp = with(density) {
            min(widthPx * 0.85f, heightPx * 0.55f).toDp()
        }
        val compositionTopPad = with(density) { (heightPx * 0.04f).toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = compositionTopPad)
                .size(compositionSizeDp),
        ) {
            SphereComposition(modifier = Modifier.fillMaxSize())
        }

        // ── Head image at bottom (same as before) ──
        val imgSizeDp = with(density) {
            val sideGapPx = widthPx * 0.075f
            min(widthPx - sideGapPx * 2, heightPx * 0.42f).toDp()
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.size(imgSizeDp, imgSizeDp)) {
                Image(
                    painter = painterResource(headRes),
                    contentDescription = "Spatial audio head",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val brush = Brush.verticalGradient(*blendStops(Color.Black))
                            onDrawBehind { drawRect(brush) }
                        },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sphere + arrows + concentric rings, all on one Canvas
// ─────────────────────────────────────────────────────────────────────────────

private data class SpherePoint(val x: Float, val y: Float, val z: Float)

private fun fibonacciSphere(n: Int): List<SpherePoint> {
    val out = ArrayList<SpherePoint>(n)
    val phi = (Math.PI * (3.0 - Math.sqrt(5.0))).toFloat()
    for (i in 0 until n) {
        val y = 1f - (i.toFloat() / (n - 1)) * 2f
        val r = sqrt(1f - y * y)
        val theta = phi * i
        out.add(SpherePoint(cos(theta) * r, y, sin(theta) * r))
    }
    return out
}

@Composable
private fun SphereComposition(modifier: Modifier = Modifier) {
    val points = remember { fibonacciSphere(180) }
    val infinite = rememberInfiniteTransition(label = "sphere")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rot",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val dim = min(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f

        // ── Radii, as fractions of the composition's min dimension ──
        val sphereR = dim * 0.30f
        val arrowBaseR = dim * 0.36f   // distance from center to arrow base
        val arrowH = dim * 0.06f
        val arrowW = dim * 0.07f
        val ringR = dim * 0.45f        // thin + thick circle radius
        val dottedR = dim * 0.485f     // outermost dotted ring

        val thinStroke = dim * 0.0028f
        val thickStroke = dim * 0.0080f

        // ── Layer 5: dotted circle (outermost, drawn first so it sits behind) ──
        val dotCount = 84
        val dotR = dim * 0.0035f
        for (i in 0 until dotCount) {
            val a = (2.0 * Math.PI * i / dotCount).toFloat()
            val dx = cx + dottedR * cos(a)
            val dy = cy + dottedR * sin(a)
            drawCircle(
                color = Cream.copy(alpha = 0.55f),
                radius = dotR,
                center = Offset(dx, dy),
            )
        }

        // ── Layer 3: thin full circle ──
        drawCircle(
            color = Cream.copy(alpha = 0.30f),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = thinStroke),
        )

        // ── Layer 4: thick arc segments — only in front of each arrow ──
        // Compose Canvas angles: 0°=right, 90°=bottom, 180°=left, 270°=top (clockwise).
        // Each arc centered on the arrow direction; the rest of the circle stays thin.
        val arcSweepDeg = 24f
        val halfSweep = arcSweepDeg / 2f
        val arcStarts = floatArrayOf(
            360f - halfSweep,  // Right (centered on 0°/360°)
            90f - halfSweep,    // Bottom (centered on 90°)
            180f - halfSweep,   // Left (centered on 180°)
            270f - halfSweep,   // Top (centered on 270°)
        )
        val arcSize = Size(ringR * 2, ringR * 2)
        val arcTopLeft = Offset(cx - ringR, cy - ringR)
        for (start in arcStarts) {
            drawArc(
                color = Cream.copy(alpha = 0.90f),
                startAngle = start,
                sweepAngle = arcSweepDeg,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = thickStroke, cap = StrokeCap.Round),
            )
        }

        // ── Layer 2: four arrows, tips pointing outward ──
        val arrowColor = Cream.copy(alpha = 0.95f)
        // Top: tip up
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - arrowBaseR - arrowH)
                lineTo(cx - arrowW / 2, cy - arrowBaseR)
                lineTo(cx + arrowW / 2, cy - arrowBaseR)
                close()
            },
            color = arrowColor,
        )
        // Bottom: tip down
        drawPath(
            path = Path().apply {
                moveTo(cx, cy + arrowBaseR + arrowH)
                lineTo(cx - arrowW / 2, cy + arrowBaseR)
                lineTo(cx + arrowW / 2, cy + arrowBaseR)
                close()
            },
            color = arrowColor,
        )
        // Left: tip left
        drawPath(
            path = Path().apply {
                moveTo(cx - arrowBaseR - arrowH, cy)
                lineTo(cx - arrowBaseR, cy - arrowW / 2)
                lineTo(cx - arrowBaseR, cy + arrowW / 2)
                close()
            },
            color = arrowColor,
        )
        // Right: tip right
        drawPath(
            path = Path().apply {
                moveTo(cx + arrowBaseR + arrowH, cy)
                lineTo(cx + arrowBaseR, cy - arrowW / 2)
                lineTo(cx + arrowBaseR, cy + arrowW / 2)
                close()
            },
            color = arrowColor,
        )

        // ── Layer 1: dot sphere (drawn last, on top in the center) ──
        val baseDot = dim * 0.0075f
        val ang = Math.toRadians(rotation.toDouble()).toFloat()
        val cosA = cos(ang); val sinA = sin(ang)
        val projected = ArrayList<SpherePoint>(points.size)
        for (p in points) {
            val x1 = p.x * cosA + p.z * sinA
            val z1 = -p.x * sinA + p.z * cosA
            projected.add(SpherePoint(x1, p.y, z1))
        }
        projected.sortBy { it.z }
        val pulseAmp = 0.10f
        for (v in projected) {
            val depth = (v.z + 1f) * 0.5f
            val wave = sin(pulse + depth * 3f)
            val scale = 1f + pulseAmp * wave
            val dotR = baseDot * (0.4f + depth * 0.9f) * scale
            val px = cx + v.x * sphereR
            val py = cy - v.y * sphereR
            val alpha = 0.18f + depth * 0.82f
            drawCircle(
                color = Cream.copy(alpha = alpha),
                radius = dotR.coerceAtLeast(0.3f),
                center = Offset(px, py),
            )
        }
    }
}
