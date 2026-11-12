package com.kyant.backdrop.catalog.appstore

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val BottomSpacer = 120.dp

@Composable
fun TodayPage(
    scrollState: ScrollState,
    onOpenApp: (String) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpacer)
    ) {
        LargeHeader("Today", onAvatar)
        Column(
            Modifier.padding(horizontal = AS.PagePad),
            verticalArrangement = Arrangement.spacedBy(AS.SectionGap)
        ) {
            Catalog.todayStories.forEach { story ->
                StoryCard(story, onClick = { onOpenApp(story.appId) })
            }
        }
    }
}

@Composable
fun GamesPage(
    scrollState: ScrollState,
    getState: (String) -> GetState,
    onGet: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chip by rememberSaveable { mutableIntStateOf(-1) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpacer)
    ) {
        LargeHeader("Games", onAvatar)
        ChipRow(
            Catalog.gamesChips,
            if (chip < 0) null else chip,
            { chip = it ?: -1 },
            Modifier.padding(horizontal = AS.PagePad)
        )
        Spacer(Modifier.height(AS.SectionGap))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slideWidth = maxWidth - 40.dp
            Row(
                Modifier
                    .padding(start = AS.PagePad)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Catalog.gamesReleases.forEach { story ->
                    Column(Modifier.width(slideWidth)) {
                        FeatureSlide(
                            story,
                            getState(story.appId),
                            { onOpenApp(story.appId) },
                            { onGet(story.appId) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AS.SectionGap))
        SectionHeader(
            "What We're Playing",
            "These favorites are always a great choice",
            Modifier.padding(horizontal = AS.PagePad)
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = AS.PagePad)) {
            Catalog.appsForTag(if (chip < 0) null else Catalog.gamesChipsTags[chip], Catalog.gamesPlaying)
                .forEach { item ->
                    AppRow(item, getState(item.id), { onOpenApp(item.id) }, { onGet(item.id) })
                }
        }
    }
}

@Composable
fun AppsPage(
    scrollState: ScrollState,
    getState: (String) -> GetState,
    onGet: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chip by rememberSaveable { mutableIntStateOf(-1) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpacer)
    ) {
        LargeHeader("Apps", onAvatar)
        ChipRow(
            Catalog.appsChips,
            if (chip < 0) null else chip,
            { chip = it ?: -1 },
            Modifier.padding(horizontal = AS.PagePad)
        )
        Spacer(Modifier.height(AS.SectionGap))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slideWidth = maxWidth - 40.dp
            Row(
                Modifier
                    .padding(start = AS.PagePad)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Catalog.appsUpdates.forEach { story ->
                    Column(Modifier.width(slideWidth)) {
                        FeatureSlide(
                            story,
                            getState(story.appId),
                            { onOpenApp(story.appId) },
                            { onGet(story.appId) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AS.SectionGap))
        SectionHeader(
            "Using Now", "Apps people love right now",
            Modifier.padding(horizontal = AS.PagePad)
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = AS.PagePad)) {
            Catalog.appsForTag(if (chip < 0) null else Catalog.appsChipsTags[chip], Catalog.appsUsingNow)
                .forEach { item ->
                    AppRow(item, getState(item.id), { onOpenApp(item.id) }, { onGet(item.id) })
                }
        }
    }
}

@Composable
fun ArcadePage(
    scrollState: ScrollState,
    getState: (String) -> GetState,
    onGet: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chip by rememberSaveable { mutableIntStateOf(-1) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpacer)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slideWidth = maxWidth
            val pageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val hScroll = rememberScrollState()
            val page by remember { derivedStateOf { (hScroll.value / pageWidthPx).roundToInt().coerceIn(0, 2) } }

            Box(Modifier.fillMaxWidth().height(560.dp)) {
                Row(Modifier.horizontalScroll(hScroll)) {
                    Catalog.arcadeSlides.forEach { story ->
                        Box(Modifier.width(slideWidth).height(560.dp)) {
                            HeroArt(story.art, Modifier.fillMaxSize())
                            Column(
                                Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth()
                                    .padding(horizontal = AS.PagePad),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Txt(story.eyebrow, AS.eyebrow().copy(color = Color.White.copy(.85f), fontSize = 12.sp))
                                Spacer(Modifier.height(10.dp))
                                Txt(
                                    story.title,
                                    AS.cardTitle().copy(fontSize = 28.sp, lineHeight = 34.sp, color = Color.White),
                                    maxLines = 2
                                )
                            }
                            val item = Catalog.byId(story.appId)
                            Row(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = AS.PagePad)
                                    .padding(bottom = 40.dp)
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0x802C2C2E))
                                    .padding(horizontal = 14.dp)
                                    .tap { onOpenApp(story.appId) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item != null) AppIcon(item, size = 44.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Txt(item?.name ?: "", AS.rowTitle(), maxLines = 1)
                                    Txt(item?.subtitle ?: "", AS.subtitle().copy(fontSize = 13.sp), maxLines = 1)
                                }
                                GetButton(getState(story.appId), onClick = { onGet(story.appId) })
                            }
                        }
                    }
                }

                // Fixed overlay title + avatar (does not scroll with the slides).
                LargeHeader("Arcade", onAvatar, Modifier.align(Alignment.TopCenter))
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (i == page) Color.White else Color.White.copy(.35f))
                        )
                    }
                }
            }
        }

        ChipRow(Catalog.arcadeChips, if (chip < 0) null else chip, { chip = it ?: -1 }, Modifier.padding(horizontal = AS.PagePad))
        Spacer(Modifier.height(AS.SectionGap))
        SectionHeader("Top Arcade Games", null, Modifier.padding(horizontal = AS.PagePad))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = AS.PagePad)) {
            Catalog.arcadeTop.forEachIndexed { i, id ->
                val item = Catalog.byId(id) ?: return@forEachIndexed
                RankedAppRow(item, i + 1, getState(id), { onOpenApp(id) }, { onGet(id) })
            }
        }
    }
}

@Composable
fun SearchPage(
    scrollState: ScrollState,
    getState: (String) -> GetState,
    onGet: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = Catalog.filter(query)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomSpacer)
    ) {
        LargeHeader("Search", onAvatar)
        SearchField(query, { query = it }, Modifier.padding(horizontal = AS.PagePad))
        Spacer(Modifier.height(AS.SectionGap))

        if (query.isBlank()) {
            SectionHeader("Suggested", null, Modifier.padding(horizontal = AS.PagePad))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = AS.PagePad)) {
                Catalog.searchSuggested.mapNotNull { id -> Catalog.byId(id) }.forEach { item ->
                    AppRow(item, getState(item.id), { onOpenApp(item.id) }, { onGet(item.id) })
                }
            }
            Spacer(Modifier.height(AS.SectionGap))
            SectionHeader("Browse", null, Modifier.padding(horizontal = AS.PagePad))
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .padding(horizontal = AS.PagePad)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrowseCard(
                    Catalog.browseCategories[0], ASGradients.BrowseOrange,
                    listOf(IconKey.CrossyRoad, IconKey.SlapKings)
                ) { query = "Games" }
                BrowseCard(
                    Catalog.browseCategories[1], ASGradients.BrowseBlue,
                    listOf(IconKey.Instagram, IconKey.TikTok)
                ) { query = "Apps" }
            }
        } else if (results.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Txt("No Results", AS.sectionTitle())
                Spacer(Modifier.height(8.dp))
                Txt("No results found for “$query”", AS.subtitle())
            }
        } else {
            Column(Modifier.padding(horizontal = AS.PagePad)) {
                results.forEach { item ->
                    AppRow(item, getState(item.id), { onOpenApp(item.id) }, { onGet(item.id) })
                }
            }
        }
    }
}

@Composable
private fun BrowseCard(
    label: String,
    colors: List<Color>,
    icons: List<IconKey>,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .width(162.dp)
            .height(158.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors))
            .tap(onClick)
            .padding(14.dp)
    ) {
        Txt(
            label,
            AS.cardTitle().copy(fontSize = 18.sp, color = Color.White),
            Modifier.align(Alignment.BottomStart),
            maxLines = 3
        )
        Row(Modifier.align(Alignment.TopEnd)) {
            icons.forEach { key ->
                Box(Modifier.padding(start = 2.dp)) { AppIcon(key, size = 34.dp) }
            }
        }
    }
}
