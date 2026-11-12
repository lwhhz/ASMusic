package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.ui.unit.Density

/**
 * Holds the device's un-scaled density so surfaces that must ignore the user's
 * UI/font scale (lyric cards, immersive lyrics) can restore it.
 */
object DensityDefaults {
    var original: Density = Density(1f, 1f)
}
