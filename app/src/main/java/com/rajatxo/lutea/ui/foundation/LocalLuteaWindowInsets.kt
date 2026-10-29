package com.rajatxo.lutea.ui.foundation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Universal Layout Law #1: ONE CompositionLocal for ALL insets.
 * Ported from Coral X's LocalCoralXWindowInsets.kt.
 *
 * Computed ONCE at root (in LuteaScaffold), consumed EVERYWHERE.
 * Includes: system bars + nav bar + header height + tab capsule height.
 */
val LocalLuteaWindowInsets = staticCompositionLocalOf<WindowInsets> {
    error("LuteaWindowInsets not provided — wrap your screen in LuteaScaffold")
}
