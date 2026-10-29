package com.rajatxo.lutea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.LuteaRoot
import com.rajatxo.lutea.ui.theme.LuteaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
}
