package com.kyant.backdrop.catalog

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.kyant.backdrop.catalog.appstore.music.PosterBg
import com.kyant.backdrop.catalog.appstore.music.PosterConfig

/**
 * Renders the lyric share poster (1080×1920).
 *
 * The background is built **from the actual cover bitmap**: the artwork itself is
 * blurred into a base wash, and up to four colors sampled from it (see
 * [CoverPalette]) drive soft radial aurora lights on top. A dark scrim keeps the
 * lyric readable. Center: the current lyric line + translation. Bottom: song info
 * card. Top-right: account avatar + nickname.
 */
object LyricPoster {

    private const val W = 1080
    private const val H = 1920

    fun render(
        songName: String,
        artist: String,
        album: String,
        line: String?,
        translation: String?,
        cover: Bitmap?,
        avatar: Bitmap?,
        nickname: String?,
        posterConfig: PosterConfig = PosterConfig()
    ): Bitmap {
        val W = posterConfig.ratio.widthPx
        val H = posterConfig.ratio.heightPx
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val palette = cover?.let { CoverPalette.extract(it, 4) }.orEmpty()
        val dominant = palette.firstOrNull() ?: Color.rgb(38, 44, 68)

        // ---- background: blurred cover + aurora lights from the cover palette ----
        canvas.drawColor(Color.rgb(12, 12, 16))
        if (cover != null && posterConfig.bg == PosterBg.Blur) {
            // Same treatment as the immersive static background: centre-crop the cover
            // to the poster aspect (never stretched) and blur it by downscale+upscale.
            val targetAspect = W.toFloat() / H
            val srcW: Int
            val srcH: Int
            if (cover.width.toFloat() / cover.height > targetAspect) {
                srcH = cover.height
                srcW = (srcH * targetAspect).toInt().coerceAtLeast(1)
            } else {
                srcW = cover.width
                srcH = (srcW / targetAspect).toInt().coerceAtLeast(1)
            }
            val cropped = Bitmap.createBitmap(
                cover,
                (cover.width - srcW) / 2,
                (cover.height - srcH) / 2,
                srcW,
                srcH
            )
            val wash = gaussianBlur(cropped, W, H, posterConfig.blurLevel)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(wash, 0f, 0f, paint)
        } else if (cover != null && posterConfig.bg == PosterBg.Solid) {
            canvas.drawColor(dominant)
        } else {
            canvas.drawColor(dominant)
            drawAurora(canvas, palette.ifEmpty { listOf(dominant) })
        }
        // Dark scrim so white text stays legible over any cover.
        canvas.drawColor(Color.argb(150, 8, 8, 12))

        // ---- top-right account chip ----
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
        }
        if (posterConfig.showAvatar && (avatar != null || !nickname.isNullOrBlank())) {
            val nick = nickname?.takeIf { it.isNotBlank() } ?: "网易云用户"
            val textW = namePaint.measureText(nick)
            if (avatar != null) {
                val size = 76
                val left = W - 64 - textW - 20 - size
                val top = 96f
                val clip = android.graphics.Path().apply {
                    addCircle(left + size / 2f, top + size / 2f, size / 2f, android.graphics.Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(
                    Bitmap.createScaledBitmap(avatar, size, size, true),
                    left, top,
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                canvas.restore()
                canvas.drawText(nick, left + size + 20, top + size / 2f + 12, namePaint)
            } else {
                canvas.drawText(nick, W - 64 - textW, 132f, namePaint)
            }
        }

        // ---- center lyric ----
        // Hard-cap the line count here: StaticLayout.setMaxLines alone does not drop
        // explicit "\n" breaks, which is why "1 行" still rendered four.
        val lyric = (line?.takeIf { it.isNotBlank() } ?: songName)
            .split("\n")
            .take(posterConfig.lyricLines.coerceIn(1, 4))
            .joinToString("\n")
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f * posterConfig.fontScale
            isFakeBoldText = true
        }
        val lyricLayout = StaticLayout.Builder
            .obtain(lyric, 0, lyric.length, lyricPaint, W - 220)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(18f, 1f)
            .setMaxLines(posterConfig.lyricLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val translationLayout = translation?.takeIf { it.isNotBlank() && posterConfig.showTranslation }?.let { t ->
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(215, 255, 255, 255)
                textSize = 38f
            }
            StaticLayout.Builder
                .obtain(t, 0, t.length, p, W - 260)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(10f, 1f)
                .setMaxLines(3)
                .build()
        }
        val textBlockH = lyricLayout.height + (translationLayout?.height?.plus(44) ?: 0)
        val lyricTop = (H * 0.42f) - textBlockH / 2f
        canvas.save()
        canvas.translate(110f, lyricTop)
        lyricLayout.draw(canvas)
        canvas.restore()
        if (translationLayout != null) {
            canvas.save()
            canvas.translate(130f, lyricTop + lyricLayout.height + 44)
            translationLayout.draw(canvas)
            canvas.restore()
        }

        // ---- bottom song info card ----
        if (!posterConfig.showInfoCard) {
            if (posterConfig.watermark.isNotBlank()) {
                val wp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, 235, 235, 245)
                    textSize = 30f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(posterConfig.watermark, W / 2f, H - 90f, wp)
            }
            return out
        }
        val cardLeft = 72f
        val cardRight = W - 72f
        val cardBottom = H - 210f
        val cardTop = cardBottom - 240f
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 22, 22, 28)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 44f, 44f, cardPaint)
        // Accent hairline tinted with the dominant cover color.
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, Color.red(dominant), Color.green(dominant), Color.blue(dominant))
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 44f, 44f, accent)

        val coverSize = 160
        if (cover != null) {
            val left = cardLeft + 40
            val top = cardTop + 40
            val path = android.graphics.Path().apply {
                addRoundRect(RectF(left, top, left + coverSize, top + coverSize), 26f, 26f, android.graphics.Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(
                Bitmap.createScaledBitmap(cover, coverSize, coverSize, true),
                left, top,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            canvas.restore()
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            isFakeBoldText = true
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 235, 235, 245)
            textSize = 32f
        }
        val textX = cardLeft + 40 + coverSize + 32
        val maxTextW = cardRight - 32 - textX
        // Vertically centre the text block against the cover instead of using fixed
        // baselines (the old ones sat visibly low in the card).
        val showAlbumLine = posterConfig.showAlbum && album.isNotBlank() && album != "单曲"
        val titleH = titlePaint.fontMetrics.let { it.descent - it.ascent }
        val subH = subPaint.fontMetrics.let { it.descent - it.ascent }
        val blockH = titleH + 16f + subH + if (showAlbumLine) 12f + subH else 0f
        val coverTop = cardTop + 40
        var baseline = coverTop + (coverSize - blockH) / 2f - titlePaint.fontMetrics.ascent
        canvas.drawText(ellipsize(songName, titlePaint, maxTextW), textX, baseline, titlePaint)
        baseline += titleH + 16f - titlePaint.fontMetrics.descent + subPaint.fontMetrics.ascent + subH * .35f
        canvas.drawText(ellipsize(artist, subPaint, maxTextW), textX, baseline, subPaint)
        if (showAlbumLine) {
            baseline += subH * 1.15f
            canvas.drawText(ellipsize(album, subPaint, maxTextW), textX, baseline, subPaint)
        }

        // ---- brand line ----
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 235, 235, 245)
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        if (posterConfig.watermark.isNotBlank()) {
            canvas.drawText(posterConfig.watermark, W / 2f, cardBottom + 76f, brandPaint)
        }
        return out
    }


    /**
     * Gaussian approximation: downscale, run three box-blur passes (a 3-pass box blur is
     * very close to a true Gaussian), then upscale. Cheap enough to run per share.
     */
    private fun gaussianBlur(src: Bitmap, w: Int, h: Int, level: Int): Bitmap {
        val factor = when (level) { 1 -> 0.09f; 3 -> 0.035f; else -> 0.055f }
        val sw = (w * factor).toInt().coerceAtLeast(8)
        val sh = (h * factor).toInt().coerceAtLeast(8)
        var small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val radius = when (level) { 1 -> 2; 3 -> 6; else -> 4 }
        repeat(3) { small = boxBlur(small, radius) }
        return Bitmap.createScaledBitmap(small, w, h, true)
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val outPx = IntArray(w * h)
        // horizontal
        for (y in 0 until h) {
            var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
            val row = y * w
            for (x in -radius..radius) {
                val c = inPx[row + x.coerceIn(0, w - 1)]
                sumA += c ushr 24 and 0xFF; sumR += c ushr 16 and 0xFF
                sumG += c ushr 8 and 0xFF; sumB += c and 0xFF
            }
            val div = radius * 2 + 1
            for (x in 0 until w) {
                tmp[row + x] = (sumA / div shl 24) or (sumR / div shl 16) or (sumG / div shl 8) or (sumB / div)
                val inC = inPx[row + (x + radius + 1).coerceIn(0, w - 1)]
                val outC = inPx[row + (x - radius).coerceIn(0, w - 1)]
                sumA += (inC ushr 24 and 0xFF) - (outC ushr 24 and 0xFF)
                sumR += (inC ushr 16 and 0xFF) - (outC ushr 16 and 0xFF)
                sumG += (inC ushr 8 and 0xFF) - (outC ushr 8 and 0xFF)
                sumB += (inC and 0xFF) - (outC and 0xFF)
            }
        }
        // vertical
        for (x in 0 until w) {
            var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
            for (y in -radius..radius) {
                val c = tmp[y.coerceIn(0, h - 1) * w + x]
                sumA += c ushr 24 and 0xFF; sumR += c ushr 16 and 0xFF
                sumG += c ushr 8 and 0xFF; sumB += c and 0xFF
            }
            val div = radius * 2 + 1
            for (y in 0 until h) {
                outPx[y * w + x] = (sumA / div shl 24) or (sumR / div shl 16) or (sumG / div shl 8) or (sumB / div)
                val inC = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                val outC = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                sumA += (inC ushr 24 and 0xFF) - (outC ushr 24 and 0xFF)
                sumR += (inC ushr 16 and 0xFF) - (outC ushr 16 and 0xFF)
                sumG += (inC ushr 8 and 0xFF) - (outC ushr 8 and 0xFF)
                sumB += (inC and 0xFF) - (outC and 0xFF)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    /** Soft radial lights at scattered positions, all colors sampled from the cover. */
    private fun drawAurora(canvas: Canvas, palette: List<Int>) {
        val anchors = listOf(
            floatArrayOf(W * .22f, H * .20f, W * .95f),
            floatArrayOf(W * .86f, H * .36f, W * .85f),
            floatArrayOf(W * .18f, H * .62f, W * .9f),
            floatArrayOf(W * .72f, H * .80f, W * .95f)
        )
        palette.take(4).forEachIndexed { i, argb ->
            val (cx, cy, radius) = anchors[i % anchors.size]
            val c = Color.argb(
                150,
                Color.red(argb), Color.green(argb), Color.blue(argb)
            )
            val shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(c, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawCircle(cx, cy, radius, paint)
        }
        // A subtle diagonal sheen from the dominant color for extra depth.
        val top = palette.first()
        val sheen = LinearGradient(
            0f, 0f, W.toFloat(), H.toFloat(),
            Color.argb(60, Color.red(top), Color.green(top), Color.blue(top)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = sheen })
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && paint.measureText("$t…") > maxWidth) {
            t = t.dropLast(1)
        }
        return "$t…"
    }
}
