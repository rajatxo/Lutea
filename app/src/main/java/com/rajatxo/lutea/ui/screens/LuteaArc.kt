package com.rajatxo.lutea.ui.screens

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.ui.components.CalSansFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * LuteaArc — the playlist wheel.
 *
 * Universal Layout Laws followed:
 *  - Law 1: status bar inset via `statusBarsPadding()` for the capsule
 *  - Law 7: Box root, Canvas measured by its own size
 *  - Edge-to-edge: MainActivity already set DecorFitsSystemWindows = false
 *
 * Layout:
 *  - Canvas FIRST (rotating arcs + balls + text labels)
 *  - "+ New Playlist" capsule on TOP (so its click handler wins)
 *
 * Wheel mechanics:
 *  - Two concentric arcs (200 segments each, needle-style fading)
 *  - OUTER (big) arc  = primary, brighter line + bigger/brighter balls + text labels
 *  - INNER (small) arc = counter-rotating, dimmer line + smaller/dimmer balls, no text
 *  - Drag up/down rotates the wheel — outer goes CW, inner goes CCW
 *  - Each integer step crosses an item boundary → tick haptic + sound (Coral pattern)
 */
@Composable
fun LuteaArc(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    val view = LocalView.current
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // ── Rotation state ──
    var rotationItems by remember { mutableStateOf(0f) }

    // ── Sound + Haptic (same pattern as CynthiaArcDial / LuteaTabCapsule) ──
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

    fun tickHaptic() {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (soundLoaded) {
            soundPool.play(tickSoundId, 0.55f, 0.55f, 1, 0, 1f)
        }
    }

    // Use actual playlists, or placeholder names
    val playlistNames = if (playlists.isNotEmpty()) {
        playlists.map { it.name }
    } else {
        listOf("Create a playlist", "", "", "", "", "", "")
    }
    val itemCount = playlistNames.size

    // ── Create Playlist dialog (rendered OUTSIDE the Box so it overlays everything) ──
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
                        // Snap the wheel to the freshly created playlist (last index).
                        // playlists.size here is the OLD count, which equals the new playlist's index.
                        rotationItems = playlists.size.toFloat()
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
            .background(Color(0xFF0A0A0A)),
    ) {
        // ──────────────────────────────────────────────────────────────
        // 1) CANVAS — rotating arcs + balls + text labels
        //    Placed FIRST so the "+ New Playlist" capsule (drawn next)
        //    sits ON TOP and wins tap events.
        // ──────────────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            val oldInt = rotationItems.toInt()
                            // Drag up (negative) = next item; drag down (positive) = prev.
                            rotationItems += dragAmount / 80f
                            val newInt = rotationItems.toInt()
                            // Tick haptic + sound only when crossing an item boundary.
                            if (newInt != oldInt) {
                                tickHaptic()
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            // Pivot — off-screen to the left, vertically centered (Coral-style).
            val pivotX = w * -0.20f
            val pivotY = h * 0.50f

            val innerRadius = w * 0.42f   // small arc
            val outerRadius = w * 0.52f   // big arc (primary)

            val arcSweepDeg = 180f
            val arcStartDeg = -arcSweepDeg / 2f
            val segments = 200

            // Helper: smooth fading needle arc.
            fun drawNeedleArc(
                radius: Float,
                maxStrokePx: Float,
                minStrokePx: Float,
                maxAlpha: Float,
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
                        style = Stroke(width = segStroke),
                    )
                }
            }

            // Outer (big / primary) arc — thicker + brighter.
            drawNeedleArc(
                radius = outerRadius,
                maxStrokePx = with(density) { 4.dp.toPx() },
                minStrokePx = with(density) { 0.6.dp.toPx() },
                maxAlpha = 0.9f,
            )
            // Inner (small / counter-rotating) arc — thinner + dimmer.
            drawNeedleArc(
                radius = innerRadius,
                maxStrokePx = with(density) { 2.dp.toPx() },
                minStrokePx = with(density) { 0.3.dp.toPx() },
                maxAlpha = 0.5f,
            )

            // ── BALLS + TEXT on the wheel ──
            if (itemCount > 0) {
                val ballSpacingDeg = 22f
                // Text sits clearly OUTSIDE the big arc — wide gap so it never overlaps balls.
                val textRadius = outerRadius + with(density) { 38.dp.toPx() }
                val visibleSpan = 4

                for (offset in -visibleSpan..visibleSpan) {
                    // Which playlist item is at this position?
                    val rawIdx = (rotationItems.toInt() - offset)
                    val modIdx = ((rawIdx % itemCount) + itemCount) % itemCount
                    val name = playlistNames[modIdx]

                    // Fractional offset from the active slot — smooth rotation.
                    val fractionalOffset = rotationItems - rotationItems.toInt() + offset
                    val absOffset = abs(fractionalOffset)
                    if (absOffset > visibleSpan) continue

                    // ★ OUTER arc rotates WITH rotationItems direction.
                    val outerAngleDeg = fractionalOffset * ballSpacingDeg
                    val outerAngleRad = (outerAngleDeg * Math.PI / 180f).toFloat()

                    // ★ INNER arc rotates in OPPOSITE direction (counter-rotating).
                    val innerAngleDeg = -fractionalOffset * ballSpacingDeg
                    val innerAngleRad = (innerAngleDeg * Math.PI / 180f).toFloat()

                    // Position on OUTER arc (big).
                    val outerBallX = pivotX + outerRadius * cos(outerAngleRad)
                    val outerBallY = pivotY + outerRadius * sin(outerAngleRad)

                    // Position on INNER arc (small).
                    val innerBallX = pivotX + innerRadius * cos(innerAngleRad)
                    val innerBallY = pivotY + innerRadius * sin(innerAngleRad)

                    // Skip if off-screen.
                    if (outerBallX < -300f || outerBallX > w + 300f) continue

                    val isActive = absOffset < 0.5f

                    // Smooth fade based on distance from active slot.
                    val alpha = when {
                        absOffset < 0.5f -> 1f
                        absOffset < 1.5f -> 0.7f
                        absOffset < 2.5f -> 0.4f
                        absOffset < 3.5f -> 0.15f
                        else -> 0.05f
                    }

                    // ★ Ball sizes — proportional to arc size:
                    //    big (outer) arc  → bigger + brighter balls (primary)
                    //    small (inner) arc → smaller + dimmer balls (decorative)
                    val outerBallRadius = if (isActive) {
                        with(density) { 6.dp.toPx() }
                    } else {
                        with(density) { (5f - absOffset * 0.6f).coerceAtLeast(2f).dp.toPx() }
                    }
                    val innerBallRadius = if (isActive) {
                        with(density) { 3.5.dp.toPx() }
                    } else {
                        with(density) { (3f - absOffset * 0.5f).coerceAtLeast(1f).dp.toPx() }
                    }

                    if (alpha > 0.01f) {
                        // OUTER arc ball — full alpha (BRIGHTER, BIGGER)
                        drawCircle(
                            color = Color.White.copy(alpha = alpha),
                            radius = outerBallRadius,
                            center = Offset(outerBallX, outerBallY),
                        )
                        // Glow halo behind the active outer ball for extra prominence.
                        if (isActive) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.18f),
                                radius = outerBallRadius * 2.4f,
                                center = Offset(outerBallX, outerBallY),
                            )
                        }

                        // INNER arc ball — 45% alpha (DIMMER, SMALLER), counter-rotating
                        drawCircle(
                            color = Color.White.copy(alpha = alpha * 0.45f),
                            radius = innerBallRadius,
                            center = Offset(innerBallX, innerBallY),
                        )
                    }

                    // ── TEXT — only beside the OUTER (big) arc, rotated to follow curve ──
                    if (name.isNotEmpty() && alpha > 0.05f) {
                        val textX = pivotX + textRadius * cos(outerAngleRad)
                        val textY = pivotY + textRadius * sin(outerAngleRad)

                        // Slightly smaller active size than before so labels never collide
                        // with adjacent balls when the playlist name is long.
                        val fontSize = if (isActive) 30f
                        else (20f - absOffset * 2.5f).coerceAtLeast(10f)
                        val fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        val textColor = Color.White.copy(alpha = alpha)

                        val textLayout = textMeasurer.measure(
                            text = AnnotatedString(name),
                            style = TextStyle(
                                color = textColor,
                                fontSize = fontSize.sp,
                                fontWeight = fontWeight,
                                fontFamily = CalSansFamily,
                            ),
                            overflow = TextOverflow.Visible,
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(
                                maxWidth = Int.MAX_VALUE,
                                maxHeight = Int.MAX_VALUE,
                            ),
                        )

                        val textW = textLayout.size.width.toFloat()
                        val textH = textLayout.size.height.toFloat()

                        // Translate → rotate radially → draw centered.
                        drawContext.canvas.save()
                        drawContext.canvas.translate(textX, textY)
                        drawContext.canvas.rotate(outerAngleDeg)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(-textW / 2f, -textH / 2f),
                            alpha = alpha,
                        )
                        drawContext.canvas.restore()
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────
        // 2) "+ NEW PLAYLIST" CAPSULE — TOP CENTER, beside the settings button.
        //    Drawn AFTER the Canvas so it sits ON TOP and its clickable wins.
        //    Universal Layout Law: statusBarsPadding() respects the status bar inset,
        //    so it lines up with the settings cog in LuteaScaffold (not "way to top").
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
