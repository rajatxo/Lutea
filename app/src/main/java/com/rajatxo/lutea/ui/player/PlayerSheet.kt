package com.rajatxo.lutea.ui.player

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.lerp as lerpFloat
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
import com.rajatxo.lutea.ui.components.BillieEilishFamily
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
    /** LIVE 0f→1f expand progress, hoisted from the host. The host owns this
     *  Animatable so the nav + control overlays can fade IN SYNC with the
     *  cover during the drag — not on separate boolean-driven tweens that only
     *  fire after the drag settles (the old "controls vanish last, nav appears
     *  last" lag). This composable still drives it (LaunchedEffect + drag). */
    expansion: Animatable<Float, *>,
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
    // expansion is HOISTED from the host (see param) so nav + controls can
    // sync to the live drag. The host seeds its initial value.

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
            // The immersive blurred background ("down blur") fades in as the
            // sheet opens. It sits BEHIND the dark surface; the morphing cover's
            // bottom DstIn fade melts the sharp cover into it.
            val blurAlpha = ((e - 0.25f) / 0.5f).coerceIn(0f, 1f)

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
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh,
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
                // A heavily-blurred copy of the cover filling the sheet. It is
                // the surface the morphing cover melts into: the cover's bottom
                // DstIn fade erases its own pixels, revealing THIS blur beneath.
                // Fades in with [blurAlpha] so it only appears as the player
                // opens (the capsule has no blur).
                if (blurAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = blurAlpha }
                            .background(seamColor)
                    ) {
                        currentSong?.albumArtUri?.let { artUri ->
                            val blurRequest = remember(artUri) {
                                ImageRequest.Builder(context)
                                    .data(artUri)
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
                )

                // ── The ONE shared title/artist ──
                // Never vanishes. It is the mini row's title (beside the cover,
                // 14sp, dark) AND the full player's title (below the cover, 22sp,
                // white) — one element that lerps position + size + colour with
                // the cover, so the text grows out of the mini spot as the cover
                // shrinks. Tapping it (in mini form) opens the player.
                SharedTitle(
                    title = currentSong?.title ?: "",
                    artist = currentSong?.artist ?: "",
                    artCx = artCx,
                    artCy = artCy,
                    artSize = artSize,
                    maxW = maxW,
                    eased = eased,
                    onMiniTap = { onExpandedChange(true) },
                )

                // ── Mini row: play/pause + next (mini-only, fade with miniAlpha) ──
                if (miniAlpha > 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = miniAlpha }
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
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
            // Offscreen compositing is REQUIRED for DstIn to erase the cover's
            // own pixels (otherwise it blends against whatever is behind the
            // layer, which has no effect on the cover).
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clip(RoundedCornerShape(artRadius))
            .background(Color(0xFF2A2A2A))
            // Bottom DstIn fade — the Spiral3 blend. As the cover grows into
            // the hero, its bottom edge dissolves into the immersive blur
            // beneath it. The fade height grows with the cover (capped at
            // 140dp like the original), and it only appears as the cover opens
            // (scaled by [eased]) so the mini thumbnail stays fully solid.
            .drawWithContent {
                drawContent()
                val fadeStrength = eased
                if (fadeStrength > 0.01f) {
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
                        alpha = fadeStrength,
                    )
                }
            }
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

/**
 * The ONE shared title/artist text — the same element in the mini player and
 * the full player. It NEVER vanishes: it lerps position + size + colour in
 * lockstep with the morphing cover.
 *
 *  - Mini state (e=0): beside the cover. Title 14sp dark [MiniInk], artist
 *    12sp dark-faded. Left edge at the cover's right + a gap; vertically
 *    centred on the cover's centre line.
 *  - Full state (e=1): below the cover. Title 22sp white, artist 16sp
 *    white-faded. Left edge at 24dp; top edge just below the cover.
 *
 * Tapping the title in mini form opens the player. In full form the tap is a
 * no-op (the cover/controls handle interaction).
 */
@Composable
private fun SharedTitle(
    title: String,
    artist: String,
    artCx: Dp,
    artCy: Dp,
    artSize: Dp,
    maxW: Dp,
    eased: Float,
    onMiniTap: () -> Unit,
) {
    val density = LocalDensity.current

    // ── Mini title anchor: beside the cover, centred on its vertical mid ──
    val miniLeft = artCx + artSize / 2f + 12.dp          // cover right edge + gap
    val miniCenterY = artCy                              // cover vertical centre
    val miniTitleSp = 14f
    val miniArtistSp = 12f

    // ── Full title anchor: below the cover, left-aligned at 24dp ──
    val fullLeft = 24.dp
    val fullTop = maxW + 16.dp                           // cover bottom + gap
    val fullTitleSp = 22f
    val fullArtistSp = 16f

    val left = lerp(miniLeft, fullLeft, eased)
    val top = lerp(miniCenterY - 12.dp, fullTop, eased)  // mini: shift up by ~half line
    val titleSp = lerpFloat(miniTitleSp, fullTitleSp, eased)
    val artistSp = lerpFloat(miniArtistSp, fullArtistSp, eased)

    // Colour: dark ink (mini) → white (full).
    val titleColor = lerpColor(MiniInk, Color.White, eased)
    val artistColor = lerpColor(MiniInk.copy(alpha = 0.68f), Color.White.copy(alpha = 0.68f), eased)

    // Tap-to-open only meaningful in mini form.
    val tapMod = if (eased < 0.5f) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onMiniTap,
        )
    } else Modifier

    Column(
        modifier = Modifier
            .offset {
                IntOffset(
                    with(density) { left.toPx() }.roundToInt(),
                    with(density) { top.toPx() }.roundToInt(),
                )
            }
            .then(tapMod),
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = titleSp.sp,
            fontFamily = BillieEilishFamily,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            color = artistColor,
            fontSize = artistSp.sp,
            fontFamily = BillieEilishFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
