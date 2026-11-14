package com.kyant.backdrop.catalog.appstore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppDetailPage(
    appId: String,
    scrollState: ScrollState,
    getState: GetState,
    onGet: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = Catalog.byId(appId)
    if (item == null) {
        Box(modifier.fillMaxSize().background(AS.Bg)) {
            CircleButton(CircleKind.Back, onBack, Modifier.padding(16.dp))
            Txt("App not found", AS.sectionTitle(), Modifier.align(Alignment.Center))
        }
        return
    }

    val showSticky by remember { derivedStateOf { scrollState.value > 260 } }
    val stickyAlpha by animateFloatAsState(if (showSticky) 1f else 0f)

    Box(modifier.fillMaxSize().background(AS.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 120.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = 1f - stickyAlpha },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CircleButton(CircleKind.Back, onBack)
                CircleButton(CircleKind.Share, onShare)
            }
            Spacer(Modifier.height(18.dp))

            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(item, size = 100.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Txt(item.name, AS.cardTitle().copy(fontSize = 21.sp), maxLines = 2)
                    Txt(item.subtitle, AS.subtitle(), maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    GetButton(getState, onGet, filled = true, width = 92.dp, height = 34.dp)
                    if (item.iap) Txt("In-App Purchases", AS.caption().copy(fontSize = 10.sp))
                }
            }

            Spacer(Modifier.height(20.dp))
            StatsRow(item)
            Box(Modifier.fillMaxWidth().height(1.dp).background(AS.Separator))
            Spacer(Modifier.height(20.dp))

            ScreenshotCarousel(item, Modifier.padding(start = 16.dp))
            Spacer(Modifier.height(26.dp))

            Txt(item.description, AS.body().copy(fontSize = 15.sp, lineHeight = 21.sp), Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(28.dp))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            Spacer(Modifier.height(22.dp))

            // Ratings & Reviews
            SectionHeader("Ratings & Reviews", null, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("${item.rating}", AS.largeTitle().copy(fontSize = 52.sp))
                    Txt("out of 5", AS.caption())
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    RatingDistribution()
                    Spacer(Modifier.height(8.dp))
                    Txt("${item.ratingCount} Ratings", AS.caption(), Modifier.align(Alignment.End))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .padding(start = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item.reviews.forEach { ReviewCard(it) }
            }

            Spacer(Modifier.height(28.dp))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            Spacer(Modifier.height(22.dp))

            // What's New
            SectionHeader("What's New", null, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Txt("Version ${item.whatsNew.version}", AS.subtitle())
                Txt(item.whatsNew.date, AS.subtitle())
            }
            Spacer(Modifier.height(14.dp))
            Txt(item.whatsNew.notes, AS.body().copy(fontSize = 15.sp, lineHeight = 21.sp), Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(28.dp))
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            Spacer(Modifier.height(22.dp))

            // Information
            Txt("Information", AS.sectionTitle(), Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                InfoRow("Seller", item.developer)
                InfoRow("Size", "148.2 MB")
                InfoRow("Category", if (item.category == "game") "Games" else "Photo & Video")
                InfoRow("Compatibility", "Works on this device")
                InfoRow("Languages", "English")
                InfoRow("Age Rating", item.age)
                InfoRow("Copyright", "© ${item.developer}")
            }
        }

        // Sticky glass bar, cross-fades with the top round buttons.
        if (stickyAlpha > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = stickyAlpha }
                    .background(Color(0xE61C1C1E))
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleButton(CircleKind.Back, onBack, Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                AppIcon(item, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Txt(item.name, AS.rowTitle().copy(fontSize = 14.sp), Modifier.weight(1f), maxLines = 1)
                GetButton(getState, onGet, filled = true, width = 72.dp, height = 30.dp)
            }
        }
    }
}
