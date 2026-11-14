package com.kyant.backdrop.catalog

import kotlinx.coroutines.sync.withPermit
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kyant.backdrop.catalog.appstore.music.AlbumBrief
import com.kyant.backdrop.catalog.appstore.music.ArtistBrief
import com.kyant.backdrop.catalog.appstore.music.ArtistStat
import com.kyant.backdrop.catalog.appstore.music.CacheInfo
import com.kyant.backdrop.catalog.appstore.music.CommentItem
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.LyricWord
import com.kyant.backdrop.catalog.appstore.music.PlayStats
import com.kyant.backdrop.catalog.appstore.music.Playlist
import com.kyant.backdrop.catalog.appstore.music.PlaylistKind
import com.kyant.backdrop.catalog.appstore.music.SearchResults
import com.kyant.backdrop.catalog.appstore.music.Song
import com.kyant.backdrop.catalog.appstore.music.SongSocial
import com.kyant.backdrop.catalog.appstore.music.SongStat
import com.kyant.backdrop.catalog.appstore.music.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Calendar

/**
 * On-disk cache for everything the app fetches from the network:
 * API payloads (playlists / songs / lyrics / comments), cover images and the
 * log of songs played inside the app (used for the local listening stats).
 *
 * Layout under filesDir/music_cache:
 *   data/<hash>.json  — {"t": fetchedAt, "d": {...}}
 *   img/<hash>.jpg    — decoded cover images
 *   plays.json        — [{song:{...}, ts: epochMillis}] newest last
 *
 * The UI reads through this cache first, so a page you already visited opens
 * instantly even when the phone is offline; [MusicBridgeImpl] refreshes the
 * entry in the background afterwards.
 */
class MusicCache(context: Context) {

    private val root = File(context.filesDir, "music_cache").apply { mkdirs() }
    private val dataDir = File(root, "data").apply { mkdirs() }
    private val imageDir = File(root, "img").apply { mkdirs() }
    private val playsFile = File(root, "plays.json")
    private val ioLock = Mutex()

    // Cap by decoded bytes rather than entry count: one 512 px cover costs ~1 MB,
    // so a fixed entry count could hold 60+ MB on a large screen.
    //
    // 这个 map 会被 4 个并发的解码线程同时读写（decodeGate 的许可数是 4），而
    // accessOrder = true 的 LinkedHashMap 连 get() 都会改内部链表 —— 无锁访问会偶发
    // ArrayIndexOutOfBounds / 死循环。所有访问都必须经过 imageLock。
    private var imageCacheBytes = 0L
    private val imageLock = Any()
    private val memoryImages = object : LinkedHashMap<String, ImageBitmap>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            eldest == null && size > 128
    }

    /** 真实占用字节数：RGB_565 是 2 B/px，按 4 算会让可用额度凭空少一半。 */
    private fun bitmapBytes(bitmap: ImageBitmap): Long = runCatching {
        bitmap.asAndroidBitmap().byteCount.toLong()
    }.getOrDefault(bitmap.width.toLong() * bitmap.height * 2)

    private fun memoryGet(key: String): ImageBitmap? = synchronized(imageLock) {
        memoryImages[key]
    }

    private fun memoryPut(key: String, bitmap: ImageBitmap) = synchronized(imageLock) {
        memoryImages.put(key, bitmap)?.let { imageCacheBytes -= bitmapBytes(it) }
        imageCacheBytes += bitmapBytes(bitmap)
        val limit = 36L * 1024 * 1024
        val iterator = memoryImages.keys.iterator()
        while ((imageCacheBytes > limit || memoryImages.size > 128) && iterator.hasNext()) {
            val eldestKey = iterator.next()
            memoryImages[eldestKey]?.let { imageCacheBytes -= bitmapBytes(it) }
            iterator.remove()
        }
    }

    // ------------------------------------------------------------------
    // JSON payload cache
    // ------------------------------------------------------------------

    /**
     * Returns the cached payload. [maxAgeMs] limits how old it may be before it is
     * treated as missing; pass [Long.MAX_VALUE] to accept any age (used as an
     * offline fallback when the refresh request fails).
     */
    suspend fun read(key: String, maxAgeMs: Long = Long.MAX_VALUE): JSONObject? = withContext(Dispatchers.IO) {
        val envelope = runCatching { JSONObject(fileFor(key).readText()) }.getOrNull()
            ?: return@withContext null
        if (maxAgeMs != Long.MAX_VALUE) {
            val age = System.currentTimeMillis() - envelope.optLong("t", 0L)
            if (age > maxAgeMs) return@withContext null
        }
        envelope.optJSONObject("d")
    }

    suspend fun write(key: String, data: JSONObject) = withContext(Dispatchers.IO) {
        ioLock.withLock {
            runCatching {
                val file = fileFor(key)
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(JSONObject().put("t", System.currentTimeMillis()).put("d", data).toString())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun fileFor(key: String) = File(dataDir, sha1(key) + ".json")

    // ------------------------------------------------------------------
    // Cover images
    // ------------------------------------------------------------------

    // List rows only need ~100 px; decoding the stored 800 px JPEG for every row was
    // the main source of scrolling jank. Decode straight to the requested size and cap
    // how many decodes run at once.
    private val decodeGate = kotlinx.coroutines.sync.Semaphore(4)

    suspend fun image(url: String, maxEdge: Int = 512): ImageBitmap? {
        val key = "$url@$maxEdge"
        memoryGet(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            decodeGate.withPermit {
                runCatching {
                    val file = imageFile(url)
                    if (!file.exists()) return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    var sample = 1
                    val longest = maxOf(bounds.outWidth, bounds.outHeight)
                    while (longest / sample > maxEdge) sample *= 2
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                }.getOrNull()
            }
        }
        if (bmp != null) {
            memoryPut(key, bmp)
        }
        return bmp
    }

    /** Stores a downscaled JPEG and returns the scaled bitmap to render. */
    suspend fun putImage(url: String, bitmap: ImageBitmap): ImageBitmap = withContext(Dispatchers.IO) {
        ioLock.withLock {
            val source = bitmap.asAndroidBitmap()
            // Album art can be 1000px+ (≈2 MB as JPEG). 800px is plenty for the
            // largest cover in the app and keeps both disk and memory small.
            val maxEdge = 800
            val scaled = if (maxOf(source.width, source.height) > maxEdge) {
                val ratio = maxEdge.toFloat() / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * ratio).toInt().coerceAtLeast(1),
                    (source.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else null
            val target = (scaled ?: source).asImageBitmap()
            runCatching {
                val out = ByteArrayOutputStream()
                (scaled ?: source).compress(Bitmap.CompressFormat.JPEG, 86, out)
                val file = imageFile(url)
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeBytes(out.toByteArray())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
            // 这里不写内存缓存：putImage 产出的尺寸（800）和 image() 的 key
            // （"$url@<maxEdge>"）对不上，写进去只会占两份字节且永远命中不了。
            // 磁盘已经落盘，下一次 image() 会按需要的 maxEdge 重新解码。
            target
        }
    }

    private fun imageFile(url: String) = File(imageDir, sha1(url) + ".jpg")

    // ------------------------------------------------------------------
    // Play log + local stats
    // ------------------------------------------------------------------

    data class PlayRecord(val song: Song, val at: Long)

    @Synchronized
    fun recordPlay(song: Song) {
        val records = readPlays().toMutableList()
        records.add(PlayRecord(song, System.currentTimeMillis()))
        val trimmed = records.takeLast(800)
        writePlays(trimmed)
    }

    @Synchronized
    fun recentSongs(limit: Int = 20): List<Song> =
        readPlays().asReversed().map { it.song }.distinctBy { it.id }.take(limit)

    @Synchronized
    fun localStats(): PlayStats {
        val records = readPlays()
        if (records.isEmpty()) {
            return PlayStats(
                totalPlays = 0,
                todayMinutes = 0,
                weekMinutes = 0,
                streakDays = 0,
                topSongs = emptyList(),
                topArtists = emptyList(),
                hourly = List(24) { 0 },
                local = true
            )
        }
        val now = Calendar.getInstance()
        val todayKey = dayKey(now)
        val weekStart = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var todayMs = 0L
        var weekMs = 0L
        val hours = IntArray(24)
        val days = HashSet<Int>()
        records.forEach { r ->
            val c = Calendar.getInstance().apply { timeInMillis = r.at }
            days.add(dayKey(c))
            hours[c.get(Calendar.HOUR_OF_DAY)] += 1
            if (dayKey(c) == todayKey) todayMs += r.song.durationMs
            if (r.at >= weekStart) weekMs += r.song.durationMs
        }

        // Consecutive days ending today (or yesterday if nothing played yet today).
        var streak = 0
        val cursor = Calendar.getInstance()
        if (!days.contains(dayKey(cursor))) cursor.add(Calendar.DAY_OF_YEAR, -1)
        while (days.contains(dayKey(cursor))) {
            streak += 1
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }

        val songGroups = records.groupBy { it.song.id }
        val topSongs = songGroups.values
            .map { list -> SongStat(list.first().song.name, list.first().song.artist, list.size) }
            .sortedByDescending { it.plays }
            .take(6)

        val topArtists = records
            .groupBy { it.song.artist }
            .map { (artist, list) -> ArtistStat(artist, list.size) }
            .sortedByDescending { it.plays }
            .take(6)

        return PlayStats(
            totalPlays = records.size,
            todayMinutes = (todayMs / 60_000L).toInt(),
            weekMinutes = (weekMs / 60_000L).toInt(),
            streakDays = streak,
            topSongs = topSongs,
            topArtists = topArtists,
            hourly = hours.toList(),
            local = true
        )
    }

    private fun dayKey(c: Calendar): Int = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)

    @Synchronized
    private fun readPlays(): List<PlayRecord> = runCatching {
        val arr = JSONArray(playsFile.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val song = songFromJson(o.optJSONObject("song") ?: return@mapNotNull null)
            PlayRecord(song, o.optLong("ts", 0L))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun writePlays(records: List<PlayRecord>) {
        runCatching {
            val arr = JSONArray()
            records.forEach { arr.put(JSONObject().put("song", songToJson(it.song)).put("ts", it.at)) }
            val tmp = File(root, "plays.json.tmp")
            tmp.writeText(arr.toString())
            if (playsFile.exists()) playsFile.delete()
            tmp.renameTo(playsFile)
        }
    }

    // ------------------------------------------------------------------
    // Size / eviction
    // ------------------------------------------------------------------

    suspend fun info(): CacheInfo = withContext(Dispatchers.IO) {
        val plays = readPlays()
        CacheInfo(
            dataBytes = dataDir.walkFiles(),
            imageBytes = imageDir.walkFiles(),
            songCount = plays.map { it.song.id }.distinct().size,
            recentCount = plays.asReversed().map { it.song.id }.distinct().size
        )
    }

    suspend fun clearData() = withContext(Dispatchers.IO) {
        ioLock.withLock { dataDir.listFiles()?.forEach { it.delete() } }
        Unit
    }

    suspend fun clearImages() = withContext(Dispatchers.IO) {
        ioLock.withLock {
            memoryImages.clear()
            imageDir.listFiles()?.forEach { it.delete() }
        }
        Unit
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        ioLock.withLock {
            memoryImages.clear()
            dataDir.listFiles()?.forEach { it.delete() }
            imageDir.listFiles()?.forEach { it.delete() }
            playsFile.delete()
        }
        Unit
    }

    private fun File.walkFiles(): Long =
        walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

// ---------------------------------------------------------------------------
// Codecs shared by the cache and the local play log
// ---------------------------------------------------------------------------

internal fun songToJson(s: Song): JSONObject = JSONObject()
    .put("id", s.id)
    .put("name", s.name)
    .put("artist", s.artist)
    .put("album", s.album)
    .put("albumId", s.albumId)
    .put("duration", s.durationMs)
    .put("cover", s.coverUrl ?: "")
    .put("trackNo", s.trackNo)
    .put("genre", s.genre)
    .put("commentCount", s.commentCount)
    .put("favoriteCount", s.favoriteCount)
    .put("bitrate", s.bitrateKbps)
    .put("size", s.sizeBytes)
    .put("artistId", s.artistId)
    .put("artists", JSONArray().apply {
        s.artists.forEach { a -> put(JSONObject().put("id", a.id).put("name", a.name)) }
    })

internal fun songFromJson(j: JSONObject): Song = Song(
    id = j.optLong("id", 0L),
    name = j.optString("name", ""),
    artist = j.optString("artist", "未知艺术家"),
    album = j.optString("album", ""),
    albumId = j.optLong("albumId", 0L),
    durationMs = j.optLong("duration", 210_000L),
    coverUrl = j.optString("cover", "").takeIf { it.isNotBlank() },
    trackNo = j.optInt("trackNo", 1),
    genre = j.optString("genre", "流行"),
    commentCount = j.optInt("commentCount", 0),
    favoriteCount = j.optInt("favoriteCount", 0),
    bitrateKbps = j.optInt("bitrate", 320),
    sizeBytes = j.optLong("size", 0L),
    artistId = j.optLong("artistId", 0L),
    artists = j.optJSONArray("artists")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id", 0L)
            val name = o.optString("name", "")
            if (id <= 0L || name.isBlank()) null else ArtistBrief(id, name)
        }
    } ?: emptyList()
)

internal fun songsToJson(songs: List<Song>): JSONObject = JSONObject().put(
    "v", JSONArray().apply { songs.forEach { put(songToJson(it)) } }
)

internal fun songsFromJson(j: JSONObject?): List<Song> {
    val arr = j?.optJSONArray("v") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::songFromJson) }
}

internal fun playlistsToJson(playlists: List<Playlist>): JSONObject = JSONObject().put(
    "v", JSONArray().apply {
        playlists.forEach { p ->
            put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("subtitle", p.subtitle)
                    .put("cover", p.coverUrl ?: "")
                    .put("songCount", p.songCount)
                    .put("kind", p.kind.name)
                    .put("songs", songsToJson(p.songs).getJSONArray("v"))
            )
        }
    }
)

internal fun playlistsFromJson(j: JSONObject?): List<Playlist> {
    val arr = j?.optJSONArray("v") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        Playlist(
            id = o.optLong("id", 0L),
            name = o.optString("name", ""),
            subtitle = o.optString("subtitle", ""),
            coverUrl = o.optString("cover", "").takeIf { it.isNotBlank() },
            songCount = o.optInt("songCount", 0),
            kind = runCatching { PlaylistKind.valueOf(o.optString("kind", "Custom")) }
                .getOrDefault(PlaylistKind.Custom),
            songs = songsFromJson(JSONObject().put("v", o.optJSONArray("songs") ?: JSONArray()))
        )
    }
}

internal fun searchToJson(r: SearchResults): JSONObject = JSONObject()
    .put("songs", songsToJson(r.songs).getJSONArray("v"))
    .put(
        "artists", JSONArray().apply {
            r.artists.forEach {
                put(
                    JSONObject().put("id", it.id).put("name", it.name)
                        .put("cover", it.coverUrl ?: "")
                )
            }
        }
    )
    .put(
        "albums", JSONArray().apply {
            r.albums.forEach {
                put(
                    JSONObject().put("id", it.id).put("name", it.name)
                        .put("artist", it.artist).put("cover", it.coverUrl ?: "")
                )
            }
        }
    )

internal fun searchFromJson(j: JSONObject?): SearchResults {
    j ?: return SearchResults()
    return SearchResults(
        songs = songsFromJson(JSONObject().put("v", j.optJSONArray("songs") ?: JSONArray())),
        artists = (0 until (j.optJSONArray("artists")?.length() ?: 0)).mapNotNull { i ->
            val o = j.optJSONArray("artists")?.optJSONObject(i) ?: return@mapNotNull null
            ArtistBrief(o.optLong("id"), o.optString("name"), o.optString("cover").takeIf { it.isNotBlank() })
        },
        albums = (0 until (j.optJSONArray("albums")?.length() ?: 0)).mapNotNull { i ->
            val o = j.optJSONArray("albums")?.optJSONObject(i) ?: return@mapNotNull null
            AlbumBrief(
                o.optLong("id"), o.optString("name"), o.optString("artist"),
                o.optString("cover").takeIf { it.isNotBlank() }
            )
        }
    )
}

internal fun lyricToJson(lines: List<LyricLine>): JSONObject = JSONObject().put(
    "v", JSONArray().apply {
        lines.forEach { line ->
            put(
                JSONObject().put("t", line.timeMs).put("x", line.text).apply {
                    line.translation?.let { put("tr", it) }
                    if (line.words.isNotEmpty()) {
                        put(
                            "w",
                            JSONArray().apply {
                                line.words.forEach { w ->
                                    put(JSONArray().put(w.timeMs).put(w.durationMs).put(w.text))
                                }
                            }
                        )
                    }
                }
            )
        }
    }
)

internal fun lyricFromJson(j: JSONObject?): List<LyricLine> {
    val arr = j?.optJSONArray("v") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val wordsArr = o.optJSONArray("w")
        val words = if (wordsArr == null) emptyList() else (0 until wordsArr.length()).mapNotNull { wi ->
            val wa = wordsArr.optJSONArray(wi) ?: return@mapNotNull null
            LyricWord(wa.optLong(0), wa.optLong(1), wa.optString(2))
        }
        LyricLine(
            timeMs = o.optLong("t"),
            text = o.optString("x"),
            translation = o.optString("tr", "").takeIf { it.isNotBlank() },
            words = words
        )
    }
}

internal fun commentsToJson(items: List<CommentItem>): JSONObject = JSONObject().put(
    "v", JSONArray().apply {
        items.forEach {
            put(
                JSONObject().put("id", it.id).put("user", it.user)
                    .put("avatar", it.avatarUrl ?: "").put("content", it.content)
                    .put("liked", it.liked).put("time", it.time).put("timeMs", it.timeMs)
            )
        }
    }
)

internal fun commentsFromJson(j: JSONObject?): List<CommentItem> {
    val arr = j?.optJSONArray("v") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        CommentItem(
            o.optLong("id"), o.optString("user"),
            o.optString("avatar").takeIf { it.isNotBlank() },
            o.optString("content"), o.optInt("liked"), o.optString("time"),
            o.optLong("timeMs", 0L)
        )
    }
}

internal fun statsToJson(s: PlayStats): JSONObject = JSONObject()
    .put("total", s.totalPlays)
    .put("today", s.todayMinutes)
    .put("week", s.weekMinutes)
    .put("streak", s.streakDays)
    .put("local", s.local)
    .put("topSongs", JSONArray().apply {
        s.topSongs.forEach { put(JSONObject().put("n", it.name).put("a", it.artist).put("p", it.plays)) }
    })
    .put("topArtists", JSONArray().apply {
        s.topArtists.forEach { put(JSONObject().put("n", it.name).put("p", it.plays)) }
    })
    .put("hourly", JSONArray().apply { s.hourly.forEach { put(it) } })

internal fun statsFromJson(j: JSONObject?): PlayStats? {
    j ?: return null
    return PlayStats(
        totalPlays = j.optInt("total"),
        todayMinutes = j.optInt("today"),
        weekMinutes = j.optInt("week"),
        streakDays = j.optInt("streak"),
        topSongs = (0 until (j.optJSONArray("topSongs")?.length() ?: 0)).mapNotNull { i ->
            val o = j.optJSONArray("topSongs")?.optJSONObject(i) ?: return@mapNotNull null
            SongStat(o.optString("n"), o.optString("a"), o.optInt("p"))
        },
        topArtists = (0 until (j.optJSONArray("topArtists")?.length() ?: 0)).mapNotNull { i ->
            val o = j.optJSONArray("topArtists")?.optJSONObject(i) ?: return@mapNotNull null
            ArtistStat(o.optString("n"), o.optInt("p"))
        },
        hourly = (0 until (j.optJSONArray("hourly")?.length() ?: 0)).map {
            j.optJSONArray("hourly")?.optInt(it) ?: 0
        },
        local = j.optBoolean("local", false)
    )
}

internal fun profileToJson(p: UserProfile): JSONObject = JSONObject()
    .put("userId", p.userId).put("nickname", p.nickname)
    .put("avatar", p.avatarUrl ?: "").put("vip", p.vip)

internal fun profileFromJson(j: JSONObject?): UserProfile? {
    j ?: return null
    if (j.optLong("userId", 0L) <= 0L) return null
    return UserProfile(
        j.optLong("userId"), j.optString("nickname"),
        j.optString("avatar").takeIf { it.isNotBlank() }, j.optBoolean("vip")
    )
}

internal fun socialToJson(s: SongSocial): JSONObject = JSONObject()
    .put("like", s.likeCount).put("comments", s.commentCount)

internal fun socialFromJson(j: JSONObject?): SongSocial? {
    j ?: return null
    if (!j.has("like") && !j.has("comments")) return null
    return SongSocial(likeCount = j.optInt("like", -1), commentCount = j.optInt("comments", -1))
}
