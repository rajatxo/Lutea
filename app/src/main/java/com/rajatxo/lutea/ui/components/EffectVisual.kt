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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.rajatxo.lutea.R
import com.rajatxo.lutea.playback.PlaybackConnection
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
    // 8D state — we only need the enabled flag for the sphere rotation.
    // The overlay UI (sliders, toggle, presets) has been removed. Controls
    // will be re-implemented inside the sphere component later.
    val eightDOn by PlaybackConnection.is8DEnabled.collectAsState()

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
            // Pass enabled flag — sphere only rotates when 8D is on.
            SphereComposition(
                modifier = Modifier.fillMaxSize(),
                rotationEnabled = eightDOn,
            )
        }

        // ── Head image at bottom — sized so DOWN-to-headphones gap ≥ 8% of screen height ──
        // (User originally asked for 130dp = 16.25% of height, but that squeezed
        // the head too small. Relaxed to 8% so the head can grow back closer to
        // its original size while keeping a visible gap to the composition.)
        val headSizeDp = with(density) {
            val edgeGapFrac = 0.08f
            val edgeGapPx = widthPx * edgeGapFrac
            val labelTextSize = widthPx * 0.024f
            val labelHalfWidth = labelTextSize * 4f * 0.55f / 2f
            val downLabelYPx = widthPx * compositionHeightFrac - edgeGapPx - labelHalfWidth
            val gapTargetPx = heightPx * 0.04f
            val maxHeadForGap = heightPx - downLabelYPx - gapTargetPx
            val sideGapPx = widthPx * 0.075f
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
// 8D Audio capsule + toggle (overlay layer — doesn't push other UI)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EffectOverlay(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    label: String,
    orbitSpeed: Float,
    onOrbitSpeedChange: (Float) -> Unit,
    onOrbitSpeedFinished: () -> Unit,
    spatialAmbience: Float,
    onSpatialAmbienceChange: (Float) -> Unit,
    onSpatialAmbienceFinished: () -> Unit,
    isProcessing: Boolean,
    processingProgress: Float,
    lastError: String,
    isActive: Boolean,
    preset: com.rajatxo.lutea.playback.Effect8DPreset,
    onPresetChange: (com.rajatxo.lutea.playback.Effect8DPreset) -> Unit,
    screenWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = screenWidthPx
    val labelTextSize = with(density) { (widthPx * 0.030f).toSp() }
    val subLabelTextSize = with(density) { (widthPx * 0.022f).toSp() }
    val valueTextSize = with(density) { (widthPx * 0.020f).toSp() }
    val capsulePadHDp = with(density) { (widthPx * 0.025f).toDp() }
    val capsulePadVDp = with(density) { (widthPx * 0.012f).toDp() }
    val capsuleCornerDp = with(density) { (widthPx * 0.04f).toDp() }
    val toggleSizeDp = with(density) { (widthPx * 0.080f).toDp() }   // bigger toggle
    val sliderTrackColor = Cream.copy(alpha = 0.20f)
    val sliderActiveColor = Color(0xFFD4A03A)
    val sliderPadH = with(density) { (widthPx * 0.06f).toDp() }
    val sliderSpacing = with(density) { (widthPx * 0.015f).toDp() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sliderSpacing),
    ) {
        // Row 1: capsule + toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Capsule label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(capsuleCornerDp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(
                        width = with(density) { (widthPx * 0.002f).toDp() },
                        color = Cream.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(capsuleCornerDp),
                    )
                    .padding(horizontal = capsulePadHDp, vertical = capsulePadVDp),
            ) {
                Text(
                    text = label,
                    color = Cream,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = labelTextSize,
                )
            }
            // Toggle
            Box(
                modifier = Modifier
                    .padding(start = with(density) { (widthPx * 0.02f).toDp() })
                    .size(toggleSizeDp, toggleSizeDp * 0.55f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (enabled) Color(0xFFD4A03A) else Color.White.copy(alpha = 0.10f)
                    )
                    .clickable { onToggle(!enabled) }
                    .padding(with(density) { (widthPx * 0.005f).toDp() }),
                contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val knobSize = with(density) { (widthPx * 0.040f).toDp() }
                Box(
                    modifier = Modifier
                        .size(knobSize)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White),
                )
            }
        }

        // Preset row — Low / Balanced / High — only visible when 8D is on
        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sliderPadH),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                com.rajatxo.lutea.playback.Effect8DPreset.entries.forEach { p ->
                    val isSel = p == preset
                    val bg = if (isSel) sliderActiveColor else Color.White.copy(alpha = 0.06f)
                    val fg = if (isSel) Color.Black else Cream.copy(alpha = 0.7f)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickable { onPresetChange(p) }
                            .padding(horizontal = capsulePadHDp, vertical = capsulePadVDp),
                    ) {
                        Text(
                            text = p.label,
                            color = fg,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = subLabelTextSize,
                        )
                    }
                }
            }
        }

        // Row 2 + 3: sliders — only visible when 8D is on
        if (enabled) {
            // Orbit Speed
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sliderPadH),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Orbit Speed",
                        color = Cream,
                        fontFamily = CalSansFamily,
                        fontSize = subLabelTextSize,
                    )
                    Text(
                        text = "${(orbitSpeed * 100f).toInt()}",
                        color = Cream.copy(alpha = 0.6f),
                        fontFamily = CalSansFamily,
                        fontSize = valueTextSize,
                    )
                }
                androidx.compose.material3.Slider(
                    value = orbitSpeed * 100f,
                    onValueChange = { onOrbitSpeedChange(it / 100f) },
                    onValueChangeFinished = onOrbitSpeedFinished,
                    valueRange = 1f..100f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = sliderActiveColor,
                        activeTrackColor = sliderActiveColor,
                        inactiveTrackColor = sliderTrackColor,
                    ),
                )
            }
            // Spatial Ambience
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sliderPadH),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Spatial Ambience",
                        color = Cream,
                        fontFamily = CalSansFamily,
                        fontSize = subLabelTextSize,
                    )
                    Text(
                        text = "%.0f%%".format(spatialAmbience * 100),
                        color = Cream.copy(alpha = 0.6f),
                        fontFamily = CalSansFamily,
                        fontSize = valueTextSize,
                    )
                }
                androidx.compose.material3.Slider(
                    value = spatialAmbience,
                    onValueChange = onSpatialAmbienceChange,
                    onValueChangeFinished = onSpatialAmbienceFinished,
                    valueRange = 0f..1f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = sliderActiveColor,
                        activeTrackColor = sliderActiveColor,
                        inactiveTrackColor = sliderTrackColor,
                    ),
                )
            }

            // Processing indicator — shows when FFmpeg is running on a song
            if (isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sliderPadH),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Processing 8D",
                        color = sliderActiveColor,
                        fontFamily = CalSansFamily,
                        fontSize = subLabelTextSize,
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { processingProgress },
                        modifier = Modifier.weight(1f),
                        color = sliderActiveColor,
                        trackColor = sliderTrackColor,
                    )
                    Text(
                        text = "%.0f%%".format(processingProgress * 100),
                        color = Cream.copy(alpha = 0.6f),
                        fontFamily = CalSansFamily,
                        fontSize = valueTextSize,
                    )
                }
            }

            // 8D ACTIVE indicator — shows when the swap to processed file has
            // happened. If you don't see this AND you don't see Processing, the
            // swap isn't firing — that's the bug.
            if (isActive && !isProcessing) {
                Text(
                    text = "● 8D AUDIO ACTIVE",
                    color = sliderActiveColor,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = subLabelTextSize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sliderPadH),
                )
            }

            // Error indicator — shows the last FFmpeg failure so we can debug
            if (lastError.isNotEmpty() && enabled) {
                Text(
                    text = "8D error: $lastError",
                    color = Color(0xFFFF6B6B),
                    fontFamily = CalSansFamily,
                    fontSize = valueTextSize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sliderPadH),
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

@Composable
private fun SphereComposition(
    modifier: Modifier = Modifier,
    rotationEnabled: Boolean = true,
) {
    val points = remember { fibonacciSphere(180) }
    val infinite = rememberInfiniteTransition(label = "sphere")
    // When 8D is off, the rotation value is forced to 0 — sphere stays still.
    // When on, it spins via the infinite transition. The infinite transition
    // keeps running internally (minor battery cost) but the rendered value is
    // discarded when 8D is off.
    val animRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rot",
    )
    val rotation = if (rotationEnabled) animRotation else 0f
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    val tagPainter = painterResource(R.drawable.ic_effect_tag)

    Canvas(modifier = modifier) {
        // Universal Layout Law #7: sizes are fractions of screen dimensions.
        // User said "make the whole composition smaller" — so we widen the edge
        // gap from 1.11% (4dp) to 8% of screen width. Everything (rings, arrows,
        // tags, labels) scales inward proportionally.
        val edgeGapFrac = 0.08f
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
        // For LEFT/RIGHT: outer edge of label sits at edgeGapPx from screen edge.
        // The tag is positioned so its outer edge sits at label_inner_edge - gap,
        // where label_inner_edge = labelCenterFromCenter - labelHalfWidth.
        // Previously I subtracted only tagSize/2 + 0.008w, forgetting the
        // labelHalfWidth subtraction — so the tag landed ON the label, not beside it.
        val tagToLabelGap = size.width * 0.012f
        val labelCenterFromCenter = size.width / 2f - edgeGapPx - labelHalfWidth
        val tagR = labelCenterFromCenter - labelHalfWidth - tagSize / 2f - tagToLabelGap
        val dottedR = tagR - tagSize / 2f - size.width * 0.008f
        val ringR = dottedR - size.width * 0.012f
        val arrowBaseR = ringR - size.width * 0.045f
        val arrowH = size.width * 0.024f
        val arrowW = size.width * 0.030f
        // Sphere sits inside the arrows with normal breathing room.
        val sphereR = arrowBaseR - size.width * 0.020f

        val cx = size.width / 2f
        // Vertical center sits at half of width so rings are perfectly circular.
        // Composition is top-anchored in its container, so rings render near the top.
        val cy = size.width / 2f

        val thinStroke = size.width * 0.0028f
        val thickStroke = size.width * 0.0080f

        // ── Dotted circle (80 dots) ──
        val dotCount = 80
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

        // ── Tag icons (80, one per dot) ──
        val smallTagSize = size.width * 0.018f
        val tagPainterAlpha = 0.85f
        for (i in 0 until dotCount) {
            val screenAngleRad = (2.0 * Math.PI * i / dotCount).toFloat()
            val compassDeg = (-Math.toDegrees(screenAngleRad.toDouble())).toFloat().mod(360f)
            val tx = cx + dottedR * cos(screenAngleRad)
            val ty = cy + dottedR * sin(screenAngleRad)
            rotate(degrees = -compassDeg, pivot = Offset(tx, ty)) {
                translate(left = tx - smallTagSize / 2f, top = ty - smallTagSize / 2f) {
                    with(tagPainter) {
                        draw(size = Size(smallTagSize, smallTagSize), colorFilter = null, alpha = tagPainterAlpha)
                    }
                }
            }
        }

        // ── Numbers around the circle ──
        // Top semicircle: 25 numbers (1 to 25), LEFT=1 → UP=13 → RIGHT=25
        // Bottom semicircle: 23 numbers (2 to 24), LEFT-below=2 → DOWN=13 → RIGHT-below=24
        // (1 and 25 are shared endpoints at LEFT and RIGHT, so bottom skips them)
        val numberFontSize = size.width * 0.028f
        val numberDistance = smallTagSize / 2f + size.width * 0.030f
        val numberPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(245, 245, 240, 230)
            this.textSize = numberFontSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // Top: 25 numbers from LEFT (π) through UP (3π/2) to RIGHT (2π)
        val topCount = 25
        for (k in 0 until topCount) {
            val num = (k + 1).toString()
            val angleRad = Math.PI.toFloat() + (k.toFloat() * Math.PI.toFloat() / (topCount - 1).toFloat())
            val labelR = dottedR + numberDistance
            val lx = cx + labelR * cos(angleRad)
            val ly = cy + labelR * sin(angleRad)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(num, lx, ly + numberPaint.textSize / 3f, numberPaint)
            }
        }

        // Bottom: 23 numbers (2 to 24) from LEFT-below through DOWN to RIGHT-below
        // Going clockwise: angle from just-below-π through π/2 (DOWN) to just-above-0
        val bottomCount = 23
        for (k in 0 until bottomCount) {
            val num = (k + 2).toString()   // 2 to 24
            // angle goes from π - π/24 (just below LEFT) clockwise to π/24 (just below RIGHT)
            // step = (π - 2*π/24) / 22 = π * (1 - 1/12) / 22 = π * 11/12 / 22 = π * 11 / 264 = π / 24
            // Actually: total arc = π - 2*(π/24) = π * 22/24 = 11π/12
            // step = (11π/12) / 22 = π/24
            // angle(k) = π - π/24 - k * π/24 = π - (k+1) * π/24
            val angleRad = Math.PI.toFloat() - ((k + 1).toFloat() * Math.PI.toFloat() / 24f)
            val labelR = dottedR + numberDistance
            val lx = cx + labelR * cos(angleRad)
            val ly = cy + labelR * sin(angleRad)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(num, lx, ly + numberPaint.textSize / 3f, numberPaint)
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
