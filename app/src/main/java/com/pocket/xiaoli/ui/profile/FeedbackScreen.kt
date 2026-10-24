package com.pocket.xiaoli.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.data.contribution.ContributionAction
import com.pocket.xiaoli.data.feedback.FeedbackCategory
import com.pocket.xiaoli.data.feedback.FeedbackDraft
import com.pocket.xiaoli.data.feedback.FeedbackFormatter
import com.pocket.xiaoli.data.feedback.FeedbackRemote
import com.pocket.xiaoli.data.feedback.FeedbackStore
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlinx.coroutines.launch

/** 反馈正文要发出去的邮箱。改这里之前先确认这个邮箱还在收信。 */
private const val FEEDBACK_MAIL = "pocketxiaoyan@outlook.com"

/** 提 Issue 用哪个仓库：与「关于」页里展示的是同一个。 */
private const val FEEDBACK_REPO = "LIULIN632/koudaixiaoli"

/**
 * 意见反馈页（二级设置页）。
 *
 * ## 为什么是"一页"而不是"一个弹窗"
 *
 * 改造前是一个 `AlertDialog`：一个输入框 + 一个"提交"，点了往邮件 App 里塞。
 * 真用起来三处都卡：弹窗太小写不下、只有邮件一条路（没有邮件 App 就点了没反应）、
 * 提交完什么都没留下（连自己写了什么都找不回来）。
 *
 * ## 三条真通道 + 一条可选通道
 *
 * | 渠道 | 要不要凭据 | 谁最常用 |
 * |---|---|---|
 * | 复制 / 系统分享 | 不要 | 微信、QQ 里发给开发者 |
 * | 邮件 | 不要（mailto） | 附截图、写长文 |
 * | GitHub Issue（预填） | 不要 | 有 GitHub 账号的人，能追踪进度 |
 * | 直接发送到表格 | 要（默认空 ⇒ 按钮不显示） | 开发者自己配好之后 |
 *
 * 三条都不要凭据，所以**装完就能用**；表格那条配了凭据才长出来，没配就不装样子。
 *
 * ## 隐私
 *
 * 「附带设备信息」默认**关**。不勾时正文里只有用户自己写的字。
 */
@Composable
internal fun ColumnScope.FeedbackContent(container: AppContainer) {
    val context = LocalContext.current
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    val store = remember { FeedbackStore(context) }
    val contribution = remember(container) { container.contributionRepository }

    var draft by remember { mutableStateOf(FeedbackDraft()) }
    var sending by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(store.recent(3)) }
    val remoteReady = FeedbackRemote.configured

    val diagnostics = remember {
        FeedbackFormatter.diagnostics(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    /**
     * 每次触发渠道前都先落一条本地账本，并返回这次用的工单号。
     * 失败/取消都不影响什么 —— 用户还能在页面底部把它捞回来。
     */
    fun openTicket(): Pair<Long, String> {
        val now = System.currentTimeMillis()
        val ticket = FeedbackFormatter.ticketId(now, draft.content.trim().hashCode())
        store.add(ticket, draft, now)
        history = store.recent(3)
        return now to ticket
    }

    fun body(): String {
        val (now, ticket) = openTicket()
        return FeedbackFormatter.buildBody(draft, ticket, now, diagnostics)
    }

    fun copyToClipboard(text: String, toast: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("口袋小鲤反馈", text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

        // ── 写什么 ──
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
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
                    FeedbackCategory.entries.forEach { category ->
                        FilterChip(
                            selected = draft.category == category,
                            onClick = { draft = draft.copy(category = category) },
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
                    onValueChange = { draft = draft.copy(content = it) },
                    placeholder = { Text("说说遇到了什么：点哪儿、看到了什么、你本来想干嘛", color = colors.textTertiary) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentMain,
                        cursorColor = colors.accentMain,
                    ),
                )
                OutlinedTextField(
                    value = draft.contact,
                    onValueChange = { draft = draft.copy(contact = it) },
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

        // ── 带不带环境 ──
        SettingsSectionHeader("附带信息")
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("附带设备信息", color = colors.textPrimary, fontSize = 15.sp)
                    Text(
                        "只在你勾选时带上，不勾就只发你写的字",
                        color = colors.textTertiary,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = draft.includeDiagnostics,
                    onCheckedChange = { draft = draft.copy(includeDiagnostics = it) },
                )
            }
            if (draft.includeDiagnostics) {
                Text(
                    diagnostics,
                    modifier = Modifier.padding(horizontal = skin.spaceLg).padding(bottom = skin.spaceMd),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        // ── 怎么发 ──
        SettingsSectionHeader("发出去")
        val enabled = draft.isSendable && !sending
        Column(verticalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
            if (remoteReady) {
                Button(
                    onClick = {
                        val (now, ticket) = openTicket()
                        scope.launch {
                            sending = true
                            val error = FeedbackRemote.submit(draft, ticket, now, diagnostics)
                            sending = false
                            if (error == null) {
                                contribution.record(ContributionAction.FEEDBACK_SEND)
                            }
                            Toast.makeText(
                                context,
                                error ?: "已发送 · 工单 $ticket",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Send, null, modifier = Modifier.padding(end = 6.dp))
                        Text("直接发送")
                    }
                }
            }

            Button(
                onClick = { copyToClipboard(body(), "已复制，粘贴到微信 / QQ 发给开发者就行") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = if (remoteReady) {
                    ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceAlt,
                        contentColor = colors.textPrimary,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.padding(end = 6.dp))
                Text("复制反馈内容")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
                OutlinedButton(
                    onClick = {
                        val (now, ticket) = openTicket()
                        val url = FeedbackFormatter.mailtoUrl(FEEDBACK_MAIL, draft, ticket, now, diagnostics)
                        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url))) }
                            .onSuccess { contribution.record(ContributionAction.FEEDBACK_SEND) }
                            .onFailure { copyToClipboard(body(), "没找到邮件 App，已复制内容") }
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.MailOutline, null, modifier = Modifier.padding(end = 4.dp))
                    Text("邮件", fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = {
                        val (now, ticket) = openTicket()
                        val url = FeedbackFormatter.issueUrl(FEEDBACK_REPO, draft, ticket, now, diagnostics)
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            .onSuccess { contribution.record(ContributionAction.FEEDBACK_SEND) }
                            .onFailure { copyToClipboard(body(), "打不开浏览器，已复制内容") }
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.padding(end = 4.dp))
                    Text("GitHub", fontSize = 14.sp)
                }
            }

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, body())
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, "分享反馈"))
                    }
                        .onSuccess { contribution.record(ContributionAction.FEEDBACK_SEND) }
                        .onFailure { copyToClipboard(body(), "没有可分享的应用，已复制内容") }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("通过其他应用分享", fontSize = 14.sp)
            }
        }

        // ── 提过的 ──
        if (history.isNotEmpty()) {
            SettingsSectionHeader("我提过的")
            SettingsCard {
                history.forEachIndexed { index, entry ->
                    if (index > 0) SettingsDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyToClipboard(
                                    "${entry.category.label}：${entry.preview}\n工单 ${entry.ticketId}",
                                    "已复制这条",
                                )
                            }
                            .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.category.label,
                                color = colors.accentMain,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .border(1.dp, colors.accentMain.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                            Text(
                                "  ${entry.ticketId}",
                                color = colors.textTertiary,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            entry.preview.ifBlank { "（空）" },
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Text(
            "反馈只在你按下按钮的那一刻才离开这台手机 —— 平时一个字节都不会往外发。",
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceAlt, RoundedCornerShape(skin.radiusChip))
                .padding(skin.spaceMd),
            color = colors.textTertiary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
}
