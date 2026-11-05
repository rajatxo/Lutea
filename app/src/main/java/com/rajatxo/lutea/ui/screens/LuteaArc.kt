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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
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
import com.rajatxo.lutea.data.prefs.PlaylistStyle
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
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
    titleFontSize: Int = 28,
    playlistStyle: PlaylistStyle = PlaylistStyle.WHEEL_SIMPLER,
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
        // ★ 10-letter limit — only a-z/A-Z count. Spaces are allowed and don't
        //    consume a letter slot. Numbers and special chars are filtered out.
        //    Example: "My Mix" = 5 letters (M-y-M-i-x), 1 space = OK.
        //             "Chase Atlantic" = 13 letters = blocked at 10.
        val MAX_LETTERS = 10
        fun letterCount(s: String): Int = s.count { it.isLetter() }
        val remainingLetters = MAX_LETTERS - letterCount(playlistName)
        val isOverLimit = remainingLetters < 0

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
                    onValueChange = { newValue ->
                        // Filter: only allow letters and spaces (no numbers/symbols).
                        val filtered = newValue.filter { it.isLetter() || it.isWhitespace() }
                        // Block if adding another letter would exceed the 10-letter limit.
                        // Spaces are always allowed (don't count toward the limit).
                        val filteredLetters = letterCount(filtered)
                        if (filteredLetters <= MAX_LETTERS) {
                            playlistName = filtered
                        }
                    },
                    placeholder = { Text("Playlist name (max 10 letters)") },
                    singleLine = true,
                    isError = isOverLimit,
                    supportingText = {
                        Text(
                            text = "$remainingLetters letters left",
                            color = if (remainingLetters <= 2) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank() && !isOverLimit) {
                            PlaylistStore.createPlaylist(playlistName.trim())
                        }
                        showCreateDialog = false
                    },
                    enabled = playlistName.isNotBlank() && !isOverLimit,
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3A3A3A),  // charcoal gray at top
                        Color(0xFF000000),  // pure black at bottom
                    )
                )
            ),
    ) {
        // ──────────────────────────────────────────────────────────────
        // Wheel Canvas — full-screen drag handlers + drawing
        // ★ Advanced mode: NO drag, NO tap, NO haptics — wheel is static.
        //    Simpler mode: full interaction (drag, fling, tap-to-open).
        // ──────────────────────────────────────────────────────────────
        val isAdvanced = playlistStyle == PlaylistStyle.WHEEL_ADVANCED
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isAdvanced) Modifier
                    else Modifier
                        .pointerInput(playlists.size) {
                            val halfScreen = size.width / 2
                            var velocityTracker = VelocityTracker()
                            var isLeftZoneDrag = false
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    isLeftZoneDrag = offset.x < halfScreen
                                    if (isLeftZoneDrag) { velocityTracker = VelocityTracker() }
                                },
                                onDragEnd = {
                                    if (isLeftZoneDrag) {
                                        val velocity = velocityTracker.calculateVelocity().y
                                        coroutineScope.launch {
                                            scrollOffset.animateDecay(
                                                initialVelocity = -velocity * 0.45f,
                                                animationSpec = exponentialDecay(frictionMultiplier = 2.5f)
                                            )
                                            val nearest = (scrollOffset.value / pxPerItem).roundToInt()
                                            scrollOffset.animateTo(nearest * pxPerItem, spring(DampingRatioNoBouncy, StiffnessLow))
                                        }
                                    }
                                    isLeftZoneDrag = false
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    if (isLeftZoneDrag) {
                                        coroutineScope.launch { scrollOffset.snapTo(scrollOffset.value - dragAmount) }
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val currentIdx = indexAtOffset(scrollOffset.value)
                                        if (currentIdx != lastSnappedIndex) { lastSnappedIndex = currentIdx; tickHaptic() }
                                        change.consume()
                                    }
                                }
                            )
                        }
                        .pointerInput(playlists.size) {
                            var velocityTracker = VelocityTracker()
                            detectVerticalDragGestures(
                                onDragStart = { velocityTracker = VelocityTracker() },
                                onDragEnd = {
                                    val velocity = velocityTracker.calculateVelocity().y
                                    coroutineScope.launch {
                                        scrollOffset.animateDecay(velocity * 0.30f, exponentialDecay(frictionMultiplier = 2.5f))
                                        val nearest = (scrollOffset.value / pxPerItem).roundToInt()
                                        scrollOffset.animateTo(nearest * pxPerItem, spring(DampingRatioNoBouncy, StiffnessLow))
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    coroutineScope.launch { scrollOffset.snapTo(scrollOffset.value + dragAmount) }
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val currentIdx = indexAtOffset(scrollOffset.value)
                                    if (currentIdx != lastSnappedIndex) { lastSnappedIndex = currentIdx; tickHaptic() }
                                    change.consume()
                                }
                            )
                        }
                        .pointerInput(playlists.size) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val centerY = size.height / 2f
                                    if (abs(offset.y - centerY) < with(density) { 80.dp.toPx() } && playlists.isNotEmpty()) {
                                        val centerIdx = indexAtOffset(scrollOffset.value)
                                        playlists.getOrNull(centerIdx)?.let { onOpenPlaylist(it) }
                                    }
                                }
                            )
                        }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // === 1. PIVOT (off-screen, left) ===
                // ★ Simpler: pivot at -0.20w (original).
                //    Advanced: pivot at the LEFT EDGE of the screen (x=0), radius = 0.28w.
                //    This gives a proper semicircle — the right half of a circle
                //    centered on the left edge, like the mockup. Arc touches the
                //    left edge at top and bottom, bulges right to 28% of screen width.
                val isAdvanced = playlistStyle == PlaylistStyle.WHEEL_ADVANCED
                val pivotX = if (isAdvanced) 0f else w * -0.20f
                val pivotY = h * 0.50f

                // === 2. RADII SYSTEM ===
                // Simpler: two arcs (inner 0.42w + outer 0.52w), text past outer arc.
                // Advanced: ONE arc at 0.28w — small circle centered at left edge.
                val innerRadius = if (isAdvanced) w * 0.28f else w * 0.42f
                val outerRadius = if (isAdvanced) innerRadius else w * 0.52f
                val textRadius = if (isAdvanced) {
                    innerRadius + with(density) { 10.dp.toPx() }
                } else {
                    outerRadius + with(density) { 14.dp.toPx() }
                }

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
                    // ★ Advanced: smooth fade over last 25% of each end (was 15% — too sudden).
                    //    Simpler: keep 50% fade (original).
                    val fadeRange = if (isAdvanced) 0.25f else 0.50f
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

                // ★ Simpler: BOTH arcs thick + bright.
                //    Advanced: ONLY the inner (small) arc — no outer arc.
                drawNeedleArc(
                    radius = innerRadius,
                    maxStrokePx = with(density) { 3.5.dp.toPx() },
                    minStrokePx = with(density) { 0.5.dp.toPx() },
                    maxAlpha = 0.8f
                )
                if (!isAdvanced) {
                    drawNeedleArc(
                        radius = outerRadius,
                        maxStrokePx = with(density) { 3.5.dp.toPx() },
                        minStrokePx = with(density) { 0.5.dp.toPx() },
                        maxAlpha = 0.8f
                    )
                }

                // === 4. TEXT ITEMS + BALLS on the wheel ===
                if (playlists.isNotEmpty()) {
                    val rotationItems = scrollOffset.value / pxPerItem
                    // ★ 14° spacing = breathable gap between labels.
                    //    Vertical gap = 0.52w × sin(14°) ≈ 136px on 1080px screen.
                    //    Keeps the original spacing — outer items extend vertically along
                    //    the arc, even if their balls are slightly off-screen left.
                    val angleStepDeg = 14f
                    val activeFontSp = 42f
                    val inactiveFontSp = 16f
                    // ±5 = 11 items total (5+1+5), arranged VERTICALLY along the arc curve.
                    // The outermost 2 items are at the very top/bottom of the visible stack.
                    val visibleSpan = 5

                    for (offset in -visibleSpan..visibleSpan) {
                        val rawIdx = (rotationItems.roundToInt() - offset)
                        val modIdx = ((rawIdx % playlists.size) + playlists.size) % playlists.size
                        val playlist = playlists[modIdx]

                        val fractionalOffset = rotationItems - rotationItems.roundToInt() + offset
                        val absOffset = abs(fractionalOffset)
                        // ★ Anti-blink: let items render past visibleSpan so they smoothly
                        //    fade to alpha 0 instead of being abruptly cut off mid-fade.
                        if (absOffset > visibleSpan + 0.5f) continue

                        val itemAngleDeg = fractionalOffset * angleStepDeg
                        val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                        val itemX = pivotX + textRadius * cos(itemAngleRad)
                        val itemY = pivotY + textRadius * sin(itemAngleRad)

                        // Skip if off-screen.
                        if (itemX < -200f || itemX > w + 200f) continue

                        val isActive = absOffset < 0.5f

                        // ★ Opacity falloff — exact user spec (mirrored top + bottom).
                        //    Position 1 (absOffset 0) = main, colourful accent, 100%
                        //    Position 2 (absOffset 1) = gradient white→accent (kept unchanged)
                        //    Position 3 (absOffset 2) = pure white 90%
                        //    Position 4 (absOffset 3) = 70%
                        //    Position 5 (absOffset 4) = 45%
                        //    Position 6 (absOffset 5) = 25%
                        //    Smooth lerp between integer positions; mirror applies automatically
                        //    because abs(fractionalOffset) is symmetric.
                        val alpha = when {
                            absOffset < 0.5f -> 1f                                                       // position 1: active, full
                            absOffset < 1.5f -> lerp(1.00f, 0.85f, (absOffset - 0.5f))                   // position 2: gradient zone (kept)
                            absOffset < 2.5f -> lerp(0.85f, 0.90f, (absOffset - 1.5f))                   // ramp to 90% pure white at position 3
                            absOffset < 3.5f -> lerp(0.90f, 0.70f, (absOffset - 2.5f))                   // position 4: 70%
                            absOffset < 4.5f -> lerp(0.70f, 0.45f, (absOffset - 3.5f))                   // position 5: 45%
                            absOffset < 5.5f -> lerp(0.45f, 0.25f, (absOffset - 4.5f))                   // position 6: 25%
                            else -> lerp(0.25f, 0f, (absOffset - 5.5f).coerceIn(0f, 1f))                 // smooth fade-out (anti-blink)
                        }.coerceIn(0f, 1f)

                        // Scale: 1.0 at apex → 0.70 → 0.55 → 0.45
                        val scale = when {
                            absOffset < 0.5f -> 1f
                            absOffset < 1.5f -> lerp(1f, 0.70f, (absOffset - 0.5f))
                            absOffset < 2.5f -> lerp(0.70f, 0.55f, (absOffset - 1.5f))
                            else -> lerp(0.55f, 0.45f, (absOffset - 2.5f).coerceIn(0f, 1f))
                        }

                        // === INNER ARC BALL (counter-rotating, pully coupling) ===
                        // ★ Simpler: balls on inner arc. Advanced: NO balls at all.
                        if (!isAdvanced) {
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
                        }

                        // === OUTER ARC BALL (text orbit) ===
                        // ★ Simpler: balls on outer arc. Advanced: NO outer balls.
                        val ballRadiusPx = with(density) { 4.dp.toPx() }
                        val ballX = pivotX + outerRadius * cos(itemAngleRad)
                        val ballY = pivotY + outerRadius * sin(itemAngleRad)
                        if (alpha > 0.01f && !isAdvanced) {
                            drawCircle(
                                color = Color.White,
                                radius = ballRadiusPx,
                                center = Offset(ballX, ballY),
                                alpha = alpha
                            )
                        }

                        // ★ Text labels are now rendered as separate Composables in the
                        //    overlay below (for per-item blur via graphicsLayer). Nothing
                        //    text-related is drawn here — prevents the "shadow/ghost" double
                        //    rendering that happened when text was drawn in BOTH the Canvas
                        //    (sharp) and the overlay (blurred).
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────
        // ★ PROGRESSIVE BLUR OVERLAY — ALL 11 text labels rendered as separate
        //    Composables so each can have its own `graphicsLayer { renderEffect }`.
        //
        //    Text is NO LONGER drawn in the Canvas — this prevents the "shadow"
        //    double-rendering (sharp underneath + blurred on top).
        //
        //    ★ Advanced mode: text overlay is DISABLED for now — we're reworking
        //      the arc shape first. Only the arc + balls are drawn.
        // ──────────────────────────────────────────────────────────────
        if (playlists.isNotEmpty() && playlistStyle == PlaylistStyle.WHEEL_SIMPLER) {
            // ★ Use BoxWithConstraints to get the ACTUAL measured width/height (not
            //    configuration.screenWidthDp which includes system bars and is wrong
            //    for the wheel's coordinate system). The Canvas uses size.width which
            //    is the same as maxWidth here — this keeps the overlay aligned with
            //    the arcs/balls drawn in the Canvas.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val w = with(density) { maxWidth.toPx() }
                val h = with(density) { maxHeight.toPx() }
                val rotationItems = scrollOffset.value / pxPerItem
                val angleStepDeg = 14f
                val visibleSpan = 5
                // ★ Advanced: smaller fonts (text hugs the small arc, less room).
                //    Simpler: original sizes.
                val isAdvancedOverlay = playlistStyle == PlaylistStyle.WHEEL_ADVANCED
                val activeFontSp = if (isAdvancedOverlay) 32f else 42f
                val inactiveFontSp = if (isAdvancedOverlay) 13f else 16f
                val maxBlurDp = 30f

            // Cache RenderEffects (Mochy pattern) — quantize to avoid thousands of allocations.
            val blurCache = remember { mutableMapOf<Int, androidx.compose.ui.graphics.RenderEffect>() }
            fun getBlurEffect(radiusDp: Float): androidx.compose.ui.graphics.RenderEffect? {
                if (radiusDp <= 0.1f) return null
                val key = (radiusDp * 10).toInt()  // quantize to 0.1dp steps
                return blurCache.getOrPut(key) {
                    val radiusPx = with(density) { radiusDp.dp.toPx() }
                    android.graphics.RenderEffect.createBlurEffect(
                        radiusPx, radiusPx,
                        android.graphics.Shader.TileMode.DECAL
                    ).asComposeRenderEffect()
                }
            }

            // ★ SMOOTH blur gradient — no sudden jumps. Starts barely visible at position 3,
            //    ramps through 10% (pos 4) → 35% (pos 5) → 50% (pos 6).
            fun blurFor(absOffset: Float): Float = when {
                absOffset < 2.0f -> 0f                                                       // positions 1-2: sharp
                absOffset < 3.0f -> lerp(0f, maxBlurDp * 0.03f, (absOffset - 2.0f))          // position 3: tiny blur (3% of max)
                absOffset < 4.0f -> lerp(maxBlurDp * 0.03f, maxBlurDp * 0.10f, (absOffset - 3.0f))  // position 4: 10% (gradient to clear)
                absOffset < 5.0f -> lerp(maxBlurDp * 0.10f, maxBlurDp * 0.35f, (absOffset - 4.0f))  // position 5: 35%
                absOffset < 5.5f -> lerp(maxBlurDp * 0.35f, maxBlurDp * 0.50f, (absOffset - 5.0f))  // position 6: 50%
                else -> maxBlurDp * 0.50f
            }

            val pivotX = w * -0.20f
            val pivotY = h * 0.50f
            // ★ Match the Canvas geometry: Advanced uses innerRadius only.
            val innerRadiusOv = w * 0.42f
            val outerRadius = if (isAdvancedOverlay) innerRadiusOv else w * 0.52f
            val textRadiusOv = if (isAdvancedOverlay) {
                innerRadiusOv + with(density) { 10.dp.toPx() }
            } else {
                outerRadius + with(density) { 14.dp.toPx() }
            }
            val ballRadiusPx = with(density) { 4.dp.toPx() }
            val gapAfterBallPx = if (isAdvancedOverlay) with(density) { 4.dp.toPx() } else with(density) { 6.dp.toPx() }
            val maxTextWidthPx = with(density) { 173.dp.toPx() }

            for (offset in -visibleSpan..visibleSpan) {
                val rawIdx = (rotationItems.roundToInt() - offset)
                val modIdx = ((rawIdx % playlists.size) + playlists.size) % playlists.size
                val playlist = playlists[modIdx]

                val fractionalOffset = rotationItems - rotationItems.roundToInt() + offset
                val absOffset = abs(fractionalOffset)
                if (absOffset > visibleSpan + 0.5f) continue

                val itemAngleDeg = fractionalOffset * angleStepDeg
                val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                val isActive = absOffset < 0.5f

                // ★ Opacity per user spec (text opacity, NOT blur opacity):
                //    Position 1 (main): accent colour, 100%
                //    Position 2 (gradient): kept
                //    Position 3: 100% white
                //    Position 4: 80%
                //    Position 5: 70%
                //    Position 6: 61%
                val alpha = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> lerp(1.00f, 0.85f, (absOffset - 0.5f))
                    absOffset < 2.5f -> lerp(0.85f, 1.00f, (absOffset - 1.5f))    // position 3: 100%
                    absOffset < 3.5f -> lerp(1.00f, 0.80f, (absOffset - 2.5f))    // position 4: 80%
                    absOffset < 4.5f -> lerp(0.80f, 0.70f, (absOffset - 3.5f))    // position 5: 70%
                    absOffset < 5.5f -> lerp(0.70f, 0.61f, (absOffset - 4.5f))    // position 6: 61%
                    else -> lerp(0.61f, 0f, (absOffset - 5.5f).coerceIn(0f, 1f))
                }.coerceIn(0f, 1f)
                if (alpha < 0.02f) continue

                // ★ Scale — gentler shrink for a more uniform circular arrangement.
                //    Was: 1.0 → 0.70 → 0.55 → 0.45 (too aggressive, made outer text
                //    much smaller and broke the circular shape).
                //    Now: 1.0 → 0.92 → 0.85 → 0.80 (text stays closer in size,
                //    so all 11 labels form a clean arc — 95% perfect circle).
                val scale = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> lerp(1f, 0.92f, (absOffset - 0.5f))
                    absOffset < 2.5f -> lerp(0.92f, 0.85f, (absOffset - 1.5f))
                    absOffset < 3.5f -> lerp(0.85f, 0.82f, (absOffset - 2.5f))
                    absOffset < 4.5f -> lerp(0.82f, 0.80f, (absOffset - 3.5f))
                    else -> lerp(0.80f, 0.78f, (absOffset - 4.5f).coerceIn(0f, 1f))
                }

                // ★ Billie Eilish font for both — distinguished by CASE:
                //    Active   = UPPERCASE (large, accent colour)
                //    Inactive = lowercase (smaller, white/faded)
                val displayText = if (isActive) playlist.name.uppercase() else playlist.name.lowercase()
                val textColor = if (isActive) accentColor else Color.White
                var baseFontSp = lerp(activeFontSp, inactiveFontSp, (1f - scale).coerceIn(0f, 1f))
                val fontFamily = BillieEilishFamily
                val fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                val fontStyle = FontStyle.Normal

                // ★ Auto-fit: measure text, shrink font if wider than 173dp budget.
                val textLayout = textMeasurer.measure(
                    text = AnnotatedString(displayText),
                    style = TextStyle(
                        color = textColor,
                        fontSize = baseFontSp.sp,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        fontFamily = fontFamily,
                    ),
                    overflow = TextOverflow.Visible,
                    maxLines = 1,
                    softWrap = false,
                    constraints = Constraints(
                        maxWidth = Int.MAX_VALUE,
                        maxHeight = Int.MAX_VALUE,
                    ),
                )
                val measuredWidth = textLayout.size.width.toFloat()
                if (measuredWidth > maxTextWidthPx && measuredWidth > 0f) {
                    val shrinkRatio = maxTextWidthPx / measuredWidth
                    baseFontSp = (baseFontSp * shrinkRatio).coerceAtLeast(8f)
                }

                val textW = textLayout.size.width.toFloat()
                val textH = textLayout.size.height.toFloat()
                val textOffsetPx = ballRadiusPx + gapAfterBallPx + textW / 2f
                val ballX = pivotX + outerRadius * cos(itemAngleRad)
                val ballY = pivotY + outerRadius * sin(itemAngleRad)
                val textCenterX = ballX + textOffsetPx * cos(itemAngleRad)
                val textCenterY = ballY + textOffsetPx * sin(itemAngleRad)

                // ★ Gradient brush for adjacent items (position 2) — white → accent.
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

                val blurRadiusDp = blurFor(absOffset)
                val blurEffect = getBlurEffect(blurRadiusDp)

                // ★ Position the text so its LEFT-CENTER is at the ball + gap position.
                //    The text rotates around its LEFT-CENTER (transformOrigin 0, 0.5),
                //    so we need to move the text up by half its height so the left-center
                //    lands exactly at the ball position.
                val textLeftX = ballX + (ballRadiusPx + gapAfterBallPx) * cos(itemAngleRad)
                val textLeftY = ballY + (ballRadiusPx + gapAfterBallPx) * sin(itemAngleRad)
                // ★ Compensate: move text up by textH/2 so the rotation pivot (left-center)
                //    is at the ball position. Without this, the text's top-left corner sits
                //    at the ball, making the text appear ~half-text-height too low.
                val pivotXpx = textLeftX
                val pivotYpx = textLeftY - textH / 2f

                val translationXDp = with(density) { pivotXpx.toDp() }
                val translationYDp = with(density) { pivotYpx.toDp() }

                // ★ Use TextStyle with brush for gradient text, or color for solid.
                val style = if (textBrush != null) {
                    TextStyle(
                        brush = textBrush,
                        fontSize = baseFontSp.sp,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        fontFamily = fontFamily,
                    )
                } else {
                    TextStyle(
                        color = textColor,
                        fontSize = baseFontSp.sp,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        fontFamily = fontFamily,
                    )
                }

                Text(
                    text = displayText,
                    style = style,
                    modifier = Modifier
                        .graphicsLayer {
                            this.alpha = alpha
                            this.translationX = with(density) { translationXDp.toPx() }
                            this.translationY = with(density) { translationYDp.toPx() }
                            this.rotationZ = itemAngleDeg
                            this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            this.renderEffect = blurEffect
                            this.compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        },
                )
            }
            }  // close BoxWithConstraints
        }

        // ──────────────────────────────────────────────────────────────
        // ★ VERTICAL SLIDER (Advanced mode only) — right edge of screen.
        //    Two layers: long gray capsule (track) + small narrow capsule (thumb).
        //    Thumb position reflects which playlist is currently selected.
        // ──────────────────────────────────────────────────────────────
        if (playlistStyle == PlaylistStyle.WHEEL_ADVANCED && playlists.isNotEmpty()) {
            val continuousIdx = ((scrollOffset.value % playlists.size) + playlists.size) % playlists.size
            // ★ Snap knobFraction to nearest item so the thumb doesn't jitter.
            val snappedFraction = (continuousIdx.roundToInt().toFloat() / playlists.size).coerceIn(0f, 1f)

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // ═══ SLIDER VALUES ═══
                val sliderHeight = maxHeight * 0.55f
                val trackWidth = 7.dp
                val trackColor = Color.White.copy(alpha = 0.15f)
                val trackCorner = 3.dp
                val trackPaddingEnd = maxWidth * 0.08f
                // ★ Thumb — dark capsule with ∧, =, ∨ symbols (like the mockup)
                val thumbWidth = 16.dp
                val thumbHeight = 52.dp
                val thumbColor = Color(0xFF2A2A2A)  // dark charcoal
                val thumbCorner = 8.dp
                // Thumb Y: snapped position (no jitter)
                val thumbY = (sliderHeight - thumbHeight) * (1f - snappedFraction)

                // Track (long gray capsule)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = trackPaddingEnd)
                        .width(trackWidth)
                        .height(sliderHeight)
                        .clip(RoundedCornerShape(trackCorner))
                        .background(trackColor)
                )

                // Thumb (dark capsule with symbols — sits ON the track)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = trackPaddingEnd - (thumbWidth - trackWidth) / 2f)
                        .offset(y = thumbY - (sliderHeight - thumbHeight) / 2f)
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .clip(RoundedCornerShape(thumbCorner))
                        .background(thumbColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("∧", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = CalSansFamily)
                        Text("=", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = CalSansFamily)
                        Text("∨", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = CalSansFamily)
                    }
                }

                // ═══ MAIN TEXT ONLY (center playlist name) ═══
                // Positioned to NOT overlap with the slider bar.
                val centerIdx = indexAtOffset(scrollOffset.value)
                val centerPlaylist = playlists.getOrNull(centerIdx)
                if (centerPlaylist != null) {
                    val displayText = centerPlaylist.name.uppercase()
                    Text(
                        text = displayText,
                        color = Color(0xFFF4B400),  // accent gold
                        fontSize = 42.sp,
                        fontFamily = BillieEilishFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(end = maxWidth * 0.16f)  // ★ shift left to avoid overlap with slider
                    )
                }
            }
        }

        // ──────────────────────────────────────────────────────────────
        // ★ "Choose your playlist" — big title in CalSans (like the mockup's
        //    "Choose your destination"). Top-left, below status bar.
        // ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 24.dp, top = 16.dp, end = 80.dp)
        ) {
            Text(
                text = "Choose your",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = titleFontSize.sp,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "playlist",
                color = Color.White,
                fontSize = titleFontSize.sp,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Linear interpolation between two floats. */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
