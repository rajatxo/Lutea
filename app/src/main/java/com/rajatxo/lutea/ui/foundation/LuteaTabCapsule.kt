package com.rajatxo.lutea.ui.foundation

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab

/**
 * LuteaTabCapsule — frosted neumorphic floating nav bar.
 *
 * Three concentric layers (outside → in):
 *   1. OUTER capsule — opaque white→grey gradient body (no blur, no glossy
 *      stroke), 80% of screen width, with a subtle dark drop shadow.
 *   2. INNER capsule — inset, sits simply on the outer; blurs the background
 *      via kyant backdrop (pure matte blur, no vibrancy/gloss).
 *   3. BLACK active pill — sits inside the inner capsule, slides between
 *      tabs with a spring; active icon+label invert to white.
 *
 * Each tab = icon (left, 22dp) + CalSans label (right, 14sp), horizontal.
 *
 * Universal Layout Laws:
 *  - Law 7: BoxWithConstraints at root. Width = 80% of maxWidth, clamped.
 *  - Universal Fitting Rule: screen-relative sizing via % of maxWidth; only
 *    UI-element sizes (icon, text, gap, elevation, inset) use hardcoded dp.
 */
@Composable
fun LuteaTabCapsule(
    tabs: List<LuteaTab>,
    activeTab: LuteaTab,
    onTabSelected: (LuteaTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    // SoundPool for tick sound
    val soundPool = remember { SoundPool.Builder().setMaxStreams(1).build() }
    val tickSoundId = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        tickSoundId.value = soundPool.load(context, R.raw.wheel_tick, 1)
        onDispose { soundPool.release() }
    }
    val doTick: () -> Unit = {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (tickSoundId.value != 0) {
            soundPool.play(tickSoundId.value, 0.5f, 0.5f, 1, 0, 1f)
        }
    }

    // Palette
    val activeContentColor = Color.White
    val inactiveContentColor = Color(0xFF1C1C1E)
    // Inner is the blur layer (frosted window on the opaque outer body).
    // White tint kept light so the blur reads but inactive dark icons stay
    // visible over Lutea's dark background.
    val innerFrostAlpha = 0.45f
    val innerBlurDp = 16.dp
    // Solid fallback for the inner when no backdrop is supplied.
    val innerSolidFill = Color(0xFFE2E2E2)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // ★ Law 7: 70% of available width, clamped for extreme aspect ratios.
        val outerWidth: Dp = (maxWidth * 0.7f).coerceIn(250.dp, 420.dp)
        // Explicit heights per spec — insets derive symmetrically from these.
        val outerHeight: Dp = 51.dp
        val innerHeight: Dp = 46.dp
        val pillHeight: Dp = 40.dp
        val innerInset: Dp = (outerHeight - innerHeight) / 2   // 2.5dp
        val pillInsetV: Dp = (innerHeight - pillHeight) / 2    // 3dp
        val outerShape: Shape = RoundedCornerShape(outerHeight / 2)
        val innerShape: Shape = RoundedCornerShape(innerHeight / 2)

        // Sliding black pill — lives inside the inner capsule
        val tabSlotWidth: Dp = (outerWidth - (innerInset * 2)) / tabs.size
        val pillInsetH: Dp = 2.dp   // tighter → pill a little longer
        val pillWidth: Dp = tabSlotWidth - (pillInsetH * 2)
        val pillShape: Shape = RoundedCornerShape(pillHeight / 2)

        // ★ Spring-animated sliding indicator — glides between slots.
        val pillPosition = remember { Animatable(activeIndex.toFloat()) }
        LaunchedEffect(activeIndex) {
            pillPosition.animateTo(
                targetValue = activeIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        // NOTE: Dp must be LEFT operand — `Float * Dp` resolves to a top-level
        // extension in androidx.compose.ui.unit that isn't auto-imported;
        // `Dp * Float` is a member operator and always resolves.
        val pillOffsetX: Dp = tabSlotWidth * pillPosition.value + pillInsetH

        // OUTER capsule surface — opaque white→grey gradient body (no blur,
        // no glossy stroke). The inner is what blurs the background.
        val outerModifier = Modifier
            .shadow(elevation = 3.dp, shape = outerShape, clip = true)
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White,
                        0.5f to Color(0xFFECECEC),
                        1.0f to Color(0xFFD4D4D4)
                    )
                )
            )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(outerWidth)
                .height(outerHeight)
                .then(outerModifier)
        ) {
            // INNER capsule surface — sits on the outer; blurs the background (matte).
            val innerModifier = if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop, shape = { innerShape },
                    effects = {
                        // No vibrancy here — keeps the inner matte (no glossy sheen).
                        blur(innerBlurDp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = innerFrostAlpha)) }
                )
            } else {
                Modifier.background(innerSolidFill)
            }

            Box(
                modifier = Modifier
                    .padding(innerInset)
                    .fillMaxSize()
                    .then(innerModifier)
            ) {
                // Black active pill (slides inside the inner capsule)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = pillOffsetX)
                        .width(pillWidth)
                        .height(pillHeight)
                        .shadow(elevation = 2.dp, shape = pillShape, clip = true)
                        .background(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xFF333333),  // top: subtle dark-gray sheen (premium highlight)
                                    0.5f to Color(0xFF0A0A0A),  // center: near-pure black
                                    1.0f to Color(0xFF1F1F1F)   // bottom: subtle reflection
                                )
                            )
                        )
                )

                // Tabs row — on top of everything
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isActive = index == activeIndex

                        val iconScale by animateFloatAsState(
                            targetValue = if (isActive) 1.1f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "iconScale"
                        )
                        // Single color state drives both icon tint and label color
                        // so they always stay in sync as the pill slides under them.
                        val contentColor by animateColorAsState(
                            targetValue = if (isActive) activeContentColor else inactiveContentColor,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "contentColor"
                        )

                        val icon = when (tab) {
                            LuteaTab.Home -> LuteaIcons.Home
                            LuteaTab.Search -> LuteaIcons.Search
                            LuteaTab.Library -> LuteaIcons.Library
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (!isActive) {
                                            onTabSelected(tab)
                                            doTick()
                                        }
                                    }
                                ),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tab.label,
                                color = contentColor,
                                fontSize = 14.sp,
                                fontFamily = CalSansFamily,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
