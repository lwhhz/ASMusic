package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.ChevronRight
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun OverlaySheetScaffold(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = .92f, stiffness = 380f)
    )
    if (progress < .001f) return

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .62f * progress))
                .tap(onDismiss)
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.86f)
                .offset { IntOffset(0, ((1f - progress) * 900.dp.toPx()).toInt()) }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AS.Card)
                // Swallow taps on the panel itself: empty space used to fall through to
                // the scrim behind it and dismiss the sheet. Only the ✕ button closes.
                .tap { }
                .padding(horizontal = 16.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                Txt(title, AS.rowTitle().copy(fontSize = 17.sp))
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AS.CardElevated)
                        .tap(onDismiss),
                    contentAlignment = Alignment.Center
                ) { Txt("✓", AS.body().copy(fontSize = 16.sp, color = AS.Blue)) }
            }
            if (scrollable) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) { content() }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AS.CardElevated)
            .tap(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Txt(label, AS.body().copy(fontSize = 15.sp, color = AS.Blue)) }
}

// ---------------------------------------------------------------------------
// Netease QR login
// ---------------------------------------------------------------------------

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AS.Blue else AS.CardElevated)
            .tap(onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Txt(
            label,
            AS.body().copy(fontSize = 14.sp, color = if (selected) Color.White else AS.Text2),
            maxLines = 1
        )
    }
}

@Composable
fun QrLoginSheet(visible: Boolean, onDismiss: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    var key by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf("正在生成二维码…") }
    var serverText by remember { mutableStateOf(state.serverUrl) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(visible, state.directMode, reload) {
        if (!visible) return@LaunchedEffect
        status = "正在生成二维码…"
        key = runCatching { bridge.qrKey() }.getOrNull()
        key?.let { bridge.rememberQrKey(it) }
        qr = key?.let { runCatching { bridge.qrImage(it) }.getOrNull() }
        status = when {
            qr != null -> "请使用网易云 App 扫码"
            state.directMode -> "二维码获取失败，请检查手机网络后重试"
            else -> "二维码获取失败，请确认代理服务地址可用"
        }
        val currentKey = key ?: return@LaunchedEffect
        var failures = 0
        while (true) {
            delay(2000)
            val result = runCatching { bridge.qrCheck(currentKey) }.getOrNull()
            if (result == null) {
                failures++
                if (failures >= 5) {
                    status = "网络异常，请关闭后重试"
                    break
                }
                continue
            }
            failures = 0
            when {
                result.isSuccess -> {
                    val cookie = result.cookie
                    if (cookie != null) bridge.applyCookie(cookie)
                    status = "登录成功，正在同步…"
                    delay(700)
                    onDismiss()
                    break
                }
                result.isExpired -> { status = "二维码已过期，请关闭后重新打开"; break }
                result.code == 802 -> status = "已扫码，请在手机端确认登录"
                else -> status = "等待扫码…（请勿关闭本页）"
            }
        }
    }

    OverlaySheetScaffold(visible, "链接网易云账号", onDismiss) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmap = qr
            if (bitmap != null) {
                Image(
                    bitmap, null,
                    Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp)
                )
            } else {
                Box(
                    Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Txt("二维码加载中", AS.body().copy(color = Color.Black)) }
            }
            Spacer(Modifier.height(16.dp))
            Txt(status, AS.subtitle())
            if (qr == null) {
                Spacer(Modifier.height(10.dp))
                SheetButton("重新获取二维码", onClick = { reload++ })
            }
            Spacer(Modifier.height(8.dp))
            Txt(
                if (state.loggedIn) "已登录 · Cookie 仅保存在本机" else "扫码后每日推荐、私人 FM、播放地址等功能才可用",
                AS.caption()
            )
        }

        Spacer(Modifier.height(22.dp))
        Txt("连接方式", AS.caption().copy(color = AS.Text3))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("直连（推荐）", state.directMode, Modifier.weight(1f)) {
                if (!state.directMode) {
                    bridge.setDirectMode(true)
                    bridge.showToast("已切换到直连模式")
                }
            }
            ModeChip("自建代理", !state.directMode, Modifier.weight(1f)) {
                if (state.directMode) {
                    bridge.setDirectMode(false)
                    bridge.showToast("已切换到代理模式")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt(
            if (state.directMode) {
                "直连模式：App 直接访问网易云官方接口，扫码即可登录，不需要电脑或代理服务。"
            } else {
                "代理模式：需要在本机或服务器运行 server/ 目录下的 NeteaseCloudMusicApi。"
            },
            AS.caption()
        )

        if (!state.directMode) {
            Spacer(Modifier.height(18.dp))
            Txt("API 代理地址", AS.caption().copy(color = AS.Text3))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = serverText,
                    onValueChange = { serverText = it },
                    singleLine = true,
                    textStyle = AS.body().copy(fontSize = 13.sp),
                    cursorBrush = SolidColor(AS.Blue),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AS.CardElevated)
                        .padding(10.dp)
                )
                Spacer(Modifier.width(8.dp))
                SheetButton("保存", onClick = {
                    bridge.setServerUrl(serverText)
                    bridge.showToast("已保存：$serverText")
                    reload++
                })
            }
            Spacer(Modifier.height(12.dp))
            Txt(
                "USB 调试时可先执行 adb reverse tcp:3000 tcp:3000，再填 http://127.0.0.1:3000。",
                AS.caption()
            )
        }
    }
}

// ---------------------------------------------------------------------------
// JS music-source script import
// ---------------------------------------------------------------------------

@Composable
fun ScriptImportSheet(visible: Boolean, onDismiss: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun importRemote(rawUrl: String, catalogId: String = "") {
        if (busy) return
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://")) {
            bridge.showToast("只支持 https:// 音源链接")
            return
        }
        busy = true
        scope.launch {
            val info = bridge.importScriptFromUrl(trimmed, catalogId)
            busy = false
            if (info != null) {
                bridge.showToast(if (catalogId.isBlank()) "已导入 ${info.name}" else "已导入/更新 ${info.name}")
                url = ""
            } else {
                bridge.showToast("导入失败，请检查链接或网络")
            }
        }
    }

    OverlaySheetScaffold(visible, "JS 音乐源脚本", onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Txt("已导入的脚本", AS.caption().copy(color = AS.Text3))
            Spacer(Modifier.height(10.dp))
            if (state.scripts.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AS.CardElevated)
                        .padding(16.dp)
                ) { Txt("还没有脚本。可以从文件或在线链接导入。", AS.subtitle()) }
            } else {
                state.scripts.forEach { script ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AS.CardElevated)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Txt(script.name, AS.rowTitle().copy(fontSize = 15.sp), maxLines = 1)
                                Txt(
                                    "${script.version} · ${script.description}",
                                    AS.caption(),
                                    maxLines = 2
                                )
                            }
                            SheetButton(if (script.enabled) "已启用" else "启用", onClick = {
                                bridge.setScriptEnabled(script.id, !script.enabled)
                            })
                            Spacer(Modifier.width(6.dp))
                            SheetButton("删除", onClick = { bridge.removeScript(script.id) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            SheetButton(
                "从文件导入 .js",
                onClick = { bridge.openScriptPicker() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            Txt("在线链接导入", AS.caption().copy(color = AS.Text3))
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                textStyle = AS.body().copy(fontSize = 12.sp),
                cursorBrush = SolidColor(AS.Blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AS.CardElevated)
                    .padding(10.dp)
            )
            Spacer(Modifier.height(8.dp))
            SheetButton(
                if (busy) "处理中…" else "导入链接",
                onClick = { importRemote(url) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Txt(
                "仅支持 HTTPS 的 .js 直链。GitHub raw 失败时会自动尝试 gh-proxy.org。",
                AS.caption()
            )

            Spacer(Modifier.height(22.dp))
            Txt("内置音源（pdone/lx-music-source）", AS.caption().copy(color = AS.Text3))
            Spacer(Modifier.height(8.dp))
            PdoneScriptSources.forEach { source ->
                val installed = state.scripts.firstOrNull {
                    it.catalogId == source.id || it.sourceUrl == source.url
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AS.CardElevated)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt(source.name, AS.rowTitle().copy(fontSize = 15.sp), maxLines = 1)
                            Txt(
                                if (installed != null) {
                                    "已安装 v${installed.version}" + if (installed.enabled) "" else " · 已停用"
                                } else {
                                    "未安装"
                                },
                                AS.caption(),
                                maxLines = 1
                            )
                        }
                        SheetButton(
                            if (busy) "…" else if (installed != null) "更新" else "导入",
                            onClick = {
                                if (!busy) importRemote(source.url, source.id)
                            }
                        )
                        if (installed != null) {
                            Spacer(Modifier.width(6.dp))
                            SheetButton("卸载", onClick = {
                                if (!busy) {
                                    bridge.removeScript(installed.id)
                                    bridge.showToast("已卸载 ${source.name}")
                                }
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Txt(
                "支持 LX Music 自定义音源（lx.on / lx.send / lx.request），以及 sourceInfo() + resolve(songJson) 简单协议。脚本在应用进程内执行，请仅导入可信来源。",
                AS.caption()
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Today card picker
// ---------------------------------------------------------------------------

@Composable
fun TodayCardPicker(visible: Boolean, onDismiss: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 62.dp.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var order by remember { mutableStateOf<List<PlaylistKind>>(emptyList()) }
    var dragging by remember { mutableStateOf<PlaylistKind?>(null) }
    var settlingKind by remember { mutableStateOf<PlaylistKind?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            val selected = state.todayKinds
            order = selected + Fixtures.allTodayKinds.filter { it !in selected }
            dragging = null
            settlingKind = null
            dragOffset = 0f
            settle.snapTo(0f)
        }
    }

    fun finishDrag() {
        val kind = dragging ?: return
        val start = dragOffset
        dragging = null
        settlingKind = kind
        dragOffset = 0f
        scope.launch {
            settle.snapTo(start)
            settle.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = .78f, stiffness = 360f)
            )
            if (settlingKind == kind) settlingKind = null
        }
    }

    OverlaySheetScaffold(visible, "自定义首页卡片", onDismiss) {
        Txt("勾选要在「今天」页展示的歌单；长按拖动可排序", AS.subtitle())
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(order, key = { _, kind -> kind }) { _, kind ->
                val checked = kind in state.todayKinds
                val isDragging = dragging == kind
                val isSettling = settlingKind == kind
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.025f else 1f,
                    animationSpec = spring(dampingRatio = .72f, stiffness = 420f),
                    label = "cardLift"
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .animateItem()
                        .zIndex(if (isDragging || isSettling) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging || isSettling) dragOffset + settle.value else 0f
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDragging) AS.Blue.copy(alpha = .20f) else AS.CardElevated)
                        .padding(horizontal = 14.dp)
                        .pointerInput(kind) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = kind
                                    settlingKind = null
                                    dragOffset = 0f
                                    scope.launch { settle.snapTo(0f) }
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() }
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val currentOrder = order
                                val from = currentOrder.indexOf(kind)
                                if (from >= 0) {
                                    val shift = (dragOffset / rowHeightPx).roundToInt()
                                    if (shift != 0) {
                                        val to = (from + shift).coerceIn(0, currentOrder.lastIndex)
                                        if (to != from) {
                                            val mutable = currentOrder.toMutableList()
                                            mutable.removeAt(from)
                                            mutable.add(to, kind)
                                            order = mutable
                                            bridge.setTodayKinds(mutable.filter { it in state.todayKinds })
                                            dragOffset -= (to - from) * rowHeightPx
                                        }
                                    }
                                }
                            }
                        }
                        .tap {
                            val next = if (checked) state.todayKinds - kind else state.todayKinds + kind
                            if (next.isNotEmpty()) {
                                val base = if (order.isEmpty()) {
                                    next + Fixtures.allTodayKinds.filter { it !in next }
                                } else order
                                bridge.setTodayKinds(base.filter { it in next })
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt("≡", AS.caption().copy(fontSize = 14.sp, color = AS.Text3))
                    Spacer(Modifier.width(10.dp))
                    Txt(Fixtures.labelOf(kind), AS.body(), Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (checked) AS.Blue else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) Txt("✓", AS.body().copy(fontSize = 12.sp, color = Color.White))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt("长按拖动排序，点击勾选/取消；修改会立即生效，至少保留一张卡片。", AS.caption())
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ---------------------------------------------------------------------------
// Cache management
// ---------------------------------------------------------------------------

@Composable
fun CacheSheet(visible: Boolean, onDismiss: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            busy = true
            state.cacheInfo = runCatching { bridge.cacheInfo() }.getOrDefault(state.cacheInfo)
            busy = false
        }
    }

    OverlaySheetScaffold(visible, "缓存管理", onDismiss, scrollable = true) {
        Txt(formatBytes(state.cacheInfo.totalBytes), AS.largeTitle().copy(fontSize = 40.sp))
        Txt(
            if (state.cacheInfo.recentCount > 0) "已缓存内容 · 播放记录 ${state.cacheInfo.recentCount} 首"
            else "已缓存内容",
            AS.caption()
        )

        Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AS.CardElevated)
        ) {
            CacheRow("歌单 / 歌曲 / 歌词", formatBytes(state.cacheInfo.dataBytes))
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            CacheRow("封面图片", formatBytes(state.cacheInfo.imageBytes))
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            CacheRow("音频缓存", formatBytes(state.cacheInfo.audioBytes))
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
            CacheRow("缓存过的歌曲", "${state.cacheInfo.songCount} 首")
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetButton(
                if (busy) "处理中…" else "清理数据缓存",
                modifier = Modifier.weight(1f),
                onClick = {
                    if (busy) return@SheetButton
                    scope.launch {
                        busy = true
                        bridge.clearCache(CacheScope.Data)
                        busy = false
                        bridge.showToast("数据缓存已清理")
                    }
                }
            )
            SheetButton(
                if (busy) "处理中…" else "清理封面",
                modifier = Modifier.weight(1f),
                onClick = {
                    if (busy) return@SheetButton
                    scope.launch {
                        busy = true
                        bridge.clearCache(CacheScope.Images)
                        busy = false
                        bridge.showToast("封面缓存已清理")
                    }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        SheetButton(
            if (busy) "处理中…" else "清理音频缓存",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (busy) return@SheetButton
                scope.launch {
                    busy = true
                    bridge.clearCache(CacheScope.Audio)
                    busy = false
                    bridge.showToast("音频缓存已清理")
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AS.CardElevated)
                .tap {
                    if (busy) return@tap
                    scope.launch {
                        busy = true
                        bridge.clearCache(CacheScope.All)
                        busy = false
                        bridge.showToast("全部缓存已清空")
                    }
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) { Txt("清空全部缓存（含音频与播放记录）", AS.body().copy(fontSize = 15.sp, color = AS.Orange)) }

        Spacer(Modifier.height(14.dp))
        Txt(
            "缓存只存在本机。清理后歌单/歌词会重新联网加载，音频会重新下载。",
            AS.caption()
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun CacheRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Txt(label, AS.body())
        Txt(value, AS.subtitle())
    }
}

// ---------------------------------------------------------------------------
// Album blurb
// ---------------------------------------------------------------------------

@Composable
fun AlbumIntroSheet(
    visible: Boolean,
    title: String,
    text: String,
    heading: String = "专辑简介",
    onDismiss: () -> Unit
) {
    OverlaySheetScaffold(visible, heading, onDismiss) {
        Txt(title, AS.sectionTitle(), maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Txt("来源：网易云音乐", AS.caption())
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Txt(
                text.ifBlank { "这张专辑暂时没有简介。" },
                AS.body().copy(fontSize = 15.sp, lineHeight = 23.sp)
            )
            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ---------------------------------------------------------------------------
// Weekly listening goal
// ---------------------------------------------------------------------------

@Composable
fun GoalSheet(visible: Boolean, onDismiss: () -> Unit) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    var text by remember { mutableStateOf(state.weekGoalMinutes.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val presets = listOf(300, 600, 900, 1200)

    LaunchedEffect(visible) {
        if (visible) {
            text = state.weekGoalMinutes.toString()
            error = null
        }
    }

    OverlaySheetScaffold(visible, "每周听歌目标", onDismiss) {
        Txt("目标时长", AS.caption().copy(color = AS.Text3))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { v ->
                    text = v.filter { it.isDigit() }.take(5)
                    error = null
                },
                singleLine = true,
                textStyle = AS.largeTitle().copy(fontSize = 34.sp),
                cursorBrush = SolidColor(AS.Blue),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AS.CardElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Spacer(Modifier.width(10.dp))
            Txt("分钟", AS.subtitle())
        }
        error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Txt(message, AS.caption().copy(color = AS.Orange))
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { minutes ->
                ModeChip("${minutes / 60} 小时", text == minutes.toString(), Modifier.weight(1f)) {
                    text = minutes.toString()
                    error = null
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SheetButton("保存目标", onClick = {
            val value = text.toIntOrNull()
            if (value == null || value < 30) {
                error = "请输入不少于 30 分钟的数值"
            } else {
                bridge.setWeekGoal(value)
                bridge.showToast("每周目标已设为 $value 分钟")
                onDismiss()
            }
        })
        Spacer(Modifier.height(12.dp))
        Txt(
            "目标只影响「本周概览」的完成度显示，统计时长仍按本机播放记录计算。",
            AS.caption()
        )
    }
}

@Composable
internal fun SettingDivider() {
    Box(Modifier.padding(start = 2.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
}

/**
 * 设置项所在的卡片容器：圆角 14、卡片底色，横向留白由内容自己决定。
 * 统一在这里是为了改一次圆角／底色就能覆盖全部设置页。
 */
@Composable
internal fun SettingsCard(
    horizontal: Dp = 12.dp,
    vertical: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AS.Card)
            .padding(horizontal = horizontal, vertical = vertical),
        content = content
    )
}

@Composable
private fun rememberCardBackdrop(): Backdrop {
    val color = AS.Card
    return rememberCanvasBackdrop(remember(color) { { drawRect(color) } })
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val backdrop = rememberCardBackdrop()
    val isChecked = rememberUpdatedState(checked)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .tap { onChange(!checked) }
        ) {
            Txt(title, AS.body().copy(fontSize = 15.sp))
            Spacer(Modifier.height(3.dp))
            Txt(subtitle, AS.caption().copy(fontSize = 11.sp), maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        LiquidToggle(
            selected = { isChecked.value },
            onSelect = onChange,
            backdrop = backdrop
        )
    }
}

/**
 * 预设档位滑块。
 *
 * [value] / [display] 都是 lambda：设置页里这些值来自 [MusicState]，如果在调用处直接
 * 求值，拖动一次滑块会让整个三级页（几十个节点）每帧重组。传 lambda 后读取发生在
 * 本函数内部，重组范围收敛到这一行。
 */
@Composable
internal fun SettingStepSlider(
    label: String,
    value: () -> Float,
    steps: List<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit
) {
    val backdrop = rememberCardBackdrop()
    val lastIndex = (steps.size - 1).coerceAtLeast(1)
    fun presetIndex(v: Float): Int = steps.indices.minByOrNull { abs(steps[it] - v) } ?: 0
    // Same idea as the reference project's 纸色 slider: the thumb follows the finger
    // freely and only snaps to the nearest preset when the finger lifts.
    var position by remember { mutableFloatStateOf(presetIndex(value()).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, AS.body().copy(fontSize = 15.sp), Modifier.weight(1f))
            Txt(display(value()), AS.subtitle().copy(fontSize = 13.sp))
        }
        Spacer(Modifier.height(6.dp))
        LiquidSlider(
            value = { position },
            onValueChange = { position = it },
            valueRange = 0f..lastIndex.toFloat(),
            visibilityThreshold = 0.01f,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            onValueChangeFinished = {
                // Snap explicitly: relying on the value round-trip alone leaves the
                // thumb parked between two presets when the snap lands on the
                // currently applied value.
                val snapped = position.roundToInt().coerceIn(0, lastIndex)
                position = snapped.toFloat()
                onChange(steps[snapped])
            }
        )
    }
}

/** 连续滑块，[value] / [display] 为 lambda 的原因同 [SettingStepSlider]。 */
@Composable
internal fun SettingSlider(
    label: String,
    value: () -> Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit
) {
    val backdrop = rememberCardBackdrop()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, AS.body().copy(fontSize = 15.sp), Modifier.weight(1f))
            Txt(display(value()), AS.subtitle().copy(fontSize = 13.sp))
        }
        Spacer(Modifier.height(6.dp))
        LiquidSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            visibilityThreshold = ((range.endInclusive - range.start) / 1000f).coerceAtLeast(1e-4f),
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** System font families understood by the lyric page; the index is the stored value. */
val SystemFontLabels = listOf("默认", "细体", "紧缩", "中黑", "衬线", "等宽", "手写", "圆润")

/** Font picker for the immersive lyric view: system families + one imported file. */
@Composable
internal fun SettingFontFamilyRow(
    selected: Int,
    importedPath: String,
    available: Set<Int>,
    onSelect: (Int) -> Unit,
    onImport: () -> Unit,
    onClearImport: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Txt("系统字体", AS.body().copy(fontSize = 15.sp))
        Spacer(Modifier.height(10.dp))
        // Hide families this device does not really provide (they would fall back).
        SystemFontLabels.filterIndexed { index, _ -> index in available }.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { label ->
                    val index = SystemFontLabels.indexOf(label)
                    val active = importedPath.isBlank() && index == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) AS.Blue else AS.CardElevated)
                            .tap { onSelect(index) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Txt(
                            label,
                            AS.caption().copy(
                                fontSize = 12.sp,
                                color = if (active) Color.White else AS.Text2
                            )
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AS.CardElevated)
                    .tap(onImport)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Txt(
                    if (importedPath.isBlank()) "导入字体文件…"
                    else "已导入：" + importedPath.substringAfterLast('/'),
                    AS.caption().copy(
                        fontSize = 12.sp,
                        color = if (importedPath.isBlank()) AS.Text2 else AS.Blue
                    )
                )
            }
            if (importedPath.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AS.CardElevated)
                        .tap(onClearImport)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Txt("移除", AS.caption().copy(fontSize = 12.sp, color = AS.Text2))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Txt(
            "导入的字体优先于上面的系统字体，只支持 ttf / otf。" +
                "Android 只内置中文黑体与宋体，所以系统字体里只有「衬线」会改变汉字字形，" +
                "其余几项只影响英文和数字。",
            AS.caption()
        )
    }
}

/** Single-line text input row, used for the TTML mirror template. */
@Composable
internal fun SettingTextFieldRow(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Txt(label, AS.body().copy(fontSize = 15.sp))
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = AS.body().copy(fontSize = 12.sp),
            cursorBrush = SolidColor(AS.Blue),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AS.CardElevated)
                .padding(10.dp)
        )
        Spacer(Modifier.height(6.dp))
        Txt(hint, AS.caption())
    }
}

@Composable
internal fun CapsuleSideSwitch(onLeft: Boolean, onChange: (Boolean) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AS.CardElevated)
            .pointerInput(onLeft) {
                detectTapGestures { pos -> onChange(pos.x < size.width / 2f) }
            }
            .pointerInput(onLeft) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragCancel = { dragged = 0f },
                    onDragEnd = { dragged = 0f }
                ) { _, amount ->
                    dragged += amount
                    val threshold = size.width * .12f
                    if (dragged < -threshold && !onLeft) {
                        onChange(true)
                        dragged = 0f
                    } else if (dragged > threshold && onLeft) {
                        onChange(false)
                        dragged = 0f
                    }
                }
            }
    ) {
        val thumbWidth = maxWidth / 2
        val thumbX by animateDpAsState(
            targetValue = if (onLeft) 3.dp else maxWidth - thumbWidth - 3.dp,
            animationSpec = spring(dampingRatio = .76f, stiffness = 320f),
            label = "capsuleThumb"
        )
        Box(
            Modifier
                .offset(x = thumbX, y = 3.dp)
                .width(thumbWidth)
                .height(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(AS.Blue)
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            SideLabel("左侧", onLeft, Modifier.weight(1f))
            SideLabel("右侧", !onLeft, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun DetailTopStyleSwitch(
    selected: DetailTopStyle,
    onChange: (DetailTopStyle) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AS.CardElevated)
            .padding(3.dp)
    ) {
        DetailTopStyle.entries.forEach { style ->
            val isSelected = style == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) AS.Blue else Color.Transparent)
                    .tap { onChange(style) },
                contentAlignment = Alignment.Center
            ) {
                Txt(
                    style.label,
                    AS.body().copy(
                        fontSize = 14.sp,
                        color = if (isSelected) Color.White else AS.Text2
                    )
                )
            }
        }
    }
}

@Composable
private fun SideLabel(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Txt(
            text,
            AS.body().copy(
                fontSize = 15.sp,
                color = if (selected) Color.White else AS.Text2
            )
        )
    }
}
