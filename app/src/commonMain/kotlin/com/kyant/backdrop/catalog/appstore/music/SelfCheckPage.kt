package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.CircleButton
import com.kyant.backdrop.catalog.appstore.CircleKind
import com.kyant.backdrop.catalog.appstore.Platform
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap
import kotlinx.coroutines.launch

private val PassColor = Color(0xFF34C759)
private val WarnColor = Color(0xFFFF9F0A)
private val FailColor = Color(0xFFFF453A)

/**
 * 软件全面自检。
 *
 * 与设置页里那个「自检与修复」弹窗的区别：那个是几毫秒的**可用性**检查（音源能不能用），
 * 一点就出结果；这一页是**诊断**——会把环境、网络、接口、账号、播放内核、Node 运行时、
 * 缓存与存储、权限逐项跑一遍，会真的发网络请求、真的启动 Node，耗时几秒到十几秒，
 * 所以只在用户点「开始自检」时执行，进页面不自动跑（避免每次进来都打一次接口）。
 *
 * 结果可以复制成纯文本，方便贴到 issue 里定位问题。
 */
@Composable
fun FullSelfCheckPage(
    scrollState: ScrollState,
    onBack: () -> Unit
) {
    val bridge = MusicServices.bridge
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<SelfCheckItem>>(emptyList()) }
    var elapsedMs by remember { mutableStateOf(0L) }

    fun run() {
        if (running) return
        running = true
        val startedAt = kotlin.time.TimeSource.Monotonic.markNow()
        scope.launch {
            items = runCatching { bridge.fullSelfCheck() }.getOrElse {
                listOf(
                    SelfCheckItem(
                        "自检中断",
                        false,
                        it.message ?: "未知错误",
                        SelfCheckLevel.Fail,
                        "运行环境"
                    )
                )
            }
            elapsedMs = startedAt.elapsedNow().inWholeMilliseconds
            running = false
        }
    }

    val failCount = items.count { it.level == SelfCheckLevel.Fail }
    val warnCount = items.count { it.level == SelfCheckLevel.Warn }
    val passCount = items.count { it.level == SelfCheckLevel.Pass }
    val groups = items.groupBy { it.group }

    Column(
        Modifier
            .fillMaxSize()
            .background(AS.Bg)
            .verticalScroll(scrollState)
            .padding(bottom = 180.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            CircleButton(CircleKind.Back, onBack)
        }
        Txt("软件全面自检", AS.largeTitle().copy(fontSize = 30.sp), Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        Txt(
            "逐项检查运行环境、网络与接口、播放内核、Node 运行时、音源脚本、缓存与存储、权限。"
                + "会发真实的网络请求，需要几秒到十几秒。",
            AS.caption(),
            Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(18.dp))

        // ---- 启动 / 结果概览 ----
        Column(Modifier.padding(horizontal = 16.dp)) {
            SettingsCard(horizontal = 14.dp, vertical = 14.dp) {
                when {
                    running -> {
                        Txt("正在自检…", AS.rowTitle().copy(fontSize = 17.sp))
                        Spacer(Modifier.height(6.dp))
                        Txt("正在逐项检查，请保持网络连接，不要退出应用。", AS.caption())
                    }
                    items.isEmpty() -> {
                        Txt("尚未自检", AS.rowTitle().copy(fontSize = 17.sp))
                        Spacer(Modifier.height(6.dp))
                        Txt(
                            "点击下方按钮开始。播放或解析莫名失败时先跑一次，"
                                + "结果能直接看出是哪一层出了问题。",
                            AS.caption()
                        )
                    }
                    else -> {
                        Txt(
                            if (failCount > 0) "发现 $failCount 项异常"
                            else if (warnCount > 0) "全部通过，$warnCount 项提示"
                            else "全部通过",
                            AS.rowTitle().copy(fontSize = 17.sp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Txt(
                            "共 ${items.size} 项 · 通过 $passCount · 提示 $warnCount · 失败 $failCount"
                                + " · 耗时 ${elapsedMs / 1000}.${(elapsedMs % 1000) / 100}s",
                            AS.caption()
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (running) AS.CardElevated else AS.Blue)
                        .tap { run() },
                    contentAlignment = Alignment.Center
                ) {
                    Txt(
                        when {
                            running -> "自检进行中…"
                            items.isEmpty() -> "开始自检"
                            else -> "重新自检"
                        },
                        AS.body().copy(
                            fontSize = 15.sp,
                            color = if (running) AS.Text3 else Color.White,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                if (items.isNotEmpty() && !running) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AS.CardElevated)
                            .tap {
                                Platform.bridge.copy(items.toPlainText(elapsedMs))
                                bridge.showToast("诊断结果已复制")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Txt("复制诊断结果", AS.subtitle().copy(color = AS.Blue))
                    }
                }
            }
        }

        // ---- 逐项结果 ----
        groups.forEach { (group, rows) ->
            Spacer(Modifier.height(22.dp))
            Txt(
                group,
                AS.caption().copy(color = AS.Text3),
                Modifier.padding(start = 20.dp, bottom = 8.dp)
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingsCard(horizontal = 12.dp, vertical = 2.dp) {
                    rows.forEachIndexed { index, item ->
                        if (index > 0) SettingDivider()
                        SelfCheckRow(item)
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Txt(
                "绿点=正常，橙点=可用但有隐患，红点=功能不可用。诊断信息只包含版本、路径、耗时与统计数字，不含账号密码。",
                AS.caption().copy(color = AS.Text3),
                Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun SelfCheckRow(item: SelfCheckItem) {
    val color = when (item.level) {
        SelfCheckLevel.Pass -> PassColor
        SelfCheckLevel.Warn -> WarnColor
        SelfCheckLevel.Fail -> FailColor
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(item.label, AS.body().copy(fontSize = 14.sp), Modifier.weight(1f, fill = false))
                if (item.level != SelfCheckLevel.Pass) {
                    Spacer(Modifier.width(8.dp))
                    Txt(
                        if (item.level == SelfCheckLevel.Fail) "失败" else "提示",
                        AS.caption().copy(fontSize = 10.sp, color = color)
                    )
                }
            }
            if (item.detail.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Txt(item.detail, AS.caption().copy(fontSize = 11.sp, color = AS.Text2))
            }
        }
        Spacer(Modifier.width(6.dp))
        Spacer(Modifier.size(1.dp))
    }
}

/** 纯文本版结果，用于复制到剪贴板后贴进 issue。 */
private fun List<SelfCheckItem>.toPlainText(elapsedMs: Long): String {
    val rowsByGroup = groupBy { it.group }
    return buildString {
        appendLine("ASMusic 软件自检")
        appendLine("共 ${size} 项 · 耗时 ${elapsedMs}ms")
        appendLine()
        rowsByGroup.forEach { (group, rows) ->
            appendLine("[$group]")
            rows.forEach { item ->
                val mark = when (item.level) {
                    SelfCheckLevel.Pass -> "OK  "
                    SelfCheckLevel.Warn -> "WARN"
                    SelfCheckLevel.Fail -> "FAIL"
                }
                appendLine("  $mark  ${item.label}：${item.detail}")
            }
            appendLine()
        }
    }
}
