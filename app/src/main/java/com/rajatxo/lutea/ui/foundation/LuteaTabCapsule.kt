package com.rajatxo.lutea.ui.foundation

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
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
 * LuteaTabCapsule — frosted floating nav bar, Coral-style single-tab display.
 *
 *   ONE OUTER capsule — blurs the background (kyant drawBackdrop) + a white
 *   gradient overlay (frosted white gradient: brighter at top, fading down).
 *   53% of screen width, subtle dark shadow.
 *
 *   Inside: only the ACTIVE tab is shown at a time — icon + CalSans label,
 *   centered. Swipe left/right to change tabs; the content slides in the
 *   swipe direction (AnimatedContent + slideInHorizontally/slideOutHorizontally).
 *   Tabs wrap around (past Library → Home). No black pill, no per-slot layout.
 *
 * Pattern ported from Coral's TabCapsule.kt (swipe + AnimatedContent slide).
 *
 * Universal Layout Laws:
 *  - Law 7: BoxWithConstraints at root. Width = 53% of maxWidth, clamped.
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

    // Swipe state — ported from Coral's TabCapsule.
    var slideDirection by remember { mutableIntStateOf(1) }   // 1 = forward (left swipe), -1 = back
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 60f

    // Palette
    val contentColor = Color(0xFF1C1C1E)   // dark icon+label on the white frosted surface
    // Frosted white gradient overlay — alpha varies top→bottom for the gradient.
    val frostWhiteTop = 0.55f
    val frostWhiteBottom = 0.38f
    // Backdrop blur radius (Coral-matched 12dp).
    val blurDp = 12.dp

    BoxWithConstraints(modifier = modifier) {
        val outerHeight: Dp = 51.dp
        // ★ Law 7: 53% of available width, clamped. On the Library tab the
        //    sparkle-search circle (= outerHeight) + 8dp gap sits beside the
        //    capsule in the scaffold — shrink the capsule so the combined
        //    [capsule + gap + circle] width equals the full 53% (same width
        //    as the capsule on the other tabs).
        val outerWidth: Dp = if (activeTab == LuteaTab.Library) {
            ((maxWidth * 0.53f) - outerHeight - 8.dp).coerceIn(140.dp, 300.dp)
        } else {
            (maxWidth * 0.53f).coerceIn(200.dp, 360.dp)
        }
        val outerShape: Shape = RoundedCornerShape(outerHeight / 2)

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
                // ★ Swipe to change tabs (Coral pattern). Wraps around.
                .pointerInput(tabs, activeTab) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            dragAccumulator = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                            if (dragAccumulator < -dragThreshold) {
                                // Swiped left → next tab
                                slideDirection = 1
                                val nextIndex = (activeIndex + 1) % tabs.size
                                onTabSelected(tabs[nextIndex])
                                doTick()
                                dragAccumulator = 0f
                            } else if (dragAccumulator > dragThreshold) {
                                // Swiped right → previous tab (wrap to last)
                                slideDirection = -1
                                val prevIndex = if (activeIndex - 1 < 0) tabs.size - 1 else activeIndex - 1
                                onTabSelected(tabs[prevIndex])
                                doTick()
                                dragAccumulator = 0f
                            }
                        }
                    )
                }
        ) {
            // ★ Only the active tab is shown at a time — icon + label, centered.
            //    Slides in the swipe direction on tab change (Coral's AnimatedContent pattern).
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    if (slideDirection == 1) {
                        slideInHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth } togetherWith
                            slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth }
                    } else {
                        slideInHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth } togetherWith
                            slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                label = "tabContent"
            ) { tab ->
                val icon = when (tab) {
                    LuteaTab.Home -> LuteaIcons.Home
                    LuteaTab.Search -> LuteaIcons.Search
                    LuteaTab.Library -> LuteaIcons.Library
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tab.label,
                        color = contentColor,
                        fontSize = 16.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
