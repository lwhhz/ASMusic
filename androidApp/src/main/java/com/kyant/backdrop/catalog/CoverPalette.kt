package com.kyant.backdrop.catalog

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real cover-art color sampling. The bitmap is downscaled, pixels are quantized
 * into RGB buckets, and the most frequent / most saturated buckets become the
 * palette. Used by the lyric-poster aurora background and by the lyric cards.
 */
object CoverPalette {

    /**
     * Extract up to [count] representative colors (ARGB ints). The first entry is
     * the dominant color; the rest are accents, preferring saturated ones so the
     * aurora / gradients do not collapse into greys.
     */
    fun extract(bitmap: Bitmap, count: Int = 4): List<Int> {
        val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        data class Bucket(var n: Int = 0, var r: Long = 0, var g: Long = 0, var b: Long = 0)
        val buckets = HashMap<Int, Bucket>()
        for (argb in pixels) {
            val a = Color.alpha(argb)
            if (a < 96) continue
            val r = Color.red(argb)
            val g = Color.green(argb)
            val b = Color.blue(argb)
            // Quantize to 32 levels per channel to merge near-identical pixels.
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.n++
            bucket.r += r.toLong()
            bucket.g += g.toLong()
            bucket.b += b.toLong()
        }
        if (buckets.isEmpty()) return emptyList()

        val ranked = buckets.values.map {
            val n = it.n.coerceAtLeast(1)
            val r = (it.r / n).toInt()
            val g = (it.g / n).toInt()
            val b = (it.b / n).toInt()
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0f else (mx - mn).toFloat() / mx
            // Frequency dominates; saturation breaks ties in favour of vivid colors.
            val score = it.n * (0.65f + sat * 0.9f)
            Triple(intArrayOf(r, g, b), score, sat)
        }.sortedByDescending { it.second }

        val picked = ArrayList<Int>()
        for ((rgb, _, sat) in ranked) {
            if (picked.size >= count) break
            // Skip near-duplicates of colors we already have.
            val tooClose = picked.any { existing ->
                val er = Color.red(existing)
                val eg = Color.green(existing)
                val eb = Color.blue(existing)
                abs(er - rgb[0]) + abs(eg - rgb[1]) + abs(eb - rgb[2]) < 90
            }
            if (tooClose) continue
            // Avoid a palette of pure blacks / whites when accents exist.
            if (picked.isNotEmpty() && sat < 0.08f && picked.size < count - 1) continue
            picked.add(Color.rgb(rgb[0], rgb[1], rgb[2]))
        }
        // Always return something usable, even for a monochrome cover.
        if (picked.isEmpty()) {
            val top = ranked.first().first
            picked.add(Color.rgb(top[0], top[1], top[2]))
        }
        // Pad with light / dark derivatives of the dominant color up to [count].
        val base = picked.first()
        val variants = listOf(1.35f, 0.72f, 1.7f, 0.5f)
        var i = 0
        while (picked.size < count && i < variants.size) {
            picked.add(scaleBrightness(base, variants[i]))
            i++
        }
        return picked
    }

    private fun scaleBrightness(argb: Int, factor: Float): Int {
        fun scale(v: Int) = (v * factor).toInt().coerceIn(0, 255)
        return Color.rgb(scale(Color.red(argb)), scale(Color.green(argb)), scale(Color.blue(argb)))
    }
}
