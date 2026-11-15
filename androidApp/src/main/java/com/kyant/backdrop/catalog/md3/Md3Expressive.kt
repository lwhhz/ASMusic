package com.kyant.backdrop.catalog.md3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 尺寸档（官方 token：XS 32 / S 40 / M 56 / L 96 / XL 136）
// 圆角：静止 12/12/16/28/28，按下 8/8/12/16/16 —— 按下更方，这是 Expressive 的 morph
// ---------------------------------------------------------------------------

enum class Md3Size(
    val height: Dp,
    val corner: Dp,
    val pressedCorner: Dp,
    val hPad: Dp,
    val icon: Dp,
    val label: androidx.compose.ui.unit.TextUnit
) {
    XS(32.dp, 12.dp, 8.dp, 16.dp, 16.dp, 12.sp),
    S(40.dp, 12.dp, 8.dp, 16.dp, 18.dp, 14.sp),
    M(56.dp, 16.dp, 12.dp, 24.dp, 22.dp, 16.sp),
    L(96.dp, 28.dp, 16.dp, 48.dp, 32.dp, 20.sp),
    XL(136.dp, 28.dp, 16.dp, 64.dp, 44.dp, 24.sp)
}

enum class Md3ButtonStyle { Filled, Tonal, Outlined, Text, Elevated }

// ---------------------------------------------------------------------------
// 按压交互：状态层 + 形状回应 + 触觉反馈
// ---------------------------------------------------------------------------

class Md3Press(
    val source: MutableInteractionSource,
    val pressed: Boolean,
    val hovered: Boolean
)

@Composable
fun rememberMd3Press(enabled: Boolean = true): Md3Press {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    return Md3Press(source, pressed && enabled, hovered && enabled)
}

/**
 * 带状态层与形变的点击：替代裸 `clickable`。
 *
 * 之前所有行、卡片都用 `md3Clickable`（indication = null），按下去只有 3% 缩放，
 * 在深色背景上几乎看不出来 —— 这是「点了没反应」的主要来源。这里补上
 * M3 的状态层（按下 12%、悬停 6%），并把触控目标补到 48dp。
 */
@Composable
fun Modifier.md3SurfaceClick(
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(0.dp),
    enabled: Boolean = true,
    scaleTo: Float = .98f,
    layerColor: Color = LocalContentColor.current
): Modifier {
    val press = rememberMd3Press(enabled)
    val tokens = LocalMd3Tokens.current
    val haptics = LocalHapticFeedback.current
    val overlay by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            press.pressed -> .12f
            press.hovered -> .06f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "state-layer"
    )
    val scale by animateFloatAsState(
        targetValue = if (press.pressed && enabled) scaleTo else 1f,
        animationSpec = tokens.spring,
        label = "press-scale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .drawBehind {
            if (overlay > 0f) drawRect(color = layerColor, alpha = overlay)
        }
        .clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = press.source,
            indication = null
        ) {
            if (tokens.haptics) {
                runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
            onClick()
        }
}

/** 只补状态层、不改尺寸的版本（列表行用，行高已足够）。 */
@Composable
fun Modifier.md3RowClick(
    onClick: () -> Unit,
    shape: Shape = MaterialTheme.shapes.medium,
    enabled: Boolean = true
): Modifier = md3SurfaceClick(onClick = onClick, shape = shape, enabled = enabled, scaleTo = 1f)

// ---------------------------------------------------------------------------
// 按钮
// ---------------------------------------------------------------------------

@Composable
fun Md3Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    style: Md3ButtonStyle = Md3ButtonStyle.Filled,
    size: Md3Size = Md3Size.S,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val container = when (style) {
        Md3ButtonStyle.Filled -> colors.primary
        Md3ButtonStyle.Tonal -> colors.secondaryContainer
        Md3ButtonStyle.Outlined, Md3ButtonStyle.Text -> Color.Transparent
        Md3ButtonStyle.Elevated -> colors.surfaceContainerLow
    }
    val contentColor = when (style) {
        Md3ButtonStyle.Filled -> colors.onPrimary
        Md3ButtonStyle.Tonal -> colors.onSecondaryContainer
        Md3ButtonStyle.Outlined, Md3ButtonStyle.Text -> colors.primary
        Md3ButtonStyle.Elevated -> colors.primary
    }
    val press = rememberMd3Press(enabled)
    val tokens = LocalMd3Tokens.current
    val haptics = LocalHapticFeedback.current
    val corner by animateDpAsState(
        targetValue = if (press.pressed) size.pressedCorner else size.corner,
        animationSpec = tokens.springDp,
        label = "btn-corner"
    )
    val overlay by animateFloatAsState(
        targetValue = if (press.pressed) .14f else if (press.hovered) .07f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "btn-layer"
    )
    val shape = RoundedCornerShape(corner)
    val shownContainer = if (enabled) container else container.copy(alpha = .38f)
    val shownContent = if (enabled) contentColor else contentColor.copy(alpha = .38f)

    Box(
        modifier
            .then(if (size == Md3Size.XS) Modifier.minimumTouchTarget() else Modifier)
            .height(size.height)
            .clip(shape)
            .background(shownContainer)
            .drawBehind {
                if (overlay > 0f) drawRect(color = shownContent, alpha = overlay)
            }
            .then(
                if (style == Md3ButtonStyle.Outlined) {
                    Modifier.border1(colors.outlineVariant, shape)
                } else Modifier
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = press.source,
                indication = null
            ) {
                if (tokens.haptics) {
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
                onClick()
            }
            .padding(horizontal = if (size == Md3Size.XS) 16.dp else size.hPad),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = shownContent,
                    modifier = Modifier.size(size.icon)
                )
                if (text != null) Spacer(Modifier.width(if (size == Md3Size.XS) 6.dp else 8.dp))
            }
            if (text != null) {
                Text(
                    text,
                    color = shownContent,
                    fontSize = size.label,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Modifier.border1(color: Color, shape: Shape): Modifier = this.border(1.dp, color, shape)

/** 图标按钮（Expressive：未选中圆角、选中更方，且按下会收）。 */
@Composable
fun Md3IconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tonal: Boolean = false,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color? = null
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalMd3Tokens.current
    val press = rememberMd3Press()
    val haptics = LocalHapticFeedback.current
    val corner by animateDpAsState(
        targetValue = when {
            press.pressed -> 12.dp
            selected -> 14.dp
            else -> size / 2
        },
        animationSpec = tokens.springDp,
        label = "icon-corner"
    )
    val bg = when {
        selected && tonal -> colors.primary
        selected -> colors.primary.copy(alpha = .16f)
        tonal -> colors.surfaceContainerHigh
        else -> Color.Transparent
    }
    val fg = tint ?: when {
        selected && tonal -> colors.onPrimary
        selected -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(
                role = Role.Button,
                interactionSource = press.source,
                indication = null
            ) {
                if (tokens.haptics) {
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(iconSize))
    }
}

/** 可选中的按钮（Expressive Toggle：选中与未选中的形状不同）。 */
@Composable
fun Md3ToggleButton(
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    size: Md3Size = Md3Size.S
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalMd3Tokens.current
    val press = rememberMd3Press()
    val corner by animateDpAsState(
        targetValue = when {
            press.pressed -> size.pressedCorner
            selected -> size.height / 2
            else -> size.corner
        },
        animationSpec = tokens.springDp,
        label = "toggle-corner"
    )
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh,
        label = "toggle-bg"
    )
    val fg = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Row(
        modifier
            .height(size.height)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(
                role = Role.Checkbox,
                interactionSource = press.source,
                indication = null
            ) { onToggle(!selected) }
            .padding(horizontal = size.hPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size.icon))
            if (text != null) Spacer(Modifier.width(8.dp))
        }
        if (text != null) {
            Text(text, color = fg, fontSize = size.label, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Connected Button Group（互斥选项）—— 选中段填充 + 对勾 + 形状 morph
// ---------------------------------------------------------------------------

@Composable
fun Md3ConnectedButtonGroup(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Md3Size = Md3Size.S,
    fullWidth: Boolean = false
) {
    if (options.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val tokens = LocalMd3Tokens.current
    val outer = RoundedCornerShape(size.height / 2)
    Row(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(outer)
            .background(colors.surfaceContainerHigh)
            .androidxSelectableGroup()
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            val press = rememberMd3Press()
            val corner by animateDpAsState(
                targetValue = when {
                    isSelected || press.pressed -> size.height / 2
                    else -> 4.dp
                },
                animationSpec = tokens.springDp,
                label = "seg-corner"
            )
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) colors.primary else Color.Transparent,
                label = "seg-bg"
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                label = "seg-fg"
            )
            Row(
                Modifier
                    .then(if (fullWidth) Modifier.weight(1f) else Modifier)
                    .height(size.height - 4.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .androidxSelectable(selected = isSelected, role = Role.RadioButton) { onSelect(i) }
                    .padding(horizontal = if (fullWidth) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    label,
                    color = fg,
                    fontSize = if (fullWidth) 13.sp else size.label,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// 语义：一组互斥选项对读屏应报「N 个中的第几个」
private fun Modifier.androidxSelectableGroup(): Modifier = this.selectableGroup()

private fun Modifier.androidxSelectable(
    selected: Boolean,
    role: Role,
    onClick: () -> Unit
): Modifier = this.selectable(
    selected = selected,
    onClick = onClick,
    role = role,
    interactionSource = null,
    indication = null
)

// ---------------------------------------------------------------------------
// Standard Button Group（一组相关但独立的动作）
// ---------------------------------------------------------------------------

@Composable
fun Md3ButtonGroup(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    var anyPressed by remember { mutableStateOf(false) }
    val gap by animateDpAsState(
        targetValue = if (anyPressed) 2.dp else spacing,
        animationSpec = spring(dampingRatio = .62f, stiffness = 520f),
        label = "group-gap"
    )
    Row(
        modifier.heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

// ---------------------------------------------------------------------------
// Split Button（左边执行、右边展开菜单）
// ---------------------------------------------------------------------------

@Composable
fun Md3SplitButton(
    text: String,
    onClick: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: Md3Size = Md3Size.S,
    tonal: Boolean = false,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalMd3Tokens.current
    var expanded by remember { mutableStateOf(false) }
    val bg = if (tonal) colors.secondaryContainer else colors.primary
    val fg = if (tonal) colors.onSecondaryContainer else colors.onPrimary
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tokens.spring,
        label = "chevron"
    )
    val inner = RoundedCornerShape(if (size == Md3Size.XS || size == Md3Size.S) 4.dp else 8.dp)
    val outer = RoundedCornerShape(size.height / 2)

    Box(modifier) {
        Row(
            Modifier
                .height(size.height)
                .clip(outer)
                .background(bg)
                .padding(1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .height(size.height - 2.dp)
                    .clip(inner)
                    .md3SurfaceClick(onClick = onClick, shape = inner, layerColor = fg)
                    .padding(horizontal = if (size == Md3Size.XS) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trailingIcon != null) {
                    Icon(trailingIcon, null, tint = fg, modifier = Modifier.size(size.icon))
                    Spacer(Modifier.width(8.dp))
                } else if (icon != null) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(size.icon))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    color = fg,
                    fontSize = size.label,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            val showChevron = actions.isNotEmpty()
            if (showChevron) {
                Box(
                    Modifier
                        .height(size.height - 2.dp)
                        .width(if (size == Md3Size.XS) 40.dp else 44.dp)
                        .clip(inner)
                        .md3SurfaceClick(
                            onClick = { expanded = true },
                            shape = inner,
                            layerColor = fg
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = if (trailingLabel != null) trailingLabel else "更多选项",
                        tint = fg,
                        modifier = Modifier.size(size.icon).rotate(chevron)
                    )
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// FAB Menu（Expressive）：关闭态固定 56dp，展开一组带文字的动作
// ---------------------------------------------------------------------------

data class Md3FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun Md3FabMenu(
    mainIcon: ImageVector,
    mainLabel: String,
    actions: List<Md3FabAction>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val tokens = LocalMd3Tokens.current
    val rotation by animateFloatAsState(
        targetValue = if (open) 135f else 0f,
        animationSpec = tokens.spring,
        label = "fab-rotate"
    )
    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEachIndexed { i, action ->
                AnimatedVisibility(
                    visible = open,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                ) {
                    Row(
                        Modifier
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(colors.secondaryContainer)
                            .md3SurfaceClick(
                                onClick = {
                                    open = false
                                    action.onClick()
                                },
                                shape = CircleShape,
                                layerColor = colors.onSecondaryContainer
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            action.label,
                            color = colors.onSecondaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            // 关闭态固定 56dp（官方要求），不因变成菜单触发器而缩小
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(color)
                    .md3SurfaceClick(
                        onClick = { open = !open },
                        shape = RoundedCornerShape(18.dp),
                        layerColor = onColor
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    mainIcon,
                    contentDescription = if (open) "关闭菜单" else mainLabel,
                    tint = onColor,
                    modifier = Modifier.size(24.dp).rotate(rotation)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 波形进度（Expressive）与可拖动波形 seek
// ---------------------------------------------------------------------------

/**
 * 波形进度。播放时波峰流动，暂停时收成直线 —— 动效只在「有东西在动」时存在。
 * 官方 WavyProgressIndicator 在 1.4.0 还没有公开 composable，这里按同一形状语言自绘。
 */
@Composable
fun Md3WavyProgress(
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    thickness: Dp = 5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
    amplitudeScale: Float = 1f
) {
    val tokens = LocalMd3Tokens.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(
                1800,
                easing = androidx.compose.animation.core.LinearEasing
            )
        ),
        label = "phase"
    )
    val amplitude by animateFloatAsState(
        targetValue = if (playing && tokens.motion != MotionLevel.Off) 1f else 0f,
        animationSpec = spring(dampingRatio = .7f, stiffness = 220f),
        label = "amplitude"
    )
    Canvas(modifier.fillMaxWidth().height(thickness * 3.2f)) {
        val cy = size.height / 2f
        val w = size.width
        val p = progress.coerceIn(0f, 1f)
        val stroke = thickness.toPx()
        drawLine(trackColor, Offset(0f, cy), Offset(w, cy), stroke, StrokeCap.Round)
        if (p <= 0f) return@Canvas
        val amp = size.height * .32f * amplitude * amplitudeScale
        val path = Path()
        val steps = 72
        for (i in 0..steps) {
            val x = w * p * i / steps
            val t = i.toFloat() / steps
            val y = cy + amp * sin(t * 5f * PI.toFloat() + phase) * sin(t * PI.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        // 端点小圆：让「进度头」在暂停时也能被看见
        drawCircle(color, radius = stroke * .9f, center = Offset(w * p, cy))
    }
}

/**
 * 可拖动的播放进度条：波形 + 拖动气泡。
 * 拖动时波形定住（不再流动），松手才 seek —— 避免边拖边发请求。
 */
@Composable
fun Md3WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    wavy: Boolean = true,
    bufferedMs: Long = 0L
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val realFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = if (dragging) dragFraction else realFraction
    val colors = MaterialTheme.colorScheme
    val tokens = LocalMd3Tokens.current
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.5f else 1f,
        animationSpec = tokens.spring,
        label = "thumb"
    )

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((f * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            onSeek((dragFraction * durationMs).toLong())
                        },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val cy = size.height / 2f
                val w = size.width
                val stroke = 6.dp.toPx()
                val trackAlpha = colors.onSurface.copy(alpha = .30f)
                if (bufferedMs > 0 && durationMs > 0) {
                    val bf = (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    drawLine(trackAlpha, Offset(0f, cy), Offset(w * bf, cy), stroke, StrokeCap.Round)
                } else {
                    drawLine(trackAlpha, Offset(0f, cy), Offset(w, cy), stroke, StrokeCap.Round)
                }
                if (fraction <= 0f) return@Canvas
                if (wavy && !dragging && fraction > .06f) {
                    // 只画已达到的部分，振幅随长度渐入：进度很小时保持直线，
                    // 否则一小段波形会缩成一个色块。
                    val amp = size.height * .22f * minOf(1f, fraction * 5f)
                    val path = Path()
                    val steps = 48
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = w * fraction * t
                        val y = cy + amp * sin(t * 7f * PI.toFloat()) * sin(t * PI.toFloat())
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, colors.primary, style = Stroke(stroke, cap = StrokeCap.Round))
                } else {
                    drawLine(
                        colors.primary,
                        Offset(0f, cy),
                        Offset(w * fraction, cy),
                        stroke,
                        StrokeCap.Round
                    )
                }
                drawCircle(
                    colors.primary,
                    radius = 7.dp.toPx() * thumbScale,
                    center = Offset(w * fraction, cy)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging) (dragFraction * durationMs).toLong() else positionMs
            Text(
                md3Time(shown),
                style = MaterialTheme.typography.labelSmall,
                color = if (dragging) colors.primary else colors.onSurfaceVariant
            )
            Text(
                md3Time(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Loading Indicator（形状 morph）
// ---------------------------------------------------------------------------

@Composable
fun Md3LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val tokens = LocalMd3Tokens.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
    val morph by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 780,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "morph"
    )
    // 可见形状 38dp，占位 48dp（官方要求预留足边距）
    Canvas(modifier.size(48.dp)) {
        val r = 19.dp.toPx()
        val c = center
        val path = Path()
        val points = 12
        for (i in 0 until points) {
            val angle = (i.toFloat() / points) * 2f * PI.toFloat()
            // 圆形 → 四角星：radius 在 1.0 与 0.72 之间按 3 分频交替
            val spike = if (i % 3 == 0) 1f + .38f * morph else 1f - .12f * morph
            val rr = r * spike
            val x = c.x + (rr * cos(angle)).toFloat()
            val y = c.y + (rr * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

// ---------------------------------------------------------------------------
// Floating Toolbar（Expressive）：收起是一个 FAB，展开是一排操作
// ---------------------------------------------------------------------------

data class Md3ToolbarAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val selected: Boolean = false
)

@Composable
fun Md3FloatingToolbar(
    actions: List<Md3ToolbarAction>,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalMd3Tokens.current
    var expanded by remember { mutableStateOf(startExpanded) }
    val corner by animateDpAsState(
        targetValue = if (expanded) 26.dp else 28.dp,
        animationSpec = tokens.springDp,
        label = "toolbar-corner"
    )
    androidx.compose.material3.Surface(
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(corner)),
        color = colors.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(corner)
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AnimatedVisibility(visible = expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    actions.forEach { action ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (action.selected) colors.primary.copy(alpha = .16f)
                                    else Color.Transparent
                                )
                                .md3SurfaceClick(
                                    onClick = action.onClick,
                                    shape = RoundedCornerShape(20.dp),
                                    layerColor = colors.onSurface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = action.label,
                                tint = if (action.selected) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .md3SurfaceClick(
                        onClick = { expanded = !expanded },
                        shape = RoundedCornerShape(20.dp),
                        layerColor = colors.onSurface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    leadingIcon ?: if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.Add,
                    contentDescription = if (expanded) "收起工具栏" else "展开工具栏",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 顶栏与标题
// ---------------------------------------------------------------------------

/**
 * 顶栏。刻意不用 Scaffold 的 topBar：那会把内容永久压下一截，
 * 滚动时留下一块空白的「占位」。这里只吃状态栏高度，其余交给页面自己滚。
 */
@Composable
fun Md3TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Md3IconButton(
                Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                size = 48.dp
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

/** 列表内的大标题（首页/发现/资料库/统计/搜索共用）。 */
@Composable
fun Md3PageHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing()
        }
    }
}

/** 区块标题 + 可选动作；没有动作时不留空按钮。 */
@Composable
fun Md3SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) trailing()
        else if (action != null && onAction != null) {
            Md3Button(
                onClick = onAction,
                text = action,
                style = Md3ButtonStyle.Text,
                size = Md3Size.XS
            )
        }
    }
}


/** 触控目标兜底：视觉尺寸不变，命中区补到 48dp。 */
private fun Modifier.minimumTouchTarget(): Modifier = this.minimumInteractiveComponentSize()


/** 状态栏高度（dp）。列表用它做 contentPadding，让内容滚动时能钻到状态栏下面。 */
@Composable
fun md3TopInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()


/** 顶栏右侧的头像入口：没登录时显示占位图标。 */
@Composable
fun Md3AvatarButton(url: String?, contentDescription: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .md3SurfaceClick(onClick = onClick, shape = CircleShape, scaleTo = .94f),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            Md3Cover(url, Modifier.fillMaxSize(), shape = CircleShape)
        } else {
            Box(
                Modifier.fillMaxSize().background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Person,
                    contentDescription = contentDescription,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
