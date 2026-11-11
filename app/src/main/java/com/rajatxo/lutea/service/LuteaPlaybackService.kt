package com.rajatxo.lutea.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SonicAudioProcessor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rajatxo.lutea.MainActivity
import com.rajatxo.lutea.playback.LuteaAudioProcessors

/**
 * LuteaPlaybackService — direct port of SpatialFlow's AudioPlaybackService
 * ExoPlayer setup pattern (github.com/MythicalSHUB/SpatialFlow, Apache-2.0).
 *
 * Key changes from the previous simple setup:
 *  - Custom DefaultRenderersFactory that overrides buildAudioRenderers to
 *    inject a custom DefaultAudioSink with audio processors:
 *      SonicAudioProcessor — Media3 built-in, handles speed/pitch
 *      Spatial8DProcessor — real-time pan LFO (the 8D effect)
 *    This is THE architectural change. Without a custom AudioSink, audio
 *    effects have to be done via FFmpeg pre-processing (slow, low quality).
 *  - Aggressive DefaultLoadControl (250ms playback start threshold) so
 *    song swaps feel instant — matches SpatialFlow's loadControl config.
 *  - DefaultRenderersFactory with setEnableDecoderFallback(true) +
 *    EXTENSION_RENDERER_MODE_ON (kept from before).
 *
 * PlaybackConnection toggles 8D by writing to the LuteaAudioProcessors
 * singleton — both the service and the controller share the same instance
 * (single process, MediaController IPC).
 */
@UnstableApi
class LuteaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // SpatialFlow's buildExoPlayer pattern: custom renderers factory that
        // injects SonicAudioProcessor + Spatial8DProcessor into the AudioSink.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                context: android.content.Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>,
            ) {
                val customAudioSink = DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(
                        SonicAudioProcessor(),
                        LuteaAudioProcessors.spatial8D,
                    ))
                    .build()
                super.buildAudioRenderers(
                    context, extensionRendererMode, mediaCodecSelector,
                    enableDecoderFallback, customAudioSink, eventHandler, eventListener, out,
                )
            }
        }
        renderersFactory
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // Aggressive LoadControl — matches SpatialFlow's config.
        // Playback starts after only 250ms of buffer (vs ExoPlayer's default 2500ms).
        // 50s max buffer prevents starvation on long files.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50000,  // minBufferMs
                60000,  // maxBufferMs
                250,    // bufferForPlaybackMs — start playback almost immediately
                1500,   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.volume = 1f

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
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
}
