package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 界面图标：全部由开源 SVG 离线转换为 ImageVector（无运行时依赖）。
 * Tabler Icons Filled（MIT）为主；Iconoir Solid（MIT）补箭头与关闭；
 * 循环/随机用 Tabler Outline（两库均无实心版）。统一 24×24 视口。
 */
object Iconoir {

    val play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.play",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M6 4v16a1 1 0 0 0 1.524 .852l13 -8a1 1 0 0 0 0 -1.704l-13 -8a1 1 0 0 0 -1.524 .852z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.pause",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M9 4h-2a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h2a2 2 0 0 0 2 -2v-12a2 2 0 0 0 -2 -2z"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M17 4h-2a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h2a2 2 0 0 0 2 -2v-12a2 2 0 0 0 -2 -2z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val skipPrev: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.skipPrev",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M19.496 4.136l-12 7a1 1 0 0 0 0 1.728l12 7a1 1 0 0 0 1.504 -.864v-14a1 1 0 0 0 -1.504 -.864z"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M4 4a1 1 0 0 1 .993 .883l.007 .117v14a1 1 0 0 1 -1.993 .117l-.007 -.117v-14a1 1 0 0 1 1 -1z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val skipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.skipNext",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M3 5v14a1 1 0 0 0 1.504 .864l12 -7a1 1 0 0 0 0 -1.728l-12 -7a1 1 0 0 0 -1.504 .864z"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M20 4a1 1 0 0 1 .993 .883l.007 .117v14a1 1 0 0 1 -1.993 .117l-.007 -.117v-14a1 1 0 0 1 1 -1z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val queue: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.queue",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M21 6a1 1 0 0 1 -1 1h-10a1 1 0 1 1 0 -2h10a1 1 0 0 1 1 1"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M21 12a1 1 0 0 1 -1 1h-10a1 1 0 0 1 0 -2h10a1 1 0 0 1 1 1"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M21 18a1 1 0 0 1 -1 1h-10a1 1 0 0 1 0 -2h10a1 1 0 0 1 1 1"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M7 5.995v.02c0 1.099 -.895 1.99 -2 1.99s-2 -.891 -2 -1.99v-.02c0 -1.099 .895 -1.99 2 -1.99s2 .891 2 1.99"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M7 11.995v.02c0 1.099 -.895 1.99 -2 1.99s-2 -.891 -2 -1.99v-.02c0 -1.099 .895 -1.99 2 -1.99s2 .891 2 1.99"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M7 17.995v.02c0 1.099 -.895 1.99 -2 1.99s-2 -.891 -2 -1.99v-.02c0 -1.099 .895 -1.99 2 -1.99s2 .891 2 1.99"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val heart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.heart",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M6.979 3.074a6 6 0 0 1 4.988 1.425l.037 .033l.034 -.03a6 6 0 0 1 4.733 -1.44l.246 .036a6 6 0 0 1 3.364 10.008l-.18 .185l-.048 .041l-7.45 7.379a1 1 0 0 1 -1.313 .082l-.094 -.082l-7.493 -7.422a6 6 0 0 1 3.176 -10.215z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.close",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M12 1.25C6.06294 1.25 1.25 6.06294 1.25 12C1.25 17.9371 6.06294 22.75 12 22.75C17.9371 22.75 22.75 17.9371 22.75 12C22.75 6.06294 17.9371 1.25 12 1.25ZM9.70164 8.64124C9.40875 8.34835 8.93388 8.34835 8.64098 8.64124C8.34809 8.93414 8.34809 9.40901 8.64098 9.7019L10.9391 12L8.64098 14.2981C8.34809 14.591 8.34809 15.0659 8.64098 15.3588C8.93388 15.6517 9.40875 15.6517 9.70164 15.3588L11.9997 13.0607L14.2978 15.3588C14.5907 15.6517 15.0656 15.6517 15.3585 15.3588C15.6514 15.0659 15.6514 14.591 15.3585 14.2981L13.0604 12L15.3585 9.7019C15.6514 9.40901 15.6514 8.93414 15.3585 8.64124C15.0656 8.34835 14.5907 8.34835 14.2978 8.64124L11.9997 10.9393L9.70164 8.64124Z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val arrowLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.arrowLeft",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M15.287 18.6929C15.5673 18.5768 15.75 18.3033 15.75 18V5.99998C15.75 5.69663 15.5673 5.42315 15.287 5.30707C15.0068 5.19098 14.6842 5.25515 14.4697 5.46965L8.46967 11.4696C8.17678 11.7625 8.17678 12.2374 8.46967 12.5303L14.4697 18.5303C14.6842 18.7448 15.0068 18.809 15.287 18.6929Z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val arrowRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.arrowRight",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M8.71299 18.6929C8.43273 18.5768 8.25 18.3033 8.25 18V5.99998C8.25 5.69663 8.43273 5.42315 8.71299 5.30707C8.99324 5.19098 9.31583 5.25515 9.53033 5.46965L15.5303 11.4696C15.8232 11.7625 15.8232 12.2374 15.5303 12.5303L9.53033 18.5303C9.31583 18.7448 8.99324 18.809 8.71299 18.6929Z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val repeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.repeat",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M4 12v-3a3 3 0 0 1 3 -3h13m-3 -3l3 3l-3 3"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M20 12v3a3 3 0 0 1 -3 3h-13m3 3l-3 -3l3 -3"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }

    val repeatOnce: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.repeatOnce",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M4 12v-3a3 3 0 0 1 3 -3h13m-3 -3l3 3l-3 3"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M20 12v3a3 3 0 0 1 -3 3h-13m3 3l-3 -3l3 -3"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M11 11l1 -1v4"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }

    val shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.shuffle",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M18 4l3 3l-3 3"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M18 20l3 -3l-3 -3"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M3 7h3a5 5 0 0 1 5 5a5 5 0 0 0 5 5h5"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M21 7h-5a4.978 4.978 0 0 0 -3 1m-4 8a4.984 4.984 0 0 1 -3 1h-3"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }

    val sequential: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.sequential",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M5 12l14 0"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M13 18l6 -6"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                pathData = addPathNodes("M13 6l6 6"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }

    val tabToday: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.tabToday",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M12.707 2.293l9 9c.63 .63 .184 1.707 -.707 1.707h-1v6a3 3 0 0 1 -3 3h-1v-7a3 3 0 0 0 -2.824 -2.995l-.176 -.005h-2a3 3 0 0 0 -3 3v7h-1a3 3 0 0 1 -3 -3v-6h-1c-.89 0 -1.337 -1.077 -.707 -1.707l9 -9a1 1 0 0 1 1.414 0m.293 11.707a1 1 0 0 1 1 1v7h-4v-7a1 1 0 0 1 .883 -.993l.117 -.007z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val tabNew: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.tabNew",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M8 15C12.8747 15 15 12.949 15 8C15 12.949 17.1104 15 22 15C17.1104 15 15 17.1104 15 22C15 17.1104 12.8747 15 8 15Z"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M2 6.5C5.13376 6.5 6.5 5.18153 6.5 2C6.5 5.18153 7.85669 6.5 11 6.5C7.85669 6.5 6.5 7.85669 6.5 11C6.5 7.85669 5.13376 6.5 2 6.5Z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val tabMine: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.tabMine",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M12 2a5 5 0 1 1 -5 5l.005 -.217a5 5 0 0 1 4.995 -4.783z"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M14 14a5 5 0 0 1 5 5v1a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-1a5 5 0 0 1 5 -5h4z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val tabSearch: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.tabSearch",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M14 3.072a8 8 0 0 1 2.32 11.834l5.387 5.387a1 1 0 0 1 -1.414 1.414l-5.388 -5.387a8 8 0 1 1 -.905 -13.249"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val tabStats: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.tabStats",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M9.883 2.207a1.9 1.9 0 0 1 2.087 1.522l.025 .167l.005 .104v7a1 1 0 0 0 .883 .993l.117 .007h6.8a2 2 0 0 1 2 2a1 1 0 0 1 -.026 .226a10 10 0 1 1 -12.27 -11.933l.27 -.067l.11 -.02z"),
                fill = SolidColor(Color.Black)
            )
            .addPath(
                pathData = addPathNodes("M14 3.5v5.5a1 1 0 0 0 1 1h5.5a1 1 0 0 0 .943 -1.332a10 10 0 0 0 -6.11 -6.111a1 1 0 0 0 -1.333 .943z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }

    val share: ImageVector by lazy {
        ImageVector.Builder(
            name = "Iconoir.share",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes("M19 2a3 3 0 0 1 3 3v14a3 3 0 0 1 -3 3h-14a3 3 0 0 1 -3 -3v-14a3 3 0 0 1 3 -3zm-7 5l-.09 .004l-.058 .007l-.118 .025l-.105 .035l-.113 .054l-.111 .071a1.008 1.008 0 0 0 -.112 .097l-4 4l-.083 .094a1 1 0 0 0 .083 1.32l.094 .083a1 1 0 0 0 1.32 -.083l2.293 -2.292v5.585l.007 .117a1 1 0 0 0 1.993 -.117v-5.585l2.293 2.292l.094 .083a1 1 0 0 0 1.32 -1.497l-4 -4l-.082 -.073l-.104 -.074l-.098 -.052l-.11 -.044l-.112 -.03l-.126 -.017l-.075 -.003z"),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
}
