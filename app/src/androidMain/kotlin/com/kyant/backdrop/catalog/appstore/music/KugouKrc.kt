package com.kyant.backdrop.catalog.appstore.music

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.Inflater

/**
 * 酷狗 KRC（逐字歌词）实验性数据源。
 *
 * 仅作为「歌词源」里的**可选**项，默认关闭：调用的是酷狗公开的歌词检索/下载接口，
 * 数据版权归酷狗及权利人所有，只在本机缓存，不随安装包分发。
 *
 * 解码流程（与 lyricify-lyrics-provider-rs 一致）：
 *   base64 → 去掉 4 字节 "krc1" 头 → 16 字节密钥循环异或 → zlib inflate
 *   → 去掉 BOM（无 BOM 时去掉首字节）→ UTF-8
 */
object KugouKrc {

    private val XOR_KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
    )

    private fun fetch(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Referer", "https://www.kugou.com/")
        }
        try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun cacheDir(ctx: Context) = File(ctx.cacheDir, "kugou_krc").apply { mkdirs() }

    /** 搜索结果里的一条歌词条目。 */
    private data class Candidate(val id: String, val accessKey: String, val duration: Long)

    private fun search(title: String, artist: String, durationMs: Long): Candidate? {
        if (title.isBlank()) return null
        val keyword = if (artist.isBlank()) title else "$title $artist"
        val url = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi" +
            "&keyword=" + URLEncoder.encode(keyword, "UTF-8") +
            "&duration=" + durationMs +
            "&hash="
        val body = fetch(url) ?: return null
        val candidates = runCatching { JSONObject(body).optJSONArray("candidates") }.getOrNull() ?: return null
        if (candidates.length() == 0) return null
        // 时长越接近越可信
        var best: Candidate? = null
        var bestDelta = Long.MAX_VALUE
        for (i in 0 until candidates.length()) {
            val item = candidates.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val key = item.optString("accesskey")
            val dur = item.optLong("duration", 0L)
            if (id.isEmpty() || key.isEmpty()) continue
            val delta = kotlin.math.abs(dur - durationMs)
            if (delta < bestDelta) {
                bestDelta = delta
                best = Candidate(id, key, dur)
            }
        }
        return best
    }

    private fun decrypt(content: String): String? = runCatching {
        val clean = content.filterNot { it.isWhitespace() }
        val raw = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
        if (raw.size <= 4) return null
        val body = ByteArray(raw.size - 4)
        for (i in body.indices) {
            body[i] = (raw[i + 4].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        }
        val inflater = Inflater()
        inflater.setInput(body)
        val out = ByteArrayOutputStream(body.size * 4)
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        val bytes = out.toByteArray()
        val skip = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else if (bytes.isNotEmpty()) 1 else 0
        String(bytes, skip, bytes.size - skip, Charsets.UTF_8)
    }.getOrNull()

    private val lineTag = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val wordTag = Regex("""<(\d+),(\d+),\d+>([^<]*)""")

    /** KRC 文本 → 逐字歌词行。 */
    private fun parse(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val m = lineTag.find(line) ?: continue
            val start = m.groupValues[1].toLongOrNull() ?: continue
            val content = m.groupValues[3]
            val words = mutableListOf<LyricWord>()
            for (w in wordTag.findAll(content)) {
                val offset = w.groupValues[1].toLongOrNull() ?: continue
                val duration = w.groupValues[2].toLongOrNull() ?: continue
                val chunk = w.groupValues[3]
                if (chunk.isEmpty()) continue
                words += LyricWord(start + offset, duration, chunk)
            }
            if (words.isEmpty()) continue
            lines += LyricLine(
                timeMs = start,
                text = words.joinToString("") { it.text },
                words = words
            )
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * 按元数据取逐字歌词；[translations] 提供网易云翻译，按时间就近合并进来。
     * 返回空列表表示未命中。
     */
    fun lyrics(
        ctx: Context,
        title: String,
        artist: String,
        durationMs: Long,
        translations: List<LyricLine>,
    ): List<LyricLine> {
        if (title.isBlank()) return emptyList()
        val cache = File(cacheDir(ctx), Integer.toHexString("$title|$artist".hashCode()) + ".krc.txt")
        val text = if (cache.isFile && cache.length() > 0L) {
            runCatching { cache.readText() }.getOrNull()
        } else {
            val candidate = search(title, artist, durationMs) ?: return emptyList()
            val body = fetch(
                "https://lyrics.kugou.com/download?ver=1&client=pc" +
                    "&id=${candidate.id}&accesskey=${candidate.accessKey}&fmt=krc&charset=utf8"
            ) ?: return emptyList()
            val content = runCatching { JSONObject(body).optString("content") }.getOrNull()
                ?: return emptyList()
            decrypt(content)?.also { runCatching { cache.writeText(it) } }
        } ?: return emptyList()

        val parsed = parse(text)
        if (parsed.isEmpty()) return emptyList()
        if (translations.isEmpty()) return parsed
        return parsed.map { line ->
            if (!line.translation.isNullOrBlank()) line
            else {
                val nearest = translations.minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                if (nearest != null && kotlin.math.abs(nearest.timeMs - line.timeMs) <= 900L) {
                    line.copy(translation = nearest.translation)
                } else line
            }
        }
    }
}
