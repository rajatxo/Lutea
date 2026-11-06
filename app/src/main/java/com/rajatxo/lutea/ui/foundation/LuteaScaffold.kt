package com.rajatxo.lutea.ui.foundation

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab
import com.rajatxo.lutea.ui.util.consistentNavBottomPadding

/**
 * LuteaScaffold — ported from Coral X's CoralXScaffold.
 *
 * Universal Layout Laws followed:
 *  - Law 1: ONE CompositionLocal for ALL insets (LocalLuteaWindowInsets)
 *  - Law 2: Manual edge-to-edge (WindowCompat in MainActivity)
 *  - Law 3: Theme XML with enforceNavigationBarContrast=false
 *  - Law 7: BoxWithConstraints at root (NEVER screenHeightDp)
 *  - Law 8: Manifest flags already set
 *
 * Structure:
 *  - Content fills the WHOLE screen (no forced padding)
 *  - Header blur (120dp) sits ON TOP of content as a sibling
 *  - Header row: profile icon + title + settings icon
 *  - Bottom: tab capsule + search FAB
 *  - Consistent nav bar padding (max of system inset, 48dp)
 */
@Composable
fun LuteaScaffold(
    title: String,
    activeTab: LuteaTab,
    onTabSelected: (LuteaTab) -> Unit,
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNewPlaylistClick: () -> Unit = {},
    onPlaylistStyleClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val systemBottom = WindowInsets.navigationBars.getBottom(density)
    val minBottom = with(density) { 48.dp.toPx().toInt() }
    val effBottomDp = with(density) { maxOf(systemBottom, minBottom).toDp() }
    val effTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val insets = WindowInsets(
        top = effTopDp + 56.dp,
        bottom = effBottomDp + 80.dp
    )

    val graphicsLayer = rememberGraphicsLayer()
    val glassBackdrop = rememberLayerBackdrop(graphicsLayer = graphicsLayer) {
        drawContent()
    }

    CompositionLocalProvider(LocalLuteaWindowInsets provides insets) {
        // ★ Law 7: BoxWithConstraints at root (NEVER screenHeightDp).
        //    Works in multi-window, split-screen, floating windows, foldables.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            var menuOpen by remember { mutableStateOf(false) }

            // Content — fills the WHOLE screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(glassBackdrop)
            ) {
                content()
            }

            // ★ Blur overlay — fades in when the menu opens. Uniform blur via
            // kyant drawBackdrop, fading to clear before the centerline.
            val blurProgress by animateFloatAsState(
                targetValue = if (menuOpen) 1f else 0f,
                animationSpec = tween(durationMillis = if (menuOpen) 400 else 700),
                label = "menuBlur"
            )
            if (blurProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { RectangleShape },
                            effects = { blur(20.dp.toPx() * blurProgress) },
                            onDrawSurface = {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.25f to Color.Transparent,
                                            0.35f to Color.White,
                                            1.0f to Color.White
                                        )
                                    ),
                                    blendMode = BlendMode.DstOut
                                )
                            }
                        )
                )
            }

            // Black fade gradient at the very bottom — ALWAYS visible.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(effBottomDp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black,
                            )
                        )
                    )
            )

            // ★ Brick box — whitish frosted glass, rounded square shape covering
            // ~20% of screen height from the top (below status bar with a gap).
            // Bottom edge fades into the blur. X icon inside (top-right corner).
            // Shows when menu opens (fades/scales in with blurProgress).
            if (blurProgress > 0.01f) {
                val brickHeight = maxHeight * 0.20f
                val brickShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = effTopDp + 8.dp)
                        .height(brickHeight)
                        .graphicsLayer {
                            alpha = blurProgress
                            this.clip = true
                            this.shape = brickShape
                        }
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { brickShape },
                            effects = {
                                vibrancy()
                                blur(12.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.12f))
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { menuOpen = false }
                        )
                ) {
                    // X icon inside the brick, top-right corner
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { menuOpen = false }
                            )
                    )
                }
            }

            // ★ Menu button (top-right) — 40dp click target. Hollow circles at rest,
            // X icon when open. Click toggles menuOpen.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = maxWidth * 0.08f, top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { menuOpen = !menuOpen }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val xAlpha by animateFloatAsState(
                        targetValue = if (menuOpen) 1f else 0f,
                        animationSpec = tween(300),
                        label = "xAlpha"
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(alpha = xAlpha)
                    )
                }

                // 4 black glossy pills — 2×2 grid on the left when open.
                // New Playlist (top-left), Select Tag (top-right),
                // Favourite (bottom-left), Settings (bottom-right).
                // At rest: hollow circles (2×2 grid = menu icon). When open:
                // crossfade from hollow circle → black glossy pill with text.
                val circleData = remember {
                    listOf(
                        CircleAnim(9.dp, 9.dp, (-255).dp, 0.dp),
                        CircleAnim(24.dp, 9.dp, (-100).dp, 0.dp),
                        CircleAnim(9.dp, 24.dp, (-255).dp, 56.dp),
                        CircleAnim(24.dp, 24.dp, (-100).dp, 56.dp),
                    )
                }
                val labels = listOf("New Playlist", "Select Tag", "Favourite", "Settings")
                circleData.forEachIndexed { i, c ->
                    val progress by animateFloatAsState(
                        targetValue = if (menuOpen) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "circle$i"
                    )
                    val offsetX = c.restX + (c.targetX - c.restX) * progress
                    val offsetY = c.restY + (c.targetY - c.restY) * progress
                    // Hollow circle (rest state) — fades out as menu opens
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(8.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 1f - progress), CircleShape)
                    )
                    // Black glossy pill with text (open state) — wrapped in
                    // size(0.dp) so it doesn't expand the outer menu Box.
                    Box(
                        modifier = Modifier
                            .size(0.dp)
                            .offset(x = offsetX - 10.dp, y = offsetY - 10.dp)
                            .graphicsLayer { alpha = progress }
                    ) {
                        Box(
                            modifier = Modifier
                                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF2C2C2C),
                                            Color(0xFF181818),
                                        )
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = labels[i],
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = CalSansFamily,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Bottom: nav capsule + (Library only) a same-blur circle with the
            // sparkle-search icon beside it. Combined unit, centered. Per Law 7
            // the capsule self-sizes to 53% of maxWidth; the circle matches the
            // capsule height (48dp) with the same frosted blur. On entering
            // Library the circle fuses with the capsule then liquid-separates.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = effBottomDp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LuteaTabCapsule(
                    tabs = LuteaTab.entries.toList(),
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                    backdrop = glassBackdrop,
                )
                if (activeTab == LuteaTab.Library) {
                    // ★ Liquid separation: capsule + circle start FUSED (gap 0),
                    //    then animate apart (gap → 8dp) with a bouncy spring —
                    //    the reverse of a liquid fuse.
                    val separation = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        separation.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp * separation.value))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(elevation = 3.dp, shape = CircleShape, clip = true)
                            .drawBackdrop(
                                backdrop = glassBackdrop, shape = { CircleShape },
                                effects = { blur(12.dp.toPx()) },
                                onDrawSurface = {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.38f)
                                            )
                                        )
                                    )
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSearchClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_sparkle),
                            contentDescription = "Search",
                            tint = Color(0xFF1C1C1E),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class CircleAnim(val restX: Dp, val restY: Dp, val targetX: Dp, val targetY: Dp)
