package com.rajatxo.lutea.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate as drawRotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset as TapOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import com.rajatxo.lutea.R
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import com.rajatxo.lutea.ui.screens.blendStops
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val Cream = Color(0xFFF5F0E6)

/**
 * Effect tab — glowing jellyfish sphere (flowing strands + bell caps) with concentric rings, outward arrows, 8 tag icons
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
    var topMenuOpen by remember { mutableStateOf(false) }
    // Any touch on the sphere toggles 8D immediately. Holding only lets you
    // watch the colour cross-fade — there is no separate hold behaviour.
    var tapTick by remember { mutableStateOf(0) }
    val tapPulse = remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(tapTick) {
        if (tapTick > 0) {
            tapPulse.snapTo(1f)
            tapPulse.animateTo(
                0f,
                androidx.compose.animation.core.tween(
                    520,
                    easing = androidx.compose.animation.core.LinearOutSlowInEasing,
                ),
            )
        }
    }
    val glowLevel = (tapPulse.value * 0.55f).coerceIn(0f, 1f)
    val spinDeg = tapPulse.value * 30f
    // Permanent state colour. Toggling cross-fades it, so turning off reads as
    // blue → red and turning on as red → blue.
    val stateTint = androidx.compose.animation.animateColorAsState(
        targetValue = if (eightDOn) Color(0xFF6E82FF) else Color(0xFFFF3B30),
        // Slow enough that a hold visibly walks blue → red instead of snapping.
        animationSpec = androidx.compose.animation.core.tween(
            900,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "eightDTint",
    ).value

    // Sphere rotation + Ball rotation toggles (persisted)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lutea_8d", android.content.Context.MODE_PRIVATE) }
    var sphereRotationOn by remember {
        mutableStateOf(prefs.getBoolean("sphere_rotation", true))
    }
    var ballRotationOn by remember {
        mutableStateOf(prefs.getBoolean("ball_rotation", true))
    }
    // Which sphere the Effect page draws. Glass bubble is the default.
    var sphereStyle by remember {
        mutableStateOf(
            when (prefs.getString("sphere_style", "glass")) {
                "jelly" -> SphereStyle.JELLY
                "metamorphic" -> SphereStyle.METAMORPHIC
                else -> SphereStyle.GLASS
            }
        )
    }
    val expandProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expandedView) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 800,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "expandProgress",
    ).value

    Box(modifier = modifier.fillMaxSize()) {
    BoxWithConstraints(
        modifier = Modifier
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
        // Bottom inset shared by the card and the head layer.
        val cardBottomPadDp = with(density) { (heightPx * 0.01f).toDp() }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // ── Rounded card behind the head ──
            val cardSideGapDp = with(density) { (widthPx * 0.01f).toDp() }
            val cardWidthDp = with(density) { (widthPx - widthPx * 0.01f * 2).toDp() }
            val cardPadDp = with(density) { (widthPx * 0.04f).toDp() }
            // Reach up behind the lower third of the sphere, so the dotted grid
            // runs under the sphere and disappears into a black panel.
            val sphereOverlapDp = compositionHeightDp * 0.33f
            val cardHeightDp = headSizeDp + cardPadDp * 2 + sphereOverlapDp
            val cardCornerDp = with(density) { (widthPx * 0.05f).toDp() }
            // Same curvature as the side bars: flare depth = bar height x 0.27,
            // bar height = 46% of the screen width.
            val cardTopCornerDp = with(density) { (widthPx * 0.46f * 0.27f).toDp() }
            Box(
                modifier = Modifier
                    .padding(
                        start = cardSideGapDp,
                        end = cardSideGapDp,
                        bottom = cardBottomPadDp,
                    )
                    .size(cardWidthDp, cardHeightDp)
                    .clip(
                        RoundedCornerShape(
                            topStart = cardTopCornerDp,
                            topEnd = cardTopCornerDp,
                            bottomStart = cardCornerDp,
                            bottomEnd = cardCornerDp,
                        )
                    )
                    .drawWithCache {
                        // Gradient: top 25% grey, next 10% transition, bottom 65% → black.
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
            }
        }
        // ── Solid black plate behind the whole sphere composition ──
        // It sits ON TOP of the gradient/dot card, so the sphere never shows the
        // grid behind it. It never reaches the two edge panels either: the bottom
        // edge stops just above them, and the bottom corners are rounded to the
        // same radius as a panel's width, so the plate slots into the canal the
        // side bars carve out of the card.
        // The bottom edge must sit on the line where the side panels start:
        //   panelHeight = 0.46w, panelCentre = 0.62h  ->  panelTop = 0.62h - 0.23w
        // ★ The bars are laid out against the FULL screen height, but this plate
        //   lives inside the inset-padded box (which starts below the header and
        //   ends above the tabs). Converting the line into this box's coordinates
        //   is what closes the gap that used to be between plate and bars.
        val windowInsets = LocalLuteaWindowInsets.current
        val topInsetPx = windowInsets.getTop(density).toFloat()
        val bottomInsetPx = windowInsets.getBottom(density).toFloat()
        val fullHeightPx = heightPx + topInsetPx + bottomInsetPx
        val barTopOnScreenPx = fullHeightPx * 0.62f - (widthPx * 0.46f) / 2f
        // Nudge past the line, so the plate tucks slightly into the canal.
        val plateExtraDrop = widthPx * 0.025f
        val plateBottomPx = barTopOnScreenPx - topInsetPx + plateExtraDrop
        val plateCorner = with(density) { (widthPx * 0.140f).toDp() }
        val plateHeight = with(density) { plateBottomPx.toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(plateHeight.coerceAtLeast(0.dp))
                .clip(
                    RoundedCornerShape(
                        bottomStart = plateCorner,
                        bottomEnd = plateCorner,
                    )
                )
                .background(Color.Black),
        )

        // ── Head image — above the plate, so expanding the plate never hides it ──
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = cardBottomPadDp)
                    .size(headSizeDp, headSizeDp),
            ) {
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
                        headRes = headRes,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // ── Blend-to-black on the head image ──
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
                glow = glowLevel,
                spinDeg = spinDeg,
                tint = stateTint,
                sphereStyle = sphereStyle,
                onQuickTap = {
                    tapTick++
                    val next = !eightDOn
                    PlaybackConnection.set8DEnabled(next)
                    if (!next) { expandedView = false; expandedSide = 0 }
                },
            )
        }

    }

        // ── Top bar: title + menu dots on one line ──
        // Outside the window insets, so the sphere, head and card never move.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val barWidth = maxWidth
            val startPad = 24.dp
            val endPad = 20.dp
            val gap = 8.dp
            val dotsSize = 40.dp
            val titleText = "8D : Spatial Audio"
            val titleStyle = TextStyle(
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 37.sp,
            )
            // Playlist title size (37sp), shrunk only if it would not fit on one line.
            val measurer = rememberTextMeasurer()
            val titleWidthPx = with(density) { measurer.measure(titleText, titleStyle).size.width.toFloat() }
            val availablePx = with(density) {
                (barWidth - startPad - endPad - dotsSize - gap).toPx()
            }
            val titleSp =
                if (titleWidthPx > 0f && titleWidthPx > availablePx) 37f * (availablePx / titleWidthPx)
                else 37f

            val capsuleTextSize = with(density) { (barWidth.toPx() * 0.023f).toSp() }
            val capsuleCorner = with(density) { (barWidth.toPx() * 0.03f).toDp() }
            val capsulePadH = with(density) { (barWidth.toPx() * 0.014f).toDp() }
            val capsulePadV = with(density) { (barWidth.toPx() * 0.012f).toDp() }

            // ── Edge panels: Orbit Speed (left), Spatial Ambience (right) ──
            val panelW = barWidth * 0.108f
            val panelH = barWidth * 0.46f
            // Vertical centre of the tabs, as a fraction of the screen height.
            val panelTop = maxHeight * 0.62f - panelH / 2f
            val panelText = with(density) { (barWidth.toPx() * 0.021f).toSp() }
            EffectSidePanel(
                label = "Orbit Speed",
                isOn = expandedView && expandedSide == 1,
                onToggle = {
                    if (expandedView && expandedSide == 1) { expandedView = false; expandedSide = 0 }
                    else { expandedView = true; expandedSide = 1 }
                },
                width = panelW,
                height = panelH,
                isLeft = true,
                textSize = panelText,
                modifier = Modifier.align(Alignment.TopStart).offset(y = panelTop),
            )
            EffectSidePanel(
                label = "Spatial Ambience",
                isOn = expandedView && expandedSide == 2,
                onToggle = {
                    if (expandedView && expandedSide == 2) { expandedView = false; expandedSide = 0 }
                    else { expandedView = true; expandedSide = 2 }
                },
                width = panelW,
                height = panelH,
                isLeft = false,
                textSize = panelText,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = panelTop),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = startPad, end = endPad, top = 16.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSp.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.weight(1f))
                    // Same 4-dot menu button as the playlist page, on the title line.
                    Box(
                        modifier = Modifier
                            .size(dotsSize)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { topMenuOpen = !topMenuOpen },
                    ) {
                        // Active indicator for 8D: the four dots fill green while the
                        // sphere is ON, and stay plain white while it is OFF (no red).
                        val menuActiveGreen = Color(0xFF3DDC84)
                        listOf(9.dp to 9.dp, 24.dp to 9.dp, 9.dp to 24.dp, 24.dp to 24.dp)
                            .forEach { (dx, dy) ->
                                Box(
                                    modifier = Modifier
                                        .offset(dx, dy)
                                        .size(8.dp)
                                        .then(
                                            if (eightDOn) {
                                                Modifier.background(menuActiveGreen, CircleShape)
                                            } else {
                                                Modifier.border(1.5.dp, Color.White, CircleShape)
                                            }
                                        ),
                                )
                            }
                    }
                }

                // ── Menu: three capsules under the title, fitted to the screen ──
                if (topMenuOpen) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EffectActionCapsule(
                            label = "Sphere Rotation",
                            active = sphereRotationOn,
                            textSize = capsuleTextSize,
                            corner = capsuleCorner,
                            padH = capsulePadH,
                            padV = capsulePadV,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                sphereRotationOn = !sphereRotationOn
                                prefs.edit().putBoolean("sphere_rotation", sphereRotationOn).apply()
                            },
                        )
                        EffectActionCapsule(
                            label = "Ball Rotation",
                            active = ballRotationOn,
                            textSize = capsuleTextSize,
                            corner = capsuleCorner,
                            padH = capsulePadH,
                            padV = capsulePadV,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                ballRotationOn = !ballRotationOn
                                prefs.edit().putBoolean("ball_rotation", ballRotationOn).apply()
                            },
                        )
                        EffectActionCapsule(
                            label = "Reset Value",
                            active = true,
                            textSize = capsuleTextSize,
                            corner = capsuleCorner,
                            padH = capsulePadH,
                            padV = capsulePadV,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                PlaybackConnection.set8DPreset(com.rajatxo.lutea.playback.Effect8DPreset.BALANCED)
                                expandedView = false
                                expandedSide = 0
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EffectActionCapsule(
                            label = "Metamorphic Sphere",
                            active = sphereStyle == SphereStyle.METAMORPHIC,
                            textSize = capsuleTextSize,
                            corner = capsuleCorner,
                            padH = capsulePadH,
                            padV = capsulePadV,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                sphereStyle = SphereStyle.METAMORPHIC
                                prefs.edit().putString("sphere_style", "metamorphic").apply()
                            },
                        )
                        EffectActionCapsule(
                            label = "Ocean Jelly",
                            active = sphereStyle == SphereStyle.JELLY,
                            textSize = capsuleTextSize,
                            corner = capsuleCorner,
                            padH = capsulePadH,
                            padV = capsulePadV,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                sphereStyle = SphereStyle.JELLY
                                prefs.edit().putString("sphere_style", "jelly").apply()
                            },
                        )
                        EffectActionCapsule(
                            label = "Glass Bubble",
                            active = sphereStyle == SphereStyle.GLASS,
                            textSize = capsuleTextSize,
                            corner = capsuleCorner,
                            padH = capsulePadH,
                            padV = capsulePadV,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                sphereStyle = SphereStyle.GLASS
                                prefs.edit().putString("sphere_style", "glass").apply()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Edge-attached pill, like the reference: the screen edge flows into the bar through a smooth
 * S-curve (concave where it leaves the edge, convex where it becomes the body), a straight
 * inner side, then the same S-curve back to the edge. Mirrored for the right side.
 * The shape's box = the full bar including both flares, so size/position stay as before.
 */
private class SideAttachShape(
    private val isLeft: Boolean,
    private val flareFrac: Float = 0.27f,   // flare height as a fraction of the bar height
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val f = h * flareFrac
        val path = Path().apply {
            if (isLeft) {
                moveTo(0f, 0f)
                cubicTo(0f, f * 0.50f, w, f * 0.50f, w, f)
                lineTo(w, h - f)
                cubicTo(w, h - f * 0.50f, 0f, h - f * 0.50f, 0f, h)
                close()
            } else {
                moveTo(w, 0f)
                cubicTo(w, f * 0.50f, 0f, f * 0.50f, 0f, f)
                lineTo(0f, h - f)
                cubicTo(0f, h - f * 0.50f, w, h - f * 0.50f, w, h)
                close()
            }
        }
        return Outline.Generic(path)
    }
}

/** Chevron drawn with two strokes (no icon dependency). */
@Composable
private fun SideChevron(
    up: Boolean,
    enabled: Boolean,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width, width * 0.55f)) {
        val sw = size.width * 0.14f
        val path = Path().apply {
            if (up) {
                moveTo(sw, size.height - sw)
                lineTo(size.width / 2f, sw)
                lineTo(size.width - sw, size.height - sw)
            } else {
                moveTo(sw, sw)
                lineTo(size.width / 2f, size.height - sw)
                lineTo(size.width - sw, sw)
            }
        }
        drawPath(
            path = path,
            color = Color.Black.copy(alpha = if (enabled) 0.85f else 0.25f),
            style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

/**
 * Side bar attached to the screen edge:
 *   [ chevron up ]
 *   [ white capsule with the label ]
 *   [ chevron down ]
 * Swipe up / down on the bar steps through [labels]. The chevrons sit outside the capsule but
 * inside the bar.
 */
@Composable
private fun EffectSidePanel(
    label: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    isLeft: Boolean,
    textSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    labels: List<String> = listOf(label),
    onStep: (index: Int) -> Unit = {},
) {
    val shape = remember(isLeft) { SideAttachShape(isLeft) }
    var index by remember { mutableStateOf(0) }
    val safeIndex = index.coerceIn(0, (labels.size - 1).coerceAtLeast(0))
    val canUp = safeIndex < labels.size - 1      // swipe up   -> next capsule
    val canDown = safeIndex > 0                  // swipe down -> previous capsule

    var dragSum by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.85f))
            .pointerInput(labels.size, safeIndex) {
                detectVerticalDragGestures(
                    onDragStart = { dragSum = 0f },
                    onVerticalDrag = { _, dy -> dragSum += dy },
                    onDragEnd = {
                        val threshold = 24.dp.toPx()
                        if (dragSum <= -threshold && canUp) { index = safeIndex + 1; onStep(index) }
                        else if (dragSum >= threshold && canDown) { index = safeIndex - 1; onStep(index) }
                        dragSum = 0f
                    },
                    onDragCancel = { dragSum = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = height * 0.20f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(height * 0.03f),
        ) {
            SideChevron(up = true, enabled = canUp, width = width * 0.34f)

            // White capsule
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(width * 0.74f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                // Text runs along the capsule: bottom-to-top on the left, top-to-bottom on the right.
                Box(
                    modifier = Modifier
                        .drawRotate(if (isLeft) -90f else 90f)
                        .requiredWidth(height * 0.44f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labels.getOrElse(safeIndex) { label },
                        color = Color.White.copy(alpha = if (isOn) 1f else 0.75f),
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = textSize,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            SideChevron(up = false, enabled = canDown, width = width * 0.34f)
        }
    }
}

@Composable
private fun EffectActionCapsule(
    label: String,
    active: Boolean,
    textSize: androidx.compose.ui.unit.TextUnit,
    corner: androidx.compose.ui.unit.Dp,
    padH: androidx.compose.ui.unit.Dp,
    padV: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onClick() }
            .padding(horizontal = padH, vertical = padV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.45f),
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = textSize,
            maxLines = 1,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Corner capsule on the head card: label + inline switch, same corner radius
// as the card behind it.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EffectCornerCapsule(
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
    textSize: androidx.compose.ui.unit.TextUnit,
    switchWidth: androidx.compose.ui.unit.Dp,
    padH: androidx.compose.ui.unit.Dp,
    padV: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val goldColor = Color(0xFFD4A03A)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onClick() }
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(padH / 2f),
    ) {
        Text(
            text = label,
            color = if (isOn) Cream else Cream.copy(alpha = 0.5f),
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = textSize,
        )
        // Switch — shows/hides the numbers around the sphere for this effect.
        Box(
            modifier = Modifier
                .size(switchWidth, switchWidth * 0.55f)
                .clip(RoundedCornerShape(50))
                .background(if (isOn) goldColor else Color.White.copy(alpha = 0.10f))
                .clickable { onClick() }
                .padding(switchWidth * 0.08f),
            contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(switchWidth * 0.5f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White),
            )
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
                    color = Color.White.copy(alpha = 0.20f),
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
                color = if (isOn) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.20f),
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
                color = Color.White.copy(alpha = 0.20f),
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
                        color = Color.White.copy(alpha = 0.20f),
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

// ─────────────────────────────────────────────────────────────────────────────
// Metamorphic sphere — the original white dot sphere
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
// Coral jellyfish sphere
//
// A glowing bioluminescent jellyfish that replaces the old dot sphere:
//   • ~300 hair-thin strands that flow from a bright, elongated core out to the rim.
//     Neighbouring strands sway together (coherent bundles), a slow wave travels
//     outward along them, and a swirl twists them near the core.
//   • every "capped" strand ends in a small white half-disc (the jellyfish bell /
//     umbrella) that faces outward and swings a little.
//   • a translucent periwinkle band of bigger half-discs sits just inside the caps.
//   • tiny twinkling squares float around it (a few red / teal accents).
//   • the whole BODY moves like a real jelly — it breathes in and out, its outline
//     swells into soft lobes and relaxes again, and it drifts a little — but the
//     outline always stays a circle and it NEVER rotates. The lobes are standing
//     waves (they swell and fade in place instead of travelling round the ring).
//   • everything is tinted by [tint], so the blue ⇄ red 8D state colour
//     cross-fades through the whole creature.
//
// All motion is built from INTEGER harmonics of one looping phase (`flow`), so the
// 15 s animation loops perfectly with no jump. Nothing allocates per strand.
// ─────────────────────────────────────────────────────────────────────────────

private const val JELLY_SAMPLES = 14
private const val TWO_PI_F = 6.2831855f
private const val RAD_TO_DEG = 57.29578f

private class JellyField(seed: Int = 11) {
    val capCount = 96
    val fiberCount = 220
    val total = capCount + fiberCount

    // Strands: caps first, then loose fibres.
    val theta = FloatArray(total)
    val rEnd = FloatArray(total)
    val phase = FloatArray(total)
    val indiv = FloatArray(total)
    val alpha = FloatArray(total)
    val widthIdx = IntArray(total)
    // Fixed (non-moving) angular patterns: which sectors sway, and how the bundles twist.
    val amp1 = FloatArray(total)
    val amp2 = FloatArray(total)
    val amp3 = FloatArray(total)
    val swA = FloatArray(total)
    val swB = FloatArray(total)
    val swC = FloatArray(total)

    // Caps (one per capped strand), recomputed each frame.
    val capSize = FloatArray(capCount)
    val capTiltPhase = FloatArray(capCount)
    val capTiltAmp = FloatArray(capCount)
    val capAlpha = FloatArray(capCount)
    val capX = FloatArray(capCount)
    val capY = FloatArray(capCount)
    val capAng = FloatArray(capCount)

    // Per-sample lookup tables (t = 0 at the core, 1 at the rim).
    val radial = FloatArray(JELLY_SAMPLES)
    val env = FloatArray(JELLY_SAMPLES)
    val swirlW = FloatArray(JELLY_SAMPLES)
    val lam = FloatArray(JELLY_SAMPLES)
    val tj = FloatArray(JELLY_SAMPLES)
    // Per-frame radial ripple (shared by all strands): travels ALONG the strand, never round the ring.
    val rf1 = FloatArray(JELLY_SAMPLES)
    val rf2 = FloatArray(JELLY_SAMPLES)
    val rf3 = FloatArray(JELLY_SAMPLES)
    val cB3 = FloatArray(JELLY_SAMPLES)
    val sB3 = FloatArray(JELLY_SAMPLES)
    val cB4 = FloatArray(JELLY_SAMPLES)
    val sB4 = FloatArray(JELLY_SAMPLES)

    // Periwinkle band clusters.
    val bandN = 12
    val bandBase = FloatArray(bandN)
    val bandCount = IntArray(bandN)
    val bandPh = FloatArray(bandN)
    val bandSize = FloatArray(bandN)

    // Floating square particles.
    val pn = 70
    val pA = FloatArray(pn)
    val pR = FloatArray(pn)
    val pS = FloatArray(pn)
    val pPh = FloatArray(pn)
    val pK = IntArray(pn)
    val pKind = IntArray(pn)

    // Scratch, reused every frame.
    val xs = FloatArray(JELLY_SAMPLES)
    val ys = FloatArray(JELLY_SAMPLES)
    val path = Path()

    init {
        val rnd = Random(seed)

        for (i in 0 until capCount) {
            theta[i] = TWO_PI_F * (i + (rnd.nextFloat() - 0.5f) * 0.9f) / capCount
            rEnd[i] = 0.93f + (rnd.nextFloat() - 0.5f) * 0.09f
        }
        for (i in capCount until total) {
            theta[i] = rnd.nextFloat() * TWO_PI_F
            rEnd[i] = 0.78f + rnd.nextFloat() * 0.20f
        }
        for (i in 0 until total) {
            phase[i] = rnd.nextFloat() * TWO_PI_F
            indiv[i] = 0.4f + rnd.nextFloat() * 0.9f
            alpha[i] =
                if (i < capCount) 0.70f + rnd.nextFloat() * 0.28f
                else 0.50f + rnd.nextFloat() * 0.35f
            widthIdx[i] = rnd.nextInt(3)
        }
        for (i in 0 until total) {
            val th = theta[i]
            amp1[i] = sin(3f * th + 0.4f)
            amp2[i] = sin(5f * th + 1.9f)
            amp3[i] = sin(8f * th + 0.8f)
            swA[i] = sin(2f * th + 0.7f)
            swB[i] = sin(3f * th - 0.8f)
            swC[i] = sin(3f * th + 0.2f)
        }
        for (i in 0 until capCount) {
            capSize[i] = 0.032f * (0.8f + rnd.nextFloat() * 0.4f)
            capTiltPhase[i] = rnd.nextFloat() * TWO_PI_F
            capTiltAmp[i] = if (rnd.nextFloat() < 0.12f) 0.7f else 0.12f + rnd.nextFloat() * 0.18f
            capAlpha[i] = 0.82f + rnd.nextFloat() * 0.18f
        }

        for (j in 0 until JELLY_SAMPLES) {
            val t = j / (JELLY_SAMPLES - 1).toFloat()
            radial[j] = 0.02f + 0.98f * t.pow(0.85f)
            // max(0) guards the float rounding of sin(pi) at t = 1 (tiny negative → NaN in pow).
            env[j] = maxOf(0f, sin(3.1415927f * t)).pow(1.2f)
            swirlW[j] = (1f - t).pow(1.5f)
            val u = (t / 0.4f).coerceIn(0f, 1f)
            lam[j] = u * u * (3f - 2f * u)
            tj[j] = t
            cB3[j] = cos(13.0f * t); sB3[j] = sin(13.0f * t)
            cB4[j] = cos(23.0f * t); sB4[j] = sin(23.0f * t)
        }

        for (c in 0 until bandN) {
            bandBase[c] = (c + rnd.nextFloat() * 0.6f) * TWO_PI_F / bandN
            bandCount[c] = 5 + rnd.nextInt(5)
            bandPh[c] = rnd.nextFloat() * TWO_PI_F
            bandSize[c] = 0.055f + rnd.nextFloat() * 0.02f
        }

        for (k in 0 until pn) {
            pA[k] = rnd.nextFloat() * TWO_PI_F
            pR[k] = 0.12f + sqrt(rnd.nextFloat()) * 0.94f
            pS[k] = 0.007f + rnd.nextFloat() * 0.011f
            pPh[k] = rnd.nextFloat() * TWO_PI_F
            pK[k] = 1 + rnd.nextInt(4)
            val kind = rnd.nextFloat()
            pKind[k] = if (kind < 0.08f) 1 else if (kind < 0.15f) 2 else 0
        }
    }
}

private fun jmix(a: Color, b: Color, t: Float): Color =
    androidx.compose.ui.graphics.lerp(a, b, t)

/** Half-disc whose flat side passes through (x, y) and whose dome faces direction [ang] (radians). */
private fun DrawScope.drawHalfDisc(x: Float, y: Float, ang: Float, radius: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = ang * RAD_TO_DEG - 90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(x - radius, y - radius),
        size = Size(radius * 2f, radius * 2f),
    )
}

/**
 * @param baseX,baseY centre of the creature in px
 * @param rad    radius of the cap ring in px
 * @param flow   looping phase 0..2π (15 s)
 * @param breath -1..1, gentle core breathing
 * @param glow   0..0.55 tap burst
 * @param tint   8D state colour (blue / red)
 */
private fun DrawScope.drawJellyfish(
    f: JellyField,
    baseX: Float,
    baseY: Float,
    rad: Float,
    flow: Float,
    breath: Float,
    glow: Float,
    tint: Color,
) {
    val white = Color.White
    val strandCol = jmix(white, tint, 0.38f)
    val capCol = jmix(white, tint, 0.10f)
    val bandCol = jmix(tint, white, 0.28f)
    val haloCol = jmix(tint, white, 0.08f)
    val coreCol = jmix(white, tint, 0.18f)

    // ── Body motion ──────────────────────────────────────────────────────────
    // The whole jelly drifts a hair around its centre…
    val cx = baseX + 0.016f * rad * sin(2f * flow + 0.9f) + 0.008f * rad * sin(3f * flow)
    val cy = baseY + 0.014f * rad * sin(3f * flow + 0.2f) + 0.007f * rad * sin(flow + 1.0f)
    // …and its outline breathes + swells into soft lobes. Every term is a STANDING
    // wave: sin(time) × cos(m·angle). Lobes grow and fade in place, never travel
    // round the ring, so nothing spins and the shape keeps coming back to a circle.
    val tB = 0.050f * sin(flow + 0.3f) + 0.018f * sin(2f * flow + 1.7f)
    val t2 = 0.042f * sin(2f * flow + 0.4f)
    val t3 = 0.032f * sin(3f * flow + 1.1f)
    val t4 = 0.022f * sin(4f * flow + 2.0f)
    val t5 = 0.016f * sin(5f * flow + 0.2f)
    fun bodyMod(a: Float): Float =
        tB + t2 * cos(2f * a - 0.6f) + t3 * cos(3f * a + 0.9f) +
            t4 * cos(4f * a - 0.3f) + t5 * cos(5f * a + 1.4f)

    val c0 = Offset(cx, cy)
    val burst = glow / 0.55f   // 1 → 0 over the tap pulse

    // ── 1. Halo + interior wash ──
    val g = 1f + glow * 1.6f
    val haloR = rad * 1.08f
    drawCircle(
        brush = Brush.radialGradient(
            0f to haloCol.copy(alpha = (0.10f * g).coerceAtMost(0.9f)),
            0.509f to haloCol.copy(alpha = (0.13f * g).coerceAtMost(0.9f)),
            0.898f to haloCol.copy(alpha = (0.32f * g).coerceAtMost(0.95f)),
            1f to haloCol.copy(alpha = 0f),
            center = c0,
            radius = haloR,
        ),
        radius = haloR,
        center = c0,
    )

    // ── 2. Periwinkle band of big translucent half-discs, just inside the caps ──
    for (c in 0 until f.bandN) {
        for (k in 0 until f.bandCount[c]) {
            val ang = f.bandBase[c] + 0.12f * sin(2f * flow + f.bandPh[c]) + k * 0.05f
            val rr = (0.865f + 0.02f * sin(3f * flow + f.bandPh[c] + k * 0.7f)) * rad * (1f + bodyMod(ang))
            val sw = sin(k * 1.7f + f.bandPh[c])
            val sz = f.bandSize[c] * rad * (0.85f + 0.3f * sw * sw)
            drawHalfDisc(cx + rr * cos(ang), cy + rr * sin(ang), ang, sz, bandCol.copy(alpha = 0.5f))
        }
    }

    // ── 3. Strands ──
    val unit = 0.0042f * rad
    val widths = floatArrayOf(0.75f, 1f, 1.3f)
    val capStrokes = Array(3) { Stroke(width = maxOf(0.6f, unit * widths[it]), cap = StrokeCap.Round) }
    val fiberStrokes = Array(3) { Stroke(width = maxOf(0.5f, unit * widths[it] * 0.72f), cap = StrokeCap.Round) }

    // Elongated, slowly turning core: strands start on this ellipse and fan out radially.
    val e = 0.5f + 0.5f * sin(3f * flow)
    val aCore = 1f + 0.9f * sin(2f * flow + 0.3f)
    val ca = cos(aCore)
    val sa = sin(aCore)
    val ea = 0.085f * rad * (1f + 0.7f * e) * (1f + 0.5f * glow)
    val eb = 0.085f * rad / (1f + 0.7f * e)
    val gl = 1f + glow * 0.7f

    val xs = f.xs
    val ys = f.ys
    val path = f.path
    val n = JELLY_SAMPLES

    // Ripple + twist are STANDING in the angular direction (fixed sectors that swell and
    // relax) and only travel outward along the strands — so the strand field never circulates.
    for (j in 0 until n) {
        val tj = f.tj[j]
        f.rf1[j] = sin(4.2f * tj - 3f * flow + 0.7f)
        f.rf2[j] = sin(6.1f * tj + 2f * flow + 0.3f)
        f.rf3[j] = sin(9.0f * tj - 4f * flow + 1.4f)
    }
    val swT1 = sin(2f * flow + 0.9f)
    val swT2 = sin(flow + 2.1f)

    for (s in 0 until f.total) {
        val th = f.theta[s]
        val ring = bodyMod(th)
        val rEff = f.rEnd[s] * rad * (1f + ring) * (1f + 0.03f * glow)

        val a3 = f.phase[s] + 5f * flow
        val a4 = 1.7f * f.phase[s] + 4f * flow
        val sA3 = sin(a3); val cA3 = cos(a3)
        val sA4 = sin(a4); val cA4 = cos(a4)
        val swirl = 0.30f * f.swC[s] + 0.45f * f.swA[s] * swT1 + 0.30f * f.swB[s] * swT2
        val amp1 = f.amp1[s]
        val amp2 = f.amp2[s]
        val amp3 = f.amp3[s]
        val indiv = f.indiv[s]

        val cth = cos(th)
        val sth = sin(th)
        val startX = cx + ea * cth * ca - eb * sth * sa
        val startY = cy + ea * cth * sa + eb * sth * ca

        for (j in 0 until n) {
            // Individual ripple: sin(A + B) via angle addition (per-strand part computed once above).
            val s3 = sA3 * f.cB3[j] + cA3 * f.sB3[j]
            val s4 = sA4 * f.cB4[j] + cA4 * f.sB4[j]
            val wob = f.env[j] * (0.17f * amp1 * f.rf1[j] + 0.11f * amp2 * f.rf2[j] + 0.08f * amp3 * f.rf3[j] +
            0.07f * indiv * s3 + 0.035f * indiv * s4)
            val phi = th + swirl * f.swirlW[j] + wob
            val r = rEff * f.radial[j]
            val qx = cx + r * cos(phi)
            val qy = cy + r * sin(phi)
            val l = f.lam[j]
            xs[j] = startX + l * (qx - startX)
            ys[j] = startY + l * (qy - startY)
        }

        // Catmull-Rom spline through the samples, as cubic Béziers.
        path.reset()
        path.moveTo(xs[0], ys[0])
        for (j in 0 until n - 1) {
            val i0 = if (j > 0) j - 1 else 0
            val i3 = if (j + 2 < n) j + 2 else n - 1
            path.cubicTo(
                xs[j] + (xs[j + 1] - xs[i0]) / 6f, ys[j] + (ys[j + 1] - ys[i0]) / 6f,
                xs[j + 1] - (xs[i3] - xs[j]) / 6f, ys[j + 1] - (ys[i3] - ys[j]) / 6f,
                xs[j + 1], ys[j + 1],
            )
        }

        val isCap = s < f.capCount
        drawPath(
            path = path,
            color = strandCol,
            alpha = minOf(1f, f.alpha[s] * gl),
            style = if (isCap) capStrokes[f.widthIdx[s]] else fiberStrokes[f.widthIdx[s]],
        )

        if (isCap) {
            // The strand ends exactly at the flat side of its cap.
            val phe = th
            f.capX[s] = cx + rEff * cos(phe)
            f.capY[s] = cy + rEff * sin(phe)
            f.capAng[s] = phe + f.capTiltAmp[s] * sin(6f * flow + f.capTiltPhase[s])
        }
    }

    // ── 4. Caps: white half-discs, dome facing outward ──
    for (s in 0 until f.capCount) {
        drawHalfDisc(
            f.capX[s], f.capY[s], f.capAng[s],
            f.capSize[s] * rad,
            capCol.copy(alpha = f.capAlpha[s]),
        )
    }

    // ── 5. Core glow (elongated along the core axis) ──
    val cb = 1f + 0.12f * breath
    val elong = 1f + 0.7f * e
    val coreR = rad * 0.46f * cb * (1f + glow * 0.8f)
    val hotR = rad * 0.15f * cb
    rotate(degrees = aCore * RAD_TO_DEG, pivot = c0) {
        scale(scaleX = elong, scaleY = 1f / elong, pivot = c0) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to coreCol.copy(alpha = 1f),
                    0.12f to coreCol.copy(alpha = 0.92f),
                    0.35f to coreCol.copy(alpha = 0.55f),
                    0.7f to coreCol.copy(alpha = 0.18f),
                    1f to coreCol.copy(alpha = 0f),
                    center = c0,
                    radius = coreR,
                ),
                radius = coreR,
                center = c0,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to white.copy(alpha = 1f),
                    0.6f to white.copy(alpha = 0.85f),
                    1f to white.copy(alpha = 0f),
                    center = c0,
                    radius = hotR,
                ),
                radius = hotR,
                center = c0,
            )
        }
    }

    // ── 6. Tap burst: a ring of light that runs from the core out to the rim ──
    if (burst > 0.02f) {
        drawCircle(
            color = white.copy(alpha = (burst * 0.55f).coerceIn(0f, 1f)),
            radius = rad * (1f - burst) * 1.05f + 1f,
            center = c0,
            style = Stroke(width = rad * 0.02f),
        )
    }

    // ── 7. Floating squares ──
    val teal = Color(0xFF21D1BF)
    val coral = Color(0xFFFF4066)
    for (k in 0 until f.pn) {
        val ang = f.pA[k] + 0.12f * sin(f.pK[k] * flow + f.pPh[k])
        val rr = f.pR[k] * rad * (1f + 0.03f * sin(f.pK[k] * flow + f.pPh[k] * 2f))
        val px = cx + rr * cos(ang)
        val py = cy + rr * sin(ang)
        val sz = f.pS[k] * rad
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(f.pK[k] * 2f * flow + f.pPh[k]))
        val col = when (f.pKind[k]) {
            1 -> teal
            2 -> coral
            else -> capCol
        }
        drawRect(
            color = col.copy(alpha = minOf(1f, tw * 1.1f)),
            topLeft = Offset(px - sz / 2f, py - sz / 2f),
            size = Size(sz, sz),
        )
    }
    // The little red marker sitting in the heart of the core.
    drawRect(
        color = Color(0xFFD9365C).copy(alpha = 0.95f),
        topLeft = Offset(cx - 0.012f * rad, cy - 0.012f * rad),
        size = Size(0.024f * rad, 0.024f * rad),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Glowing 8D ball
//
//   PATH — the outline of the REAL head image (headphones included) is traced from
//          its pixels at runtime, and the ball runs along that outline, just outside
//          it, from one ear over the top to the other ear. Swap the head image and
//          the path follows the new shape by itself. (If the image can't be read, a
//          head-shaped curve is used instead.)
//   LOOK — glossy cream → amber → coral orb with a slowly flowing wave, a pale rim
//          and a soft red-orange haze.
// ─────────────────────────────────────────────────────────────────────────────

private const val HEAD_RAYS = 181
private const val ORBIT_LUT = 256
private const val PI_F = 3.1415927f

/** Ball radius as a fraction of the head box side. */
private const val ORB_R_FRAC = 0.062f

/** Distance from the head outline to the ball CENTRE, in ball radii (1.0 = touching). */
private const val ORB_GAP_R = 1.25f

/**
 * Upper outline of the head, as seen from the ear-level centre point.
 * Units are "bitmap heights" (so they stay isotropic): x / bitmapHeight, y / bitmapHeight.
 * r[k] is the outline radius at angle PI + PI·k/(HEAD_RAYS-1): k = 0 is the LEFT ear,
 * the middle is straight up, k = HEAD_RAYS-1 is the RIGHT ear.
 */
private class HeadTrace(val aspect: Float, val cx: Float, val cy: Float, val r: FloatArray)

private val headTraceCache = java.util.concurrent.ConcurrentHashMap<Int, HeadTrace>()

/** Decodes the head drawable at a small size (raster or vector). Runs off the main thread. */
private fun loadHeadBitmap(ctx: Context, resId: Int, maxDim: Int = 200): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(ctx.resources, resId, bounds)
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeResource(ctx.resources, resId, opts)?.let { return it }
    }
    // Vector (or other) drawable: rasterise it.
    val d = ContextCompat.getDrawable(ctx, resId) ?: return null
    val iw = if (d.intrinsicWidth > 0) d.intrinsicWidth else maxDim
    val ih = if (d.intrinsicHeight > 0) d.intrinsicHeight else maxDim
    val k = maxDim.toFloat() / maxOf(iw, ih)
    val w = maxOf(1, (iw * k).toInt())
    val h = maxOf(1, (ih * k).toInt())
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, w, h)
    d.draw(android.graphics.Canvas(bmp))
    return bmp
}

/**
 * Finds the head + headphones silhouette and returns its upper outline.
 *  1. foreground = alpha (transparent background) or colour distance from the border colour
 *  2. keep the largest connected blob
 *  3. ear level = middle of the widest band of rows (the ear cups)
 *  4. cast rays from the ear-level centre over the top; the farthest foreground pixel on
 *     each ray is the outline (so the headphone band, not the skull, is followed)
 *  5. fill tiny dents and smooth
 */
private fun traceHead(bmp: Bitmap): HeadTrace? {
    val w = bmp.width
    val h = bmp.height
    if (w < 16 || h < 16) return null
    val n = w * h
    val px = IntArray(n)
    bmp.getPixels(px, 0, w, 0, 0, w, h)

    // ── 1. foreground mask ──
    val bd = 2
    val cap = 2 * bd * (w + h) + 16
    val br = IntArray(cap)
    val bgc = IntArray(cap)
    val bb = IntArray(cap)
    var bn = 0
    var clear = 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            if (x >= bd && x < w - bd && y >= bd && y < h - bd) continue
            val c = px[y * w + x]
            if (bn < cap) {
                br[bn] = (c shr 16) and 0xFF
                bgc[bn] = (c shr 8) and 0xFF
                bb[bn] = c and 0xFF
                bn++
            }
            if ((c ushr 24) < 40) clear++
        }
    }
    if (bn == 0) return null
    val mask = BooleanArray(n)
    if (clear.toFloat() / bn > 0.5f) {
        for (i in 0 until n) mask[i] = (px[i] ushr 24) >= 40
    } else {
        java.util.Arrays.sort(br, 0, bn)
        java.util.Arrays.sort(bgc, 0, bn)
        java.util.Arrays.sort(bb, 0, bn)
        val mR = br[bn / 2]
        val mG = bgc[bn / 2]
        val mB = bb[bn / 2]
        val dist = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]
            dist[i] = maxOf(
                abs(((c shr 16) and 0xFF) - mR),
                abs(((c shr 8) and 0xFF) - mG),
                abs((c and 0xFF) - mB),
            ).toFloat()
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -1..1) sum += dist[yy * w + (x + dx).coerceIn(0, w - 1)]
                }
                mask[y * w + x] = sum / 9f > 6f && (px[y * w + x] ushr 24) >= 40
            }
        }
    }

    // ── 2. largest connected blob ──
    val lab = IntArray(n)
    val stack = IntArray(n)
    var best = 0
    var bestLab = 0
    var cur = 0
    for (start in 0 until n) {
        if (!mask[start] || lab[start] != 0) continue
        cur++
        var sp = 0
        stack[sp++] = start
        lab[start] = cur
        var count = 0
        while (sp > 0) {
            val p = stack[--sp]
            count++
            val x = p % w
            val y = p / w
            if (x > 0) { val q = p - 1; if (mask[q] && lab[q] == 0) { lab[q] = cur; stack[sp++] = q } }
            if (x < w - 1) { val q = p + 1; if (mask[q] && lab[q] == 0) { lab[q] = cur; stack[sp++] = q } }
            if (y > 0) { val q = p - w; if (mask[q] && lab[q] == 0) { lab[q] = cur; stack[sp++] = q } }
            if (y < h - 1) { val q = p + w; if (mask[q] && lab[q] == 0) { lab[q] = cur; stack[sp++] = q } }
        }
        if (count > best) { best = count; bestLab = cur }
    }
    if (bestLab == 0) return null

    // ── 3. silhouette extent + ear level ──
    val left = IntArray(h) { -1 }
    val right = IntArray(h) { -1 }
    var top = -1
    var bot = -1
    var rowsWith = 0
    for (y in 0 until h) {
        var lo = -1
        var hi = -1
        for (x in 0 until w) {
            if (lab[y * w + x] == bestLab) {
                if (lo < 0) lo = x
                hi = x
            }
        }
        left[y] = lo
        right[y] = hi
        if (lo >= 0) {
            rowsWith++
            if (top < 0) top = y
            bot = y
        }
    }
    if (top < 0 || rowsWith < h * 0.3f) return null
    val bh = bot - top + 1

    val wid = FloatArray(h) { if (left[it] >= 0) (right[it] - left[it]).toFloat() else 0f }
    val ws = FloatArray(h) { y ->
        var s = 0f
        for (d in -2..2) {
            val yy = y + d
            if (yy in 0 until h) s += wid[yy]
        }
        s / 5f
    }
    val y0 = (top + 0.18f * bh).toInt().coerceIn(0, h - 1)
    val y1 = (top + 0.62f * bh).toInt().coerceIn(y0, h - 1)
    var widest = 0f
    for (y in y0..y1) widest = maxOf(widest, ws[y])
    var first = y0
    while (first < y1 && ws[first] < 0.96f * widest) first++
    var run = first
    while (run + 1 <= y1 && ws[run + 1] >= 0.96f * widest) run++
    val ye = (first + run) / 2
    if (left[ye] < 0) return null
    val xc = (left[ye] + right[ye]) / 2f

    // ── 4. rays over the top: left ear → top → right ear ──
    val rmax = sqrt((w * w + h * h).toFloat())
    val raw = FloatArray(HEAD_RAYS)
    for (k in 0 until HEAD_RAYS) {
        val th = PI_F + PI_F * k / (HEAD_RAYS - 1)
        val c = cos(th)
        val s = sin(th)
        var last = 0f
        var t = 0f
        while (t < rmax) {
            val x = (xc + c * t).roundToInt()
            val y = (ye + s * t).roundToInt()
            if (x < 0 || x >= w || y < 0 || y >= h) break
            if (lab[y * w + x] == bestLab) last = t
            t += 0.5f
        }
        raw[k] = last
    }

    // ── 5. fill small dents (sliding max), then smooth (gaussian) ──
    val filled = FloatArray(HEAD_RAYS) { i ->
        var m = 0f
        for (d in -5..5) m = maxOf(m, raw[(i + d).coerceIn(0, HEAD_RAYS - 1)])
        m
    }
    val sigma = 4f
    val kk = 12
    val kern = FloatArray(2 * kk + 1) {
        val z = (it - kk) / sigma
        exp(-0.5f * z * z)
    }
    var ksum = 0f
    for (v in kern) ksum += v
    val smooth = FloatArray(HEAD_RAYS) { i ->
        var s = 0f
        for (d in -kk..kk) s += kern[d + kk] * filled[(i + d).coerceIn(0, HEAD_RAYS - 1)]
        s / ksum
    }

    // Sanity: a real head outline is neither tiny nor wider than the picture.
    var rMin = Float.MAX_VALUE
    var rMax = 0f
    for (v in smooth) {
        if (v < rMin) rMin = v
        if (v > rMax) rMax = v
    }
    if (rMin < 0.12f * bh || rMax > 0.98f * maxOf(w, h)) return null

    return HeadTrace(
        aspect = w.toFloat() / h,
        cx = xc / h,
        cy = ye.toFloat() / h,
        r = FloatArray(HEAD_RAYS) { smooth[it] / h },
    )
}

/** Head-shaped curve (rounded box) used only if the real image can't be traced. */
private fun fallbackHeadTrace(): HeadTrace {
    val r = FloatArray(HEAD_RAYS) { k ->
        val th = PI_F + PI_F * k / (HEAD_RAYS - 1)
        val p = 2.6f
        val c = abs(cos(th)) / 0.40f
        val s = abs(sin(th)) / 0.41f
        (c.pow(p) + s.pow(p)).pow(-1f / p)
    }
    return HeadTrace(aspect = 1f, cx = 0.5f, cy = 0.5f, r = r)
}

/**
 * The ball's track in canvas pixels: the traced outline pushed outward by [gapPx],
 * resampled to ORBIT_LUT points that are evenly spaced ALONG the path (so the ball
 * keeps its speed round the headphone cups). Returns x0,y0,x1,y1,…  — index 0 is the
 * left ear, the last point is the right ear.
 */
private fun buildOrbit(trace: HeadTrace, boxW: Float, boxH: Float, gapPx: Float): FloatArray {
    // The head image is drawn with ContentScale.Fit, centred in the box.
    val scale = minOf(boxH, boxW / trace.aspect)           // pixels per "bitmap height"
    val ox = (boxW - trace.aspect * scale) / 2f
    val oy = (boxH - scale) / 2f
    val gap = gapPx / scale

    val xs = FloatArray(HEAD_RAYS)
    val ys = FloatArray(HEAD_RAYS)
    for (k in 0 until HEAD_RAYS) {
        val th = PI_F + PI_F * k / (HEAD_RAYS - 1)
        val rr = trace.r[k] + gap
        xs[k] = ox + (trace.cx + rr * cos(th)) * scale
        ys[k] = oy + (trace.cy + rr * sin(th)) * scale
    }
    val cum = FloatArray(HEAD_RAYS)
    for (k in 1 until HEAD_RAYS) {
        val dx = xs[k] - xs[k - 1]
        val dy = ys[k] - ys[k - 1]
        cum[k] = cum[k - 1] + sqrt(dx * dx + dy * dy)
    }
    val total = cum[HEAD_RAYS - 1]
    val out = FloatArray(ORBIT_LUT * 2)
    var seg = 0
    for (i in 0 until ORBIT_LUT) {
        val target = total * i / (ORBIT_LUT - 1)
        while (seg < HEAD_RAYS - 2 && cum[seg + 1] < target) seg++
        val span = cum[seg + 1] - cum[seg]
        val f = if (span > 1e-6f) ((target - cum[seg]) / span).coerceIn(0f, 1f) else 0f
        out[2 * i] = xs[seg] + (xs[seg + 1] - xs[seg]) * f
        out[2 * i + 1] = ys[seg] + (ys[seg + 1] - ys[seg]) * f
    }
    return out
}

@Composable
private fun SpatialOrbBall(
    headSizePx: Float,
    headRes: Int,
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

    // Trace the real head image once (off the main thread, then cached).
    val context = LocalContext.current
    val trace by produceState<HeadTrace?>(initialValue = headTraceCache[headRes], headRes) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                headTraceCache[headRes] ?: run {
                    val traced = runCatching {
                        val bmp = loadHeadBitmap(context, headRes)
                        val t = if (bmp != null) traceHead(bmp) else null
                        bmp?.recycle()
                        t
                    }.getOrNull() ?: fallbackHeadTrace()
                    headTraceCache[headRes] = traced
                    traced
                }
            }
        }
    }

    val ballR = headSizePx * ORB_R_FRAC
    val orbit = remember(trace, headSizePx) {
        trace?.let { buildOrbit(it, headSizePx, headSizePx, ballR * ORB_GAP_R) }
    }

    // Slow wave inside the orb (read in the draw lambda, so it only redraws).
    val infinite = rememberInfiniteTransition(label = "orb")
    val waveAnim = infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbWave",
    )
    val orbClip = remember { Path() }
    val bandPath = remember { Path() }

    Canvas(modifier = modifier) {
        val lut = orbit ?: return@Canvas

        // sin(phase) < 0 → sound LEFT → left ear (start of the track)
        // sin(phase) > 0 → sound RIGHT → right ear (end of the track)
        val pos = (sin(phase) + 1f) * 0.5f * (ORBIT_LUT - 1)
        val i0 = pos.toInt().coerceIn(0, ORBIT_LUT - 2)
        val fr = pos - i0
        val ballX = lut[2 * i0] + (lut[2 * i0 + 2] - lut[2 * i0]) * fr
        val ballY = lut[2 * i0 + 1] + (lut[2 * i0 + 3] - lut[2 * i0 + 1]) * fr

        drawGlowOrb(ballX, ballY, ballR, waveAnim.value, orbClip, bandPath)
    }
}

/**
 * Glossy orb: cream top → amber → coral-red on the left, a flowing wave boundary,
 * soft peach light along the bottom, a pale rim, and a warm red-orange haze around it.
 */
private fun DrawScope.drawGlowOrb(
    bx: Float,
    by: Float,
    r: Float,
    wave: Float,
    clip: Path,
    band: Path,
) {
    val c = Offset(bx, by)

    // ── haze ──
    val haze = Color(0xFFE23E24)
    val hazeR = r * 3.4f
    drawCircle(
        brush = Brush.radialGradient(
            0f to haze.copy(alpha = 0.50f),
            0.294f to haze.copy(alpha = 0.46f),
            0.441f to haze.copy(alpha = 0.32f),
            0.588f to haze.copy(alpha = 0.15f),
            0.765f to haze.copy(alpha = 0.06f),
            1f to haze.copy(alpha = 0f),
            center = c,
            radius = hazeR,
        ),
        radius = hazeR,
        center = c,
    )

    // ── body, clipped to the disc ──
    clip.reset()
    clip.addOval(Rect(bx - r, by - r, bx + r, by + r))
    clipPath(clip) {
        // base: cream → amber → peach, top to bottom
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFFFFF7BA),
                0.05f to Color(0xFFFFF7BA),
                0.60f to Color(0xFFFFC868),
                1f to Color(0xFFFFBE70),
                startY = by - r,
                endY = by + r,
            ),
            topLeft = Offset(bx - r, by - r),
            size = Size(2f * r, 2f * r),
        )

        // orange below a wavy boundary (feathered: 4 offset copies)
        val orange = Color(0xFFFF9C3A)
        val steps = 16
        for (off in floatArrayOf(-0.12f, -0.04f, 0.04f, 0.12f)) {
            band.reset()
            for (i in 0..steps) {
                val u = -1.05f + 2.1f * i / steps
                val yb = 0.02f + 0.30f * u +
                    0.05f * sin(3.3f * u + wave) +
                    0.03f * sin(7.1f * u - 2f * wave + 1f) + off
                val x = bx + u * r
                val y = by + yb * r
                if (i == 0) band.moveTo(x, y) else band.lineTo(x, y)
            }
            band.lineTo(bx + 1.05f * r, by + 1.4f * r)
            band.lineTo(bx - 1.05f * r, by + 1.4f * r)
            band.close()
            drawPath(path = band, color = orange.copy(alpha = 0.33f))
        }

        // hot coral-red blob on the left
        val red = Color(0xFFFD5834)
        val redC = Offset(bx - 0.78f * r, by + (0.12f + 0.04f * sin(wave)) * r)
        drawCircle(
            brush = Brush.radialGradient(
                0f to red.copy(alpha = 0.92f),
                0.30f to red.copy(alpha = 0.70f),
                0.50f to red.copy(alpha = 0.45f),
                0.70f to red.copy(alpha = 0.28f),
                0.85f to red.copy(alpha = 0.10f),
                1f to red.copy(alpha = 0f),
                center = redC,
                radius = 0.8f * r,
            ),
            radius = 0.8f * r,
            center = redC,
        )

        // soft peach light rising from the bottom rim
        val peach = Color(0xFFFFD6A0)
        val peachC = Offset(bx + 0.10f * r, by + 1.05f * r)
        drawCircle(
            brush = Brush.radialGradient(
                0f to peach.copy(alpha = 0.55f),
                0.30f to peach.copy(alpha = 0.33f),
                0.50f to peach.copy(alpha = 0.21f),
                0.75f to peach.copy(alpha = 0.04f),
                1f to peach.copy(alpha = 0f),
                center = peachC,
                radius = 1.0f * r,
            ),
            radius = 1.0f * r,
            center = peachC,
        )

        // cream gloss, upper right
        val gloss = Color(0xFFFFFCCD)
        val glossC = Offset(bx + 0.28f * r, by - 0.62f * r)
        drawCircle(
            brush = Brush.radialGradient(
                0f to gloss.copy(alpha = 0.75f),
                0.25f to gloss.copy(alpha = 0.52f),
                0.50f to gloss.copy(alpha = 0.33f),
                0.75f to gloss.copy(alpha = 0.14f),
                1f to gloss.copy(alpha = 0f),
                center = glossC,
                radius = 0.95f * r,
            ),
            radius = 0.95f * r,
            center = glossC,
        )

        // pale rim so the sphere reads round
        val rim = Color(0xFFFFEECD)
        drawCircle(
            brush = Brush.radialGradient(
                0f to rim.copy(alpha = 0f),
                0.78f to rim.copy(alpha = 0f),
                0.90f to rim.copy(alpha = 0.10f),
                1f to rim.copy(alpha = 0.32f),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Glass shell — translucent lenses that gather into a bubble
//
// Seven soft lenses drift near the middle of the sphere, then swell, slide outward
// and close into a segmented glass shell. A bright core light sits inside it and
// glows through the overlapping lenses. Every cycle the lenses relax back toward
// the middle, so the loop breathes instead of spinning forever.
//
// Everything is a periodic function of `flow`, so the 15 s loop is seamless.
// Palette follows the reference frames: deep cobalt bodies, cyan rims,
// violet cores, pink iridescent streaks, white-hot centre.
// ─────────────────────────────────────────────────────────────────────────────

private class GlassField {
    val n = 7
    val ang = FloatArray(n)
    val seed = FloatArray(n)
    val wMul = FloatArray(n)
    val hMul = FloatArray(n)
    val tilt = FloatArray(n)
    val alpha = FloatArray(n)
    val path = Path()

    init {
        val rnd = Random(29)
        for (i in 0 until n) {
            ang[i] = TWO_PI_F * i / n + (rnd.nextFloat() - 0.5f) * 0.26f
            seed[i] = rnd.nextFloat() * TWO_PI_F
            wMul[i] = 0.88f + rnd.nextFloat() * 0.30f
            hMul[i] = 0.82f + rnd.nextFloat() * 0.46f
            tilt[i] = (rnd.nextFloat() - 0.5f) * 0.22f
            alpha[i] = 0.78f + rnd.nextFloat() * 0.22f
        }
    }
}

/** A rounded segment: two cubic arcs with blunt, curved ends. */
private fun buildLens(path: Path, w: Float, h: Float) {
    path.reset()
    path.moveTo(-w, 0f)
    path.cubicTo(-w * 0.68f, -h * 1.10f, w * 0.68f, -h * 1.10f, w, 0f)
    path.cubicTo(w * 0.68f, h * 1.10f, -w * 0.68f, h * 1.10f, -w, 0f)
    path.close()
}

/**
 * @param rad  radius of the assembled shell in px
 * @param flow looping phase 0..2π (15 s)
 * @param glow 0..0.55 tap burst
 */
private fun DrawScope.drawGlassShell(
    f: GlassField,
    baseX: Float,
    baseY: Float,
    rad: Float,
    flow: Float,
    glow: Float,
) {
    val deep = Color(0xFF1E3BB8)      // cobalt body
    val blue = Color(0xFF3D6BF2)      // mid blue
    val cyan = Color(0xFF6FD1FF)      // rims / edge light
    val violet = Color(0xFF8A5CF0)    // core patches
    val pink = Color(0xFFE86FB1)      // iridescent streak
    val pale = Color(0xFFCFE6FF)      // inner highlight line
    val hot = Color(0xFFFFFFFF)       // centre point

    // Assembly 0 (loose lenses) → 1 (closed shell), smooth at both ends.
    val raw = 0.5f + 0.5f * sin(flow)
    val a = raw * raw * (3f - 2f * raw)
    val c0 = Offset(baseX, baseY)
    val g = 1f + glow * 1.4f

    // ── 1. deep glow behind the shell ──
    val bloomR = rad * 1.24f
    drawCircle(
        brush = Brush.radialGradient(
            0f to blue.copy(alpha = (0.13f * g).coerceAtMost(0.9f)),
            0.45f to violet.copy(alpha = (0.10f * g).coerceAtMost(0.9f)),
            0.80f to deep.copy(alpha = (0.07f * g).coerceAtMost(0.9f)),
            1f to deep.copy(alpha = 0f),
            center = c0,
            radius = bloomR,
        ),
        radius = bloomR,
        center = c0,
    )

    // ── 2. core light, seen through the glass ──
    val corePulse = 0.5f + 0.5f * sin(3f * flow)
    val coreR = rad * (0.22f + 0.05f * corePulse) * (1f + glow * 0.7f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to hot.copy(alpha = (0.50f * a * g).coerceIn(0f, 1f)),
            0.30f to violet.copy(alpha = (0.34f * a * g).coerceIn(0f, 1f)),
            0.62f to deep.copy(alpha = (0.16f * a * g).coerceIn(0f, 1f)),
            1f to deep.copy(alpha = 0f),
            center = c0,
            radius = coreR,
        ),
        radius = coreR,
        center = c0,
    )

    // ── 3. lenses ──
    val d0 = rad * 0.48f
    val w0 = rad * 0.34f
    val h0 = rad * 0.155f
    for (i in 0 until f.n) {
        val sw = sin(flow + f.seed[i])
        val wob = sin(2f * flow + f.seed[i] * 1.7f)
        val angle = f.ang[i] + flow + 0.05f * wob
        val d = (d0 - rad * 0.09f * a) * (1f + 0.03f * sw)
        val w = w0 * f.wMul[i] * (0.45f + 0.55f * a)
        val h = h0 * f.hMul[i] * (0.45f + 0.55f * a)
        val px = baseX + d * cos(angle)
        val py = baseY + d * sin(angle)
        val petalDeg = angle * RAD_TO_DEG + 90f + f.tilt[i] * RAD_TO_DEG + 4f * sw
        val pa = f.alpha[i] * (0.40f + 0.60f * a)

        buildLens(f.path, w, h)
        translate(left = px, top = py) {
            rotate(degrees = petalDeg, pivot = Offset.Zero) {
                // body: cobalt → blue → violet, running along the segment
                drawPath(
                    path = f.path,
                    brush = Brush.linearGradient(
                        0f to deep.copy(alpha = 0.46f * pa),
                        0.48f to blue.copy(alpha = 0.40f * pa),
                        1f to violet.copy(alpha = 0.44f * pa),
                        start = Offset(-w * 0.30f, -h),
                        end = Offset(w * 0.30f, h),
                    ),
                )
                // iridescence: cyan sheen on the upper lip, pink on the lower lip
                drawPath(
                    path = f.path,
                    brush = Brush.verticalGradient(
                        0f to cyan.copy(alpha = 0.16f * pa),
                        0.45f to Color.Transparent,
                        0.72f to pink.copy(alpha = 0.20f * pa),
                        1f to pink.copy(alpha = 0.08f * pa),
                        startY = -h * 1.1f,
                        endY = h * 1.1f,
                    ),
                )
                // bright cyan rim
                drawPath(
                    path = f.path,
                    color = cyan.copy(alpha = 0.40f * pa),
                    style = Stroke(width = (rad * 0.0038f).coerceAtLeast(0.7f)),
                )
                // inner highlight line
                scale(scaleX = 0.80f, scaleY = 0.74f, pivot = Offset.Zero) {
                    drawPath(
                        path = f.path,
                        color = pale.copy(alpha = 0.20f * pa),
                        style = Stroke(width = (rad * 0.0022f).coerceAtLeast(0.5f)),
                    )
                }
                // specular highlight near the outer tip
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to hot.copy(alpha = 0.60f * pa),
                        0.40f to pale.copy(alpha = 0.22f * pa),
                        1f to pale.copy(alpha = 0f),
                        center = Offset(w * 0.42f, -h * 0.30f),
                        radius = w * 0.40f,
                    ),
                    radius = w * 0.40f,
                    center = Offset(w * 0.42f, -h * 0.30f),
                )
                // pink glow on the opposite edge
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to pink.copy(alpha = 0.26f * pa),
                        1f to pink.copy(alpha = 0f),
                        center = Offset(-w * 0.45f, h * 0.15f),
                        radius = w * 0.52f,
                    ),
                    radius = w * 0.52f,
                    center = Offset(-w * 0.45f, h * 0.15f),
                )
            }
        }
    }

    // ── 4. outer shell rim ──
    if (a > 0.05f) {
        drawCircle(
            color = cyan.copy(alpha = 0.20f * a),
            radius = rad * 0.88f,
            center = c0,
            style = Stroke(width = (rad * 0.0032f).coerceAtLeast(0.6f)),
        )
    }

    // ── 5. hot core point, on top of the glass ──
    if (a > 0.25f) {
        val hotR = rad * 0.042f * (1f + 0.15f * corePulse)
        drawCircle(
            brush = Brush.radialGradient(
                0f to hot.copy(alpha = 0.95f * a),
                0.22f to violet.copy(alpha = 0.55f * a),
                0.55f to blue.copy(alpha = 0.22f * a),
                1f to blue.copy(alpha = 0f),
                center = c0,
                radius = hotR * 3.6f,
            ),
            radius = hotR * 3.6f,
            center = c0,
        )
        drawCircle(
            color = hot.copy(alpha = 0.90f * a),
            radius = hotR,
            center = c0,
        )
    }
}

private data class Direction(
    val angleDeg: Float,
    val label: String,
)

/** Which creature the Effect page shows in the middle. */
private enum class SphereStyle {
    /** The original white dot sphere (default). */
    METAMORPHIC,
    /** The jellyfish renderer. */
    JELLY,
    /** Translucent lenses that gather into a glass bubble. */
    GLASS,
}

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
    glow: Float = 0f,
    spinDeg: Float = 0f,
    tint: Color = Color.White,
    sphereStyle: SphereStyle = SphereStyle.METAMORPHIC,
    onQuickTap: () -> Unit = {},
) {
    val points = remember { fibonacciSphere(180) }
    val jelly = remember { JellyField() }
    val glass = remember { GlassField() }
    val infinite = rememberInfiniteTransition(label = "sphere")
    // Slow spin — used by the metamorphic sphere only. 0 when 8D is off (or when
    // the Sphere Rotation switch is off). The value is read inside the Canvas
    // draw lambda, so the 60 fps animation only re-draws.
    val rotationAnim = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rot",
    )
    // States are read inside the Canvas draw lambda (not here), so the 60 fps
    // animation only re-draws — it does not recompose this whole composable.
    // Core breathing.
    val pulseAnim = infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    // Master loop for strand sway / caps / particles. Everything uses integer
    // harmonics of this phase, so the loop is seamless.
    val flowAnim = infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow",
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

    Box(
        modifier = modifier.pointerInput(expandProgress > 0.5f) {
            if (expandProgress > 0.5f) return@pointerInput
            detectTapGestures(
                onPress = { offset ->
                    // Only the sphere body reacts. While the number ring is open
                    // its own tap layer handles the taps instead.
                    val w = size.width.toFloat()
                    val dx = offset.x - w / 2f
                    val dy = offset.y - w / 2f
                    val sphereR = w * 0.2026f
                    if (dx * dx + dy * dy <= sphereR * sphereR) {
                        // Toggles on touch, not on release — holding makes no
                        // difference, it only lets you watch the colour fade.
                        onQuickTap()
                    }
                    tryAwaitRelease()
                },
            )
        },
    ) {
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

        if (sphereStyle == SphereStyle.JELLY) {
            // ── Ocean Jelly ──
            // Colour = `tint` (blue = 8D on, red = off). It moves (breathes, lobes,
            // drift) but never rotates, whether 8D is on or off — so `rotationEnabled`
            // and `spinDeg` are intentionally not used here. Tap burst = `glow`.
            drawJellyfish(
                f = jelly,
                baseX = cx,
                baseY = cy,
                rad = sphereR * 0.93f,
                flow = flowAnim.value,
                breath = sin(pulseAnim.value),
                glow = glow,
                tint = tint,
            )
        } else if (sphereStyle == SphereStyle.GLASS) {
            // ── Glass Bubble ──
            // Warm palette of the 8D glow ball: cream → amber → deep orange.
            // The lenses gather and relax, and a hot point glows inside.
            drawGlassShell(
                f = glass,
                baseX = cx,
                baseY = cy,
                rad = sphereR * 0.93f,
                flow = flowAnim.value,
                glow = glow,
            )
        } else {
            // ── Metamorphic Sphere — the original white dot sphere ──
            // Pure white, no state colour at all: the 8D state is shown by the
            // green menu dots, never by the sphere.
            val rotation = (if (rotationEnabled) rotationAnim.value else 0f) + spinDeg
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
            val pulseAmp = 0.10f + 0.35f * glow
            for (v in projected) {
                val depth = (v.z + 1f) * 0.5f
                val wave = sin(pulseAnim.value + depth * 3f)
                val scale = 1f + pulseAmp * wave
                val dotR = baseDot * (0.4f + depth * 0.9f) * scale * (1f + glow * 1.0f)
                val px = cx + v.x * sphereR
                val py = cy - v.y * sphereR
                val alpha = (0.18f + depth * 0.82f).coerceAtMost(1f)
                // Burst halo on tap, white like the dots.
                if (glow > 0.01f) {
                    drawCircle(
                        color = Color.White.copy(
                            alpha = (0.26f * glow * (0.35f + depth * 0.65f)).coerceIn(0f, 0.55f)
                        ),
                        radius = dotR * (2.4f + 1.2f * glow),
                        center = Offset(px, py),
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = dotR.coerceAtLeast(0.3f),
                    center = Offset(px, py),
                )
            }
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
