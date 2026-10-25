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
import androidx.compose.runtime.LaunchedEffect
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
import com.pocket.xiaoli.data.feedback.FeedbackLimits
import com.pocket.xiaoli.data.feedback.FeedbackRemote
import com.pocket.xiaoli.data.feedback.FeedbackStore
import com.pocket.xiaoli.data.feedback.SubmitGate
import com.pocket.xiaoli.data.feedback.SubmitOutcome
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlinx.coroutines.launch
import kotlin.random.Random

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
 * ## 四条真通道 + 一条可选通道
 *
 * | 渠道 | 要不要凭据 | 谁最常用 |
 * |---|---|---|
 * | 复制 / 系统分享 | 不要 | 微信、QQ 里发给开发者 |
 * | 邮件 | 不要（mailto） | 附截图、写长文 |
 * | GitHub Issue（预填） | 不要 | 有 GitHub 账号的人，能追踪进度 |
 * | 直接发送 | 不要（走自建服务器，见 `FeedbackRemote`） | 想一次发完、还想收到回复的人 |
 *
 * 四条都不需要用户配任何东西，所以**装完就能用**。服务端地址优先取
 * `announce/feed.json` 的 `endpoints.feedback`——**换服务器不用发版**。
 * 服务端收下之后，「我提过的」会显示开发者回复，也能手动检查。
 *
 * ## 隐私
 *
 * 「附带设备信息」默认**关**。不勾时正文里只有用户自己写的字。
 * 只有「直接发送」会附带一条设备号哈希的匿名标识（见 [FeedbackStore.anonId]），
 * 复制 / 邮件 / GitHub 不碰它。
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
    var pending by remember { mutableStateOf(store.pending().associateBy { it.ticketId }) }
    var sentIds by remember { mutableStateOf(store.sentIds()) }
    var replies by remember { mutableStateOf(store.cachedReplies()) }
    var checkingReplies by remember { mutableStateOf(false) }
    val remoteReady = remember(context) { FeedbackRemote.configured(context) }
    // 回复与提交走同一个服务端，所以两者一起有、一起无。
    val replyReady = remoteReady

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

    fun syncLocalState() {
        history = store.recent(3)
        pending = store.pending().associateBy { it.ticketId }
        sentIds = store.sentIds()
        replies = store.cachedReplies()
    }

    /**
     * 每次触发渠道前都先落一条本地账本，并返回这次用的工单号。
     * 失败/取消都不影响什么 —— 用户还能在页面底部把它捞回来。
     */
    fun openTicket(): Pair<Long, String> {
        val now = System.currentTimeMillis()
        val ticket = FeedbackFormatter.ticketId(now, Random.nextInt(0x1000000))
        store.add(ticket, draft, now)
        syncLocalState()
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

    /** 补发上次没送出去的。网络还断着就停，别把整队都试一遍。 */
    suspend fun flushPending() {
        if (!remoteReady) return
        var changed = false
        for (item in store.pending()) {
            if (item.blocked) continue
            val outcome = FeedbackRemote.submit(
                context = context,
                draft = FeedbackDraft(
                    category = item.category,
                    content = item.content,
                    contact = item.contact,
                    includeDiagnostics = item.includeDiagnostics,
                ),
                ticketId = item.ticketId,
                diagnostics = item.diagnostics,
                anonId = store.anonId(),
            )
            when (outcome) {
                SubmitOutcome.Sent -> {
                    store.markSent(item.ticketId)
                    store.removePending(item.ticketId)
                    changed = true
                }
                is SubmitOutcome.Retryable -> break
                is SubmitOutcome.Rejected -> {
                    // 表格明确不收（多半是少了一列）。留在队列里会每次进页面重试一遍，
                    // 但不删——用户点这条还能把全文复制出来手动发。
                    store.markBlocked(item.ticketId)
                    changed = true
                }
            }
        }
        if (changed) syncLocalState()
    }

    suspend fun loadReplies(force: Boolean) {
        if (!remoteReady || !replyReady) return
        val tickets = store.sentIds().toList()
        if (tickets.isEmpty()) return
        if (!store.shouldFetchReplies(hasPending = store.pending().isNotEmpty(), force = force)) return
        val got = FeedbackRemote.replies(context, tickets)
        if (got.isNotEmpty()) store.cacheReplies(got)
        // 失败也记一次：免费版配额按接口次数算，断网时不该每次重进都打一发。
        store.markRepliesFetched()
        replies = store.cachedReplies()
    }

    // 进页面：先把排队的内容送出去，再（按节流）看看有没有新回复。
    LaunchedEffect(remoteReady, replyReady) {
        if (!remoteReady) return@LaunchedEffect
        flushPending()
        if (replyReady) loadReplies(force = false)
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
                onValueChange = {
                    if (it.length <= FeedbackLimits.MAX_CONTENT_LENGTH) draft = draft.copy(content = it)
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
                    if (it.length <= FeedbackLimits.MAX_CONTACT_LENGTH) draft = draft.copy(contact = it)
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
                    when (val gate = store.submitGate()) {
                        is SubmitGate.Cooling -> Toast.makeText(
                            context,
                            "刚提交过，${gate.secondsLeft} 秒后再试",
                            Toast.LENGTH_SHORT,
                        ).show()

                        SubmitGate.DailyLimit -> Toast.makeText(
                            context,
                            "今天已经提了 ${FeedbackLimits.DAILY_MAX} 条，明天再来吧",
                            Toast.LENGTH_SHORT,
                        ).show()

                        SubmitGate.Ok -> {
                            val (now, ticket) = openTicket()
                            store.markSubmitAttempt(now)
                            val snapshot = draft
                            val anonId = store.anonId()
                            scope.launch {
                                sending = true
                                val outcome = FeedbackRemote.submit(context, snapshot, ticket, diagnostics, anonId)
                                sending = false
                                when (outcome) {
                                    SubmitOutcome.Sent -> {
                                        store.markSent(ticket)
                                        contribution.record(ContributionAction.FEEDBACK_SEND)
                                        draft = draft.copy(content = "", contact = "")
                                        Toast.makeText(context, "已发送 · 工单 $ticket", Toast.LENGTH_SHORT).show()
                                    }

                                    is SubmitOutcome.Retryable -> {
                                        val queued = store.enqueue(
                                            FeedbackStore.Pending(
                                                ticketId = ticket,
                                                category = snapshot.category,
                                                content = snapshot.content.trim(),
                                                contact = snapshot.contact.trim(),
                                                includeDiagnostics = snapshot.includeDiagnostics,
                                                atMillis = now,
                                                diagnostics = diagnostics.takeIf { snapshot.includeDiagnostics },
                                            )
                                        )
                                        if (queued) draft = draft.copy(content = "", contact = "")
                                        Toast.makeText(
                                            context,
                                            if (queued) {
                                                "网络不通，已存本机，下次进这页自动重发 · 工单 $ticket"
                                            } else {
                                                "本地待发已满，请先复制内容手动发出去"
                                            },
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }

                                    is SubmitOutcome.Rejected -> Toast.makeText(
                                        context,
                                        outcome.reason,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                syncLocalState()
                            }
                        }
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
                val queued = pending[entry.ticketId]
                val reply = replies[entry.ticketId]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 排队中的草稿只存了全文在队列里，复制预览等于丢字。
                            val text = if (queued != null) {
                                "${queued.category.label}：${queued.content}\n工单 ${entry.ticketId}"
                            } else {
                                "${entry.category.label}：${entry.preview}\n工单 ${entry.ticketId}"
                            }
                            copyToClipboard(text, if (queued != null) "已复制全文" else "已复制这条")
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
                    when {
                        queued != null -> Text(
                            if (queued.blocked) "发送失败 · 点这条复制全文，手动发出去" else "待联网重发 · 点这条复制全文",
                            color = colors.textTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )

                        reply != null -> {
                            Text(
                                if (reply.at.isBlank()) "回复" else "回复 · ${reply.at}",
                                color = colors.accentMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp),
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

                        entry.ticketId in sentIds -> Text(
                            "已发送 · 等回复",
                            color = colors.textTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        if (replyReady && sentIds.isNotEmpty()) {
            TextButton(
                onClick = {
                    scope.launch {
                        checkingReplies = true
                        loadReplies(force = true)
                        checkingReplies = false
                    }
                },
                enabled = !checkingReplies,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checkingReplies) "正在检查…" else "检查回复", fontSize = 13.sp)
            }
        }
    }

    Text(
        "反馈只在你按下按钮的那一刻才离开这台手机 —— 平时一个字节都不会往外发。" +
            "「直接发送」会另附一条设备号哈希出的匿名标识（不含学号），用来把同一台设备的多条反馈串起来。",
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt, RoundedCornerShape(skin.radiusChip))
            .padding(skin.spaceMd),
        color = colors.textTertiary,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}
