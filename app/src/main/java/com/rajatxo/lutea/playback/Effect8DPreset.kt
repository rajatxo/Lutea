package com.rajatxo.lutea.playback

/**
 * 8D intensity presets.
 *
 * BALANCED is the default — matches SpatialFlow's fixed values exactly
 * (0.20 Hz rotation, 0.75 apulsator width). SpatialFlow has no slider for
 * these, the values are baked in.
 *
 * LOW and HIGH are Lutea's additions — give the user a quick way to dial
 * the effect down or up without dragging sliders.
 */
enum class Effect8DPreset(
    val orbitHz: Float,
    val width: Float,
    val label: String,
) {
    /** Subtle rotation, barely noticeable. Good for background listening. */
    LOW(orbitHz = 0.10f, width = 0.55f, label = "Low"),

    /** SpatialFlow's exact values — the default. */
    BALANCED(orbitHz = 0.20f, width = 0.75f, label = "Balanced"),

    /** Fast rotation with full L/R sweep. Most dramatic effect. */
    HIGH(orbitHz = 0.35f, width = 1.00f, label = "High"),
}
