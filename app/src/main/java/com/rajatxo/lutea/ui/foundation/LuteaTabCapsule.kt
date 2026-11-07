package com.rajatxo.lutea.ui.foundation

import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab
import kotlin.math.roundToInt

/*
 * =============================================================================
 *  LuteaTabCapsule.kt  —  FLUID NAV  (3 expanding frosted-glass circles)
 * =============================================================================
 *
 *  WHAT IT DOES
 *   - 3 separate glass circles in a row, left to right: Home, Search, Library.
 *   - The active tab is a wide pill (icon + label). The other two stay circles
 *     (icon only). Items never merge: it is a plain width animation, NOT gooey.
 *   - Tap a circle (or swipe the bar): it springs open, the old pill springs
 *     shut, the row reflows and the whole bar stays centred.
 *   - Glass look: backdrop blur + white tint + soft drop shadow + thin bright rim.
 *   - Glossy text: the icon/label get a light reflection on their top half, and
 *     a light glint sweeps across them every time a tab opens.
 *
 *  FITS ANY PHONE SCREEN  (Universal Layout Laws)
 *   1. No insets in this file. LuteaScaffold positions the bar
 *      (LocalLuteaWindowInsets) and applies effBottomDp itself.
 *   2. Root is BoxWithConstraints. Every size is derived from maxWidth /
 *      maxHeight. No screenHeightDp, no LocalConfiguration, no fixed screen sizes.
 *   3. No bottom padding in here. The caller owns it.
 *   4. The pill width is MEASURED from the real label (never guessed). The label
 *      ellipsises before the bar can pass 80% of the width (leaves room for the
 *      optional search circle), circles shrink on narrow screens, and the
 *      system font scale is capped at 1.15x for the label.
 *
 *  NOT IN THIS FILE
 *   The tab shown at app start. Keep the initial activeTab in LuteaScaffold /
 *   MainActivity as LuteaTab.Home so Home is the first page.
 *
 *  EASY TUNING: change the constants just below, nothing else.
 * =============================================================================
 */

// ----------------------------- tunables --------------------------------------

/** Icon + label colour (dark on white glass). */
private val FluidInk = Color(0xFF1C1C1E)

/** White glass tint, top edge → bottom edge (higher = whiter, less see-through). */
private const val FluidGlassTopAlpha = 0.80f
private const val FluidGlassBottomAlpha = 0.64f

/** Background blur behind each circle / pill. */
private val FluidGlassBlur = 16.dp

/** Soft shadow under every circle / pill. */
private val FluidShadow = 6.dp

/** Left → right order of the bar: Home first, Search in the middle, Library last. */
private val FluidOrder = listOf(LuteaTab.Home, LuteaTab.Search, LuteaTab.Library)

// ------------------------------ brushes --------------------------------------

/** Opaque white → grey, used only when no backdrop is available. */
private val FluidFallbackSurface = Brush.verticalGradient(
    colors = listOf(Color.White, Color(0xFFD8D8DC))
)

/** Soft sheen across the top of the glass. */
private val FluidSurfaceSheen = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.30f),
    0.55f to Color.Transparent,
    1f to Color.Transparent,
)

/** Thin bright rim: light catches the top edge, fades, faint bounce at the bottom. */
private val FluidRimLight = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.95f),
    0.5f to Color.White.copy(alpha = 0.18f),
    1f to Color.White.copy(alpha = 0.55f),
)

/** Glossy reflection painted ONLY over the dark glyphs (BlendMode.SrcAtop). */
private val FluidTextReflection = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.44f),
    0.49f to Color.White.copy(alpha = 0.22f),
    0.53f to Color.Transparent,
    1f to Color.Transparent,
)

// ------------------------------ public API -----------------------------------

@Composable
fun LuteaTabCapsule(
    tabs: List<LuteaTab>,
    activeTab: LuteaTab,
    onTabSelected: (LuteaTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    // Always Home -> Search -> Library, whatever order the caller passes in.
    val orderedTabs = remember(tabs) {
        FluidOrder.filter { it in tabs } + tabs.filter { it !in FluidOrder }
    }

    // ★ No sound effects. Vibration on tab change — uses the Vibrator API
    //    directly (createOneShot) which works on ALL devices, bypassing
    //    performHapticFeedback's view/window dependency that was producing
    //    no vibration at all.
    val navVibrator = remember {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    }
    val select: (LuteaTab) -> Unit = { tab ->
        if (tab != activeTab) {
            // 25ms one-shot vibration — medium intensity, noticeable but not too strong
            if (navVibrator != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                navVibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        25,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                // Fallback for older APIs or no vibrator — use performHapticFeedback
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            onTabSelected(tab)
        }
    }

    // Latest values for the long-lived swipe detector below.
    val latestSelect by rememberUpdatedState(select)
    val latestTabs by rememberUpdatedState(orderedTabs)
    val latestActive by rememberUpdatedState(activeTab)
    val swipeThresholdPx = with(density) { 28.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier.pointerInput(swipeThresholdPx) {
            var travel = 0f
            detectHorizontalDragGestures(
                onDragStart = { travel = 0f },
                onDragCancel = { travel = 0f },
                onDragEnd = {
                    val list = latestTabs
                    val index = list.indexOf(latestActive)
                    if (list.size > 1 && index >= 0) {
                        // Swipe left = next tab, swipe right = previous (wraps around).
                        if (travel <= -swipeThresholdPx) {
                            latestSelect(list[(index + 1) % list.size])
                        } else if (travel >= swipeThresholdPx) {
                            latestSelect(list[(index - 1 + list.size) % list.size])
                        }
                    }
                    travel = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    travel += dragAmount
                },
            )
        },
    ) {
        val metrics = fluidNavMetrics(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            tabCount = orderedTabs.size,
            fontScale = density.fontScale,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(metrics.itemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            orderedTabs.forEach { tab ->
                key(tab) {
                    FluidNavItem(
                        tab = tab,
                        isActive = tab == activeTab,
                        metrics = metrics,
                        backdrop = backdrop,
                        onClick = { select(tab) },
                    )
                }
            }
        }
    }
}

// ------------------------------- sizing --------------------------------------

/** Every size the bar needs, derived once from the space the caller gives us. */
@Immutable
private class FluidNavMetrics(
    val diameter: Dp,
    val itemGap: Dp,
    val iconSize: Dp,
    val iconLabelGap: Dp,
    val endPadding: Dp,
    val maxLabelWidth: Dp,
    val labelSize: TextUnit,
)

private fun fluidNavMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
    tabCount: Int,
    fontScale: Float,
): FluidNavMetrics {
    // Guard: a parent could (rarely) hand us unbounded space.
    val w = if (maxWidth == Dp.Infinity) 360.dp else maxWidth
    val h = if (maxHeight == Dp.Infinity) 800.dp else maxHeight

    // Circle diameter: 48dp normally, smaller on very narrow screens, a bit larger
    // on tablets/foldables, and never taller than 13% of the available height
    // (keeps landscape phones tidy). Never below 40dp.
    val byWidth = if (w >= 600.dp) 56.dp else minOf(48.dp, w * 0.14f)
    val diameter = maxOf(40.dp, minOf(byWidth, h * 0.13f))

    // Everything else scales from the diameter (designed at 48dp).
    val s = diameter / 48.dp
    val itemGap = 8.dp * s
    val iconSize = 22.dp * s
    val iconLabelGap = 10.dp * s
    val endPadding = 20.dp * s
    val startPadding = (diameter - iconSize) / 2f

    // Label size ignores user font scale above 1.15x so the bar can never blow up.
    val cappedScale = minOf(fontScale, 1.15f)
    val labelSize = (17f * s * cappedScale / fontScale).sp

    // The widest the label may be so that the bar stays within 80% of the width.
    val others = (tabCount - 1).coerceAtLeast(0)
    val budget = w * 0.80f
    val fixed = (diameter + itemGap) * others + startPadding + iconSize + iconLabelGap + endPadding
    val maxLabelWidth = maxOf(diameter, budget - fixed)

    return FluidNavMetrics(
        diameter = diameter,
        itemGap = itemGap,
        iconSize = iconSize,
        iconLabelGap = iconLabelGap,
        endPadding = endPadding,
        maxLabelWidth = maxLabelWidth,
        labelSize = labelSize,
    )
}

// -------------------------------- item ---------------------------------------

@Composable
private fun FluidNavItem(
    tab: LuteaTab,
    isActive: Boolean,
    metrics: FluidNavMetrics,
    backdrop: LayerBackdrop?,
    onClick: () -> Unit,
) {
    val shape: Shape = CircleShape

    // 0 = circle, 1 = full pill. The spring gives the slight overshoot.
    val expand = animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "fluidTabExpand",
    )

    // Tiny press-in feedback.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "fluidTabPress",
    )

    // Glint that sweeps over the glyphs each time this tab opens (1 = finished).
    val glint = remember { Animatable(1f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            glint.snapTo(0f)
            glint.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 900,
                    delayMillis = 120,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            glint.snapTo(1f)
        }
    }

    // Frosted glass when a backdrop exists, opaque white -> grey otherwise.
    val surface: Modifier = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(FluidGlassBlur.toPx()) },
            onDrawSurface = {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = FluidGlassTopAlpha),
                            Color.White.copy(alpha = FluidGlassBottomAlpha),
                        )
                    )
                )
                drawRect(brush = FluidSurfaceSheen)
            },
        )
    } else {
        Modifier.background(brush = FluidFallbackSurface, shape = shape)
    }

    Layout(
        content = {
            Icon(
                imageVector = fluidTabIcon(tab),
                contentDescription = null,
                tint = FluidInk,
                modifier = Modifier.size(metrics.iconSize),
            )
            Text(
                text = tab.label,
                color = FluidInk,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.labelSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    // Label fades in as the pill widens; invisible while it is a circle.
                    alpha = ((expand.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                },
            )
        },
        modifier = Modifier
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            .shadow(elevation = FluidShadow, shape = shape, clip = true)
            .then(surface)
            .border(width = 0.8.dp, brush = FluidRimLight, shape = shape)
            .fluidGloss { glint.value }
            .selectable(
                selected = isActive,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
    ) { measurables, _ ->
        val d = metrics.diameter.roundToPx()
        val iconPx = metrics.iconSize.roundToPx()
        val gapPx = metrics.iconLabelGap.roundToPx()
        val endPadPx = metrics.endPadding.roundToPx()
        val startPadPx = (d - iconPx) / 2

        val iconPlaceable = measurables[0].measure(Constraints.fixed(iconPx, iconPx))
        val labelPlaceable = measurables[1].measure(
            Constraints(maxWidth = metrics.maxLabelWidth.roundToPx())
        )

        // Pill width follows the real label width, so nothing is ever clipped.
        val pillWidth = startPadPx + iconPx + gapPx + labelPlaceable.width + endPadPx
        val t = expand.value.coerceIn(0f, 1.25f)
        val width = (d + (pillWidth - d) * t).roundToInt().coerceAtLeast(d)

        layout(width, d) {
            // The icon never moves: it sits where it is centred in the circle.
            iconPlaceable.placeRelative(startPadPx, (d - iconPlaceable.height) / 2)
            // The label is simply revealed (clipped) as the pill grows.
            labelPlaceable.placeRelative(
                startPadPx + iconPx + gapPx,
                (d - labelPlaceable.height) / 2,
            )
        }
    }
}

// ------------------------------- helpers -------------------------------------

private fun fluidTabIcon(tab: LuteaTab): ImageVector = when (tab) {
    LuteaTab.Home -> LuteaIcons.Home
    LuteaTab.Search -> LuteaIcons.Search
    LuteaTab.Library -> LuteaIcons.Library
}

/**
 * Glossy look for everything drawn inside (icon + label):
 *  1. a light reflection over the top half of the glyphs,
 *  2. a diagonal glint that sweeps across while [glint] goes 0 -> 1.
 * Both are painted with SrcAtop inside an offscreen layer, so they only ever
 * touch the glyph pixels, never the glass behind them.
 */
private fun Modifier.fluidGloss(glint: () -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = FluidTextReflection, blendMode = BlendMode.SrcAtop)

        val t = glint()
        if (t < 1f) {
            val band = size.height * 1.1f
            val startX = -band + (size.width + band) * t
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.9f),
                    1f to Color.Transparent,
                    start = Offset(startX, 0f),
                    end = Offset(startX + band, size.height * 0.6f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
