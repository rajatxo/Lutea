package com.rajatxo.lutea.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.lutea.R
import com.rajatxo.lutea.playback.LoopMode
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
import kotlin.math.roundToLong

/**
 * Navigation callbacks for the FullPlayer. These are wired through a
 * CompositionLocal so the [LuteaScaffold] (which constructs FullPlayer
 * internally) doesn't need to know about them — QuickPicksScreen provides
 * the real handlers around the scaffold, and they're read here.
 */
data class PlayerNavigation(
    val onAlbumClick: () -> Unit = {},
    val onArtistClick: () -> Unit = {},
)

val LocalPlayerNavigation = staticCompositionLocalOf { PlayerNavigation() }

/**
 * FullPlayer — full-screen player that expands from the mini player.
 *
 * - Large album art (centered, square, rounded corners)
 * - Song title (BillieEilish font, large) + artist (gray, smaller)
 * - Seek bar with position / duration labels
 * - Controls: prev, play/pause, next (large circular buttons) + loop on the right
 * - Lyrics icon (top-left) — opens the lyrics page
 * - Swipe down or tap close button to collapse back to mini player
 * - Position polled every 500ms while visible
 * - Slide-up + fade animation on expand, slide-down + fade on collapse
 */
@Composable
fun FullPlayer(
    visible: Boolean,
    onClose: () -> Unit,
    onLyricsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onAlbumClick: () -> Unit = LocalPlayerNavigation.current.onAlbumClick,
    onArtistClick: () -> Unit = LocalPlayerNavigation.current.onArtistClick,
) {
    // Provide the (possibly overridden) callbacks so FullPlayerContent and
    // its children can read them without explicit threading.
    CompositionLocalProvider(
        LocalPlayerNavigation provides PlayerNavigation(onAlbumClick, onArtistClick)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } + fadeOut(),
            modifier = modifier,
        ) {
            FullPlayerContent(onClose = onClose, onLyricsClick = onLyricsClick)
        }
    }
}

@Composable
private fun FullPlayerContent(onClose: () -> Unit, onLyricsClick: () -> Unit = {}) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val isPlaying by PlaybackConnection.isPlaying.collectAsState()
    val positionMs by PlaybackConnection.positionMs.collectAsState()
    val durationMs by PlaybackConnection.durationMs.collectAsState()
    val loopMode by PlaybackConnection.loopMode.collectAsState()
    val density = LocalDensity.current
    val nav = LocalPlayerNavigation.current

    // Poll position every 500ms while the player is visible
    LaunchedEffect(Unit) {
        while (true) {
            PlaybackConnection.pollPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            // Swipe down to close
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > with(density) { 80.dp.toPx() }) onClose()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Top: lyrics button + drag handle + close button ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                // Drag handle indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
                // Lyrics button (left)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLyricsClick
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lyrics),
                        contentDescription = "Lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                // Close button (right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = "Close player",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Middle: album art ──
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A1A)),
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

            // ── Bottom: title + artist + seek bar + controls ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title (tap → open album page for the current song's album)
                Text(
                    text = currentSong?.title ?: "Nothing playing",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = BillieEilishFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = currentSong != null,
                            onClick = { nav.onAlbumClick() },
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(6.dp))
                // Artist (tap → open artist page for the current song's artist)
                Text(
                    text = currentSong?.artist ?: "",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 16.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = currentSong != null,
                            onClick = { nav.onArtistClick() },
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )

                Spacer(Modifier.height(20.dp))

                // Seek bar
                val safeDuration = durationMs.coerceAtLeast(1L)
                val seekFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                var seeking by remember { mutableStateOf(false) }
                var seekValue by remember { mutableStateOf(0f) }

                Slider(
                    value = if (seeking) seekValue else seekFraction,
                    onValueChange = { value ->
                        seeking = true
                        seekValue = value
                    },
                    onValueChangeFinished = {
                        if (seeking) {
                            PlaybackConnection.seekTo((seekValue * safeDuration).roundToLong())
                            seeking = false
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Position / duration labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(if (seeking) (seekValue * safeDuration).roundToLong() else positionMs),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily,
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Controls: prev, play/pause, next — with loop on the right, so the
                // play button stays exactly centred.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Previous
                        PlayerButton(
                            iconRes = R.drawable.ic_prev,
                            contentDescription = "Previous",
                            size = 56.dp,
                            onClick = { PlaybackConnection.prev() },
                        )
                        Spacer(Modifier.width(28.dp))
                        // Play/pause (large)
                        PlayerButton(
                            iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            size = 72.dp,
                            filled = true,
                            onClick = { PlaybackConnection.playPause() },
                        )
                        Spacer(Modifier.width(28.dp))
                        // Next
                        PlayerButton(
                            iconRes = R.drawable.ic_next,
                            contentDescription = "Next",
                            size = 56.dp,
                            onClick = { PlaybackConnection.next() },
                        )
                    }

                    // Loop: OFF → ALL → ONE (bright when on, with a "1" for one-song)
                    val loopActive = loopMode != LoopMode.OFF
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (loopActive) 0.16f else 0.08f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { PlaybackConnection.cycleLoopMode() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (loopMode == LoopMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                            ),
                            contentDescription = when (loopMode) {
                                LoopMode.OFF -> "Loop off"
                                LoopMode.ALL -> "Loop queue"
                                LoopMode.ONE -> "Loop this song"
                            },
                            tint = Color.White.copy(alpha = if (loopActive) 1f else 0.45f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlayerButton(
    iconRes: Int,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (filled) Color.White else Color.White.copy(alpha = 0.08f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (filled) Color.Black else Color.White,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60L
    val seconds = (totalSec % 60L).toString().padStart(2, '0')
    return "$minutes:$seconds"
}
