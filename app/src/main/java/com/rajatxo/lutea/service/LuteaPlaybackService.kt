package com.rajatxo.lutea.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.common.MediaMetadata
import com.rajatxo.lutea.MainActivity
import android.util.Log

/**
 * LuteaPlaybackService — Media3 MediaSessionService.
 *
 * Follows Mochy's pattern:
 *  1. startForeground() BEFORE ExoPlayer creation (Android 14+ requires this)
 *  2. setMediaNotificationProvider() so Media3 can push lock-screen notifications
 *  3. onPlayerError() handler — logs + skips to next (never crashes)
 *  4. Proper cleanup in onDestroy()
 */
class LuteaPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "LuteaPlaybackService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lutea_playback"
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // ★ 1. Foreground notification FIRST — before ExoPlayer.
        //    On Android 14+, foregroundServiceType="mediaPlayback" requires
        //    startForeground() within 5s of service start. Without this, the
        //    MediaController.Builder().buildAsync() future throws
        //    ForegroundServiceStartNotAllowedException → app crash.
        createNotificationChannel()
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Lutea")
                .setContentText("Ready to play")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed (may be background restriction)", e)
            // Don't crash — the service will still work, just no foreground notification
        }

        // ★ 2. Create ExoPlayer with proper audio attributes
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.volume = 1f

        // ★ 3. Add player error handler — logs + skips to next.
        //    Without this, PlaybackException from ExoPlayer propagates and crashes.
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                // Skip to next media item instead of crashing
                try {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to skip after error", e)
                    player.stop()
                }
            }
        })

        // ★ 4. Session activity (tap notification → opens app)
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        // ★ 5. Media notification provider — so Media3 can push lock-screen
        //    / Bluetooth transport controls. Without this, the session exists
        //    but has no notification UI.
        try {
            setMediaNotificationProvider(
                DefaultMediaNotificationProvider.Builder(this).build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "setMediaNotificationProvider failed", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lutea Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
