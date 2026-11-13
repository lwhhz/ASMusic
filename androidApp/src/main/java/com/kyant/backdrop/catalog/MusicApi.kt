package com.kyant.backdrop.catalog

import com.kyant.backdrop.catalog.appstore.music.ArtistInfo
import androidx.compose.ui.graphics.ImageBitmap
import com.kyant.backdrop.catalog.appstore.music.CommentItem
import com.kyant.backdrop.catalog.appstore.music.LyricLine
import com.kyant.backdrop.catalog.appstore.music.PlayStats
import com.kyant.backdrop.catalog.appstore.music.Playlist
import com.kyant.backdrop.catalog.appstore.music.QrStatus
import com.kyant.backdrop.catalog.appstore.music.SearchResults
import com.kyant.backdrop.catalog.appstore.music.Song
import com.kyant.backdrop.catalog.appstore.music.SongQuality
import com.kyant.backdrop.catalog.appstore.music.SongSocial
import com.kyant.backdrop.catalog.appstore.music.UserProfile

/**
 * Everything the UI needs from Netease Cloud Music.
 *
 * Two implementations exist:
 *  - [NeteaseDirect] talks to music.163.com itself (default, works on a phone alone).
 *  - [NeteaseApi]    talks to a self-hosted NeteaseCloudMusicApi proxy (optional).
 */
interface MusicApi {
    // ---- QR login ----
    suspend fun qrKey(): String?
    suspend fun qrImage(key: String): ImageBitmap?
    suspend fun qrCheck(key: String): QrStatus

    // ---- catalogue ----
    suspend fun search(keyword: String): SearchResults?
    suspend fun newReleases(): List<Playlist>?
    suspend fun newAlbums(): List<Playlist>?
    suspend fun toplists(): List<Playlist>?
    suspend fun dailySongs(): List<Song>?
    suspend fun personalPlaylists(): List<Playlist>?
    suspend fun playlistSongs(id: Long): List<Song>?
    suspend fun albumSongs(id: Long): List<Song>?
    suspend fun artistSongs(id: Long): List<Song>?

    /** Portrait + biography; null when the source cannot provide them. */
    suspend fun artistInfo(id: Long): ArtistInfo? = null
    suspend fun albumDescription(id: Long): String?
    suspend fun songDetail(id: Long): Song?
    suspend fun lyric(id: Long): List<LyricLine>?
    suspend fun comments(id: Long): List<CommentItem>?
    suspend fun allComments(id: Long): List<CommentItem>?
    suspend fun songSocial(id: Long): SongSocial?
    suspend fun songUrl(id: Long): String?
    suspend fun songUrlAt(id: Long, level: String): com.kyant.backdrop.catalog.appstore.music.SongStream?
    suspend fun songQualities(id: Long): List<SongQuality>?
    suspend fun profile(): UserProfile?
    suspend fun stats(): PlayStats?

    // ---- media ----
    suspend fun image(url: String): ImageBitmap?
}
