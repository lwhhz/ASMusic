package com.kyant.backdrop.catalog

import com.kyant.backdrop.catalog.appstore.music.KugouKrc
import com.kyant.backdrop.catalog.appstore.music.ArtistInfo
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.kyant.backdrop.catalog.appstore.music.AudioQuality
import com.kyant.backdrop.catalog.appstore.music.CacheInfo
import com.kyant.backdrop.catalog.appstore.music.CacheScope
import com.kyant.backdrop.catalog.appstore.music.CommentItem
import com.kyant.backdrop.catalog.appstore.music.DetailTopStyle
import com.kyant.backdrop.catalog.appstore.music.Fixtures
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.MusicBridge
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.PlayStats
import com.kyant.backdrop.catalog.appstore.music.PlayMode
import com.kyant.backdrop.catalog.appstore.music.Playlist
import com.kyant.backdrop.catalog.appstore.music.PlaylistKind
import com.kyant.backdrop.catalog.appstore.music.QrStatus
import com.kyant.backdrop.catalog.appstore.music.ScriptInfo
import com.kyant.backdrop.catalog.appstore.music.SelfCheckItem
import com.kyant.backdrop.catalog.appstore.music.SearchResults
import com.kyant.backdrop.catalog.appstore.music.Song
import com.kyant.backdrop.catalog.appstore.music.SongQuality
import com.kyant.backdrop.catalog.appstore.music.SongStorageInfo
import com.kyant.backdrop.catalog.appstore.music.snapUiScale
import com.kyant.backdrop.catalog.appstore.music.SongStream
import com.kyant.backdrop.catalog.appstore.music.SongSocial
import com.kyant.backdrop.catalog.appstore.music.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Android implementation: Netease API + ExoPlayer + Rhino scripts + SharedPreferences. */
class MusicBridgeImpl(
    private val appContext: Context,
    private val config: AppConfig,
    private val proxy: NeteaseApi,
    private val direct: NeteaseDirect,
    private val cache: MusicCache,
    private val player: MusicPlayer,
    private val openPicker: () -> Unit
) : MusicBridge {

    /** Direct mode talks to music.163.com from the phone itself; proxy mode needs a PC service. */
    private val api: MusicApi
        get() = if (config.directMode) direct else proxy

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val st get() = MusicServices.state
    private val probeHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scriptHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Up-next list, filled by [playFrom] so the capsule's prev/next buttons work. */
    private var queue: List<Song> = emptyList()
    private var queueIndex = 0
    private var playToken = 0
    /** Seek requested before the new stream finished resolving. */
    private var pendingSeekMs: Long? = null
    /** Per-session quality map; /song/detail is not re-requested for every play. */
    private val qualityCache = HashMap<Long, List<SongQuality>>()
    private data class ScriptCandidate(
        val url: String,
        val latencyMs: Long,
        val qualityLevel: String,
        val sourceId: String
    )
    /** Stable random pick for the “随机个人歌单” Today card (one per session). */
    private var randomCustomCard: Playlist? = null
    /** Resolved LX script URLs, valid for a short window to avoid re-running the script. */
    private val scriptUrlCache = HashMap<String, Pair<String, Long>>()

    init {
        // Called by MusicPlayer.toggle() after a network/IO error. Re-resolve the
        // current song and resume from where playback was interrupted.
        player.resumeRetry = {
            val song = st.currentSong
            if (song != null) {
                val quality = targetQualityLevel()
                scriptUrlCache.remove("${song.id}:$quality")
                val position = st.positionMs
                scope.launch {
                    val url = withContext(Dispatchers.IO) { resolveUrl(song) }
                    if (url.isNullOrBlank()) {
                        showToast("重试失败：请检查网络或音源")
                    } else {
                        player.playUrl(song, url, quality)
                        player.seek(position)
                        st.positionMs = position
                    }
                }
            }
        }
    }

    fun initState() {
        st.serverUrl = config.serverUrl
        st.directMode = config.directMode
        st.loggedIn = !config.cookie.isNullOrBlank()
        st.todayKinds = config.todayKinds
            .filter { it != PlaylistKind.NewRelease }
            .ifEmpty { Fixtures.defaultTodayKinds }
        st.capsuleOnLeft = config.capsuleOnLeft
        st.capsuleCorner = config.capsuleCorner
        st.detailTopStyle = runCatching {
            DetailTopStyle.valueOf(config.detailTopStyle)
        }.getOrDefault(DetailTopStyle.Solid)
        st.audioCacheLimitMb = config.audioCacheLimitMb
        st.playMode = runCatching { PlayMode.valueOf(config.playMode) }.getOrDefault(PlayMode.LoopAll)
        st.weekGoalMinutes = config.weekGoalMinutes
        st.wifiQuality = config.wifiQuality
        st.mobileQuality = config.mobileQuality
        st.wordByWord = config.wordByWord
        st.showTranslation = config.showTranslation
        st.lyricGlow = config.lyricGlow
        st.amllFontSize = config.amllFontSize
        st.amllFontWeight = config.amllFontWeight
        st.amllFontFamily = config.amllFontFamily
        st.amllMaskProfanity = config.amllMaskProfanity
        st.amllTtmlEnabled = config.amllTtmlEnabled
        st.amllTtmlSource = config.amllTtmlSource
        st.amllFontFile = config.amllFontFile
        st.amllResumeDelay = config.amllResumeDelay
        st.kugouKrcEnabled = config.kugouKrcEnabled
        st.lyricSynthWords = config.lyricSynthWords
        st.lyricKeepSung = config.lyricKeepSung
        st.keepAmllWarm = config.keepAmllWarm
        st.prefetchNext = config.prefetchNext
        st.waitForCache = config.waitForCache
        st.lyricSpring = config.lyricSpring
        st.lyricFlow = config.lyricFlow
        st.lyricFont = config.lyricFont
        st.translationFont = config.translationFont
        st.backgroundAllowed = BatteryAssist.isFullyAllowed(appContext)
        st.hazeHeightDp = config.hazeHeightDp
        st.hazeOpaqueDp = config.hazeOpaqueDp
        st.hazeBlurDp = config.hazeBlurDp
        st.hazeScrim = config.hazeScrim
        st.uiScale = config.uiScale
        st.detailHideTabs = config.detailHideTabs
        st.fluidBlurDp = config.fluidBlurDp
        st.fluidWarp = config.fluidWarp
        st.fluidSpeed = config.fluidSpeed
        st.fluidDark = config.fluidDark
        refreshScripts()
        scope.launch {
            st.recentSongs = withContext(Dispatchers.IO) { cache.recentSongs(20) }
            st.cacheInfo = withContext(Dispatchers.IO) {
                cache.info().copy(audioBytes = player.audioCacheBytes())
            }
            // Recover a QR authorization that the UI poller missed.
            if (!st.loggedIn) redeemQr()
            if (st.loggedIn) {
                // Cache first so the avatar appears instantly, then refresh it.
                val cached = runCatching { profile() }.getOrNull()
                st.nickname = cached?.nickname ?: "网易云用户"
                st.avatarUrl = cached?.avatarUrl
                launch {
                    runCatching { api.profile() }.getOrNull()?.let { rememberProfile(it) }
                }
            }
        }
    }

    // ---- catalogue (cache first, then real API, then local fallback) ----

    override suspend fun search(q: String): SearchResults {
        if (q.isBlank()) return Fixtures.search(q)
        // v3 key: entries written before covers / artist portraits were resolved are stale.
        val key = "search3:${q.trim().lowercase()}"
        cache.read(key, TTL_DAY)?.let { cached ->
            val value = searchFromJson(cached)
            if (!value.isEmpty) {
                refreshInBackground(key, fetch = { api.search(q) }, encode = { searchToJson(it) })
                return value
            }
        }
        val fresh = api.search(q)
        if (fresh != null && !fresh.isEmpty) {
            cache.write(key, searchToJson(fresh))
            return fresh
        }
        cache.read(key)?.let { stale ->
            val value = searchFromJson(stale)
            if (!value.isEmpty) return value
        }
        return Fixtures.search(q)
    }

    override suspend fun newReleases(): List<Playlist> =
        cachedPlaylists("newsong", Fixtures.newReleases) { api.newReleases() }

    override suspend fun dailySongs(): List<Song> =
        cachedSongs("daily", ttlMs = TTL_DAY) { api.dailySongs() } ?: Fixtures.dailySongs

    override suspend fun toplists(): List<Playlist> =
        cachedPlaylists("toplists", emptyList()) { api.toplists() }

    override suspend fun albumDescription(id: Long): String? {
        val key = "albumdesc2:$id"
        cache.read(key, TTL_WEEK)?.let { cached ->
            val text = cached.optString("d")
            if (text.isNotBlank()) return text
        }
        val fresh = runCatching { api.albumDescription(id) }.getOrNull()
        if (!fresh.isNullOrBlank()) {
            cache.write(key, JSONObject().put("d", fresh))
            return fresh
        }
        return cache.read(key)?.optString("d")?.takeIf { it.isNotBlank() }
    }

    override suspend fun personalPlaylists(): List<Playlist> =
        cachedPlaylists("myplaylists", Fixtures.personalPlaylists) { api.personalPlaylists() }

    override suspend fun playlistSongs(id: Long): List<Song> {
        // 401 / 402 / 406 are Today-page cards, not playlist ids — asking the API for
        // them returns an unrelated playlist (or nothing), which is why the detail
        // page used to show someone else's tracks.
        if (Fixtures.isVirtualCard(id)) {
            val card = todayCards().firstOrNull { it.id == id }
            if (!card?.songs.isNullOrEmpty()) return card.songs
        }
        return cachedSongs("pl:$id") { api.playlistSongs(id) } ?: Fixtures.playlistSongs(id)
    }

    override suspend fun albumSongs(id: Long): List<Song>? =
        cachedSongs("al:$id", allowEmpty = true) { api.albumSongs(id) }

    override suspend fun artistInfo(id: Long): ArtistInfo? {
        val key = "artistinfo:$id"
        cache.read(key, TTL_WEEK)?.let { json ->
            return ArtistInfo(
                id = id,
                avatarUrl = json.optString("avatar").takeIf { it.isNotBlank() },
                brief = json.optString("brief"),
                desc = json.optString("desc")
            )
        }
        val info = api.artistInfo(id) ?: return null
        runCatching {
            cache.write(
                key,
                JSONObject()
                    .put("avatar", info.avatarUrl ?: "")
                    .put("brief", info.brief)
                    .put("desc", info.desc)
            )
        }
        return info
    }

    override suspend fun artistSongs(id: Long): List<Song> =
        cachedSongs("artist:$id", ttlMs = TTL_WEEK) { api.artistSongs(id) } ?: emptyList()

    override suspend fun todayCards(): List<Playlist> {
        val kinds = st.todayKinds
        val fallback = Fixtures.todayCards(kinds)
        if (!st.loggedIn) return fallback

        val daily = dailySongs()
        val mine = personalPlaylists()
        val releases = newReleases()
        // The "我喜欢的音乐" playlist is a real list; parse kind by specialType.
        val liked = mine.firstOrNull { it.kind == PlaylistKind.Liked } ?: mine.firstOrNull()
        val customs = mine.filter { it.kind != PlaylistKind.Liked }
        val randomCustom = randomCustomCard?.takeIf { rc -> customs.any { it.id == rc.id } }
            ?: (customs.randomOrNull() ?: liked)?.also { randomCustomCard = it }
        val newSongs = releases.flatMap { it.songs }.distinctBy { it.id }

        fun pick(index: Int): Playlist? = customs.getOrNull(index) ?: customs.firstOrNull() ?: liked

        return kinds.map { kind ->
            val card: Playlist? = when (kind) {
                PlaylistKind.Daily -> Playlist(
                    id = Fixtures.DAILY_CARD_ID,
                    name = "每日推荐",
                    subtitle = "根据你的口味生成 · ${daily.size} 首",
                    coverUrl = daily.firstOrNull()?.coverUrl,
                    songCount = daily.size,
                    kind = kind,
                    songs = daily
                )
                PlaylistKind.PrivateFm -> {
                    // No dedicated FM endpoint that survives risk control; rotate the
                    // daily list so the card still plays real, personal tracks.
                    val fm = if (daily.size > 1) daily.drop(1) + daily.take(1) else daily
                    Playlist(
                        id = Fixtures.FM_CARD_ID,
                        name = "私人 FM",
                        subtitle = "私人漫游 · ${fm.size} 首",
                        coverUrl = fm.firstOrNull()?.coverUrl,
                        songCount = fm.size,
                        kind = kind,
                        songs = fm
                    )
                }
                PlaylistKind.Liked -> liked?.copy(
                    kind = PlaylistKind.Liked,
                    subtitle = "红心歌曲 · ${liked.songCount} 首"
                )
                PlaylistKind.Exclusive -> pick(1)?.copy(
                    kind = PlaylistKind.Exclusive,
                    subtitle = "共 ${pick(1)?.songCount ?: 0} 首"
                )
                PlaylistKind.Custom -> randomCustom?.let { rc ->
                    rc.copy(
                        kind = PlaylistKind.Custom,
                        name = "随机个人歌单",
                        subtitle = "随机自「${rc.name}」 · ${rc.songCount} 首"
                    )
                }
                PlaylistKind.NewRelease -> Playlist(
                    id = Fixtures.NEW_CARD_ID,
                    name = "新歌速递",
                    subtitle = "本周新上架 · ${newSongs.size} 首",
                    coverUrl = newSongs.firstOrNull()?.coverUrl,
                    songCount = newSongs.size,
                    kind = kind,
                    songs = newSongs
                )
            }
            card ?: fallback.first { it.kind == kind }
        }
    }

    override suspend fun songDetail(id: Long): Song? {
        val key = "song3:$id"
        cache.read(key, TTL_WEEK)?.let { cached ->
            refreshInBackground(key, fetch = { api.songDetail(id) }, encode = { songToJson(it) })
            return songFromJson(cached)
        }
        val fresh = api.songDetail(id)
        if (fresh != null) {
            cache.write(key, songToJson(fresh))
            return fresh
        }
        return cache.read(key)?.let { songFromJson(it) }
    }

    override suspend fun lyric(id: Long): List<LyricLine> {
        // v2 key: older entries carry neither translations nor karaoke timings.
        val key = "lyric2:$id"
        // With word-by-word enabled a cached LRC-only entry is useless, so skip it
        // and fetch once more (the probe then asks for the karaoke version).
        val wantWords = st.wordByWord
        cache.read(key, TTL_WEEK)?.let { cached ->
            val value = lyricFromJson(cached)
            if (value.isNotEmpty() && (!wantWords || value.any { it.words.isNotEmpty() })) {
                refreshInBackground(key, fetch = { api.lyric(id) }, encode = { lyricToJson(it) })
                return value
            }
        }
        var fresh = api.lyric(id)
        // No word-level timings from Netease: try the opt-in Kugou KRC source.
        if (st.kugouKrcEnabled && (fresh.isNullOrEmpty() || fresh.none { it.words.isNotEmpty() })) {
            val song = st.currentSong?.takeIf { it.id == id }
                ?: queue.firstOrNull { it.id == id }
            if (song != null) {
                val krc = withContext(Dispatchers.IO) {
                    runCatching {
                        KugouKrc.lyrics(
                            appContext,
                            song.name,
                            song.artist,
                            song.durationMs,
                            fresh.orEmpty()
                        )
                    }.getOrDefault(emptyList())
                }
                if (krc.isNotEmpty()) fresh = krc
            }
        }
        if (!fresh.isNullOrEmpty()) cache.write(key, lyricToJson(fresh))
        cache.read(key)?.let { stale ->
            val value = lyricFromJson(stale)
            if (value.isNotEmpty()) return value
        }
        return fresh ?: Fixtures.lyric(id)
    }

    override suspend fun comments(id: Long): List<CommentItem> {
        val key = "comments:$id"
        cache.read(key, TTL_DAY)?.let { cached ->
            val value = commentsFromJson(cached)
            if (value.isNotEmpty()) {
                refreshInBackground(key, fetch = { api.comments(id) }, encode = { commentsToJson(it) })
                return value
            }
        }
        val fresh = api.comments(id)
        if (!fresh.isNullOrEmpty()) cache.write(key, commentsToJson(fresh))
        cache.read(key)?.let { stale ->
            val value = commentsFromJson(stale)
            if (value.isNotEmpty()) return value
        }
        return fresh ?: Fixtures.comments(id)
    }

    override suspend fun allComments(id: Long): List<CommentItem> {
        val key = "allcomments2:$id"
        cache.read(key, TTL_DAY)?.let { cached ->
            val value = commentsFromJson(cached)
            if (value.isNotEmpty()) return value
        }
        val fresh = api.allComments(id)
        if (!fresh.isNullOrEmpty()) {
            cache.write(key, commentsToJson(fresh))
            return fresh
        }
        return comments(id)
    }

    override suspend fun songQualities(id: Long): List<SongQuality> {
        qualityCache[id]?.let {
            st.availableQualities = it
            return it
        }
        val list = runCatching { api.songQualities(id) }.getOrNull().orEmpty()
        if (list.isNotEmpty()) qualityCache[id] = list
        st.availableQualities = list
        return list
    }

    private fun isWifi(): Boolean = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching true
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(true)

    private fun targetQualityLevel(): String = st.qualityOverride
        ?: if (isWifi()) st.wifiQuality else st.mobileQuality

    /**
     * Pick the requested quality, falling back to the best playable option the
     * account may use. The boolean is true only when the requested quality exists
     * but is blocked by account permission (that is when the UI shows a toast).
     */
    private fun chooseQuality(
        qualities: List<SongQuality>,
        targetLevel: String
    ): Pair<SongQuality?, Boolean> {
        if (qualities.isEmpty()) return null to false
        val targetRank = AudioQuality.from(targetLevel).rank
        val target = qualities.firstOrNull { it.level == targetLevel }
        if (target != null && target.playable) return target to false
        val fallback = qualities
            .filter { it.playable && it.rank <= targetRank }
            .maxByOrNull { it.rank }
            ?: qualities.filter { it.playable }.maxByOrNull { it.rank }
        return fallback to (target != null)
    }

    override suspend fun songSocial(id: Long): SongSocial? {
        val key = "social:$id"
        cache.read(key, TTL_DAY)?.let { cached ->
            val value = socialFromJson(cached)
            if (value != null) {
                refreshInBackground(
                    key,
                    fetch = { api.songSocial(id) },
                    encode = { socialToJson(it) },
                    keepWhenEmpty = true
                )
                return value
            }
        }
        val fresh = runCatching { api.songSocial(id) }.getOrNull()
        if (fresh != null) {
            cache.write(key, socialToJson(fresh))
            return fresh
        }
        return cache.read(key)?.let { socialFromJson(it) }
    }

    override suspend fun stats(): PlayStats {
        val local = withContext(Dispatchers.IO) { cache.localStats() }
        val remote = remoteStats()
        if (remote == null || remote.topSongs.isEmpty()) {
            return local.copy(
                note = if (!st.loggedIn) "本机统计 · 登录网易云后可同步账号榜单"
                else "本机统计 · 仅统计在本应用内的播放"
            )
        }
        return if (local.totalPlays > 0) {
            local.copy(
                topSongs = remote.topSongs,
                topArtists = remote.topArtists.ifEmpty { local.topArtists },
                note = "播放次数与时长来自本机 · 榜单来自网易云周榜",
                local = true
            )
        } else {
            remote.copy(
                note = "数据来自网易云账号周榜 · 播放时长在播放后开始统计",
                local = false
            )
        }
    }

    override suspend fun profile(): UserProfile? {
        val key = "profile"
        cache.read(key, TTL_HOUR)?.let { cached ->
            refreshInBackground(key, fetch = { api.profile() }, encode = { profileToJson(it) })
            profileFromJson(cached)?.let { return rememberProfile(it) }
        }
        val fresh = api.profile()
        if (fresh != null) {
            cache.write(key, profileToJson(fresh))
            return rememberProfile(fresh)
        }
        cache.read(key)?.let { profileFromJson(it)?.let { p -> return rememberProfile(p) } }
        return if (st.loggedIn) Fixtures.profile else null
    }

    /** Keep the app-wide avatar / nickname in sync whenever the profile is resolved. */
    private fun rememberProfile(p: UserProfile): UserProfile {
        st.avatarUrl = p.avatarUrl
        if (p.nickname.isNotBlank()) st.nickname = p.nickname
        return p
    }

    override suspend fun image(url: String): ImageBitmap? {
        cache.image(url)?.let { return it }
        val bitmap = api.image(url) ?: return null
        return cache.putImage(url, bitmap)
    }

    override suspend fun coverPalette(url: String): List<Int>? = withContext(Dispatchers.IO) {
        val bitmap = runCatching { image(url) }.getOrNull()?.asAndroidBitmap() ?: return@withContext null
        CoverPalette.extract(bitmap, 4)
    }

    override fun shareLyricPoster(song: Song, line: String?, translation: String?) {
        scope.launch {
            val poster = withContext(Dispatchers.IO) {
                val cover = song.coverUrl
                    ?.let { runCatching { image(it) }.getOrNull() }
                    ?.asAndroidBitmap()
                val avatar = st.avatarUrl
                    ?.let { runCatching { image(it) }.getOrNull() }
                    ?.asAndroidBitmap()
                LyricPoster.render(
                    songName = song.name,
                    artist = song.artist,
                    album = song.album,
                    line = line,
                    translation = translation,
                    cover = cover,
                    avatar = avatar,
                    nickname = st.nickname
                )
            }
            runCatching { sharePosterBitmap(poster) }
                .onFailure { showToast("海报分享失败") }
        }
    }

    /** Write the poster into the shared cache and hand it to the system share sheet. */
    private fun sharePosterBitmap(bitmap: Bitmap) {
        val dir = File(appContext.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "lyric-poster-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享歌词海报")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
    }

    // ---- cache helpers ----

    private suspend fun cachedSongs(
        key: String,
        ttlMs: Long = TTL_WEEK,
        allowEmpty: Boolean = false,
        fetch: suspend () -> List<Song>?
    ): List<Song>? {
        cache.read(key, ttlMs)?.let { cached ->
            val value = songsFromJson(cached)
            if (value.isNotEmpty() || allowEmpty) {
                refreshInBackground(
                    key,
                    fetch = { fetch() },
                    encode = { songsToJson(it) },
                    keepWhenEmpty = value.isNotEmpty()
                )
                return value
            }
        }
        val fresh = fetch()
        if (fresh != null && (fresh.isNotEmpty() || allowEmpty)) {
            cache.write(key, songsToJson(fresh))
            return fresh
        }
        // Network failed — serve whatever we had, even if it is past its TTL.
        cache.read(key)?.let { stale ->
            val value = songsFromJson(stale)
            if (value.isNotEmpty() || allowEmpty) return value
        }
        return null
    }

    private suspend fun cachedPlaylists(
        key: String,
        fallback: List<Playlist>,
        ttlMs: Long = TTL_SHORT,
        fetch: suspend () -> List<Playlist>?
    ): List<Playlist> {
        cache.read(key, ttlMs)?.let { cached ->
            val value = playlistsFromJson(cached)
            if (value.isNotEmpty()) {
                refreshInBackground(key, fetch = { fetch() }, encode = { playlistsToJson(it) })
                return value
            }
        }
        val fresh = fetch()
        if (!fresh.isNullOrEmpty()) {
            cache.write(key, playlistsToJson(fresh))
            return fresh
        }
        cache.read(key)?.let { stale ->
            val value = playlistsFromJson(stale)
            if (value.isNotEmpty()) return value
        }
        return fallback
    }

    private suspend fun remoteStats(): PlayStats? {
        val key = "stats"
        cache.read(key, TTL_HOUR)?.let { cached ->
            statsFromJson(cached)?.let { value ->
                refreshInBackground(key, fetch = { api.stats() }, encode = { statsToJson(it) })
                return value
            }
        }
        val fresh = api.stats()
        if (fresh != null) {
            cache.write(key, statsToJson(fresh))
            return fresh
        }
        return cache.read(key)?.let { statsFromJson(it) }
    }

    /**
     * Return cached data immediately, then update the entry in the background so the
     * next visit is fresh even though this one was instant.
     */
    private fun <T> refreshInBackground(
        key: String,
        fetch: suspend () -> T?,
        encode: (T) -> JSONObject,
        keepWhenEmpty: Boolean = false
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = runCatching { fetch() }.getOrNull() ?: return@launch
            if (!keepWhenEmpty && fresh is List<*> && fresh.isEmpty()) return@launch
            runCatching { cache.write(key, encode(fresh)) }
        }
    }

    // ---- QR login ----

    override suspend fun qrKey(): String? = api.qrKey()
    override suspend fun qrImage(key: String): ImageBitmap? = api.qrImage(key)
    override suspend fun qrCheck(key: String): QrStatus = api.qrCheck(key)

    override suspend fun applyCookie(cookie: String) {
        config.cookie = cleanCookie(cookie)
        // Cached lists were fetched anonymously / for another account — drop them.
        cache.clearData()
        st.loggedIn = true
        val profile = runCatching { api.profile() }.getOrNull()
        st.nickname = profile?.nickname ?: "网易云用户"
        st.avatarUrl = profile?.avatarUrl
        if (profile == null) showToast("登录凭证未生效，请重新扫码")
        st.cacheInfo = withContext(Dispatchers.IO) { cache.info() }
    }

    override fun rememberQrKey(key: String) {
        config.lastQrKey = key
    }

    override suspend fun redeemQr(): Boolean {
        val key = config.lastQrKey ?: return false
        val status = runCatching { api.qrCheck(key) }.getOrNull() ?: return false
        val cookie = status.cookie
        if (!status.isSuccess || cookie == null) return false
        applyCookie(cookie)
        config.lastQrKey = null
        return true
    }

    override suspend fun logout() {
        config.cookie = null
        cache.clearData()
        st.loggedIn = false
        st.nickname = null
        st.avatarUrl = null
        st.cacheInfo = withContext(Dispatchers.IO) { cache.info() }
    }

    // ---- playback ----

    override fun play(song: Song) {
        queue = listOf(song)
        queueIndex = 0
        start(song)
    }

    override fun playFrom(songs: List<Song>, song: Song) {
        val list = songs.ifEmpty { listOf(song) }
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        // Picking a song from a list always replaces the up-next queue, even when
        // that very song is already the loaded one.
        queue = list
        queueIndex = index
        st.playQueue = list
        st.queueIndex = index
        if (st.currentSong?.id == song.id) {
            // Never restart a track that is already loaded: only resume when paused.
            if (!st.playing) player.toggle()
            return
        }
        start(song)
    }

    private fun start(song: Song) {
        // Switching songs must cut the previous stream at once. Otherwise track A
        // keeps playing until B's URL is resolved, and only then gets interrupted.
        player.stopNow()
        st.currentSong = song
        st.durationMs = song.durationMs
        st.positionMs = 0
        st.playing = true
        st.playQueue = queue
        st.queueIndex = queueIndex
        pendingSeekMs = null
        // Keep the local play log / recent list in sync for the stats page, and warm
        // the cache for this song so its detail page opens instantly next time.
        scope.launch {
            withContext(Dispatchers.IO) {
                cache.recordPlay(song)
                runCatching { prefetch(song) }
            }
            st.recentSongs = withContext(Dispatchers.IO) { cache.recentSongs(20) }
        }
        val token = ++playToken
        scope.launch {
            val quality = targetQualityLevel()
            val cached = withContext(Dispatchers.IO) {
                player.hasCompleteCache(song.id, quality, song.sizeBytes) &&
                    player.hasCachedUrl(song.id, quality)
            }
            if (token != playToken) return@launch
            if (cached) {
                // Avoid resolving a fresh signed URL just to replay a fully cached
                // song. Media3 reads all bytes from the cache.
                player.playCached(song, quality)
                pendingSeekMs?.let { ms ->
                    player.seek(ms)
                    pendingSeekMs = null
                }
                return@launch
            }
            val url = withContext(Dispatchers.IO) { resolveUrl(song) }
            if (token != playToken) return@launch
            if (url.isNullOrBlank()) {
                st.playing = false
                showToast("JS 音源无法播放：请确认已导入并启用可用音源")
            } else {
                if (st.waitForCache) {
                    // Buffer the whole track first so progress never jumps because of
                    // a half-filled stream buffer.
                    withContext(Dispatchers.IO) {
                        runCatching {
                            withTimeoutOrNull(20_000) {
                                player.prefetchToCache(song.id, quality, url)
                            }
                        }
                    }
                    if (token != playToken) return@launch
                }
                player.playUrl(song, url, quality)
                if (st.prefetchNext) {
                    scope.launch(Dispatchers.IO) { runCatching { prefetchNextTrack(song) } }
                }
                // A seek may have arrived while the URL was still resolving
                // (e.g. tapping a lyric card of a song that was not playing yet).
                pendingSeekMs?.let { ms ->
                    player.seek(ms)
                    pendingSeekMs = null
                }
            }
        }
    }

    override fun next() {
        if (queue.size <= 1) return
        queueIndex = if (st.playMode == PlayMode.Shuffle) {
            // Shuffle stays inside the current queue and never repeats the same song twice.
            var candidate = queueIndex
            while (candidate == queueIndex) candidate = queue.indices.random()
            candidate
        } else {
            (queueIndex + 1) % queue.size
        }
        start(queue[queueIndex])
    }

    override fun previous() {
        if (queue.size <= 1) return
        queueIndex = (queueIndex - 1 + queue.size) % queue.size
        start(queue[queueIndex])
    }

    override fun playQueueIndex(index: Int) {
        if (index !in queue.indices) return
        queueIndex = index
        start(queue[index])
    }

    override fun setPlayMode(mode: PlayMode) {
        config.playMode = mode.name
        st.playMode = mode
    }

    override fun setPlaybackQuality(level: String?) {
        st.qualityOverride = level
        val song = st.currentSong ?: return
        val position = st.positionMs
        val wasPlaying = st.playing
        scope.launch {
            val url = resolveUrl(song)
            if (url.isNullOrBlank()) {
                showToast("JS 音源无法解析当前音质")
                return@launch
            }
            player.playUrl(song, url, targetQualityLevel())
            player.seek(position)
            st.positionMs = position
            if (!wasPlaying) {
                player.pause()
                st.playing = false
            }
        }
    }

    override fun setWifiQuality(level: String) {
        config.wifiQuality = level
        st.wifiQuality = level
    }

    override fun setMobileQuality(level: String) {
        config.mobileQuality = level
        st.mobileQuality = level
    }

    override fun refreshBackgroundPermission() {
        st.backgroundAllowed = BatteryAssist.isFullyAllowed(appContext)
    }

    override fun openBackgroundSettings() {
        BatteryAssist.openSettings(appContext)
    }

    override fun setFluidBlurDp(value: Float) {
        config.fluidBlurDp = value
        st.fluidBlurDp = value.coerceIn(0f, 64f)
    }

    override fun setFluidWarp(value: Float) {
        config.fluidWarp = value
        st.fluidWarp = value.coerceIn(0f, 3f)
    }

    override fun setFluidSpeed(value: Float) {
        config.fluidSpeed = value
        st.fluidSpeed = value.coerceIn(0.1f, 3f)
    }

    override fun setFluidDark(value: Float) {
        config.fluidDark = value
        st.fluidDark = value.coerceIn(0f, 1f)
    }

    override suspend fun selfCheck(): List<SelfCheckItem> = withContext(Dispatchers.IO) {
        checkRuntime()
    }

    override suspend fun selfRepair(): List<SelfCheckItem> = withContext(Dispatchers.IO) {
        scriptUrlCache.clear()
        NodeProcess.stop()
        rewriteSources()
        NodeRuntime.restart(appContext)
        checkRuntime()
    }

    private fun rewriteSources() {
        val directory = File(appContext.filesDir, "node_sources").apply { mkdirs() }
        config.scripts.filter { it.enabled }.forEach { script ->
            val safe = script.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(directory, "$safe.js")
            if (!file.exists() || file.readText() != script.content) {
                file.writeText(script.content)
            }
        }
    }

    private fun checkRuntime(): List<SelfCheckItem> {
        val enabled = config.scripts.filter { it.enabled }
        val executable = File(appContext.applicationInfo.nativeLibraryDir, "libnode_exec.so")
        val directory = File(appContext.filesDir, "node_sources")
        val missing = enabled.count { script ->
            val safe = script.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
            !File(directory, "$safe.js").exists()
        }
        val nodeOk = NodeRuntime.ensure(appContext)
        val notificationOk = Build.VERSION.SDK_INT < 33 ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val batteryOk = BatteryAssist.isFullyAllowed(appContext)
        return listOf(
            SelfCheckItem(
                "JS 音源脚本",
                enabled.isNotEmpty(),
                if (enabled.isEmpty()) "没有启用的音源脚本"
                else "${enabled.size} 个已启用 · ${enabled.joinToString { it.name }}"
            ),
            SelfCheckItem(
                "Node 可执行文件",
                executable.exists(),
                if (executable.exists()) "已安装" else "缺少 libnode_exec.so"
            ),
            SelfCheckItem(
                "音源文件",
                missing == 0,
                if (missing == 0) "全部脚本文件可读" else "$missing 个脚本文件缺失，可一键修复"
            ),
            SelfCheckItem(
                "Node 解析服务",
                nodeOk,
                if (nodeOk) "健康检查通过" else (NodeRuntime.lastError ?: "未启动或端口 32123 无响应")
            ),
            SelfCheckItem(
                "通知权限",
                notificationOk,
                if (notificationOk) "系统媒体控制可用" else "通知被关闭，媒体控制可能不可见"
            ),
            SelfCheckItem(
                "后台常驻权限",
                batteryOk,
                if (batteryOk) "完全允许后台行为" else "建议设为无限制，避免解析服务被回收"
            )
        )
    }


    /**
     * Called by the player when a track finishes naturally. The next action depends on
     * the playback order: 单曲循环 replays, 随机 picks another queue song, 顺序播放 stops
     * after the last song, 循环播放 wraps around.
     */
    fun onTrackEnded() {
        if (queue.isEmpty()) {
            st.playing = false
            return
        }
        when (st.playMode) {
            PlayMode.RepeatOne -> player.replay()
            PlayMode.Shuffle -> {
                if (queue.size <= 1) player.replay() else next()
            }
            PlayMode.LoopAll -> next()
            PlayMode.Sequential -> {
                if (queueIndex < queue.lastIndex) {
                    queueIndex++
                    start(queue[queueIndex])
                } else {
                    // End of the queue in sequential mode: stop, keep the last song loaded.
                    st.playing = false
                    st.positionMs = st.durationMs
                }
            }
        }
    }

    override fun setAudioCacheLimitMb(mb: Int) {
        val value = mb.coerceIn(512, 20480)
        config.audioCacheLimitMb = value
        st.audioCacheLimitMb = value
        player.setAudioCacheLimit(value.toLong() * 1024L * 1024L)
        st.cacheInfo = st.cacheInfo.copy(audioBytes = player.audioCacheBytes())
    }

    override suspend fun cacheInfo(): CacheInfo = withContext(Dispatchers.IO) {
        cache.info().copy(audioBytes = player.audioCacheBytes())
    }.also { st.cacheInfo = it }

    override suspend fun clearCache(scope: CacheScope) {
        withContext(Dispatchers.IO) {
            when (scope) {
                CacheScope.All -> {
                    cache.clearAll()
                    player.clearAudioCache()
                }
                CacheScope.Data -> cache.clearData()
                CacheScope.Images -> cache.clearImages()
                CacheScope.Audio -> player.clearAudioCache()
            }
        }
        st.cacheInfo = withContext(Dispatchers.IO) {
            cache.info().copy(audioBytes = player.audioCacheBytes())
        }
        st.recentSongs = withContext(Dispatchers.IO) { cache.recentSongs(20) }
    }

    private suspend fun resolveUrl(song: Song): String? {
        val json = JSONObject()
            .put("id", song.id)
            .put("name", song.name)
            .put("artist", song.artist)
            .put("album", song.album)
            .put("duration", song.durationMs)
            .put("cover", song.coverUrl ?: "")
            .toString()
        val scriptQuality = targetQualityLevel()
        val cacheKey = "${song.id}:$scriptQuality"
        val cached = scriptUrlCache[cacheKey]?.takeIf {
            System.currentTimeMillis() - it.second < 10 * 60_000L
        }
        if (cached != null && withContext(Dispatchers.IO) { probeUrl(cached.first) }) {
            markScriptQuality(scriptQuality)
            return cached.first
        }
        val candidates = withContext(Dispatchers.IO) {
            runCatching { resolveCandidates(json, scriptQuality) }.getOrDefault(emptyList())
        }
        // Sources often return a syntactically valid but dead URL (404/403). Probe
        // each candidate and keep the first actually playable one.
        for (candidate in candidates) {
            val ok = withContext(Dispatchers.IO) { probeUrl(candidate) }
            if (!ok) continue
            scriptUrlCache[cacheKey] = candidate to System.currentTimeMillis()
            markScriptQuality(scriptQuality)
            return candidate
        }
        st.currentQuality = null
        return null
    }

    /** Cheap liveness probe: any 2xx/3xx response counts. */
    private fun probeUrl(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()
        probeHttp.newCall(request).execute().use { response ->
            response.code in 200..399
        }
    }.getOrDefault(false)


    private suspend fun resolveCandidates(songJson: String, quality: String): List<String> {
        val scripts = config.scripts.filter { it.enabled }
        if (scripts.isEmpty()) return emptyList()
        val results = NodeSourceResolver.resolve(
            context = appContext,
            sources = scripts.map { it.id to it.content },
            songJson = songJson,
            quality = quality
        ) ?: run {
            return emptyList()
        }
        return results
            .filter { !it.url.isNullOrBlank() }
            .sortedBy { it.latencyMs }
            .mapNotNull { it.url }
            .distinct()
    }

    private fun markScriptQuality(level: String) {
        st.currentQuality = SongQuality(AudioQuality.from(level))
    }

    /** Cache lyrics + cover of a played song so its detail page opens instantly next time. */
    /** Downloads the next queue song into the playback cache while this one plays. */
    private suspend fun prefetchNextTrack(current: Song) {
        if (queue.size <= 1) return
        val nextSong = queue.getOrNull((queueIndex + 1) % queue.size) ?: return
        if (nextSong.id == current.id) return
        val quality = targetQualityLevel()
        if (player.hasCompleteCache(nextSong.id, quality, nextSong.sizeBytes)) return
        val url = resolveUrl(nextSong) ?: return
        player.prefetchToCache(nextSong.id, quality, url)
    }

    private suspend fun prefetch(song: Song) {
        val lyricKey = "lyric:${song.id}"
        if (cache.read(lyricKey, TTL_WEEK) == null) {
            api.lyric(song.id)?.takeIf { it.isNotEmpty() }
                ?.let { cache.write(lyricKey, lyricToJson(it)) }
        }
        song.coverUrl?.let { url ->
            if (cache.image(url) == null) {
                api.image(url)?.let { cache.putImage(url, it) }
            }
        }
    }

    override fun toggle() {
        if (st.currentSong == null) return
        player.toggle()
    }

    override fun pause() {
        player.pause()
        st.playing = false
    }

    override fun seek(ms: Long) {
        // ExoPlayer clamps a seek to the very end back to the last decodable sample,
        // which made the bar jump backwards. Stop just short of the end instead so the
        // track plays out normally.
        val duration = st.durationMs
        val upper = if (duration > 600L) duration - 500L else Long.MAX_VALUE
        val target = ms.coerceIn(0L, upper)
        pendingSeekMs = target
        player.seek(target)
    }

    // ---- settings / plugins ----

    override fun setServerUrl(url: String) {
        config.serverUrl = url
        st.serverUrl = config.serverUrl
    }

    override fun setDirectMode(enabled: Boolean) {
        config.directMode = enabled
        st.directMode = enabled
    }

    override fun setTodayKinds(kinds: List<PlaylistKind>) {
        val cleaned = kinds.filter { it != PlaylistKind.NewRelease }
            .ifEmpty { Fixtures.defaultTodayKinds }
        config.todayKinds = cleaned
        st.todayKinds = cleaned
    }

    override fun setCapsuleOnLeft(onLeft: Boolean) {
        config.capsuleOnLeft = onLeft
        st.capsuleOnLeft = onLeft
    }

    override fun setCapsuleCorner(corner: Float) {
        val value = corner.coerceIn(0f, 1f)
        config.capsuleCorner = value
        st.capsuleCorner = value
    }

    override fun setDetailTopStyle(style: DetailTopStyle) {
        config.detailTopStyle = style.name
        st.detailTopStyle = style
    }

    override fun setHazeHeightDp(value: Float) {
        val clamped = value.coerceIn(64f, 420f)
        config.hazeHeightDp = clamped
        st.hazeHeightDp = clamped
        if (config.hazeOpaqueDp > clamped) {
            config.hazeOpaqueDp = clamped
            st.hazeOpaqueDp = clamped
        }
    }

    override fun setHazeOpaqueDp(value: Float) {
        val clamped = value.coerceIn(64f, 320f)
        config.hazeOpaqueDp = clamped
        st.hazeOpaqueDp = clamped
    }

    override fun setHazeBlurDp(value: Float) {
        config.hazeBlurDp = value
        st.hazeBlurDp = value.coerceIn(0f, 140f)
    }

    override fun setDetailHideTabs(enabled: Boolean) {
        config.detailHideTabs = enabled
        st.detailHideTabs = enabled
    }

    override fun setHazeScrim(value: Float) {
        config.hazeScrim = value
        st.hazeScrim = value.coerceIn(0f, 1f)
    }

    override fun setUiScale(value: Float) {
        config.uiScale = value
        st.uiScale = snapUiScale(value)
    }


    override fun setWeekGoal(minutes: Int) {
        val goal = minutes.coerceIn(30, 10080)
        config.weekGoalMinutes = goal
        st.weekGoalMinutes = goal
    }

    override fun setWordByWord(enabled: Boolean) {
        config.wordByWord = enabled
        st.wordByWord = enabled
    }

    override fun setShowTranslation(enabled: Boolean) {
        config.showTranslation = enabled
        st.showTranslation = enabled
    }

    override fun setLyricGlow(value: Float) {
        config.lyricGlow = value
        st.lyricGlow = value
    }

    override fun setLyricFlow(enabled: Boolean) {
        config.lyricFlow = enabled
        st.lyricFlow = enabled
    }

    override fun setLyricSpring(value: Float) {
        config.lyricSpring = value
        st.lyricSpring = value
    }

    override fun setLyricFont(value: Float) {
        config.lyricFont = value
        st.lyricFont = value
    }

    override fun setAmllFontSize(value: Float) {
        config.amllFontSize = value
        st.amllFontSize = value
    }

    override fun setAmllFontWeight(value: Float) {
        config.amllFontWeight = value
        st.amllFontWeight = value
    }

    override fun setAmllFontFamily(value: Int) {
        config.amllFontFamily = value
        st.amllFontFamily = value
    }

    override fun setAmllMaskProfanity(value: Boolean) {
        config.amllMaskProfanity = value
        st.amllMaskProfanity = value
    }

    override fun setAmllTtmlEnabled(value: Boolean) {
        config.amllTtmlEnabled = value
        st.amllTtmlEnabled = value
    }

    override fun setAmllTtmlSource(value: String) {
        config.amllTtmlSource = value
        st.amllTtmlSource = config.amllTtmlSource
    }

    override fun setAmllFontFile(value: String) {
        config.amllFontFile = value
        st.amllFontFile = value
    }

    override fun setAmllResumeDelay(value: Float) {
        config.amllResumeDelay = value
        st.amllResumeDelay = value
    }

    override fun setPrefetchNext(value: Boolean) {
        config.prefetchNext = value
        st.prefetchNext = value
    }

    override fun setKeepAmllWarm(value: Boolean) {
        config.keepAmllWarm = value
        st.keepAmllWarm = value
    }

    override fun setKugouKrcEnabled(value: Boolean) {
        config.kugouKrcEnabled = value
        st.kugouKrcEnabled = value
    }

    override fun setLyricSynthWords(value: Boolean) {
        config.lyricSynthWords = value
        st.lyricSynthWords = value
    }

    override fun setLyricKeepSung(value: Boolean) {
        config.lyricKeepSung = value
        st.lyricKeepSung = value
    }

    override fun setWaitForCache(value: Boolean) {
        config.waitForCache = value
        st.waitForCache = value
    }
    override fun setTranslationFont(value: Float) {
        config.translationFont = value
        st.translationFont = value
    }

    override fun importScript(name: String, content: String): ScriptInfo? =
        persistScript(content, catalogId = "", sourceUrl = "")

    override suspend fun importScriptFromUrl(url: String, catalogId: String): ScriptInfo? {
        val text = withContext(Dispatchers.IO) { fetchScriptText(url) } ?: return null
        return withContext(Dispatchers.Main.immediate) {
            persistScript(text, catalogId = catalogId, sourceUrl = url)
        }
    }

    private fun persistScript(
        content: String,
        catalogId: String,
        sourceUrl: String
    ): ScriptInfo? {
        val info = ScriptEngine.info(content) ?: return null
        val existing = config.scripts.firstOrNull { record ->
            (catalogId.isNotBlank() && record.catalogId == catalogId) ||
                (sourceUrl.isNotBlank() && record.sourceUrl == sourceUrl) ||
                record.name == info.name
        }
        val record = existing?.copy(
            name = info.name,
            version = info.version,
            description = info.description,
            content = content,
            catalogId = catalogId,
            sourceUrl = sourceUrl
        ) ?: AppConfig.ScriptRecord(
            id = "s_${System.currentTimeMillis()}",
            name = info.name,
            version = info.version,
            description = info.description,
            enabled = true,
            content = content,
            catalogId = catalogId,
            sourceUrl = sourceUrl
        )
        config.scripts = config.scripts.filter {
            it.id != record.id &&
                it.name != record.name &&
                (catalogId.isBlank() || it.catalogId != catalogId)
        } + record
        scriptUrlCache.clear()
        refreshScripts()
        return ScriptInfo(
            record.id,
            record.name,
            record.version,
            record.description,
            record.enabled,
            record.catalogId,
            record.sourceUrl
        )
    }

    /** Downloads only HTTPS scripts. Raw GitHub URLs are retried via gh-proxy.org. */
    private fun fetchScriptText(rawUrl: String): String? {
        val url = rawUrl.trim()
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https") return null
        val candidates = buildList {
            add(parsed.toString())
            when (parsed.host) {
                "raw.githubusercontent.com" ->
                    add("https://gh-proxy.org/" + parsed.toString().removePrefix("https://"))
                "gh-proxy.org" -> {
                    val path = parsed.encodedPath
                    if (path.startsWith("/raw.githubusercontent.com/")) {
                        val query = parsed.encodedQuery?.let { "?$it" } ?: ""
                        add("https://" + path.removePrefix("/") + query)
                    }
                }
            }
        }.distinct()
        for (candidate in candidates) {
            val text = fetchOne(candidate) ?: continue
            if (ScriptEngine.info(text) != null) return text
        }
        return null
    }

    private fun fetchOne(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleMusic/1.3.5")
            .header("Accept", "text/javascript, application/javascript, text/plain, */*")
            .build()
        scriptHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val declared = response.body?.contentLength() ?: -1L
            if (declared > MAX_SCRIPT_BYTES) return@use null
            val text = response.body?.string() ?: return@use null
            if (text.toByteArray(Charsets.UTF_8).size > MAX_SCRIPT_BYTES) null else text
        }
    }.getOrNull()

    override suspend fun songStorageInfo(songId: Long): SongStorageInfo =
        withContext(Dispatchers.IO) { player.songStorageInfo(songId) }

    override fun setScriptEnabled(id: String, enabled: Boolean) {
        config.scripts = config.scripts.map { if (it.id == id) it.copy(enabled = enabled) else it }
        scriptUrlCache.clear()
        refreshScripts()
    }

    override fun removeScript(id: String) {
        config.scripts = config.scripts.filter { it.id != id }
        scriptUrlCache.clear()
        refreshScripts()
    }

    override fun loadSampleScript() {
        importScript("sample-source.js", Fixtures.SAMPLE_SCRIPT)
    }

    override fun openScriptPicker() {
        openPicker()
    }

    override fun showToast(text: String) {
        st.toast = text
    }

    private fun refreshScripts() {
        st.scripts = config.scripts.map {
            ScriptInfo(
                it.id,
                it.name,
                it.version,
                it.description,
                it.enabled,
                it.catalogId,
                it.sourceUrl
            )
        }
    }

    fun release() {
        player.release()
        scope.cancel()
    }

    private companion object {
        const val TTL_HOUR = 3_600_000L
        const val TTL_SHORT = 6 * TTL_HOUR
        const val TTL_DAY = 24 * TTL_HOUR
        const val TTL_WEEK = 7 * TTL_DAY
        const val MAX_SCRIPT_BYTES = 2_000_000L
    }
}
