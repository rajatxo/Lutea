package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.ui.components.CalSansFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * LuteaArc — the playlist wheel.
 *
 * - Two concentric smooth arcs (200 segments, no visible bands)
 * - Balls on BOTH arcs (aligned via imaginary line)
 * - Text placed radially — ROTATED to follow the arc curve (like Coral)
 *   Uses Compose textMeasurer + drawText (NOT nativeCanvas)
 *   Active = CalSans SemiBold, Inactive = Instrument Serif
 * - Drag to rotate the wheel (vertical drag gesture)
 * - "+ New Playlist" capsule button at top center
 */
@Composable
fun LuteaArc(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // Wheel rotation state — drag to rotate
    var rotationItems by remember { mutableStateOf(0f) } // how many "items" rotated

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
                    }
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Use actual playlists, or placeholder names
    val playlistNames = if (playlists.isNotEmpty()) {
        playlists.map { it.name }
    } else {
        listOf("Create a playlist", "", "", "", "", "", "")
    }
    val itemCount = playlistNames.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        // "New Playlist" capsule button at TOP CENTER
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            // Drag up = rotate one way, drag down = other way
                            rotationItems += dragAmount / 80f
                        }
                    )
                }
        ) {
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

            // Draw arcs
            drawNeedleArc(innerRadius, with(density) { 3.5.dp.toPx() }, with(density) { 0.5.dp.toPx() }, 0.8f)
            drawNeedleArc(outerRadius, with(density) { 3.5.dp.toPx() }, with(density) { 0.5.dp.toPx() }, 0.8f)

            // ── BALLS + TEXT on the wheel ──
            if (itemCount > 0) {
                val ballSpacingDeg = 22f
                val textRadius = outerRadius + with(density) { 25.dp.toPx() }
                val visibleSpan = 4

                for (offset in -visibleSpan..visibleSpan) {
                    // Calculate which playlist item is at this position
                    val rawIdx = (rotationItems.toInt() - offset)
                    val modIdx = ((rawIdx % itemCount) + itemCount) % itemCount
                    val name = playlistNames[modIdx]

                    // Fractional offset for smooth rotation
                    val fractionalOffset = rotationItems - rotationItems.toInt() + offset
                    val absOffset = abs(fractionalOffset)

                    if (absOffset > visibleSpan) continue

                    // Angle on the arc
                    val itemAngleDeg = fractionalOffset * ballSpacingDeg
                    val itemAngleRad = (itemAngleDeg * Math.PI / 180f).toFloat()

                    // Position on inner arc (balls)
                    val ballX = pivotX + innerRadius * cos(itemAngleRad)
                    val ballY = pivotY + innerRadius * sin(itemAngleRad)

                    // Position on outer arc (balls)
                    val outerBallX = pivotX + outerRadius * cos(itemAngleRad)
                    val outerBallY = pivotY + outerRadius * sin(itemAngleRad)

                    // Skip if off-screen
                    if (ballX < -200f || ballX > w + 200f) continue

                    val isActive = absOffset < 0.5f

                    // Alpha: smooth fade based on distance
                    val alpha = when {
                        absOffset < 0.5f -> 1f
                        absOffset < 1.5f -> 0.7f
                        absOffset < 2.5f -> 0.4f
                        absOffset < 3.5f -> 0.15f
                        else -> 0.05f
                    }

                    // Ball sizes
                    val ballRadius = if (isActive) {
                        with(density) { 5.dp.toPx() }
                    } else {
                        with(density) { (4f - absOffset * 0.5f).coerceAtLeast(1.5f).dp.toPx() }
                    }

                    if (alpha > 0.01f) {
                        // Ball on inner arc
                        drawCircle(
                            color = Color.White.copy(alpha = alpha),
                            radius = ballRadius,
                            center = Offset(ballX, ballY),
                        )

                        // Ball on outer arc (aligned, smaller + dimmer)
                        drawCircle(
                            color = Color.White.copy(alpha = alpha * 0.6f),
                            radius = ballRadius * 0.8f,
                            center = Offset(outerBallX, outerBallY),
                        )
                    }

                    // ── TEXT — rotated radially (like Coral) ──
                    if (name.isNotEmpty() && alpha > 0.05f) {
                        // Text position — OUTSIDE the outer arc
                        val textX = pivotX + textRadius * cos(itemAngleRad)
                        val textY = pivotY + textRadius * sin(itemAngleRad)

                        // Font: active = CalSans SemiBold, inactive = serif
                        val fontSize = if (isActive) 36f else (24f - absOffset * 3f).coerceAtLeast(10f)
                        val fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        val textColor = Color.White.copy(alpha = alpha)

                        // Measure text
                        val textLayout = textMeasurer.measure(
                            text = AnnotatedString(name),
                            style = TextStyle(
                                color = textColor,
                                fontSize = fontSize.sp,
                                fontWeight = fontWeight,
                                fontFamily = CalSansFamily,  // using CalSans for now
                            ),
                            overflow = TextOverflow.Visible,
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(
                                maxWidth = Int.MAX_VALUE,
                                maxHeight = Int.MAX_VALUE
                            )
                        )

                        val textW = textLayout.size.width.toFloat()
                        val textH = textLayout.size.height.toFloat()

                        // Draw text — TRANSLATE to position, ROTATE radially, DRAW centered
                        drawContext.canvas.save()
                        drawContext.canvas.translate(textX, textY)
                        drawContext.canvas.rotate(itemAngleDeg)  // rotate to follow curve
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
    }
}
