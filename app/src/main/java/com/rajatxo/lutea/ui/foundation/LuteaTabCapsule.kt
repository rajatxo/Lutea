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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
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
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab

/**
 * LuteaTabCapsule — perfected 3-icon glass capsule nav bar.
 *
 * Improvements over the previous redesign:
 *  1. Animated sliding indicator (Animatable + spring) — pill glides between
 *     tabs instead of jumping. No layout shift (uses Modifier.offset, draw-phase).
 *  2. Animated icon scale — active icon springs up to 1.18× (graphicsLayer,
 *     draw-phase, zero recomposition of siblings).
 *  3. Animated icon tint — smooth color transition between active/inactive.
 *  4. Always-on labels under icons (active bright, inactive dim, alpha animated).
 *  5. Responsive sizing per Universal Layout Law 7 — width = 55% of maxWidth,
 *     clamped to [208dp, 300dp]; height 54dp. Works in multi-window/foldables.
 *  6. Pill uses a subtle vertical gradient (premium feel) instead of flat color.
 *
 * Kept from previous design:
 *  - Glass morphism via kyant LayerBackdrop (vibrancy + colorControls + blur)
 *  - Haptic + tick sound on tab change
 *  - Fallback solid background when no backdrop is supplied
 *
 * Universal Layout Laws followed:
 *  - Law 7: BoxWithConstraints at root (NEVER screenHeightDp / hardcoded screen dp)
 *  - Universal Fitting Rule: width is % of maxWidth, clamped; only UI-element
 *    sizes (icon, text, stroke) use hardcoded dp.
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

    // SoundPool for tick sound (kept from previous design)
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(1).build()
    }
    val tickSoundId = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        tickSoundId.value = soundPool.load(context, R.raw.wheel_tick, 1)
        onDispose { soundPool.release() }
    }

    // Combined tick + haptic feedback on tab change
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

    // ★ Law 7: BoxWithConstraints at root — responsive width, multi-window safe.
    //    Width = 55% of available width, clamped so 3 slots always stay ≥ ~69dp.
    //    Height is a fixed UI-element size (54dp) — allowed by Universal Fitting Rule.
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val capsuleWidth: Dp = (maxWidth * 0.55f).coerceIn(208.dp, 300.dp)
        val capsuleHeight: Dp = 54.dp
        val capsuleShape: Shape = RoundedCornerShape(capsuleHeight / 2)

        // Per-slot geometry
        val tabSlotWidth: Dp = capsuleWidth / tabs.size
        val pillInset: Dp = 5.dp
        val pillWidth: Dp = tabSlotWidth - (pillInset * 2)
        val pillHeight: Dp = capsuleHeight - (pillInset * 2)
        val pillShape: Shape = RoundedCornerShape(pillHeight / 2)

        // ★ Animated sliding indicator — glides between slots with a spring.
        //    Initialised to the current active index so the first composition
        //    has no spurious animation.
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
        // pillOffset = animated slot index × slot width + inset (draw-phase via offset).
        // NOTE: Dp must be the LEFT operand — `Float * Dp` resolves to a top-level
        // extension in androidx.compose.ui.unit that isn't auto-imported, whereas
        // `Dp * Float` is a member operator on Dp and always resolves.
        val pillOffsetX: Dp = tabSlotWidth * pillPosition.value + pillInset

        // Glass capsule surface
        val glassModifier = if (backdrop != null) {
            Modifier
                .width(capsuleWidth)
                .height(capsuleHeight)
                .clip(capsuleShape)
                .drawBackdrop(
                    backdrop = backdrop, shape = { capsuleShape },
                    effects = {
                        vibrancy()
                        colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
                        blur(12f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.25f)) }
                )
        } else {
            Modifier
                .width(capsuleWidth)
                .height(capsuleHeight)
                .clip(capsuleShape)
                .background(Color.Black.copy(alpha = 0.55f))
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(glassModifier)
        ) {
            // ★ Sliding active-pill highlight — vertical gradient, offset in draw phase.
            //    No layout shift: Modifier.offset moves it without re-measuring siblings.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillOffsetX)
                    .width(pillWidth)
                    .height(pillHeight)
                    .clip(pillShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.22f),
                                0.5f to Color.White.copy(alpha = 0.12f),
                                1.0f to Color.White.copy(alpha = 0.06f)
                            )
                        )
                    )
            )

            // Icons + labels row
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isActive = index == activeIndex

                    // Animated icon scale (draw-phase, graphicsLayer)
                    val iconScale by animateFloatAsState(
                        targetValue = if (isActive) 1.18f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "iconScale"
                    )
                    // Animated icon tint
                    val iconTint by animateColorAsState(
                        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "iconTint"
                    )
                    // Animated label alpha
                    val labelAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.42f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "labelAlpha"
                    )

                    val icon = when (tab) {
                        LuteaTab.Home -> LuteaIcons.Home
                        LuteaTab.Search -> LuteaIcons.Search
                        LuteaTab.Library -> LuteaIcons.Library
                    }

                    Box(
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
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = iconTint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer(
                                        scaleX = iconScale,
                                        scaleY = iconScale
                                    )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                color = Color.White.copy(alpha = labelAlpha),
                                fontSize = 10.sp,
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
