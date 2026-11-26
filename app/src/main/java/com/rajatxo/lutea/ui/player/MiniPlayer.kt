package com.rajatxo.lutea.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.rajatxo.lutea.R
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.CalSansFamily

/** Same dark ink the nav capsule uses on its white glass. */
private val MiniInk = Color(0xFF1C1C1E)

/**
 * MiniPlayer — a compact glass capsule above the nav bar showing the current song.
 *
 * - Shows album art thumbnail (or placeholder) + title + artist
 * - Play/pause + next buttons on the right
 * - Tapping the capsule expands to the full player (onExpand callback)
 * - Only visible when a song is loaded (currentSong != null)
 * - Slide-up + fade animation when a song starts playing
 */
@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {},
    hidden: Boolean = false,
    backdrop: LayerBackdrop? = null,
    // Total width to match the nav bar (4 tabs + gaps). When null, falls back
    // to fillMaxWidth with 16dp side padding (the old behaviour).
    navMatchedWidth: Dp? = null,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val isPlaying by PlaybackConnection.isPlaying.collectAsState()

    val capsuleShape = RoundedCornerShape(percent = 50)
    // The mini player is the nav bar's little sibling: the same 16dp frost,
    // the same white 0.80 → 0.64 glass tint, and the same soft top sheen.
    // Its ink is dark like the nav so the white surface stays legible.
    val glassSurface = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { capsuleShape },
            effects = {
                blur(16.dp.toPx())
            },
            onDrawSurface = {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.80f),
                            Color.White.copy(alpha = 0.64f),
                        )
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.30f),
                        0.55f to Color.Transparent,
                        1f to Color.Transparent,
                    )
                )
            },
        )
    } else {
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFFD8D8DC)),
            ),
            shape = capsuleShape,
        )
    }

    AnimatedVisibility(
        visible = currentSong != null && !hidden,
        enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(),
        exit = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                // Match the nav bar's full outer width (4 tabs + gaps) when
                // provided; otherwise fall back to fillMaxWidth + 16dp pad.
                .then(
                    if (navMatchedWidth != null) {
                        Modifier
                            .width(navMatchedWidth)
                            .height(56.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(56.dp)
                    }
                )
                .shadow(elevation = 6.dp, shape = capsuleShape, clip = true)
                .clip(capsuleShape)
                .then(glassSurface)
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.95f),
                        0.50f to Color.White.copy(alpha = 0.18f),
                        1f to Color.White.copy(alpha = 0.55f),
                    ),
                    shape = capsuleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onExpand() }
                // Drag the capsule up to open the player too.
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = { if (total < -24.dp.toPx()) onExpand() },
                    )
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album art thumbnail (or placeholder)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
                    .border(0.8.dp, MiniInk.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                currentSong?.albumArtUri?.let { artUri ->
                    AsyncImage(
                        model = artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Title + artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong?.title ?: "",
                    color = MiniInk,
                    fontSize = 14.sp,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentSong?.artist ?: "",
                    color = MiniInk.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Play/pause button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MiniInk.copy(alpha = 0.06f))
                    .border(0.6.dp, MiniInk.copy(alpha = 0.10f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { PlaybackConnection.playPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MiniInk,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Next button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MiniInk.copy(alpha = 0.06f))
                    .border(0.6.dp, MiniInk.copy(alpha = 0.10f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { PlaybackConnection.next() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_next),
                    contentDescription = "Next",
                    tint = MiniInk,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
