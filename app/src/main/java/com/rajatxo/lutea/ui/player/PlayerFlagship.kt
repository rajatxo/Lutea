package com.rajatxo.lutea.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.lutea.R
import com.rajatxo.lutea.data.store.FavouriteStore
import com.rajatxo.lutea.playback.LoopMode
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.util.rememberCoverThemeColor

import kotlin.math.roundToLong

// ─────────────────────────────────────────────────────────────────────────────
// PlayerCoverBlend — Coral Spiral3 cover block, ported faithfully, with the
// edits the user asked for (corrected after seeing the screenshots).
//
// Spiral3 layout (Spiral3Player.kt lines 722–791):
//   (1) 96dp BLURRED full-screen album art = the immersive background canvas.
//   (2) Sharp SQUARE cover (TopCenter) with a bottom DstIn fade so the sharp
//       cover melts into the blur at its lower edge.
//
// The edits:
//   EDIT 1 — "remove the blur part from the top": the TOP FADE that hazed the
//     cover's top edge is REMOVED (was 120dp Transparent→Black DstIn). The
//     96dp blurred bg + the bottom 140dp fade stay. The cover's top is crisp.
//   EDIT 2 — universal fitting rule: the WHOLE cover is visible (square box,
//     aspectRatio 1f, ContentScale.Fit — nothing cropped, no logo clip).
//   EDIT 3 — offset 0dp (top edge touches screen top edge) + gentle 1.06 zoom
//     with top-center pivot (grows downward, no top crop). Bottom 140dp fade
//     is drawn inside the box, so it moves up + grows with the cover.
//
//   EDIT 4 — SMOOTH SONG TRANSITION (no black flash between songs):
//     Problem: when the song changed, the new cover wasn't in Coil's memory
//     cache yet, so both AsyncImages rendered EMPTY while the parent
//     Box(background(Color.Black)) showed through = a ~0.5s BLACK FLASH. Going
//     back (previous) was instant because that cover was just shown (cached).
//     Two fixes, both applied:
//       WAY 1 — keep the OLD cover painted during the new cover's load. Each
//         cover layer remembers its LAST successful painter (State.Success).
//         While the new song's painter is Loading/Empty/Error, the previous
//         Success painter stays painted underneath — the screen never goes
//         black. Only when the new painter reaches Success does the old layer
//         drop out. Also the parent background switched Color.Black -> the
//         cover's themed seam colour, so even a true cold-start shows a themed
//         colour instead of pure black.
//       WAY 2 — PREFETCH the next 5 songs' covers into Coil's memory cache.
//         A LaunchedEffect keyed on currentSong reads PlaybackConnection's
//         queue + currentIndex, takes the next 5 songs (respecting LoopMode
//         wrap), and enqueues a prefetch request for each cover at the
//         player's display size. Coil shares its memory+disk cache across all
//         callers, so by the time you hit next, the cover is already decoded
//         and the swap is instant.
//     Implementation detail: each layer uses rememberAsyncImagePainter +
//     painter.state (a StateFlow<State> with Empty/Loading/Success/Error).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlayerCoverBlend(
    modifier: Modifier = Modifier,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val loopMode by PlaybackConnection.loopMode.collectAsState()
    val context = LocalContext.current
    val coverUriStr = currentSong?.albumArtUri?.toString()

    // Themed seam colour = the cover's own dominant colour (extracted via
    // Palette, cached). Used as the parent background instead of pure black,
    // so a cold-start load shows a themed colour, never a black flash.
    val seamColor by rememberCoverThemeColor(
        coverUri = coverUriStr,
        fallbackSeed = currentSong?.id?.toString(),
    )

    val request = remember(coverUriStr) {
        if (coverUriStr.isNullOrBlank()) null
        else ImageRequest.Builder(context).data(coverUriStr).crossfade(true).build()
    }

    // WAY 2 — prefetch the next 5 songs' covers into Coil's memory cache so the
    // next-song swap is instant (no decode on the critical path). Runs whenever
    // the current song (or loop mode) changes.
    LaunchedEffect(currentSong?.id, loopMode) {
        val queue = PlaybackConnection.currentQueue
        val idx = PlaybackConnection.currentIndex
        if (queue.isEmpty() || idx < 0) return@LaunchedEffect

        // Next 5, respecting loop wrap-around (ALL wraps, ONE/OFF stop at end).
        val upcoming = buildList {
            for (step in 1..5) {
                val nextIdx = when (loopMode) {
                    LoopMode.ONE -> idx // same song re-queueing — skip prefetch churn
                    LoopMode.OFF -> idx + step
                    LoopMode.ALL -> (idx + step) % queue.size
                }
                if (nextIdx !in queue.indices) break
                add(queue[nextIdx])
            }
        }

        val imageLoader = SingletonImageLoader.get(context.applicationContext)
        upcoming.forEach { song ->
            val art = song.albumArtUri?.toString() ?: return@forEach
            // Match the player's display size so the prefetched bitmap is the
            // SAME cache entry the on-screen AsyncImage will request.
            val prefetch = ImageRequest.Builder(context.applicationContext)
                .data(art)
                .size(1080, 1080)
                .crossfade(true)
                .build()
            imageLoader.enqueue(prefetch)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(seamColor),
    ) {
        // (1) 96dp BLURRED full-screen album art — the immersive background
        //     canvas. Verbatim Spiral3 (lines 722–733). WAY 1: keeps the
        //     previous cover painted during the new cover's load.
        if (request != null) {
            CrossfadeCoverLayer(
                request = request,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(96.dp),
            )
        }

        // (2) Sharp SQUARE cover (Spiral3 block, lines 735–791) — SQUARE box
        //     (aspectRatio 1f), TopCenter, offset 0dp (top edge touches screen
        //     top edge), gentle 1.06 top-center-pivot zoom. Universal fitting:
        //     full image visible. Bottom 140dp fade inside the box moves up +
        //     grows with the cover. WAY 1: keeps the previous cover painted
        //     during the new cover's load.
        if (request != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .align(Alignment.TopCenter)
                    .offset(y = 0.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleX = 1.06f
                        scaleY = 1.06f
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .drawWithContent {
                        drawContent()
                        val bottomFadeHeightPx = 140.dp.toPx()
                        val imageHeight = size.height
                        val bottomFadeStartY =
                            (imageHeight - bottomFadeHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = bottomFadeStartY,
                                endY = imageHeight,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                CrossfadeCoverLayer(
                    request = request,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CrossfadeCoverLayer — the WAY-1 building block. Renders the cover for the
// given request, and while that cover is still loading (or failed), keeps the
// PREVIOUS successfully-loaded cover painted underneath. So the layer never
// shows empty/black during a song change — the old cover stays until the new
// one is ready, then crossfades in.
//
// Mechanism: rememberAsyncImagePainter loads the new request; painter.state is
// a StateFlow<State> (Empty/Loading/Success/Error). We stash the painter from
// the last Success into a `remember`-held var, and while the current state is
// NOT Success we render that stashed painter first (underneath), then the live
// painter on top. When the live painter reaches Success, it paints over the
// stashed one and we update the stash. Result: the screen is never empty.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CrossfadeCoverLayer(
    request: ImageRequest,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember { SingletonImageLoader.get(context.applicationContext) }

    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = imageLoader,
        contentScale = contentScale,
    )
    val state by painter.state.collectAsState()

    // The last SUCCESSFULLY-LOADED painter, KEPT ACROSS cover changes. This is
    // the heart of WAY 1: it is NOT keyed on request.data, so when the song
    // changes and the new cover is still loading, this still holds the OLD
    // cover's painter and renders it underneath. Only when the new painter
    // reaches Success do we overwrite it — so the screen is never empty/black.
    var lastSuccessPainter by remember { mutableStateOf<Painter?>(null) }
    LaunchedEffect(state) {
        if (state is AsyncImagePainter.State.Success) {
            lastSuccessPainter = (state as AsyncImagePainter.State.Success).painter
        }
    }

    // Underneath: the previous successful cover. Painted first so it shows
    // through while the new cover loads (no black flash).
    lastSuccessPainter?.let { prev ->
        Image(
            painter = prev,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    }

    // On top: the live painter. While Loading it's empty (transparent) so the
    // underneath layer shows; on Success it paints over the underneath layer
    // and updates the stash.
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerControlBar — the persistent bottom control cluster. Drawn ONCE at the
// scaffold level so it sits over the player AND the lyrics page AND the queue
// page — Mochy keeps the seek bar + transport visible on all three.
//
// Layout (matches Mochy Flagship):
//   title/artist .............. heart + up-chevron(-> queue/lyrics)
//   ─────── seek bar ───────  0:06                    -3:06
//   -10s    prev    [play]    next    +10s      (loop)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlayerControlBar(
    onClose: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val isPlaying by PlaybackConnection.isPlaying.collectAsState()
    val positionMs by PlaybackConnection.positionMs.collectAsState()
    val durationMs by PlaybackConnection.durationMs.collectAsState()
    val loopMode by PlaybackConnection.loopMode.collectAsState()
    val favourites by FavouriteStore.favourites.collectAsState()
    val nav = LocalPlayerNavigation.current

    // Poll position while the control bar exists (it persists across pages).
    LaunchedEffect(Unit) {
        while (true) {
            PlaybackConnection.pollPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        // ── Heart + up-chevron row (title/artist now live in the morphing
        //    SharedTitle inside PlayerSheet — one continuous element mini→full,
        //    so they are removed here to avoid a duplicate title when expanded) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            val isFav = currentSong?.id in favourites
            FlagshipIconButton(
                iconRes = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                contentDescription = if (isFav) "Unfavourite" else "Favourite",
                size = 22.dp,
                tint = if (isFav) Color(0xFFFF6B6B) else Color.White,
                onClick = { currentSong?.let { FavouriteStore.toggle(it.id) } },
            )
            Spacer(Modifier.width(6.dp))
            // Up-chevron opens the queue (scroll-up gesture equivalent).
            FlagshipIconButton(
                iconRes = R.drawable.ic_chevron_up,
                contentDescription = "Queue",
                size = 22.dp,
                onClick = onQueueClick,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── Seek bar — thin at rest, swells under the finger ──
        SeekBarRow(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = { PlaybackConnection.seekTo(it) },
        )

        Spacer(Modifier.height(20.dp))

        // ── Transport: -10s / prev / play / next / +10s, loop at the edge ──
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlagshipIconButton(
                    iconRes = R.drawable.ic_skip_back_10,
                    contentDescription = "Back 10 seconds",
                    size = 26.dp,
                    onClick = {
                        val target = (positionMs - 10_000L).coerceAtLeast(0L)
                        PlaybackConnection.seekTo(target)
                    },
                )
                Spacer(Modifier.width(20.dp))
                FlagshipIconButton(
                    iconRes = R.drawable.ic_prev,
                    contentDescription = "Previous",
                    size = 30.dp,
                    onClick = { PlaybackConnection.prev() },
                )
                Spacer(Modifier.width(24.dp))
                // Play/pause — the large filled circle, dead-centre.
                Box(
                    modifier = Modifier
                        .size(68.dp)
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
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.width(24.dp))
                FlagshipIconButton(
                    iconRes = R.drawable.ic_next,
                    contentDescription = "Next",
                    size = 30.dp,
                    onClick = { PlaybackConnection.next() },
                )
                Spacer(Modifier.width(20.dp))
                FlagshipIconButton(
                    iconRes = R.drawable.ic_skip_forward_10,
                    contentDescription = "Forward 10 seconds",
                    size = 26.dp,
                    onClick = {
                        val target = (positionMs + 10_000L)
                            .coerceAtMost(durationMs.coerceAtLeast(0L))
                        PlaybackConnection.seekTo(target)
                    },
                )
            }
            // Loop tucked at the right edge so the play button stays centred.
            val loopActive = loopMode != LoopMode.OFF
            FlagshipIconButton(
                iconRes = if (loopMode == LoopMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                contentDescription = when (loopMode) {
                    LoopMode.OFF -> "Loop off"
                    LoopMode.ALL -> "Loop queue"
                    LoopMode.ONE -> "Loop this song"
                },
                size = 22.dp,
                tint = Color.White.copy(alpha = if (loopActive) 1f else 0.45f),
                bgAlpha = if (loopActive) 0.16f else 0f,
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = { PlaybackConnection.cycleLoopMode() },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Seek bar — thin at rest (3dp), swells under the finger (6dp), no thumb at
// rest, small thumb while dragging. Mochy Flagship seek bar.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SeekBarRow(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }
    val trackHeight by animateDpAsState(
        targetValue = if (seeking) 6.dp else 3.dp,
        animationSpec = tween(180),
        label = "seekTrack",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White.copy(alpha = 0.24f))
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
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (seeking) seekValue else fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(if (seeking) (seekValue * safeDuration).roundToLong() else positionMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
            )
            Text(
                text = formatTime(durationMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
            )
        }
    }
}

/** A small round icon button used in the title row and transport. */
@Composable
private fun FlagshipIconButton(
    iconRes: Int,
    contentDescription: String,
    size: Dp,
    tint: Color = Color.White,
    bgAlpha: Float = 0.08f,
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
            modifier = Modifier.size(size),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60L
    val seconds = (totalSec % 60L).toString().padStart(2, '0')
    return "$minutes:$seconds"
}
