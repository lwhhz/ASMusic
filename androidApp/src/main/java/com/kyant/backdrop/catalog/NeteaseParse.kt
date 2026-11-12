package com.kyant.backdrop.catalog

import com.kyant.backdrop.catalog.appstore.music.ArtistBrief
import com.kyant.backdrop.catalog.appstore.music.AudioQuality
import com.kyant.backdrop.catalog.appstore.music.CommentItem
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.LyricWord
import com.kyant.backdrop.catalog.appstore.music.Song
import com.kyant.backdrop.catalog.appstore.music.SongQuality
import org.json.JSONArray
import org.json.JSONObject

/** Response shapes shared by the direct client and the proxy client. */

internal fun parseNeteaseSong(j: JSONObject?): Song? {
    j ?: return null
    val id = j.optLong("id", 0L)
    if (id <= 0L) return null
    val name = j.optString("name", "")
    if (name.isBlank()) return null
    // Some endpoints (e.g. /recommend/songs) use short keys: ar / al / dt.
    val artists = j.optJSONArray("artists") ?: j.optJSONArray("ar")
    val artist = if (artists != null && artists.length() > 0) {
        (0 until artists.length())
            .mapNotNull { artists.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
            .joinToString(" / ")
    } else {
        j.optString("artistsNames", "").ifBlank { j.optString("artistName", "") }
    }
    val artistList = if (artists != null) {
        (0 until artists.length()).mapNotNull { index ->
            val a = artists.optJSONObject(index) ?: return@mapNotNull null
            val artistPk = a.optLong("id", 0L)
            val artistName = a.optString("name", "").trim()
            if (artistPk <= 0L || artistName.isBlank()) null
            else ArtistBrief(artistPk, artistName)
        }
    } else emptyList()
    val artistId = artistList.firstOrNull()?.id ?: 0L
    val albumObj = j.optJSONObject("album") ?: j.optJSONObject("al")
    val album = albumObj?.optString("name", "") ?: j.optString("album", "")
    val albumId = albumObj?.optLong("id", 0L) ?: 0L
    val cover = albumObj?.optString("picUrl", "")?.takeIf { it.isNotBlank() }
        ?: j.optString("picUrl", "").takeIf { it.isNotBlank() }
    val duration = j.optLong("duration", j.optLong("dt", 210_000L))
    return Song(
        id = id,
        name = name,
        artist = artist.ifBlank { "未知艺术家" },
        album = album.ifBlank { "单曲" },
        albumId = albumId,
        durationMs = if (duration > 0) duration else 210_000L,
        coverUrl = cover,
        trackNo = j.optInt("no", 1).coerceAtLeast(1),
        genre = j.optString("genre", "流行").ifBlank { "流行" },
        commentCount = j.optInt("commentCount", 0),
        favoriteCount = j.optInt("popularity", 0),
        artistId = artistId,
        artists = artistList
    )
}

internal fun parseLrc(raw: String): List<LyricLine> {
    val pattern = Regex("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?\\](.*)")
    return raw.lines().mapNotNull { line ->
        val m = pattern.find(line) ?: return@mapNotNull null
        val min = m.groupValues[1].toLongOrNull() ?: 0L
        val sec = m.groupValues[2].toLongOrNull() ?: 0L
        val frac = m.groupValues[3]
        val ms = when (frac.length) {
            2 -> (frac.toLongOrNull() ?: 0L) * 10L
            3 -> frac.toLongOrNull() ?: 0L
            else -> 0L
        }
        val text = m.groupValues[4].trim()
        if (text.isEmpty()) null else LyricLine(min * 60_000L + sec * 1_000L + ms, text)
    }
}

/**
 * Karaoke lyric (yrc): `[lineStart,lineDuration](wordStart,wordDur,0)word ...`
 * Every word carries its own timing, which drives the lyric-card sweep.
 */
internal fun parseYrc(raw: String): List<LyricLine> {
    if (raw.isBlank()) return emptyList()
    val lineRe = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
    val wordRe = Regex("\\((\\d+),(\\d+),\\d+\\)([^()]*)")
    val out = ArrayList<LyricLine>()
    raw.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        val m = lineRe.find(line) ?: return@forEach
        val start = m.groupValues[1].toLongOrNull() ?: return@forEach
        val body = m.groupValues[3]
        val words = wordRe.findAll(body).mapNotNull { wm ->
            val text = wm.groupValues[3]
            if (text.isEmpty()) null
            else LyricWord(
                timeMs = wm.groupValues[1].toLongOrNull() ?: 0L,
                durationMs = wm.groupValues[2].toLongOrNull() ?: 0L,
                text = text
            )
        }.toList()
        val text = words.joinToString("") { it.text }
        if (text.isNotBlank()) out.add(LyricLine(start, text, words = words))
    }
    return out
}

/** Attach Netease translations (tlyric) to lines, matching by timestamp. */
internal fun withTranslations(lines: List<LyricLine>, raw: String): List<LyricLine> {
    val translated = parseLrc(raw)
    if (translated.isEmpty()) return lines
    return lines.map { line ->
        val hit = translated.minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
        if (hit != null && kotlin.math.abs(hit.timeMs - line.timeMs) <= 400L) {
            line.copy(translation = hit.text)
        } else line
    }
}

/** Translate the song's l/m/h/sq/hr blocks + privilege.maxbr into UI qualities. */
internal fun parseSongQualities(song: JSONObject?, privilege: JSONObject?): List<SongQuality> {
    val maxBr = privilege?.optInt("maxbr", 0) ?: 0
    // maxBrLevel is the song's ceiling (e.g. jymaster); plLevel is the account's
    // actual playable ceiling (e.g. jyeffect). Using both lets the sheet show the
    // full Netease ladder and mark the levels this account cannot use.
    val maxLevel = privilege?.optString("maxBrLevel", "").orEmpty()
        .ifBlank { privilege?.optString("playMaxBrLevel", "").orEmpty() }
    val playLevel = privilege?.optString("plLevel", "").orEmpty()
    val playRank = if (playLevel.isNotBlank() && playLevel != "none") {
        AudioQuality.from(playLevel).rank
    } else 0
    val defs = listOf(
        "l" to AudioQuality.Standard,
        "m" to AudioQuality.Higher,
        "h" to AudioQuality.Exhigh,
        "sq" to AudioQuality.Lossless,
        "hr" to AudioQuality.Hires
    )
    val objectRank = defs.mapNotNull { (key, quality) ->
        val q = song?.optJSONObject(key) ?: return@mapNotNull null
        if (q.optInt("br", 0) > 0 || q.optLong("size", 0L) > 0L) quality.rank else null
    }.maxOrNull() ?: 0
    val declaredRank = if (maxLevel.isNotBlank() && maxLevel != "none") {
        AudioQuality.from(maxLevel).rank
    } else 0
    val songMaxRank = maxOf(declaredRank, objectRank, playRank)

    if (songMaxRank > 0 && (declaredRank > 0 || playRank > 0)) {
        return AudioQuality.entries
            .filter { it.rank <= songMaxRank }
            .map { quality ->
                val key = when (quality) {
                    AudioQuality.Standard -> "l"
                    AudioQuality.Higher -> "m"
                    AudioQuality.Exhigh -> "h"
                    AudioQuality.Lossless -> "sq"
                    AudioQuality.Hires -> "hr"
                    else -> null
                }
                val obj = key?.let { song?.optJSONObject(it) }
                val br = obj?.optInt("br", 0) ?: 0
                val size = obj?.optLong("size", 0L) ?: 0L
                SongQuality(
                    quality = quality,
                    bitrate = br,
                    sizeBytes = size,
                    playable = playRank <= 0 || quality.rank <= playRank
                )
            }
    }

    return defs.mapNotNull { (key, quality) ->
        val q = song?.optJSONObject(key) ?: return@mapNotNull null
        val br = q.optInt("br", 0)
        val size = q.optLong("size", 0L)
        if (br <= 0 && size <= 0L) return@mapNotNull null
        SongQuality(
            quality = quality,
            bitrate = br,
            sizeBytes = size,
            playable = when {
                playRank > 0 -> quality.rank <= playRank
                maxBr <= 0 || br <= 0 -> true
                else -> br <= maxBr
            }
        )
    }.sortedBy { it.rank }
}

/** Parse hot + newest comments, de-duplicated by comment id. */
internal fun parseComments(
    body: JSONObject?,
    includeHot: Boolean = true,
    includeLatest: Boolean = true
): List<CommentItem>? {
    body ?: return null
    val out = LinkedHashMap<Long, CommentItem>()
    fun add(arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val id = c.optLong("commentId", i.toLong())
            val ms = c.optLong("time", 0L)
            out[id] = CommentItem(
                id = id,
                user = c.optJSONObject("user")?.optString("nickname", "用户") ?: "用户",
                avatarUrl = c.optJSONObject("user")?.optString("avatarUrl", "")
                    ?.takeIf { it.isNotBlank() },
                content = c.optString("content", ""),
                liked = c.optInt("likedCount", 0),
                time = commentTimeText(ms),
                timeMs = ms
            )
        }
    }
    if (includeHot) add(body.optJSONArray("hotComments"))
    if (includeLatest) add(body.optJSONArray("comments"))
    return out.values.toList().ifEmpty { null }
}

/** Human-readable relative time for a Netease comment timestamp. */
internal fun commentTimeText(ms: Long): String {
    if (ms <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = (now - ms).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 2 * 86_400_000L -> "昨天"
        diff < 30 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(ms))
    }
}

/** Keep only name=value pairs from a raw Set-Cookie blob and drop duplicates. */
fun cleanCookie(raw: String): String {
    val skip = setOf("max-age", "expires", "path", "domain", "secure", "httponly", "samesite")
    val seen = LinkedHashMap<String, String>()
    raw.split(';').forEach { part ->
        val p = part.trim()
        val i = p.indexOf('=')
        if (i <= 0) return@forEach
        val k = p.substring(0, i).trim()
        val v = p.substring(i + 1).trim()
        if (k.lowercase() in skip || v.isEmpty()) return@forEach
        seen[k] = "$k=$v"
    }
    return seen.values.joinToString("; ")
}
