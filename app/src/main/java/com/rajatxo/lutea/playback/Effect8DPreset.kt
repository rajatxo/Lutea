package com.rajatxo.lutea.playback

/**
 * 8D intensity presets.
 *
 * Slider scale is 1..25, matching the numbers around the sphere.
 * BALANCED is at position 13 (default). When 8D is on, the two "13" labels
 * (top + bottom) highlight gold, and the top/down arrows turn gold.
 *
 * Conversion from slider value to internal rps/width is via [sliderToRps] /
 * [sliderToWidth]. At slider=13, values match SpatialFlow's defaults
 * (0.20 Hz, 0.75 width).
 */
enum class Effect8DPreset(
    val orbitSlider: Int,
    val ambienceSlider: Int,
    val label: String,
) {
    LOW(orbitSlider = 7, ambienceSlider = 7, label = "Low"),
    BALANCED(orbitSlider = 13, ambienceSlider = 13, label = "Balanced"),
    HIGH(orbitSlider = 19, ambienceSlider = 19, label = "High"),
}

/** Slider 1..25 → rps. 13=0.20, 1=0.02, 25=0.40. Linear. */
fun sliderToRps(slider: Int): Float {
    val s = slider.coerceIn(1, 25)
    return 0.02f + (s - 1) * 0.01583f
}

/** Slider 1..25 → width. 13=0.75, 1=0.05, 25=1.00. Linear. */
fun sliderToWidth(slider: Int): Float {
    val s = slider.coerceIn(1, 25)
    return 0.05f + (s - 1) * 0.03958f
}

