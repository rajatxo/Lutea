package com.rajatxo.lutea.ui.util

/**
 * Lutea's tab definitions — redesigned nav bar.
 *
 * Three tabs matching the reference design:
 *   Home → quick picks / recently played
 *   Search → search screen
 *   Library → songs + playlists
 */
enum class LuteaTab(val label: String) {
    Home("Home"),
    Search("Search"),
    Library("Library")
}
