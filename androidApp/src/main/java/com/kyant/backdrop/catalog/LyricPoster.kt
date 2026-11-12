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
        nickname: String?
    ): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val palette = cover?.let { CoverPalette.extract(it, 4) }.orEmpty()
        val dominant = palette.firstOrNull() ?: Color.rgb(38, 44, 68)

        // ---- background: blurred cover + aurora lights from the cover palette ----
        canvas.drawColor(Color.rgb(12, 12, 16))
        if (cover != null) {
            // Cheap, strong blur: downscale hard, then upscale with bilinear filtering.
            val tiny = Bitmap.createScaledBitmap(cover, 24, 24, true)
            val wash = Bitmap.createScaledBitmap(tiny, W, H, true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(wash, 0f, 0f, paint)
        } else {
            canvas.drawColor(dominant)
        }
        drawAurora(canvas, palette.ifEmpty { listOf(dominant) })
        // Dark scrim so white text stays legible over any cover.
        canvas.drawColor(Color.argb(150, 8, 8, 12))

        // ---- top-right account chip ----
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
        }
        if (avatar != null || !nickname.isNullOrBlank()) {
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
        val lyric = line?.takeIf { it.isNotBlank() } ?: songName
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            isFakeBoldText = true
        }
        val lyricLayout = StaticLayout.Builder
            .obtain(lyric, 0, lyric.length, lyricPaint, W - 220)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(18f, 1f)
            .setMaxLines(4)
            .build()
        val translationLayout = translation?.takeIf { it.isNotBlank() }?.let { t ->
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
        canvas.drawText(ellipsize(songName, titlePaint, maxTextW), textX, cardTop + 96, titlePaint)
        canvas.drawText(ellipsize(artist, subPaint, maxTextW), textX, cardTop + 156, subPaint)
        if (album.isNotBlank() && album != "单曲") {
            canvas.drawText(ellipsize(album, subPaint, maxTextW), textX, cardTop + 204, subPaint)
        }

        // ---- brand line ----
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 235, 235, 245)
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("音乐 · 网易云歌词海报", W / 2f, cardBottom + 76f, brandPaint)
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
