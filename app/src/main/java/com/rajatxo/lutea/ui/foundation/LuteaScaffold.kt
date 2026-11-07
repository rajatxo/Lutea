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

            // ★ Blur overlay — full mechanism preserved but INTENTIONALLY
            // DISCONNECTED from the menu icon for now (will be re-wired to a
            // different trigger later). blurProgress stays at 0 so opening the
            // menu no longer blurs the background. The drawBackdrop / kyant
            // pipeline below remains intact for the future re-wire.
            val blurProgress by animateFloatAsState(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 700),
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
                                // Punch out the blur below ~45% so it fades COMPLETELY
                                // before the screen's horizontal center — the original
                                // (unblurred) content shows through below the fade.
                                // DstOut: keep blur where gradient is transparent (top),
                                // remove it where gradient is opaque (below 45%).
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

            // Black fade gradient at the very bottom — ALWAYS visible (permanent).
            // Layer order: content → this gradient → menu/nav (on top of everything).
            // Fades from transparent (above the nav bar) to black (very bottom).
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

            // ★ Menu button (top-right) — 4 hollow circles in a 2×2 grid at rest,
            //    crossfading to an X icon when the menu opens. The 4 menu PILLS
            //    no longer live here — they now appear in their own row below the
            //    screen's top title (see pills row further down). Clicking the
            //    40dp target toggles menuOpen.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = maxWidth * 0.08f, top = 8.dp)
            ) {
                // Click target (40dp) + X icon (fades in when open, out when closed)
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

                // 4 hollow circles in 2×2 grid (the menu icon). When the menu
                // opens they simply fade out in place — no scatter anymore.
                val circlePos = remember {
                    listOf(
                        9.dp to 9.dp,
                        24.dp to 9.dp,
                        9.dp to 24.dp,
                        24.dp to 24.dp,
                    )
                }
                circlePos.forEachIndexed { i, (cx, cy) ->
                    val progress by animateFloatAsState(
                        targetValue = if (menuOpen) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "circle$i"
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = cx, y = cy)
                            .size(8.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 1f - progress), CircleShape)
                    )
                }
            }

            // ★ Menu pills row — 4 white/grey gradient pills SIDE BY SIDE in a
            // single horizontal row. Each pill is weighted equally so the row
            // fits any screen width. Positioned JUST BELOW the screen's big top
            // text: the wheel screen's two-line title "Rotate the Playlist wheel"
            // (28sp × 2 lines) sits at statusBars + 16dp top padding and ends at
            // roughly statusBars + 84dp; pills at statusBars + 92dp leave an 8dp
            // gap below the title. Other tabs' titles sit slightly lower, so the
            // pills never overlap. Pills fade in with a bouncy spring on menu
            // open.
            if (menuOpen) {
                val pillProgress by animateFloatAsState(
                    targetValue = if (menuOpen) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "pills"
                )
                val labels = listOf("New Playlist", "Select Tag", "Favourite", "Settings")
                // 4 slightly different white/grey vertical gradients — "random
                // gradient" feel so each pill reads distinct instead of uniform.
                val gradients = listOf(
                    listOf(Color.White.copy(alpha = 0.95f), Color(0xFFB0B0B0).copy(alpha = 0.85f)),
                    listOf(Color(0xFFD8D8D8).copy(alpha = 0.92f), Color.White.copy(alpha = 0.78f)),
                    listOf(Color.White.copy(alpha = 0.82f), Color(0xFF9A9A9A).copy(alpha = 0.72f)),
                    listOf(Color(0xFFC8C8C8).copy(alpha = 0.88f), Color(0xFFE8E8E8).copy(alpha = 0.78f)),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 92.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .graphicsLayer { alpha = pillProgress },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    labels.forEachIndexed { i, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(brush = Brush.verticalGradient(colors = gradients[i]))
                                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(17.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color(0xFF1C1C1E),
                                fontSize = 11.sp,
                                fontFamily = CalSansFamily,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
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

