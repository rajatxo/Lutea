package com.rajatxo.lutea.ui.screens

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.components.BlurRevealText
import com.rajatxo.lutea.ui.foundation.LuteaBackdrop
import com.rajatxo.lutea.ui.foundation.LuteaBackground
import kotlinx.coroutines.delay

/**
 * NameCardScreen — the first content screen of Lutea.
 *
 * Reveal sequence (per Lutea-Inspiration Opal video analysis):
 *
 *   1. Logo (top-center, 120dp) — visible from t=0
 *
 *   2. Sentence 1 reveals at the LOWER position (where Sentence 2 will eventually sit).
 *      → BlurRevealText, delay 200ms, perChar 300ms, stagger 30ms (finishes ~1430ms)
 *
 *   3. Sentence 1 MOVES UP to its final upper position.
 *      → animateDpAsState on the bottom padding, spring animation, ~500ms
 *
 *   4. Sentence 2 reveals in the now-vacated lower position.
 *      → BlurRevealText, starts after Sentence 1 finishes moving up
 *
 *   5. Continue button appears MID-WAY through Sentence 2's reveal (~50% progress).
 *      → fadeIn + slideIn from below
 *
 *   6. Name capsule appears AFTER Sentence 2 fully reveals.
 *      → fadeIn + slideIn from below — positioned in the SAME Column as the Continue
 *        button with a Spacer between them, so they never overlap.
 *
 *   7. User taps capsule → keyboard opens → ALL elements shift UP together as ONE unit
 *      via Modifier.imePadding() on the root Box.
 *
 *   8. User taps back → keyboard closes → ALL elements shift DOWN together.
 *
 * Universe-fitting rule:
 *   Uses LuteaBackground (teal-tinted gradient + radial glow).
 *
 * Layout structure:
 *   Root Box (imePadding — moves everything as one unit)
 *     └─ Column (TopCenter)
 *         ├─ Logo (120dp)
 *         ├─ Spacer
 *         ├─ Sentence 1 (animates from lower position to upper position)
 *         ├─ Sentence 2 (reveals in the lower position)
 *         ├─ Spacer
 *         ├─ Name capsule (appears after sentence 2)
 *         ├─ Spacer (16dp)
 *         └─ Continue button (appears mid-way through sentence 2)
 */
@Composable
fun NameCardScreen(
    onComplete: (displayName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LuteaBackground(modifier = modifier) {
        NameCardContent(onComplete = onComplete)
    }
}

@Composable
private fun NameCardContent(
    onComplete: (displayName: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // ── Animation state flags ──────────────────────────────────────────────
    var sentence1Dismissing by remember { mutableStateOf(false) }
    var showContinueButton by remember { mutableStateOf(false) }
    var showCapsule by remember { mutableStateOf(false) }

    // ── POST-CONTINUE state ────────────────────────────────────────────────
    // Sequential text reveal + dissolve, just like S1→S2 on the first page.
    var greetingShown by remember { mutableStateOf(false) }
    var oldSentencesDissolving by remember { mutableStateOf(false) }

    // Sequential post-Continue flags:
    //   greetingShown → "Hi, [name]" reveals
    //   greetingDismissing → "Hi, [name]" dissolves, "But to find your music" reveals
    //   line2Dismissing → "But to find your music" dissolves, "I need some permission" reveals
    //   showPermissionCards → permission cards + Continue appear (below center)
    var greetingDismissing by remember { mutableStateOf(false) }
    var line2Dismissing by remember { mutableStateOf(false) }
    var showPermissionCards by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1500)
        sentence1Dismissing = true
        showContinueButton = true
        delay(1100)
        showCapsule = true
    }

    LaunchedEffect(showCapsule) {
        if (showCapsule && !greetingShown) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    // ── POST-CONTINUE sequential animation ────────────────────────────────
    // Each line reveals, then dissolves when the next line appears.
    // Same BlurRevealText reverse mechanic as S1→S2.
    LaunchedEffect(greetingShown) {
        if (greetingShown) {
            keyboard?.hide()
            oldSentencesDissolving = true
            delay(2600)
            greetingDismissing = true
            delay(2100)
            line2Dismissing = true
            delay(1600)
            showPermissionCards = true
        }
    }

    // ── S1's smoke-dissolve exit — blend into air and vanish ──
    // Per user: 'when it move it just dissolve..mean its blur spread away and expand..
    // just like a smoke blend into air..like it dissolve..blend and vanish and so smoothly'
    //
    // Mechanics (all CONCURRENT, perfectly synchronized):
    //   1. Y offset: 17dp (overlap S2) → -40dp (moves UP gently, not too far — smoke drifts)
    //   2. Scale: 1.0 → 1.3 (text EXPANDS outward in all directions like smoke spreading)
    //   3. Letter-spacing: 0 → 12sp (characters DRIFT APART as they dissolve)
    //   4. Blur: 0 → 32dp (heavy blur so the text loses all shape — becomes smoke)
    //   5. Alpha: 1 → 0 (fades to invisible as it spreads)
    //
    // All animations use the SAME 1500ms duration + LinearOutSlowInEasing for perfect sync.
    // LinearOutSlowInEasing: starts fast (smoke initial puff), slows to settle (gentle fade).
    // 1500ms is slow enough to feel like smoke, not a snap.
    val S1_EXIT_DURATION = 1500
    val S1_EXIT_EASING = androidx.compose.animation.core.LinearOutSlowInEasing

    // S1 vanish offset: starts at 0 (natural position, where the reveal left it),
    // moves UP to -40dp during the vanish. Gently drifts up like smoke.
    val sentence1YOffset: Dp by animateDpAsState(
        targetValue = if (sentence1Dismissing) (-40).dp else 0.dp,
        animationSpec = tween(durationMillis = S1_EXIT_DURATION, easing = S1_EXIT_EASING),
        label = "sentence1_y",
    )
    val sentence1Alpha by animateFloatAsState(
        targetValue = if (sentence1Dismissing) 0f else 1f,
        animationSpec = tween(durationMillis = S1_EXIT_DURATION, easing = S1_EXIT_EASING),
        label = "sentence1_alpha",
    )
    val sentence1BlurProgress by animateFloatAsState(
        targetValue = if (sentence1Dismissing) 1f else 0f,
        animationSpec = tween(durationMillis = S1_EXIT_DURATION, easing = S1_EXIT_EASING),
        label = "sentence1_blur_progress",
    )
    // Heavy blur (32dp max) so text loses all shape and becomes pure smoke.
    val sentence1BlurPx = with(LocalDensity.current) {
        (32.dp * sentence1BlurProgress).toPx()
    }
    // Scale up so the text expands outward (smoke spreading).
    val sentence1Scale by animateFloatAsState(
        targetValue = if (sentence1Dismissing) 1.3f else 1f,
        animationSpec = tween(durationMillis = S1_EXIT_DURATION, easing = S1_EXIT_EASING),
        label = "sentence1_scale",
    )
    // Letter-spacing grows so characters drift apart as they dissolve.
    val sentence1LetterSpacing by animateFloatAsState(
        targetValue = if (sentence1Dismissing) 12f else 0f,
        animationSpec = tween(durationMillis = S1_EXIT_DURATION, easing = S1_EXIT_EASING),
        label = "sentence1_letter_spacing",
    )

    // ── POST-CONTINUE dissolve: old sentences (S1 + S2) move UP + fade ──
    // When user taps Continue, BOTH remaining sentences dissolve UP together.
    // They fade from white → dark grey (alpha 0.15) + move up + blur.
    val POST_CONTINUE_DURATION = 1200
    val oldSentencesYOffset by animateDpAsState(
        targetValue = if (oldSentencesDissolving) (-80).dp else 0.dp,
        animationSpec = tween(durationMillis = POST_CONTINUE_DURATION, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "old_sentences_y",
    )
    val oldSentencesAlpha by animateFloatAsState(
        targetValue = if (oldSentencesDissolving) 0f else 1f,  // was 0.15f — fully invisible, no ghost
        animationSpec = tween(durationMillis = POST_CONTINUE_DURATION, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "old_sentences_alpha",
    )

    // ── POST-CONTINUE line offsets (same move-up pattern as S1→S2) ──
    val greetingYOffset: Dp by animateDpAsState(
        targetValue = if (greetingDismissing) (-40).dp else 0.dp,
        animationSpec = tween(durationMillis = 800, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "greeting_y",
    )
    val line2YOffset: Dp by animateDpAsState(
        targetValue = if (line2Dismissing) (-40).dp else 0.dp,
        animationSpec = tween(durationMillis = 800, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "line2_y",
    )
    val oldSentencesBlurProgress by animateFloatAsState(
        targetValue = if (oldSentencesDissolving) 1f else 0f,
        animationSpec = tween(durationMillis = POST_CONTINUE_DURATION, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "old_sentences_blur",
    )
    val oldSentencesBlurPx = with(LocalDensity.current) {
        (20.dp * oldSentencesBlurProgress).toPx()
    }

    // ── OUTER: LuteaBackdrop wraps the 3-layer kyant structure ──
    // Layer 1: Flower image (visible background)
    // Layer 2: Transparent capture layer (NOT clickable, NOT scrollable) — kyant captures here
    // Layer 3: Foreground content (uses drawBackdrop on individual elements to blur Layer 1)
    //
    // Per Coral's SettingsScreen clever approach (avoids the kyant crash):
    //   layerBackdrop is OUTSIDE the scroll (kyant criteria met).
    //   Only drawBackdrop is on individual elements (capsule, button).
    //   Kyant content lives on TOP of everything — it's the last child of LuteaBackdrop.
    LuteaBackdrop(
        background = {
            // Flower image + soft glow — both rendered twice by LuteaBackdrop (visible + captured)
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.flower_top),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Soft glowing pulse on the flower (loops forever) — captured by kyant too
                val glowTransition = rememberInfiniteTransition(label = "flower_glow")
                val glowAlpha by glowTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "glow_alpha",
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val glowWidthPx = constraints.maxWidth.toFloat()
                    val glowHeightPx = constraints.maxHeight.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFD89E).copy(alpha = glowAlpha),
                                        Color(0xFFFFD89E).copy(alpha = glowAlpha * 0.8f),
                                        Color(0xFFFF9A8B).copy(alpha = glowAlpha * 0.5f),
                                        Color(0xFFFF9A8B).copy(alpha = glowAlpha * 0.25f),
                                        Color(0xFFFF9A8B).copy(alpha = glowAlpha * 0.1f),
                                        Color.Transparent,
                                    ),
                                    center = androidx.compose.ui.geometry.Offset(
                                        x = glowWidthPx * 0.52f,
                                        y = glowHeightPx * 0.20f,
                                    ),
                                    radius = glowWidthPx * 0.45f,
                                ),
                            ),
                    )
                }
            }
        },
    ) { backdrop ->

        // ── Back button (kyant frosted glass, top-left) — visible only on permission page ──
        // Lets the user go back and change their name.
        if (showPermissionCards) {
            val backShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .padding(
                        start = 24.dp,
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    )
                    .size(40.dp)
                    .clip(backShape)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { backShape },
                        effects = {
                            vibrancy()
                            colorControls(
                                brightness = 0.05f,
                                contrast = 1f,
                                saturation = 1.4f,
                            )
                            blur(16f.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = 0.3f))
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // Reset to name entry phase
                        showPermissionCards = false
                        line2Dismissing = false
                        greetingDismissing = false
                        greetingShown = false
                        oldSentencesDissolving = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Chevron-left icon (Lucide vector drawable)
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // ── Layer B: Content — STATIC position (NO imePadding) ──────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 24.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // ── OLD SENTENCES (S1 + S2) — dissolve UP when Continue is tapped ──
                // Wrap in a Box that applies the dissolve offset + alpha + blur to BOTH.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = oldSentencesYOffset)
                        .alpha(oldSentencesAlpha)
                        .graphicsLayer {
                            if (oldSentencesBlurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = RenderEffect.createBlurEffect(
                                    oldSentencesBlurPx, oldSentencesBlurPx,
                                    Shader.TileMode.DECAL,
                                ).asComposeRenderEffect()
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // ── Sentence 1 — "I'll help you find your sound." ───────
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BlurRevealText(
                                text = "I'll help you find your sound.",
                                color = Color.White,
                                fontSize = 22,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0f,
                                delayMillis = 200,
                                perCharDurationMillis = 300,
                                staggerMillis = 30,
                                maxBlurDp = 10.dp,
                                reverse = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (sentence1Dismissing) 0f else 1f),
                            )
                            BlurRevealText(
                                text = "I'll help you find your sound.",
                                color = Color.White,
                                fontSize = 22,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0f,
                                delayMillis = 1500,
                                perCharDurationMillis = 400,
                                staggerMillis = 30,
                                maxBlurDp = 10.dp,
                                reverse = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (sentence1Dismissing) 1f else 0f)
                                    .offset(y = sentence1YOffset),
                            )
                        }

                        // ── Sentence 2 — "First, what's your name?" ─────────
                        Spacer(Modifier.height(17.dp))
                        BlurRevealText(
                            text = "First, what's your name?",
                            color = Color.White,
                            fontSize = 24,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0f,
                            delayMillis = 1500,
                            perCharDurationMillis = 300,
                            staggerMillis = 30,
                            maxBlurDp = 10.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ── Capsule + Continue button (hidden when greeting is shown) ──
                if (!greetingShown) {
                    Spacer(Modifier.height(12.dp))
                    AnimatedVisibility(
                        visible = showCapsule,
                        enter = fadeIn(tween(400)) +
                            slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(400),
                            ),
                    ) {
                        PillTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            placeholder = "Your name",
                            focusRequester = focusRequester,
                            imeAction = ImeAction.Done,
                            onImeAction = { keyboard?.hide() },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    AnimatedVisibility(
                        visible = showContinueButton,
                        enter = fadeIn(tween(400)) +
                            slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(400),
                            ),
                    ) {
                        val canContinue = name.isNotBlank()
                        ContinueButton(
                            enabled = canContinue,
                            onClick = { greetingShown = true },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ── POST-CONTINUE: sequential text reveal + dissolve ──
                // Same BlurRevealText reveal/vanish pattern as S1→S2.
                // Each line reveals, then dissolves (reverse=true) when the next shows.
                //
                // When permission cards are shown, switch to BOTTOM-aligned layout
                // so they don't cover the flower.

                if (!showPermissionCards) {
                    // ── Text-only phase: sequential reveal + move-up + vanish ──
                    // Same pattern as S1→S2: each line has a reveal instance (reverse=false)
                    // and a vanish instance (reverse=true + offset moves UP).
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // ── Greeting: "Hi, [name]" — reveal then move-up + vanish ──
                        if (greetingShown) {
                            // A) Reveal instance — visible until greeting starts dismissing
                            BlurRevealText(
                                text = "Hi, ${name.ifBlank { "Friend" }}",
                                color = Color(0xFF80E5FF),
                                fontSize = 27,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0f,
                                delayMillis = 400,
                                perCharDurationMillis = 300,
                                staggerMillis = 30,
                                maxBlurDp = 10.dp,
                                reverse = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (greetingDismissing) 0f else 1f),
                            )
                            // B) Vanish instance — moves UP + reverse blur when line 2 shows
                            if (greetingDismissing) {
                                BlurRevealText(
                                    text = "Hi, ${name.ifBlank { "Friend" }}",
                                    color = Color(0xFF80E5FF),
                                    fontSize = 27,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0f,
                                    delayMillis = 0,
                                    perCharDurationMillis = 400,
                                    staggerMillis = 30,
                                    maxBlurDp = 10.dp,
                                    reverse = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = greetingYOffset),
                                )
                            }
                        }

                        // ── Line 2: "But to find your music," — reveal then move-up + vanish ──
                        if (greetingDismissing) {
                            // A) Reveal instance
                            BlurRevealText(
                                text = "But to find your music,",
                                color = Color.White,
                                fontSize = 22,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0f,
                                delayMillis = 0,
                                perCharDurationMillis = 300,
                                staggerMillis = 30,
                                maxBlurDp = 10.dp,
                                reverse = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (line2Dismissing) 0f else 1f),
                            )
                            // B) Vanish instance — moves UP when line 3 shows
                            if (line2Dismissing) {
                                BlurRevealText(
                                    text = "But to find your music,",
                                    color = Color.White,
                                    fontSize = 22,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0f,
                                    delayMillis = 0,
                                    perCharDurationMillis = 400,
                                    staggerMillis = 30,
                                    maxBlurDp = 10.dp,
                                    reverse = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = line2YOffset),
                                )
                            }
                        }

                        // ── Line 3: "I need some permission." — reveal, stays VISIBLE ──
                        // This line does NOT vanish. It stays visible when the permission
                        // cards appear below it.
                        if (line2Dismissing && !showPermissionCards) {
                            BlurRevealText(
                                text = "I need some permission.",
                                color = Color.White,
                                fontSize = 24,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0f,
                                delayMillis = 0,
                                perCharDurationMillis = 300,
                                staggerMillis = 30,
                                maxBlurDp = 10.dp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // ── PERMISSION PHASE: text moves up with blur + cards blur-reveal ──
                if (showPermissionCards) {
                    // Animate "I need some permission." text: moves UP + blur fades to sharp.
                    val permissionTextOffset by animateDpAsState(
                        targetValue = 0.dp,  // settles at 0 (natural position)
                        animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                        label = "perm_text_offset",
                    )
                    val permissionTextBlurProgress by animateFloatAsState(
                        targetValue = 0f,  // 1=max blur, 0=sharp
                        animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                        label = "perm_text_blur",
                    )
                    val permissionTextBlurPx = with(LocalDensity.current) {
                        (12.dp * permissionTextBlurProgress).toPx()
                    }

                    // Animate cards + button: blur reveals from blurred → sharp.
                    val cardsBlurProgress by animateFloatAsState(
                        targetValue = 0f,  // 1=max blur, 0=sharp
                        animationSpec = tween(durationMillis = 800, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                        label = "cards_blur",
                    )
                    val cardsBlurPx = with(LocalDensity.current) {
                        (16.dp * cardsBlurProgress).toPx()
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    // "I need some permission." — moves up + blur→sharp
                    Text(
                        text = "I need some permission.",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = com.rajatxo.lutea.ui.components.CalSansFamily,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-20).dp + permissionTextOffset)  // starts 20dp high, settles to 0
                            .graphicsLayer {
                                if (permissionTextBlurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = RenderEffect.createBlurEffect(
                                        permissionTextBlurPx, permissionTextBlurPx,
                                        Shader.TileMode.DECAL,
                                    ).asComposeRenderEffect()
                                }
                            },
                    )
                    Spacer(Modifier.height(16.dp))
                    // Permission cards + Continue — blur reveal (blurred → sharp)
                    PermissionScreen(
                        displayName = name.ifBlank { "Friend" },
                        onPermissionsComplete = { onComplete(name.ifBlank { "Friend" }) },
                        backdrop = backdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (cardsBlurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = RenderEffect.createBlurEffect(
                                        cardsBlurPx, cardsBlurPx,
                                        Shader.TileMode.DECAL,
                                    ).asComposeRenderEffect()
                                }
                            },
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────
// Reusable bits
// ────────────────────────────────────────────────────────────────────────────────────

/** Pill-shaped text input — kyant frosted glass via drawBackdrop. */
@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = 0.05f,
                        contrast = 1f,
                        saturation = 1.4f,
                    )
                    blur(16f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.3f))
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(Color(0xFFD4A03A)),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = imeAction,
                keyboardType = KeyboardType.Text,
            ),
            keyboardActions = KeyboardActions(onDone = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(horizontal = 22.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF6E6E73),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
                inner()
            },
        )
    }
}

/** Full-width pill Continue button — kyant frosted glass via drawBackdrop.
 *  Disabled = darker tint over the backdrop.
 *  Enabled  = brighter tint (looks like solid white when name is entered). */
@Composable
private fun ContinueButton(
    enabled: Boolean,
    onClick: () -> Unit,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed && enabled) 0.98f else 1f
    val overlayAlpha = if (enabled) 0.85f else 0.5f
    val overlayColor = if (enabled) Color.White else Color(0xFF3A3A3A)
    val fg = if (enabled) Color.Black else Color(0xFF8E8E93)

    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = if (enabled) 0.4f else 0.05f,
                        contrast = 1f,
                        saturation = 1.4f,
                    )
                    blur(16f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(overlayColor.copy(alpha = overlayAlpha))
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Continue",
            color = fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
