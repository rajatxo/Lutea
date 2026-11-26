package com.rajatxo.lutea.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.rajatxo.lutea.R
import com.rajatxo.lutea.data.store.FavouriteStore
import com.rajatxo.lutea.playback.LoopMode
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.util.rememberCoverThemeColor
import kotlin.math.roundToLong

/**
 * Navigation callbacks for the FullPlayer. Wired through a CompositionLocal so
 * the scaffold doesn't have to know about them — QuickPicksScreen provides the
 * real handlers around the scaffold, and they're read here.
 */
data class PlayerNavigation(
    val onAlbumClick: () -> Unit = {},
    val onArtistClick: () -> Unit = {},
)

val LocalPlayerNavigation = staticCompositionLocalOf { PlayerNavigation() }

/**
 * FullPlayer — the immersive full-screen player.
 *
 * The hero cover is a full-width square flush with the top edge; its bottom
 * edge dissolves (DstIn) into a heavily blurred copy of the same cover, so the
 * art melts into the background instead of ending on a hard line. The controls
 * sit over the blur at the bottom: title/artist + favourite, thin seek bar, and
 * the transport row with ±10s and loop.
 *
 * Swipe down or tap close to go back to the mini player.
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
    val favourites by FavouriteStore.favourites.collectAsState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val nav = LocalPlayerNavigation.current

    // Themed seam colour = the cover's own dominant colour. Shows behind the
    // blur while the cover decodes, so a cold start never flashes black.
    val seamColor by rememberCoverThemeColor(
        coverUri = currentSong?.albumArtUri?.toString(),
        fallbackSeed = currentSong?.id?.toString(),
    )

    LaunchedEffect(Unit) {
        while (true) {
            PlaybackConnection.pollPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(seamColor)
            // Swipe down to close.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > with(density) { 80.dp.toPx() }) onClose()
                    }
                )
            }
    ) {
        val maxW = maxWidth
        val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val artUri = currentSong?.albumArtUri

        // ── Immersive backdrop: the cover, heavily blurred ──
        artUri?.let { uri ->
            val blurRequest = remember(uri) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(Size(540, 540))
                    .build()
            }
            AsyncImage(
                model = blurRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(96.dp),
            )
        }

        // ── Hero cover: full width, flush with the top, melting into the blur ──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .aspectRatio(1f)
                // Offscreen is required for DstIn to erase the cover's own pixels.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .background(Color(0xFF1A1A1A))
                .drawWithContent {
                    drawContent()
                    // Bottom dissolves into the blurred background. Tall enough
                    // that there is no visible edge where the art ends.
                    val fadeHeight = (size.height * 0.45f)
                        .coerceAtMost(160.dp.toPx())
                        .coerceAtLeast(0f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = (size.height - fadeHeight).coerceAtLeast(0f),
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            artUri?.let { uri ->
                val request = remember(uri) {
                    ImageRequest.Builder(context)
                        .data(uri)
                        .size(Size(1080, 1080))
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── Top bar: lyrics + drag handle + close ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.55f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(
                iconRes = R.drawable.ic_lyrics,
                contentDescription = "Lyrics",
                onClick = onLyricsClick,
            )
            RoundIconButton(
                iconRes = R.drawable.ic_close_circle,
                contentDescription = "Close player",
                iconSize = 20.dp,
                onClick = onClose,
            )
        }

        // ── Bottom: controls over the blur ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = navBottom + 20.dp),
        ) {
            // Title / artist + favourite + collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "Nothing playing",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontFamily = BillieEilishFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = currentSong != null,
                                onClick = { nav.onAlbumClick() },
                            )
                            .padding(vertical = 2.dp),
                    )
                    Text(
                        text = currentSong?.artist ?: "",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 15.sp,
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
                            .padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                val isFav = currentSong?.id in favourites
                IconCircleButton(
                    iconRes = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                    contentDescription = if (isFav) "Unfavourite" else "Favourite",
                    size = 22.dp,
                    tint = if (isFav) Color(0xFFFF6B6B) else Color.White,
                    bgAlpha = 0f,
                    onClick = { currentSong?.let { FavouriteStore.toggle(it.id) } },
                )
                Spacer(Modifier.width(4.dp))
                IconCircleButton(
                    iconRes = R.drawable.ic_chevron_up,
                    contentDescription = "Collapse player",
                    size = 22.dp,
                    rotationDeg = 180f,
                    bgAlpha = 0f,
                    onClick = onClose,
                )
            }

            Spacer(Modifier.height(20.dp))

            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { PlaybackConnection.seekTo(it) },
            )

            Spacer(Modifier.height(22.dp))

            TransportRow(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                loopMode = loopMode,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Seek bar — thin track with a small dot at the played position, grows under
//  the finger while scrubbing.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    val shown = if (seeking) seekValue else fraction
    val trackHeight by animateDpAsState(
        targetValue = if (seeking) 6.dp else 3.dp,
        animationSpec = tween(180),
        label = "seekTrack",
    )

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            seeking = true
                            seekValue = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (seeking) {
                                onSeek((seekValue * safeDuration).roundToLong())
                                seeking = false
                            }
                        },
                        onDragCancel = { seeking = false },
                        onHorizontalDrag = { change, _ ->
                            seekValue = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White.copy(alpha = 0.24f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White),
            )
            // Playhead dot.
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .height(24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (seeking) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(if (seeking) (seekValue * safeDuration).roundToLong() else positionMs),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
            )
            Text(
                text = formatTime(durationMs),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Transport — -10s / prev / play / next / +10s, loop tucked at the right.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    loopMode: LoopMode,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                iconRes = R.drawable.ic_skip_back_10,
                contentDescription = "Back 10 seconds",
                size = 24.dp,
                onClick = {
                    PlaybackConnection.seekTo((positionMs - 10_000L).coerceAtLeast(0L))
                },
            )
            Spacer(Modifier.width(6.dp))
            IconCircleButton(
                iconRes = R.drawable.ic_prev,
                contentDescription = "Previous",
                size = 28.dp,
                onClick = { PlaybackConnection.prev() },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { PlaybackConnection.playPause() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            IconCircleButton(
                iconRes = R.drawable.ic_next,
                contentDescription = "Next",
                size = 28.dp,
                onClick = { PlaybackConnection.next() },
            )
            Spacer(Modifier.width(6.dp))
            IconCircleButton(
                iconRes = R.drawable.ic_skip_forward_10,
                contentDescription = "Forward 10 seconds",
                size = 24.dp,
                onClick = {
                    PlaybackConnection.seekTo(
                        (positionMs + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))
                    )
                },
            )
        }
        val loopActive = loopMode != LoopMode.OFF
        IconCircleButton(
            iconRes = if (loopMode == LoopMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
            contentDescription = when (loopMode) {
                LoopMode.OFF -> "Loop off"
                LoopMode.ALL -> "Loop queue"
                LoopMode.ONE -> "Loop this song"
            },
            size = 20.dp,
            tint = Color.White.copy(alpha = if (loopActive) 1f else 0.45f),
            bgAlpha = if (loopActive) 0.16f else 0f,
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = { PlaybackConnection.cycleLoopMode() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoundIconButton(
    iconRes: Int,
    contentDescription: String,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(iconSize + 18.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
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
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun IconCircleButton(
    iconRes: Int,
    contentDescription: String,
    size: Dp,
    tint: Color = Color.White,
    bgAlpha: Float = 0.08f,
    rotationDeg: Float = 0f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = bgAlpha))
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
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotationDeg },
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60L
    val seconds = (totalSec % 60L).toString().padStart(2, '0')
    return "$minutes:$seconds"
}
