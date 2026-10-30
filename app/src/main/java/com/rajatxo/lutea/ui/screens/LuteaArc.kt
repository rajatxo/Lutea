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
import com.rajatxo.lutea.ui.components.PicaflorFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * LuteaArc — the playlist wheel.
 *
 * Coral's layout (what we're repolishing):
 *  - Two concentric arcs, BOTH thick and bright (we don't thin the inner one).
 *  - Balls on BOTH arcs — outer (big arc) balls are BIGGER + BRIGHTER,
 *    inner (small arc) balls are SMALLER + DIMMER, proportional to arc size.
 *  - The two arcs rotate in OPPOSITE directions when you drag.
 *  - Text labels sit ONLY beside the OUTER (big) arc — LEFT-ANCHORED just past
 *    the ball so they extend radially outward. A long name never overlaps the
 *    ball, because the text starts AT the ball + small gap, not centered on it.
 *  - Active item = CalSans SemiBold (large, bright white).
 *    Inactive items = Instrument Serif (smaller, dimmed) — typographic contrast.
 *  - Drag vertically to rotate — each integer step fires tick haptic + sound.
 *
 * Universal Layout Laws:
 *  - Law 1: statusBarsPadding() on the "+ New Playlist" capsule.
 *  - Edge-to-edge: MainActivity already set DecorFitsSystemWindows = false.
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

    // ── Sound + Haptic (Coral pattern: SoundPool + VIRTUAL_KEY + FLAG_IGNORE) ──
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
                        // Snap the wheel to the freshly created playlist.
                        // playlists.size here is the OLD count = new playlist's index.
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
        // 1) CANVAS — rotating arcs + balls + text labels.
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
                            rotationItems += dragAmount / 80f
                            val newInt = rotationItems.toInt()
                            // Tick haptic + sound only on item-boundary crossings.
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

            // Helper: smooth fading needle arc (center thick → endpoints hairline).
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

            // ★ BOTH arcs thick + bright — Coral's look (do NOT thin the inner arc).
            //    "what we did the difference is we just thiken the arc"
            drawNeedleArc(
                radius = outerRadius,
                maxStrokePx = with(density) { 3.5.dp.toPx() },
                minStrokePx = with(density) { 0.5.dp.toPx() },
                maxAlpha = 0.8f,
            )
            drawNeedleArc(
                radius = innerRadius,
                maxStrokePx = with(density) { 3.5.dp.toPx() },
                minStrokePx = with(density) { 0.5.dp.toPx() },
                maxAlpha = 0.8f,
            )

            // ── BALLS + TEXT on the wheel ──
            if (itemCount > 0) {
                // 16° spacing — fits all 9 items (4 + main + 4) within the visible portion
                // of the arc. With pivot off-screen left at -0.2w and outerRadius = 0.52w,
                // balls stay on-screen up to angle ~67° (cos ≥ 0.385). 4 × 16° = 64° → fits.
                // The old 22° put offset ±4 at 88° (off-screen), which is why you only saw 3+1+3.
                val ballSpacingDeg = 16f
                // Text starts just past the outer ball and extends radially outward.
                // LEFT-ANCHORED → a long name can never overlap the ball.
                val textGap = with(density) { 14.dp.toPx() }
                val visibleSpan = 4

                for (offset in -visibleSpan..visibleSpan) {
                    val rawIdx = (rotationItems.toInt() - offset)
                    val modIdx = ((rawIdx % itemCount) + itemCount) % itemCount
                    val name = playlistNames[modIdx]

                    val fractionalOffset = rotationItems - rotationItems.toInt() + offset
                    val absOffset = abs(fractionalOffset)
                    if (absOffset > visibleSpan) continue

                    // ★ OUTER arc rotates WITH rotationItems direction.
                    val outerAngleDeg = fractionalOffset * ballSpacingDeg
                    val outerAngleRad = (outerAngleDeg * Math.PI / 180f).toFloat()

                    // ★ INNER arc rotates in OPPOSITE direction (counter-rotating).
                    val innerAngleDeg = -fractionalOffset * ballSpacingDeg
                    val innerAngleRad = (innerAngleDeg * Math.PI / 180f).toFloat()

                    val outerBallX = pivotX + outerRadius * cos(outerAngleRad)
                    val outerBallY = pivotY + outerRadius * sin(outerAngleRad)

                    val innerBallX = pivotX + innerRadius * cos(innerAngleRad)
                    val innerBallY = pivotY + innerRadius * sin(innerAngleRad)

                    if (outerBallX < -300f || outerBallX > w + 300f) continue

                    val isActive = absOffset < 0.5f

                    // ★ Coral-style linear opacity fade.
                    //    Center = 1.0 (full), edge = 0.05 (95% faded, just barely visible).
                    //    Smooth linear interpolation between — same shape Coral uses on its wheel.
                    val alpha = when {
                        absOffset < 0.5f -> 1.00f
                        absOffset < 1.5f -> 0.75f
                        absOffset < 2.5f -> 0.50f
                        absOffset < 3.5f -> 0.25f
                        else -> 0.05f   // edge: 95% faded
                    }

                    // ★ Ball sizes proportional to arc size:
                    //    big (outer) arc  → BIGGER + BRIGHTER balls (primary)
                    //    small (inner) arc → SMALLER + DIMMER balls (decorative)
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
                        // OUTER arc ball — full alpha (BRIGHTER, BIGGER).
                        // No glow halo (Coral doesn't have one — plain solid circles).
                        drawCircle(
                            color = Color.White.copy(alpha = alpha),
                            radius = outerBallRadius,
                            center = Offset(outerBallX, outerBallY),
                        )

                        // INNER arc ball — 45% alpha (DIMMER, SMALLER), counter-rotating.
                        drawCircle(
                            color = Color.White.copy(alpha = alpha * 0.45f),
                            radius = innerBallRadius,
                            center = Offset(innerBallX, innerBallY),
                        )
                    }

                    // ── TEXT — only beside OUTER arc, LEFT-ANCHORED, rotated radially ──
                    // Single font (Picaflor) for both, distinguished by CASE + size + alpha:
                    //   - Active    = UPPERCASE, 60sp, full alpha (the main label, prominent)
                    //   - Inactive  = lowercase, 40sp (constant), faded alpha (secondary labels)
                    if (name.isNotEmpty() && alpha > 0.04f) {
                        // Anchor: just past the outer ball, extending radially outward.
                        val textStartX = pivotX + (outerRadius + textGap) * cos(outerAngleRad)
                        val textStartY = pivotY + (outerRadius + textGap) * sin(outerAngleRad)

                        // Case: main = UPPERCASE, secondary = lowercase
                        val displayText = if (isActive) name.uppercase() else name.lowercase()
                        val fontSize = if (isActive) 60f else 40f
                        val fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        val fontFamily = PicaflorFamily
                        val textColor = Color.White.copy(alpha = alpha)

                        val textLayout = textMeasurer.measure(
                            text = AnnotatedString(displayText),
                            style = TextStyle(
                                color = textColor,
                                fontSize = fontSize.sp,
                                fontWeight = fontWeight,
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

                        val textH = textLayout.size.height.toFloat()

                        // Translate → rotate radially → draw LEFT-ANCHORED.
                        // topLeft = (0, -textH/2) means the text's LEFT edge sits at
                        // (textStartX, textStartY) and extends outward in +x (rotated frame).
                        drawContext.canvas.save()
                        drawContext.canvas.translate(textStartX, textStartY)
                        drawContext.canvas.rotate(outerAngleDeg)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(0f, -textH / 2f),
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
