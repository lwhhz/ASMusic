package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.runtime.Immutable

enum class PlaylistKind { Daily, PrivateFm, Exclusive, Liked, Custom, NewRelease }

/** Playback order of the up-next queue. */
enum class PlayMode {
    /** 顺序播放：播完整个列表后停止。 */
    Sequential,
    /** 循环播放：列表播完后从头再来。 */
    LoopAll,
    /** 单曲循环。 */
    RepeatOne,
    /** 随机顺序：只在当前列表内随机。 */
    Shuffle;

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    val label: String
        get() = when (this) {
            Sequential -> "顺序播放"
            LoopAll -> "循环播放"
            RepeatOne -> "单曲循环"
            Shuffle -> "随机播放"
        }
}

@Immutable
data class Song(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String = "",
    val albumId: Long = 0L,
    val durationMs: Long = 210_000L,
    val coverUrl: String? = null,
    val trackNo: Int = 1,
    val genre: String = "流行",
    val commentCount: Int = 0,
    val favoriteCount: Int = 0,
    val bitrateKbps: Int = 320,
    val sizeBytes: Long = 0L,
    /** Netease artist id of the first artist; 0 when unknown. */
    val artistId: Long = 0L,
    /** All credited artists, in Netease order. */
    val artists: List<ArtistBrief> = emptyList()
) {
    val durationText: String
        get() {
            val total = durationMs / 1000
            val m = total / 60
            val s = total % 60
            return "$m:${if (s < 10) "0" else ""}$s"
        }

    val sizeText: String
        get() = when {
            sizeBytes <= 0L -> "—"
            sizeBytes >= 1_048_576L -> "${(sizeBytes / 104_857.6).toLong() / 10.0} MB"
            else -> "${sizeBytes / 1024} KB"
        }
}

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val subtitle: String,
    val coverUrl: String? = null,
    val songCount: Int = 0,
    val kind: PlaylistKind = PlaylistKind.Custom,
    val songs: List<Song> = emptyList()
)

@Immutable
data class ArtistBrief(val id: Long, val name: String, val coverUrl: String? = null)

@Immutable
data class AlbumBrief(
    val id: Long,
    val name: String,
    val artist: String,
    val coverUrl: String? = null
)

@Immutable
data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistBrief> = emptyList(),
    val albums: List<AlbumBrief> = emptyList()
) {
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty()
}

/** One karaoke word: when it lights up and for how long. */
@Immutable
data class LyricWord(val timeMs: Long, val durationMs: Long, val text: String)

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    /** Machine translation supplied by Netease (tlyric), when available. */
    val translation: String? = null,
    /** Word-by-word timings from the karaoke lyric (yrc). */
    val words: List<LyricWord> = emptyList()
)

@Immutable
data class ArtistInfo(
    val id: Long,
    val avatarUrl: String?,
    val brief: String,
    val desc: String,
)

@Immutable
data class CommentItem(
    val id: Long,
    val user: String,
    val avatarUrl: String? = null,
    val content: String,
    val liked: Int = 0,
    val time: String = "",
    /** Epoch millis from Netease; 0 for local fixtures. Used by “按时间” sorting. */
    val timeMs: Long = 0L
)

/** Order of the all-comments sheet. */
enum class CommentSort(val label: String) { Likes("最热"), Time("最新") }

/** Netease playable qualities, ordered from lowest to highest. */
enum class AudioQuality(
    val level: String,
    val label: String,
    val shortLabel: String,
    val rank: Int
) {
    Standard("standard", "标准", "128K", 1),
    Higher("higher", "较高", "192K", 2),
    Exhigh("exhigh", "极高", "320K", 3),
    Lossless("lossless", "无损", "SQ", 4),
    Hires("hires", "Hi-Res", "HR", 5),
    Jyeffect("jyeffect", "高清环绕声", "JE", 6),
    Sky("sky", "沉浸环绕声", "Sky", 7),
    Jymaster("jymaster", "超清母带", "JM", 8);

    companion object {
        fun from(level: String?): AudioQuality =
            entries.firstOrNull { it.level == level } ?: Exhigh
    }
}

/** One quality actually offered for a song, with account permission. */
@Immutable
data class SongStream(
    val url: String,
    /** Level actually returned by Netease (may be lower than requested). */
    val level: String = "",
    val bitrate: Int = 0
)

@Immutable
data class SongQuality(
    val quality: AudioQuality,
    val bitrate: Int = 0,
    val sizeBytes: Long = 0L,
    val playable: Boolean = true
) {
    val level: String get() = quality.level
    val label: String get() = quality.label
    val shortLabel: String get() = quality.shortLabel
    val rank: Int get() = quality.rank
}


/** Netease-side social stats of a song: 红心 (like) count and comment count. */
@Immutable
data class SongSocial(
    val likeCount: Int = -1,
    val commentCount: Int = -1
) {
    val hasLike: Boolean get() = likeCount >= 0
    val hasComments: Boolean get() = commentCount >= 0
}

@Immutable
data class UserProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String? = null,
    val vip: Boolean = false
)

@Immutable
data class SongStat(val name: String, val artist: String, val plays: Int)

@Immutable
data class ArtistStat(val name: String, val plays: Int)

@Immutable
data class PlayStats(
    val totalPlays: Int,
    val todayMinutes: Int,
    val weekMinutes: Int,
    val streakDays: Int,
    val topSongs: List<SongStat>,
    val topArtists: List<ArtistStat>,
    val hourly: List<Int>,
    /** true = numbers come from this app's own play log, false = merged with the Netease account. */
    val local: Boolean = false,
    /** Optional caption explaining where the numbers come from. */
    val note: String = ""
)

/** What is currently stored on disk. Sizes are bytes. */
@Immutable
data class CacheInfo(
    val dataBytes: Long = 0L,
    val imageBytes: Long = 0L,
    val songCount: Int = 0,
    val recentCount: Int = 0,
    val audioBytes: Long = 0L
) {
    val totalBytes: Long get() = dataBytes + imageBytes + audioBytes
}

enum class CacheScope { All, Data, Images, Audio }

@Immutable
data class ScriptInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val catalogId: String = "",
    val sourceUrl: String = ""
)

@Immutable
data class SongStorageInfo(
    val cachedBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val downloaded: Boolean = false
)

@Immutable
data class QrStatus(val code: Int, val message: String, val cookie: String?) {
    val isSuccess: Boolean get() = code == 803 && !cookie.isNullOrBlank()
    val isExpired: Boolean get() = code == 800
}

fun formatCount(n: Int): String = when {
    n >= 100_000_000 -> "${n / 100_000_000}亿"
    n >= 10_000 -> "${n / 10_000}万"
    n >= 1000 -> "${n / 1000}k"
    else -> n.toString()
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> {
        val v = bytes * 100 / 1_073_741_824L
        "${v / 100}.${(v % 100).toString().padStart(2, '0')} GB"
    }
    bytes >= 1_048_576L -> {
        val v = bytes * 10 / 1_048_576L
        "${v / 10}.${v % 10} MB"
    }
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
