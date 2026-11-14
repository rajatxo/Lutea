package com.rajatxo.lutea.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Lumen — the two tone palette used by the playlist wheel and the 8D effects.
 *
 * Mint is the "main" role: the selected playlist name, the glow ball inside the
 * glass shell. Ice is the "secondary" role: inactive playlist names, the glass
 * plates, the light rays. The pair is deliberately kept in one place, because
 * the same two colours are reused in several screens.
 */
object LumenPalette {
    /** #D4FF9C — main text, active state. */
    val Mint = Color(0xFFD4FF9C)

    /** #9EF9FF — secondary text, inactive state, light. */
    val Ice = Color(0xFF9EF9FF)
}
