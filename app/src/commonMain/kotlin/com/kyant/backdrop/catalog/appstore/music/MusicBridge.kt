package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** Observable app-wide state. The Android implementation writes into it; UI only reads. */
data class SelfCheckItem(
    val label: String,
    val ok: Boolean,
    val detail: String
)

class MusicState {
    var currentSong by mutableStateOf<Song?>(null)
    var playing by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var loggedIn by mutableStateOf(false)
    var nickname by mutableStateOf<String?>(null)
    /** Remote avatar of the signed-in Netease account; null while signed out. */
    var avatarUrl by mutableStateOf<String?>(null)
    var serverUrl by mutableStateOf("http://127.0.0.1:3000")
    /** true = connect to music.163.com directly; false = use a self-hosted proxy. */
    var directMode by mutableStateOf(true)
    var scripts by mutableStateOf<List<ScriptInfo>>(emptyList())
    var todayKinds by mutableStateOf(Fixtures.defaultTodayKinds)
    /** Dock the playback capsule to the left edge instead of the right one. */
    var capsuleOnLeft by mutableStateOf(false)
    /** Up-next queue: replaced by the list a song was picked from. */
    var playQueue by mutableStateOf<List<Song>>(emptyList())
    var queueIndex by mutableStateOf(0)
    /** Playback order (顺序 / 循环 / 单曲 / 随机). */
    var playMode by mutableStateOf(PlayMode.LoopAll)
    /** UI-only: the play-queue bottom sheet is open. */
    var queueSheetOpen by mutableStateOf(false)
    /**
     * Corner radius of the capsule **on the song detail page** (its expanded
     * full-width form). 1 = full capsule; 0..~0.99 = rounded rectangle.
     * The compact floating capsule always keeps the full capsule shape.
     */
    var capsuleCorner by mutableStateOf(1f)
    /** Weekly listening goal in minutes, editable from the stats page. */
    var weekGoalMinutes by mutableStateOf(700)
    // ---- audio quality ----
    /** Qualities offered by Netease for the current song, low → high. */
    var availableQualities by mutableStateOf<List<SongQuality>>(emptyList())
    /** Effective quality of the stream currently being resolved. */
    var currentQuality by mutableStateOf<SongQuality?>(null)
    var qualitySheetOpen by mutableStateOf(false)
    /** Ceiling picked in settings; the per-song button overrides it for this session. */
    var wifiQuality by mutableStateOf("lossless")
    var mobileQuality by mutableStateOf("exhigh")
    var qualityOverride by mutableStateOf<String?>(null)
    // ---- lyrics preferences (Customize page) ----
    /** Karaoke (word-by-word) lyrics when Netease provides yrc timings. */
    var wordByWord by mutableStateOf(true)
    /** Show the machine translation under each lyric line when available. */
    var showTranslation by mutableStateOf(true)
    /** Song-detail sticky header treatment. */
    var detailTopStyle by mutableStateOf(DetailTopStyle.Solid)
    /** Haze panel height in dp (how far down the blur reaches). */
    var hazeHeightDp by mutableStateOf(262f)
    /** Height of the solid band before the progressive fade starts, in dp. */
    var hazeOpaqueDp by mutableStateOf(150f)
    /** Peak blur radius of the progressive top bar, in dp. */
    var hazeBlurDp by mutableStateOf(80f)
    /** Hide the liquid bottom tab bar on the song detail page. */
    var detailHideTabs by mutableStateOf(false)
    /** Extra dark gradient over the Haze panel, 0..1. */
    var hazeScrim by mutableStateOf(0.55f)
    /** Global UI scale (density multiplier). 0.8..1.3; lyrics cards are excluded. */
    var uiScale by mutableStateOf(1f)
    /** 0..1 — blur / glow strength of the lyric-card sweep. */
    var lyricGlow by mutableStateOf(0.55f)
    /** 0..1 — how springy the per-word sweep follows playback. */
    var lyricSpring by mutableStateOf(0.5f)
    /** Animate the lyric-card aurora / sheen instead of a static gradient. */
    var lyricFlow by mutableStateOf(false)
    /** Lyric text size in sp. */
    var lyricFont by mutableStateOf(23f)
    // ---- immersive (AMLL) ----
    var amllFontSize by mutableStateOf(34f)
    var amllFontWeight by mutableStateOf(700f)
    var amllFontFamily by mutableStateOf(0)
    var amllTtmlEnabled by mutableStateOf(true)
    var amllTtmlSource by mutableStateOf("https://amlldb.bikonoo.com/%p/%s.ttml")
    /** Absolute path of an imported font file; empty = system family. */
    var amllFontFile by mutableStateOf("")
    var amllMaskProfanity by mutableStateOf(true)
    var posterConfig by mutableStateOf(PosterConfig())
    var amllStaticBg by mutableStateOf(false)
    var heartMode by mutableStateOf(false)
    var kugouKrcEnabled by mutableStateOf(false)
    var lyricSynthWords by mutableStateOf(true)
    var lyricKeepSung by mutableStateOf(true)
    var keepAmllWarm by mutableStateOf(true)
    var prefetchNext by mutableStateOf(true)
    var waitForCache by mutableStateOf(true)
    var amllResumeDelay by mutableStateOf(1.5f)
    /** Translation text size in sp. */
    var translationFont by mutableStateOf(15f)
    var toast by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    /** Songs played inside this app, newest first (persisted by the Android cache). */
    var recentSongs by mutableStateOf<List<Song>>(emptyList())
    var cacheInfo by mutableStateOf(CacheInfo())
    /** Audio disk cache limit in MiB; 512..20480. */
    var audioCacheLimitMb by mutableStateOf(512)
    /** True when the OS allows unrestricted background work (battery/Doze/app-op). */
    var backgroundAllowed by mutableStateOf(false)
    /** Immersive lyric fluid background tuning. */
    var fluidBlurDp by mutableStateOf(30f)
    var fluidWarp by mutableStateOf(1.4f)
    var fluidSpeed by mutableStateOf(1f)
    var fluidDark by mutableStateOf(0.5f)
}

/**
 * Single seam between pure-Compose UI (commonMain) and Android-only capabilities
 * (network, ExoPlayer, Rhino, SharedPreferences, QR login).
 *
 * Every method has a local-fixture default so the UI is fully usable offline;
 * the Android module overrides them with real Netease Cloud Music calls.
 */
interface MusicBridge {
    val state: MusicState get() = MusicServices.state

    // ---- catalogue ----
    suspend fun search(q: String): SearchResults = Fixtures.search(q)
    suspend fun newReleases(): List<Playlist> = Fixtures.newReleases
    /** 最新专辑，用于新歌页顶部大卡。 */
    suspend fun newAlbums(): List<Playlist> = emptyList()
    /** Netease "all charts" list (排行榜). */
    suspend fun toplists(): List<Playlist> = emptyList()
    suspend fun dailySongs(): List<Song> = Fixtures.dailySongs
    suspend fun personalPlaylists(): List<Playlist> = Fixtures.personalPlaylists
    suspend fun playlistSongs(id: Long): List<Song> = Fixtures.playlistSongs(id)
    /** Songs of an album (used by new-release slides, which carry album ids). */
    suspend fun albumSongs(id: Long): List<Song>? = null
    /** Netease artist top songs (artist page). */
    suspend fun artistSongs(id: Long): List<Song> = emptyList()
    suspend fun artistInfo(id: Long): ArtistInfo? = null
    /** Album blurb from Netease, shown on the album page. */
    suspend fun albumDescription(id: Long): String? = null
    suspend fun todayCards(): List<Playlist> = Fixtures.todayCards(state.todayKinds)
    suspend fun songDetail(id: Long): Song? = Fixtures.song(id)
    suspend fun lyric(id: Long): List<LyricLine> = Fixtures.lyric(id)
    suspend fun comments(id: Long): List<CommentItem> = Fixtures.comments(id)
    /** All comments (up to 100) for the “查看全部评论” sheet. */
    suspend fun allComments(id: Long): List<CommentItem> = comments(id)
    /** Qualities Netease offers for this song and whether the account can play them. */
    suspend fun songQualities(id: Long): List<SongQuality> = emptyList()
    /** Netease-side like (红心) and comment counts of a song. */
    suspend fun songSocial(id: Long): SongSocial? = null
    suspend fun stats(): PlayStats = Fixtures.stats
    suspend fun profile(): UserProfile? = if (state.loggedIn) Fixtures.profile else null
    suspend fun image(url: String, maxEdge: Int = 512): ImageBitmap? = null
    /**
     * Representative cover colors (ARGB ints), dominant first — drives the lyric-card
     * gradients and the share-poster aurora. Null when the cover cannot be loaded.
     */
    suspend fun coverPalette(url: String): List<Int>? = null
    /** Share a lyric-poster PNG (aurora background sampled from the cover). */
    fun shareLyricPoster(song: Song, line: String?, translation: String?) {}

    // ---- QR login ----
    suspend fun qrKey(): String? = null
    suspend fun qrImage(key: String): ImageBitmap? = null
    suspend fun qrCheck(key: String): QrStatus = QrStatus(800, "未连接服务", null)
    suspend fun applyCookie(cookie: String) {
        state.loggedIn = true
        state.nickname = "网易云用户"
    }
    suspend fun logout() {
        state.loggedIn = false
        state.nickname = null
        state.avatarUrl = null
    }
    /** Persist the QR key so an authorization can be redeemed after a restart. */
    fun rememberQrKey(key: String) {}
    suspend fun redeemQr(): Boolean = false

    // ---- playback ----
    fun play(song: Song) {
        state.currentSong = song
        state.durationMs = song.durationMs
        state.positionMs = 0
        state.playing = true
        state.recentSongs = (listOf(song) + state.recentSongs.filter { it.id != song.id }).take(20)
    }
    /** Play [song] with [songs] as the up-next queue (prev/next walk this list). */
    fun playFrom(songs: List<Song>, song: Song) = play(song)
    /**
     * Row play buttons must pause / resume the current track instead of restarting it.
     */
    fun toggleSong(songs: List<Song>, song: Song) {
        if (state.currentSong?.id == song.id) {
            if (state.playing) pause() else toggle()
        } else {
            playFrom(songs, song)
        }
    }
    fun toggle() { state.playing = !state.playing }
    fun pause() { state.playing = false }
    fun seek(ms: Long) { state.positionMs = ms }
    fun next() {}
    fun previous() {}
    /** Jump to a song inside the current queue (play-queue sheet). */
    fun playQueueIndex(index: Int) {}
    fun setPlayMode(mode: PlayMode) { state.playMode = mode }
    /** Re-resolve the current stream at [level] (null = follow the network setting). */
    fun setPlaybackQuality(level: String?) { state.qualityOverride = level }
    fun setWifiQuality(level: String) { state.wifiQuality = level }
    fun setMobileQuality(level: String) { state.mobileQuality = level }

    /** Re-read OEM battery / Doze restrictions. */
    fun refreshBackgroundPermission() {}
    /** Open the OEM battery settings page for this app. */
    fun openBackgroundSettings() {}

    /** Health checks shown on the settings page. */
    suspend fun selfCheck(): List<SelfCheckItem> = emptyList()
    /** Rebuilds the Node resolver and source files. */
    suspend fun selfRepair(): List<SelfCheckItem> = emptyList()

    fun setFluidBlurDp(value: Float) { state.fluidBlurDp = value.coerceIn(0f, 64f) }
    fun setFluidWarp(value: Float) { state.fluidWarp = value.coerceIn(0f, 3f) }
    fun setFluidSpeed(value: Float) { state.fluidSpeed = value.coerceIn(0.1f, 3f) }
    fun setFluidDark(value: Float) { state.fluidDark = value.coerceIn(0f, 1f) }

    // ---- cache ----
    suspend fun cacheInfo(): CacheInfo = CacheInfo()
    fun setAudioCacheLimitMb(mb: Int) {}
    suspend fun clearCache(scope: CacheScope) {}

    // ---- settings / plugins ----
    fun setServerUrl(url: String) { state.serverUrl = url }
    fun setDirectMode(enabled: Boolean) { state.directMode = enabled }
    fun setTodayKinds(kinds: List<PlaylistKind>) { state.todayKinds = kinds }
    fun setCapsuleOnLeft(onLeft: Boolean) { state.capsuleOnLeft = onLeft }
    fun setCapsuleCorner(corner: Float) { state.capsuleCorner = corner.coerceIn(0f, 1f) }

    fun setDetailTopStyle(style: DetailTopStyle) { state.detailTopStyle = style }
    fun setHazeHeightDp(value: Float) {
        val clamped = value.coerceIn(HazeBarDp, 420f)
        state.hazeHeightDp = clamped
        // The opaque band can never be taller than the panel itself.
        if (state.hazeOpaqueDp > clamped) state.hazeOpaqueDp = clamped
    }
    fun setHazeOpaqueDp(value: Float) { state.hazeOpaqueDp = value.coerceIn(HazeBarDp, 320f) }
    fun setHazeBlurDp(value: Float) { state.hazeBlurDp = value.coerceIn(0f, 140f) }
    fun setDetailHideTabs(enabled: Boolean) { state.detailHideTabs = enabled }
    fun setHazeScrim(value: Float) { state.hazeScrim = value.coerceIn(0f, 1f) }
    fun setUiScale(value: Float) { state.uiScale = snapUiScale(value) }
    /** Apply a Haze preset: (height, opaque, blur). */
    fun applyHazePreset(height: Float, opaque: Float, blur: Float) {
        setHazeHeightDp(height)
        setHazeOpaqueDp(opaque)
        setHazeBlurDp(blur)
    }
    fun setWeekGoal(minutes: Int) { state.weekGoalMinutes = minutes.coerceIn(30, 10080) }
    fun setWordByWord(enabled: Boolean) { state.wordByWord = enabled }
    fun setShowTranslation(enabled: Boolean) { state.showTranslation = enabled }
    fun setLyricGlow(value: Float) { state.lyricGlow = value.coerceIn(0f, 1f) }
    fun setLyricSpring(value: Float) { state.lyricSpring = value.coerceIn(0f, 1f) }
    fun setLyricFlow(enabled: Boolean) { state.lyricFlow = enabled }
    fun setLyricFont(value: Float) { state.lyricFont = value.coerceIn(16f, 34f) }
    fun setAmllFontSize(value: Float) { state.amllFontSize = value.coerceIn(20f, 64f) }
    fun setAmllFontWeight(value: Float) { state.amllFontWeight = value.coerceIn(300f, 900f) }
    fun setAmllFontFamily(value: Int) { state.amllFontFamily = value.coerceIn(0, 4) }
    fun setAmllTtmlEnabled(value: Boolean) { state.amllTtmlEnabled = value }
    fun setAmllTtmlSource(value: String) {
        state.amllTtmlSource = value.ifBlank { "https://amlldb.bikonoo.com/%p/%s.ttml" }
    }
    fun setAmllFontFile(value: String) { state.amllFontFile = value }
    fun setAmllMaskProfanity(value: Boolean) { state.amllMaskProfanity = value }
    fun setPosterConfig(value: PosterConfig) { state.posterConfig = value }
    fun setAmllStaticBg(value: Boolean) { state.amllStaticBg = value }
    fun setHeartMode(value: Boolean) { state.heartMode = value }
    fun setKugouKrcEnabled(value: Boolean) { state.kugouKrcEnabled = value }
    fun setLyricSynthWords(value: Boolean) { state.lyricSynthWords = value }
    fun setLyricKeepSung(value: Boolean) { state.lyricKeepSung = value }
    fun setKeepAmllWarm(value: Boolean) { state.keepAmllWarm = value }
    fun setPrefetchNext(value: Boolean) { state.prefetchNext = value }
    fun setWaitForCache(value: Boolean) { state.waitForCache = value }
    fun setAmllResumeDelay(value: Float) { state.amllResumeDelay = value.coerceIn(0f, 10f) }
    fun setTranslationFont(value: Float) { state.translationFont = value.coerceIn(10f, 24f) }
    fun importScript(name: String, content: String): ScriptInfo? = null
    suspend fun importScriptFromUrl(url: String, catalogId: String = ""): ScriptInfo? = null
    suspend fun songStorageInfo(songId: Long): SongStorageInfo = SongStorageInfo()
    fun setScriptEnabled(id: String, enabled: Boolean) {}
    fun removeScript(id: String) {}
    fun loadSampleScript() {}
    fun openScriptPicker() {}
    fun showToast(text: String) { state.toast = text }
}

enum class DetailTopStyle(val label: String) {
    Solid("纯色"),
    GradientBlur("Haze 渐进模糊")
}

object MusicServices {
    val state = MusicState()
    var bridge: MusicBridge = object : MusicBridge {}
}
