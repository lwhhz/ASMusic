package com.kyant.backdrop.catalog.appstore.music

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.CircleButton
import com.kyant.backdrop.catalog.appstore.CircleKind
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap
import kotlin.math.roundToInt

/** Audio cache presets: 512 MB, then 5 GB steps up to 20 GB. */
val CacheLimitSteps = listOf(512f, 5120f, 10240f, 15360f, 20480f)

/**
 * Which group of controls a third-level page shows.
 *
 * 分组按**用户意图**而不是实现模块划分：同一个功能的所有参数都在同一页，
 * 因此不再存在「卡片歌词的字号」和「沉浸式歌词的字号」分处两地、也看不到
 * 「自定义 → 歌词 → 歌词显示参数」这种四层入口。
 */
enum class FineTuneGroup(val title: String, val note: String) {
    Capsule(
        "播放胶囊",
        "停靠位置决定胶囊浮在底栏左侧还是右侧；控制栏圆角只影响歌曲详情页展开后的全宽形态，胶囊本身始终是完整的胶囊形。"
    ),
    Appearance(
        "顶栏与详情页",
        "顶栏是歌曲详情页下滑后浮在最上面的那一层。Haze 直接对页面内容做实时模糊，纯色则用一块纯色顶栏；界面大小同时影响间距与控件尺寸。"
    ),
    Lyrics(
        "卡片歌词",
        "只作用于歌曲详情页的歌词卡。逐字歌词使用 Compose 原生渲染，关闭后歌词卡使用整行静态文字。"
    ),
    Immersive(
        "沉浸式歌词",
        "只作用于 AMLL 沉浸式页面。滑走歌词后会在设定时间后自动回到正在播放的那一行；点击任意一行可跳到该句。"
    ),
    Fluid(
        "沉浸式歌词背景",
        "进入沉浸式歌词后生效，背景由当前歌曲封面实时生成，不是视频。"
    ),
    Source(
        "歌词源",
        "逐字歌词的数据来源。TTML 来自 AMLL 公共库；第三方源默认关闭，开启后歌词数据来自对应平台。"
    ),
    Cache(
        "缓存与预加载",
        "音频缓存上限、下一首预缓存，以及「缓存好再播放」。清理缓存请回到设置里的「缓存管理」。"
    ),
    Poster(
        "分享海报",
        "歌词海报的背景、比例、行数与水印；修改后下一次分享立即生效。"
    ),
}

/**
 * Third-level page showing exactly one group of controls. Reaching a slider always
 * requires an explicit navigation step from Settings, so scrolling the list can never
 * nudge a value by accident.
 */
@Composable
fun FineTunePage(
    group: FineTuneGroup,
    scrollState: ScrollState,
    onBack: () -> Unit
) {
    val bridge = MusicServices.bridge
    val state = MusicServices.state
    // 每个 group 进来都从顶部开始，避免沿用上一个三级页的滚动位置。
    LaunchedEffect(group) {
        runCatching { scrollState.scrollTo(0) }
    }

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
            SettingsCard {
                when (group) {
                    FineTuneGroup.Capsule -> {
                        CapsuleSideSwitch(state.capsuleOnLeft) { bridge.setCapsuleOnLeft(it) }
                        Spacer(Modifier.height(10.dp))
                        Txt(
                            if (state.capsuleOnLeft) "胶囊停靠在底栏上方左侧，也可以在首页直接左右拖动。"
                            else "胶囊停靠在底栏上方右侧，也可以在首页直接左右拖动。",
                            AS.caption()
                        )
                        Spacer(Modifier.height(14.dp))
                        SettingDivider()
                        // Live preview of the song-detail control bar shape.
                        val previewCorner = state.capsuleCorner
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .height(64.dp)
                                .clip(
                                    if (previewCorner >= .995f) CircleShape
                                    else RoundedCornerShape((previewCorner * 32f).dp)
                                )
                                .background(AS.CardElevated)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Decorative transport bar: progress line + prev / play / next dots.
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(AS.Text3))
                                Spacer(Modifier.width(18.dp))
                                Box(Modifier.size(16.dp).clip(CircleShape).background(AS.Blue))
                                Spacer(Modifier.width(18.dp))
                                Box(Modifier.size(10.dp).clip(CircleShape).background(AS.Text3))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SettingSlider(
                            "详情页控制栏圆角",
                            { state.capsuleCorner },
                            0f..1f,
                            { if (it >= .995f) "胶囊" else "${(it * 52f).toInt()} dp" }
                        ) { bridge.setCapsuleCorner(it) }
                    }

                    FineTuneGroup.Appearance -> {
                        Txt("顶栏风格", AS.body().copy(fontSize = 15.sp))
                        Spacer(Modifier.height(8.dp))
                        DetailTopStyleSwitch(state.detailTopStyle) { bridge.setDetailTopStyle(it) }
                        Spacer(Modifier.height(8.dp))
                        Txt(
                            if (state.detailTopStyle == DetailTopStyle.Solid) {
                                "纯色：下滑后使用 64dp 的纯色顶栏。下面的模糊参数在切到 Haze 后生效。"
                            } else {
                                "Haze 渐进模糊：直接对页面内容做实时模糊，顶部最强，向下逐渐完全透明。"
                            },
                            AS.caption()
                        )
                        Spacer(Modifier.height(14.dp))
                        SettingDivider()
                        Spacer(Modifier.height(12.dp))
                        // 纯色风格下模糊参数不生效，但保留可调：切回 Haze 时立即沿用。
                        Column(
                            Modifier.alpha(if (state.detailTopStyle == DetailTopStyle.Solid) .45f else 1f)
                        ) {
                            Txt("Haze 预设", AS.body().copy(fontSize = 15.sp))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val matched = matchedHazePreset(
                                    state.hazeHeightDp,
                                    state.hazeOpaqueDp,
                                    state.hazeBlurDp
                                )
                                HazePresets.forEach { preset ->
                                    val selected = preset.label == matched
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) AS.Blue else AS.CardElevated)
                                            .tap {
                                                bridge.applyHazePreset(
                                                    preset.height,
                                                    preset.opaque,
                                                    preset.blur
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Txt(
                                            preset.label,
                                            AS.body().copy(
                                                fontSize = 14.sp,
                                                color = if (selected) Color.White else AS.Text2
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Txt("选预设后仍可继续手动微调。", AS.caption())
                            Spacer(Modifier.height(10.dp))
                            SettingDivider()
                            SettingSlider(
                                "模糊覆盖高度",
                                { state.hazeHeightDp },
                                64f..420f,
                                { "${it.toInt()} dp" }
                            ) { bridge.setHazeHeightDp(it) }
                            SettingDivider()
                            SettingSlider(
                                "清晰到模糊的过渡",
                                { state.hazeOpaqueDp },
                                64f..state.hazeHeightDp.coerceIn(64f, 420f),
                                { "${it.toInt()} dp" }
                            ) { bridge.setHazeOpaqueDp(it) }
                            SettingDivider()
                            SettingSlider(
                                "最终模糊强度",
                                { state.hazeBlurDp },
                                0f..140f,
                                { "${it.toInt()} dp" }
                            ) { bridge.setHazeBlurDp(it) }
                            SettingDivider()
                            SettingSlider(
                                "暗色渐变强度",
                                { state.hazeScrim },
                                0f..1f,
                                { "${(it * 100).toInt()}%" }
                            ) { bridge.setHazeScrim(it) }
                        }
                        Spacer(Modifier.height(14.dp))
                        SettingDivider()
                        SettingSwitchRow(
                            "歌曲详情页隐藏底栏",
                            "进入歌曲详情页时隐藏底部标签栏，返回后自动恢复",
                            state.detailHideTabs
                        ) { bridge.setDetailHideTabs(it) }
                        SettingDivider()
                        SettingStepSlider(
                            "界面大小",
                            { state.uiScale },
                            UiScaleSteps,
                            { "${(it * 100).toInt()}%" }
                        ) { bridge.setUiScale(it) }
                        Spacer(Modifier.height(6.dp))
                        Txt(
                            "界面大小分八级预设，同时影响间距与控件尺寸。歌词卡片与沉浸式歌词不受影响，它们使用专属的字号设置。",
                            AS.caption()
                        )
                    }

                    FineTuneGroup.Lyrics -> {
                        SettingSwitchRow(
                            "逐字歌词（卡拉OK）",
                            "有网易云 yrc 时按真实字时间高亮，没有则按整行时长均分",
                            state.wordByWord
                        ) { bridge.setWordByWord(it) }
                        SettingDivider()
                        SettingSwitchRow(
                            "显示翻译",
                            "歌词带中文翻译时显示在原文下方",
                            state.showTranslation
                        ) { bridge.setShowTranslation(it) }
                        SettingDivider()
                        SettingSwitchRow(
                            "卡片流光",
                            "让歌词卡片的取色背景缓慢流动",
                            state.lyricFlow
                        ) { bridge.setLyricFlow(it) }
                        SettingDivider()
                        SettingSwitchRow(
                            "估算逐字",
                            "没有词级时间轴时，按行时长均分到每个字",
                            state.lyricSynthWords
                        ) { bridge.setLyricSynthWords(it) }
                        SettingDivider()
                        SettingSwitchRow(
                            "已唱部分保持高亮",
                            "关闭后高亮只跟随当前字，唱过的字会变暗",
                            state.lyricKeepSung
                        ) { bridge.setLyricKeepSung(it) }
                        SettingDivider()
                        SettingSlider(
                            "模糊 / 光晕强度",
                            { state.lyricGlow },
                            0f..1f,
                            { "${(it * 100).toInt()}%" }
                        ) { bridge.setLyricGlow(it) }
                        SettingDivider()
                        SettingSlider(
                            "歌词字号",
                            { state.lyricFont },
                            16f..34f,
                            { "${it.toInt()} sp" }
                        ) { bridge.setLyricFont(it) }
                        SettingDivider()
                        SettingSlider(
                            "翻译字号",
                            { state.translationFont },
                            10f..24f,
                            { "${it.toInt()} sp" }
                        ) { bridge.setTranslationFont(it) }
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
                            { state.amllFontSize },
                            20f..64f,
                            { "${it.toInt()} px" }
                        ) { bridge.setAmllFontSize(it) }
                        SettingDivider()
                        SettingSlider(
                            "字重",
                            { state.amllFontWeight },
                            300f..900f,
                            { "${it.toInt()}" }
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
                            { state.amllResumeDelay },
                            0f..10f,
                            { if (it < .05f) "立即" else "${it.toInt()} s" }
                        ) { bridge.setAmllResumeDelay(it) }
                    }

                    FineTuneGroup.Fluid -> {
                        SettingSwitchRow(
                            "静态背景",
                            "用当前封面居中裁切+模糊替代流体背景，明显更省电",
                            state.amllStaticBg
                        ) { bridge.setAmllStaticBg(it) }
                        SettingDivider()
                        SettingSlider(
                            "模糊强度",
                            { state.fluidBlurDp },
                            0f..64f,
                            { "${it.toInt()} dp" }
                        ) { bridge.setFluidBlurDp(it) }
                        SettingDivider()
                        SettingSlider(
                            "流体形变",
                            { state.fluidWarp },
                            0f..3f,
                            { "${(it * 100).toInt()}%" }
                        ) { bridge.setFluidWarp(it) }
                        SettingDivider()
                        SettingSlider(
                            "流动速度",
                            { state.fluidSpeed },
                            0.1f..3f,
                            { "${(it * 100).toInt()}%" }
                        ) { bridge.setFluidSpeed(it) }
                        SettingDivider()
                        SettingSlider(
                            "暗角强度",
                            { state.fluidDark },
                            0f..1f,
                            { "${(it * 100).toInt()}%" }
                        ) { bridge.setFluidDark(it) }
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
                            "网易云无词级时间轴时向酷狗请求逐字歌词；默认关闭",
                            state.kugouKrcEnabled
                        ) { bridge.setKugouKrcEnabled(it) }
                        Spacer(Modifier.height(8.dp))
                        Txt(
                            "第三方源说明：开启后会向酷狗音乐的公开歌词接口发起请求，结果仅缓存在本机。" +
                                "歌词数据版权归酷狗及权利人所有，本项目不分发该数据；如不同意请保持关闭。",
                            AS.caption()
                        )
                    }

                    FineTuneGroup.Cache -> {
                        SettingStepSlider(
                            "音频缓存上限",
                            { state.audioCacheLimitMb.toFloat() },
                            CacheLimitSteps,
                            { formatBytes(it.toLong() * 1024L * 1024L) }
                        ) { bridge.setAudioCacheLimitMb(it.roundToInt()) }
                        SettingDivider()
                        SettingSwitchRow(
                            "缓存下一首",
                            "播放时后台把下一首下完",
                            state.prefetchNext
                        ) { bridge.setPrefetchNext(it) }
                        SettingDivider()
                        SettingSwitchRow(
                            "缓存好再播放",
                            "先下完再播；网速慢时首播会等几秒，但不跳进度",
                            state.waitForCache
                        ) { bridge.setWaitForCache(it) }
                    }

                    FineTuneGroup.Poster -> {
                        val cfg = state.posterConfig
                        SettingStepSlider(
                            "背景样式",
                            { cfg.bg.ordinal.toFloat() },
                            listOf(0f, 1f, 2f),
                            {
                                when (it.roundToInt()) {
                                    0 -> "模糊封面"
                                    1 -> "流光渐变"
                                    else -> "纯色"
                                }
                            }
                        ) { bridge.setPosterConfig(cfg.copy(bg = PosterBg.entries[it.roundToInt().coerceIn(0, 2)])) }
                        SettingDivider()
                        SettingStepSlider(
                            "模糊强度",
                            { cfg.blurLevel.toFloat() },
                            listOf(1f, 2f, 3f),
                            { if (it.roundToInt() == 1) "轻微" else if (it.roundToInt() == 3) "重度" else "标准" }
                        ) { bridge.setPosterConfig(cfg.copy(blurLevel = it.roundToInt().coerceIn(1, 3))) }
                        SettingDivider()
                        SettingStepSlider(
                            "比例",
                            { cfg.ratio.ordinal.toFloat() },
                            listOf(0f, 1f, 2f),
                            { PosterRatio.entries[it.roundToInt().coerceIn(0, 2)].label }
                        ) { bridge.setPosterConfig(cfg.copy(ratio = PosterRatio.entries[it.roundToInt().coerceIn(0, 2)])) }
                        SettingDivider()
                        SettingStepSlider(
                            "歌词行数",
                            { cfg.lyricLines.toFloat() },
                            listOf(1f, 2f, 4f),
                            { "${it.roundToInt()} 行" }
                        ) { bridge.setPosterConfig(cfg.copy(lyricLines = it.roundToInt().coerceIn(1, 4))) }
                        SettingDivider()
                        SettingStepSlider(
                            "歌词字号",
                            { cfg.fontScale },
                            listOf(0.85f, 1f, 1.15f),
                            { "${(it * 100).toInt()}%" }
                        ) { bridge.setPosterConfig(cfg.copy(fontScale = it)) }
                        SettingDivider()
                        SettingSwitchRow("显示翻译", "歌词下方附上翻译", cfg.showTranslation) {
                            bridge.setPosterConfig(cfg.copy(showTranslation = it))
                        }
                        SettingDivider()
                        SettingSwitchRow("显示歌曲信息卡", "底部封面 + 歌名 + 歌手卡片", cfg.showInfoCard) {
                            bridge.setPosterConfig(cfg.copy(showInfoCard = it))
                        }
                        SettingDivider()
                        SettingSwitchRow("显示头像与昵称", "右上角账号信息", cfg.showAvatar) {
                            bridge.setPosterConfig(cfg.copy(showAvatar = it))
                        }
                        SettingDivider()
                        SettingSwitchRow("显示专辑名", "信息卡内第三行", cfg.showAlbum) {
                            bridge.setPosterConfig(cfg.copy(showAlbum = it))
                        }
                        SettingDivider()
                        SettingTextFieldRow(
                            "水印文案",
                            cfg.watermark,
                            "留空则不显示水印"
                        ) { bridge.setPosterConfig(cfg.copy(watermark = it)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Txt(group.note, AS.caption())
            Spacer(Modifier.height(40.dp))
        }
    }
}
