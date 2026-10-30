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
import com.rajatxo.lutea.ui.components.InstrumentSerifFamily
import com.rajatxo.lutea.ui.components.PicaflorFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * LuteaArc — the playlist wheel.
 *
 * Coral-inspired, simplified:
 *  - ONE arc (thick + bright, needle-style fading at endpoints) — big arc removed
 *  - NO balls (removed per design)
 *  - 9 text labels along the arc (4 above + 1 main + 4 below)
 *  - Main text    = Picaflor (uppercase glyphs only) — UPPERCASE, 60sp
 *  - Secondary   = Instrument Serif (Coral's secondary text font) — lowercase, 40sp
 *  - SMOOTH opacity (continuous smoothstep function of offset, NOT stepped when-blocks)
 *    — fades smoothly from 1.0 at center to 0.05 (95% fade) at the edge as you drag.
 *  - Coral-style auto-fit: if a label is wider than the available width budget,
 *    the canvas is scaled down so the text fits on-screen.
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

            // SINGLE arc (Coral has two; per user request we keep only the small one).
            val arcRadius = w * 0.42f

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

            // ★ Single arc (thick + bright, needle-style fading at endpoints).
            //    Big (outer) arc removed per user request; balls removed entirely.
            drawNeedleArc(
                radius = arcRadius,
                maxStrokePx = with(density) { 3.5.dp.toPx() },
                minStrokePx = with(density) { 0.5.dp.toPx() },
                maxAlpha = 0.8f,
            )

            // ── TEXT LABELS along the arc (no balls) ──
            if (itemCount > 0) {
                // 16° spacing — fits all 9 items (4 + main + 4) within the visible portion
                // of the arc (with pivot off-screen left at -0.2w and arcRadius = 0.42w,
                // 4 × 16° = 64° keeps balls on-screen).
                val itemSpacingDeg = 16f
                val textGap = with(density) { 14.dp.toPx() }
                val visibleSpan = 4
                // Max width budget for each text label (Coral-style auto-fit).
                // If a label is wider than this, we scale it down to fit.
                val maxTextWidthPx = w * 0.55f

                for (offset in -visibleSpan..visibleSpan) {
                    val rawIdx = (rotationItems.toInt() - offset)
                    val modIdx = ((rawIdx % itemCount) + itemCount) % itemCount
                    val name = playlistNames[modIdx]

                    val fractionalOffset = rotationItems - rotationItems.toInt() + offset
                    val absOffset = abs(fractionalOffset)
                    if (absOffset > visibleSpan) continue

                    val angleDeg = fractionalOffset * itemSpacingDeg
                    val angleRad = (angleDeg * Math.PI / 180f).toFloat()

                    // Text anchor: just past the arc, extending radially outward.
                    val textStartX = pivotX + (arcRadius + textGap) * cos(angleRad)
                    val textStartY = pivotY + (arcRadius + textGap) * sin(angleRad)

                    // Skip if completely off-screen.
                    if (textStartX > w + 50f || textStartX < -w) continue

                    val isActive = absOffset < 0.5f

                    // ★ Coral-style SMOOTH opacity (continuous, no stepped when-blocks).
                    //    norm = 0 at center → 1 at the visible edge.
                    //    smoothstep(baseAlpha) gives an ease-in-out fade.
                    //    Floor at 0.05 so edge items are 95% faded but still visible.
                    val norm = (absOffset / visibleSpan).coerceIn(0f, 1f)
                    val baseAlpha = 1f - norm
                    val smoothAlpha = baseAlpha * baseAlpha * (3f - 2f * baseAlpha)
                    val alpha = smoothAlpha.coerceAtLeast(0.05f)

                    // ── Text content + style ──
                    //    Active   = Picaflor (uppercase glyphs only) → UPPER the string
                    //    Secondary = Instrument Serif (Coral's secondary text font) → lower the string
                    if (name.isNotEmpty() && alpha > 0.04f) {
                        val displayText = if (isActive) name.uppercase() else name.lowercase()
                        val baseFontSize = if (isActive) 60f else 40f
                        val fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        val fontFamily = if (isActive) PicaflorFamily else InstrumentSerifFamily
                        val textColor = Color.White.copy(alpha = 1f)  // alpha applied via drawText.alpha

                        val textLayout = textMeasurer.measure(
                            text = AnnotatedString(displayText),
                            style = TextStyle(
                                color = textColor,
                                fontSize = baseFontSize.sp,
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

                        val textW = textLayout.size.width.toFloat()
                        val textH = textLayout.size.height.toFloat()

                        // ★ Coral-style auto-fit: if the text is wider than the
                        //    available budget, scale the canvas down so it fits.
                        val scale = if (textW > maxTextWidthPx) maxTextWidthPx / textW else 1f

                        drawContext.canvas.save()
                        drawContext.canvas.translate(textStartX, textStartY)
                        drawContext.canvas.rotate(angleDeg)
                        if (scale < 1f) drawContext.canvas.scale(scale, scale)
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
