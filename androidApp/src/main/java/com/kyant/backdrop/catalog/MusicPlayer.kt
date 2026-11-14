package com.kyant.backdrop.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kyant.backdrop.catalog.appstore.music.MusicState
import com.kyant.backdrop.catalog.appstore.music.Song
import com.kyant.backdrop.catalog.appstore.music.SongStorageInfo
import java.io.File

/** Real playback via Media3/ExoPlayer + a 200ms position ticker for the lyric cards. */
class MusicPlayer(
    context: Context,
    private val state: MusicState,
    private val onError: (String) -> Unit,
    private val onEnded: () -> Unit = {},
    cacheLimitBytes: Long = 512L * 1024 * 1024
) {
    private val appContext = context
    val exoPlayer: ExoPlayer get() = player
    /**
     * Set by the bridge after construction. When a network/IO error interrupted
     * playback, [toggle] calls this so the URL is resolved and prepared again
     * instead of a bare [ExoPlayer.play] on a dead media item.
     */
    @Volatile var resumeRetry: (() -> Unit)? = null
    @Volatile private var needsReload = false
    private val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val cacheUrlPrefs = context.getSharedPreferences("audio_cache_urls", Context.MODE_PRIVATE)
    private val cacheEvictor = ConfigurableCacheEvictor(cacheLimitBytes)
    private val audioCache = SimpleCache(
        File(context.filesDir, "audio_cache"),
        cacheEvictor,
        StandaloneDatabaseProvider(context)
    )
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(audioCache)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        .build()
    private val handler = Handler(Looper.getMainLooper())
    private var tickerRunning = false
    private var released = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!tickerRunning) return
            state.positionMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (d > 0) state.durationMs = d
            if (tickerRunning) handler.postDelayed(this, 500L)
        }
    }

    /** The ticker only runs while audio is actually playing (saves idle wakeups). */
    private fun startTicker() {
        if (!tickerRunning) {
            tickerRunning = true
            handler.post(ticker)
        }
    }

    private fun stopTicker() {
        tickerRunning = false
        handler.removeCallbacks(ticker)
        if (released) return
        state.positionMs = player.currentPosition.coerceAtLeast(0L)
        val d = player.duration
        if (d > 0) state.durationMs = d
    }

    init {
        // Older builds cached Netease encrypted streams under song_* keys. Those
        // cannot be decoded from disk; drop them once and namespace JS streams.
        if (cacheUrlPrefs.getInt("cache_policy_version", 0) < 2) {
            runCatching {
                audioCache.keys.toList().forEach { key ->
                    if (!key.startsWith("js_")) audioCache.removeResource(key)
                }
            }
            cacheUrlPrefs.edit().putInt("cache_policy_version", 2).apply()
        }
        cacheEvictor.setLimit(audioCache, cacheLimitBytes)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.playing = isPlaying
                if (isPlaying) {
                    startTicker()
                } else {
                    stopTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!released && playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                // A network hiccup leaves the media item unusable; the next play
                // press must re-resolve / re-prepare instead of just play().
                needsReload = true
                state.playing = false
                stopTicker()
                onError("播放失败：${error.message ?: "音源不可访问"}")
            }
        })
    }

    fun playUrl(song: Song, url: String, quality: String) {
        val cacheKey = cacheKey(song.id, quality)
        cacheUrlPrefs.edit().putString(cacheKey, url).apply()
        startMedia(url, cacheKey, song)
    }

    /** Streams [url] into the playback cache so a later play/seek needs no network. */
    fun prefetchToCache(songId: Long, quality: String, url: String) {
        val key = cacheKey(songId, quality)
        runCatching {
            cacheUrlPrefs.edit().putString(key, url).apply()
            val dataSource = cacheDataSourceFactory.createDataSource()
            val spec = DataSpec.Builder()
                .setUri(Uri.parse(url))
                .setKey(key)
                .build()
            dataSource.open(spec)
            try {
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = dataSource.read(buffer, 0, buffer.size)
                    if (read == -1) break
                    total += read
                    if (total > 300L * 1024L * 1024L) break
                }
            } finally {
                runCatching { dataSource.close() }
            }
        }
    }

    fun hasCachedUrl(songId: Long, quality: String): Boolean =
        cacheUrlPrefs.contains(cacheKey(songId, quality))

    /** 冷启动时预热 SimpleCache 索引：首次 isCached 会触发磁盘扫描。 */
    fun warmCache() {
        runCatching { audioCache.isCached("__warm__", 0L, 1L) }
    }

    fun hasCompleteCache(songId: Long, quality: String, expectedBytes: Long): Boolean {
        val key = cacheKey(songId, quality)
        return runCatching {
            if (expectedBytes <= 0L) {
                // 推荐接口经常不带文件大小；此时只要缓存里已有数据就直接播放，
                // 缺的部分 CacheDataSource 会补，避免每次都重新解析 URL。
                audioCache.isCached(key, 0L, 1L)
            } else {
                audioCache.isCached(key, 0L, expectedBytes)
            }
        }.getOrDefault(false)
    }

    /** Play a fully cached song without resolving a fresh, signed stream URL. */
    fun playCached(song: Song, quality: String) {
        val key = cacheKey(song.id, quality)
        val cachedUrl = cacheUrlPrefs.getString(key, null)
            ?: "https://cache.local/js_${song.id}.mp3"
        startMedia(cachedUrl, key, song)
    }

    private fun startMedia(uri: String, cacheKey: String, song: Song) {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.coverUrl?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setCustomCacheKey(cacheKey)
                .setMediaMetadata(metadata)
                .build()
        )
        needsReload = false
        player.prepare()
        player.play()
        state.playing = true
        appContext.startService(Intent(appContext, PlaybackService::class.java))
        PlaybackService.refresh(song)
        startTicker()
    }

    private fun cacheKey(songId: Long, quality: String): String =
        "js_${songId}_${quality}"

    fun setAudioCacheLimit(bytes: Long) {
        cacheEvictor.setLimit(audioCache, bytes.coerceAtLeast(0L))
    }

    fun audioCacheBytes(): Long = runCatching { audioCache.getCacheSpace() }.getOrDefault(0L)

    fun clearAudioCache() {
        runCatching { audioCache.keys.toList().forEach { audioCache.removeResource(it) } }
    }

    /** Local cache/download size. Future download support only needs to write <id>.* here. */
    fun songStorageInfo(songId: Long): SongStorageInfo {
        val downloaded = runCatching {
            downloadsDir.listFiles { file ->
                file.isFile && file.name.substringBefore('.').toLongOrNull() == songId
            }?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)
        val cached = runCatching {
            audioCache.getKeys()
                .filter { it.startsWith("js_${songId}_") }
                .sumOf { key ->
                    audioCache.getCachedSpans(key).sumOf { span ->
                        if (span.isCached) span.length else 0L
                    }
                }
        }.getOrDefault(0L)
        return SongStorageInfo(
            cachedBytes = cached.coerceAtLeast(0L),
            downloadedBytes = downloaded,
            downloaded = downloaded > 0L
        )
    }

    fun toggle() {
        if (player.isPlaying) {
            player.pause()
        } else {
            resumePlay()
        }
    }

    /** Play (or retry a failed network stream). Used by the system media session too. */
    fun resumePlay() {
        val retry = resumeRetry
        if (needsReload && retry != null) {
            needsReload = false
            retry()
        } else {
            player.play()
        }
    }

    fun pause() {
        player.pause()
    }

    /**
     * Stop immediately and drop the current media item. Used when switching songs:
     * the previous track must not keep playing while the new URL is being resolved.
     */
    fun stopNow() {
        runCatching {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        state.playing = false
        state.positionMs = 0L
        needsReload = false
        stopTicker()
    }

    /** Single-song repeat: restart the current stream without resolving a new URL. */
    fun replay() {
        player.seekTo(0)
        player.play()
        state.positionMs = 0L
    }

    fun seek(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
        state.positionMs = ms.coerceAtLeast(0L)
    }

    fun release() {
        released = true
        tickerRunning = false
        handler.removeCallbacks(ticker)
        player.release()
        audioCache.release()
    }
}

/** LRU evictor with a runtime-adjustable byte limit (512MB..5GB in the UI). */
private class ConfigurableCacheEvictor(
    @Volatile private var maxBytes: Long
) : CacheEvictor {
    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        evict(cache)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        evict(cache)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) = Unit

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        evict(cache)
    }

    fun setLimit(cache: Cache, bytes: Long) {
        maxBytes = bytes.coerceAtLeast(0L)
        evict(cache)
    }

    private fun evict(cache: Cache) {
        if (maxBytes <= 0L || cache.cacheSpace <= maxBytes) return
        val spans = cache.keys
            .flatMap { key -> cache.getCachedSpans(key) }
            .filter { it.isCached && it.length > 0L }
            .sortedWith(
                compareBy<CacheSpan> { it.lastTouchTimestamp }
                    .thenBy { span -> runCatching { span.file?.lastModified() ?: 0L }.getOrDefault(0L) }
            )
        for (span in spans) {
            if (cache.cacheSpace <= maxBytes) break
            runCatching { cache.removeSpan(span) }
        }
    }
}
