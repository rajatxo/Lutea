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
import androidx.compose.ui.draw.clip
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
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab

/**
 * LuteaTabCapsule — neumorphic floating nav bar matching the reference design.
 *
 * Design:
 *  - Floating light capsule at bottom center, 90% of screen width (Law 7).
 *  - Three tabs, each = icon (left) + CalSans label (right), horizontal layout.
 *  - Active tab: dark filled pill behind icon+label together; white icon+text.
 *  - Inactive tabs: dark icon + dark label, no background.
 *  - Spring-animated sliding pill (Animatable + Modifier.offset, no layout shift).
 *  - Animated icon tint + label color (single animateColorAsState drives both).
 *  - Animated icon scale (graphicsLayer, draw-phase).
 *  - Haptic + wheel-tick sound on tab change.
 *
 * Universal Layout Laws:
 *  - Law 7: BoxWithConstraints at root — width = 90% of maxWidth, clamped for
 *    extreme aspect ratios (tablets/foldables). NEVER screenHeightDp.
 *  - Universal Fitting Rule: screen-relative sizing via % of maxWidth; only
 *    UI-element sizes (icon, text, gap, elevation) use hardcoded dp.
 */
@Composable
fun LuteaTabCapsule(
    tabs: List<LuteaTab>,
    activeTab: LuteaTab,
    onTabSelected: (LuteaTab) -> Unit,
    modifier: Modifier = Modifier,
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

    // Palette — light neumorphic capsule floating on Lutea's dark background,
    // with a dark active pill wrapping icon+label (matches the reference image).
    val capsuleFill = Color(0xFFEFEFEF)
    val activePillStart = Color(0xFF2C2C2C)
    val activePillEnd = Color(0xFF181818)
    val activeContentColor = Color.White
    val inactiveContentColor = Color(0xFF1C1C1E)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // ★ Law 7: 90% of available width, clamped so it never gets absurd on
        //    tablets/foldables or too cramped on tiny screens.
        val capsuleWidth: Dp = (maxWidth * 0.9f).coerceIn(300.dp, 520.dp)
        val capsuleHeight: Dp = 60.dp
        val capsuleShape: Shape = RoundedCornerShape(capsuleHeight / 2)

        // Per-slot geometry for the sliding active pill.
        val tabSlotWidth: Dp = capsuleWidth / tabs.size
        val slotPadding: Dp = 8.dp
        val pillWidth: Dp = tabSlotWidth - (slotPadding * 2)
        val pillHeight: Dp = capsuleHeight - 12.dp
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
        // NOTE: Dp must be the LEFT operand — `Float * Dp` resolves to a
        // top-level extension in androidx.compose.ui.unit that isn't
        // auto-imported; `Dp * Float` is a member operator and always resolves.
        val pillOffsetX: Dp = tabSlotWidth * pillPosition.value + slotPadding

        // Light capsule surface with a soft drop shadow so it floats on dark bg.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(capsuleWidth)
                .height(capsuleHeight)
                .shadow(elevation = 8.dp, shape = capsuleShape, clip = true)
                .background(capsuleFill)
        ) {
            // Sliding dark pill behind the active slot (icon + label together).
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

            // Tabs row — each tab is a horizontal icon + label, evenly spaced.
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isActive = index == activeIndex

                    val iconScale by animateFloatAsState(
                        targetValue = if (isActive) 1.15f else 1f,
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
                                .size(24.dp)
                                .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tab.label,
                            color = contentColor,
                            fontSize = 15.sp,
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
