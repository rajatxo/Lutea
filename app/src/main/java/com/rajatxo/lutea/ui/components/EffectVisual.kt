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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import com.rajatxo.lutea.ui.screens.blendStops
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val Cream = Color(0xFFF5F0E6)

/**
 * Effect tab — dot sphere with concentric rings, outward arrows, 8 tag icons
 * placed around the ring (4 facing the arrows + 4 at mid-arcs), and directional
 * labels (LEFT/RIGHT/UP/DOWN) next to the arrow-facing tags.
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

        // ── Sphere composition, anchored to top ──
        // Width = full screen so rings extend edge-to-edge.
        // Height = enough to fit the rings (which are width-driven, diameter ≈ width)
        // plus a small headroom for the UP label above the rings.
        //   headroom = 6.67% of width (24dp ÷ 360dp on reference phone)
        val compositionHeightFrac = 1.0f + 0.0667f
        val compositionHeightDp = with(density) { (widthPx * compositionHeightFrac).toDp() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(compositionHeightDp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.TopCenter,
        ) {
            SphereComposition(modifier = Modifier.fillMaxSize())
        }

        // ── Head image at bottom — sized so DOWN-to-headphones gap ≥ 16.25% of screen height ──
        // (User asked for 130dp. On reference phone 800dp tall, 130/800 = 16.25%.)
        // DOWN label y ≈ compositionHeight - edgeGap - halfLabelWidth
        // head top y = screenHeight - imgSize
        // gap = headTop - DOWN_label_y
        // Solve for imgSize so gap ≥ 0.1625 × screenHeight.
        val headSizeDp = with(density) {
            val edgeGapFrac = 0.0111f
            val edgeGapPx = widthPx * edgeGapFrac
            val labelTextSize = widthPx * 0.024f
            val labelHalfWidth = labelTextSize * 4f * 0.55f / 2f
            val downLabelYPx = widthPx * compositionHeightFrac - edgeGapPx - labelHalfWidth
            val gapTargetPx = heightPx * 0.1625f
            val maxHeadForGap = heightPx - downLabelYPx - gapTargetPx
            val sideGapPx = widthPx * 0.075f
            // Cap by width-minus-side-gaps, 50% of height, and the gap-driven max.
            // Floor at 30% of width so the head doesn't get tiny on short screens.
            minOf(
                widthPx - sideGapPx * 2,
                heightPx * 0.50f,
                maxOf(maxHeadForGap, widthPx * 0.30f),
            ).toDp()
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.size(headSizeDp, headSizeDp)) {
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
// Sphere + arrows + concentric rings + 8 tag icons + direction labels
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

private data class Direction(
    val angleDeg: Float,        // compass: 0=right, 90=up, 180=left, 270=down
    val label: String,          // "UP"/"DOWN"/"LEFT"/"RIGHT" — empty for mid-arc tags
    val facesArrow: Boolean,    // true = sits in front of an arrow, false = mid-arc
)

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

    // 8 tag positions, clockwise from right (compass angles).
    // Arrow positions are at 0/90/180/270. Mid-arcs at 45/135/225/315.
    val tagDirs = remember {
        listOf(
            Direction(0f,   "RIGHT", true),
            Direction(45f,  "",      false),
            Direction(90f,  "UP",    true),
            Direction(135f, "",      false),
            Direction(180f, "LEFT",  true),
            Direction(225f, "",      false),
            Direction(270f, "DOWN",  true),
            Direction(315f, "",      false),
        )
    }

    val tagPainter = painterResource(R.drawable.ic_effect_tag)

    Canvas(modifier = modifier) {
        // Universal Layout Law #7: sizes are fractions of screen dimensions.
        // User asked for 4dp edge gap and 130dp DOWN-to-head gap (measured on a
        // standard 360dp × 800dp phone). Converted to fractions so the layout
        // scales proportionally on every screen size:
        //   4dp ÷ 360dp = 1.11% of screen width
        //   130dp ÷ 800dp = 16.25% of screen height (used in head sizing below)
        val edgeGapFrac = 0.0111f
        val edgeGapPx = size.width * edgeGapFrac

        // Text size — measured once to size the composition around it.
        val labelFontSize = size.width * 0.024f   // LEFT/RIGHT/UP/DOWN same size
        // Approximate width of "LEFT"/"RIGHT" — 4 chars × ~0.55 × fontSize.
        val labelHalfWidth = labelFontSize * 4f * 0.55f / 2f

        // Geometry, driven by width so rings fill edge-to-edge:
        //   outermost extent (LEFT/RIGHT label outer edge) = width/2 - edgeGapPx
        //   label center = outer edge - labelHalfWidth
        //   tag center  = label center - tagSize/2 - small gap
        //   dotted ring = tag center - tagSize/2 - gap
        //   thin ring   = dotted ring - gap
        //   sphere      = thin ring - arrow - gap
        val tagSize = size.width * 0.028f
        val labelCenterFromCenter = size.width / 2f - edgeGapPx - labelHalfWidth
        val tagR = labelCenterFromCenter - tagSize / 2f - size.width * 0.008f
        val dottedR = tagR - tagSize / 2f - size.width * 0.008f
        val ringR = dottedR - size.width * 0.012f
        val arrowBaseR = ringR - size.width * 0.045f
        val arrowH = size.width * 0.024f
        val arrowW = size.width * 0.030f
        // Sphere sits inside the arrows with extra breathing room (gap doubled).
        val sphereR = arrowBaseR - size.width * 0.060f

        val cx = size.width / 2f
        // Vertical center sits at half of width so rings are perfectly circular.
        // Composition is top-anchored in its container, so rings render near the top.
        val cy = size.width / 2f

        val thinStroke = size.width * 0.0028f
        val thickStroke = size.width * 0.0080f

        // ── Dotted circle (84 dots) ──
        val dotCount = 84
        val dotR = size.width * 0.0035f
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

        // ── Thin full circle ──
        drawCircle(
            color = Cream.copy(alpha = 0.30f),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = thinStroke),
        )

        // ── Thick arc segments at the 4 arrow directions ──
        val arcSweepDeg = 50f
        val halfSweep = arcSweepDeg / 2f
        val arcCenterCompass = floatArrayOf(0f, 90f, 180f, 270f)
        val arcSize = Size(ringR * 2, ringR * 2)
        val arcTopLeft = Offset(cx - ringR, cy - ringR)
        for (compass in arcCenterCompass) {
            val canvasAngle = (360f - compass - halfSweep).mod(360f)
            drawArc(
                color = Cream.copy(alpha = 0.90f),
                startAngle = canvasAngle,
                sweepAngle = arcSweepDeg,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = thickStroke, cap = StrokeCap.Round),
            )
        }

        // ── Four arrows ──
        val arrowColor = Cream.copy(alpha = 0.95f)
        // Top
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - arrowBaseR - arrowH)
                lineTo(cx - arrowW / 2, cy - arrowBaseR)
                lineTo(cx + arrowW / 2, cy - arrowBaseR)
                close()
            },
            color = arrowColor,
        )
        // Bottom
        drawPath(
            path = Path().apply {
                moveTo(cx, cy + arrowBaseR + arrowH)
                lineTo(cx - arrowW / 2, cy + arrowBaseR)
                lineTo(cx + arrowW / 2, cy + arrowBaseR)
                close()
            },
            color = arrowColor,
        )
        // Left
        drawPath(
            path = Path().apply {
                moveTo(cx - arrowBaseR - arrowH, cy)
                lineTo(cx - arrowBaseR, cy - arrowW / 2)
                lineTo(cx - arrowBaseR, cy + arrowW / 2)
                close()
            },
            color = arrowColor,
        )
        // Right
        drawPath(
            path = Path().apply {
                moveTo(cx + arrowBaseR + arrowH, cy)
                lineTo(cx + arrowBaseR, cy - arrowW / 2)
                lineTo(cx + arrowBaseR, cy + arrowW / 2)
                close()
            },
            color = arrowColor,
        )

        // ── Tag icons around the ring ──
        for (dir in tagDirs) {
            val angleRad = Math.toRadians(dir.angleDeg.toDouble()).toFloat()
            val tx = cx + tagR * cos(angleRad)
            val ty = cy - tagR * sin(angleRad)
            val rotationDeg = -dir.angleDeg
            rotate(degrees = rotationDeg, pivot = Offset(tx, ty)) {
                translate(left = tx - tagSize / 2f, top = ty - tagSize / 2f) {
                    with(tagPainter) {
                        draw(size = Size(tagSize, tagSize), colorFilter = null, alpha = 0.92f)
                    }
                }
            }
        }

        // ── Direction labels (LEFT/RIGHT/UP/DOWN) ──
        // Position so the OUTER edge of LEFT/RIGHT text sits exactly 4dp from the
        // screen edge. UP/DOWN are centered horizontally already.
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(245, 245, 240, 230)
            this.textSize = labelFontSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (dir in tagDirs) {
            if (dir.label.isEmpty()) continue
            val angleRad = Math.toRadians(dir.angleDeg.toDouble()).toFloat()
            // For LEFT/RIGHT, use the precise labelCenterFromCenter so the outer
            // edge sits at edgeGapPx from the screen edge. For UP/DOWN, sit just
            // outside the tag icon.
            val distFromCenter = when (dir.label) {
                "LEFT", "RIGHT" -> labelCenterFromCenter
                else -> tagR + tagSize / 2f + size.width * 0.020f
            }
            val lx = cx + distFromCenter * cos(angleRad)
            val ly = cy - distFromCenter * sin(angleRad)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    dir.label,
                    lx,
                    ly + paint.textSize / 3f,
                    paint,
                )
            }
        }

        // ── Dot sphere ──
        val baseDot = size.width * 0.0075f
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
