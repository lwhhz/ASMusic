@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShapeLine
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.AudioQuality
import com.kyant.backdrop.catalog.appstore.music.CacheScope
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.PlayMode
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

private enum class SettingsPage(val title: String) {
    Root("设置"),
    Appearance("外观"),
    Shape("形状与排版"),
    Motion("动效"),
    Layout("布局"),
    Playback("播放"),
    Source("音源"),
    Storage("存储"),
    About("关于与维护")
}

/**
 * MD3 设置（分类版）。
 *
 * 之前的版本把 20 多个条目平铺在一页里，没有层级；现在按
 * 「外观 / 播放 / 音源 / 存储 / 关于」分组，父页每行右侧给出当前值摘要，
 * 顶部还有一个能搜设置的搜索框。
 */
@Composable
fun Md3SettingsScreen(
    config: Md3Config,
    onBack: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onRestart: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }

    // 层级：形状/动效/布局属于「外观」，其余二级页属于根页。
    fun parentOf(p: SettingsPage): SettingsPage = when (p) {
        SettingsPage.Shape, SettingsPage.Motion, SettingsPage.Layout -> SettingsPage.Appearance
        SettingsPage.Root -> SettingsPage.Root
        else -> SettingsPage.Root
    }
    BackHandler(enabled = page != SettingsPage.Root) { page = parentOf(page) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally { it / 5 * dir } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 5 * dir } + fadeOut())
        },
        label = "settings-page"
    ) { current ->
        when (current) {
            SettingsPage.Root -> SettingsRoot(
                config = config,
                onBack = onBack,
                onOpen = { page = it },
                onRestart = onRestart
            )
            SettingsPage.Appearance -> AppearancePage(
                config = config,
                onBack = { page = SettingsPage.Root },
                onShape = { page = SettingsPage.Shape },
                onMotion = { page = SettingsPage.Motion },
                onLayout = { page = SettingsPage.Layout }
            )
            SettingsPage.Shape -> ShapePage(config) { page = SettingsPage.Appearance }
            SettingsPage.Motion -> MotionPage(config) { page = SettingsPage.Appearance }
            SettingsPage.Layout -> LayoutPage(config) { page = SettingsPage.Appearance }
            SettingsPage.Playback -> PlaybackPage(config) { page = SettingsPage.Root }
            SettingsPage.Source -> SourcePage { page = SettingsPage.Root }
            SettingsPage.Storage -> StoragePage { page = SettingsPage.Root }
            SettingsPage.About -> AboutPage(
                config = config,
                onBack = { page = SettingsPage.Root },
                onOpenSelfCheck = onOpenSelfCheck,
                onRestart = onRestart
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 根页
// ---------------------------------------------------------------------------

private data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val page: SettingsPage,
    val summary: () -> String
)

@Composable
private fun SettingsRoot(
    config: Md3Config,
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onRestart: () -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    var styleTarget by remember { mutableStateOf<UiStyle?>(null) }

    val entries = listOf(
        SettingsEntry(Icons.Rounded.Palette, "外观", "配色、形状、动效、布局", SettingsPage.Appearance) {
            "${config.schemeStyle.label} · ${config.seedSource.label} · ${config.darkMode.label}"
        },
        SettingsEntry(Icons.Rounded.PlayCircle, "播放", "音质、播放模式、播放页", SettingsPage.Playback) {
            "${qualityLabel(state.wifiQuality)} · ${playModeLabel(state.playMode)} · ${config.seekStyle.label}进度"
        },
        SettingsEntry(Icons.Rounded.Extension, "音源", "脚本、直连与代理", SettingsPage.Source) {
            if (state.scripts.isEmpty()) "未导入脚本"
            else "${state.scripts.count { it.enabled }}/${state.scripts.size} 个脚本启用"
        },
        SettingsEntry(Icons.Rounded.Storage, "存储", "缓存占用与清理", SettingsPage.Storage) {
            "缓存 ${md3Bytes(state.cacheInfo.totalBytes)}"
        },
        SettingsEntry(Icons.Rounded.HealthAndSafety, "关于与维护", "自检、重启、版本", SettingsPage.About) {
            "v${bridge.appVersion}"
        }
    )
    val filtered = if (query.isBlank()) entries else entries.filter {
        it.title.contains(query, true) || it.summary().contains(query, true) ||
            it.desc.contains(query, true)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Md3TopBar(title = "设置", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AccountCard(
                    loggedIn = state.loggedIn,
                    nickname = state.nickname,
                    avatar = state.avatarUrl,
                    onLogin = { showLogin = true },
                    onLogout = {
                        scope.launch {
                            bridge.logout()
                            bridge.showToast("已退出登录")
                        }
                    }
                )
            }

            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("界面风格", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "两套界面各自独立，切换后会重启应用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Md3ConnectedButtonGroup(
                            options = UiStyle.entries.map { it.label },
                            selected = UiStyle.entries.indexOf(Md3Config.readStyle(ctx)),
                            onSelect = { styleTarget = UiStyle.entries[it] },
                            fullWidth = true
                        )
                    }
                }
            }

            item { SettingsSearchField(query) { query = it } }

            items(filtered.size) { i ->
                val entry = filtered[i]
                SettingsNavRow(entry) { onOpen(entry.page) }
            }
            if (filtered.isEmpty()) {
                item {
                    Md3EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "没有匹配的设置",
                        desc = "试试「配色」「音质」「缓存」"
                    )
                }
            }

            item {
                Text(
                    "Material 3 界面的设置独立保存（md3_app.xml），切回经典界面后这些值仍然保留。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }

    styleTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { styleTarget = null },
            title = { Text("切换到「${target.label}」？") },
            text = {
                Text(
                    target.desc +
                        "\n\n应用会重启一次。两套界面的设置分别保存在各自的配置里，切回来都还在；" +
                        "但当前播放队列与播放进度会丢失（播放内核随进程重建），登录状态不受影响。" +
                        if (target == UiStyle.Ios) "\n\n经典界面按竖屏设计，切换后会锁回竖屏。" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Md3Config.writeStyle(ctx, target)
                    styleTarget = null
                    onRestart()
                }) { Text("切换并重启") }
            },
            dismissButton = { TextButton(onClick = { styleTarget = null }) { Text("取消") } }
        )
    }

    if (showLogin) Md3QrSheet(onClose = { showLogin = false })
}

@Composable
private fun SettingsSearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "搜索设置",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            Md3IconButton(
                Icons.Rounded.Delete,
                contentDescription = "清除",
                onClick = { onQuery("") },
                size = 32.dp,
                iconSize = 16.dp
            )
        }
    }
}

@Composable
private fun SettingsNavRow(entry: SettingsEntry, onClick: () -> Unit) {
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .md3SurfaceClick(onClick = onClick, shape = MaterialTheme.shapes.large, scaleTo = .99f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(entry.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val sub = entry.summary().ifBlank { entry.desc }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AccountCard(
    loggedIn: Boolean,
    nickname: String?,
    avatar: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .md3SurfaceClick(
                    onClick = { if (!loggedIn) onLogin() },
                    shape = MaterialTheme.shapes.large,
                    scaleTo = .99f
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Md3Cover(avatar, Modifier.size(52.dp), shape = CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    nickname ?: if (loggedIn) "网易云用户" else "未登录",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (loggedIn) "已连接网易云账号" else "点此扫码登录，同步歌单与红心",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loggedIn) {
                Md3IconButton(
                    Icons.Rounded.Logout,
                    contentDescription = "退出登录",
                    onClick = onLogout,
                    size = 40.dp,
                    iconSize = 20.dp
                )
            } else {
                Md3Button(
                    onClick = onLogin,
                    text = "登录",
                    icon = Icons.Rounded.Login,
                    size = Md3Size.XS
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 外观
// ---------------------------------------------------------------------------

@Composable
private fun AppearancePage(
    config: Md3Config,
    onBack: () -> Unit,
    onShape: () -> Unit,
    onMotion: () -> Unit,
    onLayout: () -> Unit
) {
    val seed = LocalMd3Seed.current
    SettingsScaffold("外观", onBack) {
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("当前配色", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "种子 ${String.format("#%06X", seed.toArgb() and 0xFFFFFF)} · ${config.schemeStyle.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Md3Badge(if (config.darkMode == DarkMode.Dark) "深色" else "自动")
                    }
                    Spacer(Modifier.height(14.dp))
                    Md3SchemePreview()
                }
            }
        }

        item {
            SettingsGroup("配色风格", "决定强调色的彩度与色相关系") {
                Md3OptionChips(
                    options = SchemeStyle.entries.map { it.label },
                    selected = config.schemeStyle.ordinal,
                    onSelect = { config.schemeStyle = SchemeStyle.from(it) }
                )
            }
        }

        item {
            SettingsGroup("取色来源") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = SeedSource.entries.map { it.label },
                        selected = config.seedSource.ordinal,
                        onSelect = { config.seedSource = SeedSource.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(10.dp))
                    when (config.seedSource) {
                        SeedSource.Album -> Label("换歌时自动跟着封面变色；取不到封面就回落到预设紫。")
                        SeedSource.Preset -> Md3ColorPalette(config.seedColor) {
                            config.seedColor = it.toArgb()
                        }
                        SeedSource.Manual -> Md3HueSliders(config.seedColor) {
                            config.seedColor = it.toArgb()
                        }
                    }
                }
            }
        }

        item {
            SettingsGroup("明暗") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = DarkMode.entries.map { it.label },
                        selected = config.darkMode.ordinal,
                        onSelect = { config.darkMode = DarkMode.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingsSwitch("纯黑背景", "深色下背景取 #000，省电但对比更硬", config.pureBlack) {
                        config.pureBlack = it
                    }
                }
            }
        }

        item {
            SettingsGroup("对比度", "拉开正文与背景的差值，适合户外或视力较弱时") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = listOf("标准", "中等", "高"),
                        selected = when {
                            config.contrast < .33f -> 0
                            config.contrast < .66f -> 1
                            else -> 2
                        },
                        onSelect = { config.contrast = it / 2f },
                        fullWidth = true
                    )
                }
            }
        }

        item {
            SettingsGroup("更多外观") {
                Column {
                    SubEntry(Icons.Rounded.ShapeLine, "形状与排版", shapeSummary(config), onShape)
                    SubEntry(Icons.Rounded.MotionPhotosOn, "动效", "${config.motion.label} · 触觉${if (config.haptics) "开" else "关"}", onMotion)
                    SubEntry(Icons.Rounded.Tune, "布局", "${config.navLayout.label} · ${config.homeLayout.label}", onLayout)
                }
            }
        }

        item {
            SettingsGroup {
                ResetRow("恢复外观默认值") { config.resetAppearance() }
            }
        }
    }
}

@Composable
private fun ShapePage(config: Md3Config, onBack: () -> Unit) {
    SettingsScaffold("形状与排版", onBack) {
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("圆角预览", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Md3ShapePreview(config.roundness)
                }
            }
        }
        item {
            SettingsGroup("圆润度", "0 = 直角，100 = 非常圆（Expressive 的 28dp 大圆角）") {
                Column(Modifier.padding(14.dp)) {
                    SettingsSlider(
                        value = config.roundness.toFloat(),
                        range = 0f..100f,
                        valueText = "${config.roundness}",
                        steps = 0
                    ) { config.roundness = it.toInt() }
                }
            }
        }
        item {
            SettingsGroup("布局密度", "影响所有间距，紧凑 ↔ 宽松") {
                Column(Modifier.padding(14.dp)) {
                    SettingsSlider(
                        value = config.density,
                        range = .85f..1.2f,
                        valueText = String.format("%.2f×", config.density)
                    ) { config.density = it }
                    Label("同一屏能放下更多内容，或让留白更从容。")
                }
            }
        }
        item {
            SettingsGroup("字号缩放", "只影响 MD3 界面的字阶，不动系统字体") {
                Column(Modifier.padding(14.dp)) {
                    SettingsSlider(
                        value = config.fontScale,
                        range = .85f..1.25f,
                        valueText = String.format("%.0f%%", config.fontScale * 100),
                        steps = 0
                    ) { config.fontScale = it }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "标题 Headline",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1
                    )
                    Text(
                        "正文 Body —— 拖动滑杆看这行变化",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SettingsGroup {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    SettingsSwitch("封面圆角", "关掉后所有封面变成直角，适合极简风", config.coverCorner) {
                        config.coverCorner = it
                    }
                }
            }
        }
        item {
            SettingsGroup {
                ResetRow("恢复形状默认值") {
                    config.roundness = 70
                    config.density = 1f
                    config.fontScale = 1f
                    config.coverCorner = true
                }
            }
        }
    }
}

@Composable
private fun MotionPage(config: Md3Config, onBack: () -> Unit) {
    SettingsScaffold("动效", onBack) {
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("动效预览", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "点一下上面这条轨道：小球用当前弹簧曲线跑一次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Md3MotionPreview(config.motion)
                }
            }
        }
        item {
            SettingsGroup("动效强度") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = MotionLevel.entries.map { it.label },
                        selected = config.motion.ordinal,
                        onSelect = { config.motion = MotionLevel.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Label(config.motion.hint)
                }
            }
        }
        item {
            SettingsGroup {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    SettingsSwitch("触觉反馈", "按下按钮、切换开关时给一次轻震", config.haptics) {
                        config.haptics = it
                    }
                }
            }
        }
        item {
            Text(
                "官方在 Expressive 里用弹簧替代了 easing + duration：位置/尺寸/圆角过冲回弹，" +
                    "颜色与透明度不允许过冲。「关闭」只保留必要的淡入淡出。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item {
            SettingsGroup { ResetRow("恢复动效默认值") { config.motion = MotionLevel.Expressive; config.haptics = true } }
        }
    }
}

@Composable
private fun LayoutPage(config: Md3Config, onBack: () -> Unit) {
    SettingsScaffold("布局", onBack) {
        item {
            SettingsGroup("导航栏") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = NavLayout.entries.map { it.label },
                        selected = config.navLayout.ordinal,
                        onSelect = { config.navLayout = NavLayout.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Md3NavPreview(config.navLayout)
                }
            }
        }
        item {
            SettingsGroup("首页样式", "「今日推荐」列表的排布") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = HomeLayout.entries.map { it.label },
                        selected = config.homeLayout.ordinal,
                        onSelect = { config.homeLayout = HomeLayout.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Md3HomePreview(config.homeLayout)
                }
            }
        }
        item {
            SettingsGroup("歌曲详情页") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = DetailLayout.entries.map { it.label },
                        selected = config.detailLayout.ordinal,
                        onSelect = { config.detailLayout = DetailLayout.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Label("「歌词优先」把歌词卡放在操作行下面；「封面优先」先给完整信息区。")
                }
            }
        }
        item {
            SettingsGroup("标题栏") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = HeaderStyle.entries.map { it.label },
                        selected = config.headerStyle.ordinal,
                        onSelect = { config.headerStyle = HeaderStyle.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Label("大标题更醒目，紧凑模式下首屏能多看到一行内容。")
                }
            }
        }
        item {
            SettingsGroup {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    SettingsSwitch("迷你播放条", "在底栏上方常驻一条播放控制", config.showMiniPlayer) {
                        config.showMiniPlayer = it
                    }
                    SettingsSwitch("记住上次的标签页", "冷启动后回到上次停留的分区", config.rememberTab) {
                        config.rememberTab = it
                    }
                }
            }
        }
        item { SettingsGroup { ResetRow("恢复布局默认值") { config.resetLayout() } } }
    }
}

// ---------------------------------------------------------------------------
// 播放
// ---------------------------------------------------------------------------

@Composable
private fun PlaybackPage(config: Md3Config, onBack: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    SettingsScaffold("播放", onBack) {
        item {
            SettingsGroup("Wi-Fi 音质", "在线播放时优先争取的上限") {
                Column(Modifier.padding(14.dp)) {
                    Md3OptionChips(
                        options = AudioQuality.entries.take(5).map { it.shortLabel },
                        selected = AudioQuality.entries.indexOfFirst { it.level == state.wifiQuality }
                            .coerceIn(0, 4),
                        onSelect = { bridge.setWifiQuality(AudioQuality.entries[it].level) }
                    )
                    Spacer(Modifier.height(8.dp))
                    val br = state.currentQuality?.bitrate ?: 0
                    Label(
                        if (br > 0) "当前：${qualityLabel(state.wifiQuality)}（$br kbps 实测）"
                        else "当前：${qualityLabel(state.wifiQuality)}（未在播放，暂无实测码率）"
                    )
                }
            }
        }
        item {
            SettingsGroup("移动网络音质") {
                Column(Modifier.padding(14.dp)) {
                    Md3OptionChips(
                        options = AudioQuality.entries.take(4).map { it.shortLabel },
                        selected = AudioQuality.entries.indexOfFirst { it.level == state.mobileQuality }
                            .coerceIn(0, 3),
                        onSelect = { bridge.setMobileQuality(AudioQuality.entries[it].level) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Label("移动网络下选无损会明显吃流量，按需选择。")
                }
            }
        }
        item {
            SettingsGroup("播放模式") {
                Column(Modifier.padding(14.dp)) {
                    Md3ConnectedButtonGroup(
                        options = listOf("列表循环", "单曲循环", "随机播放"),
                        selected = when (state.playMode) {
                            PlayMode.LoopAll -> 0
                            PlayMode.RepeatOne -> 1
                            else -> 2
                        },
                        onSelect = {
                            bridge.setPlayMode(
                                when (it) {
                                    1 -> PlayMode.RepeatOne
                                    2 -> PlayMode.Shuffle
                                    else -> PlayMode.LoopAll
                                }
                            )
                        },
                        fullWidth = true
                    )
                }
            }
        }
        item {
            SettingsGroup("播放页") {
                Column(Modifier.padding(14.dp)) {
                    Text("进度条", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Md3ConnectedButtonGroup(
                        options = SeekStyle.entries.map { it.label },
                        selected = config.seekStyle.ordinal,
                        onSelect = { config.seekStyle = SeekStyle.from(it) },
                        fullWidth = true
                    )
                    Spacer(Modifier.height(6.dp))
                    Md3WavyProgress(
                        progress = .62f,
                        playing = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingsSwitch("呼吸封面", "播这首歌时，详情页封面缓慢缩放", config.breathingCover) {
                        config.breathingCover = it
                    }
                }
            }
        }
        item {
            SettingsGroup("歌词") {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    SettingsSwitch(
                        "逐字歌词",
                        "音源带词级时间轴时按字上色；没有则整行高亮",
                        config.lyricWordByWord
                    ) {
                        config.lyricWordByWord = it
                        MusicServices.bridge.setWordByWord(it)
                    }
                    SettingsSwitch("显示翻译", "有机器翻译时显示在原文下方", config.showLyricTranslation) {
                        config.showLyricTranslation = it
                    }
                }
            }
        }
        item { SettingsGroup { ResetRow("恢复播放页默认值") { config.resetPlayer() } } }
    }
}

// ---------------------------------------------------------------------------
// 音源
// ---------------------------------------------------------------------------

@Composable
private fun SourcePage(onBack: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    SettingsScaffold("音源", onBack) {
        item {
            SettingsGroup("网络") {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    SettingsSwitch(
                        "直连网易云",
                        if (state.directMode) "直接访问 music.163.com" else "走自建代理 ${state.serverUrl}",
                        state.directMode
                    ) { bridge.setDirectMode(it) }
                }
            }
        }
        item {
            SettingsGroup("JS 音源脚本", "没有脚本时只能用内置接口，很多歌拿不到播放地址") {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.scripts.isEmpty()) "未导入"
                                else "${state.scripts.size} 个脚本 · ${state.scripts.count { it.enabled }} 个启用",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "支持从文件导入，或载入内置示例",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Md3Button(
                            onClick = { bridge.openScriptPicker() },
                            text = "导入",
                            size = Md3Size.XS
                        )
                    }
                    state.scripts.forEach { script ->
                        Md3Divider()
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    script.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    script.version,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = script.enabled, onCheckedChange = {
                                bridge.setScriptEnabled(script.id, it)
                            })
                            Md3IconButton(
                                Icons.Rounded.Delete,
                                contentDescription = "删除 ${script.name}",
                                onClick = { confirmDelete = script.id },
                                size = 40.dp,
                                iconSize = 18.dp
                            )
                        }
                    }
                    if (state.scripts.isEmpty()) {
                        Md3Divider()
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Extension,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "也可以先载入示例脚本试听",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Md3Button(
                                onClick = { bridge.loadSampleScript() },
                                text = "载入示例",
                                style = Md3ButtonStyle.Tonal,
                                size = Md3Size.XS
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "本项目不附带任何音源脚本，请自行准备并遵守相应服务条款。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这个脚本？") },
            text = { Text("删除后需要重新导入才能使用。") },
            confirmButton = {
                TextButton(onClick = {
                    bridge.removeScript(id)
                    confirmDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}

// ---------------------------------------------------------------------------
// 存储
// ---------------------------------------------------------------------------

@Composable
private fun StoragePage(onBack: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<CacheScope?>(null) }

    SettingsScaffold("存储", onBack) {
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("缓存占用", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CacheTile("音频", state.cacheInfo.audioBytes, Modifier.weight(1f))
                        CacheTile("图片", state.cacheInfo.imageBytes, Modifier.weight(1f))
                        CacheTile("数据", state.cacheInfo.dataBytes, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${state.cacheInfo.songCount} 首音频 · ${state.cacheInfo.recentCount} 条播放记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SettingsGroup("清理") {
                Column {
                    SubEntry(Icons.Rounded.Delete, "清理音频缓存", md3Bytes(state.cacheInfo.audioBytes)) {
                        confirm = CacheScope.Audio
                    }
                    SubEntry(Icons.Rounded.Delete, "清理图片缓存", md3Bytes(state.cacheInfo.imageBytes)) {
                        confirm = CacheScope.Images
                    }
                    SubEntry(Icons.Rounded.Restore, "清理全部缓存", md3Bytes(state.cacheInfo.totalBytes)) {
                        confirm = CacheScope.All
                    }
                }
            }
        }
        item {
            SettingsGroup("音频缓存上限", "超过后按最久未播放淘汰") {
                Column(Modifier.padding(14.dp)) {
                    SettingsSlider(
                        value = state.audioCacheLimitMb.toFloat(),
                        range = 512f..4096f,
                        valueText = "${state.audioCacheLimitMb} MB",
                        steps = 13
                    ) { bridge.setAudioCacheLimitMb(it.toInt()) }
                }
            }
        }
    }

    confirm?.let { target ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("确定清理缓存？") },
            text = {
                Text(
                    when (target) {
                        CacheScope.Audio -> "会删除已下载的音频，正在播放的歌不受影响。"
                        CacheScope.Images -> "会删除封面与头像缓存，之后重新加载。"
                        CacheScope.Data -> "会删除接口数据缓存。"
                        CacheScope.All -> "会删除音频、图片与接口数据，之后首次加载会变慢。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { bridge.clearCache(target) }
                        bridge.showToast("已清理")
                    }
                    confirm = null
                }) { Text("清理") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CacheTile(label: String, bytes: Long, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(md3Bytes(bytes), style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// 关于与维护
// ---------------------------------------------------------------------------

@Composable
private fun AboutPage(
    config: Md3Config,
    onBack: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onRestart: () -> Unit
) {
    val bridge = MusicServices.bridge
    SettingsScaffold("关于与维护", onBack) {
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("ASMusic", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "版本 ${bridge.appVersion} · Material 3 界面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "两套界面共用同一份播放内核与设置项之外的配置；" +
                            "这里的设置只作用于 Material 3 界面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SettingsGroup("诊断") {
                Column {
                    SubEntry(Icons.Rounded.HealthAndSafety, "软件全面自检", "环境 / 网络 / 播放 / Node / 缓存", onOpenSelfCheck)
                    SubEntry(Icons.Rounded.RestartAlt, "重启应用", "换界面风格后需要重启") { onRestart() }
                }
            }
        }
        item {
            SettingsGroup("重置") {
                Column {
                    ResetRow("恢复外观默认值") { config.resetAppearance() }
                    Md3Divider()
                    ResetRow("恢复布局默认值") { config.resetLayout() }
                    Md3Divider()
                    ResetRow("恢复播放页默认值") { config.resetPlayer() }
                }
            }
        }
        item {
            Text(
                "配色、形状与动效全部由本地算法生成，不依赖联网主题服务。图标来自官方 " +
                    "material-icons-extended（Apache-2.0）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 共用设置件
// ---------------------------------------------------------------------------

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        // 用 container 而不是 containerLow：开了 AMOLED 纯黑后，
        // containerLow 和 #000 的差值只有 3% 左右，卡片会整片消失。
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    desc: String? = null,
    content: @Composable () -> Unit
) {
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = if (desc == null) 6.dp else 2.dp)
            )
        }
        if (desc != null) {
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        SettingsCard { content() }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    desc: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .md3SurfaceClick(onClick = { onChange(!checked) }, shape = MaterialTheme.shapes.medium, scaleTo = 1f)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun SubEntry(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .md3SurfaceClick(onClick = onClick, shape = MaterialTheme.shapes.medium, scaleTo = .99f)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Rounded.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ResetRow(text: String, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .md3SurfaceClick(onClick = { confirm = true }, shape = MaterialTheme.shapes.medium, scaleTo = .99f)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Restore,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(text + "？") },
            text = { Text("只会重置这一组的设置，其它保持不变。") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    confirm = false
                }) { Text("重置") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Md3TopBar(title = title, onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** 一组 2-8 个选项的按钮组（Expressive toggle：选中与未选中形状不同）。 */
@Composable
private fun Md3OptionChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (options.size >= 4) 3 else options.size
    ) {
        options.forEachIndexed { i, label ->
            Md3ToggleButton(
                selected = i == selected,
                onToggle = { onSelect(i) },
                text = label,
                size = Md3Size.XS
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 预览件（让设置「看得见」）
// ---------------------------------------------------------------------------

@Composable
private fun Md3SchemePreview() {
    val c = MaterialTheme.colorScheme
    val swatches = listOf(
        "主色" to c.primary,
        "次色" to c.secondary,
        "第三" to c.tertiary,
        "容器" to c.primaryContainer,
        "表面1" to c.surfaceContainerLow,
        "表面2" to c.surfaceContainerHigh
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { (_, color) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerLow)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Palette,
                    null,
                    tint = c.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("正文 onSurface", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "次要文字 onSurfaceVariant",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant
                )
            }
            Md3Badge("强调")
        }
    }
}

private val Md3CornerSteps = listOf(0, 4, 8, 12, 16, 20, 28)

private fun cornerFor(roundness: Int) =
    Md3CornerSteps[(roundness.coerceIn(0, 100) / 100f * (Md3CornerSteps.size - 1)).toInt()]

@Composable
private fun Md3ShapePreview(roundness: Int) {
    val dp = cornerFor(roundness).dp
    val r = dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(64.dp, 52.dp, 40.dp).forEachIndexed { i, size ->
            // 每块用自己的尺寸做上限，避免小方块被圆角吃掉变成圆
            val corner = minOf(r.value, size.value / 2f).dp
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(corner))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 1f - i * .22f))
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(r))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${r.value.toInt()}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun Md3MotionPreview(level: MotionLevel) {
    val tokens = LocalMd3Tokens.current
    var right by remember { mutableStateOf(false) }
    val pos by animateFloatAsState(
        targetValue = if (right) 1f else 0f,
        animationSpec = when (level) {
            MotionLevel.Expressive -> androidx.compose.animation.core.spring(
                dampingRatio = .52f, stiffness = 260f
            )
            MotionLevel.Standard -> androidx.compose.animation.core.spring(
                dampingRatio = .78f, stiffness = 420f
            )
            MotionLevel.Off -> androidx.compose.animation.core.tween(0)
        },
        label = "motion-preview"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .md3SurfaceClick(
                onClick = { right = !right },
                shape = RoundedCornerShape(28.dp),
                scaleTo = 1f
            )
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints {
            val travel = maxWidth - 40.dp
            Box(
                Modifier
                    .size(40.dp)
                    .offsetX(travel * pos)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun Md3NavPreview(layout: NavLayout) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceContainerHigh)
    ) {
        if (layout != NavLayout.Bar) {
            Column(
                Modifier
                    .width(44.dp)
                    .fillMaxSize()
                    .background(c.surfaceContainerHighest),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (it == 0) c.primary else c.onSurfaceVariant.copy(alpha = .3f))
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth())
            if (layout != NavLayout.Rail) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(c.surfaceContainerHighest),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (it == 0) c.primary else c.onSurfaceVariant.copy(alpha = .3f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Md3HomePreview(layout: HomeLayout) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceContainerHigh)
            .padding(8.dp)
    ) {
        when (layout) {
            HomeLayout.Cards -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(52.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.primary.copy(alpha = .25f))
                    )
                }
            }
            HomeLayout.List -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.primary.copy(alpha = .22f))
                    )
                }
            }
            HomeLayout.Grid -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(2) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.secondaryContainer)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.offsetX(dx: androidx.compose.ui.unit.Dp): Modifier = this.offset(x = dx)

// ---------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------

fun md3Bytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
    else -> String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024)
}

fun qualityShortLabel(level: String): String =
    AudioQuality.entries.firstOrNull { it.level == level }?.shortLabel ?: "自动"

fun qualityLabel(level: String): String =
    AudioQuality.entries.firstOrNull { it.level == level }?.label ?: "自动"

fun playModeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.LoopAll -> "列表循环"
    PlayMode.RepeatOne -> "单曲循环"
    PlayMode.Shuffle -> "随机"
    else -> "顺序"
}

private fun shapeSummary(config: Md3Config): String =
    "圆润度 ${config.roundness} · 字号 ${(config.fontScale * 100).toInt()}% · 密度 ${String.format("%.2f×", config.density)}"

// ---------------------------------------------------------------------------
// 扫码登录
// ---------------------------------------------------------------------------

@Composable
fun Md3QrSheet(onClose: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    var key by remember { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var status by remember { mutableStateOf("正在获取二维码…") }

    LaunchedEffect(Unit) {
        val k = runCatching { bridge.qrKey() }.getOrNull()
        key = k
        if (k != null) {
            image = runCatching { bridge.qrImage(k) }.getOrNull()
            status = "用网易云 App 扫码"
        } else {
            status = "无法获取二维码，检查网络或直连设置"
        }
    }
    LaunchedEffect(key) {
        val k = key ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(2200)
            val r = runCatching { bridge.qrCheck(k) }.getOrNull() ?: continue
            status = r.message
            if (r.code == 803) {
                onClose()
                break
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("扫码登录") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(200.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    val img = image
                    if (img != null) {
                        androidx.compose.foundation.Image(
                            bitmap = img,
                            contentDescription = "登录二维码",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            state.nickname ?: "加载中…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}


// ---------------------------------------------------------------------------
// 色板 / 手动调色
// ---------------------------------------------------------------------------

@Composable
fun Md3ColorPalette(seed: Int, onPick: (Color) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Md3PresetSeeds.forEach { (name, color) ->
            val isSelected = seed == color.toArgb()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) {
                                Modifier.md3BorderRing(MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else Modifier
                        )
                        .md3SurfaceClick(onClick = { onPick(color) }, shape = CircleShape, scaleTo = .9f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Modifier.md3BorderRing(color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.border(2.dp, color, shape)

/** 手动色相 / 彩度：从当前种子的 OKLCH 值起步，不会一拖就跳色。 */
@Composable
fun Md3HueSliders(seed: Int, onPick: (Color) -> Unit) {
    val base = remember(seed) {
        Color(if (seed == 0) 0xFF6750A4.toInt() else seed)
    }
    val initial = remember(seed) { base.toOklch() }
    var hue by remember(seed) {
        mutableStateOf(((Math.toDegrees(initial.h).toFloat() % 360f) + 360f) % 360f)
    }
    var chroma by remember(seed) {
        mutableStateOf((initial.c / 0.16).toFloat().coerceIn(0f, 1f))
    }
    Column {
        Text(
            "色相 ${hue.toInt()}° · 彩度 ${(chroma * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text("色相", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                onPick(oklchPreview(hue, chroma))
            },
            valueRange = 0f..360f
        )
        Text("彩度", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = chroma,
            onValueChange = {
                chroma = it
                onPick(oklchPreview(hue, chroma))
            },
            valueRange = 0f..1f
        )
    }
}

/** 与主题同源的最小预览色；只用于拖动时的即时反馈。 */
private fun oklchPreview(hue: Float, chroma: Float): Color {
    val h = Math.toRadians(hue.toDouble())
    val c = chroma.toDouble() * 0.16
    val a = c * kotlin.math.cos(h)
    val b = c * kotlin.math.sin(h)
    val l = 0.62
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b
    val ll = l_ * l_ * l_
    val mm = m_ * m_ * m_
    val ss = s_ * s_ * s_
    fun enc(v: Double): Float =
        if (v <= 0.0031308) (12.92 * v).toFloat()
        else (1.055 * Math.pow(v, 1 / 2.4) - 0.055).toFloat()

    val r = 4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss
    val g = -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss
    val bl = -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss
    return Color(enc(r).coerceIn(0f, 1f), enc(g).coerceIn(0f, 1f), enc(bl).coerceIn(0f, 1f))
}
