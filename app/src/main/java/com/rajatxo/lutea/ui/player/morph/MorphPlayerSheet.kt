package com.rajatxo.lutea.ui.player.morph

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.rajatxo.lutea.R
import com.rajatxo.lutea.data.store.FavouriteStore
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.playback.LoopMode
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.util.rememberCoverThemeColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ─────────────────────────────────────────────────────────────────────────────
//  MorphPlayerSheet — one window that opens from the mini capsule into the
//  full player and then into the queue.
//
//  This is the Yuma-style sheet: the window is a *clipped, translated* box, not
//  a cross-fade of two players. One fraction drives everything:
//
//    top edge        collapsedTop  →  0            (follows the finger)
//    height          mini height   →  screen height
//    width           capsule       →  screen width
//    corner radius   28dp          →  0
//
//  The player content inside is always laid out at full screen size and pinned
//  to the window's top-left, so it is *revealed* as the window grows instead of
//  being re-laid-out every frame. The mini row is simply the top band of that
//  content, which is why the capsule turns into the player without a jump.
//
//  All per-frame values are read inside layout/draw lambdas, so a drag never
//  recomposes the tree.
// ─────────────────────────────────────────────────────────────────────────────

private val MiniHeight = 56.dp
private val MiniArt = 40.dp
private val MorphInk = Color(0xFF1C1C1E)

/** Bounce-free settle that carries the flick velocity into the spring. */
private val SettleSpec = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun MorphPlayerSheet(
    modifier: Modifier = Modifier,
    onLyricsClick: () -> Unit = {},
    onActiveChange: (Boolean) -> Unit = {},
    hidden: Boolean = false,
    navMatchedWidth: Dp? = null,
    miniBottomPadding: Dp = 0.dp,
) {
    val song by PlaybackConnection.currentSong.collectAsState()
    val isPlaying by PlaybackConnection.isPlaying.collectAsState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 0 → 1 : capsule → full player.  0 → 1 : full player → queue.
    val sheet = remember { Animatable(0f) }
    val queue = remember { Animatable(0f) }

    // Live drag values. Kept apart from the Animatables so a drag mutates a
    // plain float (cheap) instead of starting a coroutine per finger movement.
    var sheetDrag by remember { mutableFloatStateOf(0f) }
    var queueDrag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var queueAxis by remember { mutableStateOf(false) }

    val currentOnActiveChange by rememberUpdatedState(onActiveChange)
    val expanded by remember { derivedStateOf { (if (dragging) sheetDrag else sheet.value) >= 0.5f } }
    val queueOpen by remember { derivedStateOf { (if (dragging) queueDrag else queue.value) >= 0.5f } }
    val miniMounted by remember { derivedStateOf { (if (dragging) sheetDrag else sheet.value) < 0.4f } }
    val fullMounted by remember { derivedStateOf { (if (dragging) sheetDrag else sheet.value) > 0.02f } }
    val queueMounted by remember { derivedStateOf { (if (dragging) queueDrag else queue.value) > 0.001f } }
    val show = (song != null || expanded) && !hidden

    LaunchedEffect(Unit) {
        snapshotFlow { if (dragging) sheetDrag else sheet.value }
            .collect { currentOnActiveChange(it > 0.02f) }
    }
    LaunchedEffect(show) {
        if (!show) currentOnActiveChange(false)
    }

    // Keep the clock ticking while the player is on screen.
    LaunchedEffect(expanded) {
        while (expanded) {
            PlaybackConnection.pollPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    fun settle(velocity: Float, sheetTravelPx: Float, queueTravelPx: Float) {
        val f = sheetDrag
        val q = queueDrag
        val onQueue = queueAxis
        scope.launch {
            sheet.snapTo(f)
            queue.snapTo(q)
            dragging = false

            if (onQueue) {
                val target = when {
                    q <= 0f -> 0f
                    velocity < -500f -> 1f
                    velocity > 500f -> if (q > 0.725f) 0.45f else 0f
                    q < 0.225f -> 0f
                    q < 0.725f -> 0.45f
                    else -> 1f
                }
                if (target > 0f && f < 1f) sheet.animateTo(1f, SettleSpec)
                queue.animateTo(
                    target,
                    SettleSpec,
                    initialVelocity = (-velocity / queueTravelPx).coerceIn(-20f, 20f),
                )
            } else {
                val target = when {
                    velocity < -500f -> 1f
                    velocity > 500f -> 0f
                    f > 0.5f -> 1f
                    else -> 0f
                }
                sheet.animateTo(
                    target,
                    SettleSpec,
                    initialVelocity = (-velocity / sheetTravelPx).coerceIn(-20f, 20f),
                )
            }
        }
    }

    val collapse: () -> Unit = {
        if (!dragging) {
            scope.launch {
                queue.animateTo(0f, SettleSpec)
                sheet.animateTo(0f, SettleSpec)
            }
        }
    }
    val expand: () -> Unit = {
        if (!dragging) {
            scope.launch { sheet.animateTo(1f, SettleSpec) }
        }
    }

    // Back: queue first, then the player collapses to the capsule.
    BackHandler(enabled = queueOpen) {
        scope.launch { queue.animateTo(0f, SettleSpec) }
    }
    BackHandler(enabled = expanded && !queueOpen) { collapse() }

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxW = maxWidth
            val maxH = maxHeight
            val capsuleW = navMatchedWidth ?: (maxW - 32.dp)
            val insetDp = ((maxW - capsuleW) / 2f).coerceAtLeast(0.dp)
            val collapsedTop = (maxH - miniBottomPadding - MiniHeight).coerceAtLeast(0.dp)

            val screenWpx = with(density) { maxW.toPx() }
            val screenHpx = with(density) { maxH.toPx() }
            val collapsedTopPx = with(density) { collapsedTop.toPx() }
            val insetPx = with(density) { insetDp.toPx() }
            // The top edge travels this far over a full 0 → 1 drag.
            val sheetTravelPx = collapsedTopPx.coerceAtLeast(1f)
            val queueTravelPx = screenHpx.coerceAtLeast(1f)

            val sheetFrac: () -> Float = {
                (if (dragging) sheetDrag else sheet.value).coerceIn(0f, 1f)
            }
            val queueFrac: () -> Float = {
                (if (dragging) queueDrag else queue.value).coerceIn(0f, 1f)
            }
            val sheetTopPx: () -> Float = {
                collapsedTopPx + (0f - collapsedTopPx) * sheetFrac()
            }
            val windowInsetPx: () -> Float = {
                insetPx + (0f - insetPx) * sheetFrac()
            }
            val windowRadius: () -> Dp = { lerp(28.dp, 0.dp, sheetFrac()) }

            val statusTop = with(density) {
                WindowInsets.statusBars.getTop(this).toDp()
            }
            val navBottom = with(density) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }

            val dragState = rememberDraggableState { dy ->
                var d = dy
                var guard = 0
                while (guard++ < 4) {
                    if (queueAxis) {
                        val cur = queueDrag
                        val next = (cur - d / queueTravelPx).coerceIn(0f, 1f)
                        d -= (cur - next) * queueTravelPx
                        queueDrag = next
                        if (next <= 0f && d > 0f) {
                            queueAxis = false
                            continue
                        }
                    } else {
                        val cur = sheetDrag
                        val next = (cur - d / sheetTravelPx).coerceIn(0f, 1f)
                        d -= (cur - next) * sheetTravelPx
                        sheetDrag = next
                        if (next >= 1f && d < 0f) {
                            queueAxis = true
                            continue
                        }
                    }
                    break
                }
            }

            val seamColor by rememberCoverThemeColor(
                coverUri = song?.albumArtUri?.toString(),
                fallbackSeed = song?.id?.toString(),
            )
            val darkSurface = remember(seamColor) {
                Brush.verticalGradient(listOf(seamColor, Color(0xFF07070A)))
            }
            val capsuleGlass = remember {
                Brush.verticalGradient(listOf(Color(0xF7FFFFFF), Color(0xE4E9E9EE)))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, sheetTopPx().roundToInt()) }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // Reports the window size (moves with the drag).
                        .layout { measurable, _ ->
                            val w = (screenWpx - 2f * windowInsetPx()).roundToInt()
                                .coerceIn(1, screenWpx.roundToInt())
                            val h = (screenHpx - sheetTopPx()).roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) { placeable.place(0, 0) }
                        }
                        .graphicsLayer {
                            shape = RoundedCornerShape(windowRadius())
                            clip = sheetFrac() < 0.995f
                        }
                        // Lays the content out at full screen and pins it to the
                        // window's top-left, so it is revealed, not squeezed.
                        .layout { measurable, _ ->
                            val w = screenWpx.roundToInt()
                            val h = screenHpx.roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) {
                                placeable.place(-windowInsetPx().roundToInt(), 0)
                            }
                        }
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = {
                                dragging = true
                                sheetDrag = sheet.value
                                queueDrag = queue.value
                                queueAxis = queue.value > 0.001f && sheet.value >= 0.999f
                                scope.launch {
                                    sheet.stop()
                                    queue.stop()
                                }
                            },
                            onDragStopped = { velocity ->
                                settle(velocity, sheetTravelPx, queueTravelPx)
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── Surface: white capsule → dark cover-tinted player ──
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val f = sheetFrac()
                                    drawRect(
                                        brush = darkSurface,
                                        alpha = (f / 0.35f).coerceIn(0f, 1f),
                                    )
                                    drawRect(
                                        brush = capsuleGlass,
                                        alpha = (1f - f / 0.3f).coerceIn(0f, 1f),
                                    )
                                }
                        )

                        // ── Mini row — the top band of the content ──
                        if (miniMounted) {
                            MiniRow(
                                song = song,
                                isPlaying = isPlaying,
                                alpha = { 1f - sheetFrac() / 0.3f },
                                inset = insetDp,
                                onExpand = expand,
                            )
                        }

                        // ── Full player — fades in and drifts up ──
                        if (fullMounted) {
                            FullPlayer(
                                song = song,
                                isPlaying = isPlaying,
                                alpha = { ((sheetFrac() - 0.25f) / 0.75f).coerceIn(0f, 1f) },
                                shiftPx = { (1f - ((sheetFrac() - 0.25f) / 0.75f).coerceIn(0f, 1f)) * 150f },
                                statusTop = statusTop,
                                navBottom = navBottom,
                                onCollapse = collapse,
                                onLyricsClick = onLyricsClick,
                                onQueueOpen = {
                                    scope.launch { queue.animateTo(1f, SettleSpec) }
                                },
                            )
                        }

                        // ── Queue — slides up over the player ──
                        if (queueMounted) {
                            QueueLayer(
                                current = song,
                                fraction = queueFrac,
                                travelPx = queueTravelPx,
                                statusTop = statusTop,
                                navBottom = navBottom,
                                onClose = { scope.launch { queue.animateTo(0f, SettleSpec) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini row — the capsule contents. Positioned at the very top of the content,
//  horizontally inset by the capsule margin.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniRow(
    song: Song?,
    isPlaying: Boolean,
    alpha: () -> Float,
    inset: Dp,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MiniHeight)
            .padding(horizontal = inset)
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpand,
            )
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(uri = song?.albumArtUri, size = MiniArt, radius = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: "",
                color = MorphInk,
                fontSize = 14.sp,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song?.artist ?: "",
                color = MorphInk.copy(alpha = 0.62f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        MiniButton(
            iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = { PlaybackConnection.playPause() },
        )
        Spacer(Modifier.width(4.dp))
        MiniButton(
            iconRes = R.drawable.ic_next,
            contentDescription = "Next",
            onClick = { PlaybackConnection.next() },
        )
    }
}

@Composable
private fun MiniButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MorphInk.copy(alpha = 0.06f))
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
            tint = MorphInk,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Full player — toolbar / cover / controls, all inside the window's content.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullPlayer(
    song: Song?,
    isPlaying: Boolean,
    alpha: () -> Float,
    shiftPx: () -> Float,
    statusTop: Dp,
    navBottom: Dp,
    onCollapse: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha()
                translationY = shiftPx()
            }
            .drawBehind {
                // Legibility scrims over the cover-tinted surface.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.28f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    )
                )
            },
    ) {
        // ── Toolbar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 6.dp, start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                iconRes = R.drawable.ic_chevron_up,
                contentDescription = "Collapse player",
                rotationDeg = 180f,
                onClick = onCollapse,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                iconRes = R.drawable.ic_lyrics,
                contentDescription = "Lyrics",
                onClick = onLyricsClick,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                iconRes = R.drawable.ic_queue,
                contentDescription = "Queue",
                onClick = onQueueOpen,
            )
        }

        // ── Cover ──
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
            val side = minOf(maxWidth, (maxHeight - 12.dp).coerceAtLeast(96.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(side)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF1B1B1E))
                    .graphicsLayer {
                        val a = alpha()
                        alpha = a
                        scaleX = 0.92f + 0.08f * a
                        scaleY = 0.92f + 0.08f * a
                    },
            ) {
                Artwork(
                    uri = song?.albumArtUri,
                    size = side,
                    radius = 26.dp,
                    crisp = true,
                )
            }
        }

        // ── Controls ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = navBottom + 18.dp),
        ) {
            MetadataRow(song = song)
            Spacer(Modifier.height(18.dp))
            SeekBar()
            Spacer(Modifier.height(14.dp))
            TransportRow(isPlaying = isPlaying)
        }
    }
}

@Composable
private fun MetadataRow(song: Song?) {
    val favourites by FavouriteStore.favourites.collectAsState()
    val isFav = song != null && song.id in favourites
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: "Nothing playing",
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = BillieEilishFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song?.artist ?: "",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        IconButton(
            iconRes = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
            contentDescription = if (isFav) "Unfavourite" else "Favourite",
            tint = if (isFav) Color(0xFFFF6B6B) else Color.White,
            onClick = { song?.let { FavouriteStore.toggle(it.id) } },
        )
    }
}

@Composable
private fun SeekBar() {
    val positionMs by PlaybackConnection.positionMs.collectAsState()
    val durationMs by PlaybackConnection.durationMs.collectAsState()
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    val shown = if (seeking) seekValue else fraction

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            seeking = true
                            seekValue = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (seeking) {
                                PlaybackConnection.seekTo((seekValue * safeDuration).roundToLong())
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
                    .height(if (seeking) 6.dp else 3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .height(if (seeking) 6.dp else 3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(if (seeking) (seekValue * safeDuration).roundToLong() else positionMs),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
            )
            Text(
                text = "-" + formatMs(
                    (safeDuration - if (seeking) (seekValue * safeDuration).roundToLong() else positionMs)
                        .coerceAtLeast(0L)
                ),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
            )
        }
    }
}

@Composable
private fun TransportRow(isPlaying: Boolean) {
    val positionMs by PlaybackConnection.positionMs.collectAsState()
    val durationMs by PlaybackConnection.durationMs.collectAsState()
    val loopMode by PlaybackConnection.loopMode.collectAsState()

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                iconRes = R.drawable.ic_skip_back_10,
                contentDescription = "Back 10 seconds",
                iconSize = 24.dp,
                onClick = {
                    PlaybackConnection.seekTo((positionMs - 10_000L).coerceAtLeast(0L))
                },
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                iconRes = R.drawable.ic_prev,
                contentDescription = "Previous",
                iconSize = 28.dp,
                onClick = { PlaybackConnection.prev() },
            )
            Spacer(Modifier.width(10.dp))
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
            Spacer(Modifier.width(10.dp))
            IconButton(
                iconRes = R.drawable.ic_next,
                contentDescription = "Next",
                iconSize = 28.dp,
                onClick = { PlaybackConnection.next() },
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                iconRes = R.drawable.ic_skip_forward_10,
                contentDescription = "Forward 10 seconds",
                iconSize = 24.dp,
                onClick = {
                    PlaybackConnection.seekTo(
                        (positionMs + 10_000L)
                            .coerceAtMost(durationMs.coerceAtLeast(0L))
                    )
                },
            )
        }
        val loopActive = loopMode != LoopMode.OFF
        IconButton(
            iconRes = if (loopMode == LoopMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
            contentDescription = when (loopMode) {
                LoopMode.OFF -> "Loop off"
                LoopMode.ALL -> "Loop queue"
                LoopMode.ONE -> "Loop this song"
            },
            iconSize = 20.dp,
            tint = Color.White.copy(alpha = if (loopActive) 1f else 0.4f),
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = { PlaybackConnection.cycleLoopMode() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Queue — slides up over the player; 0.45 is the half-open "peek".
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QueueLayer(
    current: Song?,
    fraction: () -> Float,
    travelPx: Float,
    statusTop: Dp,
    navBottom: Dp,
    onClose: () -> Unit,
) {
    val songs = remember(current) { PlaybackConnection.currentQueue }
    val index = remember(current) { PlaybackConnection.currentIndex }
    val listState = rememberLazyListState()

    LaunchedEffect(index) {
        if (index >= 0 && songs.isNotEmpty()) {
            listState.scrollToItem(index.coerceAtMost(songs.lastIndex))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val f = fraction()
                translationY = (1f - f) * travelPx
                alpha = (f / 0.25f).coerceIn(0f, 1f)
            }
            .background(Color(0xF7070709))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusTop + 14.dp, start = 24.dp, end = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Up Next",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = BillieEilishFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (songs.isEmpty()) "Queue is empty" else "${songs.size} in queue",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontFamily = CalSansFamily,
                    )
                }
                IconButton(
                    iconRes = R.drawable.ic_chevron_up,
                    contentDescription = "Close queue",
                    rotationDeg = 180f,
                    onClick = onClose,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 28.dp,
                ),
            ) {
                itemsIndexed(songs, key = { i, s -> "$i_${s.id}" }) { i, s ->
                    QueueRow(
                        song = s,
                        isCurrent = i == index,
                        onClick = { PlaybackConnection.playQueueItem(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(uri = song.albumArtUri, size = 48.dp, radius = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontFamily = CalSansFamily,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Small shared pieces.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Artwork(
    uri: Uri?,
    size: Dp,
    radius: Dp,
    crisp: Boolean = false,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Color(0xFF1B1B1E)),
        contentAlignment = Alignment.Center,
    ) {
        uri?.let {
            val request = remember(it, crisp) {
                ImageRequest.Builder(context)
                    .data(it)
                    .size(if (crisp) Size(1080, 1080) else Size(192, 192))
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
}

@Composable
private fun IconButton(
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    tint: Color = Color.White,
    rotationDeg: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
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
                .size(iconSize)
                .graphicsLayer { rotationZ = rotationDeg },
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}
