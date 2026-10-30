package com.rajatxo.lutea.ui.screens

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.data.model.Playlist
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.components.NyghtSerifFamily
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * LuteaArc — the playlist wheel.
 *
 * Geometry (Lutea's own — kept as you specified, NOT changed):
 *  - Pivot off-screen left (x = -50% width, y = 50% height)
 *  - INNER arc radius = 0.42w   (small arc, counter-rotating balls)
 *  - OUTER arc radius = 0.52w   (big arc, primary balls + text)
 *  - 180° sweep, both arcs thick + bright (3.5dp→0.5dp @ 0.8 alpha needle arc)
 *  - Text orbit = outerRadius + 14dp (just past the outer arc)
 *
 * Coral mechanics (copied in):
 *  - Physics fling (animateDecay) + spring snap to nearest item
 *  - Two-zone drag: left-half (counter-rotating) + full-screen (normal)
 *  - Tap near center opens the playlist
 *  - Wheel position persists across tab switches (SharedPreferences)
 *  - Tick sound + haptic on each item boundary crossing
 *  - Balls on BOTH arcs (counter-rotating — pully coupling)
 *  - ±7 items visible, 8° spacing, smooth opacity falloff
 *
 * Text style (Coral spec):
 *  - Active   = CalSans SemiBold 42sp TitleCase, accent color (#F4B400 default)
 *  - Inactive = NyghtSerif Light Italic 16sp TitleCase, white
 *  - Adjacent items (offset ±1) get a white→accent vertical gradient
 *  - Auto-fit: long text shrinks to fit 173dp budget
 *
 * Universal Layout Laws:
 *  - Law 1: statusBarsPadding() on the "+ New Playlist" capsule
 *  - Edge-to-edge: MainActivity already set DecorFitsSystemWindows = false
 */
@Composable
fun LuteaArc(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFF4B400),
    onOpenPlaylist: (Playlist) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // ── Wheel rotation state (Animatable for physics fling + spring snap) ──
    val wheelPrefs = remember { context.getSharedPreferences("lutea_wheel", android.content.Context.MODE_PRIVATE) }
    val savedIndex = remember { wheelPrefs.getInt("wheel_center_index", 0) }
    val pxPerItem = with(density) { 64.dp.toPx() }
    val scrollOffset = remember { Animatable(savedIndex * pxPerItem) }
    var lastSnappedIndex by remember { mutableStateOf(savedIndex) }

    // ── Sound + Haptic (Coral pattern) ──
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var soundLoaded by remember { mutableStateOf(false) }
    val tickSoundId = remember {
        soundPool.setOnLoadCompleteListener { _, _, status -> if (status == 0) soundLoaded = true }
        soundPool.load(context, R.raw.wheel_tick, 1)
    }
    DisposableEffect(Unit) { onDispose { soundPool.release() } }

    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                    as? android.os.VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    fun tickHaptic() {
        var hapticPerformed = false
        try {
            hapticPerformed = view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Exception) { }
        if (!hapticPerformed) {
            try {
                vibrator?.let { v ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        v.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(30)
                    }
                }
            } catch (_: Exception) { }
        }
        if (soundLoaded) {
            try { soundPool.play(tickSoundId, 0.6f, 0.6f, 1, 0, 1f) } catch (_: Exception) { }
        }
    }

    fun indexAtOffset(offset: Float): Int {
        if (playlists.isEmpty()) return 0
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % playlists.size
        return if (mod < 0) mod + playlists.size else mod
    }

    val centerIndex = remember(scrollOffset.value) { indexAtOffset(scrollOffset.value) }

    // Persist the wheel position whenever it changes.
    LaunchedEffect(centerIndex) {
        wheelPrefs.edit().putInt("wheel_center_index", centerIndex).apply()
    }

    // ── Create Playlist dialog ──
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    "New Playlist",
                    color = Color.White,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistName.isNotBlank()) {
                        PlaylistStore.createPlaylist(playlistName)
                    }
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05050A)),
    ) {
        // ──────────────────────────────────────────────────────────────
        // Wheel Canvas — full-screen drag handlers + drawing
        // (1:1 port of Coral's PlaylistWheel)
        // ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                // LEFT ZONE drag handler (counter-rotating first arc)
                .pointerInput(playlists.size) {
                    val halfScreen = size.width / 2
                    var velocityTracker = VelocityTracker()
                    var isLeftZoneDrag = false
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isLeftZoneDrag = offset.x < halfScreen
                            if (isLeftZoneDrag) {
                                velocityTracker = VelocityTracker()
                            }
                        },
                        onDragEnd = {
                            if (isLeftZoneDrag) {
                                val velocity = velocityTracker.calculateVelocity().y
                                coroutineScope.launch {
                                    scrollOffset.animateDecay(
                                        initialVelocity = -velocity * 0.35f,
                                        animationSpec = exponentialDecay(frictionMultiplier = 0.9f)
                                    )
                                    val nearest = (scrollOffset.value / pxPerItem).roundToInt()
                                    scrollOffset.animateTo(
                                        targetValue = nearest * pxPerItem,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                            isLeftZoneDrag = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (isLeftZoneDrag) {
                                coroutineScope.launch {
                                    scrollOffset.snapTo(scrollOffset.value - dragAmount)
                                }
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val currentIdx = indexAtOffset(scrollOffset.value)
                                if (currentIdx != lastSnappedIndex) {
                                    lastSnappedIndex = currentIdx
                                    tickHaptic()
                                }
                                change.consume()
                            }
                        }
                    )
                }
                // FULL SCREEN drag handler (normal rotation)
                .pointerInput(playlists.size) {
                    var velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { velocityTracker = VelocityTracker() },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity().y
                            coroutineScope.launch {
                                scrollOffset.animateDecay(
                                    initialVelocity = velocity * 0.20f,
                                    animationSpec = exponentialDecay(frictionMultiplier = 1.2f)
                                )
                                val nearest = (scrollOffset.value / pxPerItem).roundToInt()
                                scrollOffset.animateTo(
                                    targetValue = nearest * pxPerItem,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            coroutineScope.launch {
                                scrollOffset.snapTo(scrollOffset.value + dragAmount)
                            }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val currentIdx = indexAtOffset(scrollOffset.value)
                            if (currentIdx != lastSnappedIndex) {
                                lastSnappedIndex = currentIdx
                                tickHaptic()
                            }
                            change.consume()
                        }
                    )
                }
                // TAP near center opens the playlist
                .pointerInput(playlists.size) {
                    detectTapGestures(
                        onTap = { offset ->
                            val canvasHeight = size.height
                            val centerY = canvasHeight / 2f
                            val distFromCenter = abs(offset.y - centerY)
                            val maxDist = with(density) { 80.dp.toPx() }
                            if (distFromCenter < maxDist && playlists.isNotEmpty()) {
                                val centerIdx = indexAtOffset(scrollOffset.value)
                                playlists.getOrNull(centerIdx)?.let { onOpenPlaylist(it) }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // === 1. PIVOT (off-screen, left) ===
                // Lutea's pivot at -20% width (smaller offset than Coral's -50%
                // because Lutea's radii are 0.42w/0.52w, not Coral's 0.65w).
                // With outerRadius = 0.52w, the arc's right edge lands at
                // -0.20w + 0.52w = 0.32w — covers the left third of the screen.
                val pivotX = w * -0.20f
                val pivotY = h * 0.50f

                // === 2. DUAL CONCENTRIC RADII SYSTEM (Lutea's own geometry) ===
                // Two thick + bright arcs (kept at Lutea's 3.5dp→0.5dp @ 0.8 alpha needle arc)
                // at 0.42w (inner) and 0.52w (outer). Text sits just past the outer arc.
                val innerRadius = w * 0.42f
                val outerRadius = w * 0.52f
                val textRadius = outerRadius + with(density) { 14.dp.toPx() }

                val arcSweepDeg = 180f
                val arcStartDeg = -arcSweepDeg / 2f
                val segments = 200

                // Helper: smooth fading needle arc (center thick → endpoints hairline).
                fun drawNeedleArc(
                    radius: Float,
                    maxStrokePx: Float,
                    minStrokePx: Float,
                    maxAlpha: Float
                ) {
                    val fadeRange = 0.50f
                    for (i in 0 until segments) {
                        val segStart = i / segments.toFloat()
                        val segEnd = (i + 1) / segments.toFloat()
                        val distFromEndpoint = minOf(segStart, 1f - segStart)
                        val alphaFactor = if (distFromEndpoint > fadeRange) 1f
                        else (distFromEndpoint / fadeRange).pow(2.0f)
                        val segAlpha = maxAlpha * alphaFactor
                        if (segAlpha <= 0.001f) continue
                        val strokeFactor = if (distFromEndpoint > fadeRange) 1f
                        else 0.15f + 0.85f * (distFromEndpoint / fadeRange).pow(2.0f)
                        val segStroke = minStrokePx + (maxStrokePx - minStrokePx) * strokeFactor
                        drawArc(
                            color = Color.White.copy(alpha = segAlpha),
                            startAngle = arcStartDeg + segStart * arcSweepDeg,
                            sweepAngle = (segEnd - segStart) * arcSweepDeg,
                            useCenter = false,
                            topLeft = Offset(pivotX - radius, pivotY - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = segStroke)
                        )
                    }
                }

                // ★ BOTH arcs thick + bright (Lutea's signature needle arc — DO NOT change).
                drawNeedleArc(
                    radius = innerRadius,
                    maxStrokePx = with(density) { 3.5.dp.toPx() },
                    minStrokePx = with(density) { 0.5.dp.toPx() },
                    maxAlpha = 0.8f
                )
                drawNeedleArc(
                    radius = outerRadius,
                    maxStrokePx = with(density) { 3.5.dp.toPx() },
                    minStrokePx = with(density) { 0.5.dp.toPx() },
                    maxAlpha = 0.8f
                )

                // === 4. TEXT ITEMS + BALLS on the wheel ===
                if (playlists.isNotEmpty()) {
                    val rotationItems = scrollOffset.value / pxPerItem
                    // ★ 14° spacing = breathable gap between labels (was 8°, too tight).
                    //    Vertical gap between adjacent items at this radius:
                    //    0.52w × sin(14°) ≈ 0.126w ≈ 136px on 1080px screen.
                    //    With 42sp active text (~56px), gap ≈ 80px — plenty of room.
                    val angleStepDeg = 14f
                    val activeFontSp = 42f
                    val inactiveFontSp = 16f
                    // ±4 = 9 items total (4+1+4). At 14° × 4 = 56°, items stay on-screen
                    // (cos(56°) = 0.559, ballX = -0.20w + 0.52w×0.559 = 0.091w > 0 ✓).
                    val visibleSpan = 4

                    for (offset in -visibleSpan..visibleSpan) {
                        val rawIdx = (rotationItems.roundToInt() - offset)
                        val modIdx = ((rawIdx % playlists.size) + playlists.size) % playlists.size
                        val playlist = playlists[modIdx]

                        val fractionalOffset = rotationItems - rotationItems.roundToInt() + offset
                        val absOffset = abs(fractionalOffset)
                        if (absOffset > visibleSpan) continue

                        val itemAngleDeg = fractionalOffset * angleStepDeg
                        val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                        val itemX = pivotX + textRadius * cos(itemAngleRad)
                        val itemY = pivotY + textRadius * sin(itemAngleRad)

                        // Skip if off-screen.
                        if (itemX < -200f || itemX > w + 200f) continue

                        val isActive = absOffset < 0.5f

                        // Coral's smooth opacity falloff (smooth lerp between integer positions).
                        val alpha = when {
                            absOffset < 0.5f -> 1f
                            absOffset < 1.5f -> lerp(1.00f, 0.70f, (absOffset - 0.5f))
                            absOffset < 2.5f -> lerp(0.70f, 0.45f, (absOffset - 1.5f))
                            absOffset < 3.5f -> lerp(0.45f, 0.25f, (absOffset - 2.5f))
                            absOffset < 4.5f -> lerp(0.25f, 0.07f, (absOffset - 3.5f))
                            absOffset < 5.5f -> lerp(0.07f, 0.02f, (absOffset - 4.5f))
                            else -> lerp(0.02f, 0f, (absOffset - 5.5f).coerceIn(0f, 1f))
                        }.coerceIn(0f, 1f)

                        // Scale: 1.0 at apex → 0.70 → 0.55 → 0.45
                        val scale = when {
                            absOffset < 0.5f -> 1f
                            absOffset < 1.5f -> lerp(1f, 0.70f, (absOffset - 0.5f))
                            absOffset < 2.5f -> lerp(0.70f, 0.55f, (absOffset - 1.5f))
                            else -> lerp(0.55f, 0.45f, (absOffset - 2.5f).coerceIn(0f, 1f))
                        }

                        // === INNER ARC BALL (counter-rotating, pully coupling) ===
                        // Solid white balls (Lutea look — no accent gradient on balls).
                        val firstArcFractionalOffset = -fractionalOffset
                        val firstArcAngleDeg = firstArcFractionalOffset * angleStepDeg
                        val firstArcAngleRad = (firstArcAngleDeg * PI / 180f).toFloat()
                        val firstArcBallX = pivotX + innerRadius * cos(firstArcAngleRad)
                        val firstArcBallY = pivotY + innerRadius * sin(firstArcAngleRad)
                        val firstArcBallRadiusPx = with(density) { 3.dp.toPx() }
                        if (alpha > 0.01f) {
                            drawCircle(
                                color = Color.White,
                                radius = firstArcBallRadiusPx,
                                center = Offset(firstArcBallX, firstArcBallY),
                                alpha = alpha
                            )
                        }

                        // === OUTER ARC BALL (text orbit) ===
                        // Solid white balls (Lutea look — no accent gradient on balls).
                        val ballRadiusPx = with(density) { 4.dp.toPx() }
                        val ballX = pivotX + outerRadius * cos(itemAngleRad)
                        val ballY = pivotY + outerRadius * sin(itemAngleRad)
                        if (alpha > 0.01f) {
                            drawCircle(
                                color = Color.White,
                                radius = ballRadiusPx,
                                center = Offset(ballX, ballY),
                                alpha = alpha
                            )
                        }

                        // === TEXT ===
                        val displayText = playlist.name.replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.uppercaseChar().toString() else ch.toString()
                        }
                        // Active = accent color, inactive = white (Coral text-color rule).
                        val textColor = if (isActive) accentColor else Color.White

                        val gapAfterBallPx = with(density) { 6.dp.toPx() }
                        val maxTextWidthPx = with(density) { 173.dp.toPx() }
                        val baseFontSp = lerp(activeFontSp, inactiveFontSp, (1f - scale).coerceIn(0f, 1f))
                        val textFontFamily = if (isActive) CalSansFamily else NyghtSerifFamily
                        val textFontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Light
                        val textFontStyle = if (isActive) FontStyle.Normal else FontStyle.Italic

                        var fontSp = baseFontSp
                        var textLayout = textMeasurer.measure(
                            text = AnnotatedString(displayText),
                            style = TextStyle(
                                color = textColor,
                                fontSize = fontSp.sp,
                                fontWeight = textFontWeight,
                                fontStyle = textFontStyle,
                                fontFamily = textFontFamily
                            ),
                            overflow = TextOverflow.Visible,
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(
                                maxWidth = Int.MAX_VALUE,
                                maxHeight = Int.MAX_VALUE
                            )
                        )

                        // SHRINK ONLY: if text wider than 173dp, scale font down to fit.
                        val measuredWidth = textLayout.size.width.toFloat()
                        if (measuredWidth > maxTextWidthPx && measuredWidth > 0f) {
                            val shrinkRatio = maxTextWidthPx / measuredWidth
                            fontSp = (baseFontSp * shrinkRatio).coerceAtLeast(8f)
                            textLayout = textMeasurer.measure(
                                text = AnnotatedString(displayText),
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = fontSp.sp,
                                    fontWeight = textFontWeight,
                                    fontStyle = textFontStyle,
                                    fontFamily = textFontFamily
                                ),
                                overflow = TextOverflow.Visible,
                                maxLines = 1,
                                softWrap = false,
                                constraints = Constraints(
                                    maxWidth = Int.MAX_VALUE,
                                    maxHeight = Int.MAX_VALUE
                                )
                            )
                        }

                        val textW = textLayout.size.width.toFloat()
                        val textH = textLayout.size.height.toFloat()
                        val textOffsetPx = ballRadiusPx + gapAfterBallPx + textW / 2f
                        // Anchor: just past the OUTER ball, extending radially outward.
                        val textCenterX = ballX + textOffsetPx * cos(itemAngleRad)
                        val textCenterY = ballY + textOffsetPx * sin(itemAngleRad)
                        val radialDeg = itemAngleDeg

                        // Adjacent items get a vertical gradient (white → accentColor).
                        val isAdjacent = absOffset >= 0.5f && absOffset < 1.5f
                        val textBrush: Brush? = if (isAdjacent) {
                            if (fractionalOffset < 0) {
                                Brush.verticalGradient(
                                    colors = listOf(Color.White, accentColor),
                                    startY = 0f,
                                    endY = textH
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(accentColor, Color.White),
                                    startY = 0f,
                                    endY = textH
                                )
                            }
                        } else null

                        if (alpha > 0.01f) {
                            drawContext.canvas.save()
                            drawContext.canvas.translate(textCenterX, textCenterY)
                            drawContext.canvas.rotate(radialDeg)
                            if (textBrush != null) {
                                drawText(
                                    textLayoutResult = textLayout,
                                    topLeft = Offset(-textW / 2f, -textH / 2f),
                                    alpha = alpha,
                                    brush = textBrush
                                )
                            } else {
                                drawText(
                                    textLayoutResult = textLayout,
                                    topLeft = Offset(-textW / 2f, -textH / 2f),
                                    alpha = alpha
                                )
                            }
                            drawContext.canvas.restore()
                        }
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────
        // 2) "+ NEW PLAYLIST" CAPSULE — TOP CENTER, beside the settings button.
        //    statusBarsPadding() lines it up with the settings cog (Law #1).
        // ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showCreateDialog = true }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "+ New Playlist",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
            )
        }
    }
}

/** Linear interpolation between two floats. */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
