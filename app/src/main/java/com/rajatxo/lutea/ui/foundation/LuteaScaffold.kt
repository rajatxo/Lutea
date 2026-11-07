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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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

            // ★ Morph animation — one bouncy spring drives the whole menu-open
            // morph. It is read by BOTH the hollow circles in the menu button
            // (fade out) AND the morphing pills overlay below (fade in + translate
            // + scale from circle position to pill row position). StiffnessLow
            // gives a slower, more visible morph (~0.7s settle) so the user
            // clearly sees the circles morphing into pills + the gooey fading
            // out as they separate. MediumBouncy damping for the satisfying
            // overshoot "snap" matching the reference video.
            val morphProgress by animateFloatAsState(
                targetValue = if (menuOpen) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "morph"
            )

            // ★ Pre-computed morph geometry. The 4 pills morph FROM the 4 hollow
            // circles in the menu button (2×2 grid, 8dp circles spaced 15dp apart)
            // TO the 4 pills in a row below the top title. We compute every pill's
            // final row position and its corresponding circle's position, then the
            // per-pill delta from one to the other. graphicsLayer applies that delta
            // × (1 - morphProgress) as translation, so at progress=0 each pill sits
            // exactly on its circle, and at progress=1 it sits at its row slot.
            val density = LocalDensity.current
            val pillHeightDp = 34.dp
            val pillGapDp = 8.dp
            val sidePadDp = 16.dp
            val pillWidthDp = (maxWidth - sidePadDp * 2 - pillGapDp * 3) / 4
            val pillWidthPx = with(density) { pillWidthDp.toPx() }
            val pillHeightPx = with(density) { pillHeightDp.toPx() }
            val pillGapPx = with(density) { pillGapDp.toPx() }
            val sidePadPx = with(density) { sidePadDp.toPx() }
            val circleSizePx = with(density) { 8.dp.toPx() }
            val menuBtnSizePx = with(density) { 40.dp.toPx() }
            // Menu button box position (align TopEnd + statusBarsPadding +
            // padding(end = maxWidth*0.08, top = 8.dp))
            val menuBtnRightPx = with(density) { (maxWidth - maxWidth * 0.08f).toPx() }
            val menuBtnTopPx = with(density) { (effTopDp + 8.dp).toPx() }
            val menuBtnLeftPx = menuBtnRightPx - menuBtnSizePx
            // 4 circle centers in the menu button's 2×2 grid (offsets 9,9 / 24,9 /
            // 9,24 / 24,24 within the 40dp target; circle is 8dp so center at +4)
            val circleCentersPx = remember(menuBtnLeftPx, menuBtnTopPx, density) {
                listOf(
                    Offset(menuBtnLeftPx + with(density) { 13.dp.toPx() }, menuBtnTopPx + with(density) { 13.dp.toPx() }),
                    Offset(menuBtnLeftPx + with(density) { 28.dp.toPx() }, menuBtnTopPx + with(density) { 13.dp.toPx() }),
                    Offset(menuBtnLeftPx + with(density) { 13.dp.toPx() }, menuBtnTopPx + with(density) { 28.dp.toPx() }),
                    Offset(menuBtnLeftPx + with(density) { 28.dp.toPx() }, menuBtnTopPx + with(density) { 28.dp.toPx() }),
                )
            }
            // Pill row Y center (statusBarsPadding + padding(top=92) + half height)
            val pillRowTopPx = with(density) { (effTopDp + 92.dp).toPx() }
            val pillRowCenterYPx = pillRowTopPx + pillHeightPx / 2
            // 4 pill centers in the row (sidePad + i×(pillWidth+gap) + pillWidth/2)
            val pillCentersPx = remember(sidePadPx, pillWidthPx, pillGapPx, pillRowCenterYPx) {
                (0..3).map { i ->
                    Offset(sidePadPx + i * (pillWidthPx + pillGapPx) + pillWidthPx / 2, pillRowCenterYPx)
                }
            }
            // Per-pill delta from row position to circle position (px)
            val pillToCircleDeltasPx = remember(circleCentersPx, pillCentersPx) {
                (0..3).map { i ->
                    Offset(
                        circleCentersPx[i].x - pillCentersPx[i].x,
                        circleCentersPx[i].y - pillCentersPx[i].y,
                    )
                }
            }

            // ★ Menu button (top-right) — 40dp click target + X icon (fades in when
            //    open) + 4 hollow circles in 2×2 grid (the menu icon at rest).
            //    The circles now use morphProgress (shared with the pills overlay)
            //    so their fade-out stays perfectly in sync with the pills morphing
            //    in from these exact positions.
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

                // 4 hollow circles in 2×2 grid (the menu icon). Fade out via the
                // shared morphProgress as the pills morph in FROM these positions.
                val circlePos = remember {
                    listOf(
                        9.dp to 9.dp,
                        24.dp to 9.dp,
                        9.dp to 24.dp,
                        24.dp to 24.dp,
                    )
                }
                circlePos.forEachIndexed { i, (cx, cy) ->
                    Box(
                        modifier = Modifier
                            .offset(x = cx, y = cy)
                            .size(8.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 1f - morphProgress), CircleShape)
                    )
                }
            }

            // ★ Precomputed gooey RenderEffects at 15 blur levels (0..14) — ported
            // from gooey-react's SVG filter (https://github.com/luukdv/gooey-react).
            // Each is a chain of Gaussian blur → ColorMatrix threshold. We use a
            // SOFTER threshold than the original (multiplier=18, shift=-8 →
            // threshold at alpha 0.44) because Android's RenderEffect blur is a
            // box blur, not a true Gaussian — the original ×72 / -36 values
            // produce jagged binary edges on Android. Softer threshold = smooth
            // anti-aliased edges while still forming the metaball bridge.
            //
            // Why 15 levels? The gooey strength must FADE as the pills separate,
            // otherwise the blur reaches across the 8dp gap and keeps them merged
            // even at morphProgress=1 ("just connected together"). At level 14
            // (strongest) the gooey is deep — pills fuse as one blob. At level 0
            // there's no effect — clean separate pills. graphicsLayer picks the
            // level from (1 - morphProgress) × 14, so the gooey smoothly fades
            // out as the pills separate — that's the "neck stretching then
            // snapping" effect. API 31+ only; empty list on older devices.
            val gooeyEffects = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (0..14).map { level ->
                        if (level == 0) null
                        else {
                            val blurRadius = level.toFloat()
                            val blur = android.graphics.RenderEffect.createBlurEffect(
                                blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
                            )
                            val matrix = android.graphics.ColorMatrix(floatArrayOf(
                                1f, 0f, 0f, 0f, 0f,
                                0f, 1f, 0f, 0f, 0f,
                                0f, 0f, 1f, 0f, 0f,
                                0f, 0f, 0f, 18f, -8f,
                            ))
                            val threshold = android.graphics.RenderEffect.createColorFilterEffect(
                                android.graphics.ColorMatrixColorFilter(matrix)
                            )
                            android.graphics.RenderEffect.createChainEffect(threshold, blur)
                                .asComposeRenderEffect()
                        }
                    }
                } else emptyList()
            }

            // ★ Morphing pills overlay — 4 white/grey gradient pills that morph
            // FROM the 4 hollow circles at the menu button (rest, fused as one
            // blob via the gooey filter) TO 4 separate pills in a horizontal row
            // below the top title (open, separated cleanly once the gap exceeds
            // the blur radius). The liquid "neck stretching then snapping" between
            // adjacent pills during the morph IS the gooey metaball effect.
            //
            // Two layers:
            //  • Shape layer — 4 rounded-rect gradients with the gooey RenderEffect
            //    applied. Blurred+thresholded as a group so adjacent shapes merge
            //    when close. (Border is omitted — the threshold would eat it.)
            //  • Text layer — 4 labels at the same morphed positions, NO gooey so
            //    text stays crisp. Fades in over the last 60% of the morph.
            if (morphProgress > 0.01f) {
                val labels = listOf("New Playlist", "Select Tag", "Favourite", "Settings")
                // 4 slightly different white/grey vertical gradients — "random
                // gradient" feel so each pill reads distinct.
                val gradients = listOf(
                    listOf(Color.White.copy(alpha = 0.95f), Color(0xFFB0B0B0).copy(alpha = 0.85f)),
                    listOf(Color(0xFFD8D8D8).copy(alpha = 0.92f), Color.White.copy(alpha = 0.78f)),
                    listOf(Color.White.copy(alpha = 0.82f), Color(0xFF9A9A9A).copy(alpha = 0.72f)),
                    listOf(Color(0xFFC8C8C8).copy(alpha = 0.88f), Color(0xFFE8E8E8).copy(alpha = 0.78f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                ) {
                    // ── Shape layer (with gooey) ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Gooey strength fades as morphProgress → 1: level 14
                                // (strongest) at progress=0, level 0 (no effect) at
                                // progress=1. This is what makes the pills actually
                                // SEPARATE at the end — without this fade, the blur
                                // would keep reaching across the 8dp gap and the
                                // pills would stay visually merged ("just connected
                                // together"). The fade-out of the gooey IS the
                                // "neck snapping" moment.
                                if (gooeyEffects.isNotEmpty()) {
                                    val level = ((1f - morphProgress) * 14f)
                                        .roundToInt().coerceIn(0, 14)
                                    renderEffect = gooeyEffects[level]
                                }
                            }
                    ) {
                        labels.forEachIndexed { i, _ ->
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (pillCentersPx[i].x - pillWidthPx / 2).roundToInt(),
                                            (pillCentersPx[i].y - pillHeightPx / 2).roundToInt(),
                                        )
                                    }
                                    .size(width = pillWidthDp, height = pillHeightDp)
                                    .graphicsLayer {
                                        // Translate from circle position (morphProgress=0)
                                        // to pill row position (morphProgress=1)
                                        translationX = pillToCircleDeltasPx[i].x * (1f - morphProgress)
                                        translationY = pillToCircleDeltasPx[i].y * (1f - morphProgress)
                                        // Scale from 8dp circle (morphProgress=0) to
                                        // full pill size (morphProgress=1)
                                        val startScaleX = circleSizePx / pillWidthPx
                                        val startScaleY = circleSizePx / pillHeightPx
                                        scaleX = startScaleX + (1f - startScaleX) * morphProgress
                                        scaleY = startScaleY + (1f - startScaleY) * morphProgress
                                        alpha = morphProgress
                                    }
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(brush = Brush.verticalGradient(colors = gradients[i]))
                            )
                        }
                    }

                    // ── Text layer (no gooey — sharp labels on top) ──
                    labels.forEachIndexed { i, label ->
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (pillCentersPx[i].x - pillWidthPx / 2).roundToInt(),
                                        (pillCentersPx[i].y - pillHeightPx / 2).roundToInt(),
                                    )
                                }
                                .size(width = pillWidthDp, height = pillHeightDp)
                                .graphicsLayer {
                                    // Same morph as the shape layer (translation + scale)
                                    translationX = pillToCircleDeltasPx[i].x * (1f - morphProgress)
                                    translationY = pillToCircleDeltasPx[i].y * (1f - morphProgress)
                                    val startScaleX = circleSizePx / pillWidthPx
                                    val startScaleY = circleSizePx / pillHeightPx
                                    scaleX = startScaleX + (1f - startScaleX) * morphProgress
                                    scaleY = startScaleY + (1f - startScaleY) * morphProgress
                                    // Text fades in over the last 60% of the morph so
                                    // the shape change reads first, then labels appear
                                    alpha = ((morphProgress - 0.4f) / 0.6f).coerceIn(0f, 1f)
                                },
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

