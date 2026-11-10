package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import com.rajatxo.lutea.ui.screens.blendStops
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val Cream = Color(0xFFF5F0E6)
private val GinkgoGold = Color(0xFFD4A03A)

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
    title: String = "Spatial Audio",
    buttonLabel: String = "Activate",
    onButtonClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalLuteaWindowInsets.current),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top area: title + button (small) ──
            val topHeight = with(density) { (heightPx * 0.08f).toDp() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color = Cream,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = with(density) { (min(widthPx, heightPx) * 0.040f).toSp() },
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GinkgoGold)
                        .clickable { onButtonClick() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = buttonLabel,
                        color = Color.Black,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = with(density) { (min(widthPx, heightPx) * 0.025f).toSp() },
                    )
                }
            }

            // ── Sphere composition — fills remaining space above the head ──
            // Width = full screen so rings extend edge-to-edge.
            // Height = everything except top area and head.
            val headFraction = 0.50f
            val imgSizeDp = with(density) {
                val sideGapPx = widthPx * 0.075f
                min(widthPx - sideGapPx * 2, heightPx * headFraction).toDp()
            }
            val compositionHeightDp = with(density) {
                (heightPx - with(density) { topHeight.toPx() } - with(density) { imgSizeDp.toPx() }).toDp()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(compositionHeightDp),
                contentAlignment = Alignment.TopCenter,
            ) {
                SphereComposition(modifier = Modifier.fillMaxSize())
            }

            // ── Head image at bottom ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imgSizeDp),
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
        // Width drives sizing so the composition fills left/right edges of screen.
        val dim = size.width
        val cx = size.width / 2f
        // Vertical center sits at half of width (since rings are circular and
        // width-driven). The composition is top-aligned in its container, so the
        // rings render near the top — leaves all remaining container height as
        // empty space below, which becomes the gap to the head image.
        val cy = dim / 2f

        // Radii — all scaled to width. LEFT/RIGHT labels need ~0.10 width outside
        // the tag (tagSize/2 + labelOffset*2.4 ≈ 0.05 + 0.096 = 0.146 of width
        // per side). So the outermost ring (tag) sits at 0.5 - 0.146 = 0.354 max.
        val sphereR = dim * 0.22f
        val arrowBaseR = dim * 0.235f
        val arrowH = dim * 0.022f
        val arrowW = dim * 0.028f
        val ringR = dim * 0.285f          // thin + thick circle
        val dottedR = dim * 0.310f
        val tagR = dim * 0.335f          // tags sit OUTSIDE the dotted ring — no overlap
        val tagSize = dim * 0.026f
        val labelOffset = dim * 0.030f

        val thinStroke = dim * 0.0028f
        val thickStroke = dim * 0.0080f

        // ── Dotted circle (84 dots) ──
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

        // ── Thin full circle ──
        drawCircle(
            color = Cream.copy(alpha = 0.30f),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = thinStroke),
        )

        // ── Thick arc segments at the 4 arrow directions ──
        // Canvas angles: 0°=right, 90°=bottom, 180°=left, 270°=top (clockwise).
        // Compass angles above are 0=right, 90=up. Convert: canvas = 360 - compass.
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
        // Top (compass 90)
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - arrowBaseR - arrowH)
                lineTo(cx - arrowW / 2, cy - arrowBaseR)
                lineTo(cx + arrowW / 2, cy - arrowBaseR)
                close()
            },
            color = arrowColor,
        )
        // Bottom (compass 270)
        drawPath(
            path = Path().apply {
                moveTo(cx, cy + arrowBaseR + arrowH)
                lineTo(cx - arrowW / 2, cy + arrowBaseR)
                lineTo(cx + arrowW / 2, cy + arrowBaseR)
                close()
            },
            color = arrowColor,
        )
        // Left (compass 180)
        drawPath(
            path = Path().apply {
                moveTo(cx - arrowBaseR - arrowH, cy)
                lineTo(cx - arrowBaseR, cy - arrowW / 2)
                lineTo(cx - arrowBaseR, cy + arrowW / 2)
                close()
            },
            color = arrowColor,
        )
        // Right (compass 0)
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
        // SVG points the "tip" left (negative X). We rotate the icon so its
        // tip points INWARD toward the sphere — i.e. it "faces" the arrow.
        // Compass angle → rotation (SVG points left = 180° baseline).
        // To point inward at compass θ, rotation = θ + 180°.
        for (dir in tagDirs) {
            val angleRad = Math.toRadians(dir.angleDeg.toDouble()).toFloat()
            val tx = cx + tagR * cos(angleRad)
            val ty = cy - tagR * sin(angleRad)  // flip Y because compass up = negative Y
            // SVG tip points left at 0° rotation. We want it to point INWARD
            // (toward center). Direction from this tag TO center is compass+180.
            // SVG's "left" = 180°. So rotation_deg = (compass + 180) - 180 = compass.
            // Wait — let's redo: SVG path's pointed end is at -X (left in canvas
            // coords). To make that point face center from compass θ, the
            // icon's local -X axis must point in direction (θ+180)°.
            // rotate(deg) rotates clockwise in canvas. So set rotation = -compass.
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
        // Place OUTSIDE the tag icon, further along the same compass direction.
        // Use Compose's drawText via nativeCanvas — but Canvas drawScope doesn't
        // have direct text. So draw text labels using drawIntoCanvas + native Paint.
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(245, 245, 240, 230)
            textSize = dim * 0.022f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (dir in tagDirs) {
            if (dir.label.isEmpty()) continue
            val angleRad = Math.toRadians(dir.angleDeg.toDouble()).toFloat()
            // LEFT/RIGHT labels are wide horizontally — push them further out so
            // they don't sit on top of the tag icon. UP/DOWN are short, less offset.
            val offsetForDir = when (dir.label) {
                "LEFT", "RIGHT" -> labelOffset * 2.4f
                else -> labelOffset
            }
            val lx = cx + (tagR + tagSize / 2f + offsetForDir) * cos(angleRad)
            val ly = cy - (tagR + tagSize / 2f + offsetForDir) * sin(angleRad)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    dir.label,
                    lx,
                    ly + paint.textSize / 3f,  // vertical centering approx
                    paint,
                )
            }
        }

        // ── Dot sphere ──
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
