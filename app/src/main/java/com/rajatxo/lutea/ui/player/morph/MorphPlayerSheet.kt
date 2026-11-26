package com.rajatxo.lutea.ui.player.morph

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
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
//  MorphPlayerSheet — one surface that is the mini player, the full player and
//  the queue, connected by two continuous motion axes:
//
//    expansion (0 → 1)  mini capsule  ⇄  full player
//    queue     (0 → 1)  full player   ⇄  queue list
//
//  A single vertical drag drives whichever axis the gesture belongs to; a flick
//  carries its own momentum into the spring settle, so the open/close reads as
//  the capsule *growing* and the queue *sliding up over* the player — not as a
//  cross-fade between separate screens. One shared cover art grows from the
//  40dp thumbnail into the hero cover and then shrinks + fades as the queue
//  rises. Reimplemented for Lutea; it does not touch the legacy PlayerSheet.
// ─────────────────────────────────────────────────────────────────────────────

/** Dark ink used for text on the collapsed white-glass capsule. */
private val MorphInk = Color(0xFF1C1C1E)

/** Height of the collapsed capsule. */
private val MorphMiniHeight = 56.dp

/** Soft, bounce-free settle that accepts the drag's flick velocity. */
private val SettleSpec = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun MorphPlayerSheet(
    modifier: Modifier = Modifier,
    onLyricsClick: () -> Unit = {},
    onActiveChange: (Boolean) -> Unit = {},
    hidden: Boolean = false,
    backdrop: LayerBackdrop? = null,
    navMatchedWidth: Dp? = null,
    miniBottomPadding: Dp = 0.dp,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val isPlaying by PlaybackConnection.isPlaying.collectAsState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val expansion = remember { Animatable(0f) }
    val queue = remember { Animatable(0f) }

    val expanded by remember { derivedStateOf { expansion.value > 0.5f } }
    val queueOpen by remember { derivedStateOf { queue.value > 0.5f } }

    // Tell the host when the player owns the screen, so the nav bar dissolves.
    LaunchedEffect(Unit) {
        snapshotFlow { expansion.value > 0.02f }.collect { onActiveChange(it) }
    }

    // Keep the position clock ticking while the full player or queue is visible.
    LaunchedEffect(expanded) {
        while (expanded) {
            PlaybackConnection.pollPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    // Back: queue closes first, then the player collapses.
    BackHandler(enabled = queueOpen) {
        scope.launch { queue.animateTo(0f, SettleSpec) }
    }
    BackHandler(enabled = expanded && !queueOpen) {
        scope.launch { queue.snapTo(0f); expansion.animateTo(0f, SettleSpec) }
    }

    val seamColor by rememberCoverThemeColor(
        coverUri = currentSong?.albumArtUri?.toString(),
        fallbackSeed = currentSong?.id?.toString(),
    )

    AnimatedVisibility(
        visible = (currentSong != null || expanded) && !hidden,
        enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(),
        exit = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(),
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxW = maxWidth
            val maxH = maxHeight
            val e = expansion.value
            val q = queue.value
            // Queue peek finishes at q = 0.55, the rest of the travel is the list
            // sliding fully over the player.
            val qPeek = (q / 0.55f).coerceIn(0f, 1f)

            val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

            val capsuleW = navMatchedWidth ?: (maxW - 32.dp)
            val sheetW = lerp(capsuleW, maxW, e)
            val sheetH = lerp(MorphMiniHeight, maxH, e)
            val sheetBottom = lerp(miniBottomPadding, 0.dp, e)
            val sheetShape = RoundedCornerShape(lerp(MorphMiniHeight / 2f, 0.dp, e))

            val glassAlpha = (1f - e * 4f).coerceIn(0f, 1f)
            val darkAlpha = (e * 2.2f).coerceIn(0f, 1f)
            val miniAlpha = (1f - e * 3.5f).coerceIn(0f, 1f)
            val fullAlpha = ((e - 0.55f) / 0.35f).coerceIn(0f, 1f)
            val blurAlpha = ((e - 0.25f) / 0.5f).coerceIn(0f, 1f)

            // ── Shared cover geometry ──
            val miniArtSize = 40.dp
            val miniArtInset = 12.dp
            val heroArtSize = maxW

            // mini → hero (driven by expansion)
            val artSizeE = lerp(miniArtSize, heroArtSize, e)
            val artCxE = lerp(miniArtInset + miniArtSize / 2f, maxW / 2f, e)
            val artCyE = lerp(MorphMiniHeight / 2f, maxW / 2f, e)
            val artRadiusE = lerp(8.dp, 0.dp, e)

            // hero → peek (driven by queue): the cover shrinks and drifts up, then
            // fades as the queue list rises over it.
            val coverLift = 72.dp
            val artSize = lerp(artSizeE, heroArtSize * 0.82f, qPeek)
            val artCx = artCxE
            val artCy = artCyE - coverLift * qPeek
            val artRadius = lerp(artRadiusE, 28.dp, qPeek)
            val coverAlpha = (1f - qPeek)

            val expandTravelPx = with(density) {
                (maxH - MorphMiniHeight - miniBottomPadding).toPx()
            }.coerceAtLeast(1f)
            val queueTravelPx = with(density) { maxH.toPx() }.coerceAtLeast(1f)

            // ── One drag, two axes ──
            val dragState = rememberDraggableState { dy ->
                scope.launch {
                    val onQueueAxis = queue.value > 0f || (expansion.value >= 0.999f && dy < 0f)
                    if (onQueueAxis) {
                        queue.snapTo((queue.value - dy / queueTravelPx).coerceIn(0f, 1f))
                    } else {
                        if (queue.value > 0f) queue.snapTo(0f)
                        expansion.snapTo((expansion.value - dy / expandTravelPx).coerceIn(0f, 1f))
                    }
                }
            }
            val settle: (Float) -> Unit = { velocity ->
                val onQueueAxis = queue.value > 0f
                if (onQueueAxis) {
                    val target = when {
                        velocity < -1000f -> 1f
                        velocity > 1000f -> 0f
                        else -> if (queue.value > 0.4f) 1f else 0f
                    }
                    scope.launch {
                        queue.animateTo(target, SettleSpec, initialVelocity = -velocity / queueTravelPx)
                    }
                } else {
                    val target = when {
                        velocity < -1200f -> 1f
                        velocity > 1200f -> 0f
                        else -> if (expansion.value > 0.4f) 1f else 0f
                    }
                    scope.launch {
                        if (target == 0f && queue.value > 0f) queue.snapTo(0f)
                        expansion.animateTo(target, SettleSpec, initialVelocity = -velocity / expandTravelPx)
                    }
                }
            }

            // ── Collapsed capsule glass ──
            if (glassAlpha > 0f) {
                MorphCapsuleGlass(
                    backdrop = backdrop,
                    width = capsuleW,
                    bottomPadding = miniBottomPadding,
                    alpha = glassAlpha,
                )
            }

            // ── The morphing sheet ──
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = sheetBottom)
                    .width(sheetW)
                    .height(sheetH)
                    .clip(sheetShape)
                    .background(seamColor.copy(alpha = darkAlpha))
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    .then(
                        if (expanded) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {} else Modifier
                    )
            ) {
                // Immersive blurred backdrop the cover melts into.
                if (blurAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = blurAlpha }
                            .background(seamColor)
                    ) {
                        currentSong?.albumArtUri?.let { artUri ->
                            val blurRequest = remember(artUri) {
                                ImageRequest.Builder(context).data(artUri).size(Size(540, 540)).build()
                            }
                            AsyncImage(
                                model = blurRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(96.dp),
                            )
                        }
                    }
                }

                // One shared cover.
                if (coverAlpha > 0.01f) {
                    MorphSharedCover(
                        artUri = currentSong?.albumArtUri,
                        artSize = artSize,
                        artCx = artCx,
                        artCy = artCy,
                        artRadius = artRadius,
                        fadeStrength = e * (1f - qPeek),
                        alpha = coverAlpha,
                        density = density,
                        context = context,
                    )
                }

                // Mini row.
                if (miniAlpha > 0f) {
                    MorphMiniRow(
                        song = currentSong,
                        isPlaying = isPlaying,
                        alpha = miniAlpha,
                        onExpand = { scope.launch { expansion.animateTo(1f, SettleSpec) } },
                    )
                }

                // Full player controls (fade/translate out as the queue opens).
                if (fullAlpha > 0f) {
                    MorphFullControls(
                        song = currentSong,
                        isPlaying = isPlaying,
                        statusTop = statusTop,
                        navBottom = navBottom,
                        alpha = fullAlpha * (1f - qPeek),
                        contentShift = qPeek,
                        onClose = { scope.launch { expansion.animateTo(0f, SettleSpec) } },
                        onLyricsClick = onLyricsClick,
                        onQueueOpen = { scope.launch { queue.animateTo(1f, SettleSpec) } },
                    )
                }

                // Queue layer — slides up over the player.
                if (q > 0.001f) {
                    MorphQueueLayer(
                        current = currentSong,
                        statusTop = statusTop,
                        navBottom = navBottom,
                        queueTravelPx = queueTravelPx,
                        fractionProvider = { queue.value },
                        onClose = { scope.launch { queue.animateTo(0f, SettleSpec) } },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collapsed capsule glass (kyant backdrop blur, white frosted glass).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MorphCapsuleGlass(
    backdrop: LayerBackdrop?,
    width: Dp,
    bottomPadding: Dp,
    alpha: Float,
) {
    val shape = RoundedCornerShape(percent = 50)
    val surface = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(16.dp.toPx()) },
            onDrawSurface = {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.64f))
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
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8DC))),
            shape = shape,
        )
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomPadding)
            .width(width)
            .height(MorphMiniHeight)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .then(surface)
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.95f),
                    0.50f to Color.White.copy(alpha = 0.18f),
                    1f to Color.White.copy(alpha = 0.55f),
                ),
                shape = shape,
            )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  The one shared cover. Positioned by absolute offset + size so it grows
//  rather than cross-fades; its bottom edge dissolves (DstIn) into the blur.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MorphSharedCover(
    artUri: Uri?,
    artSize: Dp,
    artCx: Dp,
    artCy: Dp,
    artRadius: Dp,
    fadeStrength: Float,
    alpha: Float,
    density: Density,
    context: Context,
) {
    Box(
        modifier = Modifier
            .offset {
                val half = with(density) { artSize.toPx() } / 2f
                IntOffset(
                    (with(density) { artCx.toPx() } - half).roundToInt(),
                    (with(density) { artCy.toPx() } - half).roundToInt(),
                )
            }
            .size(artSize)
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(RoundedCornerShape(artRadius))
            .background(Color(0xFF2A2A2A))
            .drawWithContent {
                drawContent()
                if (fadeStrength > 0.01f) {
                    val fadeHeight = (size.height * 0.45f).coerceAtMost(140.dp.toPx()).coerceAtLeast(0f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = (size.height - fadeHeight).coerceAtLeast(0f),
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                        alpha = fadeStrength,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        artUri?.let { uri ->
            val request = remember(uri) {
                ImageRequest.Builder(context).data(uri).size(Size(1080, 1080)).build()
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

// ─────────────────────────────────────────────────────────────────────────────
//  Mini row — sits beside the thumbnail in the collapsed capsule.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MorphMiniRow(
    song: Song?,
    isPlaying: Boolean,
    alpha: Float,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onExpand() }
            .padding(start = 64.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: "",
                color = MorphInk,
                fontSize = 14.sp,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song?.artist ?: "",
                color = MorphInk.copy(alpha = 0.68f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        MorphMiniButton(
            iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = { PlaybackConnection.playPause() },
        )
        Spacer(Modifier.width(8.dp))
        MorphMiniButton(
            iconRes = R.drawable.ic_next,
            contentDescription = "Next",
            onClick = { PlaybackConnection.next() },
        )
    }
}

@Composable
private fun MorphMiniButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MorphInk.copy(alpha = 0.06f))
            .border(0.6.dp, MorphInk.copy(alpha = 0.10f), CircleShape)
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
//  Full player controls — top bar + bottom cluster (metadata, seek, transport).
//  The hero cover (the shared element above) fills the top; these controls sit
//  over the blurred lower half and translate + fade away as the queue rises.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MorphFullControls(
    song: Song?,
    isPlaying: Boolean,
    statusTop: Dp,
    navBottom: Dp,
    alpha: Float,
    contentShift: Float,
    onClose: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueOpen: () -> Unit,
) {
    val positionMs by PlaybackConnection.positionMs.collectAsState()
    val durationMs by PlaybackConnection.durationMs.collectAsState()
    val loopMode by PlaybackConnection.loopMode.collectAsState()
    val favourites by FavouriteStore.favourites.collectAsState()
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        // ── Top bar: collapse chevron / lyrics + queue ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 8.dp, start = 18.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphIconButton(
                iconRes = R.drawable.ic_chevron_up,
                contentDescription = "Collapse player",
                size = 22.dp,
                rotationDeg = 180f,
                onClick = onClose,
            )
            Spacer(Modifier.weight(1f))
            MorphIconButton(
                iconRes = R.drawable.ic_lyrics,
                contentDescription = "Lyrics",
                size = 22.dp,
                onClick = onLyricsClick,
            )
            Spacer(Modifier.width(6.dp))
            MorphIconButton(
                iconRes = R.drawable.ic_queue,
                contentDescription = "Queue",
                size = 22.dp,
                onClick = onQueueOpen,
            )
        }

        // ── Bottom cluster ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = contentShift * with(density) { 140.dp.toPx() } }
                .padding(horizontal = 24.dp)
                .padding(bottom = navBottom + 28.dp),
        ) {
            // Title / artist + favourite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "Nothing playing",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontFamily = BillieEilishFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song?.artist ?: "",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                val isFav = song != null && song.id in favourites
                MorphIconButton(
                    iconRes = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                    contentDescription = if (isFav) "Unfavourite" else "Favourite",
                    size = 24.dp,
                    tint = if (isFav) Color(0xFFFF6B6B) else Color.White,
                    onClick = { song?.let { FavouriteStore.toggle(it.id) } },
                )
            }

            Spacer(Modifier.height(20.dp))

            MorphSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { PlaybackConnection.seekTo(it) },
            )

            Spacer(Modifier.height(22.dp))

            // Transport
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MorphIconButton(
                        iconRes = R.drawable.ic_skip_back_10,
                        contentDescription = "Back 10 seconds",
                        size = 26.dp,
                        onClick = { PlaybackConnection.seekTo((positionMs - 10_000L).coerceAtLeast(0L)) },
                    )
                    Spacer(Modifier.width(18.dp))
                    MorphIconButton(
                        iconRes = R.drawable.ic_prev,
                        contentDescription = "Previous",
                        size = 30.dp,
                        onClick = { PlaybackConnection.prev() },
                    )
                    Spacer(Modifier.width(22.dp))
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
                            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.width(22.dp))
                    MorphIconButton(
                        iconRes = R.drawable.ic_next,
                        contentDescription = "Next",
                        size = 30.dp,
                        onClick = { PlaybackConnection.next() },
                    )
                    Spacer(Modifier.width(18.dp))
                    MorphIconButton(
                        iconRes = R.drawable.ic_skip_forward_10,
                        contentDescription = "Forward 10 seconds",
                        size = 26.dp,
                        onClick = {
                            PlaybackConnection.seekTo((positionMs + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L)))
                        },
                    )
                }
                val loopActive = loopMode != LoopMode.OFF
                MorphIconButton(
                    iconRes = if (loopMode == LoopMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                    contentDescription = when (loopMode) {
                        LoopMode.OFF -> "Loop off"
                        LoopMode.ALL -> "Loop queue"
                        LoopMode.ONE -> "Loop this song"
                    },
                    size = 22.dp,
                    tint = Color.White.copy(alpha = if (loopActive) 1f else 0.45f),
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = { PlaybackConnection.cycleLoopMode() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Seek bar — thin at rest, swells under the finger, white fill, time labels.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MorphSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }
    val trackHeight by animateDpAsState(
        targetValue = if (seeking) 6.dp else 3.dp,
        animationSpec = tween(180),
        label = "morphSeekTrack",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2f))
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
                    .clip(RoundedCornerShape(trackHeight / 2f))
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(if (seeking) (seekValue * safeDuration).roundToLong() else positionMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
            )
            Text(
                text = "-" + formatMs((safeDuration - (if (seeking) (seekValue * safeDuration).roundToLong() else positionMs)).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
            )
        }
    }
}

@Composable
private fun MorphIconButton(
    iconRes: Int,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
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
            modifier = Modifier.size(size).graphicsLayer { rotationZ = rotationDeg },
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60L
    val s = totalSec % 60L
    return "%d:%02d".format(m, s)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Queue layer — the "Up Next" list that slides up over the full player. Its
//  rise is driven by the same queue fraction the drag feeds, so letting go of a
//  half-open queue springs it the rest of the way (or back down) with momentum.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MorphQueueLayer(
    current: Song?,
    statusTop: Dp,
    navBottom: Dp,
    queueTravelPx: Float,
    fractionProvider: () -> Float,
    onClose: () -> Unit,
) {
    val queueSongs = remember(current) { PlaybackConnection.currentQueue }
    val currentIndex = remember(current) { PlaybackConnection.currentIndex }
    val listState = rememberLazyListState()

    // Keep the playing track in view whenever the queue is opened.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex.coerceAtMost(queueSongs.lastIndex.coerceAtLeast(0)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val f = fractionProvider().coerceIn(0f, 1f)
                translationY = (1f - f) * queueTravelPx
                alpha = (f / 0.3f).coerceIn(0f, 1f)
            }
            .background(Color(0xF2080808))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusTop + 14.dp, start = 24.dp, end = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Up Next",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = BillieEilishFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (queueSongs.isEmpty()) "Queue is empty"
                        else "${queueSongs.size} in queue",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontFamily = CalSansFamily,
                    )
                }
                MorphIconButton(
                    iconRes = R.drawable.ic_chevron_up,
                    contentDescription = "Close queue",
                    size = 22.dp,
                    rotationDeg = 180f,
                    onClick = onClose,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 32.dp,
                ),
            ) {
                itemsIndexed(queueSongs, key = { index, song -> "${index}_${song.id}" }) { index, song ->
                    MorphQueueItem(
                        song = song,
                        isCurrent = index == currentIndex,
                        onClick = { PlaybackConnection.playQueueItem(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MorphQueueItem(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isCurrent) Modifier.background(Color.White.copy(alpha = 0.07f)) else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            song.albumArtUri?.let { uri ->
                val request = remember(uri) {
                    ImageRequest.Builder(context).data(uri).size(Size(160, 160)).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                    .background(Color.White),
            )
        }
    }
}
