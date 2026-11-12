package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap

/**
 * Small circular toggle used above the lyric cards. [active] fills it with the accent
 * color; the label stays readable either way.
 */
@Composable
fun LyricToggleButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fill by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = .8f, stiffness = 520f),
        label = "lyric-toggle"
    )
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                lerp(AS.CardElevated, AS.Blue, fill.coerceIn(0f, 1f))
            )
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Txt(
            label,
            AS.rowTitle().copy(
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.55f + 0.45f * fill.coerceIn(0f, 1f))
            )
        )
    }
}
