package com.kyant.backdrop.catalog.appstore.music

import kotlin.math.abs

/** Presets for the Haze top bar: (height, opaque, blur) in dp. */
data class HazePreset(
    val label: String,
    val height: Float,
    val opaque: Float,
    val blur: Float
)

val HazePresets = listOf(
    HazePreset("轻", 180f, 96f, 48f),
    HazePreset("标准", 262f, 150f, 80f),
    HazePreset("重", 340f, 220f, 120f)
)

/** Which preset the current numbers match, if any. */
fun matchedHazePreset(height: Float, opaque: Float, blur: Float): String? =
    HazePresets.firstOrNull {
        abs(it.height - height) < 1f && abs(it.opaque - opaque) < 1f && abs(it.blur - blur) < 1f
    }?.label

/** Height of the song-detail sticky control bar; blur always starts below it. */
const val HazeBarDp = 64f
