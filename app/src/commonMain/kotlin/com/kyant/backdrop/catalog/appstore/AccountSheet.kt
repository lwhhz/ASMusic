package com.kyant.backdrop.catalog.appstore

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.music.MusicServices
import com.kyant.backdrop.catalog.appstore.music.SelfCheckItem
import kotlinx.coroutines.launch
import com.kyant.backdrop.catalog.appstore.music.formatBytes

/** Account & settings sheet. Everything App-Store-specific has been removed. */
@Composable
fun SettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNeteaseLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onScriptImport: () -> Unit = {},
    onCache: () -> Unit = {},
    onCustomize: () -> Unit = {},
    onQuality: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state = MusicServices.state
    var logoutConfirm by remember { mutableStateOf(false) }
    var checkOpen by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkItems by remember { mutableStateOf<List<SelfCheckItem>>(emptyList()) }
    val checkScope = rememberCoroutineScope()
    fun runCheck(repair: Boolean) {
        checkOpen = true
        checking = true
        checkScope.launch {
            checkItems = runCatching {
                if (repair) MusicServices.bridge.selfRepair()
                else MusicServices.bridge.selfCheck()
            }.getOrElse {
                listOf(SelfCheckItem("自检失败", false, it.message ?: "未知错误"))
            }
            checking = false
        }
    }
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = .92f, stiffness = 380f)
    )
    LaunchedEffect(visible) {
        if (visible) {
            MusicServices.bridge.refreshBackgroundPermission()
            state.cacheInfo = runCatching { MusicServices.bridge.cacheInfo() }
                .getOrDefault(state.cacheInfo)
        }
    }
    val pulse = rememberInfiniteTransition(label = "background-permission").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "background-pulse"
    )
    if (progress < 0.001f) return

    Box(modifier.fillMaxSize()) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Title row
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                Txt("设置", AS.rowTitle().copy(fontSize = 17.sp))
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AS.CardElevated)
                        .tap(onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(16.dp)) {
                        val c = Color.White
                        val sw = 2.dp.toPx()
                        drawLine(c, Offset(4.dp.toPx(), 4.dp.toPx()), Offset(12.dp.toPx(), 12.dp.toPx()), sw, StrokeCap.Round)
                        drawLine(c, Offset(12.dp.toPx(), 4.dp.toPx()), Offset(4.dp.toPx(), 12.dp.toPx()), sw, StrokeCap.Round)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Profile card
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AS.CardElevated)
                    .tap(onNeteaseLogin)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarArt(size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(
                        state.nickname ?: if (state.loggedIn) "网易云用户" else "未链接账号",
                        AS.rowTitle().copy(fontSize = 18.sp),
                        maxLines = 1
                    )
                    Txt(
                        if (state.loggedIn) "已链接网易云音乐" else "点击扫码登录网易云账号",
                        AS.subtitle()
                    )
                }
                Chevron()
            }

            Spacer(Modifier.height(22.dp))
            Txt("账号", AS.caption().copy(color = AS.Text3), Modifier.padding(start = 4.dp, bottom = 8.dp))
            SheetGroup {
                SheetValueRow(
                    "音质",
                    "WiFi ${com.kyant.backdrop.catalog.appstore.music.AudioQuality.from(state.wifiQuality).label} · 移动 ${com.kyant.backdrop.catalog.appstore.music.AudioQuality.from(state.mobileQuality).label}",
                    onQuality
                )
                SheetDivider()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (state.backgroundAllowed) Color.Transparent
                            else AS.Blue.copy(alpha = 0.08f + 0.12f * pulse.value)
                        )
                ) {
                    SheetValueRow(
                        "后台常驻权限",
                        if (state.backgroundAllowed) "完全允许" else "去设置",
                        onClick = { MusicServices.bridge.openBackgroundSettings() }
                    )
                }
                SheetDivider()
                SheetLinkRow(if (state.loggedIn) "重新扫码登录" else "链接网易云账号", onNeteaseLogin)
                if (state.loggedIn) {
                    SheetDivider()
                    SheetLinkRow(
                        "退出登录",
                        onClick = { logoutConfirm = true },
                        color = Color(0xFFFF453A)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Txt("播放与外观", AS.caption().copy(color = AS.Text3), Modifier.padding(start = 4.dp, bottom = 8.dp))
            SheetGroup {
                SheetLinkRow("JS 音乐源脚本导入", onScriptImport)
                SheetDivider()
                SheetLinkRow("播放、外观与歌词", onCustomize, color = AS.Blue)
            }
            Spacer(Modifier.height(10.dp))
            Txt(
                "播放胶囊、顶栏风格、界面大小、歌词与歌词源都在这里。" +
                    "后台常驻权限用于保持 JS 音源解析服务；系统媒体控制与流体云由系统自动接入。",
                AS.caption()
            )

            Spacer(Modifier.height(22.dp))
            Txt("维护", AS.caption().copy(color = AS.Text3), Modifier.padding(start = 4.dp, bottom = 8.dp))
            SheetGroup {
                SheetLinkRow("自检与修复", { runCheck(false) })
                SheetDivider()
                SheetValueRow("缓存管理", formatBytes(state.cacheInfo.totalBytes), onCache)
                SheetDivider()
                SheetValueRow("版本", "1.3.52", onClick = null)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (logoutConfirm) {
        LogoutConfirmDialog(
            onDismiss = { logoutConfirm = false },
            onConfirm = {
                logoutConfirm = false
                onLogout()
            }
        )
    }

    if (checkOpen) {
        SelfCheckDialog(
            items = checkItems,
            checking = checking,
            onRecheck = { runCheck(false) },
            onRepair = { runCheck(true) },
            onDismiss = { checkOpen = false }
        )
    }
}

@Composable
private fun LogoutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(290.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AS.CardElevated)
        ) {
            Txt(
                "退出登录",
                AS.rowTitle().copy(fontSize = 17.sp, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp)
            )
            Txt(
                "退出后需要重新扫码登录。本机播放统计、缓存和音源脚本会保留。",
                AS.caption().copy(textAlign = TextAlign.Center),
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 20.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(AS.Separator))
            Row(Modifier.fillMaxWidth().height(50.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().tap(onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Txt("取消", AS.body().copy(color = AS.Blue))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(AS.Separator))
                Box(
                    Modifier.weight(1f).fillMaxHeight().tap(onConfirm),
                    contentAlignment = Alignment.Center
                ) {
                    Txt("退出登录", AS.body().copy(color = Color(0xFFFF453A), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                }
            }
        }
    }
}


@Composable
private fun SelfCheckDialog(
    items: List<SelfCheckItem>,
    checking: Boolean,
    onRecheck: () -> Unit,
    onRepair: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(320.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AS.CardElevated)
        ) {
            Txt(
                "自检与修复",
                AS.rowTitle().copy(fontSize = 17.sp, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)
            )
            Txt(
                "检查 JS 音源、Node 解析服务、权限与媒体控制。",
                AS.caption().copy(textAlign = TextAlign.Center),
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp)
            )
            if (checking) {
                Txt("正在检测…", AS.subtitle().copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth().padding(24.dp))
            } else {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (item.ok) Color(0xFF34C759) else Color(0xFFFF453A))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(item.label, AS.body().copy(fontSize = 14.sp))
                                Txt(item.detail, AS.caption().copy(fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AS.Separator))
            Row(Modifier.fillMaxWidth().height(50.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().tap(onRecheck),
                    contentAlignment = Alignment.Center
                ) {
                    Txt("重新检测", AS.body().copy(color = AS.Blue))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(AS.Separator))
                Box(
                    Modifier.weight(1f).fillMaxHeight().tap(onRepair),
                    contentAlignment = Alignment.Center
                ) {
                    Txt("一键修复", AS.body().copy(color = Color(0xFF34C759), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AS.Separator))
            Box(
                Modifier.fillMaxWidth().height(46.dp).tap(onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Txt("关闭", AS.subtitle())
            }
        }
    }
}

@Composable
private fun SheetGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AS.CardElevated)
    ) { content() }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(AS.Separator))
}

@Composable
private fun SheetLinkRow(label: String, onClick: () -> Unit = {}, color: Color? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .tap(onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Txt(label, if (color != null) AS.body().copy(color = color) else AS.body())
        Chevron()
    }
}

@Composable
private fun SheetValueRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Txt(label, AS.body())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(value, AS.subtitle())
            if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Chevron()
            }
        }
    }
}

@Composable
private fun Chevron() {
    Canvas(Modifier.size(12.dp)) {
        val c = AS.Text3
        val sw = 2.dp.toPx()
        drawLine(c, Offset(3.dp.toPx(), 1.dp.toPx()), Offset(9.dp.toPx(), 6.dp.toPx()), sw, StrokeCap.Round)
        drawLine(c, Offset(9.dp.toPx(), 6.dp.toPx()), Offset(3.dp.toPx(), 11.dp.toPx()), sw, StrokeCap.Round)
    }
}
