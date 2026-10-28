package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.feedback.FeedbackCategory
import com.pocket.xiaoli.data.feedback.FeedbackDraft
import com.pocket.xiaoli.data.feedback.FeedbackLimits
import com.pocket.xiaoli.data.feedback.FeedbackReply
import com.pocket.xiaoli.data.feedback.FeedbackStore
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.miuixPress
import com.pocket.xiaoli.util.relativeSinceLabel

/**
 * 反馈页的**纯内容块**（吃数据 + 回调，不碰 `AppContainer`）。
 *
 * 拆出来有两个理由，都不是"文件太长"：
 *
 * 1. **拍得到图**。`FeedbackContent` 的第一个参数是真 DI 容器，Robolectric 里造不出来
 *（项目 120 多个测试类一个都没造过它），不拆就一张基线都录不了 ——
 * 与 `AccountBindingScreenshotTest` / `UpdatePageContent` 同一个办法。
 * 2. **一条链只管一件事**。`FeedbackScreen.kt` 留的是"什么时候发、发到哪、失败怎么办"，
 * 这一页的视觉语言（渠道格、状态胶囊、回复引用块）集中在这里改，两边互不牵连。
 */

/** 「写什么」：分类 + 正文 + 联系方式。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackFormCard(
    draft: FeedbackDraft,
    sending: Boolean,
    onDraftChange: (FeedbackDraft) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    SettingsCard {
        Column(
            modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            Text(
                "哪里不对？",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // FlowRow 而不是 Row / 横向滚动：四枚分类是**一次要看完的选项**。
            // 用 `Row` 时第四枚被挤成竖排两个字（「其/他」）；用横向滚动会把第四条藏到屏外
            //（`MiuixSettings.kt` 那条"用滚动不换行"的前提是列表会长到三四行，这里不会 ——
            // 分类是四个，永远四个）。换行是这里唯一两种机器上都不丢信息的排法：
            // 392dp 宽的 Pixel 5 上「其他」会落到第二行，而它**看得见、点得着**。
            // ⚠️ 想收成一行的三条路都堵着：M3 `FilterChip` 没有 `contentPadding` 参数；
            //   缩字号会让分类和正文同级；改标签名会动到账本（`fromLabel` 按标签反解）。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(skin.spaceXs),
                verticalArrangement = Arrangement.spacedBy(skin.spaceXs),
            ) {
                FeedbackCategory.entries.forEach { category ->
                    FilterChip(
                        selected = draft.category == category,
                        onClick = { onDraftChange(draft.copy(category = category)) },
                        label = { Text(category.label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentMain.copy(alpha = 0.16f),
                            selectedLabelColor = colors.accentMain,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = draft.content,
                onValueChange = {
                    if (it.length <= FeedbackLimits.MAX_CONTENT_LENGTH) {
                        onDraftChange(draft.copy(content = it))
                    }
                },
                enabled = !sending,
                placeholder = { Text("说说遇到了什么：点哪儿、看到了什么、你本来想干嘛", color = colors.textTertiary) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentMain,
                    cursorColor = colors.accentMain,
                ),
            )
            // 只在快到上限时才出现：平时它是一句没人看的计数，接近了才是有用的边界提示
            if (draft.content.length > FeedbackLimits.MAX_CONTENT_LENGTH - 200) {
                Text(
                    "${draft.content.length} / ${FeedbackLimits.MAX_CONTENT_LENGTH}",
                    modifier = Modifier.align(Alignment.End),
                    color = if (draft.content.length >= FeedbackLimits.MAX_CONTENT_LENGTH) {
                        colors.accentMain
                    } else {
                        colors.textTertiary
                    },
                    fontSize = 11.sp,
                )
            }
            OutlinedTextField(
                value = draft.contact,
                onValueChange = {
                    if (it.length <= FeedbackLimits.MAX_CONTACT_LENGTH) {
                        onDraftChange(draft.copy(contact = it))
                    }
                },
                enabled = !sending,
                placeholder = { Text("联系方式（选填，想收到回复就留）", color = colors.textTertiary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentMain,
                    cursorColor = colors.accentMain,
                ),
            )
        }
    }
}

/** 「附带信息」：设备信息开关 + 勾上之后展开的那一段。 */
@Composable
internal fun FeedbackAttachCard(
    diagnostics: String,
    included: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    SettingsSectionHeader("附带信息")
    SettingsCard {
        SettingsSwitchItem(
            icon = Icons.Outlined.Smartphone,
            title = "附带设备信息",
            checked = included,
            onToggle = { onToggle(!included) },
            accent = ServiceAccent.QUERY,
            hint = "勾上会一起带上机型、系统版本与 App 版本（下面能看到全部）。" +
                "不勾就只发你写的字，一个字节都不多。",
        )
        if (included) {
            // 展开的就是"到底会带什么"，所以必须**逐字给看**：只说"会带设备信息"
            // 等于让用户凭信任勾选，那是隐私说明最没用的写法。
            Text(
                diagnostics,
                modifier = Modifier
                    .padding(start = skin.spaceLg, end = skin.spaceLg, bottom = skin.spaceMd)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(colors.surfaceAlt)
                    .padding(skin.spaceMd),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/** 一个渠道：图标块 + 名称 + 一句"这条比别条好在哪"。 */
private data class FeedbackChannel(
    val icon: ImageVector,
    val title: String,
    val note: String,
    val accent: ServiceAccent,
    val onClick: () -> Unit,
)

/** 「发出去」：一个主按钮 + 2×2 渠道格。 */
@Composable
internal fun FeedbackSendSection(
    remoteReady: Boolean,
    enabled: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onMail: () -> Unit,
    onIssue: () -> Unit,
    onShare: () -> Unit,
) {
    val skin = LocalSkin.current
    SettingsSectionHeader("发出去")

    // 服务端没配好时，「直接发送」这个按钮根本不该占位 —— 主按钮换成"复制"，
    // 用户进来第一眼看到的就是一条真能走通的路（以前是灰掉的发送按钮）。
    // 两种情况下它都是"这一屏唯一的主行动"，所以配色用主题的实心主色，不做二次区分。
    Button(
        onClick = if (remoteReady) onSend else onCopy,
        enabled = enabled,
        shape = RoundedCornerShape(skin.radiusCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = skin.spaceSm),
    ) {
        if (sending && remoteReady) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = if (remoteReady) Icons.Filled.Send else Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(if (remoteReady) "直接发送" else "复制反馈内容")
        }
    }

    val channels = buildList {
        if (remoteReady) {
            add(
                FeedbackChannel(
                    icon = Icons.Filled.ContentCopy,
                    title = "复制内容",
                    note = "粘到微信就行",
                    accent = ServiceAccent.LIFE,
                    onClick = onCopy,
                )
            )
        }
        add(
            FeedbackChannel(
                icon = Icons.Outlined.MailOutline,
                title = "邮件",
                note = "适合带截图",
                accent = ServiceAccent.QUERY,
                onClick = onMail,
            )
        )
        add(
            FeedbackChannel(
                icon = Icons.Filled.OpenInNew,
                title = "GitHub Issue",
                note = "能追踪进度",
                accent = ServiceAccent.ACADEMIC,
                onClick = onIssue,
            )
        )
        add(
            FeedbackChannel(
                icon = Icons.Filled.Share,
                title = "系统分享",
                note = "发给别的 App",
                accent = ServiceAccent.PAYMENT,
                onClick = onShare,
            )
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
        channels.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                row.forEach { channel ->
                    FeedbackChannelTile(
                        icon = channel.icon,
                        title = channel.title,
                        note = channel.note,
                        accent = channel.accent,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = channel.onClick,
                    )
                }
                // 渠道数是奇数（没服务端时只剩三条）：补一个占位，否则最后一条会被拉成整行宽
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 一格渠道。用 [AppCard] 而不是 M3 `OutlinedButton`：这一页四个动作是同一层级的并列选项，
 * 按钮样式（实心/描边/文字）会把它们排出主次，而格子只表达"这是四条路"。
 * 边界与按压反馈都由 `AppCard` 给（可点元素不能只有一行字）。
 */
@Composable
internal fun FeedbackChannelTile(
    icon: ImageVector,
    title: String,
    note: String,
    accent: ServiceAccent,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        onClick = onClick.takeIf { enabled },
        border = BorderStroke(1.dp, colors.borderDivider),
    ) {
        Row(
            modifier = Modifier.padding(skin.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(icon = icon, accent = accent)
            Spacer(Modifier.width(skin.spaceSm))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    note,
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 「我提过的」：账本 + 状态 + 开发者回复，末尾挂「检查回复」。 */
@Composable
internal fun FeedbackHistorySection(
    entries: List<FeedbackStore.Entry>,
    pending: Map<String, FeedbackStore.Pending>,
    replies: Map<String, FeedbackReply>,
    sentIds: Set<String>,
    canCheck: Boolean,
    checking: Boolean,
    now: Long = System.currentTimeMillis(),
    onCopyEntry: (FeedbackStore.Entry) -> Unit,
    onCheckReplies: () -> Unit,
) {
    val skin = LocalSkin.current
    SettingsSectionHeader("我提过的")
    SettingsCard {
        entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            FeedbackHistoryRow(
                entry = entry,
                queued = pending[entry.ticketId],
                reply = replies[entry.ticketId],
                sent = entry.ticketId in sentIds,
                now = now,
                onClick = { onCopyEntry(entry) },
            )
        }
    }
    if (canCheck) {
        Spacer(Modifier.height(skin.spaceSm))
        FeedbackChannelTile(
            icon = Icons.Outlined.Refresh,
            title = "检查回复",
            note = if (checking) "正在检查…" else "看开发者回没回",
            accent = ServiceAccent.INFO,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
            onClick = onCheckReplies,
        )
    }
}

/** 一条提过的反馈。整行可点 = 复制（排队中的那条复制的是全文，不是摘要）。 */
@Composable
private fun FeedbackHistoryRow(
    entry: FeedbackStore.Entry,
    queued: FeedbackStore.Pending?,
    reply: FeedbackReply?,
    sent: Boolean,
    now: Long,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .miuixPress(onClick = onClick)
            .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.category.label,
                color = colors.accentMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accentMain.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(skin.spaceXs))
            // 时间以前一直不显示（`atMillis` 在账本里躺着）——用户分不清哪条是上周提的
            Text(
                relativeSinceLabel(entry.atMillis, "提过", now).ifBlank { entry.ticketId },
                color = colors.textTertiary,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            when {
                queued != null && queued.blocked -> FeedbackStatusPill("发送失败", colors.danger)
                queued != null -> FeedbackStatusPill("待重发", colors.warning)
                reply != null -> FeedbackStatusPill("有回复", colors.success)
                sent -> FeedbackStatusPill("等回复", colors.textTertiary)
            }
        }
        Spacer(Modifier.height(skin.spaceMicro))
        Text(
            (queued?.content ?: entry.preview).ifBlank { "（空）" },
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (reply != null) {
            // 回复是这屏唯一"别人说的话"，所以标出来源与时刻，并且不跟用户自己的正文混色
            Spacer(Modifier.height(skin.spaceXs))
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(colors.surfaceAlt)
                    .border(width = 1.dp, color = colors.success.copy(alpha = 0.45f))
                    .padding(horizontal = skin.spaceMd, vertical = skin.spaceXs),
            ) {
                Text(
                    if (reply.at.isBlank()) "开发者回复" else "开发者回复 · ${reply.at}",
                    color = colors.success,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    reply.text,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (queued != null) {
            Text(
                if (queued.blocked) "点这条复制全文，手动发出去" else "点这条复制全文，下次进这页会自动重发",
                color = colors.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 状态胶囊：只说"现在到哪一步了"，颜色承担急不急（红=要你自己动手，黄=还没送出去，绿=有回应）。 */
@Composable
private fun FeedbackStatusPill(label: String, tone: Color) {
    val colors = LocalPocketXiaoliColors.current
    Text(
        label,
        color = tone,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tone.copy(alpha = if (tone == colors.textTertiary) 0.10f else 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 隐私承诺：常驻可见，不收进气泡 —— 这是"我们不会背着你发东西"的声明，不是操作说明。 */
@Composable
internal fun FeedbackPrivacyNote() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Spacer(Modifier.height(skin.spaceSm))
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.surfaceAlt,
    ) {
        Row(
            modifier = Modifier.padding(skin.spaceMd),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(colors.accentMain.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.accentMain,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                "反馈只在你按下按钮的那一刻才离开这台手机 —— 平时一个字节都不会往外发。" +
                    "「直接发送」会另附一条设备号哈希出的匿名标识（不含学号），用来把同一台设备的多条反馈串起来。",
                // weight 而不是让 Row 按固有宽度量：图标那一列固定 28dp，剩下的宽度归这段文字
                modifier = Modifier.weight(1f),
                color = colors.textTertiary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
