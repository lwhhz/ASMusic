package com.kyant.backdrop.catalog.appstore.music

/** Eight discrete UI-size presets, including the 100% default. */
val UiScaleSteps = listOf(0.80f, 0.85f, 0.90f, 0.95f, 1.00f, 1.10f, 1.20f, 1.30f)

/** Snap a raw value to the closest preset (keeps old free-form saved values usable). */
fun snapUiScale(value: Float): Float =
    UiScaleSteps.minByOrNull { kotlin.math.abs(it - value) } ?: 1f
