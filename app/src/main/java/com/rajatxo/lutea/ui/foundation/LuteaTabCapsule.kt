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
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab

/**
 * LuteaTabCapsule — frosted neumorphic floating nav bar.
 *
 * Three concentric layers (outside → in):
 *   1. OUTER capsule — whitish frosted glass (kyant blur + white tint),
 *      80% of screen width, thin, with a subtle dark drop shadow.
 *   2. INNER capsule — inset 4dp, less white + STRONGER blur (frosts harder
 *      than the outer) so the inner ring reads visibly grayer/more-blurred.
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
    val activePillStart = Color(0xFF2C2C2C)
    val activePillEnd = Color(0xFF181818)
    val activeContentColor = Color.White
    val inactiveContentColor = Color(0xFF1C1C1E)
    // Frosted-glass tints — white overlays on the blurred backdrop.
    // Outer is MORE white (0.55); inner is LESS white (0.30) so blur dominates.
    val outerFrostAlpha = 0.55f
    val innerFrostAlpha = 0.30f
    // Blur radii. Inner blurs harder than outer (the key requirement).
    val outerBlurDp = 8.dp
    val innerBlurDp = 16.dp
    // Solid fallback fills (when no backdrop is supplied)
    val outerSolidFill = Color(0xFFEFEFEF)
    val innerSolidFill = Color(0xFFE2E2E2)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // ★ Law 7: 80% of available width, clamped for extreme aspect ratios.
        val outerWidth: Dp = (maxWidth * 0.8f).coerceIn(280.dp, 460.dp)
        val outerHeight: Dp = 46.dp
        val outerShape: Shape = RoundedCornerShape(outerHeight / 2)

        // Inner capsule — inset 4dp on all sides
        val innerInset: Dp = 4.dp
        val innerHeight: Dp = outerHeight - (innerInset * 2)
        val innerShape: Shape = RoundedCornerShape(innerHeight / 2)

        // Sliding black pill — lives inside the inner capsule
        val tabSlotWidth: Dp = (outerWidth - (innerInset * 2)) / tabs.size
        val pillInsetH: Dp = 4.dp
        val pillInsetV: Dp = 3.dp
        val pillWidth: Dp = tabSlotWidth - (pillInsetH * 2)
        val pillHeight: Dp = innerHeight - (pillInsetV * 2)
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

        // OUTER capsule surface — whitish frosted glass + subtle dark shadow.
        val outerModifier = if (backdrop != null) {
            Modifier
                .shadow(elevation = 3.dp, shape = outerShape, clip = true)
                .drawBackdrop(
                    backdrop = backdrop, shape = { outerShape },
                    effects = {
                        vibrancy()
                        blur(outerBlurDp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = outerFrostAlpha)) }
                )
        } else {
            Modifier
                .shadow(elevation = 3.dp, shape = outerShape, clip = true)
                .background(outerSolidFill)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(outerWidth)
                .height(outerHeight)
                .then(outerModifier)
        ) {
            // INNER capsule surface — inset 4dp, less white + STRONGER blur.
            val innerModifier = if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop, shape = { innerShape },
                    effects = {
                        vibrancy()
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
                            brush = Brush.linearGradient(
                                colors = listOf(activePillStart, activePillEnd)
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
