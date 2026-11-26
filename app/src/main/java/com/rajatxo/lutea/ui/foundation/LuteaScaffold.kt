package com.rajatxo.lutea.ui.foundation

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.platform.LocalContext
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
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import com.rajatxo.lutea.ui.util.consistentNavBottomPadding

val LocalSearchTrigger = staticCompositionLocalOf<() -> Unit> {
    {}
}

val LocalSearchOpen = staticCompositionLocalOf< androidx.compose.runtime.State<Boolean>> {
    androidx.compose.runtime.mutableStateOf(false)
}

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

    // The search page pins its machine to the keyboard; while the keyboard is
    // up the mini player would just sit behind it, so it gets out of the way.
    val imeInsets = WindowInsets.ime
    val imeOpen by remember {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val searchOpenState = remember { mutableStateOf(false) }
    var playerVisible by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    // Page-open progress drives the player's control-hide animation for BOTH
    // the lyrics and queue pages (Mochy's controlsHiddenState). 1 = a page is
    // fully open, 0 = player-only.
    val searchHistoryOverlay = remember { SearchHistoryOverlayState() }
    val context = LocalContext.current
    val searchSoundOverlay = remember(context) {
        SearchSoundOverlayState(
            initialTypingSoundEnabled = loadTypingSoundEnabled(context),
            initialTypingSoundVolume = loadTypingSoundVolume(context),
        )
    }
    val currentSong by PlaybackConnection.currentSong.collectAsState()

    // Leaving the Search tab always closes the sound menu, so it cannot
    // reappear unexpectedly when the user comes back.
    LaunchedEffect(activeTab) {
        if (activeTab != LuteaTab.Search) searchSoundOverlay.expanded = false
    }

    // Clear All sits above the nav (48dp + 8dp gap). When the mini player is
    // visible it lifts another 64dp to stay 8dp above the mini player.
    val clearAllBottom by animateDpAsState(
        targetValue = effBottomDp + if (currentSong != null && !imeOpen) 120.dp else 56.dp,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "searchClearAllBottom",
    )

    // The long-press sound card floats above the nav, and above the mini
    // player when one is showing.
    val searchSoundCardBottom =
        effBottomDp + if (currentSong != null && !imeOpen) 132.dp else 70.dp

    // The clear-all capsule appears once the simple history is scrolled.
    val historyClearVisible = searchHistoryOverlay.simpleClearVisible

    CompositionLocalProvider(
        LocalLuteaWindowInsets provides insets,
        LocalSearchTrigger provides {
            searchOpen = true
            searchOpenState.value = true
            menuOpen = false
        },
        LocalSearchOpen provides searchOpenState,
        LocalSearchHistoryOverlay provides searchHistoryOverlay,
        LocalSearchSoundOverlay provides searchSoundOverlay,
    ) {
        // ★ Law 7: BoxWithConstraints at root (NEVER screenHeightDp).
        //    Works in multi-window, split-screen, floating windows, foldables.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ★ BackHandler — the search sound menu closes first, then the
            //    search overlay, then the Library menu.
            BackHandler(
                enabled = searchSoundOverlay.expanded || searchOpen || menuOpen,
            ) {
                if (searchSoundOverlay.expanded) searchSoundOverlay.expanded = false
                else if (searchOpen) { searchOpen = false; searchOpenState.value = false }
                else if (menuOpen) menuOpen = false
            }

            // ★ Back with the player stack open: the queue closes and the
            //    player collapses inside MorphPlayerSheet, which owns those
            //    gestures. The lyrics handler is registered below the sheet so
            //    it wins while lyrics is open.

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

            // Black scrim behind the nav. It starts completely transparent at
            // the mini-player's bottom edge, darkens gradually, and reaches pure
            // black at the very bottom of the screen. This keeps the nav readable
            // without putting a hard edge under the glass capsule.
            //
            // The top edge MUST blend into the page behind it (especially the
            // Effect page's warm-black gradient), so the first third of the band
            // is held transparent and the second third eases in very gently.
            // The previous ramp (0.08 → 0.06) hit black too quickly and showed
            // a hard line where the fade began.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(effBottomDp + 56.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Transparent,
                                0.18f to Color.Transparent,
                                0.30f to Color.Black.copy(alpha = 0.03f),
                                0.42f to Color.Black.copy(alpha = 0.10f),
                                0.54f to Color.Black.copy(alpha = 0.24f),
                                0.64f to Color.Black.copy(alpha = 0.42f),
                                0.74f to Color.Black.copy(alpha = 0.62f),
                                0.83f to Color.Black.copy(alpha = 0.80f),
                                0.91f to Color.Black.copy(alpha = 0.93f),
                                0.97f to Color.Black.copy(alpha = 0.985f),
                                1.00f to Color.Black,
                            )
                        )
                    )
            )

            // ★ Gooey metaball menu: 4 hollow dots → 4 fused capsules → liquid separation (and back)
            // ONLY on the Library tab — menu button + pills don't show on Home/Search tabs.
            // Ported from gooey-react's SVG filter (blur + alpha threshold ColorMatrix).
            // Two sequential phases:
            //   Phase 1 (FAST, 150ms tween): dots morph into 4 capsules at the row position, gap=0 (FUSED)
            //   Phase 2 (SLOW, ~750ms bouncy spring): fused capsules separate, gooey neck stretches then snaps.
            if (activeTab == LuteaTab.Library) {

            // ── Tunables ────────────────────────────────────────────────────────────────
            val gooeyBlurSigmaDp = 4.dp       // blur σ (= 12px at 3x). Neck snaps at gap ≈ 1.4σ ≈ 5.5dp; 8dp end gap stays clean
            val gooeyAlphaMultiplier = 18f    // soft threshold: alpha' = 18·a − 8  (cut-off at a ≈ 0.44)
            val gooeyAlphaShift = -8f         // in 0..1 alpha units — converted to Android's 0..255 units below
            val fusedGapDp = 0.dp             // gap while fused. Try (-3).dp for a deeper, reference-video-style fusion
            val dotBorderColor = Color.White
            val dotBorderWidth = 1.5.dp

            val gooeyDensity = LocalDensity.current

            val pillLabels = remember { listOf("New Playlist", "Select Tag", "Favourite", "Settings") }

            // Gradients with alpha forced to 1. The threshold turns alpha binary anyway, and
            // alpha < 1 would shrink those pills' edges by up to ~3px (uneven pill sizes).
            val pillGradients = remember {
                listOf(
                    listOf(Color(0xFFFFFFFF), Color(0xFFB0B0B0)),
                    listOf(Color(0xFFD8D8D8), Color(0xFFFFFFFF)),
                    listOf(Color(0xFFFFFFFF), Color(0xFF9A9A9A)),
                    listOf(Color(0xFFC8C8C8), Color(0xFFE8E8E8)),
                )
            }

            // ── Geometry (dp values; everything screen-relative derives from maxWidth) ──
            val pillHeightDp = 34.dp
            val pillGapDp = 8.dp
            val sidePadDp = 16.dp
            val pillWidthDp = (maxWidth - sidePadDp * 2 - pillGapDp * 3) / 4   // (maxWidth − 32dp − 24dp) / 4
            val pillRowTopDp = 92.dp                                           // below the 2-line title, from the bottom of the status bar
            val regionHeightDp = pillRowTopDp + pillHeightDp + 16.dp           // +16dp spare below the row for the blur halo
            val menuBoxDp = 40.dp
            val dotSizeDp = 8.dp
            val menuEndPadDp = maxWidth * 0.08f
            val menuTopPadDp = 8.dp
            // top-left of each 8dp dot inside the 40dp menu box → centres (13,13) (28,13) (13,28) (28,28)
            val dotOffsetsDp = remember { listOf(9.dp to 9.dp, 24.dp to 9.dp, 9.dp to 24.dp, 24.dp to 24.dp) }

            val pillWidthPx = with(gooeyDensity) { pillWidthDp.toPx() }
            val pillHeightPx = with(gooeyDensity) { pillHeightDp.toPx() }
            val pillGapPx = with(gooeyDensity) { pillGapDp.toPx() }
            val fusedGapPx = with(gooeyDensity) { fusedGapDp.toPx() }
            val sidePadPx = with(gooeyDensity) { sidePadDp.toPx() }
            val rowCenterYPx = with(gooeyDensity) { (pillRowTopDp + pillHeightDp / 2).toPx() }
            val dotSizePx = with(gooeyDensity) { dotSizeDp.toPx() }
            val blurSigmaPx = with(gooeyDensity) { gooeyBlurSigmaDp.toPx() }
            val menuLeftPx = with(gooeyDensity) { (maxWidth - menuEndPadDp - menuBoxDp).toPx() }
            val menuTopPx = with(gooeyDensity) { menuTopPadDp.toPx() }
            val dotCentersPx = dotOffsetsDp.map { (dx, dy) ->
                with(gooeyDensity) {
                    Offset(
                        menuLeftPx + (dx + dotSizeDp / 2).toPx(),
                        menuTopPx + (dy + dotSizeDp / 2).toPx()
                    )
                }
            }
            // Label size follows pill width (so "New Playlist" fits on narrow screens)
            val labelFontSize = with(gooeyDensity) { (pillWidthDp.value / 7.5f).coerceIn(9f, 12f).dp.toSp() }

            // ── Animation state ─────────────────────────────────────────────────────────
            val appearProgress = remember { Animatable(0f) }      // 0 = hollow dots, 1 = capsules in the row
            val separationProgress = remember { Animatable(0f) }  // 0 = fused (fusedGap), 1 = separated (8dp)
            val closeIconAlpha by animateFloatAsState(
                targetValue = if (menuOpen) 1f else 0f,
                animationSpec = tween(300),
                label = "menuCloseIconAlpha"
            )

            LaunchedEffect(menuOpen) {
                if (menuOpen) {
                    // Phase 1 — FAST: dots morph into capsules, already fused (gap = fusedGap)
                    if (appearProgress.value != 1f) {
                        appearProgress.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                    }
                    // Phase 2 — SLOW: fused → separated; the neck stretches, thins, snaps.
                    separationProgress.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessVeryLow
                        )
                    )
                } else {
                    // Reverse: capsules merge back (neck re-forms), then morph back into the dots
                    if (separationProgress.value != 0f) {
                        separationProgress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                    }
                    if (appearProgress.value != 0f) {
                        appearProgress.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
                    }
                }
            }

            // ── Gooey filter: blur → alpha threshold (API 31+; below that: plain morph, no neck) ──
            val gooeyRenderEffect = remember(blurSigmaPx) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blur = android.graphics.RenderEffect.createBlurEffect(
                        blurSigmaPx, blurSigmaPx, android.graphics.Shader.TileMode.DECAL
                    )
                    // ⚠ android.graphics.ColorMatrix translate values are in 0..255 units, NOT 0..1 like SVG.
                    //   A raw "-8f" puts the cut-off at alpha ≈ 0.002 → the pills balloon into one blob.
                    val threshold = android.graphics.RenderEffect.createColorFilterEffect(
                        android.graphics.ColorMatrixColorFilter(
                            android.graphics.ColorMatrix(
                                floatArrayOf(
                                    1f, 0f, 0f, 0f, 0f,
                                    0f, 1f, 0f, 0f, 0f,
                                    0f, 0f, 1f, 0f, 0f,
                                    0f, 0f, 0f, gooeyAlphaMultiplier, gooeyAlphaShift * 255f,
                                )
                            )
                        )
                    )
                    // createChainEffect(outer, inner): inner (blur) runs first, then outer (threshold)
                    android.graphics.RenderEffect.createChainEffect(threshold, blur).asComposeRenderEffect()
                } else null
            }

            // Current rect of capsule i in px, relative to the top-left of the region below.
            // Only called from draw / layout lambdas → animating costs zero recompositions.
            fun pillRect(i: Int): Rect {
                val appear = appearProgress.value
                val gap = fusedGapPx + (pillGapPx - fusedGapPx) * separationProgress.value
                val rowX = sidePadPx + i * (pillWidthPx + gap) + pillWidthPx / 2f
                val morphT = 1f - appear
                val cx = rowX + (dotCentersPx[i].x - rowX) * morphT
                val cy = rowCenterYPx + (dotCentersPx[i].y - rowCenterYPx) * morphT
                val w = dotSizePx + (pillWidthPx - dotSizePx) * appear
                val h = dotSizePx + (pillHeightPx - dotSizePx) * appear
                return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            }

            // Region = just below the status bar; holds the menu dots AND the pill row,
            // so the dots can morph straight into the capsules in one coordinate space.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(regionHeightDp)
            ) {
                // 1 ▸ SHAPES — ONE container holding all 4 capsules, so the blur can bleed them into each other.
                //     Outer layer fades (applied after the gooey effect); inner layer carries the static effect.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = appearProgress.value }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { renderEffect = gooeyRenderEffect }
                            .drawBehind {
                                if (appearProgress.value <= 0f) return@drawBehind
                                for (i in pillLabels.indices) {
                                    val r = pillRect(i)
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colors = pillGradients[i],
                                            startY = r.top,
                                            endY = r.bottom
                                        ),
                                        topLeft = Offset(r.left, r.top),
                                        size = Size(r.width, r.height),
                                        cornerRadius = CornerRadius(r.height / 2f, r.height / 2f)
                                    )
                                }
                            }
                    )
                }

                // 2 ▸ LABELS — separate layer, NO gooey, so the text stays sharp. Same positions as the shapes.
                //    Each label's Box is CLICKABLE — but ONLY when visible (appearProgress > 0.55).
                //    When the menu is closed, the labels are invisible (alpha=0) AND their
                //    clickable is disabled — so they can't intercept taps meant for other elements.
                //    This fixes the "hidden Settings trigger" at the top-right corner.
                pillLabels.forEachIndexed { i, label ->
                    val labelVisible by remember {
                        derivedStateOf { appearProgress.value > 0.55f }
                    }
                    Box(
                        modifier = Modifier
                            .size(pillWidthDp, pillHeightDp)
                            .offset {
                                val c = pillRect(i).center
                                IntOffset(
                                    (c.x - pillWidthPx / 2f).roundToInt(),
                                    (c.y - pillHeightPx / 2f).roundToInt()
                                )
                            }
                            .graphicsLayer {
                                alpha = ((appearProgress.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                            }
                            .then(
                                if (labelVisible) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    when (label) {
                                        "New Playlist" -> onNewPlaylistClick()
                                        "Settings" -> onSettingsClick()
                                        "Favourite" -> onPlaylistStyleClick()
                                        "Select Tag" -> {}
                                    }
                                    menuOpen = false
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = Color(0xFF1C1C1E),
                            fontFamily = CalSansFamily,
                            fontSize = labelFontSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // 3 ▸ MENU BUTTON — 40dp click target. THREE mutually exclusive states:
                //    • Menu closed, search closed → hollow circles visible, clickable opens menu
                //    • Menu open → X icon visible, clickable closes menu (via inner Box)
                //    • Search open → cross icon visible, clickable closes search
                //    ALL clickables are GATED on visibility to prevent invisible elements
                //    from intercepting taps (the "unreliable tapping" bug). graphicsLayer
                //    alpha=0 does NOT disable pointer input in Compose — so we must
                //    conditionally apply .clickable only when the element is visible.
                val menuButtonAlpha by animateFloatAsState(
                    targetValue = if (searchOpen) 0f else 1f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "menuButtonFade"
                )
                val searchCloseIconAlpha by animateFloatAsState(
                    targetValue = if (searchOpen) 1f else 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "searchCloseIconFade"
                )
                // Visibility gates — derivedStateOf limits recomposition to threshold crossings
                val menuButtonVisible by remember {
                    derivedStateOf { menuButtonAlpha > 0.5f && !searchOpen }
                }
                val searchCloseVisible by remember {
                    derivedStateOf { searchCloseIconAlpha > 0.5f && searchOpen }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = menuEndPadDp, top = menuTopPadDp)
                        .size(menuBoxDp)
                ) {
                    // Menu button (hollow circles) — clickable ONLY when visible (menu closed, search closed)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = menuButtonAlpha * (1f - appearProgress.value) }
                            .then(
                                if (menuButtonVisible) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { menuOpen = !menuOpen; if (menuOpen) { searchOpen = false; searchOpenState.value = false } }
                                else Modifier
                            )
                    ) {
                        dotOffsetsDp.forEach { (dx, dy) ->
                            Box(
                                modifier = Modifier
                                    .offset(dx, dy)
                                    .size(dotSizeDp)
                                    .border(dotBorderWidth, dotBorderColor, CircleShape)
                            )
                        }
                    }
                    // Menu's X icon (visible when menu is open) — NO clickable here,
                    // the inner Box above handles the toggle.
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = "Close menu",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .graphicsLayer { alpha = menuButtonAlpha * closeIconAlpha }
                    )
                    // ★ Search close cross icon — clickable ONLY when visible (search open)
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = "Close search",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .graphicsLayer { alpha = searchCloseIconAlpha }
                            .then(
                                if (searchCloseVisible) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { searchOpen = false; searchOpenState.value = false }
                                else Modifier
                            )
                    )
                }
            }
            } // end if (activeTab == LuteaTab.Library)

            // ★ Search input field is now in LuteaArc.kt (inside the title area)
            //    so it can coordinate with the per-word title animation. The
            //    searchOpen state + BackHandler stay here in the scaffold.

            // ★ Mini player — above the nav bar. Only visible when a song is playing.
            //    Slide-up animation when a song starts, slide-down when stopped.
            //    Tapping the mini player expands to the full player.
            //    Width matches the nav bar (4 tabs + gaps) so the two align exactly.
            val navMetrics = fluidNavMetrics(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                tabCount = LuteaTab.entries.size,
                fontScale = density.fontScale,
            )
            val navMatchedWidth = navMetrics.navBarWidthFor(LuteaTab.entries.size)

            // ★ Black fade at the very bottom edge — pure black at the screen's
            //    bottom edge, fading UP to transparent. Its soft top now dies out
            //    on the mini player's centre line, so the capsule sits in the
            //    fade instead of ending above a hard edge. The nav bar sits in
            //    this region (drawn on top of the fade) so the nav glyphs stay
            //    readable. Restored from a previous build that another session
            //    removed. Hidden while the keyboard is open (the mini player is
            //    hidden then anyway).
            //    This is SEPARATE from the nav's own scrim: that one darkens the
            //    nav area for legibility; this one is a softer ambient fade that
            //    grounds the bottom of the screen. Drawn BEFORE the mini player
            //    so its glass paints on top of it.
            val miniPlayerGap = 56.dp       // inset → mini player bottom edge
            val miniPlayerHeight = 56.dp    // keep in sync with PlayerSheet.kt
            val miniPlayerBottom = effBottomDp + miniPlayerGap
            val bottomFadeHeightDp = miniPlayerBottom + miniPlayerHeight * 0.5f
            if (!imeOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomFadeHeightDp)
                        .background(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.10f to Color.Black.copy(alpha = 0.02f),
                                    0.28f to Color.Black.copy(alpha = 0.10f),
                                    0.46f to Color.Black.copy(alpha = 0.26f),
                                    0.62f to Color.Black.copy(alpha = 0.48f),
                                    0.76f to Color.Black.copy(alpha = 0.72f),
                                    0.88f to Color.Black.copy(alpha = 0.90f),
                                    0.96f to Color.Black.copy(alpha = 0.98f),
                                    1.00f to Color.Black,
                                )
                            )
                        )
                )
            }

            // Bottom: nav capsule (fluid 3-circle expanding nav). Centered at
            // the bottom. The old sparkle-search circle beside the Library tab
            // has been removed — search is now triggered from the title text.
            //
            // Drawn BEFORE the player sheet so the sheet paints on top of it
            // when expanded. Also fades out when the player or lyrics is visible
            // so it doesn't show through.
            // The nav dissolves as the player sheet grows over it.
            val navAlpha by animateFloatAsState(
                targetValue = if (playerVisible || showLyrics) 0f else 1f,
                animationSpec = tween(280),
                label = "navAlpha",
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = effBottomDp)
                    .graphicsLayer { alpha = navAlpha },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LuteaTabCapsule(
                    tabs = LuteaTab.entries.toList(),
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                    backdrop = glassBackdrop,
                    onSearchLongPress = {
                        if (!playerVisible && !showLyrics) {
                            if (activeTab != LuteaTab.Search) onTabSelected(LuteaTab.Search)
                            menuOpen = false
                            searchSoundOverlay.expanded = true
                        }
                    },
                )
            }

            // ★ Hold-to-clear history — a sibling of the layerBackdrop, never a
            //    child. SearchScreen publishes its scroll state + real clear
            //    action through LocalSearchHistoryOverlay.
            if (activeTab == LuteaTab.Search) {
                SearchClearAllOverlay(
                    visible = historyClearVisible &&
                        !playerVisible &&
                        !showLyrics,
                    backdrop = glassBackdrop,
                    onClearAll = { searchHistoryOverlay.clearAll() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = clearAllBottom),
                )
            }

            // ★ Search sound menu — also a sibling of the layerBackdrop, so
            //    the card is REAL kyant glass. It only exists on the Search
            //    tab and gets out of the way of the player.
            SearchSoundOverlay(
                state = searchSoundOverlay,
                backdrop = glassBackdrop,
                visible = activeTab == LuteaTab.Search &&
                    !playerVisible &&
                    !showLyrics,
                bottomPadding = searchSoundCardBottom,
            )

            // ★ Player sheet — one surface: mini capsule ⇄ full player ⇄ queue.
            //    Drag up/down to morph, swipe up from the player for the queue.
            //    Drawn AFTER the nav so it paints on top of it.
            com.rajatxo.lutea.ui.player.morph.MorphPlayerSheet(
                modifier = Modifier.fillMaxSize(),
                onLyricsClick = { showLyrics = true },
                onActiveChange = { playerVisible = it },
                hidden = imeOpen,
                navMatchedWidth = navMatchedWidth,
                miniBottomPadding = miniPlayerBottom,
            )

            // Registered after the sheet so back closes lyrics first.
            BackHandler(enabled = showLyrics) { showLyrics = false }

            // ★ Lyrics screen — on top of everything. Opens from the player's
            //    lyrics icon. Closes back to the player.
            androidx.compose.animation.AnimatedVisibility(
                visible = showLyrics,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(300)
                ),
                exit = androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(300)
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                com.rajatxo.lutea.ui.screens.LyricsScreen(
                    onClose = { showLyrics = false },
                )
            }
        }
    }
}

