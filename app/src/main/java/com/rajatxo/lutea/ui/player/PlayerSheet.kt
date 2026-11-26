package com.rajatxo.lutea.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.rajatxo.lutea.R
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.util.rememberCoverThemeColor
import kotlinx.coroutines.launch

/** Same dark ink the nav capsule uses on its white glass. */
private val MiniInk = Color(0xFF1C1C1E)

/** Height of the collapsed capsule. */
private val MiniHeight = 56.dp

data class PlayerNavigation(
    val onAlbumClick: () -> Unit = {},
    val onArtistClick: () -> Unit = {},
)

val LocalPlayerNavigation = staticCompositionLocalOf { PlayerNavigation() }

/**
 * The mini player and the full player are two independent surfaces.
 *
 * Mini: a fixed glass capsule. Swipe up (or tap) gives a small bounce and
 * opens the player; swipe down stops playback and removes the capsule.
 *
 * Player: a full-screen surface that only fades in/out. Swipe down (or tap
 * close) fades it out and brings the mini capsule back. Nothing morphs or
 * slides between the two — they are not the same element.
 */
@Composable
fun PlayerSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLyricsClick: () -> Unit = {},
    onQueueOpen: () -> Unit = {},
    /** 0 = player only, 1 = a lyrics/queue page is fully open. */
    pageProgress: Float = 0f,
    hidden: Boolean = false,
    backdrop: LayerBackdrop? = null,
    navMatchedWidth: Dp? = null,
    miniBottomPadding: Dp = 0.dp,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val isPlaying by PlaybackConnection.isPlaying.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(0f) }

    val coverUriStr = currentSong?.albumArtUri?.toString()
    val seamColor by rememberCoverThemeColor(
        coverUri = coverUriStr,
        fallbackSeed = currentSong?.id?.toString(),
    )

    // A tiny lift-and-settle on the capsule; the player itself just fades in.
    LaunchedEffect(expanded) {
        if (!expanded) bounce.snapTo(0f)
    }
    val openPlayer: () -> Unit = {
        if (!expanded) {
            scope.launch {
                bounce.animateTo(-10f, tween(90, easing = FastOutSlowInEasing))
                bounce.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                onExpandedChange(true)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxW = maxWidth

        // ── Mini player: its own tiny surface, never a seed for the player ──
        AnimatedVisibility(
            visible = currentSong != null && !expanded && !hidden,
            enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { it },
            // Fade quickly so it looks like it slips behind the nav bar.
            exit = fadeOut(tween(140)) + slideOutVertically(tween(240)) { it / 6 },
        ) {
            // AnimatedVisibility's own layout ignores ParentDataModifiers, so
            // the capsule needs a real Box to align in — without it the align
            // is dropped and the capsule sits at the top-left corner.
            Box(Modifier.fillMaxSize()) {
                MiniCapsule(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    title = currentSong?.title.orEmpty(),
                    artist = currentSong?.artist.orEmpty(),
                    artUri = currentSong?.albumArtUri,
                    playing = isPlaying,
                    width = navMatchedWidth ?: (maxW - 32.dp),
                    bottomPadding = miniBottomPadding,
                    bouncePx = { bounce.value },
                    backdrop = backdrop,
                    onOpen = openPlayer,
                    onDismiss = {
                        // Swipe down: kill the mini player and the music with it.
                        PlaybackConnection.stop()
                    },
                )
            }
        }

        // ── Full player: independent screen, fade-only transition ──
        AnimatedVisibility(
            visible = expanded && !hidden,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(240)),
            modifier = Modifier.fillMaxSize(),
        ) {
            FullPlayerScreen(
                artUri = currentSong?.albumArtUri,
                seamColor = seamColor,
                context = context,
                onClose = { onExpandedChange(false) },
                onLyricsClick = onLyricsClick,
                onQueueOpen = onQueueOpen,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini capsule
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniCapsule(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    artUri: android.net.Uri?,
    playing: Boolean,
    width: Dp,
    bottomPadding: Dp,
    bouncePx: () -> Float,
    backdrop: LayerBackdrop?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val glassSurface = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(16.dp.toPx()) },
            onDrawSurface = {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
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
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8DC))),
            shape = shape,
        )
    }

    Box(
        modifier = modifier
            .padding(bottom = bottomPadding)
            .width(width)
            .height(MiniHeight)
            .graphicsLayer { translationY = bouncePx() }
            .shadow(elevation = 6.dp, shape = shape, clip = true)
            .clip(shape)
            .then(glassSurface)
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.95f),
                    0.5f to Color.White.copy(alpha = 0.18f),
                    1f to Color.White.copy(alpha = 0.55f),
                ),
                shape = shape,
            )
            .pointerInput(Unit) {
                var up = 0f
                var down = 0f
                detectVerticalDragGestures(
                    onDragStart = { up = 0f; down = 0f },
                    onVerticalDrag = { _, dy ->
                        if (dy < 0f) up -= dy else down += dy
                    },
                    onDragEnd = {
                        when {
                            up > 24.dp.toPx() -> onOpen()
                            down > 24.dp.toPx() -> onDismiss()
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpen() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniCoverArt(artUri = artUri)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MiniInk,
                    fontSize = 14.sp,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    color = MiniInk.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            MiniButton(
                iconRes = if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = if (playing) "Pause" else "Play",
                onClick = { PlaybackConnection.playPause() },
            )
            Spacer(Modifier.width(8.dp))
            MiniButton(
                iconRes = R.drawable.ic_next,
                contentDescription = "Next",
                onClick = { PlaybackConnection.next() },
            )
        }
    }
}

@Composable
private fun MiniCoverArt(artUri: android.net.Uri?) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A))
            .border(0.8.dp, MiniInk.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        artUri?.let { uri ->
            val request = remember(uri) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(Size(160, 160))
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
private fun MiniButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MiniInk.copy(alpha = 0.06f))
            .border(0.6.dp, MiniInk.copy(alpha = 0.10f), CircleShape)
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
            tint = MiniInk,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Full player: its own screen, no shared geometry with the capsule
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullPlayerScreen(
    artUri: android.net.Uri?,
    seamColor: Color,
    context: android.content.Context,
    onClose: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueOpen: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(seamColor)
            .pointerInput(Unit) {
                var pulledDown = 0f
                detectVerticalDragGestures(
                    onDragStart = { pulledDown = 0f },
                    onVerticalDrag = { _, dy ->
                        pulledDown = if (dy > 0f) pulledDown + dy else 0f
                    },
                    onDragEnd = {
                        if (pulledDown > 80.dp.toPx()) onClose()
                    },
                )
            }
            // Swallow taps on the player surface so nothing underneath reacts.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        // Immersive backdrop: the same art, heavily blurred.
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

        // Fixed hero cover, flush with the top; its bottom melts into the blur.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .background(Color(0xFF2A2A2A))
                .drawWithContent {
                    drawContent()
                    val fadeHeight = (size.height * 0.45f)
                        .coerceAtMost(140.dp.toPx())
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

        // Top scrim + top bar (lyrics / close). Controls live in the host's
        // persistent PlayerControlBar at the bottom.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TopBarCircleButton(
                iconRes = R.drawable.ic_lyrics,
                contentDescription = "Lyrics",
                onClick = onLyricsClick,
            )
            TopBarCircleButton(
                iconRes = R.drawable.ic_close_circle,
                contentDescription = "Close player",
                iconSize = 20.dp,
                onClick = onClose,
            )
        }
        // The host's control bar owns the queue button; this callback is kept
        // in the signature so call sites stay unchanged.
    }
}

@Composable
private fun TopBarCircleButton(
    iconRes: Int,
    contentDescription: String,
    iconSize: Dp = 22.dp,
    tint: Color = Color.White,
    bgAlpha: Float = 0.08f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val btnSize = iconSize + 18.dp
    Box(
        modifier = modifier
            .size(btnSize)
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
            modifier = Modifier.size(iconSize),
        )
    }
}
