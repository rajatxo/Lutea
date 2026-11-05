package com.rajatxo.lutea.data.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

enum class PlaylistStyle(val label: String) {
    WHEEL_SIMPLER("Wheel Simpler"),
    WHEEL_ADVANCED("Wheel Advanced"),
}

object PlaylistStyleManager {
    private const val PREFS_NAME = "lutea_prefs"
    private const val KEY = "playlist_style"

    private val _state = mutableStateOf(PlaylistStyle.WHEEL_ADVANCED)
    val state: State<PlaylistStyle> = _state

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY, PlaylistStyle.WHEEL_ADVANCED.name)
        _state.value = PlaylistStyle.entries.firstOrNull { it.name == name } ?: PlaylistStyle.WHEEL_ADVANCED
    }

    fun setStyle(style: PlaylistStyle, context: Context) {
        _state.value = style
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, style.name)
            .apply()
    }

    val current: PlaylistStyle get() = _state.value
}
