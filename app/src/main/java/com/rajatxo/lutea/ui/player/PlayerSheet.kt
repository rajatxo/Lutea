package com.rajatxo.lutea.ui.player

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.lerp as lerpColor
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
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.util.rememberCoverThemeColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * PlayerSheet — ONE surface that IS the mini player collapsed and the full
 * player expanded. There is exactly one cover-art element: a single
 * [AsyncImage] that is the 40dp thumbnail in the capsule and grows — corner
 * radius and all — into the full-width square hero cover at the top of the
 * player. No second [PlayerCoverBlend], no full-screen blur, no offscreen
 * compositing on the transition's critical path. That is what makes the
 * expand/shrink read as "the mini player itself is growing", and what removes
 * the lag: the GPU only touches one image, and it is layout-driven (offset +
 * size), not a per-frame filter.
 *
 * @param miniBottomPadding distance from the screen bottom to the capsule's
 *   bottom edge (the host's nav-bar space). Animates to zero as it expands.
 */
@Composable
fun PlayerSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLyricsClick: () -> Unit = {},
    onQueueOpen: () -> Unit = {},
    /** 0 = player only, 1 = a lyrics/queue page is fully open. Drives the
     *  control-hide animation (Mochy controlsHiddenState). */
    pageProgress: Float = 0f,
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
    val expansion = remember { Animatable(if (expanded) 1f else 0f) }

    // Themed seam colour = the cover's own dominant colour (Palette, cached).
    // Used as the dark surface's tint so a cold-start cover load shows a themed
    // colour behind the blur instead of pure black (no black flash).
    val coverUriStr = currentSong?.albumArtUri?.toString()
    val seamColor by rememberCoverThemeColor(
        coverUri = coverUriStr,
        fallbackSeed = currentSong?.id?.toString(),
    )

    LaunchedEffect(expanded) {
        // Critically-damped spring (dampingRatio = 1 → no overshoot/bounce),
        // high stiffness → opens FAST and settles without sluggishness. A
        // tween here ignored the finger's momentum on a tap-drag and always
        // ran the full fixed duration, which read as slow/lagging. The spring
        // starts from whatever velocity the drag left behind.
        expansion.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh,
            ),
        )
    }

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
            // NO smoothstep here. A smoothstep (e*e*(3-2e)) double-eases on top
            // of the animation spec, so during a drag the cover would barely
            // move at low expansion while your finger moved a lot — that
            // sticky, "lagging", heavy feel. Linear (eased = e) makes the
            // geometry track the finger 1:1; the spring spec does all the
            // easing on release. This is the single biggest smoothness win.
            val eased = e

            val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

            val capsuleW = navMatchedWidth ?: (maxW - 32.dp)
            val sheetW = lerp(capsuleW, maxW, eased)
            val sheetH = lerp(MiniHeight, maxH, eased)
            val sheetBottom = lerp(miniBottomPadding, 0.dp, eased)
            val sheetShape = RoundedCornerShape(lerp(MiniHeight / 2f, 0.dp, eased))

            // The capsule glass fades out fast; the dark player surface fades
            // in behind it. The mini row and the player body cross-fade.
            val glassAlpha = (1f - e * 4f).coerceIn(0f, 1f)
            val darkAlpha = (e * 2.2f).coerceIn(0f, 1f)
            val miniAlpha = (1f - e * 3.5f).coerceIn(0f, 1f)
            val fullAlpha = ((e - 0.55f) / 0.35f).coerceIn(0f, 1f)

            // ── The ONE shared cover ──
            // Mini state: 40dp thumbnail, 12dp in from the capsule's left,
            // vertically centred in the 56dp capsule, 8dp corner radius.
            // Full state: full-width square, top edge flush with the sheet's
            // top (which is the screen top once expanded), 0dp corner radius.
            // Size, position and corner radius all lerp together, so the
            // thumbnail literally grows into the hero cover — one element.
            val miniArtSize = 40.dp
            val miniArtInset = 12.dp
            val miniArtCy = MiniHeight / 2f
            val miniArtRadius = 8.dp

            val heroArtSize = maxW
            val heroArtCy = maxW / 2f
            val heroArtRadius = 0.dp

            val artSize = lerp(miniArtSize, heroArtSize, eased)
            val artCx = lerp(miniArtInset + miniArtSize / 2f, maxW / 2f, eased)
            val artCy = lerp(miniArtCy, heroArtCy, eased)
            val artRadius = lerp(miniArtRadius, heroArtRadius, eased)

            val travelPx = with(density) {
                (maxH - MiniHeight - miniBottomPadding).toPx()
            }.coerceAtLeast(1f)

            val settle: (Float) -> Unit = { velocity ->
                val target = when {
                    velocity < -1200f -> 1f
                    velocity > 1200f -> 0f
                    expansion.value > 0.4f -> 1f
                    else -> 0f
                }
                // Convert the flick's pixel velocity (screen-down positive) into
                // expansion-axis velocity (swipe up = negative px = positive
                // expansion). Feeding this as the spring's initial velocity is
                // what makes a fast upward flick WHOOSH open instead of getting
                // dampened to a slow ease — the gesture's momentum carries
                // straight through into the settle.
                val initialVel = -velocity / travelPx
                scope.launch {
                    expansion.animateTo(
                        target,
                        // Softer than the tap-open spring: a flick-driven
                        // settle on StiffnessMedium glides longer and carries
                        // the gesture's momentum further, which reads as
                        // "buttery" rather than a hard snap. Still critically
                        // damped (no bounce), so it never overshoots.
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        initialVelocity = initialVel,
                    )
                }
                if ((target == 1f) != expanded) onExpandedChange(target == 1f)
            }

            val dragState = rememberDraggableState { dy ->
                scope.launch {
                    expansion.snapTo((expansion.value - dy / travelPx).coerceIn(0f, 1f))
                }
            }

            // ── Collapsed capsule glass, pinned where the mini player lives ──
            if (glassAlpha > 0f) {
                val capsuleShape = RoundedCornerShape(percent = 50)
                val glassSurface = if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { capsuleShape },
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
                        brush = Brush.verticalGradient(
                            listOf(Color.White, Color(0xFFD8D8DC))
                        ),
                        shape = capsuleShape,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = miniBottomPadding)
                        .width(capsuleW)
                        .height(MiniHeight)
                        .graphicsLayer { alpha = glassAlpha }
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
                    // Themed seam colour is the sheet's base surface (fades in
                    // with [darkAlpha]). The immersive blur sits on top of it,
                    // and the morphing cover sits on top of the blur — so the
                    // cover's DstIn-erased bottom reveals the blur, which in
                    // turn sits over the seam colour. No pure black anywhere.
                    .background(seamColor.copy(alpha = darkAlpha))
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    // While open, swallow taps on the empty player surface so
                    // they cannot reach the screen underneath.
                    .then(
                        if (expanded) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {} else Modifier
                    )
            ) {
                // ── Immersive blurred background ("down blur") ──
                // The surface the morphing cover melts into. CRITICAL FOR
                // SMOOTHNESS: this mounts via AnimatedVisibility on the SETTLED
                // [expanded] state, NOT on the live [expansion.value]. Reading
                // a per-frame alpha here would re-evaluate the blur every drag
                // frame — and Modifier.blur() is a SOFTWARE path on API 29/30
                // (no RenderEffect until 31), so a per-frame software blur on a
                // full-sheet image is the single biggest jank source. By tying
                // it to [expanded], the blur layer mounts ONCE when the player
                // settles open and the GPU caches it; during the drag the seam
                // colour sweeps up cheaply and the blur resolves after settle.
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(160)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(seamColor)
                    ) {
                        currentSong?.albumArtUri?.let { artUri ->
                            val blurRequest = remember(artUri) {
                                ImageRequest.Builder(context)
                                    .data(artUri)
                                    .size(Size(360, 360))
                                    .build()
                            }
                            AsyncImage(
                                model = blurRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(72.dp),
                            )
                        }
                    }
                }

                // ── The single shared cover art ──
                // One AsyncImage. It is the mini thumbnail (40dp, 8dp radius,
                // capsule-left) and the full hero cover (full-width square,
                // flush to the sheet's top, square corners) — size, position
                // and corner radius lerp together so it grows, not crossfades.
                // Its bottom edge dissolves into the blur above via DstIn.
                SharedCoverArt(
                    artUri = currentSong?.albumArtUri,
                    artSize = artSize,
                    artCx = artCx,
                    artCy = artCy,
                    artRadius = artRadius,
                    density = density,
                    context = context,
                    eased = eased,
                    // The bottom melt (DstIn) only renders when the cover is
                    // SETTLED open. During the drag the cover is a plain clip —
                    // no offscreen compositing buffer, no per-frame DstIn. The
                    // melt is imperceptible while the cover is flying, so gating
                    // it removes the heaviest per-frame GPU cost for free.
                    meltActive = expanded && e > 0.985f,
                )                // ── Mini row: title/artist + play/pause + next ──
                if (miniAlpha > 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = miniAlpha }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onExpandedChange(true) }
                            .padding(start = 64.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
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

                        MiniButton(
                            iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
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

                // ── Expanded player body ──
                if (fullAlpha > 0f) {
                    ExpandedPlayerBody(
                        alpha = fullAlpha,
                        onClose = { onExpandedChange(false) },
                        onLyricsClick = onLyricsClick,
                        onQueueOpen = onQueueOpen,
                        pageProgress = pageProgress,
                    )
                }
            }
        }
    }
}

/**
 * The single shared cover. One [AsyncImage], fixed 1080 decode size (so the
 * growing drag never triggers a re-decode per frame), positioned by absolute
 * offset within the sheet and clipped to a lerped corner radius. The border
 * tints from the mini ink to white as it grows.
 *
 * @param meltActive only when true (cover SETTLED open) does the bottom DstIn
 *   melt render. Offscreen compositing + DstIn allocate a fresh GPU buffer
 *   every frame, so they are gated off the drag's critical path entirely.
 */
@Composable
private fun SharedCoverArt(
    artUri: Uri?,
    artSize: Dp,
    artCx: Dp,
    artCy: Dp,
    artRadius: Dp,
    density: Density,
    context: Context,
    eased: Float,
    meltActive: Boolean,
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
            .clip(RoundedCornerShape(artRadius))
            .background(Color(0xFF2A2A2A))
            // Bottom DstIn melt — the Spiral3 blend — ONLY when settled open.
            // Offscreen compositing is required for DstIn to erase the cover's
            // own pixels, but it allocates an offscreen buffer per frame; gating
            // it to [meltActive] keeps the drag at zero offscreen cost. The melt
            // is imperceptible while the cover is moving, so this is a free win.
            .then(
                if (meltActive) {
                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                } else {
                    Modifier
                }
            )
            .then(
                if (meltActive) {
                    Modifier.drawWithContent {
                        drawContent()
                        val maxFadePx = 140.dp.toPx()
                        val fadeHeight = (size.height * 0.45f)
                            .coerceAtMost(maxFadePx)
                            .coerceAtLeast(0f)
                        val fadeStartY = (size.height - fadeHeight).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = fadeStartY,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                } else {
                    Modifier
                }
            )
            .border(
                width = 0.8.dp,
                color = lerpColor(
                    MiniInk.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.10f),
                    eased,
                ).copy(alpha = (1f - eased).coerceIn(0f, 1f)),
                shape = RoundedCornerShape(artRadius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        artUri?.let { uri ->
            // Fixed decode size: the art grows during the expand drag, so a
            // layout-sized request would restart on every frame and settle on
            // a small decode. 1080 keeps the real metadata art crisp without
            // a huge bitmap.
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

@Composable
private fun ExpandedPlayerBody(
    alpha: Float,
    onClose: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueOpen: () -> Unit,
    pageProgress: Float,
) {
    // The expanded player is a LIGHTWEIGHT surface: the themed seam colour
    // (already painted by the morphing sheet's background) + the immersive
    // blurred cover behind it. The title/artist + control cluster are drawn by
    // the host's persistent [PlayerControlBar] overlay (so they stay on the
    // lyrics/queue pages too). Here we only need the top bar (lyrics + close).
    // The sharp cover is the single shared element above — no second
    // PlayerCoverBlend.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
    ) {
        // A subtle scrim behind the top bar so the round buttons stay legible
        // over any cover.
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

        // ── Top bar: lyrics button (left) + close (right) ──
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
    }
}

/** A small round icon button for the expanded player's top bar. */
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
