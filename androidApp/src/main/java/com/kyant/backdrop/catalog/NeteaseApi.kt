package com.kyant.backdrop.catalog

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kyant.backdrop.catalog.appstore.music.AlbumBrief
import com.kyant.backdrop.catalog.appstore.music.ArtistBrief
import com.kyant.backdrop.catalog.appstore.music.ArtistStat
import com.kyant.backdrop.catalog.appstore.music.CommentItem
import com.kyant.backdrop.catalog.appstore.music.Fixtures
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.PlayStats
import com.kyant.backdrop.catalog.appstore.music.Playlist
import com.kyant.backdrop.catalog.appstore.music.PlaylistKind
import com.kyant.backdrop.catalog.appstore.music.QrStatus
import com.kyant.backdrop.catalog.appstore.music.SearchResults
import com.kyant.backdrop.catalog.appstore.music.Song
import com.kyant.backdrop.catalog.appstore.music.SongQuality
import com.kyant.backdrop.catalog.appstore.music.SongStream
import com.kyant.backdrop.catalog.appstore.music.SongSocial
import com.kyant.backdrop.catalog.appstore.music.SongStat
import com.kyant.backdrop.catalog.appstore.music.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Thin, defensive client for a self-hosted NeteaseCloudMusicApi proxy. */
class NeteaseApi(private val config: AppConfig) : MusicApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun get(path: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(config.serverUrl + path)
            config.cookie?.let { builder.header("Cookie", it) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) null
                else JSONObject(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private suspend fun getBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
    }

    override suspend fun image(url: String): ImageBitmap? {
        val bytes = getBytes(url) ?: return null
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    // ---- QR login ----

    override suspend fun qrKey(): String? = get("/login/qr/key")
        ?.optJSONObject("data")
        ?.optString("unikey")
        ?.takeIf { it.isNotBlank() }

    override suspend fun qrImage(key: String): ImageBitmap? {
        val j = get("/login/qr/create?key=$key&qrimg=true") ?: return null
        val dataUrl = j.optJSONObject("data")?.optString("qrimg").orEmpty()
        val payload = dataUrl.substringAfter("base64,", "")
        if (payload.isBlank()) return null
        return runCatching {
            val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    override suspend fun qrCheck(key: String): QrStatus {
        val j = get("/login/qr/check?key=$key") ?: return QrStatus(800, "网络不可用", null)
        return QrStatus(
            j.optInt("code", 800),
            j.optString("message", ""),
            j.optString("cookie", "").takeIf { it.isNotBlank() }
        )
    }

    // ---- catalogue ----

    override suspend fun search(keyword: String): SearchResults? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val j = get("/search?keywords=$encoded&type=1&limit=20") ?: return null
        val arr = j.optJSONObject("result")?.optJSONArray("songs") ?: return null
        val songs = (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }
        if (songs.isEmpty()) return null
        // The legacy search payload omits artwork; batch-fetch it from song detail.
        val missing = songs.filter { it.coverUrl == null }
        val filled = if (missing.isEmpty()) songs else {
            val ids = missing.joinToString(",") { it.id.toString() }
            val detail = get("/song/detail?ids=$ids")?.optJSONArray("songs")
            val byId = HashMap<Long, Song>()
            if (detail != null) {
                for (i in 0 until detail.length()) {
                    parseNeteaseSong(detail.optJSONObject(i))?.let { byId[it.id] = it }
                }
            }
            songs.map { s ->
                val full = byId[s.id] ?: return@map s
                s.copy(
                    coverUrl = s.coverUrl ?: full.coverUrl,
                    albumId = if (s.albumId > 0L) s.albumId else full.albumId
                )
            }
        }
        val artists = filled.map { ArtistBrief(it.artist.hashCode().toLong(), it.artist, it.coverUrl) }
            .distinctBy { it.name }.take(6)
        val albums = filled.map { AlbumBrief(it.albumId, it.album, it.artist, it.coverUrl) }
            .distinctBy { it.id }.take(6)
        return SearchResults(filled, artists, albums)
    }

    override suspend fun newReleases(): List<Playlist>? {
        val j = get("/personalized/newsong") ?: return null
        val arr = j.optJSONArray("result") ?: return null
        val songs = (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            parseNeteaseSong(item.optJSONObject("song") ?: item)
        }
        if (songs.isEmpty()) return null
        // Group by album so each slide is a real new album / single.
        return songs.groupBy { it.albumId }
            .entries
            .sortedByDescending { (_, list) -> list.size }
            .take(8)
            .map { (albumId, list) ->
                val head = list.first()
                Playlist(
                    id = if (albumId > 0L) albumId else head.id,
                    name = head.album.ifBlank { head.name },
                    subtitle = head.artist,
                    coverUrl = head.coverUrl,
                    songCount = list.size,
                    kind = PlaylistKind.NewRelease,
                    songs = list
                )
            }
    }

    /** 最新专辑：本实现（JS 音源通道）没有专辑接口，返回 null 让上层回退。 */
    override suspend fun newAlbums(): List<Playlist>? = null

    override suspend fun dailySongs(): List<Song>? {
        if (config.cookie.isNullOrBlank()) return null
        val j = get("/recommend/songs") ?: return null
        val arr = j.optJSONObject("data")?.optJSONArray("dailySongs") ?: return null
        return (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }.ifEmpty { null }
    }

    override suspend fun personalPlaylists(): List<Playlist>? {
        if (config.cookie.isNullOrBlank()) return null
        val account = get("/user/account") ?: return null
        val uid = account.optJSONObject("account")?.optLong("id", 0L) ?: 0L
        if (uid <= 0L) return null
        val j = get("/user/playlist?uid=$uid") ?: return null
        val arr = j.optJSONArray("playlist") ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val isLiked = p.optInt("specialType", 0) == 5 ||
                p.optString("name", "").contains("喜欢的音乐")
            Playlist(
                id = p.optLong("id", 0L),
                name = p.optString("name", "歌单"),
                subtitle = p.optJSONObject("creator")?.optString("nickname", "我的歌单") ?: "我的歌单",
                coverUrl = p.optString("coverImgUrl", "").takeIf { it.isNotBlank() },
                songCount = p.optInt("trackCount", 0),
                kind = if (isLiked) PlaylistKind.Liked else PlaylistKind.Custom
            )
        }.ifEmpty { null }
    }

    override suspend fun playlistSongs(id: Long): List<Song>? {
        val j = get("/playlist/track/all?id=$id&limit=30")
        val arr = j?.optJSONArray("songs")
        if (arr != null && arr.length() > 0) {
            (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }.let {
                if (it.isNotEmpty()) return it
            }
        }
        return albumSongs(id)
    }

    override suspend fun albumSongs(id: Long): List<Song>? {
        val album = get("/album?id=$id") ?: return null
        val arr = album.optJSONArray("songs") ?: return null
        return (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }.ifEmpty { null }
    }

    override suspend fun toplists(): List<Playlist>? {
        val j = get("/toplist") ?: return null
        val arr = j.optJSONArray("list") ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id", 0L)
            val name = o.optString("name", "")
            if (id <= 0L || name.isBlank()) return@mapNotNull null
            Playlist(
                id = id,
                name = name,
                subtitle = o.optString("updateFrequency", "").ifBlank { "排行榜" },
                coverUrl = o.optString("coverImgUrl", "").takeIf { it.isNotBlank() },
                songCount = o.optInt("trackCount", 0),
                kind = PlaylistKind.Custom,
                songs = emptyList()
            )
        }.ifEmpty { null }
    }

    override suspend fun artistSongs(id: Long): List<Song>? {
        val j = get("/artist/top/song?id=$id") ?: return null
        val arr = j.optJSONArray("songs") ?: return null
        return (0 until minOf(arr.length(), 50))
            .mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }
            .ifEmpty { null }
    }

    override suspend fun albumDescription(id: Long): String? {
        val j = get("/album?id=$id") ?: return null
        val album = j.optJSONObject("album") ?: return null
        return album.optString("description", "").takeIf { it.isNotBlank() }
            ?: album.optString("briefDesc", "").takeIf { it.isNotBlank() }
    }

    override suspend fun songQualities(id: Long): List<SongQuality>? {
        val body = get("/song/detail?ids=$id") ?: return null
        val song = body.optJSONArray("songs")?.optJSONObject(0) ?: return null
        val privilege = body.optJSONArray("privileges")?.optJSONObject(0)
        return parseSongQualities(song, privilege)
    }

    override suspend fun songDetail(id: Long): Song? {
        val j = get("/song/detail?ids=[$id]") ?: return null
        val arr = j.optJSONArray("songs") ?: return null
        return parseNeteaseSong(arr.optJSONObject(0))
    }

    override suspend fun lyric(id: Long): List<LyricLine>? {
        val j = get("/lyric/new?id=$id") ?: return null
        val raw = j.optJSONObject("lrc")?.optString("lyric", "").orEmpty()
        val yrc = j.optJSONObject("yrc")?.optString("lyric", "").orEmpty()
        val tv = j.optJSONObject("tlyric")?.optString("lyric", "").orEmpty()
        var lines = parseYrc(yrc).ifEmpty { parseLrc(raw) }
        if (lines.size >= 2 && tv.isNotBlank()) lines = withTranslations(lines, tv)
        // A single metadata line (e.g. "[00:00.000] 作词 : ...") is not a usable lyric.
        return lines.takeIf { it.size >= 2 }
    }

    override suspend fun comments(id: Long): List<CommentItem>? {
        val j = get("/comment/music?id=$id&limit=10") ?: return null
        return parseComments(j, includeHot = true, includeLatest = false)
            ?: parseComments(j, includeHot = false, includeLatest = true)
    }

    override suspend fun allComments(id: Long): List<CommentItem>? {
        val out = LinkedHashMap<Long, CommentItem>()
        var total = 0
        for (offset in 0..300 step 100) {
            val j = get("/comment/music?id=$id&limit=100&offset=$offset") ?: break
            total = j.optInt("total", total)
            parseComments(j, includeHot = offset == 0, includeLatest = true)
                ?.forEach { out[it.id] = it }
            if (out.size >= total || total <= offset + 100) break
        }
        return out.values.toList().ifEmpty { null }
    }

    override suspend fun songUrl(id: Long): String? {
        // /song/url/v1 sometimes rejects QR-login cookies with 400; fall back to /song/url.
        listOf(
            "/song/url/v1?id=$id&level=exhigh",
            "/song/url/v1?id=$id&level=standard",
            "/song/url?id=$id"
        ).forEach { path ->
            val j = get(path) ?: return@forEach
            val arr = j.optJSONArray("data") ?: return@forEach
            val first = arr.optJSONObject(0) ?: return@forEach
            val url = first.optString("url", "")
            if (url.isNotBlank() && url != "null") return url
        }
        return null
    }

    override suspend fun songUrlAt(id: Long, level: String): SongStream? {
        fun fromJson(j: JSONObject?): SongStream? {
            val first = j?.optJSONArray("data")?.optJSONObject(0) ?: return null
            val url = first.optString("url", "").takeIf { it.isNotBlank() && it != "null" }
                ?: return null
            return SongStream(
                url = url,
                level = first.optString("level", ""),
                bitrate = first.optInt("br", 0)
            )
        }
        val levels = when (level) {
            "jymaster" -> listOf("jymaster", "sky", "jyeffect", "hires", "lossless", "exhigh", "standard")
            "sky" -> listOf("sky", "jyeffect", "hires", "lossless", "exhigh", "standard")
            "jyeffect" -> listOf("jyeffect", "hires", "lossless", "exhigh", "standard")
            "hires" -> listOf("hires", "jyeffect", "lossless", "exhigh", "standard")
            "lossless" -> listOf("lossless", "hires", "jyeffect", "exhigh", "standard")
            "exhigh" -> listOf("exhigh", "lossless", "standard")
            else -> listOf("standard")
        }.distinct()
        repeat(2) {
            for (lv in levels) {
                fromJson(get("/song/url/v1?id=$id&level=$lv"))?.let { return it }
            }
            fromJson(get("/song/url?id=$id"))?.let { return it }
        }
        return null
    }

    override suspend fun profile(): UserProfile? {
        if (config.cookie.isNullOrBlank()) return null
        val j = get("/user/account") ?: return null
        val p = j.optJSONObject("profile") ?: return null
        return UserProfile(
            userId = p.optLong("userId", 0L),
            nickname = p.optString("nickname", "网易云用户"),
            avatarUrl = p.optString("avatarUrl", "").takeIf { it.isNotBlank() },
            vip = p.optInt("vipType", 0) > 0
        )
    }

    override suspend fun stats(): PlayStats? {
        if (config.cookie.isNullOrBlank()) return null
        val j = get("/record?type=1") ?: return null
        val arr = j.optJSONArray("weekData") ?: return null
        val tops = (0 until minOf(arr.length(), 6)).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            val song = item.optJSONObject("song") ?: return@mapNotNull null
            val artist = song.optJSONArray("artists")?.optJSONObject(0)?.optString("name", "") ?: ""
            SongStat(song.optString("name", ""), artist, item.optInt("playCount", 0))
        }
        if (tops.isEmpty()) return null
        val artists = tops.groupBy { it.artist }
            .map { (artist, list) -> ArtistStat(artist, list.sumOf { it.plays }) }
            .sortedByDescending { it.plays }
            .take(6)
        return PlayStats(
            totalPlays = tops.sumOf { it.plays },
            todayMinutes = 0,
            weekMinutes = 0,
            streakDays = 0,
            topSongs = tops,
            topArtists = artists,
            hourly = List(24) { 0 },
            local = false
        )
    }

    // ---- helpers ----

    /** The public proxy has no like-count route; only the comment total is available. */
    override suspend fun songSocial(id: Long): SongSocial? {
        val j = get("/comment/music?id=$id&limit=1") ?: return null
        val total = j.optInt("total", -1)
        return if (total >= 0) SongSocial(likeCount = -1, commentCount = total) else null
    }

    override suspend fun likeSong(id: Long, like: Boolean): Boolean? = null

    override suspend fun artistAlbums(id: Long): List<Playlist>? = null

    private fun parseSong(j: JSONObject?): Song? = parseNeteaseSong(j)
}
