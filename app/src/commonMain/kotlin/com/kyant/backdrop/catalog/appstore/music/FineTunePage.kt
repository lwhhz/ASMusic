package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.CircleButton
import com.kyant.backdrop.catalog.appstore.CircleKind
import com.kyant.backdrop.catalog.appstore.Txt
import kotlin.math.roundToInt

/** Audio cache presets: 512 MB, then 5 GB steps up to 20 GB. */
val CacheLimitSteps = listOf(512f, 5120f, 10240f, 15360f, 20480f)

/** Which group of sliders a third-level page shows. */
enum class FineTuneGroup(val title: String, val note: String) {
    Capsule("播放胶囊", "只影响歌曲详情页展开后的全宽控制栏，胶囊形态始终保持。"),
    Haze("Haze 效果参数", "覆盖高度是模糊区域总高；过渡是顶部完全不透明的高度，之后才逐渐变透明；模糊强度决定最顶端的峰值模糊。"),
    Interface("界面大小", "同时影响间距与控件尺寸。歌词卡片与沉浸式歌词不受影响。"),
    Lyrics("卡片歌词", "逐字方式、字号与光晕，只作用于歌曲详情页的歌词卡。"),
    Source("歌词源", "逐字歌词的数据来源。TTML 来自 AMLL 公共库；第三方源默认关闭，开启后歌词数据来自对应平台。"),
    Fluid("沉浸式歌词背景", "长按歌词卡进入沉浸式歌词后生效；背景由当前歌曲封面实时生成。"),
    Immersive(
        "沉浸式歌词文字",
        "只作用于 AMLL 沉浸式页面。滑走歌词后会在设定时间后自动回到正在播放的那一行；点击任意一行可跳到该句。"
    ),
}

/**
 * Third-level page showing exactly one group of sliders. Reaching sliders requires
 * an explicit navigation step from the owning Customize module, so scrolling the
 * Customize page can never nudge a value by accident.
 */
@Composable
fun FineTunePage(
    group: FineTuneGroup,
    scrollState: ScrollState,
    onBack: () -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state

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
        Txt(group.title, AS.largeTitle().copy(fontSize = 30.sp), Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(18.dp))

        Column(Modifier.padding(horizontal = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AS.Card)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                when (group) {
                    FineTuneGroup.Capsule -> {
                        SettingSlider(
                            "详情页控制栏圆角",
                            state.capsuleCorner,
                            0f..1f,
                            if (state.capsuleCorner >= .995f) "胶囊"
                            else "${(state.capsuleCorner * 52f).toInt()} dp"
                        ) { bridge.setCapsuleCorner(it) }
                    }
                    FineTuneGroup.Haze -> {
                        SettingSlider(
                            "模糊覆盖高度",
                            state.hazeHeightDp,
                            64f..420f,
                            "${state.hazeHeightDp.toInt()} dp"
                        ) { bridge.setHazeHeightDp(it) }
                        SettingDivider()
                        SettingSlider(
                            "清晰到模糊的过渡",
                            state.hazeOpaqueDp,
                            64f..state.hazeHeightDp.coerceIn(64f, 420f),
                            "${state.hazeOpaqueDp.toInt()} dp"
                        ) { bridge.setHazeOpaqueDp(it) }
                        SettingDivider()
                        SettingSlider(
                            "最终模糊强度",
                            state.hazeBlurDp,
                            0f..140f,
                            "${state.hazeBlurDp.toInt()} dp"
                        ) { bridge.setHazeBlurDp(it) }
                        SettingDivider()
                        SettingSlider(
                            "暗色渐变强度",
                            state.hazeScrim,
                            0f..1f,
                            "${(state.hazeScrim * 100).toInt()}%"
                        ) { bridge.setHazeScrim(it) }
                    }
                    FineTuneGroup.Interface -> {
                        SettingStepSlider(
                            "界面大小",
                            state.uiScale,
                            UiScaleSteps,
                            "${(state.uiScale * 100).toInt()}%"
                        ) { bridge.setUiScale(it) }
                    }
                    FineTuneGroup.Lyrics -> {
                        SettingSwitchRow(
                            "估算逐字",
                            "歌词源没有词级时间轴时，按行时长均分给每个字，仍能呈现扫过效果",
                            state.lyricSynthWords
                        ) { bridge.setLyricSynthWords(it) }
                        SettingDivider()
                        SettingSwitchRow(
                            "已唱部分保持高亮",
                            "关闭后高亮只在当前字附近移动，唱过的部分会重新变暗",
                            state.lyricKeepSung
                        ) { bridge.setLyricKeepSung(it) }
                        SettingDivider()
                        SettingSlider(
                            "模糊 / 光晕强度",
                            state.lyricGlow,
                            0f..1f,
                            "${(state.lyricGlow * 100).toInt()}%"
                        ) { bridge.setLyricGlow(it) }
                        SettingDivider()
                        SettingSlider(
                            "歌词字号",
                            state.lyricFont,
                            16f..34f,
                            "${state.lyricFont.toInt()} sp"
                        ) { bridge.setLyricFont(it) }
                        SettingDivider()
                        SettingSlider(
                            "翻译字号",
                            state.translationFont,
                            10f..24f,
                            "${state.translationFont.toInt()} sp"
                        ) { bridge.setTranslationFont(it) }
                    }
                    FineTuneGroup.Fluid -> {
                        SettingSlider(
                            "模糊强度",
                            state.fluidBlurDp,
                            0f..64f,
                            "${state.fluidBlurDp.toInt()} dp"
                        ) { bridge.setFluidBlurDp(it) }
                        SettingDivider()
                        SettingSlider(
                            "流体形变",
                            state.fluidWarp,
                            0f..3f,
                            "${(state.fluidWarp * 100).toInt()}%"
                        ) { bridge.setFluidWarp(it) }
                        SettingDivider()
                        SettingSlider(
                            "流动速度",
                            state.fluidSpeed,
                            0.1f..3f,
                            "${(state.fluidSpeed * 100).toInt()}%"
                        ) { bridge.setFluidSpeed(it) }
                        SettingDivider()
                        SettingSlider(
                            "暗角强度",
                            state.fluidDark,
                            0f..1f,
                            "${(state.fluidDark * 100).toInt()}%"
                        ) { bridge.setFluidDark(it) }
                    }
                    FineTuneGroup.Immersive -> {
                        val importFont = rememberFontImport { bridge.setAmllFontFile(it) }
                        SettingSwitchRow(
                            "后台预加载",
                            "播放时保持沉浸式歌词引擎常驻，进入更快；关闭可省 40–60 MB 内存",
                            state.keepAmllWarm
                        ) { bridge.setKeepAmllWarm(it) }
                        SettingDivider()
                        SettingSlider(
                            "歌词字号",
                            state.amllFontSize,
                            20f..64f,
                            "${state.amllFontSize.toInt()} px"
                        ) { bridge.setAmllFontSize(it) }
                        SettingDivider()
                        SettingSlider(
                            "字重",
                            state.amllFontWeight,
                            300f..900f,
                            "${state.amllFontWeight.toInt()}"
                        ) { bridge.setAmllFontWeight(it) }
                        SettingDivider()
                        val availableFamilies = rememberAvailableFontFamilies()
                        SettingFontFamilyRow(
                            selected = state.amllFontFamily,
                            importedPath = state.amllFontFile,
                            available = availableFamilies,
                            onSelect = { bridge.setAmllFontFamily(it) },
                            onImport = { importFont() },
                            onClearImport = { bridge.setAmllFontFile("") }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            "遮挡不雅用词",
                            "开启后歌词里的脏话会按 AMLL 默认规则打码",
                            state.amllMaskProfanity
                        ) { bridge.setAmllMaskProfanity(it) }
                        SettingDivider()
                        SettingSlider(
                            "滑走后自动归位",
                            state.amllResumeDelay,
                            0f..10f,
                            if (state.amllResumeDelay < .05f) "立即"
                            else "${state.amllResumeDelay.toInt()} s"
                        ) { bridge.setAmllResumeDelay(it) }
                    }
                    FineTuneGroup.Source -> {
                        SettingSwitchRow(
                            "TTML 逐字歌词",
                            "关闭后只用网易云歌词；开启时优先使用 TTML（命中即逐字）",
                            state.amllTtmlEnabled
                        ) { bridge.setAmllTtmlEnabled(it) }
                        SettingDivider()
                        SettingTextFieldRow(
                            "TTML 源",
                            state.amllTtmlSource,
                            "%p = 目录（ncm-lyrics / raw-lyrics），%s = 文件名。留空恢复默认。",
                        ) { bridge.setAmllTtmlSource(it) }
                        SettingDivider()
                        SettingSwitchRow(
                            "酷狗 KRC 逐字（实验）",
                            "网易云没有词级时间轴时，按歌名/歌手/时长向酷狗请求逐字歌词；默认关闭",
                            state.kugouKrcEnabled
                        ) { bridge.setKugouKrcEnabled(it) }
                        Spacer(Modifier.height(8.dp))
                        Txt(
                            "第三方源说明：开启后会向酷狗音乐的公开歌词接口发起请求，结果仅缓存在本机。" +
                                "歌词数据版权归酷狗及权利人所有，本项目不分发该数据；如不同意请保持关闭。",
                            AS.caption()
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Txt(group.note, AS.caption())
            Spacer(Modifier.height(40.dp))
        }
    }
}
