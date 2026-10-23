package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.network.Announcement
import com.pocket.xiaoli.network.AnnouncementLevel
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@Composable
internal fun NotificationDialog(
    state: AppFeedRepository.State,
    onDismiss: () -> Unit,
    onMarkAllSeen: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current

    // 「看过即已读」：对话框一露出来就算看过，不要求逐条点。
    // key 用 lastCheckedAt —— 后台又拉到新内容（值变了）时会重新标记一次。
    LaunchedEffect(state.lastCheckedAt) { onMarkAllSeen() }

    val hasContent = state.announcements.isNotEmpty() || state.updateAvailable
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    // 「正在查」和「查挂了」必须分开：以前两者都用 !loaded 判断，
                    // 于是「拉取失败且没有缓存」（loaded 也是 false）会永远显示「正在检查…」，
                    // 用户等不到结果，也看不到失败原因。
                    state.checking -> Text(
                        "正在检查…",
                        style = PocketTypography.caption,
                        color = colors.textSecondary,
                    )

                    state.error != null && !hasContent -> {
                        Text(
                            "检查失败",
                            style = PocketTypography.cardTitle,
                            fontWeight = FontWeight.Bold,
                            color = colors.danger,
                        )
                        Text(
                            state.error,
                            style = PocketTypography.caption,
                            color = colors.textSecondary,
                        )
                    }

                    !hasContent -> {
                        Text(
                            "暂无通知",
                            style = PocketTypography.cardTitle,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            "版本更新与校园公告会出现在这里。",
                            style = PocketTypography.caption,
                            color = colors.textSecondary,
                        )
                    }

                    else -> {
                        if (state.updateAvailable) {
                            FeedRow(
                                title = if (state.forceUpdate) {
                                    "请更新到 v${state.latestVersionName}${state.updateBadge}"
                                } else {
                                    "有新版本 v${state.latestVersionName}${state.updateBadge}"
                                },
                                body = if (state.forceUpdate) {
                                    "当前版本已不受支持，更新后才能继续使用。"
                                } else {
                                    "点右侧去下载，装完再回来。"
                                },
                                accent = if (state.forceUpdate) colors.danger else colors.accentMain,
                                actionLabel = "去下载",
                                onAction = { openFeedUrl(context, state.apkUrl) },
                            )
                        }
                        state.announcements.forEach { announcement ->
                            FeedRow(
                                title = announcement.title,
                                body = announcement.body,
                                accent = when (announcement.level) {
                                    AnnouncementLevel.CRITICAL,
                                    AnnouncementLevel.FORCE_UPDATE -> colors.danger
                                    AnnouncementLevel.WARN -> colors.warning
                                    else -> colors.accentMain
                                },
                                actionLabel = if (announcement.url != null) "查看" else null,
                                onAction = { openFeedUrl(context, announcement.url.orEmpty()) },
                            )
                        }
                    }
                }

                // 有内容时的失败只做补充说明（此时已经用缓存渲染出来了）
                if (state.error != null && hasContent) {
                    Text(
                        state.error,
                        style = PocketTypography.caption,
                        color = colors.textSecondary,
                    )
                }
                if (state.lastCheckedAt > 0) {
                    val stamp = java.text.SimpleDateFormat(
                        "MM-dd HH:mm",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date(state.lastCheckedAt))
                    Text(
                        "上次检查：$stamp",
                        style = PocketTypography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("知道了") }
        },
        // 失败时给一个原地重试，不必退出再进
        dismissButton = if (state.error != null) {
            { TextButton(onClick = onRetry) { Text("重试") } }
        } else {
            null
        },
    )
}

/** 抽屉里的一行：标题 + 正文 + 可选动作 */
@Composable
internal fun FeedRow(
    title: String,
    body: String,
    accent: Color,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = PocketTypography.cardTitle,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = PocketTypography.caption)
                }
            }
        }
        if (body.isNotBlank()) {
            Text(
                body,
                style = PocketTypography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

/** 打开 feed 里给的链接；空串就什么都不做（绝不因为数据脏就崩） */
internal fun openFeedUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 必须确认的公告：点外面也算确认过（等价于「知道了」），免得成了关不掉的弹窗 */
@Composable
internal fun AnnouncementAlert(announcement: Announcement, onDismiss: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(announcement.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (announcement.body.isNotBlank()) {
                    Text(
                        announcement.body,
                        style = PocketTypography.caption,
                        color = colors.textSecondary,
                    )
                }
                if (announcement.url != null) {
                    TextButton(onClick = { openFeedUrl(context, announcement.url) }) {
                        Text("查看详情", style = PocketTypography.caption)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("知道了") }
        },
    )
}
