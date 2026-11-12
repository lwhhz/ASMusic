package com.kyant.backdrop.catalog.appstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.utils.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TabLabels = listOf("Today", "Games", "Apps", "Arcade", "Search")

/**
 * Root of the App Store replica.
 *
 * The whole page tree is recorded into a [rememberLayerBackdrop] layer and the
 * floating [LiquidBottomTabs] samples it, so the tab bar gets the real
 * vibrancy / blur / lens / chromatic-aberration / damped-drag behaviour from
 * the upstream template rather than a CSS-like approximation.
 */
@Composable
fun AppStoreApp() {
    val backdrop = rememberLayerBackdrop()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }

    var installed by remember { mutableStateOf(Platform.bridge.loadInstalled()) }
    var installing by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    // Scroll states live here so each tab keeps its position across switches.
    val todayScroll = rememberScrollState()
    val gamesScroll = rememberScrollState()
    val appsScroll = rememberScrollState()
    val arcadeScroll = rememberScrollState()
    val searchScroll = rememberScrollState()
    val detailScroll = rememberScrollState()

    fun getState(id: String): GetState = when {
        id in installed -> GetState(InstallPhase.Installed)
        installing.containsKey(id) -> GetState(InstallPhase.Installing, installing[id] ?: 0f)
        else -> GetState(InstallPhase.Idle)
    }

    fun startInstall(id: String) {
        if (id in installed || installing.containsKey(id)) return
        scope.launch {
            repeat(20) { step ->
                delay(65)
                installing = installing + (id to (step + 1) / 20f)
            }
            installing = installing - id
            installed = installed + id
            Platform.bridge.saveInstalled(installed)
        }
    }

    fun selectTab(idx: Int) {
        if (idx == tab) {
            scope.launch {
                when (idx) {
                    0 -> todayScroll.animateScrollTo(0)
                    1 -> gamesScroll.animateScrollTo(0)
                    2 -> appsScroll.animateScrollTo(0)
                    3 -> arcadeScroll.animateScrollTo(0)
                    4 -> searchScroll.animateScrollTo(0)
                }
            }
        } else {
            tab = idx
        }
    }

    fun openApp(id: String) {
        detailId = id
        scope.launch { detailScroll.scrollTo(0) }
    }

    LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(1800)
            toastText = null
        }
    }

    Box(Modifier.fillMaxSize().background(AS.Bg)) {
        // Recorded content layer: sampled by the floating glass tab bar.
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .layerBackdrop(backdrop)
        ) {
            when (tab) {
                0 -> TodayPage(todayScroll, ::openApp, onAvatar = { sheetOpen = true })
                1 -> GamesPage(gamesScroll, ::getState, ::startInstall, ::openApp, onAvatar = { sheetOpen = true })
                2 -> AppsPage(appsScroll, ::getState, ::startInstall, ::openApp, onAvatar = { sheetOpen = true })
                3 -> ArcadePage(arcadeScroll, ::getState, ::startInstall, ::openApp, onAvatar = { sheetOpen = true })
                4 -> SearchPage(searchScroll, ::getState, ::startInstall, ::openApp, onAvatar = { sheetOpen = true })
            }

            val currentId = detailId
            if (currentId != null) {
                AppDetailPage(
                    currentId,
                    detailScroll,
                    getState(currentId),
                    { startInstall(currentId) },
                    { detailId = null },
                    {
                        val item = Catalog.byId(currentId)
                        val name = item?.name ?: "this app"
                        Platform.bridge.share("Check out $name on the App Store")
                        toastText = "Sharing $name"
                    }
                )
            }
        }

        // Floating liquid-glass tab bar (upstream LiquidBottomTabs, 5 tabs).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { selectTab(it) },
                backdrop = backdrop,
                tabsCount = 5,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(5) { i ->
                    LiquidBottomTab({ selectTab(i) }) {
                        TabIcon(
                            i,
                            if (tab == i) AS.Blue else Color.White.copy(alpha = .78f),
                            size = 24.dp
                        )
                        BasicText(TabLabels[i], style = AS.tabLabel(tab == i))
                    }
                }
            }
        }

        if (toastText != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)) {
                Toast(toastText!!)
            }
        }

        SettingsSheet(sheetOpen, onDismiss = { sheetOpen = false })
    }

    // Back: close sheet -> pop detail -> let the system finish the activity.
    BackHandler(sheetOpen || detailId != null) {
        when {
            sheetOpen -> sheetOpen = false
            detailId != null -> detailId = null
        }
    }
}
