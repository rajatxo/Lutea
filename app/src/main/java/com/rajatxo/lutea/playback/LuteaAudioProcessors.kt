package com.rajatxo.lutea.playback

import androidx.media3.common.util.UnstableApi

/**
 * Singleton holder for the audio processors wired into LuteaPlaybackService's
 * custom AudioSink.
 *
 * Pattern adapted from SpatialFlow's AudioPlaybackService — they keep processor
 * instances as private fields on the service and expose them via getters.
 * Lutea uses MediaController (IPC) for player control, so the service can't
 * expose processors directly to the controller. Instead, both sides reference
 * this singleton object — the service reads from it when building the AudioSink,
 * the controller writes to it when the user toggles 8D / moves sliders.
 *
 * This works because MediaSessionService runs in the same process as the
 * UI (Lutea doesn't run the service in a separate process), so the singleton
 * state is shared.
 */
@UnstableApi
object LuteaAudioProcessors {
    val spatial8D: Spatial8DProcessor = Spatial8DProcessor().also {
        // Defaults: disabled, 0.20 Hz, width 0.75 (matches SpatialFlow's apulsator defaults)
        it.set8DEnabled(false)
        it.setOrbitHz(0.20f)
        it.setSpatialWidth(0.75f)
    }
}
