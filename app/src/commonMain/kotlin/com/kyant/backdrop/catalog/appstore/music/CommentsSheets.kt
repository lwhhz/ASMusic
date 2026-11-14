package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.appstore.AS
import com.kyant.backdrop.catalog.appstore.Txt
import com.kyant.backdrop.catalog.appstore.tap
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as glassBlur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

/** Full comment list, styled with the same liquid-glass material as the tab bar. */
@Composable
fun CommentsSheet(
    songId: Long?,
    total: Int,
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onOpenComment: (CommentItem) -> Unit
) {
    val bridge = MusicServices.bridge
    // 可见性由导航栈给出，而不是"songId 非空"：关闭后 songId 仍保留，
    // 这样滑出动画期间内容不会先一步变空。
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = .92f, stiffness = 380f)
    )
    if (progress < .001f) return

    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf(CommentSort.Likes) }

    LaunchedEffect(songId) {
        if (songId == null) return@LaunchedEffect
        loading = true
        comments = runCatching { bridge.allComments(songId) }.getOrDefault(emptyList())
        loading = false
    }
    val sorted = remember(comments, sort) {
        when (sort) {
            CommentSort.Likes -> comments.sortedByDescending { it.liked }
            CommentSort.Time -> comments.sortedByDescending { it.timeMs }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .60f * progress))
                .tap(onDismiss)
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.80f)
                .offset { IntOffset(0, ((1f - progress) * 1000.dp.toPx()).toInt()) }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) },
                    effects = {
                        vibrancy()
                        glassBlur(22f.dp.toPx())
                        lens(16f.dp.toPx(), 16f.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    innerShadow = { InnerShadow(radius = 7f.dp, alpha = .28f) },
                    onDrawSurface = {
                        drawRect(Color(0xFF121212).copy(alpha = .58f))
                        drawRect(Color.White.copy(alpha = .04f))
                    }
                )
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Txt("全部评论", AS.rowTitle().copy(fontSize = 18.sp))
                    Spacer(Modifier.height(2.dp))
                    Txt(
                        when {
                            loading -> "正在加载…"
                            total > comments.size -> "共 ${formatCount(total)} 条 · 已加载 ${comments.size} 条"
                            else -> "共 ${comments.size} 条（按${sort.label}排序）"
                        },
                        AS.caption().copy(fontSize = 11.sp)
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = .08f))
                        .padding(3.dp)
                ) {
                    CommentSort.entries.forEach { item ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sort == item) AS.Blue else Color.Transparent)
                                .tap { sort = item }
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Txt(
                                item.label,
                                AS.caption().copy(
                                    fontSize = 11.sp,
                                    color = if (sort == item) Color.White else AS.Text3
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                CloseCircle(onDismiss)
            }
            if (comments.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Txt("暂时没有更多评论", AS.subtitle())
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(sorted, key = { it.id }) { comment ->
                        CommentSheetRow(comment, onClick = { onOpenComment(comment) })
                    }
                }
            }
        }
    }
}

/** Full text of one comment, in the same glass sheet. */
@Composable
fun CommentDetailSheet(
    comment: CommentItem?,
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit
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
                .background(Color.Black.copy(alpha = .60f * progress))
                .tap(onDismiss)
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.62f)
                .offset { IntOffset(0, ((1f - progress) * 1000.dp.toPx()).toInt()) }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) },
                    effects = {
                        vibrancy()
                        glassBlur(22f.dp.toPx())
                        lens(16f.dp.toPx(), 16f.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    innerShadow = { InnerShadow(radius = 7f.dp, alpha = .28f) },
                    onDrawSurface = {
                        drawRect(Color(0xFF121212).copy(alpha = .58f))
                        drawRect(Color.White.copy(alpha = .04f))
                    }
                )
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt("评论详情", AS.rowTitle().copy(fontSize = 18.sp), Modifier.weight(1f))
                CloseCircle(onDismiss)
            }
            if (comment != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteAvatar(
                        url = comment.avatarUrl,
                        seed = comment.user.hashCode().toLong(),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(comment.user, AS.rowTitle().copy(fontSize = 16.sp), maxLines = 1)
                        Txt(comment.time.ifBlank { "热门评论" }, AS.caption())
                    }
                    Txt("♡ ${formatCount(comment.liked)}", AS.caption().copy(color = AS.Text3))
                }
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Txt(
                        comment.content,
                        AS.body().copy(fontSize = 16.sp, lineHeight = 24.sp)
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentSheetRow(comment: CommentItem, onClick: () -> Unit) {
    var truncated by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .055f))
            .tap(onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteAvatar(
                url = comment.avatarUrl,
                seed = comment.user.hashCode().toLong(),
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(9.dp))
            Txt(comment.user, AS.rowTitle().copy(fontSize = 14.sp), Modifier.weight(1f), maxLines = 1)
            Txt(comment.time.ifBlank { "热门评论" }, AS.caption().copy(fontSize = 10.sp))
        }
        Spacer(Modifier.height(9.dp))
        BasicText(
            text = comment.content,
            style = AS.body().copy(fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(.86f)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { truncated = it.hasVisualOverflow }
        )
        Spacer(Modifier.weight(1f))
        Txt("♡ ${formatCount(comment.liked)}", AS.caption().copy(fontSize = 10.sp, color = AS.Text3))
    }
    Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CloseCircle(onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .10f))
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Txt("✕", AS.caption().copy(fontSize = 13.sp, color = Color.White.copy(.8f)))
    }
}
