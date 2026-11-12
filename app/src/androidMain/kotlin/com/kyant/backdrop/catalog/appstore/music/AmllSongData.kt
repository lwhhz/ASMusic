package com.kyant.backdrop.catalog.appstore.music

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Data the AMLL WebView page needs: the TTML lyric (CC0 amll-ttml-db, any mirror), an
 * LRCLIB line-level fallback, and the album cover as a data URL — the page runs from
 * `file://` and WebGL refuses to upload cross-origin bitmaps, so the cover is inlined.
 *
 * Lookup order for lyrics:
 *  1. `{neteaseId}.ttml` from the configured mirror (`%p` = dir, `%s` = file stem)
 *  2. `metadata/raw-lyrics-index.jsonl` → by Netease id, then fuzzy title + artist
 *  3. `raw-lyrics/{file}.ttml`
 *  4. Netease yrc lines (fallback, pushed from Kotlin)
 *  5. LRCLIB line-level lyrics, only when Netease returned nothing
 */
internal object AmllSongData {

    const val DEFAULT_SOURCE = "https://amlldb.bikonoo.com/%p/%s.ttml"

    /** Fallback hosts, used when the configured mirror does not answer. */
    private val FALLBACK_ROOTS = listOf(
        "https://cdn.jsdelivr.net/gh/amll-dev/amll-ttml-db@main",
        "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main",
    )

    private const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val INDEX_TTL_MS = 7L * 24 * 60 * 60 * 1000

    private fun ttmlDir(ctx: Context) = File(ctx.cacheDir, "amll_ttml").apply { mkdirs() }

    private fun coverDir(ctx: Context) = File(ctx.cacheDir, "amll_cover").apply { mkdirs() }

    private fun fetch(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MusicApp-AMLL")
        }
        try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun firstNonEmpty(urls: List<String>): ByteArray? {
        for (url in urls) {
            val bytes = fetch(url)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }
        return null
    }

    private fun rootOf(template: String): String =
        template.substringBefore("/%p").ifEmpty { "https://amlldb.bikonoo.com" }

    /** The configured template plus the built-in mirrors, in order. */
    private fun songUrls(template: String, dir: String, stem: String): List<String> {
        val urls = mutableListOf<String>()
        if (template.contains("%p") && template.contains("%s")) {
            urls += template.replace("%p", dir).replace("%s", stem)
        } else if (template.isNotBlank()) {
            urls += template.trimEnd('/') + "/$dir/$stem.ttml"
        }
        for (root in FALLBACK_ROOTS) urls += "$root/$dir/$stem.ttml"
        return urls.distinct()
    }

    private fun indexUrls(template: String): List<String> =
        (listOf(rootOf(template)) + FALLBACK_ROOTS)
            .map { "$it/metadata/raw-lyrics-index.jsonl" }
            .distinct()

    /** TTML text for a song, or null when nothing is available. */
    fun ttml(
        ctx: Context,
        songId: Long,
        title: String,
        artist: String,
        enabled: Boolean,
        template: String
    ): String? {
        if (!enabled) return null
        val dir = ttmlDir(ctx)
        val hit = File(dir, "$songId.ttml")
        if (hit.isFile && hit.length() > 0L) {
            return runCatching { hit.readText() }.getOrNull()
        }
        val miss = File(dir, "$songId.miss")
        if (miss.isFile && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS) {
            return null
        }
        var bytes = firstNonEmpty(songUrls(template, "ncm-lyrics", songId.toString()))
        if (bytes == null || bytes.isEmpty()) {
            val rawFile = indexLookup(ctx, songId, title, artist, template)
            if (rawFile != null) {
                val stem = rawFile.removeSuffix(".ttml")
                bytes = firstNonEmpty(songUrls(template, "raw-lyrics", stem))
            }
        }
        if (bytes == null || bytes.isEmpty()) {
            runCatching { miss.writeText("") }
            runCatching { miss.setLastModified(System.currentTimeMillis()) }
            return null
        }
        runCatching { hit.writeBytes(bytes) }
            .onSuccess { runCatching { miss.delete() } }
        return runCatching { hit.readText() }.getOrNull()
    }

    // ---------------------------------------------------------------- index lookup

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun indexFile(ctx: Context, template: String): File? {
        val file = File(ttmlDir(ctx), "raw-lyrics-index.jsonl")
        val fresh = file.isFile && file.length() > 0L &&
            System.currentTimeMillis() - file.lastModified() < INDEX_TTL_MS
        if (fresh) return file
        val bytes = firstNonEmpty(indexUrls(template))
        if (bytes != null && bytes.isNotEmpty()) {
            runCatching { file.writeBytes(bytes) }
            return file
        }
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    private fun parseEntry(line: String): Triple<String, String, String>? = runCatching {
        val json = JSONObject(line)
        val raw = json.optString("rawLyricFile")
        if (raw.isEmpty()) return null
        val meta = json.optJSONArray("metadata") ?: return null
        var name = ""
        var artist = ""
        for (i in 0 until meta.length()) {
            val pair = meta.optJSONArray(i) ?: continue
            val key = pair.optString(0)
            val values = pair.optJSONArray(1) ?: continue
            val first = if (values.length() > 0) values.optString(0) else ""
            when (key) {
                "musicName" -> name = first
                "artists" -> artist = first
            }
        }
        if (name.isEmpty()) null else Triple(name, artist, raw)
    }.getOrNull()

    private fun fileOf(json: String): String? = runCatching {
        JSONObject(json).optString("rawLyricFile").takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun indexLookup(
        ctx: Context,
        songId: Long,
        title: String,
        artist: String,
        template: String
    ): String? {
        val file = indexFile(ctx, template) ?: return null
        val idToken = "\"ncmMusicId\",[\"$songId\"]"
        val wantTitle = normalize(title)
        val wantArtist = normalize(artist)
        var idHit: String? = null
        var nameHit: String? = null
        var nameHitScore = 0
        runCatching {
            file.bufferedReader().useLines { seq ->
                for (line in seq) {
                    if (idHit == null && line.contains(idToken)) idHit = fileOf(line)
                    if (wantTitle.isNotEmpty() && nameHitScore < 2 &&
                        line.contains(wantTitle, ignoreCase = true)
                    ) {
                        val entry = parseEntry(line) ?: continue
                        if (normalize(entry.first) != wantTitle) continue
                        val score =
                            if (wantArtist.isNotEmpty() && normalize(entry.second).contains(wantArtist)) 2
                            else 1
                        if (score > nameHitScore) {
                            nameHitScore = score
                            nameHit = entry.third
                        }
                    }
                }
            }
        }
        return idHit ?: nameHit
    }

    // -------------------------------------------------------------------- LRCLIB

    /**
     * Line-level lyrics from LRCLIB (free, huge catalogue) — used only when Netease
     * returned no lyric at all. Returns the same simple JSON shape as [linesJson].
     */
    fun lrclibLines(
        ctx: Context,
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): String? {
        if (title.isBlank()) return null
        val cache = File(ttmlDir(ctx), "lrclib_" + Integer.toHexString("$title|$artist".hashCode()) + ".json")
        if (cache.isFile && cache.length() > 0L) {
            return runCatching { cache.readText() }.getOrNull()
        }
        val encoded = runCatching {
            "track_name=" + URLEncoder.encode(title, "UTF-8") +
                "&artist_name=" + URLEncoder.encode(artist, "UTF-8") +
                (if (album.isNotBlank()) "&album_name=" + URLEncoder.encode(album, "UTF-8") else "") +
                (if (durationMs > 0) "&duration=" + (durationMs / 1000) else "")
        }.getOrNull() ?: return null
        val bytes = fetch("https://lrclib.net/api/get?$encoded")
            ?: fetch("https://lrclib.net/api/search?q=" + URLEncoder.encode("$title $artist", "UTF-8"))
            ?: return null
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
        val synced = runCatching {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                (0 until arr.length())
                    .mapNotNull { i -> arr.optJSONObject(i)?.optString("syncedLyrics") }
                    .firstOrNull { it.isNotBlank() }
            } else {
                JSONObject(trimmed).optString("syncedLyrics").takeIf { it.isNotBlank() }
            }
        }.getOrNull() ?: return null
        val json = lrcToJson(synced) ?: return null
        runCatching { cache.writeText(json) }
        return json
    }

    /** `[mm:ss.xx]line` -> `[{"t":ms,"text":"line"}]`. */
    private fun lrcToJson(lrc: String): String? {
        val tag = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        val out = StringBuilder("[")
        var first = true
        for (raw in lrc.lineSequence()) {
            val match = tag.find(raw) ?: continue
            val text = raw.substring(match.range.last + 1).trim()
            if (text.isEmpty()) continue
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3]
            val millis = if (fraction.isEmpty()) 0L else {
                val padded = fraction.padEnd(3, '0').take(3)
                padded.toLongOrNull() ?: 0L
            }
            if (!first) out.append(',')
            first = false
            out.append('{').append("\"t\":").append(minutes * 60_000 + seconds * 1_000 + millis)
            out.append(",\"text\":").append(quote(text)).append('}')
        }
        out.append(']')
        return if (first) null else out.toString()
    }

    // ---------------------------------------------------------------------- cover

    /** Album cover as a `data:` URL, cached on disk; null when it cannot be loaded. */
    fun coverDataUrl(ctx: Context, url: String?): String? {
        if (url.isNullOrBlank()) return null
        val file = File(coverDir(ctx), "${Integer.toHexString(url.hashCode())}.img")
        if (file.isFile && file.length() > 0L) return encode(file)
        val bytes = fetch(url)
        if (bytes == null || bytes.isEmpty()) return null
        runCatching { file.writeBytes(bytes) }
        return encode(file)
    }

    private fun encode(file: File): String? = runCatching {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null
        val png = bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val type = if (png) "image/png" else "image/jpeg"
        "data:$type;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    // --------------------------------------------------------------------- lyrics

    /**
     * Netease lyric lines as JSON for the page. Word timings are kept when the yrc
     * parser produced them, so AMLL still gets karaoke timing without a TTML file.
     */
    fun linesJson(lines: List<LyricLine>): String {
        val sb = StringBuilder("[")
        lines.forEachIndexed { index, line ->
            if (index > 0) sb.append(',')
            sb.append('{')
            sb.append("\"t\":").append(line.timeMs)
            sb.append(",\"text\":").append(quote(line.text))
            line.translation?.let { sb.append(",\"tr\":").append(quote(it)) }
            if (line.words.isNotEmpty()) {
                sb.append(",\"w\":[")
                line.words.forEachIndexed { wi, word ->
                    if (wi > 0) sb.append(',')
                    sb.append('{')
                    sb.append("\"t\":").append(word.timeMs)
                    sb.append(",\"d\":").append(word.durationMs)
                    sb.append(",\"s\":").append(quote(word.text))
                    sb.append('}')
                }
                sb.append(']')
            }
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else ->
                    if (ch < ' ') sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
