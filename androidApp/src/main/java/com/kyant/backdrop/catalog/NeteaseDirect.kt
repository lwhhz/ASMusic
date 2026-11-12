package com.kyant.backdrop.catalog

import com.kyant.backdrop.catalog.appstore.music.ArtistInfo
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kyant.backdrop.catalog.appstore.music.AlbumBrief
import com.kyant.backdrop.catalog.appstore.music.ArtistBrief
import com.kyant.backdrop.catalog.appstore.music.ArtistStat
import com.kyant.backdrop.catalog.appstore.music.CommentItem
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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct client for music.163.com — no PC-side proxy required.
 *
 * Requests are encrypted locally with [NeteaseCrypto] and sent to the official
 * endpoints, so scanning the QR code from the Netease app is enough to sign in
 * on a phone that has no access to a dev machine.
 */
class NeteaseDirect(private val config: AppConfig) : MusicApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private data class Resp(val body: JSONObject?, val setCookies: List<String>)

    // ---- cookie plumbing ----

    private fun userCookie(): Map<String, String> {
        val raw = config.cookie ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.split(';').forEach { part ->
            val i = part.indexOf('=')
            if (i > 0) out[part.substring(0, i).trim()] = part.substring(i + 1).trim()
        }
        return out
    }

    private fun baseCookie(): LinkedHashMap<String, String> {
        val p = config.deviceProfile
        val map = linkedMapOf(
            "__remember_me" to "true",
            "ntes_kaola_ad" to "1",
            "_ntes_nuid" to p.nuid,
            "_ntes_nnid" to "${p.nuid},${p.createdAt}",
            "WNMCID" to "${p.wnmcid}.${p.createdAt}.01.0",
            "WEVNSM" to "1.0.0",
            "osver" to "Microsoft-Windows-10-Professional-build-19045-64bit",
            "deviceId" to p.deviceId,
            "os" to "pc",
            "channel" to "netease",
            "appver" to APP_VER
        )
        map.putAll(userCookie())
        return map
    }

    private fun cookieHeader(map: Map<String, String>): String =
        map.entries.joinToString("; ") {
            "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }

    // ---- transports ----

    private suspend fun weapi(path: String, data: JSONObject): Resp = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = baseCookie()
            val payload = JSONObject()
            data.keys().forEach { k -> payload.put(k, data.get(k)) }
            payload.put("csrf_token", cookie["__csrf"] ?: "")
            payload.put("e_r", false)
            val enc = NeteaseCrypto.weapi(payload.toString())
            val form = FormBody.Builder()
                .add("params", enc.params)
                .add("encSecKey", enc.encSecKey)
                .build()
            val request = Request.Builder()
                .url("https://music.163.com/weapi/" + path.removePrefix("/api/"))
                .header("Referer", "https://music.163.com")
                .header("User-Agent", UA_WEAPI)
                .header("Cookie", cookieHeader(cookie))
                .post(form)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                Resp(
                    runCatching { JSONObject(text) }.getOrNull(),
                    response.headers.values("Set-Cookie")
                )
            }
        }.getOrDefault(Resp(null, emptyList()))
    }

    private suspend fun eapi(path: String, data: JSONObject): Resp = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = baseCookie()
            val header = JSONObject()
                .put("osver", cookie["osver"])
                .put("deviceId", cookie["deviceId"])
                .put("os", cookie["os"])
                .put("appver", cookie["appver"])
                .put("versioncode", "140")
                .put("mobilename", "")
                .put("buildver", (System.currentTimeMillis() / 1000).toString())
                .put("resolution", "1920x1080")
                .put("__csrf", cookie["__csrf"] ?: "")
                .put("channel", cookie["channel"])
                .put("requestId", "${System.currentTimeMillis()}_0000")
            val headerCookie = LinkedHashMap<String, String>()
            header.keys().forEach { k -> headerCookie[k] = header.optString(k) }
            cookie["MUSIC_U"]?.let { header.put("MUSIC_U", it); headerCookie["MUSIC_U"] = it }

            val payload = JSONObject()
            data.keys().forEach { k -> payload.put(k, data.get(k)) }
            payload.put("header", header)
            payload.put("e_r", false)
            val params = NeteaseCrypto.eapi(path, payload.toString())
            val request = Request.Builder()
                .url("https://interface.music.163.com/eapi/" + path.removePrefix("/api/"))
                .header("User-Agent", UA_APP)
                .header("Cookie", cookieHeader(headerCookie))
                .post(FormBody.Builder().add("params", params).build())
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                Resp(
                    runCatching { JSONObject(text) }.getOrNull(),
                    response.headers.values("Set-Cookie")
                )
            }
        }.getOrDefault(Resp(null, emptyList()))
    }

    // ---- QR login ----

    override suspend fun qrKey(): String? = eapi("/api/login/qrcode/unikey", JSONObject().put("type", 3))
        .body?.optString("unikey")?.takeIf { it.isNotBlank() }

    override suspend fun qrImage(key: String): ImageBitmap? =
        QrBitmap.encode("https://music.163.com/login?codekey=$key")

    override suspend fun qrCheck(key: String): QrStatus {
        val resp = eapi(
            "/api/login/qrcode/client/login",
            JSONObject().put("key", key).put("type", 3)
        )
        val code = resp.body?.optInt("code", 800) ?: 800
        // A successful scan carries the credentials in Set-Cookie; some gateway
        // responses echo them in the JSON body instead, so accept both.
        val fromHeaders = cleanCookie(resp.setCookies.joinToString(";"))
        val fromBody = cleanCookie(resp.body?.optString("cookie").orEmpty())
        val merged = listOf(fromHeaders, fromBody)
            .filter { it.contains("MUSIC_U=") }
            .joinToString("; ")
        return QrStatus(
            code,
            resp.body?.optString("message").orEmpty(),
            merged.takeIf { it.isNotBlank() }
        )
    }

    // ---- catalogue ----

    override suspend fun search(keyword: String): SearchResults? {
        val body = eapi(
            "/api/search/get",
            JSONObject()
                .put("s", keyword)
                .put("type", 1)
                .put("limit", 20)
                .put("offset", 0)
        ).body ?: return null
        val arr = body.optJSONObject("result")?.optJSONArray("songs") ?: return null
        val songs = (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }
        if (songs.isEmpty()) return null
        // The legacy search payload omits artwork; one batched detail call fills it.
        val withCovers = fillMissingCovers(songs)
        val realArtists = searchArtists(keyword)
        val artists = if (realArtists.isEmpty()) {
            withCovers.map { ArtistBrief(it.artist.hashCode().toLong(), it.artist, it.coverUrl) }
                .distinctBy { it.name }.take(6)
        } else {
            // Some artist records ship without a portrait — borrow their song artwork.
            realArtists.map { a ->
                a.copy(
                    coverUrl = a.coverUrl ?: withCovers.firstOrNull { song ->
                        song.artist.split(" / ").any { it.isNotBlank() && (a.name.contains(it) || it.contains(a.name)) }
                    }?.coverUrl
                )
            }
        }
        val albums = withCovers.map { AlbumBrief(it.albumId, it.album, it.artist, it.coverUrl) }
            .distinctBy { it.id }.take(6)
        return SearchResults(withCovers, artists, albums)
    }

    /** Fill missing song / album covers from the batched song-detail endpoint. */
    private suspend fun fillMissingCovers(songs: List<Song>): List<Song> {
        val missing = songs.filter { it.coverUrl == null }
        if (missing.isEmpty()) return songs
        val c = missing.joinToString(",", "[", "]") { "{\"id\":${it.id}}" }
        val detail = weapi("/api/v3/song/detail", JSONObject().put("c", c)).body ?: return songs
        val arr = detail.optJSONArray("songs") ?: return songs
        val byId = HashMap<Long, Song>()
        for (i in 0 until arr.length()) {
            val s = parseNeteaseSong(arr.optJSONObject(i)) ?: continue
            byId[s.id] = s
        }
        return songs.map { s ->
            val full = byId[s.id] ?: return@map s
            s.copy(
                coverUrl = s.coverUrl ?: full.coverUrl,
                albumId = if (s.albumId > 0L) s.albumId else full.albumId
            )
        }
    }

    /** Real artist portraits (search type 100); empty when the endpoint is unavailable. */
    private suspend fun searchArtists(keyword: String): List<ArtistBrief> {
        val body = eapi(
            "/api/search/get",
            JSONObject().put("s", keyword).put("type", 100).put("limit", 6).put("offset", 0)
        ).body ?: return emptyList()
        val arr = body.optJSONObject("result")?.optJSONArray("artists") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = a.optString("name", "")
            if (name.isBlank()) return@mapNotNull null
            ArtistBrief(
                id = a.optLong("id", 0L),
                name = name,
                coverUrl = a.optString("picUrl", "").takeIf { it.isNotBlank() }
            )
        }
    }

    override suspend fun newReleases(): List<Playlist>? {
        val body = weapi(
            "/api/personalized/newsong",
            JSONObject().put("type", "recommend").put("limit", 10).put("areaId", 0)
        ).body ?: return null
        val arr = body.optJSONArray("result") ?: return null
        val songs = (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            parseNeteaseSong(item.optJSONObject("song") ?: item)
        }
        if (songs.isEmpty()) return null
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

    override suspend fun dailySongs(): List<Song>? {
        if (config.cookie.isNullOrBlank()) return null
        val body = weapi("/api/v3/discovery/recommend/songs", JSONObject()).body ?: return null
        val arr = body.optJSONObject("data")?.optJSONArray("dailySongs") ?: return null
        return (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }
            .ifEmpty { null }
    }

    private suspend fun accountJson(): JSONObject? =
        weapi("/api/nuser/account/get", JSONObject()).body?.takeIf { it.optInt("code", 0) == 200 }

    override suspend fun personalPlaylists(): List<Playlist>? {
        if (config.cookie.isNullOrBlank()) return null
        val uid = accountJson()?.optJSONObject("account")?.optLong("id", 0L) ?: 0L
        if (uid <= 0L) return null
        val body = weapi(
            "/api/user/playlist",
            JSONObject().put("uid", uid).put("limit", 100).put("offset", 0).put("includeVideo", true)
        ).body ?: return null
        val arr = body.optJSONArray("playlist") ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            // specialType 5 = 「我喜欢的音乐」; do not rely on list order.
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
        val detail = eapi(
            "/api/v6/playlist/detail",
            JSONObject().put("id", id).put("n", 100000).put("s", 8)
        ).body
        val trackIds = detail?.optJSONObject("playlist")?.optJSONArray("trackIds")
        if (trackIds != null && trackIds.length() > 0) {
            val ids = (0 until minOf(trackIds.length(), 100))
                .mapNotNull { trackIds.optJSONObject(it)?.optLong("id") }
            if (ids.isNotEmpty()) {
                val c = ids.joinToString(",", "[", "]") { "{\"id\":$it}" }
                val songs = weapi("/api/v3/song/detail", JSONObject().put("c", c)).body
                    ?.optJSONArray("songs")
                    ?.let { arr -> (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) } }
                if (!songs.isNullOrEmpty()) return songs
            }
        }
        return albumSongs(id)
    }

    override suspend fun albumSongs(id: Long): List<Song>? {
        val body = weapi("/api/v1/album/$id", JSONObject()).body ?: return null
        val arr = body.optJSONArray("songs") ?: return null
        return (0 until arr.length()).mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }
            .ifEmpty { null }
    }

    override suspend fun toplists(): List<Playlist>? {
        val body = weapi("/api/toplist", JSONObject()).body ?: return null
        val arr = body.optJSONArray("list") ?: return null
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

    /** Plain GET with the account cookie attached (some endpoints require login). */
    private suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = baseCookie()
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", UA_WEAPI)
                .header("Cookie", cookieHeader(cookie))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    override suspend fun artistInfo(id: Long): ArtistInfo? {
        var avatar: String? = null
        var brief = ""
        var rawHead = ""
        var rawDesc = ""
        runCatching {
            val text = getText("https://music.163.com/api/artist/head/info/get?id=$id").orEmpty()
            rawHead = text.take(500)
            val data = runCatching { JSONObject(text) }.getOrNull()?.optJSONObject("data")
            // The payload nests everything under "artist" (verified on device):
            // data.artist.avatar / data.artist.briefDesc.
            val artist = data?.optJSONObject("artist")
            avatar = artist?.optString("avatar")?.takeIf { it.isNotBlank() && it != "null" }
                ?: data?.optJSONObject("identify")
                    ?.optString("imageUrl")
                    ?.takeIf { it.isNotBlank() && it != "null" }
            brief = artist?.optString("briefDesc").orEmpty()
        }
        var desc = ""
        runCatching {
            val body = weapi("/api/artist/desc", JSONObject().put("id", id)).body
            rawDesc = body?.toString().orEmpty().take(500)
            desc = body?.optString("briefDesc").orEmpty()
            if (desc.isBlank()) {
                val intro = body?.optJSONArray("introduction")
                if (intro != null) {
                    desc = (0 until intro.length())
                        .mapNotNull { intro.optJSONObject(it)?.optString("txt") }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                }
            }
        }
        if (avatar == null && brief.isBlank() && desc.isBlank()) return null
        return ArtistInfo(id, avatar, brief, desc)
    }

    override suspend fun artistSongs(id: Long): List<Song>? {
        val body = weapi("/api/v1/artist/top/song", JSONObject().put("id", id)).body ?: return null
        val arr = body.optJSONArray("songs") ?: return null
        return (0 until minOf(arr.length(), 50))
            .mapNotNull { parseNeteaseSong(arr.optJSONObject(it)) }
            .ifEmpty { null }
    }

    override suspend fun albumDescription(id: Long): String? {
        // Netease's web client asks /api/v1/album/{id}; the old /api/album?id= form
        // often returns an empty description (or a different shell entirely).
        val body = weapi("/api/v1/album/$id", JSONObject()).body ?: return null
        val album = body.optJSONObject("album") ?: return null
        return album.optString("description", "").takeIf { it.isNotBlank() }
            ?: album.optString("briefDesc", "").takeIf { it.isNotBlank() }
    }

    override suspend fun songDetail(id: Long): Song? {
        val c = "[{\"id\":$id}]"
        val body = weapi("/api/v3/song/detail", JSONObject().put("c", c)).body ?: return null
        val arr = body.optJSONArray("songs") ?: return null
        return parseNeteaseSong(arr.optJSONObject(0))
    }

    override suspend fun songQualities(id: Long): List<SongQuality>? {
        val c = "[{\"id\":$id}]"
        val body = weapi("/api/v3/song/detail", JSONObject().put("c", c)).body ?: return null
        val song = body.optJSONArray("songs")?.optJSONObject(0) ?: return null
        val privilege = body.optJSONArray("privileges")?.optJSONObject(0)
        return parseSongQualities(song, privilege)
    }

    override suspend fun lyric(id: Long): List<LyricLine>? {
        // The v1 endpoint can return tlyric (translation) and yrc (karaoke timings).
        // Netease only answers with yrc for some crypto/param combinations, so when
        // word-by-word lyrics are enabled we probe a few variants and keep the first
        // answer that actually carries word timings.
        val wantWords = com.kyant.backdrop.catalog.appstore.music.MusicServices.state.wordByWord
        val karaoke = JSONObject().put("cp", true)
            .put("lv", 1).put("kv", 0).put("tv", 1).put("rv", 1)
            .put("yv", 1).put("ytv", 1).put("yrv", 1)
        val karaokeEapi = JSONObject().put("cp", true)
            .put("lv", 0).put("kv", 0).put("tv", 0).put("rv", 0)
            .put("yv", 0).put("ytv", 0).put("yrv", 0)
        val plain = JSONObject().put("cp", false)
            .put("lv", -1).put("kv", -1).put("tv", -1).put("rv", -1)

        data class Attempt(val eapi: Boolean, val payload: JSONObject)
        val attempts = if (wantWords) listOf(
            Attempt(false, karaoke),
            Attempt(true, karaokeEapi),
            Attempt(false, karaokeEapi)
        ) else listOf(Attempt(false, plain))

        var fallback: List<LyricLine> = emptyList()
        for (attempt in attempts) {
            val payload = JSONObject(attempt.payload.toString()).put("id", id)
            val body = runCatching {
                if (attempt.eapi) eapi("/api/song/lyric/v1", payload).body
                else weapi("/api/song/lyric/v1", payload).body
            }.getOrNull() ?: continue
            val lrcRaw = body.optJSONObject("lrc")?.optString("lyric", "").orEmpty()
            val tlyric = body.optJSONObject("tlyric")?.optString("lyric", "").orEmpty()
            val yrc = body.optJSONObject("yrc")?.optString("lyric", "").orEmpty()
            var lines = parseYrc(yrc).ifEmpty { parseLrc(lrcRaw) }
            if (lines.size < 2) continue
            if (tlyric.isNotBlank()) lines = withTranslations(lines, tlyric)
            if (lines.any { it.words.isNotEmpty() } || !wantWords) return lines
            if (fallback.isEmpty()) fallback = lines
        }
        if (fallback.isNotEmpty()) return fallback

        // /api/song/lyric keeps returning classic LRC even for the new clients.
        val classic = weapi(
            "/api/song/lyric",
            JSONObject().put("id", id).put("lv", -1).put("kv", -1).put("tv", -1).put("os", "pc")
        ).body ?: return null
        val raw = classic.optJSONObject("lrc")?.optString("lyric", "").orEmpty()
        val tv = classic.optJSONObject("tlyric")?.optString("lyric", "").orEmpty()
        var lines = parseLrc(raw)
        if (lines.size >= 2 && tv.isNotBlank()) lines = withTranslations(lines, tv)
        return lines.takeIf { it.size >= 2 }
    }

    override suspend fun comments(id: Long): List<CommentItem>? {
        val body = weapi(
            "/api/v1/resource/comments/R_SO_4_$id",
            JSONObject().put("rid", id).put("limit", 10).put("offset", 0).put("beforeTime", 0)
        ).body ?: return null
        return parseComments(body, includeHot = true, includeLatest = false)
            ?: parseComments(body, includeHot = false, includeLatest = true)
    }

    override suspend fun allComments(id: Long): List<CommentItem>? {
        val out = LinkedHashMap<Long, CommentItem>()
        var total = 0
        for (offset in 0..300 step 100) {
            val body = weapi(
                "/api/v1/resource/comments/R_SO_4_$id",
                JSONObject().put("rid", id).put("limit", 100).put("offset", offset)
                    .put("beforeTime", 0)
            ).body ?: break
            total = body.optInt("total", total)
            parseComments(body, includeHot = offset == 0, includeLatest = true)
                ?.forEach { out[it.id] = it }
            if (out.size >= total || total <= offset + 100) break
        }
        return out.values.toList().ifEmpty { null }
    }

    /**
     * Song-level social stats: 红心数 from the eapi red/count endpoint, comment
     * total from the comment resource. Either may fail independently.
     */
    override suspend fun songSocial(id: Long): SongSocial? {
        val likeCount = runCatching {
            val body = eapi("/api/song/red/count", JSONObject().put("songId", id)).body
            body?.optJSONObject("data")?.optInt("count", -1)
                ?: body?.optInt("count", -1) ?: -1
        }.getOrDefault(-1)
        val commentCount = runCatching {
            weapi(
                "/api/v1/resource/comments/R_SO_4_$id",
                JSONObject().put("rid", id).put("limit", 1).put("offset", 0)
            ).body?.optInt("total", -1) ?: -1
        }.getOrDefault(-1)
        if (likeCount < 0 && commentCount < 0) return null
        return SongSocial(likeCount = likeCount, commentCount = commentCount)
    }

    override suspend fun songUrl(id: Long): String? {
        suspend fun fromArray(body: JSONObject?): String? {
            val first = body?.optJSONArray("data")?.optJSONObject(0) ?: return null
            return first.optString("url", "").takeIf { it.isNotBlank() && it != "null" }
        }
        // Risk control (-460) is transient; one retry is usually enough.
        repeat(2) { attempt ->
            fromArray(
                eapi(
                    "/api/song/enhance/player/url/v1",
                    JSONObject().put("ids", "[$id]").put("level", "exhigh").put("encodeType", "flac")
                ).body
            )?.let { return it }
            fromArray(
                eapi(
                    "/api/song/enhance/player/url/v1",
                    JSONObject().put("ids", "[$id]").put("level", "standard").put("encodeType", "mp3")
                ).body
            )?.let { return it }
            fromArray(
                weapi("/api/song/enhance/player/url", JSONObject().put("ids", "[$id]").put("br", 320000)).body
            )?.let { return it }
            if (attempt == 0) withContext(Dispatchers.IO) { Thread.sleep(500) }
        }
        return null
    }

    override suspend fun songUrlAt(id: Long, level: String): SongStream? {
        suspend fun fromArray(body: JSONObject?): SongStream? {
            val first = body?.optJSONArray("data")?.optJSONObject(0) ?: return null
            val url = first.optString("url", "").takeIf { it.isNotBlank() && it != "null" }
                ?: return null
            return SongStream(
                url = url,
                level = first.optString("level", ""),
                bitrate = first.optInt("br", 0)
            )
        }
        // Netease often answers a single level with -460 risk control while another
        // level succeeds. Try a descending ladder, including hires as a FLAC
        // fallback for lossless (it frequently returns the same master).
        val levels = when (level) {
            "jymaster" -> listOf("jymaster", "sky", "jyeffect", "hires", "lossless", "exhigh", "standard")
            "sky" -> listOf("sky", "jyeffect", "hires", "lossless", "exhigh", "standard")
            "jyeffect" -> listOf("jyeffect", "hires", "lossless", "exhigh", "standard")
            "hires" -> listOf("hires", "jyeffect", "lossless", "exhigh", "standard")
            "lossless" -> listOf("lossless", "hires", "jyeffect", "exhigh", "standard")
            "exhigh" -> listOf("exhigh", "lossless", "standard")
            else -> listOf("standard")
        }.distinct()
        repeat(3) { attempt ->
            for (lv in levels) {
                val payload = JSONObject()
                    .put("ids", "[$id]")
                    .put("level", lv)
                    .put("encodeType", "flac")
                if (lv == "sky") payload.put("immerseType", "c51")
                fromArray(
                    eapi("/api/song/enhance/player/url/v1", payload).body
                )?.let { return it }
            }
            fromArray(
                weapi("/api/song/enhance/player/url", JSONObject().put("ids", "[$id]").put("br", 320000)).body
            )?.let { return it }
            if (attempt < 2) withContext(Dispatchers.IO) { Thread.sleep(700) }
        }
        return null
    }

    override suspend fun profile(): UserProfile? {
        if (config.cookie.isNullOrBlank()) return null
        val body = accountJson() ?: return null
        val p = body.optJSONObject("profile") ?: return null
        return UserProfile(
            userId = p.optLong("userId", 0L),
            nickname = p.optString("nickname", "网易云用户"),
            avatarUrl = p.optString("avatarUrl", "").takeIf { it.isNotBlank() },
            vip = p.optInt("vipType", 0) > 0
        )
    }

    override suspend fun stats(): PlayStats? {
        if (config.cookie.isNullOrBlank()) return null
        val uid = accountJson()?.optJSONObject("account")?.optLong("id", 0L) ?: 0L
        if (uid <= 0L) return null
        val body = weapi("/api/v1/play/record", JSONObject().put("uid", uid).put("type", 1)).body
            ?: return null
        val arr = body.optJSONArray("weekData") ?: return null
        val tops = (0 until minOf(arr.length(), 6)).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            val song = item.optJSONObject("song") ?: return@mapNotNull null
            val artists = song.optJSONArray("artists") ?: song.optJSONArray("ar")
            val artist = (0 until (artists?.length() ?: 0))
                .mapNotNull { artists?.optJSONObject(it)?.optString("name") }
                .joinToString(" / ")
            SongStat(song.optString("name", ""), artist, item.optInt("playCount", 0))
        }
        if (tops.isEmpty()) return null
        // /play/record only exposes weekly play counts — never mix in fake totals.
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

    override suspend fun image(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }

    private companion object {
        const val APP_VER = "3.1.17.204416"
        const val UA_APP = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
        const val UA_WEAPI =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
    }
}
