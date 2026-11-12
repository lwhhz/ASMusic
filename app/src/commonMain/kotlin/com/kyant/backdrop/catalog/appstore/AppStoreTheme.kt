package com.kyant.backdrop.catalog.appstore

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens transcribed from 开发文档.md §6 (App Store dark appearance).
 * All surfaces are dark; text colors carry alpha so they composite correctly
 * over both pure black and the elevated #1c1c1e cards.
 */
object AS {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF1C1C1E)
    val CardElevated = Color(0xFF2C2C2E)
    val Text = Color.White
    val Text2 = Color(0x99EBEBF5) // rgba(235,235,245,.6)
    val Text3 = Color(0x4DEBEBF5) // rgba(235,235,245,.3)
    val Blue = Color(0xFF0A84FF)
    val Orange = Color(0xFFFF9F0A)
    val Separator = Color(0xA6545458) // rgba(84,84,88,.65)

    val PagePad = 16.dp
    val CardPad = 20.dp
    val CardRadius = 20.dp
    val SectionGap = 26.dp
    val RowGap = 18.dp

    // iOS-style large title, tuned for a 375pt-wide viewport.
    fun largeTitle() = TextStyle(
        color = Text, fontSize = 36.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp
    )

    fun sectionTitle() = TextStyle(
        color = Text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    )

    fun cardTitle() = TextStyle(
        color = Text, fontSize = 23.sp, fontWeight = FontWeight.Bold
    )

    fun rowTitle() = TextStyle(
        color = Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
    )

    fun body() = TextStyle(color = Text, fontSize = 17.sp)

    fun subtitle() = TextStyle(color = Text2, fontSize = 15.sp)

    fun caption() = TextStyle(color = Text2, fontSize = 12.sp)

    fun eyebrow() = TextStyle(
        color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    )

    fun button() = TextStyle(
        color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold
    )

    fun tabLabel(active: Boolean) = TextStyle(
        color = if (active) Blue else Text,
        fontSize = 10.sp, fontWeight = FontWeight.Medium
    )
}

/** Gradients used by the original artwork (no third-party images are used). */
object ASGradients {
    val ArcadeLight = listOf(Color(0xFFEEF5FC), Color(0xFFDCEAF7))
    val TikTokMist = listOf(
        Color(0xFF3C2A63), Color(0xFF127A76), Color(0xFFB9703B), Color(0xFF1B3B6F)
    )
    val BrowseOrange = listOf(Color(0xFFFFB54F), Color(0xFFF5822A))
    val BrowseBlue = listOf(Color(0xFF7AA8FF), Color(0xFF4C72E8))
}
