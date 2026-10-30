package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.ui.components.CalSansFamily
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * LuteaArc — the playlist wheel.
 *
 * Two concentric smooth arcs + balls on BOTH arcs (aligned via imaginary line).
 * Playlist names show next to balls.
 *
 * "New Playlist" button replaces the "Playlists" title.
 * Tapping it opens a dialog to create a new playlist.
 */
@Composable
fun LuteaArc(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableStateOf(0) }

    // Use actual playlists, or empty if none created yet
    val playlistNames = playlists.map { it.name }
    val hasPlaylists = playlistNames.isNotEmpty()

    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist", color = Color.White) },
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
                        activeIndex = playlists.size // new playlist becomes active
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
        // "New Playlist" button at top (replaces "Playlists" title)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showCreateDialog = true }
                .then(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)),
        ) {
            Text(
                text = "+ New Playlist",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val pivotX = w * -0.20f
            val pivotY = h * 0.50f

            val innerRadius = w * 0.42f
            val outerRadius = w * 0.52f

            val arcSweepDeg = 180f
            val arcStartDeg = -arcSweepDeg / 2f
            val segments = 200

            // Helper: smooth fading needle arc
            fun drawNeedleArc(radius: Float, maxStroke: Float, minStroke: Float, maxAlpha: Float) {
                val fadeRange = 0.50f
                for (i in 0 until segments) {
                    val segStart = i / segments.toFloat()
                    val segEnd = (i + 1) / segments.toFloat()
                    val distFromEndpoint = minOf(segStart, 1f - segStart)
                    val alphaFactor = if (distFromEndpoint > fadeRange) 1f else (distFromEndpoint / fadeRange).pow(2.0f)
                    val segAlpha = maxAlpha * alphaFactor
                    if (segAlpha <= 0.001f) continue
                    val strokeFactor = if (distFromEndpoint > fadeRange) 1f else 0.15f + 0.85f * (distFromEndpoint / fadeRange).pow(2.0f)
                    val segStroke = minStroke + (maxStroke - minStroke) * strokeFactor
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

            // Inner arc
            drawNeedleArc(innerRadius, with(density) { 3.5.dp.toPx() }, with(density) { 0.5.dp.toPx() }, 0.8f)
            // Outer arc
            drawNeedleArc(outerRadius, with(density) { 3.5.dp.toPx() }, with(density) { 0.5.dp.toPx() }, 0.8f)

            // ── BALLS on BOTH arcs ──
            if (hasPlaylists) {
                val ballSpacingDeg = 22f
                val ballFadeRange = 4
                val itemCount = playlistNames.size
                val safeActive = activeIndex.coerceIn(0, itemCount - 1)

                for (offset in -ballFadeRange..ballFadeRange) {
                    val index = safeActive + offset
                    if (index < 0 || index >= itemCount) continue

                    val ballAngleDeg = offset * ballSpacingDeg
                    val ballAngleRad = Math.toRadians(ballAngleDeg.toDouble())
                    val cosA = cos(ballAngleRad).toFloat()
                    val sinA = sin(ballAngleRad).toFloat()

                    // Positions on both arcs (same angle = aligned via imaginary line)
                    val innerBallX = pivotX + innerRadius * cosA
                    val innerBallY = pivotY + innerRadius * sinA
                    val outerBallX = pivotX + outerRadius * cosA
                    val outerBallY = pivotY + outerRadius * sinA

                    val dist = kotlin.math.abs(offset)
                    val isActive = offset == 0

                    // Active ball is SLIGHTLY smaller (was 6dp, now 5dp)
                    val ballRadius = if (isActive) {
                        with(density) { 5.dp.toPx() }
                    } else {
                        with(density) { (4f - dist * 0.5f).coerceAtLeast(1.5f).dp.toPx() }
                    }

                    val ballAlpha = if (isActive) 1f else (1f - dist * 0.2f).coerceAtLeast(0f)
                    if (ballAlpha <= 0.01f) continue

                    // Ball on inner arc
                    drawCircle(
                        color = Color.White.copy(alpha = ballAlpha),
                        radius = ballRadius,
                        center = Offset(innerBallX, innerBallY),
                    )

                    // Ball on outer arc (aligned — same imaginary line)
                    drawCircle(
                        color = Color.White.copy(alpha = ballAlpha * 0.6f),  // slightly dimmer
                        radius = ballRadius * 0.8f,  // slightly smaller
                        center = Offset(outerBallX, outerBallY),
                    )

                    // Playlist name text next to the ball (outside outer arc)
                    val textRadius = outerRadius + with(density) { 25.dp.toPx() }
                    val textX = pivotX + textRadius * cosA
                    val textY = pivotY + textRadius * sinA
                    val rotationDeg = ballAngleDeg - 90f // perpendicular to radius

                    val fontSize = if (isActive) 32f else (22f - dist * 2f).coerceAtLeast(10f)
                    val textAlpha = if (isActive) 1f else (1f - dist * 0.2f).coerceAtLeast(0f)

                    if (textAlpha > 0.05f) {
                        drawContext.canvas.nativeCanvas.apply {
                            save()
                            translate(textX, textY)
                            rotate(rotationDeg)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(
                                    (textAlpha * 255).toInt(), 255, 255, 255
                                )
                                textSize = with(density) { fontSize.sp.toPx() }
                                typeface = android.graphics.Typeface.create(
                                    android.graphics.Typeface.SERIF,
                                    android.graphics.Typeface.NORMAL
                                )
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText(playlistNames[index], 0f, 0f, paint)
                            restore()
                        }
                    }
                }
            } else {
                // No playlists yet — show hint text
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(100, 255, 255, 255)
                        textSize = with(density) { 14.sp.toPx() }
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(
                        "Tap \"+ New Playlist\" to create one",
                        w * 0.5f, h * 0.5f,
                        paint
                    )
                }
            }
        }
    }
}
