package com.kyant.backdrop.catalog.appstore.music

/** Local fallback data so every screen renders before login / without network. */
object Fixtures {

    // Cards on the Today page that are not real Netease playlists. Their songs are
    // carried by the card itself, so they must never be used as playlist endpoints.
    const val DAILY_CARD_ID = 401L
    const val FM_CARD_ID = 402L
    const val NEW_CARD_ID = 406L

    val virtualCardIds: Set<Long> = setOf(DAILY_CARD_ID, FM_CARD_ID, NEW_CARD_ID)

    fun isVirtualCard(id: Long): Boolean = id in virtualCardIds

    val songs: List<Song> = listOf(
        Song(1, "晴天", "周杰伦", "叶惠美", 101, 269_000, null, 1, "华语流行", 1_920_000, 862_000, 320, 10_700_000),
        Song(2, "起风了", "买辣椒也用券", "起风了", 102, 325_000, null, 1, "华语流行", 860_000, 430_000, 320, 12_900_000),
        Song(3, "夜空中最亮的星", "逃跑计划", "世界", 103, 252_000, null, 2, "摇滚", 640_000, 320_000, 320, 10_100_000),
        Song(4, "稻香", "周杰伦", "魔杰座", 101, 223_000, null, 3, "华语流行", 710_000, 390_000, 320, 8_900_000),
        Song(5, "光年之外", "邓紫棋", "光年之外", 104, 235_000, null, 1, "流行", 510_000, 260_000, 320, 9_400_000),
        Song(6, "平凡之路", "朴树", "猎户星座", 105, 302_000, null, 1, "民谣", 430_000, 210_000, 320, 12_100_000),
        Song(7, "富士山下", "陈奕迅", "What's Going On...?", 106, 259_000, null, 2, "粤语流行", 380_000, 190_000, 320, 10_400_000),
        Song(8, "孤勇者", "陈奕迅", "孤勇者", 107, 256_000, null, 1, "流行", 1_240_000, 610_000, 320, 10_200_000),
        Song(9, "夏日漱石", "橘子海", "浪潮上岸", 108, 244_000, null, 1, "独立摇滚", 220_000, 120_000, 320, 9_700_000),
        Song(10, "后来的我们", "五月天", "后来的我们", 109, 298_000, null, 1, "华语流行", 330_000, 170_000, 320, 11_900_000),
        Song(11, "大鱼", "周深", "大鱼", 110, 305_000, null, 1, "国风", 470_000, 240_000, 320, 12_200_000),
        Song(12, "易燃易爆炸", "陈粒", "如也", 111, 216_000, null, 2, "民谣", 290_000, 150_000, 320, 8_600_000)
    )

    val defaultTodayKinds = listOf(
        PlaylistKind.Daily,
        PlaylistKind.PrivateFm,
        PlaylistKind.Exclusive,
        PlaylistKind.Liked
    )

    val allTodayKinds = listOf(
        PlaylistKind.Daily,
        PlaylistKind.PrivateFm,
        PlaylistKind.Exclusive,
        PlaylistKind.Liked,
        PlaylistKind.Custom
    )

    fun labelOf(kind: PlaylistKind): String = when (kind) {
        PlaylistKind.Daily -> "每日推荐"
        PlaylistKind.PrivateFm -> "私人 FM"
        PlaylistKind.Exclusive -> "专属歌单"
        PlaylistKind.Liked -> "我喜欢的音乐"
        PlaylistKind.Custom -> "随机个人歌单"
        PlaylistKind.NewRelease -> "新歌速递"
    }

    val dailySongs: List<Song> = songs.take(8)

    val newReleases: List<Playlist> = listOf(
        Playlist(201, "午夜漫游", "橘子海 · 全新单曲", null, 1, PlaylistKind.NewRelease, listOf(songs[8])),
        Playlist(202, "回声集", "陈粒 · EP", null, 3, PlaylistKind.NewRelease, songs.takeLast(3)),
        Playlist(203, "城市之光", "群星 · 新专辑", null, 6, PlaylistKind.NewRelease, songs.take(6)),
        Playlist(204, "潮汐", "独立音乐人合辑", null, 4, PlaylistKind.NewRelease, songs.drop(4).take(4))
    )

    val personalPlaylists: List<Playlist> = listOf(
        Playlist(301, "我喜欢的音乐", "12 首 · 自动同步", null, 12, PlaylistKind.Liked, songs),
        Playlist(302, "深夜电台", "自建歌单 · 8 首", null, 8, PlaylistKind.Custom, songs.take(8)),
        Playlist(303, "通勤路上", "自建歌单 · 6 首", null, 6, PlaylistKind.Custom, songs.drop(2).take(6)),
        Playlist(304, "华语流行精选", "收藏歌单", null, 10, PlaylistKind.Custom, songs.take(10))
    )

    fun todayCards(kinds: List<PlaylistKind>): List<Playlist> = kinds.mapIndexed { i, kind ->
        when (kind) {
            PlaylistKind.Daily -> Playlist(DAILY_CARD_ID, "每日推荐", "根据你的口味生成 · ${dailySongs.size} 首", dailySongs.firstOrNull()?.coverUrl, dailySongs.size, kind, dailySongs)
            PlaylistKind.PrivateFm -> Playlist(FM_CARD_ID, "私人 FM", "无限电台 · 随机播放", songs[2].coverUrl, 1, kind, listOf(songs[2]))
            PlaylistKind.Exclusive -> Playlist(403, "专属歌单", "为你定制的宝藏歌曲", null, 6, kind, songs.drop(3).take(6))
            PlaylistKind.Liked -> Playlist(404, "我喜欢的音乐", "12 首 · 红心歌曲", null, 12, kind, songs)
            PlaylistKind.Custom -> Playlist(405, "随机个人歌单", "随机自「深夜电台」 · 8 首", null, 8, kind, songs.take(8))
            PlaylistKind.NewRelease -> Playlist(NEW_CARD_ID, "新歌速递", "本周新上架 · ${newReleases.first().songs.size} 首", newReleases.first().songs.firstOrNull()?.coverUrl, newReleases.first().songs.size, kind, newReleases.first().songs)
        }
    }

    fun playlistSongs(id: Long): List<Song> =
        (todayCards(allTodayKinds) + personalPlaylists + newReleases)
            .firstOrNull { it.id == id }?.songs?.ifEmpty { songs.take(6) } ?: songs.take(6)

    fun song(id: Long): Song? = songs.firstOrNull { it.id == id } ?: songs.firstOrNull()

    fun search(q: String): SearchResults {
        val key = q.trim()
        if (key.isEmpty()) return SearchResults()
        val matched = songs.filter {
            it.name.contains(key, true) || it.artist.contains(key, true) ||
                it.album.contains(key, true) || it.genre.contains(key, true)
        }
        val artists = songs.filter { it.artist.contains(key, true) }
            .map { ArtistBrief(it.artist.hashCode().toLong(), it.artist) }.distinctBy { it.name }
        val albums = songs.filter { it.album.contains(key, true) }
            .map { AlbumBrief(it.albumId, it.album, it.artist) }.distinctBy { it.id }
        return SearchResults(matched, artists, albums)
    }

    private val originalLyric = listOf(
        "把夜色折成一封信",
        "寄给还没醒来的城",
        "风从窗缝里经过",
        "把旧唱片轻轻翻面",
        "你说时间走得很慢",
        "慢到足够听完一整首歌",
        "路灯把影子拉长",
        "又在下个路口收回去",
        "那些没说出口的话",
        "都藏进副歌里",
        "如果旋律也有温度",
        "就让它替我拥抱你",
        "天亮之前再放一遍",
        "把这一刻留在这里",
        "等下一次前奏响起",
        "我们还会在原地相遇"
    )

    /** Original placeholder lyrics; real data comes from /lyric/new. */
    fun lyric(id: Long): List<LyricLine> {
        val shift = ((id % originalLyric.size).toInt()).coerceAtLeast(0)
        val rotated = originalLyric.drop(shift) + originalLyric.take(shift)
        return listOf(LyricLine(0, "♪")) +
            rotated.mapIndexed { i, text -> LyricLine(3_000L + i * 4_200L, text) }
    }

    val comments: List<CommentItem> = listOf(
        CommentItem(1, "听风的人", null, "每次听到前奏都会停下来，好像时间被按了暂停。", 12_834, "2 小时前"),
        CommentItem(2, "橙色黄昏", null, "这首歌陪我走过了整个夏天，感谢还在。", 8_921, "昨天"),
        CommentItem(3, "海边的卡夫卡", null, "编曲细节太棒了，戴耳机听完全是另一个世界。", 5_402, "3 天前"),
        CommentItem(4, "一颗橘子", null, "循环第 27 遍，还是会在副歌部分起鸡皮疙瘩。", 3_190, "上周")
    )

    fun comments(id: Long): List<CommentItem> = comments

    val stats = PlayStats(
        totalPlays = 12_480,
        todayMinutes = 86,
        weekMinutes = 512,
        streakDays = 23,
        topSongs = listOf(
            SongStat("晴天", "周杰伦", 328),
            SongStat("起风了", "买辣椒也用券", 274),
            SongStat("夏日漱石", "橘子海", 211),
            SongStat("大鱼", "周深", 186)
        ),
        topArtists = listOf(
            ArtistStat("周杰伦", 842),
            ArtistStat("陈奕迅", 516),
            ArtistStat("橘子海", 388),
            ArtistStat("陈粒", 274)
        ),
        hourly = listOf(2, 1, 0, 0, 0, 1, 4, 12, 26, 38, 31, 24, 42, 36, 28, 30, 41, 56, 72, 88, 76, 54, 28, 9)
    )

    val profile = UserProfile(10001, "网易云用户", null, true)

    const val SAMPLE_SCRIPT: String = """
function sourceInfo() {
  return { name: "Sample / 公网测试音源", version: "1.0.0", description: "Public test MP3 for network playback verification." };
}
function resolve(songJson) {
  var song = JSON.parse(songJson);
  if (!song || !song.id) return "";
  return "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
}
"""
}
