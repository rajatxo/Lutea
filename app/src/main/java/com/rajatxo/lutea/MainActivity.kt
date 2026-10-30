package com.rajatxo.lutea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.LuteaRoot
import com.rajatxo.lutea.ui.theme.LuteaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Law 2: Manual edge-to-edge (NOT enableEdgeToEdge) ──
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ── Law 10: BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE ──
        // The ONLY systemBarsBehavior that doesn't steal gestures from the app.
        // Even though Lutea keeps system bars visible (transparent), setting this
        // ensures swipe-from-edge doesn't conflict with the playlist wheel drag.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // ── Law 6: System gesture exclusion zone ──
        // The playlist wheel uses vertical drag on the LEFT half of the screen.
        // Without this exclusion, the system back-gesture (swipe from left edge)
        // steals the drag and the wheel doesn't rotate. We exclude a vertical strip
        // on the left side so the wheel drag takes priority there.
        setSystemGestureExclusion()

        setContent {
            LuteaTheme {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    PlaybackConnection.connect(context)
                }
                DisposableEffect(Unit) {
                    onDispose {
                        PlaybackConnection.disconnect()
                    }
                }
                LuteaRoot()
            }
        }
    }

    /**
     * Law 6: Exclude the left ~25% of the screen from system gesture interception.
     *
     * On Android 10+, the system reserves a vertical strip at the left/right edges
     * for back-gesture. When the user drags vertically in that zone (e.g., to rotate
     * the playlist wheel), the system steals the gesture. By declaring an exclusion
     * rect, we tell the system "this area is ours, don't intercept."
     *
     * We exclude the left quarter of the screen height-wise (top to bottom), which
     * is where the wheel's arcs and drag zone live.
     */
    private fun setSystemGestureExclusion() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.post {
                val width = window.decorView.width
                val height = window.decorView.height
                // Exclude left 35% of the screen, full height — that's where both arcs live.
                val exclusionWidth = (width * 0.35f).toInt()
                val exclusionRects = listOf(
                    android.graphics.Rect(0, 0, exclusionWidth, height)
                )
                window.setSystemGestureExclusionRects(exclusionRects)
            }
        }
    }
}
