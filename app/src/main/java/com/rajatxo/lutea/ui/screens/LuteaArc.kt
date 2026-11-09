package com.rajatxo.lutea.ui.screens

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntOffset
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
import com.rajatxo.lutea.ui.components.Lutea3DThumb
import com.rajatxo.lutea.data.prefs.UserNameManager
import com.rajatxo.lutea.ui.foundation.LocalSearchOpen
import com.rajatxo.lutea.ui.foundation.LocalSearchTrigger
import com.rajatxo.lutea.ui.icons.LuteaIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
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

    // ── Search state (hoisted so the wheel can filter playlists) ──
    var searchQuery by remember { mutableStateOf("") }
    val searchHistory = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val searchOpenState by LocalSearchOpen.current

    // ── Wheel rotation state (Animatable for physics fling + spring snap) ──
    val wheelPrefs = remember { context.getSharedPreferences("lutea_wheel", android.content.Context.MODE_PRIVATE) }
    // ★ Save RAW scroll value (float) — remembers exact thumb position on restart.
    val savedScroll = remember { wheelPrefs.getFloat("wheel_scroll_offset", 0f) }
    val savedIndex = remember { wheelPrefs.getInt("wheel_center_index", 0) }
    val pxPerItem = with(density) { 64.dp.toPx() }
    val scrollOffset = remember { Animatable(savedScroll) }
    var lastSnappedIndex by remember { mutableStateOf(savedIndex) }

    // ★ True while the wheel is moving (finger drag, fling, snap or search jump), false ~160ms
    //    after it stops. Drives the slider thumb's LED: white when idle, glowing when rotating.
    var isWheelRotating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { scrollOffset.value }
            .drop(1) // ignore the initial value so the LED doesn't flash on open
            .collectLatest {
                isWheelRotating = true
                delay(160)
                isWheelRotating = false
            }
    }

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
        // ★ Uses Vibrator API directly with createOneShot — works on ALL devices.
        //    10ms one-shot = very light vibration, less than the nav bar's 25ms.
        //    performHapticFeedback was unreliable (CLOCK_TICK not supported on
        //    some devices, returning false and producing no vibration at all).
        try {
            vibrator?.let { v ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(
                        android.os.VibrationEffect.createOneShot(
                            10,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(10)
                }
            }
        } catch (_: Exception) { }
        // ★ Tick sound temporarily DISABLED — uncomment to re-enable
        // if (soundLoaded) {
        //     try { soundPool.play(tickSoundId, 0.6f, 0.6f, 1, 0, 1f) } catch (_: Exception) { }
        // }
    }

    fun indexAtOffset(offset: Float): Int {
        if (playlists.isEmpty()) return 0
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % playlists.size.coerceAtLeast(1)
        return if (mod < 0) mod + playlists.size else mod
    }

    val centerIndex = remember(scrollOffset.value) { indexAtOffset(scrollOffset.value) }

    // Persist the wheel position — debounced: only save when centerIndex changes
    // (NOT on every scrollOffset.value frame). This prevents 60fps SharedPreferences
    // thrashing that causes first-drag lag.
    var lastSavedCenter by remember { mutableIntStateOf(centerIndex) }
    LaunchedEffect(centerIndex) {
        if (centerIndex != lastSavedCenter) {
            lastSavedCenter = centerIndex
            wheelPrefs.edit()
                .putInt("wheel_center_index", centerIndex)
                .putFloat("wheel_scroll_offset", scrollOffset.value)
                .apply()
        }
    }

    // ── Search: when query changes, rotate wheel to first match ──
    //    On first letter: rotate one full cycle (for animation flourish) then
    //    settle on the first matching playlist. Non-matching items get alpha=0
    //    in the text overlay (eliminated from view).
    LaunchedEffect(searchQuery, playlists) {
        if (searchQuery.isNotBlank() && playlists.isNotEmpty()) {
            val query = searchQuery.trim()
            val firstMatch = playlists.indexOfFirst {
                it.name.startsWith(query, ignoreCase = true)
            }
            if (firstMatch >= 0) {
                val cycleLength = playlists.size * pxPerItem
                val targetOffset = firstMatch * pxPerItem
                // Add one full cycle so the wheel visually rotates once before settling
                val animatedTarget = targetOffset + cycleLength
                scrollOffset.animateTo(
                    targetValue = animatedTarget,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }

    // ── When search closes, save query to history ──
    LaunchedEffect(searchOpenState) {
        if (!searchOpenState && searchQuery.isNotBlank()) {
            val query = searchQuery.trim()
            if (query !in searchHistory) {
                searchHistory.add(0, query)  // most recent first
                // Keep max 8 history items
                while (searchHistory.size > 8) {
                    searchHistory.removeAt(searchHistory.lastIndex)
                }
            }
            searchQuery = ""  // clear query when search closes
        }
    }

    // Helper: does a playlist match the current search query?
    fun matchesSearch(name: String): Boolean {
        if (searchQuery.isBlank()) return true
        return name.startsWith(searchQuery.trim(), ignoreCase = true)
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
                        // ★ Advanced: BOTH screen drag AND slider drag rotate the wheel.
                        //    Thumb button follows wheel position (connected).
                        .pointerInput(playlists.size) {
                            var velocityTracker = VelocityTracker()
                            detectVerticalDragGestures(
                                onDragStart = { velocityTracker = VelocityTracker() },
                                onDragEnd = {
                                    val velocity = velocityTracker.calculateVelocity().y
                                    coroutineScope.launch {
                                        val cycleLen = playlists.size * pxPerItem
                                        val maxSc = cycleLen * 3f
                                        scrollOffset.animateDecay(velocity * 0.30f, exponentialDecay(frictionMultiplier = 2.5f))
                                        val clamped = scrollOffset.value.coerceIn(0f, maxSc)
                                        scrollOffset.snapTo(clamped)
                                        val nearest = (clamped / pxPerItem).roundToInt()
                                        scrollOffset.animateTo(nearest * pxPerItem, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    coroutineScope.launch {
                                        val cycleLen = playlists.size * pxPerItem
                                        val maxSc = cycleLen * 3f
                                        val newValue = (scrollOffset.value + dragAmount).coerceIn(0f, maxSc)
                                        scrollOffset.snapTo(newValue)
                                    }
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val currentIdx = ((scrollOffset.value / pxPerItem).roundToInt() % playlists.size.coerceAtLeast(1)).let { if (it < 0) it + playlists.size else it }
                                    if (currentIdx != lastSnappedIndex) { lastSnappedIndex = currentIdx; tickHaptic() }
                                    change.consume()
                                }
                            )
                        }
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
                                            scrollOffset.animateTo(nearest * pxPerItem, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
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
                                        scrollOffset.animateTo(nearest * pxPerItem, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
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
            if (playlists.isNotEmpty()) {
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

                // ★ Advanced: main arc only. Simpler: BOTH original arcs (inner + outer).
                drawNeedleArc(
                    radius = innerRadius,
                    maxStrokePx = with(density) { 3.5.dp.toPx() },
                    minStrokePx = with(density) { 0.5.dp.toPx() },
                    maxAlpha = 0.8f
                )
                if (!isAdvanced) {
                    // Simpler mode: outer arc too. Advanced: main arc only (no small inner arc).
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
                        val modIdx = ((rawIdx % playlists.size.coerceAtLeast(1)) + playlists.size) % playlists.size.coerceAtLeast(1)
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
                val modIdx = ((rawIdx % playlists.size.coerceAtLeast(1)) + playlists.size) % playlists.size.coerceAtLeast(1)
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
                }.coerceIn(0f, 1f) * if (matchesSearch(playlist.name)) 1f else 0f  // ★ hide non-matching when searching
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
            // ★ MATH: Slider covers 3 full wheel cycles.
            //    One cycle = N * pxPerItem. Total scroll = 3 * N * pxPerItem.
            //    Thumb maps to the full 3-cycle range (traverses whole track).
            //    Text indices wrap modulo N (so every playlist appears 3 times).
            //    For 100 playlists: total scroll = 3 * 100 * 64dp = 19200dp — fine.
            val cycleLength = playlists.size * pxPerItem  // one full wheel rotation
            val totalCycles = 3f  // user wants 3 rotations per full slider sweep
            val maxScroll = (cycleLength * totalCycles)
            // Thumb fraction = scroll / maxScroll (0 → 1 over the full track)
            val knobFraction = if (maxScroll > 0f) (scrollOffset.value / maxScroll).coerceIn(0f, 1f) else 0.5f

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // ═══ SLIDER VALUES ═══
                val sliderHeight = maxHeight * 0.55f
                val trackWidth = 7.dp
                val trackColor = Color.White.copy(alpha = 0.25f)  // ★ was 0.15f, now 0.25f
                val trackCorner = 3.dp
                val trackPaddingEnd = maxWidth * 0.075f  // ★ was 0.08f, now 0.075f (7.5%)
                // ★ Thumb — Lutea3DThumb is drawn horizontally and rotated -90° to sit on the vertical track.
                //    thumbHeight = its length ALONG the track, thumbThickness = its width ACROSS the track
                //    (keep them about 2.35 : 1). thumbHitWidth = the grab area around the track.
                val thumbHeight = 90.dp
                val thumbThickness = 38.dp
                val thumbHitWidth = thumbThickness + 8.dp
                // Thumb Y: thumb moves DOWN when scroll increases (finger down = thumb down).
                // knobFraction = 0 → thumb at TOP. knobFraction = 1 → thumb at BOTTOM.
                val thumbY = (sliderHeight - thumbHeight) * knobFraction

                // Track (long gray capsule) — DRAG to rotate wheel (clamped, no infinite loop).
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        // keep the visible track where it was, but make the grab area as wide as the thumb
                        .padding(end = (trackPaddingEnd + (trackWidth + 20.dp) / 2 - thumbHitWidth / 2).coerceAtLeast(0.dp))
                        .width(thumbHitWidth)
                        .height(sliderHeight)
                        .pointerInput(playlists.size) {
                            // ★ Fixed: use detectTapGestures for taps + detectVerticalDragGestures for drags.
                            //    detectTapGestures handles single taps reliably (no double-touch needed).
                            detectTapGestures(
                                onTap = { offset ->
                                    val cycleLen = playlists.size * pxPerItem
                                    val maxSc = cycleLen * 3f
                                    val trackHeightPx = size.height.toFloat()
                                    val fraction = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                                    val targetScroll = fraction * maxSc
                                    coroutineScope.launch {
                                        scrollOffset.animateTo(targetScroll, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
                                        val nearest = (targetScroll / pxPerItem).roundToInt()
                                        scrollOffset.animateTo(nearest * pxPerItem, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
                                    }
                                    val currentIdx = ((targetScroll / pxPerItem).roundToInt() % playlists.size.coerceAtLeast(1)).let { if (it < 0) it + playlists.size else it }
                                    if (currentIdx != lastSnappedIndex) { lastSnappedIndex = currentIdx; tickHaptic() }
                                }
                            )
                        }
                        .pointerInput(playlists.size) {
                            // ★ Real-time drag: thumb follows finger directly.
                            val cycleLen = playlists.size * pxPerItem
                            val maxSc = cycleLen * 3f
                            val trackHeightPx = size.height.toFloat()

                            fun jumpToPosition(y: Float) {
                                val fraction = (y / trackHeightPx).coerceIn(0f, 1f)
                                val targetScroll = fraction * maxSc
                                coroutineScope.launch { scrollOffset.snapTo(targetScroll) }
                                val currentIdx = ((targetScroll / pxPerItem).roundToInt() % playlists.size.coerceAtLeast(1)).let { if (it < 0) it + playlists.size else it }
                                if (currentIdx != lastSnappedIndex) { lastSnappedIndex = currentIdx; tickHaptic() }
                            }

                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    jumpToPosition(down.position.y)

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        jumpToPosition(change.position.y)
                                    }

                                    coroutineScope.launch {
                                        val clamped = scrollOffset.value.coerceIn(0f, maxSc)
                                        scrollOffset.snapTo(clamped)
                                        val nearest = (clamped / pxPerItem).roundToInt()
                                        scrollOffset.animateTo(nearest * pxPerItem, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
                                    }
                                }
                            }
                        }
                ) {
                    // Visible track (narrow, centered in hit area)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(trackWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(trackCorner))
                            .background(trackColor)
                    )

                    // ★ Thumb — Lutea3DThumb: white LED when idle, glowing accent colour while the
                    //    wheel rotates. Pass no activeColor to get the photo's yellow-green instead.
                    Lutea3DThumb(
                        active = isWheelRotating,
                        activeColor = accentColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = thumbY.coerceIn(0.dp, sliderHeight - thumbHeight) + (thumbHeight - thumbThickness) / 2)
                            .requiredSize(thumbHeight, thumbThickness)
                            .graphicsLayer { rotationZ = -90f },
                    )
                }

                // ═══ TEXT LABELS (main + 4 above + 4 below) ═══
                // ★ Texts follow the EXACT arc curve — same method as Simpler.
                //    Each text is positioned at (pivotX + textRadius * cos(angle),
                //    pivotY + textRadius * sin(angle)) — just like Simpler's overlay.
                val rotationItems = scrollOffset.value / pxPerItem
                val centerIdx = indexAtOffset(scrollOffset.value)
                // Arc geometry (same as Canvas)
                val arcPivotXpx = 0f  // pivot at left edge
                val arcPivotYpx = with(density) { maxHeight.toPx() } * 0.50f  // vertically centered
                val arcRadiusPx = with(density) { maxWidth.toPx() } * 0.28f
                val arcGapPx = with(density) { maxWidth.toPx() } * 0.05f
                val textRadiusPx = arcRadiusPx + arcGapPx
                // ★ Angle step — bigger = more gap between texts. 22° fits 9 items on a
                //    semicircle without overlapping (22° × 4 = 88°, within ±90° half arc).
                //    Universal fitting: gap between texts = textRadius * sin(22°) ≈ 37% of radius.
                //    On a 1080px screen with 0.33w radius = ~117px gap — plenty of room.
                val angleStepDeg = 22f
                val secondaryFontSize = 40.sp  // ★ was 35sp, now 40sp
                // ★ Max text width = screen width minus slider zone (7.5% + 20dp thumb + 20dp gap buffer)
                //    Buffer increased from 10dp to 20dp for a slightly larger gap between text and thumb.
                val maxTextWidthPx = with(density) { maxWidth.toPx() } - with(density) { (trackPaddingEnd + thumbThickness + 12.dp).toPx() }

                // Opacity: 100% → 90% → 75% → 62% → 45% → 30%
                // Blur: 2dp on ±3, 5dp on ±4
                fun alphaFor(absOffset: Float): Float = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> 0.90f
                    absOffset < 2.5f -> 0.75f
                    absOffset < 3.5f -> 0.62f
                    absOffset < 4.5f -> 0.45f
                    else -> 0.30f
                }

                // Progressive blur — light on ±3, heavier on ±4. Combined with fade.
                val blurCache = remember { mutableMapOf<Int, androidx.compose.ui.graphics.RenderEffect>() }
                fun getBlurEffect(radiusDp: Float): androidx.compose.ui.graphics.RenderEffect? {
                    if (radiusDp <= 0.1f) return null
                    val key = (radiusDp * 10).toInt()
                    return blurCache.getOrPut(key) {
                        val radiusPx = with(density) { radiusDp.dp.toPx() }
                        android.graphics.RenderEffect.createBlurEffect(
                            radiusPx, radiusPx,
                            android.graphics.Shader.TileMode.DECAL
                        ).asComposeRenderEffect()
                    }
                }
                fun blurFor(absOffset: Float): Float = when {
                    absOffset < 2.5f -> 0f                                    // positions 1-2: sharp
                    absOffset < 3.5f -> lerp(0f, 2f, (absOffset - 2.5f))      // position 3: 2dp blur
                    absOffset < 4.5f -> lerp(2f, 5f, (absOffset - 3.5f))       // position 4: 5dp blur
                    else -> 5f
                }

                for (offset in -4..4) {
                    // ★ Modulo wrap — every playlist appears 3 times across the full slider range.
                    val rawIdx = (rotationItems.roundToInt() - offset)
                    val modIdx = ((rawIdx % playlists.size.coerceAtLeast(1)) + playlists.size) % playlists.size.coerceAtLeast(1)
                    val playlist = playlists[modIdx]
                    val fractionalOffset = rotationItems - rotationItems.roundToInt() + offset
                    val absOffset = abs(fractionalOffset)
                    if (absOffset > 4.5f) continue
                    val isActive = absOffset < 0.5f
                    // ★ Top 5 texts (absOffset < 2.5) are clickable → opens playlist
                    val isClickable = absOffset < 2.5f
                    val displayText = if (isActive) playlist.name.uppercase() else playlist.name.lowercase()
                    val alpha = alphaFor(absOffset) * if (matchesSearch(playlist.name)) 1f else 0f  // ★ hide non-matching when searching
                    val blurRadius = blurFor(absOffset)
                    val blurEffect = getBlurEffect(blurRadius)

                    if (alpha > 0.01f) {
                        val itemAngleDeg = fractionalOffset * angleStepDeg
                        val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()
                        val textX = arcPivotXpx + textRadiusPx * cos(itemAngleRad)
                        val textY = arcPivotYpx + textRadiusPx * sin(itemAngleRad)

                        // ★ Responsive text size — scales with screen width (Universal Fitting Rule).
                        //    Main = 15% of screen width (dp→sp), clamped 50-60.
                        //    Secondary = 10% of screen width, clamped 35-45.
                        //    On 360dp phone: main=54sp, secondary=36sp
                        //    On 411dp phone: main=57sp, secondary=~45sp
                        //    On 480dp phone: main=60sp (clamped), secondary=49sp (clamped)
                        val mainTextSp = (maxWidth.value * 0.15f).coerceIn(50f, 60f)
                        val secondaryTextSp = (maxWidth.value * 0.105f).coerceIn(36f, 46f)  // ★ was 0.11/38-49, now 0.105/36-46 (only ~1sp increase from original)
                        var actualFontSize = if (isActive) mainTextSp else secondaryTextSp
                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString(displayText),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = actualFontSize.sp,
                                fontFamily = BillieEilishFamily,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE),
                        )
                        val measuredWidth = textLayoutResult.size.width.toFloat()
                        val availableWidth = maxTextWidthPx - textX
                        if (measuredWidth > availableWidth && availableWidth > 0f) {
                            val shrinkRatio = availableWidth / measuredWidth
                            actualFontSize = (actualFontSize * shrinkRatio).coerceAtLeast(12f)
                        }
                        val finalLayout = textMeasurer.measure(
                            text = AnnotatedString(displayText),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = actualFontSize.sp,
                                fontFamily = BillieEilishFamily,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE),
                        )
                        val textH = finalLayout.size.height.toFloat()

                        // ★ SYMMETRY FIX: Each text centered on its OWN measured height.
                        //    Main text reduced to 55sp (from 60sp) to reduce height difference.
                        //    With each text using textH/2, the bounding box centers are on the arc
                        //    point — symmetric by construction (sin(angle) is odd function).
                        val modifier = Modifier
                            .graphicsLayer {
                                this.alpha = alpha
                                this.translationX = textX
                                this.translationY = textY - textH / 2f  // ★ own height, not constant
                                this.rotationZ = itemAngleDeg
                                this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                this.renderEffect = blurEffect
                                this.compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            }

                        if (isClickable) {
                            Text(
                                text = displayText,
                                color = if (isActive) Color.White else Color.White.copy(alpha = alpha),
                                fontSize = actualFontSize.sp,
                                fontFamily = BillieEilishFamily,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                modifier = modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onOpenPlaylist(playlist) }
                            )
                        } else {
                            Text(
                                text = displayText,
                                color = Color.White.copy(alpha = alpha),
                                fontSize = actualFontSize.sp,
                                fontFamily = BillieEilishFamily,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                modifier = modifier
                            )
                        }
                    }
                }
            }
        }

            } // end if (playlists.isNotEmpty()) — arc + slider

        // ──────────────────────────────────────────────────────────────
        // ★ Title — EACH WORD IS A SEPARATE LAYER. When search opens:
        //   1. Username "(name)," + "here" FADE OUT (alpha → 0).
        //   2. Remaining words SHRINK + slide to one line: "Search your playlist".
        //   3. Search capsule FADES IN at line 2's Y + 6dp gap, full width.
        //   The menu button (in LuteaScaffold) fades out and a cross icon
        //   fades in at its position — tapping the cross closes the search.
        // ──────────────────────────────────────────────────────────────
        val onSearchTrigger = LocalSearchTrigger.current
        val userName by UserNameManager.state
        val searchOpen by LocalSearchOpen.current

        val words = remember(userName) {
            listOf("$userName,", " Search", " your", " playlist", " here")
        }
        val wordColors = listOf(
            Color.White, Color.White.copy(alpha = 0.5f), Color.White, Color.White, Color.White,
        )

        val bigFontSize = titleFontSize.toFloat()
        val smallFontSize = (titleFontSize - 6).toFloat()

        val currentFontSize by animateFloatAsState(
            targetValue = if (searchOpen) smallFontSize else bigFontSize,
            animationSpec = tween(300, easing = FastOutSlowInEasing), label = "titleSize"
        )
        val searchProgress by animateFloatAsState(
            targetValue = if (searchOpen) 1f else 0f,
            animationSpec = tween(400, easing = FastOutSlowInEasing), label = "searchProgress"
        )
        val fadeProgress by animateFloatAsState(
            targetValue = if (searchOpen) 0f else 1f,
            animationSpec = tween(300, easing = FastOutSlowInEasing), label = "fadeProgress"
        )

        val textMeasurer = rememberTextMeasurer()
        val baseStyle = TextStyle(fontFamily = CalSansFamily, fontWeight = FontWeight.Bold)
        val measuredBig = remember(titleFontSize, userName) {
            words.map { textMeasurer.measure(it, baseStyle.copy(fontSize = bigFontSize.sp)) }
        }
        val measuredSmall = remember(titleFontSize, userName) {
            words.map { textMeasurer.measure(it, baseStyle.copy(fontSize = smallFontSize.sp)) }
        }

        val arcDensity = LocalDensity.current
        val line1Ypx = 0f
        val line2Ypx = measuredBig[0].size.height.toFloat() + with(arcDensity) { 6.dp.toPx() }

        val posA = listOf(
            Offset(0f, line1Ypx),
            Offset(measuredBig[0].size.width.toFloat(), line1Ypx),
            Offset(0f, line2Ypx),
            Offset(measuredBig[2].size.width.toFloat(), line2Ypx),
            Offset(measuredBig[2].size.width + measuredBig[3].size.width.toFloat(), line2Ypx),
        )
        val s1 = measuredSmall[1].size.width.toFloat()
        val s2 = measuredSmall[2].size.width.toFloat()
        val posB = listOf(
            Offset(0f, line1Ypx),
            Offset(0f, line1Ypx),
            Offset(s1, line1Ypx),
            Offset(s1 + s2, line1Ypx),
            Offset(0f, line2Ypx),
        )

        val searchFocusRequester = remember { FocusRequester() }
        // searchQuery is hoisted to the function level — shared with the wheel.
        LaunchedEffect(searchOpen) {
            if (searchOpen) {
                kotlinx.coroutines.delay(200)
                runCatching { searchFocusRequester.requestFocus() }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 24.dp, top = 16.dp, end = 24.dp)
                .fillMaxWidth()
        ) {
            // ── 5 word layers ──
            words.forEachIndexed { i, word ->
                val px = lerp(posA[i].x, posB[i].x, searchProgress)
                val py = lerp(posA[i].y, posB[i].y, searchProgress)
                val wordAlpha = if (i == 0 || i == 4) fadeProgress else 1f
                Text(
                    text = word,
                    color = wordColors[i],
                    fontSize = currentFontSize.sp,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer { alpha = wordAlpha }
                        .offset { IntOffset(px.roundToInt(), py.roundToInt()) }
                        .then(
                            if (i == 1) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSearchTrigger() }
                            else Modifier
                        )
                )
            }

            // ── Search capsule ── full width, at line 2's Y + 6dp gap.
            //    Fades in as searchProgress → 1.
            if (searchProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, line2Ypx.roundToInt()) }
                        .fillMaxWidth()
                        .height(34.dp)
                        .graphicsLayer { alpha = searchProgress }
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.95f),
                                    Color(0xFFB0B0B0).copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()  // ★ fill capsule height so cursor centers vertically
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFF1C1C1E),
                            fontSize = 14.sp,
                            fontFamily = CalSansFamily,
                        ),
                        decorationBox = { innerTextField ->
                            // ★ Wrap in a Box that fills the capsule height and centers
                            //    content vertically — fixes cursor "|" being positioned
                            //    left + slightly up instead of centered in the capsule.
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Type to search...",
                                        color = Color(0xFF1C1C1E).copy(alpha = 0.5f),
                                        fontSize = 14.sp,
                                        fontFamily = CalSansFamily,
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // ── Search history pills ── below the search capsule.
                //    Each pill: white/grey gradient, text + black circle with white cross.
                //    Tap pill → re-run that search. Tap cross → remove from history.
                if (searchProgress > 0.5f && searchHistory.isNotEmpty()) {
                    val capsuleH = with(arcDensity) { 34.dp.toPx() }
                    val historyY = line2Ypx + capsuleH + with(arcDensity) { 8.dp.toPx() }
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(0, historyY.roundToInt()) }
                            .fillMaxWidth()
                            .graphicsLayer { alpha = searchProgress },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        searchHistory.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.95f),
                                                Color(0xFFB0B0B0).copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { searchQuery = entry }
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry,
                                    color = Color(0xFF1C1C1E),
                                    fontSize = 12.sp,
                                    fontFamily = CalSansFamily,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(6.dp))
                                // Black circle with white cross
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF1C1C1E))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { searchHistory.remove(entry) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_cross),
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Linear interpolation between two floats. */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
