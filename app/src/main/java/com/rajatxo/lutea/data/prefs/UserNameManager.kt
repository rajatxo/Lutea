package com.rajatxo.lutea.data.prefs

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * UserNameManager — persists the user's display name set during onboarding.
 *
 * Mirrors PlaylistStyleManager's pattern:
 *  - SharedPreferences("lutea_prefs") with key "user_name"
 *  - Compose-reactive State<T> for reading from Composables
 *  - init(context) called once in LuteaApplication.onCreate()
 *  - setName(name, context) called when the user completes onboarding
 *  - current getter for one-shot reads
 *
 * Default value: "Friend" (matches NameCardScreen's fallback).
 */
object UserNameManager {
    private const val PREFS_NAME = "lutea_prefs"
    private const val KEY = "user_name"

    private val _state = mutableStateOf("Friend")
    val state: State<String> = _state

    fun init(context: Context) {
        _state.value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, "Friend") ?: "Friend"
    }

    fun setName(name: String, context: Context) {
        _state.value = name.ifBlank { "Friend" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, _state.value).apply()
    }

    val current: String get() = _state.value
}
