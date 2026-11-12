package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.ChevronRight
import com.kyant.backdrop.catalog.appstore.CircleButton
import com.kyant.backdrop.catalog.appstore.CircleKind
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap

/** Netease artist page reached from the artist name on a song. */
@Composable
fun ArtistPage(
    artistId: Long,
    artistName: String,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onOpenSong: (List<Song>, Song) -> Unit,
    onOpenIntro: (String, String) -> Unit = { _, _ -> }
) {
    val state = MusicServices.state
    val bridge = MusicServices.bridge
    var songs by remember(artistId) { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember(artistId) { mutableStateOf(true) }
    var info by remember(artistId) { mutableStateOf<ArtistInfo?>(null) }

    LaunchedEffect(artistId) {
        loading = true
        songs = runCatching { bridge.artistSongs(artistId) }.getOrDefault(emptyList())
        loading = false
        info = runCatching { bridge.artistInfo(artistId) }.getOrNull()
    }
    val name = artistName.ifBlank { songs.firstOrNull()?.artist ?: "艺术家" }

    Column(
        Modifier
            .fillMaxSize()
            .background(AS.Bg)
            .verticalScroll(scrollState)
            .padding(bottom = 250.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            CircleButton(CircleKind.Back, onBack)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(168.dp)
                    .clip(CircleShape)
                    .background(AS.Card)
            ) {
                CoverImage(
                    info?.avatarUrl ?: songs.firstOrNull()?.coverUrl,
                    artistId,
                    Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(18.dp))
            Txt("ARTIST", AS.eyebrow())
            Spacer(Modifier.height(6.dp))
            Txt(name, AS.largeTitle().copy(fontSize = 30.sp), maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Txt(
                if (loading) "正在加载…" else "热门歌曲 · ${songs.size} 首",
                AS.subtitle(),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AS.Blue)
                .tap { songs.firstOrNull()?.let { bridge.playFrom(songs, it) } }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(
                false,
                { songs.firstOrNull()?.let { bridge.playFrom(songs, it) } },
                size = 22.dp,
                filled = false
            )
            Spacer(Modifier.width(10.dp))
            Txt("播放全部", AS.body().copy(fontSize = 15.sp, color = Color.White))
        }
        Spacer(Modifier.height(20.dp))
        val bio = info?.desc?.takeIf { it.isNotBlank() }
            ?: info?.brief?.takeIf { it.isNotBlank() }
        if (bio != null) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AS.Card)
                    .tap { onOpenIntro(name, bio) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Txt("艺术家简介", AS.body().copy(fontSize = 15.sp))
                    Spacer(Modifier.height(4.dp))
                    Txt(bio.replace("\n", " "), AS.caption(), maxLines = 2)
                }
                Spacer(Modifier.width(8.dp))
                ChevronRight(size = 12.dp)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (songs.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Txt(if (loading) "正在加载…" else "暂时没有这位歌手的歌曲", AS.caption())
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp)) {
                songs.forEach { song ->
                    SongRow(
                        song,
                        playing = state.currentSong?.id == song.id && state.playing,
                        onOpen = { onOpenSong(songs, song) },
                        onPlay = { bridge.toggleSong(songs, song) }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Txt(
                artistSummary(songs),
                AS.caption().copy(color = AS.Text3),
                Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun artistSummary(songs: List<Song>): String {
    if (songs.isEmpty()) return "0 首"
    val minutes = songs.sumOf { it.durationMs } / 60_000L
    val hours = minutes / 60
    val rest = minutes % 60
    val duration = if (hours > 0) "$hours 小时 $rest 分钟" else "$rest 分钟"
    return "${songs.size} 首 · $duration"
}
