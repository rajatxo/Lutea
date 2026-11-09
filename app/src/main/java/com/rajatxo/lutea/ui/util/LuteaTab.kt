package com.rajatxo.lutea.ui.util

/**
 * Lutea's tab definitions — redesigned nav bar.
 *
 * Four tabs:
 *   Home → quick picks / recently played
 *   Search → search screen
 *   Effect → audio effects (EQ, reverb, 8D, speed)
 *   Library → songs + playlists
 */
enum class LuteaTab(val label: String) {
    Home("Home"),
    Search("Search"),
    Effect("Effect"),
    Library("Library")
}
