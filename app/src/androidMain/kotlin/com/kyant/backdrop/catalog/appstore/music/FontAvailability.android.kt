package com.kyant.backdrop.catalog.appstore.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val FAMILY_NAMES = listOf(
    "sans-serif",
    "sans-serif-light",
    "sans-serif-condensed",
    "sans-serif-medium",
    "serif",
    "monospace",
    "casual",
    "cursive",
)

/** Renders [text] with [family] and returns the pixels, used to detect fallbacks. */
private fun render(family: String, text: String): ByteArray {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(family, Typeface.NORMAL)
        textSize = 56f
    }
    val width = (paint.measureText(text).toInt() + 8).coerceIn(16, 512)
    val bitmap = Bitmap.createBitmap(width, 72, Bitmap.Config.ALPHA_8)
    Canvas(bitmap).drawText(text, 4f, 56f, paint)
    val pixels = ByteArray(width * 72)
    bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(pixels))
    bitmap.recycle()
    return pixels
}

@Composable
actual fun rememberAvailableFontFamilies(): Set<Int> {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            // A family that is not installed renders exactly like the default one, so a
            // pixel comparison is the reliable way to tell them apart.
            val sample = "Ag漢字"
            val base = render("sans-serif", sample)
            FAMILY_NAMES.indices.filter { index ->
                index == 0 || runCatching {
                    !render(FAMILY_NAMES[index], sample).contentEquals(base)
                }.getOrDefault(true)
            }.toSet()
        }.getOrDefault(FAMILY_NAMES.indices.toSet())
    }
}
