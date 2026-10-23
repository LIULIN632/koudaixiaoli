package com.pocket.xiaoli.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.network.Announcement
import com.pocket.xiaoli.network.AnnouncementLevel
import com.pocket.xiaoli.notification.AppUpdateReceiver
import com.pocket.xiaoli.notification.AppUpdateRequest
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.MiuixSettingsScaffold
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「消息」页：版本更新 + 校园公告的**唯一落地面**。
 *
 * ## 为什么从对话框改成整页（2026-10-19，用户「通知、搜索、设置都可以直接抄岱宗的」）
 *
 * 原来它是一个 Material3 `AlertDialog`（标题「通知」+ 几个圆点行 + 「知道了」）：
 * 公告一多就变成一坨挤在屏幕中间的小字，正文两行以上被对话框宽度压成碎句，
 * 而且要「关掉才能继续看」。岱宗那一屏是列表：一行一条、图标 + 标题 + 摘要 + 时间，
 * 顶栏给一个刷新。结构长这样之后，模板文案长一点也不会难看。
 *
 * ## 语义与旧对话框**完全一致**（不重新发明）
 *
 * - 一进页面就算「看过」：[onMarkAllSeen] 与旧实现的 `LaunchedEffect(lastCheckedAt)` 同义；
 * - 有新版时点整行**直接下载安装**（`AppUpdateReceiver.pending().send()`），
 *   与通知栏里那条「点一下直接下载」是同一条链路，不再让用户跑到浏览器；
 * - 没有内容就说「暂无消息」，检查失败就把失败原因原样摆出来 + 顶栏刷新可重试。
 *
 * @param state 由 [AppFeedRepository] 统一给出的状态，页面自己不判断版本号。
 * @param onBack 返回。
 * @param onMarkAllSeen 「看过即已读」。
 * @param onRetry 顶栏刷新，强制联网核对（`force = true`）。
 */
@Composable
internal fun NotificationScreen(
    state: AppFeedRepository.State,
    onBack: () -> Unit,
    onMarkAllSeen: () -> Unit,
    onRetry: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current

    // 与旧对话框同义：露出来就算看过。
    LaunchedEffect(state.lastCheckedAt) { onMarkAllSeen() }

    val hasContent = state.announcements.isNotEmpty() || state.updateAvailable

    MiuixSettingsScaffold(
        title = "消息",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRetry) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重新检查",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MiuixTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceWide),
        ) {
            when {
                // 「正在查」与「查挂了」必须分开：两者都用 !loaded 判断过一次，
                // 结果是「失败且没有缓存」永远显示「正在检查…」。
                state.checking && !hasContent -> LoadingRow()

                state.error != null && !hasContent -> StatusCard(
                    title = "检查失败",
                    body = state.error,
                    accent = colors.danger,
                    icon = Icons.Outlined.ErrorOutline,
                )

                !hasContent -> StatusCard(
                    title = "暂无消息",
                    body = "版本更新与校园公告会出现在这里。",
                    accent = colors.accentMain,
                    icon = Icons.Outlined.NotificationsNone,
                )

                else -> Unit
            }

            if (state.updateAvailable) {
                SectionHeader("更新")
                AppCard {
                    NotificationRow(
                        title = if (state.forceUpdate) {
                            "请更新到 v${state.latestVersionName}${state.updateBadge}"
                        } else {
                            "有新版本 v${state.latestVersionName}${state.updateBadge}"
                        },
                        body = when {
                            state.forceUpdate -> "当前版本已不受支持，更新后才能继续使用。"
                            state.notes.isNotEmpty() -> state.notes.joinToString("；")
                            else -> "点这一行直接下载安装，装完回来看。"
                        },
                        meta = "点这里下载安装",
                        accent = if (state.forceUpdate) colors.danger else colors.accentMain,
                        icon = { NotificationIcon(Icons.Outlined.SystemUpdateAlt, colors.accentMain) },
                        onClick = { startUpdate(context, state) },
                    )
                }
            }

            if (state.announcements.isNotEmpty()) {
                SectionHeader("公告", count = state.announcements.size)
                state.announcements.forEach { announcement ->
                    AppCard {
                        NotificationRow(
                            title = announcement.title,
                            body = announcement.body,
                            meta = announcement.url?.takeIf { it.isNotBlank() }?.let { "点这里查看详情" },
                            accent = announcement.accent(colors),
                            icon = { NotificationIcon(Icons.Outlined.Description, announcement.accent(colors)) },
                            onClick = announcement.url?.takeIf { it.isNotBlank() }?.let { url ->
                                { openFeedUrl(context, url) }
                            },
                        )
                    }
                }
            }

            // 有内容时的失败只做补充说明（此时已经用缓存渲染出来了）
            if (state.error != null && hasContent) {
                StatusCard(
                    title = "顺带一提",
                    body = state.error,
                    accent = colors.warning,
                    icon = Icons.Outlined.ErrorOutline,
                )
            }

            if (state.lastCheckedAt > 0) {
                val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(Date(state.lastCheckedAt))
                Text(
                    "上次检查：$stamp",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "正在检查…",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/**
 * 空态 / 失败态：图标 + 标题 + 一句说明，底色跟卡片走。
 *
 * ⚠️ 原来这里是一个 7dp 圆点 —— 在整页列表里它跟"未读标记"长得一模一样，
 * 空态读起来像"有一条没读的点"，而不像"没有消息"。图标块把它和列表行分开。
 * 失败态用 **中性衬底 + danger 图标**，不是 danger 低透明度打底（`CLAUDE.md` 的坑）。
 */
@Composable
private fun StatusCard(title: String, body: String?, accent: Color, icon: ImageVector) {
    val colors = LocalPocketXiaoliColors.current
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (accent == colors.danger) colors.surfaceHighlight else accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (!body.isNullOrBlank()) {
                    // 说明文字用次级色而不是 summary（后者压在卡底只有约 2.15:1，读不出）
                    Text(
                        body,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

/** 一行消息：左图标 + 标题 / 正文 / 动作提示，整行可点。 */
@Composable
private fun NotificationRow(
    title: String,
    body: String,
    meta: String?,
    accent: Color,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (body.isNotBlank()) {
                Text(
                    body,
                    style = MiuixTheme.textStyles.footnote1,
                    // summary 三级灰压在卡底只有约 2.15:1；正文用次级色才读得实。
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta != null) {
                // 动作提示后面跟一枚小箭头：这行可点，但正文里原本只有一行 11sp 的彩字，
                // 在长文下面很容易被当成普通标注。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        meta,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** 圆角小方块里的图标：与设置页的 `SettingsIcon` 同一尺寸体系（32dp 圈、18dp 图标）。 */
@Composable
private fun NotificationIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(32.dp).background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** 公告等级 → 颜色，与旧对话框里的映射一字不差。 */
private fun Announcement.accent(colors: com.pocket.xiaoli.ui.theme.PocketXiaoliColors): Color =
    when (level) {
        AnnouncementLevel.CRITICAL, AnnouncementLevel.FORCE_UPDATE -> colors.danger
        AnnouncementLevel.WARN -> colors.warning
        else -> colors.accentMain
    }

/**
 * 点「有新版本」那一行时的动作。
 *
 * 优先走通知栏那条**直接下载**的链路（`AppUpdateReceiver` → WorkManager → 安装器）；
 * 只有当 feed 里的地址/版本不完整（老路 releases/latest 拿到的数据）才退回去开浏览器。
 * 这样「消息页点一下」和「通知栏点一下」不会出现两种行为。
 */
private fun startUpdate(context: Context, state: AppFeedRepository.State) {
    val request = AppUpdateRequest.of(
        rawVersion = state.latestVersionName,
        rawUrl = state.apkUrl,
        rawSha256 = state.sha256,
    )
    if (request.isValid) {
        AppUpdateReceiver.pending(
            context = context,
            versionName = state.latestVersionName,
            apkUrl = state.apkUrl,
            sha256 = state.sha256,
        ).send()
    } else {
        openFeedUrl(context, state.apkUrl)
    }
}
