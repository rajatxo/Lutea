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
 * LuteaTabCapsule — frosted floating nav bar (2 layers).
 *
 *   1. OUTER capsule — blurs the background (kyant drawBackdrop) with a white
 *      gradient overlay on top (frosted white gradient: brighter at top,
 *      fading toward the bottom). 70% of screen width, subtle dark shadow.
 *   2. BLACK active pill — sits directly inside the outer capsule (the inner
 *      capsule was removed), slides between tabs with a spring; active
 *      icon+label invert to white.
 *
 * Each tab = icon (left, 22dp) + CalSans label (right, 14sp), horizontal.
 *
 * Universal Layout Laws:
 *  - Law 7: BoxWithConstraints at root. Width = 70% of maxWidth, clamped.
 *  - Universal Fitting Rule: screen-relative sizing via % of maxWidth; only
 *    UI-element sizes (icon, text, gap, elevation) use hardcoded dp.
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
    // Frosted white gradient overlay — alpha varies top→bottom for the gradient.
    val frostWhiteTop = 0.55f
    val frostWhiteBottom = 0.38f
    // Backdrop blur radius (Coral-matched 12dp).
    val blurDp = 12.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // ★ Law 7: 70% of available width, clamped for extreme aspect ratios.
        val outerWidth: Dp = (maxWidth * 0.7f).coerceIn(250.dp, 420.dp)
        val outerHeight: Dp = 51.dp
        val outerShape: Shape = RoundedCornerShape(outerHeight / 2)

        // Sliding black pill — sits directly inside the outer capsule (no inner layer).
        val tabSlotWidth: Dp = outerWidth / tabs.size
        val pillInsetH: Dp = 2.dp
        val pillHeight: Dp = 40.dp
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

        // OUTER capsule surface — blurs the background + white gradient overlay.
        val outerModifier = if (backdrop != null) {
            Modifier
                .shadow(elevation = 3.dp, shape = outerShape, clip = true)
                .drawBackdrop(
                    backdrop = backdrop, shape = { outerShape },
                    effects = {
                        blur(blurDp.toPx())
                    },
                    onDrawSurface = {
                        // Frosted white gradient — brighter at top, fading down.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = frostWhiteTop),
                                    Color.White.copy(alpha = frostWhiteBottom)
                                )
                            )
                        )
                    }
                )
        } else {
            // Fallback: opaque white→grey gradient when no backdrop is supplied.
            Modifier
                .shadow(elevation = 3.dp, shape = outerShape, clip = true)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFCFCFC), Color(0xFFE2E2E2))
                    )
                )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(outerWidth)
                .height(outerHeight)
                .then(outerModifier)
        ) {
            // Black active pill (slides inside the outer capsule)
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
