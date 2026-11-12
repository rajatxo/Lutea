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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset as TapOffset
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
    // 8D state — slider values are 1..25 Int (matches numbers around the sphere)
    val eightDOn by PlaybackConnection.is8DEnabled.collectAsState()
    val orbitSlider by PlaybackConnection.orbitSlider.collectAsState()
    val ambienceSlider by PlaybackConnection.ambienceSlider.collectAsState()

    // Local UI state
    var expandedView by remember { mutableStateOf(false) }
    var expandedSide by remember { mutableStateOf(0) }  // 0=none, 1=orbit(top), 2=ambience(bottom)
    // Sphere rotation + Ball rotation toggles (persisted)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lutea_8d", android.content.Context.MODE_PRIVATE) }
    var sphereRotationOn by remember {
        mutableStateOf(prefs.getBoolean("sphere_rotation", true))
    }
    var ballRotationOn by remember {
        mutableStateOf(prefs.getBoolean("ball_rotation", true))
    }
    val expandProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expandedView) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 800,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "expandProgress",
    ).value

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
            // Pass expandProgress so the sphere can cross-fade between old (8 labels +
            // LEFT/RIGHT/UP/DOWN text) and new (1-25 top + 2-24 bottom) structures.
            SphereComposition(
                modifier = Modifier.fillMaxSize(),
                rotationEnabled = eightDOn && sphereRotationOn,
                arrowsHighlighted = eightDOn,
                expandProgress = expandProgress,
                expandedSide = expandedSide,
                selectedOrbit = orbitSlider,
                selectedAmbience = ambienceSlider,
                onNumberClick = { num, isTop ->
                    if (isTop) PlaybackConnection.setOrbitSpeed(num)
                    else PlaybackConnection.setSpatialAmbience(num)
                },
            )
        }

        // ── Top row: exactly 4 capsules (Sphere Rotation | 8D Audio | Ball Rotation | Reset) ──
        EffectHeaderRow(
            enabled = eightDOn,
            onToggle = {
                PlaybackConnection.set8DEnabled(it)
                if (!it) { expandedView = false; expandedSide = 0 }
            },
            sphereRotationOn = sphereRotationOn,
            onSphereRotationToggle = {
                sphereRotationOn = it
                prefs.edit().putBoolean("sphere_rotation", it).apply()
            },
            ballRotationOn = ballRotationOn,
            onBallRotationToggle = {
                ballRotationOn = it
                prefs.edit().putBoolean("ball_rotation", it).apply()
            },
            onDefaultClick = {
                PlaybackConnection.set8DPreset(com.rajatxo.lutea.playback.Effect8DPreset.BALANCED)
            },
            screenWidthPx = widthPx,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { (-(widthPx * 0.156f)).toDp() })
                .padding(top = with(density) { (heightPx * 0.005f).toDp() }),
        )

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
            // ── Rounded card behind the head ──
            val cardSideGapDp = with(density) { (widthPx * 0.01f).toDp() }
            val cardWidthDp = with(density) { (widthPx - widthPx * 0.01f * 2).toDp() }
            val cardPadDp = with(density) { (widthPx * 0.04f).toDp() }
            val cardHeightDp = headSizeDp + cardPadDp * 2
            val cardCornerDp = with(density) { (widthPx * 0.05f).toDp() }
            val cardBottomPadDp = with(density) { (heightPx * 0.01f).toDp() }

            Box(
                modifier = Modifier
                    .padding(
                        start = cardSideGapDp,
                        end = cardSideGapDp,
                        bottom = cardBottomPadDp,
                    )
                    .size(cardWidthDp, cardHeightDp)
                    .clip(RoundedCornerShape(cardCornerDp))
                    .drawWithCache {
                        // Gradient: top 25% grey, next 10% transition, bottom 65% → black.
                        // More black at bottom for seamless blend with head image's gradient.
                        val grey = Color(0xFF1A1A1A)
                        val darkerGrey = Color(0xFF0F0F0F)
                        val black = Color(0xFF000000)
                        val brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to grey,
                                0.25f to grey,
                                0.35f to darkerGrey,
                                1.0f to black,
                            )
                        )
                        onDrawBehind {
                            drawRect(brush)
                            // Dot grid
                            val dotSpacing = size.minDimension * 0.04f
                            val dotRadius = size.minDimension * 0.004f
                            val dotColor = Color.White.copy(alpha = 0.06f)
                            var yPos = dotSpacing
                            while (yPos < size.height) {
                                var xPos = dotSpacing
                                while (xPos < size.width) {
                                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(xPos, yPos))
                                    xPos += dotSpacing
                                }
                                yPos += dotSpacing
                            }
                            // Metamorphic particles
                            val centerX = size.width / 2f
                            val centerY = size.height * 0.35f
                            val maxRadius = size.minDimension * 0.35f
                            for (i in 0 until 60) {
                                val angle = (Math.PI * 2 * i / 60).toFloat()
                                val dist = maxRadius * (0.3f + 0.7f * ((i * 0.6180339887f) % 1f))
                                val px = centerX + dist * cos(angle)
                                val py = centerY + dist * sin(angle)
                                val alpha = (1f - dist / maxRadius) * 0.12f
                                if (alpha > 0.01f) {
                                    drawCircle(color = Color.White.copy(alpha = alpha), radius = dotRadius * 1.5f, center = Offset(px, py))
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                // ── Orbit Speed capsule at top-left corner of card ──
                if (eightDOn) {
                    val cornerCapsulePadH = with(density) { (widthPx * 0.018f).toDp() }
                    val cornerCapsulePadV = with(density) { (widthPx * 0.008f).toDp() }
                    val cornerCapsuleCorner = with(density) { (widthPx * 0.025f).toDp() }
                    val cornerTextSize = with(density) { (widthPx * 0.020f).toSp() }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = cornerCapsulePadV, start = cornerCapsulePadH)
                            .clip(RoundedCornerShape(cornerCapsuleCorner))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(
                                width = cornerCapsulePadH / 12f,
                                color = Cream.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(cornerCapsuleCorner),
                            )
                            .clickable {
                                if (expandedView && expandedSide == 1) { expandedView = false; expandedSide = 0 }
                                else { expandedView = true; expandedSide = 1 }
                            }
                            .padding(horizontal = cornerCapsulePadH, vertical = cornerCapsulePadV),
                    ) {
                        Text(
                            text = "Orbit Speed",
                            color = Cream,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = cornerTextSize,
                        )
                    }
                    // ── Spatial Ambience capsule at top-right corner of card ──
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = cornerCapsulePadV, end = cornerCapsulePadH)
                            .clip(RoundedCornerShape(cornerCapsuleCorner))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(
                                width = cornerCapsulePadH / 12f,
                                color = Cream.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(cornerCapsuleCorner),
                            )
                            .clickable {
                                if (expandedView && expandedSide == 2) { expandedView = false; expandedSide = 0 }
                                else { expandedView = true; expandedSide = 2 }
                            }
                            .padding(horizontal = cornerCapsulePadH, vertical = cornerCapsulePadV),
                    ) {
                        Text(
                            text = "Spatial Ambience",
                            color = Cream,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = cornerTextSize,
                        )
                    }
                }

                Box(modifier = Modifier.size(headSizeDp, headSizeDp)) {
                    Image(
                        painter = painterResource(headRes),
                        contentDescription = "Spatial audio head",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // ── Glowing 8D ball that orbits the head ──
                    if (eightDOn && ballRotationOn) {
                        SpatialOrbBall(
                            headSizePx = with(density) { headSizeDp.toPx() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // ── Existing blend-to-black on the head image ──
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
// 8D Audio capsule + toggle + flanking Orbit Speed / Spatial Ambience capsules
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EffectHeaderRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    sphereRotationOn: Boolean,
    onSphereRotationToggle: (Boolean) -> Unit,
    ballRotationOn: Boolean,
    onBallRotationToggle: (Boolean) -> Unit,
    onDefaultClick: () -> Unit,
    screenWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = screenWidthPx
    // 4 capsules must fit horizontally — calculate size accordingly.
    val labelTextSize = with(density) { (widthPx * 0.026f).toSp() }
    val capsulePadHDp = with(density) { (widthPx * 0.020f).toDp() }
    val capsulePadVDp = with(density) { (widthPx * 0.010f).toDp() }
    val capsuleCornerDp = with(density) { (widthPx * 0.03f).toDp() }
    val toggleSizeDp = with(density) { (widthPx * 0.045f).toDp() }
    val sideCapsuleSpacing = with(density) { (widthPx * 0.010f).toDp() }
    val goldColor = Color(0xFFD4A03A)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sideCapsuleSpacing, Alignment.CenterHorizontally),
    ) {
        // 1. Sphere Rotation toggle
        ToggleCapsule(
            label = "Sphere Rotation",
            isOn = sphereRotationOn,
            onToggle = onSphereRotationToggle,
            textSize = labelTextSize,
            toggleSize = toggleSizeDp,
            padH = capsulePadHDp,
            padV = capsulePadVDp,
            corner = capsuleCornerDp,
        )

        // 2. 8D Audio toggle
        ToggleCapsule(
            label = "8D Audio",
            isOn = enabled,
            onToggle = onToggle,
            textSize = labelTextSize,
            toggleSize = toggleSizeDp,
            padH = capsulePadHDp,
            padV = capsulePadVDp,
            corner = capsuleCornerDp,
        )

        // 3. Ball Rotation toggle
        ToggleCapsule(
            label = "Ball Rotation",
            isOn = ballRotationOn,
            onToggle = onBallRotationToggle,
            textSize = labelTextSize,
            toggleSize = toggleSizeDp,
            padH = capsulePadHDp,
            padV = capsulePadVDp,
            corner = capsuleCornerDp,
        )

        // 4. Reset button with circular arrow
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(capsuleCornerDp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(
                    width = capsulePadHDp / 12f,
                    color = Cream.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(capsuleCornerDp),
                )
                .clickable { onDefaultClick() }
                .padding(horizontal = capsulePadHDp, vertical = capsulePadVDp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Reset",
                    color = Cream,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = labelTextSize,
                )
                Spacer(Modifier.width(with(density) { (widthPx * 0.008f).toDp() }))
                Box(modifier = Modifier.size(with(density) { (widthPx * 0.022f).toDp() })) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension * 0.35f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        drawArc(
                            color = goldColor,
                            startAngle = -45f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round),
                        )
                        drawPath(
                            path = Path().apply {
                                val tipX = c.x + r * cos(Math.toRadians(225.0).toFloat())
                                val tipY = c.y + r * sin(Math.toRadians(225.0).toFloat())
                                moveTo(tipX, tipY)
                                lineTo(tipX - size.minDimension * 0.08f, tipY - size.minDimension * 0.04f)
                                lineTo(tipX - size.minDimension * 0.04f, tipY + size.minDimension * 0.08f)
                                close()
                            },
                            color = goldColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleCapsule(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    textSize: androidx.compose.ui.unit.TextUnit,
    toggleSize: androidx.compose.ui.unit.Dp,
    padH: androidx.compose.ui.unit.Dp,
    padV: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
) {
    val goldColor = Color(0xFFD4A03A)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(
                width = padH / 12f,
                color = if (isOn) goldColor.copy(alpha = 0.4f) else Cream.copy(alpha = 0.18f),
                shape = RoundedCornerShape(corner),
            )
            .clickable { onToggle(!isOn) }
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(padH / 3f),
    ) {
        Text(
            text = label,
            color = if (isOn) Cream else Cream.copy(alpha = 0.5f),
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = textSize,
        )
        // Small toggle inside the capsule
        Box(
            modifier = Modifier
                .size(toggleSize, toggleSize * 0.55f)
                .clip(RoundedCornerShape(50))
                .background(if (isOn) goldColor else Color.White.copy(alpha = 0.10f))
                .padding(toggleSize * 0.08f),
            contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(toggleSize * 0.5f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun SideCapsule(
    label: String,
    labelTextSize: androidx.compose.ui.unit.TextUnit,
    padH: androidx.compose.ui.unit.Dp,
    padV: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(
                width = padH / 12f,
                color = Cream.copy(alpha = 0.18f),
                shape = RoundedCornerShape(corner),
            )
            .clickable { onClick() }
            .padding(horizontal = padH, vertical = padV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Cream,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = labelTextSize,
        )
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

// ─────────────────────────────────────────────────────────────────────────────
// Glowing 8D ball — traces head-shaped arc following the LFO phase
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpatialOrbBall(
    headSizePx: Float,
    modifier: Modifier = Modifier,
) {
    var phase by remember { mutableStateOf(0f) }
    // Poll the audio thread's LFO phase every vsync for smooth, jitter-free tracking.
    // withFrameNanos suspends until the next display frame — better than delay(16)
    // which can drift and cause the ball to feel out of sync with the audio.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            phase = com.rajatxo.lutea.playback.LuteaAudioProcessors.spatial8D.currentPhase
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Arc path — hugs the head/headphones shape like a band.
        // Tight radius: stays close to the head, not a wide circle.
        // Ball is always in FRONT or above the head, NEVER behind.
        // orbitW tighter so ball is closer to the ears at the sides.
        // orbitH stays at 0.46 — top gap is perfect per user feedback.
        val orbitW = headSizePx * 0.45f   // half-width: tighter at ear positions
        val orbitH = headSizePx * 0.46f   // height: gap above headphone band (perfect)
        val ballR = headSizePx * 0.035f
        val glowR = ballR * 3.5f

        // Ball X = sin(phase) × orbitW
        // sin < 0 → sound LEFT → user's left ear → SCREEN LEFT ✓
        // sin > 0 → sound RIGHT → user's right ear → SCREEN RIGHT ✓
        val sinP = kotlin.math.sin(phase.toDouble()).toFloat()

        // Ball Y: follow the head shape. The ball should be CLOSE to the head:
        // - At ears (sin = ±1, cos = 0) → ball at ear level (Y ≈ cy)
        // - At center (sin = 0, cos = ±1) → ball just above the headphone band (Y ≈ cy - orbitH)
        // Use |cos|^0.7 for a shape that's rounder than a pure ellipse but
        // still has a flatter top than a circle. Snug fit.
        val cosP = kotlin.math.cos(phase.toDouble()).toFloat()
        val absCos = kotlin.math.abs(cosP)
        val arcShape = Math.pow(absCos.toDouble(), 0.7).toFloat()

        val ballX = cx + sinP * orbitW
        val ballY = cy - arcShape * orbitH

        // Sunset palette
        val deepOrange = Color(0xFFEA6113)
        val midOrange = Color(0xFFF88F22)
        val lightOrange = Color(0xFFFBB931)
        val paleYellow = Color(0xFFFFE3B3)

        // Outer glow — radial gradient fading to transparent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    midOrange.copy(alpha = 0.7f),
                    lightOrange.copy(alpha = 0.35f),
                    paleYellow.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                center = Offset(ballX, ballY),
                radius = glowR,
            ),
            radius = glowR,
            center = Offset(ballX, ballY),
        )

        // Ball core — solid deep orange
        drawCircle(
            color = deepOrange,
            radius = ballR,
            center = Offset(ballX, ballY),
        )

        // Ball highlight — bright spot for 3D look
        drawCircle(
            color = paleYellow.copy(alpha = 0.7f),
            radius = ballR * 0.35f,
            center = Offset(
                ballX - ballR * 0.25f,
                ballY - ballR * 0.25f,
            ),
        )
    }
}

private data class Direction(
    val angleDeg: Float,
    val label: String,
)

@Composable
private fun SphereComposition(
    modifier: Modifier = Modifier,
    rotationEnabled: Boolean = true,
    arrowsHighlighted: Boolean = false,
    expandProgress: Float = 0f,
    expandedSide: Int = 0,
    selectedOrbit: Int = 13,
    selectedAmbience: Int = 13,
    onNumberClick: (Int, Boolean) -> Unit = { _, _ -> },
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

    // OLD look: 8 tag icons at compass positions + LEFT/RIGHT/UP/DOWN text labels.
    // Shown when expandProgress = 0 (default). Cross-fades to the new 1-25/2-24
    // structure as expandProgress → 1.
    val oldTagDirs = remember {
        listOf(
            Direction(0f,   "Plate"),
            Direction(45f,  "Hall"),
            Direction(90f,  "None"),
            Direction(135f, "Chamber"),
            Direction(180f, "Spring"),
            Direction(225f, "Room"),
            Direction(270f, "Stadium"),
            Direction(315f, "Cathedral"),
        )
    }

    val tagPainter = painterResource(R.drawable.ic_effect_tag)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
        // Universal Layout Law #7: sizes are fractions of screen dimensions.
        // User said "make the whole composition smaller" — so we widen the edge
        // gap from 1.11% (4dp) to 8% of screen width. Everything (rings, arrows,
        // tags, labels) scales inward proportionally.
        val edgeGapFrac = 0.08f
        val edgeGapPx = size.width * edgeGapFrac

        // Text size — measured once to size the composition around it.
        // Increased from 0.024 to 0.028 for better readability.
        val labelFontSize = size.width * 0.028f
        // Approximate width of "Spring"/"Plate" — 5-6 chars × ~0.55 × fontSize.
        val labelHalfWidth = labelFontSize * 6f * 0.55f / 2f

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
        // When 8D is on (arrowsHighlighted=true), TOP and BOTTOM arrows turn gold.
        // LEFT and RIGHT stay white (cream) regardless of state.
        val goldColor = Color(0xFFD4A03A)
        val whiteArrowColor = Cream.copy(alpha = 0.95f)
        val topBottomColor = if (arrowsHighlighted) goldColor else whiteArrowColor
        // Top — gold when highlighted
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - arrowBaseR - arrowH)
                lineTo(cx - arrowW / 2, cy - arrowBaseR)
                lineTo(cx + arrowW / 2, cy - arrowBaseR)
                close()
            },
            color = topBottomColor,
        )
        // Bottom — gold when highlighted
        drawPath(
            path = Path().apply {
                moveTo(cx, cy + arrowBaseR + arrowH)
                lineTo(cx - arrowW / 2, cy + arrowBaseR)
                lineTo(cx + arrowW / 2, cy + arrowBaseR)
                close()
            },
            color = topBottomColor,
        )
        // Left — always white
        drawPath(
            path = Path().apply {
                moveTo(cx - arrowBaseR - arrowH, cy)
                lineTo(cx - arrowBaseR, cy - arrowW / 2)
                lineTo(cx - arrowBaseR, cy + arrowW / 2)
                close()
            },
            color = whiteArrowColor,
        )
        // Right — always white
        drawPath(
            path = Path().apply {
                moveTo(cx + arrowBaseR + arrowH, cy)
                lineTo(cx + arrowBaseR, cy - arrowW / 2)
                lineTo(cx + arrowBaseR, cy + arrowW / 2)
                close()
            },
            color = whiteArrowColor,
        )

        // ── Tags + labels: cross-fade between OLD look (8 tags + LEFT/RIGHT/UP/DOWN)
        // and NEW look (80 small tags + 1-25 top + 2-24 bottom) based on expandProgress.
        // 0 = old look fully visible, 1 = new look fully visible, 0.5 = both at 50% alpha.
        // Cool fade-in rotation transition when user taps Orbit Speed / Spatial Ambience.
        val smallTagSize = size.width * 0.018f
        val numberFontSize = size.width * 0.028f
        val numberDistance = smallTagSize / 2f + size.width * 0.030f
        // Sunset palette: EA6113 (deepest) → F88F22 → FBB931 → FFE3B3 (lightest)
        // 13 = deepest, gradually lighter outward.
        val creamArgb = android.graphics.Color.argb(245, 245, 240, 230)
        val palette = intArrayOf(
            0xFFEA6113.toInt(),  // distance 0 (num=13) — deepest
            0xFFF88F22.toInt(),  // distance 1 (num=12,14)
            0xFFFBB931.toInt(),  // distance 2 (num=11,15)
            0xFFFFE3B3.toInt(),  // distance 3 (num=10,16)
            0xFFFFE3B3.toInt(),  // distance 4+ (lighter, will fade alpha)
        )

        // Returns the ARGB color for a number given which side is active.
        // The SELECTED value (selectedOrbit or selectedAmbience) is the center of the
        // sunset gradient — not always 13. User can click any number to re-center.
        fun numColor(num: Int, isActiveSide: Boolean, alpha: Float, selectedValue: Int): Int {
            if (!isActiveSide) {
                return android.graphics.Color.argb((70 * alpha).toInt(), 245, 240, 230)
            }
            val dist = kotlin.math.abs(num - selectedValue)
            val baseColor = palette[dist.coerceIn(0, palette.lastIndex)]
            val baseAlpha = when {
                dist == 0 -> 255
                dist <= 3 -> 245
                dist <= 6 -> 200
                dist <= 9 -> 150
                else -> 100
            }
            return android.graphics.Color.argb(
                (baseAlpha * alpha).toInt(),
                android.graphics.Color.red(baseColor),
                android.graphics.Color.green(baseColor),
                android.graphics.Color.blue(baseColor),
            )
        }
        val numberPaint = android.graphics.Paint().apply {
            color = creamArgb
            this.textSize = numberFontSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // ════════════════════════════════════════════════════════════════════
        // OLD LOOK: 8 tag icons + LEFT/RIGHT/UP/DOWN text labels
        // ════════════════════════════════════════════════════════════════════
        // Alpha = (1 - expandProgress). Fades out as the new structure fades in.
        // Sequential cross-fade: old fades out during 0→0.5, new fades in during 0.5→1.
        // This prevents the visual "breaking" where both old+new are visible at the
        // same time during the transition.
        val oldAlpha = ((0.5f - expandProgress) / 0.5f).coerceIn(0f, 1f)
        if (oldAlpha > 0.01f) {
            val oldTagSize = size.width * 0.028f
            val oldTagAlpha = oldAlpha * 0.92f
            for (dir in oldTagDirs) {
                val angleRad = Math.toRadians(dir.angleDeg.toDouble()).toFloat()
                val tx = cx + tagR * cos(angleRad)
                val ty = cy - tagR * sin(angleRad)
                val rotationDeg = -dir.angleDeg
                rotate(degrees = rotationDeg, pivot = Offset(tx, ty)) {
                    translate(left = tx - oldTagSize / 2f, top = ty - oldTagSize / 2f) {
                        with(tagPainter) {
                            draw(size = Size(oldTagSize, oldTagSize), colorFilter = null, alpha = oldTagAlpha)
                        }
                    }
                }
            }

            // Old direction labels (LEFT/RIGHT/UP/DOWN)
            val oldLabelFontSize = size.width * 0.028f
            val oldPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb((245 * oldAlpha).toInt(), 245, 240, 230)
                this.textSize = oldLabelFontSize
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (dir in oldTagDirs) {
                if (dir.label.isEmpty()) continue
                val angleRad = Math.toRadians(dir.angleDeg.toDouble()).toFloat()
                // Spring/Plate are at compass 180/0 (LEFT/RIGHT). They need to sit
                // outside the tag icons with enough gap for 5-6 char words.
                // Use the same labelCenterFromCenter which accounts for labelHalfWidth.
                val distFromCenter = when (dir.angleDeg) {
                    0f, 180f -> labelCenterFromCenter
                    else -> tagR + oldTagSize / 2f + size.width * 0.020f
                }
                val lx = cx + distFromCenter * cos(angleRad)
                val ly = cy - distFromCenter * sin(angleRad)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(dir.label, lx, ly + oldPaint.textSize / 3f, oldPaint)
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // NEW LOOK: 80 small tag icons + 25 top numbers + 23 bottom numbers
        // ════════════════════════════════════════════════════════════════════
        // Alpha = expandProgress. Fades in as the old look fades out.
        val newAlpha = ((expandProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        if (newAlpha > 0.01f) {
            val newTagAlpha = newAlpha * 0.85f
            // 80 small tag icons — one per dot
            for (i in 0 until dotCount) {
                val screenAngleRad = (2.0 * Math.PI * i / dotCount).toFloat()
                val compassDeg = (-Math.toDegrees(screenAngleRad.toDouble())).toFloat().mod(360f)
                val tx = cx + dottedR * cos(screenAngleRad)
                val ty = cy + dottedR * sin(screenAngleRad)
                rotate(degrees = -compassDeg, pivot = Offset(tx, ty)) {
                    translate(left = tx - smallTagSize / 2f, top = ty - smallTagSize / 2f) {
                        with(tagPainter) {
                            draw(size = Size(smallTagSize, smallTagSize), colorFilter = null, alpha = newTagAlpha)
                        }
                    }
                }
            }

            // Top: 25 numbers from LEFT (π) through UP (3π/2) to RIGHT (2π)
            val topCount = 25
            val topIsActive = expandedSide == 1
            for (k in 0 until topCount) {
                val num = (k + 1)
                val angleRad = Math.PI.toFloat() + (k.toFloat() * Math.PI.toFloat() / (topCount - 1).toFloat())
                val labelR = dottedR + numberDistance
                val lx = cx + labelR * cos(angleRad)
                val ly = cy + labelR * sin(angleRad)
                numberPaint.color = numColor(num, topIsActive, newAlpha, selectedOrbit)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(num.toString(), lx, ly + numberPaint.textSize / 3f, numberPaint)
                }
            }

            // Bottom: 23 numbers (2 to 24)
            val bottomCount = 23
            val bottomIsActive = expandedSide == 2
            for (k in 0 until bottomCount) {
                val num = (k + 2)
                val angleRad = Math.PI.toFloat() - ((k + 1).toFloat() * Math.PI.toFloat() / 24f)
                val labelR = dottedR + numberDistance
                val lx = cx + labelR * cos(angleRad)
                val ly = cy + labelR * sin(angleRad)
                numberPaint.color = numColor(num, bottomIsActive, newAlpha, selectedAmbience)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(num.toString(), lx, ly + numberPaint.textSize / 3f, numberPaint)
                }
            }
        }

        // ── Dot sphere — white dots only, no color sync ──
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

        // Tap overlay for clicking on numbers
        if (expandProgress > 0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(expandedSide) {
                        detectTapGestures { offset: Offset ->
                            val w = size.width.toFloat()
                            val edgeGapPx = w * 0.08f
                            val labelFontSize = w * 0.028f
                            val labelHalfWidth = labelFontSize * 6f * 0.55f / 2f
                            val tagToLabelGap = w * 0.012f
                            val labelCenterFromCenter = w / 2f - edgeGapPx - labelHalfWidth
                            val tagSize = w * 0.028f
                            val tagR = labelCenterFromCenter - labelHalfWidth - tagSize / 2f - tagToLabelGap
                            val dottedR = tagR - tagSize / 2f - w * 0.008f
                            val smallTagSize = w * 0.018f
                            val numberDistance = smallTagSize / 2f + w * 0.030f
                            val cx = w / 2f
                            val cy = w / 2f
                            val labelR = dottedR + numberDistance
                            val tapR = w * 0.04f
                            for (k in 0 until 25) {
                                val num = k + 1
                                val angleRad = Math.PI.toFloat() + (k.toFloat() * Math.PI.toFloat() / 24f)
                                val lx = cx + labelR * cos(angleRad)
                                val ly = cy + labelR * sin(angleRad)
                                if ((offset.x - lx) * (offset.x - lx) + (offset.y - ly) * (offset.y - ly) < tapR * tapR) {
                                    onNumberClick(num, true)
                                    return@detectTapGestures
                                }
                            }
                            for (k in 0 until 23) {
                                val num = k + 2
                                val angleRad = Math.PI.toFloat() - ((k + 1).toFloat() * Math.PI.toFloat() / 24f)
                                val lx = cx + labelR * cos(angleRad)
                                val ly = cy + labelR * sin(angleRad)
                                if ((offset.x - lx) * (offset.x - lx) + (offset.y - ly) * (offset.y - ly) < tapR * tapR) {
                                    onNumberClick(num, false)
                                    return@detectTapGestures
                                }
                            }
                        }
                    },
            )
        }
    }
}
