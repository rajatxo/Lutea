package com.rajatxo.lutea.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import kotlinx.coroutines.delay

/**
 * Cal Sans font family — bundled in res/font/cal_sans_semibold.ttf.
 * Same family declaration as Coral uses (CoralTypography.kt:75).
 */
val CalSansFamily = FontFamily(
    Font(R.font.cal_sans_semibold, FontWeight.Normal),
    Font(R.font.cal_sans_semibold, FontWeight.SemiBold),
    Font(R.font.cal_sans_semibold, FontWeight.Medium),
    Font(R.font.cal_sans_semibold, FontWeight.Bold),
)

/**
 * Instrument Serif font family — bundled in res/font/.
 * Used for SECONDARY (inactive) playlist names on the wheel, to contrast
 * with the active item's CalSans SemiBold. The serif italic feel matches
 * the original LuteaArc.kt design intent: "Active = CalSans SemiBold,
 * Inactive = Instrument Serif".
 */
val InstrumentSerifFamily = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Picaflor — display font bundled in res/font/picaflor.otf.
 * Used for BOTH the active and secondary playlist names on the wheel:
 *   - Active item   = UPPERCASE, large, full alpha
 *   - Secondary items = lowercase, smaller, faded alpha
 * Single font, two treatments (size + case + alpha) for clear hierarchy.
 */
val PicaflorFamily = FontFamily(
    Font(R.font.picaflor, FontWeight.Normal),
    Font(R.font.picaflor, FontWeight.SemiBold),
    Font(R.font.picaflor, FontWeight.Bold),
)

/**
 * Jun — display font bundled in res/font/jun_regular.otf.
 * Used for the MAIN (active) playlist label on the wheel — supports both
 * upper and lowercase glyphs (unlike Picaflor which is uppercase-only).
 * Rendered UPPERCASE at 60sp for prominence.
 */
val JunFamily = FontFamily(
    Font(R.font.jun_regular, FontWeight.Normal),
)

/**
 * Nyght Serif — by Maksym Kobuzan, SIL Open Font License 1.1.
 * Coral's secondary text font. Used for SECONDARY (inactive) playlist
 * labels on the wheel. Lowercase, 40sp, faded alpha — pairs with CalSans
 * as the main font, exactly like Coral's pairing.
 *
 * License: OFL 1.1 (commercial use OK, no attribution required in-app).
 * License file: res/font/nyght_serif_license.txt
 */
val NyghtSerifFamily = FontFamily(
    Font(R.font.nyght_serif_regular, FontWeight.Normal),
    Font(R.font.nyght_serif_medium, FontWeight.Medium),
    Font(R.font.nyght_serif_bold, FontWeight.Bold),
    Font(R.font.nyght_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Per-character blur reveal — port of codrops/ScrollBlurTypography Effect 1.
 *
 * Normal mode (reverse=false): chars start blurred+invisible, animate to sharp+visible.
 * Reverse mode (reverse=true): chars start sharp+visible, animate to blurred+invisible.
 *   Used for S1's vanish effect — same blur mechanic but played backward.
 *
 * @param reverse  if true, plays the animation in reverse (vanish instead of reveal)
 */
@Composable
fun BlurRevealText(
    text: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    perCharDurationMillis: Int = 600,
    staggerMillis: Int = 50,
    maxBlurDp: Dp = 10.dp,
    color: Color = Color.White,
    fontSize: Int = 24,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
    textAlign: TextAlign = TextAlign.Center,
    lineHeight: Int? = null,
    reverse: Boolean = false,
) {
    var started by remember(text, reverse) { mutableStateOf(false) }
    LaunchedEffect(text, reverse) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        started = true
    }

    val totalDurationMs = (text.length - 1) * staggerMillis + perCharDurationMillis

    // Whole-row blur progress.
    // Normal: 0 (max blur) → 1 (no blur)
    // Reverse: 1 (no blur) → 0 (max blur)
    val blurProgress by animateFloatAsState(
        targetValue = if (started) (if (reverse) 0f else 1f) else (if (reverse) 1f else 0f),
        animationSpec = tween(totalDurationMs, easing = LinearEasing),
        label = "row_blur",
    )
    val currentBlurPx = with(LocalDensity.current) {
        (maxBlurDp * (1f - blurProgress)).coerceAtLeast(0.dp).toPx()
    }

    val style = TextStyle(
        color = color,
        fontSize = fontSize.sp,
        fontFamily = CalSansFamily,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing.sp,
        textAlign = textAlign,
    ).let { if (lineHeight != null) it.copy(lineHeight = lineHeight.sp) else it }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(align = Alignment.Center)
            .graphicsLayer {
                if (started && currentBlurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = RenderEffect.createBlurEffect(
                        currentBlurPx,
                        currentBlurPx,
                        Shader.TileMode.DECAL,
                    ).asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
            },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        text.forEachIndexed { index, char ->
            val charStartDelay = delayMillis + index * staggerMillis
            var charStarted by remember(text, index, reverse) { mutableStateOf(false) }
            LaunchedEffect(text, index, reverse) {
                if (charStartDelay > 0) delay(charStartDelay.toLong())
                charStarted = true
            }

            // Per-char alpha.
            // Normal: 0 → 1 (appear)
            // Reverse: 1 → 0 (vanish)
            val charAlpha by animateFloatAsState(
                targetValue = if (charStarted) (if (reverse) 0f else 1f) else (if (reverse) 1f else 0f),
                animationSpec = tween(perCharDurationMillis, easing = LinearEasing),
                label = "char_${index}_alpha",
            )

            Text(
                text = char.toString(),
                style = style,
                modifier = Modifier.alpha(charAlpha),
            )
        }
    }
}
