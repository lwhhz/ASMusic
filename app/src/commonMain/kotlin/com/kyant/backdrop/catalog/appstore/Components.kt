package com.kyant.backdrop.catalog.appstore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class InstallPhase { Idle, Installing, Installed }

data class GetState(val phase: InstallPhase = InstallPhase.Idle, val progress: Float = 0f)

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(text = text, modifier = modifier, style = style, maxLines = maxLines)
}

fun Modifier.tap(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

// ---------------------------------------------------------------------------
// Headers
// ---------------------------------------------------------------------------

@Composable
fun LargeHeader(title: String, onAvatar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = AS.PagePad, end = AS.PagePad, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(title, AS.largeTitle())
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .tap(onAvatar)
        ) { AvatarArt(Modifier.fillMaxSize()) }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(modifier.then(if (onClick != null) Modifier.tap(onClick) else Modifier)) {
        Txt(title, AS.sectionTitle())
        if (subtitle != null) Txt(subtitle, AS.subtitle())
    }
}

@Composable
fun ChevronRight(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = .55f),
    size: Dp = 12.dp
) {
    androidx.compose.foundation.Image(
        imageVector = com.kyant.backdrop.catalog.appstore.music.Iconoir.arrowRight,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color)
    )
}

@Composable
fun ChipRow(
    chips: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        chips.forEachIndexed { i, label ->
            val active = selected == i
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) AS.Blue.copy(alpha = .28f) else AS.Card)
                    .tap { onSelect(if (active) null else i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Txt(label, AS.body().copy(fontSize = 15.sp), maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Get button (idle -> progress ring -> Open)
// ---------------------------------------------------------------------------

@Composable
fun GetButton(
    state: GetState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    width: Dp = 76.dp,
    height: Dp = 32.dp
) {
    val clickablePhase = state.phase != InstallPhase.Installing
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) AS.Blue else AS.CardElevated)
            .then(if (clickablePhase) Modifier.tap(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (state.phase) {
            InstallPhase.Installing -> Canvas(Modifier.size(20.dp)) {
                val sw = 3.dp.toPx()
                drawArc(Color.White.copy(.28f), 0f, 360f, false, style = Stroke(sw))
                drawArc(if (filled) Color.White else AS.Blue, -90f, 360f * state.progress, false, style = Stroke(sw))
            }
            InstallPhase.Installed -> Txt("Open", if (filled) AS.button().copy(color = Color.White) else AS.button())
            InstallPhase.Idle -> Txt("Get", if (filled) AS.button().copy(color = Color.White) else AS.button())
        }
    }
}

// ---------------------------------------------------------------------------
// App list rows
// ---------------------------------------------------------------------------

@Composable
fun AppRow(
    item: AppItem,
    state: GetState,
    onOpen: () -> Unit,
    onGet: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .tap(onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(item, size = 60.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(item.name, AS.rowTitle(), maxLines = 2)
                Txt(item.subtitle, AS.subtitle(), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                GetButton(state, onGet)
                Spacer(Modifier.height(4.dp))
                if (item.iap) Txt("In-App Purchases", AS.caption().copy(fontSize = 10.sp))
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = 72.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AS.Separator)
            )
        }
    }
}

@Composable
fun RankedAppRow(
    item: AppItem,
    rank: Int,
    state: GetState,
    onOpen: () -> Unit,
    onGet: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .tap(onOpen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(item, size = 62.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt("Apple Arcade", AS.caption().copy(fontSize = 11.sp))
            Txt("$rank", AS.rowTitle().copy(fontSize = 22.sp))
            Txt(item.name, AS.rowTitle().copy(fontSize = 20.sp), maxLines = 1)
            Txt(item.subtitle, AS.subtitle(), maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            GetButton(state, onGet)
            Spacer(Modifier.height(4.dp))
            Txt("In-App Purchases", AS.caption().copy(fontSize = 10.sp))
        }
    }
}

// ---------------------------------------------------------------------------
// Detail page blocks
// ---------------------------------------------------------------------------

@Composable
private fun StatCell(label: String, value: String, footer: String, width: Dp = 96.dp) {
    Column(
        Modifier
            .width(width)
            .height(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Txt(label.uppercase(), AS.caption().copy(fontSize = 10.sp), maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Txt(value, AS.body().copy(fontSize = 22.sp, color = AS.Text2), maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Txt(footer, AS.caption().copy(fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
fun StatsRow(item: AppItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(96.dp).height(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Txt("${item.ratingCount} RATINGS", AS.caption().copy(fontSize = 10.sp), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Txt("${item.rating}", AS.body().copy(fontSize = 24.sp), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Txt(starString(item.rating), AS.body().copy(fontSize = 12.sp, color = AS.Text2))
        }
        DividerV()
        StatCell("AGE RATING", item.age, "Years Old")
        DividerV()
        StatCell("CHART", "#${item.chart ?: 1}", if (item.category == "game") "Games" else "Apps")
        DividerV()
        Column(Modifier.width(96.dp).height(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Txt("DEVELOPER", AS.caption().copy(fontSize = 10.sp), maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.size(22.dp)) {
                drawRoundRect(AS.Text2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                drawCircle(AS.Card, 5.dp.toPx(), Offset(size.width / 2, 8.dp.toPx()))
            }
            Spacer(Modifier.height(8.dp))
            Txt(item.developer, AS.caption().copy(fontSize = 11.sp), maxLines = 1)
        }
        DividerV()
        StatCell("LANGUAGE", "EN", "English")
        DividerV()
        StatCell("SIZE", "148", "MB")
    }
}

private fun starString(rating: Double): String {
    val full = rating.toInt()
    return "★".repeat(full) + if (rating - full >= .5) "☆" else ""
}

@Composable
private fun DividerV() {
    Box(Modifier.width(1.dp).height(58.dp).background(AS.Separator))
}

@Composable
fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Txt(label, AS.body().copy(color = AS.Text2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(value, AS.body().copy(color = if (onClick != null) AS.Blue else AS.Text), maxLines = 1)
            if (onClick != null) Txt("  ›", AS.body().copy(color = AS.Blue))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(AS.Separator))
}

@Composable
fun ReviewCard(review: Review, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AS.Card)
            .padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(review.author, AS.rowTitle())
            Txt(review.date, AS.subtitle())
        }
        Txt("★".repeat(review.rating), AS.body().copy(fontSize = 15.sp, color = AS.Orange))
        Txt(review.handle, AS.caption().copy(color = AS.Text3))
        Spacer(Modifier.height(12.dp))
        Txt(review.body, AS.body().copy(fontSize = 15.sp, lineHeight = 21.sp))
    }
}

@Composable
fun ScreenshotCarousel(item: AppItem, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item.shots.forEach { shot ->
            Box(
                Modifier
                    .width(210.dp)
                    .height(380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AS.Card)
            ) {
                HeroArt(shot.art, Modifier.fillMaxSize())
                Txt(
                    shot.headline,
                    AS.cardTitle().copy(fontSize = 20.sp),
                    Modifier.padding(16.dp).align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
fun RatingDistribution() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 5 downTo 1) {
                Txt("★".repeat(i), AS.body().copy(fontSize = 9.sp, color = AS.Text2))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val weights = listOf(.78f, .52f, .30f, .14f, .08f)
            weights.forEach { w ->
                Box(Modifier.fillMaxWidth().height(3.dp).background(AS.Separator)) {
                    Box(Modifier.fillMaxWidth(w).height(3.dp).background(AS.Text2))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AS.Card)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            imageVector = com.kyant.backdrop.catalog.appstore.music.Iconoir.tabSearch,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(AS.Text2)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = AS.body().copy(fontSize = 16.sp),
            cursorBrush = SolidColor(AS.Blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Txt("Games, Apps, Stories, and More", AS.body().copy(color = AS.Text3, fontSize = 16.sp), maxLines = 1)
                    inner()
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Buttons / overlays
// ---------------------------------------------------------------------------

enum class CircleKind { Back, Share }

@Composable
fun CircleButton(kind: CircleKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C2C2E).copy(alpha = .72f))
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        val vector = when (kind) {
            CircleKind.Back -> com.kyant.backdrop.catalog.appstore.music.Iconoir.arrowLeft
            CircleKind.Share -> com.kyant.backdrop.catalog.appstore.music.Iconoir.share
        }
        if (vector != null) {
            androidx.compose.foundation.Image(
                imageVector = vector,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
            )
            return@Box
        }

    }
}

@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xE61C1C1E))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Txt(text, AS.body().copy(fontSize = 14.sp))
    }
}

// ---------------------------------------------------------------------------
// Cards shared by Today / Games / Apps / Arcade
// ---------------------------------------------------------------------------

@Composable
fun StoryCard(story: Story, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardHeight = (maxWidth.value * 0.94f).dp.coerceIn(300.dp, 360.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(AS.CardRadius))
                .then(
                    if (story.tone == Tone.Light) Modifier.background(Brush.verticalGradient(ASGradients.ArcadeLight))
                    else Modifier.background(AS.Card)
                )
                .tap(onClick)
        ) {
            if (story.tone == Tone.Light) {
                // Text and artwork are stacked, never overlapping.
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(AS.CardPad)) {
                        Txt(story.eyebrow, AS.eyebrow().copy(color = Color(0xFF8A8A8F)))
                        Spacer(Modifier.height(8.dp))
                        Txt(story.title, AS.cardTitle().copy(color = Color(0xFF111114), fontSize = 34.sp, lineHeight = 40.sp), maxLines = 3)
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        HeroArt(ArtKey.Joystick, Modifier.fillMaxSize())
                    }
                }
            } else {
                HeroArt(story.art, Modifier.fillMaxSize())
                if (story.subtitle != null) {
                    Txt(story.subtitle, AS.eyebrow().copy(color = Color.White.copy(.8f)), Modifier.padding(AS.CardPad).align(Alignment.TopStart))
                }
                if (story.title.isNotEmpty()) {
                    Txt(story.title, AS.cardTitle().copy(color = Color.White), Modifier.padding(AS.CardPad).align(Alignment.BottomStart), maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun FeatureSlide(
    story: Story,
    state: GetState,
    onOpen: () -> Unit,
    onGet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Txt(story.eyebrow, AS.eyebrow())
        Spacer(Modifier.height(6.dp))
        Txt(story.title, AS.cardTitle().copy(fontSize = 26.sp, lineHeight = 31.sp), maxLines = 2)
        Txt(story.subtitle ?: "", AS.subtitle().copy(fontSize = 18.sp), maxLines = 1)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(AS.CardRadius))
                .background(AS.Card)
                .tap(onOpen)
        ) {
            HeroArt(story.art, Modifier.fillMaxSize())
            val item = Catalog.byId(story.appId)
            if (story.art == ArtKey.ColorMist && item != null) {
                Box(Modifier.align(Alignment.Center).padding(bottom = 48.dp)) {
                    AppIcon(item, size = 92.dp)
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(76.dp)
                    .background(Color(0x99101012))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item != null) AppIcon(item, size = 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt(item?.name ?: story.title, AS.rowTitle().copy(fontSize = 16.sp), maxLines = 1)
                    Txt(item?.subtitle ?: story.subtitle ?: "", AS.subtitle().copy(fontSize = 13.sp), maxLines = 1)
                }
                GetButton(state, onGet)
            }
        }
    }
}
