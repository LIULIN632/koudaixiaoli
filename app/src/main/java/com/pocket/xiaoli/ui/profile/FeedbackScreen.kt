package com.pocket.xiaoli.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.data.contribution.ContributionAction
import com.pocket.xiaoli.data.feedback.FeedbackDraft
import com.pocket.xiaoli.data.feedback.FeedbackFormatter
import com.pocket.xiaoli.data.feedback.FeedbackLimits
import com.pocket.xiaoli.data.feedback.FeedbackRemote
import com.pocket.xiaoli.data.feedback.FeedbackStore
import com.pocket.xiaoli.data.feedback.SubmitGate
import com.pocket.xiaoli.data.feedback.SubmitOutcome
import com.pocket.xiaoli.di.AppContainer
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 反馈正文要发出去的邮箱。改这里之前先确认这个邮箱还在收信。 */
private const val FEEDBACK_MAIL = "pocketxiaoyan@outlook.com"

/** 提 Issue 用哪个仓库：与「关于」页里展示的是同一个。 */
private const val FEEDBACK_REPO = "LIULIN632/koudaixiaoli"

/**
 * 反馈页（二级设置页）。
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
 *
 * ## 2026-10-28 这轮美化改了什么（以及为什么）
 *
 * 原先这一页是**全 App 唯一没走设置页语言的地方**：Material3 原生 `Switch`、
 * 三种按钮样式（实心 / 半宽描边 / 文字）叠在一起，四个渠道各长得不一样，
 * 而它们做的是同一件事。这轮把它们归口：
 *
 * - 「附带设备信息」→ [SettingsSwitchItem]（图标块 + MIUIX 开关 + 「?」），与别的开关行同一形态；
 * - 四个渠道 → [FeedbackChannelTile] 2×2 格（边界 + MIUIX 按压下沉），说明只写"这条比别条好在哪"；
 * - 「我提过的」→ 状态做成胶囊（待重发 / 有回复 / 等回复用三种语义色），回复正文做成带色条的引用块，
 *   整条可点并有按压反馈，并补上**什么时候提的**（`atMillis` 一直在账本里，以前从不显示）；
 * - 底部隐私承诺 → 带锁图标的小卡，仍然常驻可见（这是承诺，不是复述性小字，不能收进气泡）。
 *
 * ⚠️ 全部视觉块都拆成了 `internal` 的纯内容（吃数据 + 回调，不碰 `AppContainer`）——
 * 不是洁癖，是因为 `FeedbackContent` 的第一个参数是真 DI 容器，Robolectric 里造不出来，
 * 不拆就**一张基线都录不了**（`AccountBindingScreenshotTest` 的 KDoc 记过同一件事）。
 */
@Composable
internal fun ColumnScope.FeedbackContent(container: AppContainer) {
    val context = LocalContext.current
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

    FeedbackFormCard(
        draft = draft,
        sending = sending,
        onDraftChange = { draft = it },
    )

    FeedbackAttachCard(
        diagnostics = diagnostics,
        included = draft.includeDiagnostics,
        onToggle = { draft = draft.copy(includeDiagnostics = it) },
    )

    CrashReportSection(
        remoteReady = remoteReady,
        onSent = { contribution.record(ContributionAction.FEEDBACK_SEND) },
    )

    FeedbackSendSection(
        remoteReady = remoteReady,
        enabled = draft.isSendable && !sending,
        sending = sending,
        onSend = {
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
        onCopy = { copyToClipboard(body(), "已复制，粘贴到微信 / QQ 发给开发者就行") },
        onMail = {
            val (now, ticket) = openTicket()
            val url = FeedbackFormatter.mailtoUrl(FEEDBACK_MAIL, draft, ticket, now, diagnostics)
            runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url))) }
                .onSuccess { contribution.record(ContributionAction.FEEDBACK_SEND) }
                .onFailure { copyToClipboard(body(), "没找到邮件 App，已复制内容") }
        },
        onIssue = {
            val (now, ticket) = openTicket()
            val url = FeedbackFormatter.issueUrl(FEEDBACK_REPO, draft, ticket, now, diagnostics)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onSuccess { contribution.record(ContributionAction.FEEDBACK_SEND) }
                .onFailure { copyToClipboard(body(), "打不开浏览器，已复制内容") }
        },
        onShare = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body())
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "分享反馈")) }
                .onSuccess { contribution.record(ContributionAction.FEEDBACK_SEND) }
                .onFailure { copyToClipboard(body(), "没有可分享的应用，已复制内容") }
        },
    )

    if (history.isNotEmpty()) {
        FeedbackHistorySection(
            entries = history,
            pending = pending,
            replies = replies,
            sentIds = sentIds,
            canCheck = replyReady && sentIds.isNotEmpty(),
            checking = checkingReplies,
            onCopyEntry = { entry ->
                // 排队中的草稿只存了全文在队列里，复制预览等于丢字。
                val queued = pending[entry.ticketId]
                val text = if (queued != null) {
                    "${queued.category.label}：${queued.content}\n工单 ${entry.ticketId}"
                } else {
                    "${entry.category.label}：${entry.preview}\n工单 ${entry.ticketId}"
                }
                copyToClipboard(text, if (queued != null) "已复制全文" else "已复制这条")
            },
            onCheckReplies = {
                scope.launch {
                    checkingReplies = true
                    loadReplies(force = true)
                    checkingReplies = false
                }
            },
        )
    }

    FeedbackPrivacyNote()
}
