package com.rajatxo.lutea.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.icons.LuteaIcons

/**
 * SettingsScreen — minimal dev-info page for the testing APK.
 *
 * Style inspired by Coral's settings: dark background, glass cards, social links,
 * "Made with ❤️ in India" badge. Lets testers reach the developer if they share
 * the APK with someone who wants to contact Rajat.
 *
 * Universal Layout Laws:
 *  - Law 1: statusBarsPadding() — no system bar overlap
 *  - Law 2/3: edge-to-edge (already set in MainActivity + theme)
 *  - Law 7: fillMaxSize + verticalScroll — works on every phone size
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = "0.1.116"
    val versionCode = "216"

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0A0A),
                        Color(0xFF0A0A0A),
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar: back button + "Settings" title ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "←",
                        color = Color.White,
                        fontSize = 20.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── App logo / name ──
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF4B400),
                                Color(0xFFB8860B),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "L",
                    color = Color.Black,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Lutea",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily,
            )

            Text(
                text = "v$versionName ($versionCode)",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(32.dp))

            // ── Developer info card ──
            GlassCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Developer",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Rajat",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CalSansFamily,
                    )
                    Spacer(Modifier.height(20.dp))

                    // Social links row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        SocialButton(
                            label = "Telegram",
                            color = Color(0xFF229ED9),
                            onClick = { openUrl("https://t.me/rajat_xo") },
                        )
                        SocialButton(
                            label = "GitHub",
                            color = Color.White,
                            onClick = { openUrl("https://github.com/rajatxo") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Support group card ──
            GlassCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Need help?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CalSansFamily,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Join our Telegram support group",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF229ED9))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { openUrl("https://t.me/coralmusicsupport") },
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "Join Support Group",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = CalSansFamily,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Made with love in India ──
            Text(
                text = "Made with ❤️ in India",
                color = Color(0xFFFF9933),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Lutea is a closed-source project.\nFor inquiries, contact @rajat_xo on Telegram.",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * Glass card — semi-transparent with border, mimicking the kyant glass look
 * without needing a LayerBackdrop (so it works standalone on the settings page).
 */
@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
    ) {
        content()
    }
}

/**
 * Round social button with label below.
 */
@Composable
private fun SocialButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.first().toString(),
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = CalSansFamily,
        )
    }
}
