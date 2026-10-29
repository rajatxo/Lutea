package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.foundation.LuteaScaffold
import com.rajatxo.lutea.ui.util.LuteaTab

/**
 * QuickPicksScreen — the first page of Lutea after onboarding.
 *
 * Now wrapped in LuteaScaffold (ported from Coral X's CoralXScaffold):
 *  - Header blur (120dp) with profile + title + settings icons
 *  - Bottom: tab capsule + search FAB (both kyant frosted glass)
 *  - Content: placeholder song rows
 */
@Composable
fun QuickPicksScreen(
    modifier: Modifier = Modifier,
) {
    // Default tab is Playlists (per user request: 'make the playlist page default')
    var activeTab by remember { mutableStateOf(LuteaTab.Playlists) }

    LuteaScaffold(
        title = when (activeTab) {
            LuteaTab.QuickPicks -> "Quick Picks"
            LuteaTab.Songs -> "Songs"
            LuteaTab.Playlists -> "Playlists"
        },
        activeTab = activeTab,
        onTabSelected = { activeTab = it },
    ) {
        // Content changes based on the active tab
        when (activeTab) {
            LuteaTab.QuickPicks -> QuickPicksContent()
            LuteaTab.Songs -> SongsContent()
            LuteaTab.Playlists -> PlaylistsContent()
        }
    }
}

// ── Quick Picks content ──
@Composable
private fun QuickPicksContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp)
            .padding(top = 120.dp, bottom = 120.dp),
    ) {
            // Section: Recently Played
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recently Played",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                )
                Text(
                    text = "See all",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Horizontal row of circular thumbnails
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(4) {
                    CircularThumbPlaceholder()
                }
            }

            Spacer(Modifier.height(28.dp))

            // Section: Made for You
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Made for You",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                )
                Text(
                    text = "See all",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(4) {
                    CircularThumbPlaceholder()
                }
            }

            Spacer(Modifier.height(28.dp))

            // Section: Recently Added
            Text(
                text = "Recently Added",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
            )

            Spacer(Modifier.height(16.dp))

            repeat(3) {
                SongRowPlaceholder()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CircularThumbPlaceholder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)),
                    ),
                ),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        )
    }
}

@Composable
private fun SongRowPlaceholder() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)),
                    ),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

// ── Songs content (placeholder for now — will show scanned music later) ──
@Composable
private fun SongsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp)
            .padding(top = 120.dp, bottom = 120.dp),
    ) {
        Text(
            text = "All Songs",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Music scanning will be added next.",
            color = Color(0xFF8E8E93),
            fontSize = 14.sp,
        )
    }
}

// ── Playlists content (placeholder for now — will get the wheel UI later) ──
@Composable
private fun PlaylistsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp)
            .padding(top = 120.dp, bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Playlists",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalSansFamily,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your playlists will appear here.",
            color = Color(0xFF8E8E93),
            fontSize = 14.sp,
        )
    }
}
