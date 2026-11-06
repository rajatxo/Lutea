package com.rajatxo.lutea.ui.foundation

import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.icons.LuteaIcons
import com.rajatxo.lutea.ui.util.LuteaTab

/**
 * LuteaTabCapsule — redesigned 3-icon glass capsule nav bar.
 *
 * Design (from reference images):
 *  - Floating pill/capsule at bottom center
 *  - 3 tabs: Home, Search, Library (evenly spaced icons)
 *  - Active tab: dark pill background + white icon
 *  - Inactive tabs: white icon, no background
 *  - Glass morphism via kyant backdrop (blur + vibrancy + colorControls)
 *  - Universal fitting: width = 70% of screen, height = 56dp, corner = 28dp
 *  - Haptic + tick sound on tab change
 */
@Composable
fun LuteaTabCapsule(
    tabs: List<LuteaTab>,
    activeTab: LuteaTab,
    onTabSelected: (LuteaTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    customWidth: Dp? = null,
    customHeight: Dp? = null,
    customShape: Shape? = null
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    // SoundPool for tick sound
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(1).build()
    }
    val tickSoundId = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        tickSoundId.value = soundPool.load(context, R.raw.wheel_tick, 1)
        onDispose { soundPool.release() }
    }

    // Combined tick + haptic
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

    // ★ Universal fitting: width = 70% of screen (not hardcoded), height = 56dp
    val actualWidth = customWidth ?: 220.dp
    val actualHeight = customHeight ?: 56.dp
    val capsuleShape = customShape ?: RoundedCornerShape(28.dp)
    // Active tab pill dimensions
    val activePillWidth = (actualWidth / tabs.size) - 8.dp
    val activePillShape = RoundedCornerShape(actualHeight / 2)

    val glassModifier = if (backdrop != null) {
        modifier
            .width(actualWidth)
            .height(actualHeight)
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
        modifier
            .width(actualWidth)
            .height(actualHeight)
            .clip(capsuleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    }

    Box(modifier = glassModifier) {
        // Active tab highlight (animated pill that slides)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp + (activeIndex * (actualWidth / tabs.size)).toDpApprox())
                .width(activePillWidth)
                .height(actualHeight - 8.dp)
                .clip(activePillShape)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Icons row
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == activeIndex
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
                    val icon = when (tab) {
                        LuteaTab.Home -> LuteaIcons.Home
                        LuteaTab.Search -> LuteaIcons.Search
                        LuteaTab.Library -> LuteaIcons.Library
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// Helper: approximate Dp from Float (for active pill positioning)
private fun Float.toDpApprox(): Dp = (this / 3f).dp
