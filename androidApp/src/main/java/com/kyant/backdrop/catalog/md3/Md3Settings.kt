@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kyant.backdrop.catalog.md3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.AudioQuality
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.PlayMode

/**
 * MD3 设置页。与 iOS 那套完全独立的配置（存在 md3_app.xml），
 * 所以这里可以放开手做「主题实验台」，不影响原界面。
 */
@Composable
fun Md3SettingsScreen(
    config: Md3Config,
    onBack: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onRestart: () -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val scope = rememberCoroutineScope()
    var showStyleConfirm by remember { mutableStateOf<UiStyle?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    // 用可变状态包住配置，改完立刻重组预览
    var revision by remember { mutableStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 44.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text("设置", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Material 3 界面 · 独立配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---------------- 账号 ----------------
        item {
            Md3SettingsGroup {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .md3Clickable { if (state.loggedIn) { } else showLogin = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md3Cover(
                        state.avatarUrl,
                        Modifier.size(48.dp),
                        shape = CircleShape
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.nickname ?: if (state.loggedIn) "网易云用户" else "未登录",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (state.loggedIn) "已连接网易云账号" else "点击扫码登录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (state.loggedIn) Icons.Rounded.Check else Icons.Rounded.Login,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.loggedIn) {
                    Md3Divider()
                    Md3ValueRow("退出登录") {
                        scope.launch {
                            bridge.logout()
                            bridge.showToast("已退出登录")
                        }
                    }
                }
            }
        }

        // ---------------- 界面风格 ----------------
        item {
            Md3SectionLabel("界面风格")
            Md3SettingsGroup {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "两套界面各自独立，切换后需要重启应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiStyle.entries.forEach { style ->
                            FilterChip(
                                selected = Md3Config.readStyle(ctx) == style,
                                onClick = { showStyleConfirm = style },
                                label = { Text(style.label) },
                                leadingIcon = if (Md3Config.readStyle(ctx) == style) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 主题 ----------------
        item { Md3SectionLabel("主题") }
        item {
            Md3SettingsGroup {
                Md3ChipBlock(
                    title = "配色风格",
                    desc = "决定强调色的彩度与色相关系",
                    options = SchemeStyle.entries.map { it.label },
                    selected = config.schemeStyle.ordinal
                ) {
                    config.schemeStyle = SchemeStyle.from(it); revision++
                }
                Md3Divider()
                Md3ChipBlock(
                    title = "取色来源",
                    desc = when (config.seedSource) {
                        SeedSource.Album -> "跟随当前播放封面，换歌即换色"
                        SeedSource.Manual -> "手动调色相与彩度"
                        SeedSource.Preset -> "从下方色板里选一个"
                    },
                    options = SeedSource.entries.map { it.label },
                    selected = config.seedSource.ordinal
                ) {
                    config.seedSource = SeedSource.from(it); revision++
                }
                if (config.seedSource != SeedSource.Album) {
                    Md3Divider()
                    Md3ColorPalette(config.seedColor) {
                        config.seedColor = it.toArgb(); revision++
                    }
                }
                if (config.seedSource == SeedSource.Manual) {
                    Md3Divider()
                    Md3HueSliders(config.seedColor) { config.seedColor = it.toArgb(); revision++ }
                }
                Md3Divider()
                Md3ChipBlock(
                    title = "明暗",
                    options = DarkMode.entries.map { it.label },
                    selected = config.darkMode.ordinal
                ) { config.darkMode = DarkMode.from(it); revision++ }
                Md3Divider()
                Md3SwitchRow(
                    "纯黑背景（AMOLED）",
                    "深色主题下背景取纯黑，省电但对比更硬",
                    config.pureBlack
                ) { config.pureBlack = it; revision++ }
                Md3Divider()
                Md3ChipBlock(
                    title = "对比度",
                    options = listOf("标准", "中等", "高"),
                    selected = when {
                        config.contrast < .33f -> 0
                        config.contrast < .66f -> 1
                        else -> 2
                    }
                ) { config.contrast = it / 2f; revision++ }
            }
        }

        // ---------------- 形状与动效 ----------------
        item { Md3SectionLabel("形状 · 排版 · 动效") }
        item {
            Md3SettingsGroup {
                Md3SliderRow(
                    "圆润度",
                    "${config.roundness}",
                    "0 = 直角，100 = 非常圆（Expressive）",
                    config.roundness.toFloat(), 0f..100f
                ) { config.roundness = it.toInt(); revision++ }
                Md3Divider()
                Md3SliderRow(
                    "布局密度",
                    String.format("%.2f×", config.density),
                    "影响所有间距，紧凑 ↔ 宽松",
                    config.density, .85f..1.2f
                ) { config.density = it; revision++ }
                Md3Divider()
                Md3SliderRow(
                    "字号缩放",
                    String.format("%.0f%%", config.fontScale * 100),
                    "只影响 MD3 界面的字阶",
                    config.fontScale, .85f..1.25f
                ) { config.fontScale = it; revision++ }
                Md3Divider()
                Md3ChipBlock(
                    title = "动效",
                    desc = "表达力档位会让按下、歌词、播放按钮带弹簧回弹",
                    options = MotionLevel.entries.map { it.label },
                    selected = config.motion.ordinal
                ) { config.motion = MotionLevel.from(it); revision++ }
            }
        }

        // ---------------- 布局 ----------------
        item { Md3SectionLabel("布局") }
        item {
            Md3SettingsGroup {
                Md3ChipBlock(
                    title = "导航栏",
                    options = NavLayout.entries.map { it.label },
                    selected = config.navLayout.ordinal
                ) { config.navLayout = NavLayout.from(it); revision++ }
                Md3Divider()
                Md3ChipBlock(
                    title = "首页样式",
                    options = HomeLayout.entries.map { it.label },
                    selected = config.homeLayout.ordinal
                ) { config.homeLayout = HomeLayout.from(it); revision++ }
                Md3Divider()
                Md3ChipBlock(
                    title = "播放页歌词",
                    options = DetailLayout.entries.map { it.label },
                    selected = config.detailLayout.ordinal
                ) { config.detailLayout = DetailLayout.from(it); revision++ }
                Md3Divider()
                Md3SwitchRow("迷你播放条", "在底栏上方常驻一条播放控制", config.showMiniPlayer) {
                    config.showMiniPlayer = it; revision++
                }
                Md3Divider()
                Md3SwitchRow("歌词显示翻译", null, config.showLyricTranslation) {
                    config.showLyricTranslation = it; revision++
                }
                Md3Divider()
                Md3SwitchRow("播放页呼吸封面", "播放时封面缓慢缩放", config.breathingCover) {
                    config.breathingCover = it; revision++
                }
                Md3Divider()
                Md3SwitchRow("首页问候语", "显示时段问候与正在播放", config.greeting != 2) {
                    config.greeting = if (it) 1 else 2; revision++
                }
            }
        }

        // ---------------- 播放 ----------------
        item { Md3SectionLabel("播放") }
        item {
            Md3SettingsGroup {
                Md3ChipBlock(
                    title = "Wi-Fi 音质",
                    options = AudioQuality.entries.take(5).map { it.label },
                    selected = AudioQuality.entries.indexOfFirst { it.level == state.wifiQuality }.coerceAtLeast(0)
                ) { bridge.setWifiQuality(AudioQuality.entries[it].level); revision++ }
                Md3Divider()
                Md3ChipBlock(
                    title = "移动网络音质",
                    options = AudioQuality.entries.take(4).map { it.label },
                    selected = AudioQuality.entries.indexOfFirst { it.level == state.mobileQuality }.coerceAtLeast(0)
                ) { bridge.setMobileQuality(AudioQuality.entries[it].level); revision++ }
                Md3Divider()
                Md3ChipBlock(
                    title = "播放模式",
                    options = listOf("列表循环", "单曲循环", "随机"),
                    selected = when (state.playMode) {
                        PlayMode.LoopAll -> 0
                        PlayMode.RepeatOne -> 1
                        else -> 2
                    }
                ) {
                    bridge.setPlayMode(
                        when (it) {
                            1 -> PlayMode.RepeatOne
                            2 -> PlayMode.Shuffle
                            else -> PlayMode.LoopAll
                        }
                    ); revision++
                }
            }
        }

        // ---------------- 音源 ----------------
        item { Md3SectionLabel("音源") }
        item {
            Md3SettingsGroup {
                Md3SwitchRow("直连模式", "关闭后走自建 NeteaseCloudMusicApi 代理", state.directMode) {
                    bridge.setDirectMode(it); revision++
                }
                Md3Divider()
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("JS 音源脚本", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (state.scripts.isEmpty()) "未导入" else "${state.scripts.count { it.enabled }} / ${state.scripts.size} 已启用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { bridge.openScriptPicker() }) { Text("导入") }
                }
                state.scripts.forEach { script ->
                    Md3Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(script.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                script.version,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = script.enabled, onCheckedChange = {
                            bridge.setScriptEnabled(script.id, it); revision++
                        })
                        IconButton(onClick = { bridge.removeScript(script.id); revision++ }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (state.scripts.isEmpty()) {
                    Md3Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "没有脚本时播放依赖内置接口",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { bridge.loadSampleScript(); revision++ }) { Text("载入示例") }
                    }
                }
            }
        }

        // ---------------- 维护 ----------------
        item { Md3SectionLabel("维护") }
        item {
            Md3SettingsGroup {
                Md3ValueRow("软件全面自检", "检查运行环境、网络、Node 与缓存") { onOpenSelfCheck() }
                Md3Divider()
                Md3ValueRow("清理缓存") {
                    scope.launch {
                        bridge.clearCache(com.kyant.backdrop.catalog.appstore.music.CacheScope.All)
                        bridge.showToast("缓存已清理")
                    }
                }
                Md3Divider()
                Md3ValueRow("重启应用", "切换界面风格后需要重启") { onRestart() }
                Md3Divider()
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("版本", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        bridge.appVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                "配色、形状与动效全部由本地算法生成，不依赖联网主题服务。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }

    // 风格切换确认
    showStyleConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { showStyleConfirm = null },
            title = { Text("切换到「${target.label}」？") },
            text = { Text(target.desc + "\n\n重启应用后生效，当前界面的设置会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    Md3Config.writeStyle(ctx, target)
                    showStyleConfirm = null
                    onRestart()
                }) { Text("切换并重启") }
            },
            dismissButton = {
                TextButton(onClick = { showStyleConfirm = null }) { Text("取消") }
            }
        )
    }

    if (showLogin) {
        Md3QrSheet(onClose = { showLogin = false })
    }
}

// ---------------------------------------------------------------------------
// 设置项基础件
// ---------------------------------------------------------------------------

@Composable
fun Md3SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun Md3SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        Column { content() }
    }
}

@Composable
fun Md3SwitchRow(
    title: String,
    desc: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
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
fun Md3ValueRow(title: String, desc: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .md3Clickable(scaleTo = .99f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
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
        Icon(
            Icons.Rounded.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).graphicsRotate(180f)
        )
    }
}

private fun Modifier.graphicsRotate(deg: Float): Modifier =
    this.graphicsLayer { rotationZ = deg }

@Composable
fun Md3ChipBlock(
    title: String,
    desc: String? = null,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (desc != null) {
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { i, label ->
                FilterChip(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

@Composable
fun Md3SliderRow(
    title: String,
    valueText: String,
    desc: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (desc != null) {
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun Md3ColorPalette(seed: Int, onPick: (Color) -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text("色板", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Md3PresetSeeds.forEach { (_, color) ->
                val isSelected = seed == color.toArgb()
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .md3Clickable(scaleTo = .9f) { onPick(color) }
                )
            }
        }
    }
}

/** 手动色相 / 彩度：直接调 OKLCH，所见即所得。 */
@Composable
fun Md3HueSliders(seed: Int, onPick: (Color) -> Unit) {
    val base = remember(seed) { Color(if (seed == 0) 0xFF6750A4.toInt() else seed) }
    var hue by remember(seed) { mutableStateOf(0f) }
    var chroma by remember(seed) { mutableStateOf(0f) }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("手动调色", style = MaterialTheme.typography.bodyLarge)
        Text(
            "色相 ${hue.toInt()}° · 彩度 ${(chroma * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(value = hue, onValueChange = {
            hue = it
            onPick(oklchPreview(hue, chroma))
        }, valueRange = 0f..360f)
        Slider(value = chroma, onValueChange = {
            chroma = it
            onPick(oklchPreview(hue, chroma))
        }, valueRange = 0f..1f)
    }
}

/** 与主题同源的轻量预览色（避免把内部色板类暴露出去）。 */
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
    fun enc(v: Double): Float = if (v <= 0.0031308) (12.92 * v).toFloat()
    else (1.055 * Math.pow(v, 1 / 2.4) - 0.055).toFloat()
    val r = 4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss
    val g = -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss
    val bl = -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss
    return Color(enc(r).coerceIn(0f, 1f), enc(g).coerceIn(0f, 1f), enc(bl).coerceIn(0f, 1f))
}

// ---------------------------------------------------------------------------
// 扫码登录（简版）
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
                        Text(state.nickname ?: "加载中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}
