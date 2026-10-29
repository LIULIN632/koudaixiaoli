package com.pocket.xiaoli.data.announcement

import android.content.Context
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.network.AppReleaseFeed
import com.pocket.xiaoli.network.Announcement
import com.pocket.xiaoli.network.AppReleaseFeedParser
import com.pocket.xiaoli.network.AppUpdateChecker
import com.pocket.xiaoli.network.FeedFetch
import com.pocket.xiaoli.network.ReleaseGate
import com.pocket.xiaoli.network.needsAck
import com.pocket.xiaoli.util.AppVersion
import com.pocket.xiaoli.util.relativeSinceLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 「更新 + 公告」的统一门面：**拉一次 feed，产出首页抽屉 / 红点 / 更新卡要用的一切**。
 *
 * 这一层刻意做得很薄：判定全在 [ReleaseGate]（纯函数、有单测），这里只负责
 * 「什么时候拉、缓存多久、已读怎么记」。
 *
 * ## 三层漏斗
 *
 * | 层 | 谁在拉 | 节奏 |
 * |---|---|---|
 * | L1 打开即知 | `MainActivity` 冷启动 + 导航层每次回前台 | TTL [TTL_MS]（30 分钟）内有缓存就不联网 |
 * | L2 关着也有 | `AppFeedWorker`（WorkManager） | 15 分钟一次，见 `AppFeedScheduler` |
 * | L3 校内变更 | 尚未做（需要教务抓取器） | — |
 *
 * ⚠️ **L2 的 worker 是独立实例**（`AppFeedRepository(context)`），它只负责发系统通知，
 * **不会更新 AppContainer 那份单例的 [state]**。所以"回前台"必须自己再调一次
 * [refresh]（节流会自动压住频率）——否则进程不死的用户永远等不到新的
 * `updateAvailable`，表现就是"只有手动检查才看得见新版"。
 *
 * ## 失败也要有东西看
 *
 * 网络挂了就**用上一次的缓存**渲染，并把 `error` 带出去（UI 只用来显示一句"检查失败"，
 * 不该因此把公告清空）。这是「不能两边都不说」的应用内一侧。
 */
class AppFeedRepository(
    context: Context,
    private val store: AppFeedStore = AppFeedStore(context),
    private val checker: AppUpdateChecker = AppUpdateChecker(),
    private val versionCodeProvider: () -> Int = { AppVersion.versionCode(context.applicationContext) },
) {

    private val mutex = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /**
     * 当前该展示的东西。
     *
     * @param announcements 可见公告（已按等级排序、已过滤时间窗/版本窗）
     * @param unread 其中没被点开过的条数（红点数字）
     * @param updateAvailable 有更新的版本可装
     * @param forceUpdate 低于 `minSupported`，**不许取消**
     * @param blocking 必须确认才能关掉的公告（CRITICAL 或 forceBelow 提级）
     * @param showRedDot 底部导航/首页入口要不要亮红点
     */
    data class State(
        val announcements: List<Announcement> = emptyList(),
        val unread: Int = 0,
        val updateAvailable: Boolean = false,
        val forceUpdate: Boolean = false,
        val blocking: Announcement? = null,
        val latestVersionCode: Int = 0,
        val latestVersionName: String = "",
        val apkUrl: String = "",
        val sha256: String = "",
        /** 新版改了什么（来自 feed 的 `notes`）；老路 releases/latest 拿不到，留空 */
        val notes: List<String> = emptyList(),
        val lastCheckedAt: Long = 0L,
        val error: String? = null,
        /** 至少成功读到过一次 feed（缓存也算） */
        val loaded: Boolean = false,
        /**
         * 此刻正在联网核对。
         *
         * 单独一个字段，是因为以前用「`!loaded`」兼作进行中 ——
         * 一旦拉取**失败且没有缓存**，`loaded` 也是 false，于是「失败了」被显示成
         * 「正在检查…」，用户永远等不到结果。两者必须分开。
         */
        val checking: Boolean = false,
        /**
         * 这次提示的包来自**内测通道**（feed 的 `beta` 块）。
         *
         * 必须让 UI 标出来：内测包没经过完整验证，用户以为是正式版就会带着它去上课，
         * 出问题时无从排查（也收不到"这是你自己选的"这层心理预期）。
         */
        val updateIsBeta: Boolean = false,
        /**
         * 确实有比本机新的包，只是**可能被「稍后提醒」压住了**（所以 [updateAvailable] 是 false）。
         *
         * 单独一个字段，是因为「已是最新」这句话必须有它才配说：抽屉里已经因为这处
         * 缺信息修过一次（见 `AppUpdateSheet.checkNow`），把「你选了稍后提醒」显示成
         * 「已是最新版本」是同一类谎。
         */
        val newerVersionExists: Boolean = false,
        /** 这个构建允不允许应用内自更新（`-Pfdroid=true` = false，包交给商店推）。 */
        val updatesEnabled: Boolean = !BuildConfig.FDROID,
        /**
         * 本机要不要**主动**被提醒更新（设置 → 更新 那个开关，默认开）。
         *
         * 只影响"App 自己凑上来"的三处：自动弹的抽屉、通知栏推送、消息红点。
         * 检查、手动「检查更新」、公告、以及**强制更新**都不受它影响
         *（强更可被一个开关关掉就不叫底线了）。
         */
        val pushEnabled: Boolean = true,
    ) {
        // 更新那颗红点归"主动提醒"管；公告未读不归（那是用户自己发的消息）。
        // forceUpdate 例外：它一定要亮，理由同上。
        val showRedDot: Boolean get() = unread > 0 || forceUpdate || (updateAvailable && pushEnabled)
        val hasAnything: Boolean get() = announcements.isNotEmpty() || updateAvailable || error != null

        /**
         * 「 · 内测版」后缀，非内测时是空串。
         *
         * 后缀只在 State 上算一次：通知、气泡、消息页、抽屉四处都要拼这句话，
         * 写四遍 `if` 早晚有一处忘改（内测用户看到"正式版"字样就是这么来的）。
         */
        val updateBadge: String get() = if (updateIsBeta) " · 内测版" else ""

        /**
         * 「检查更新」那行行尾「?」里的正文：**平时是行为说明，这一轮有毛病时换成毛病原文**。
         *
         * 小字只放得下一句结论（"这次没查上"），而真正有用的是**为什么**——哪个镜像、HTTP 几、
         * 什么异常。这句以前只进一条两秒的 Toast，用户想回头看就得去导日志；挂在问号上之后，
         * 反馈给作者时他自己就能念出来。
         *
         * [error] 非空有两种：真的没查上，和"镜像还在返回旧数据"（手里那份缓存更新）。
         * 两种都不该被"查到新版会先问你"这种常态说明盖掉，所以这里不分类，统一说"这一轮的毛病"。
         */
        val checkHint: String
            get() = error?.let { "这一轮的毛病：$it" } ?: "查到新版会先问你，再下载。"

        /**
         * 「设置 → 更新 → 检查更新」那一行的小字：**这一轮的结论 + 它有多新鲜**。
         *
         * ## 为什么要占这一行
         *
         * 检查的结果原先只走一条 Toast，两秒就没了。用户点了、Toast 被别的通知压掉、
         * 或者只是怀疑「刚才那一下到底查没查」，页面上**一点痕迹都不留** —— 于是只能
         * 再点一次、再点一次。这一行把结论钉在原地，随时回来看都算数。
         *
         * ## 顺序即优先级（每一处都是踩过的坑）
         *
         * | 先 | 后 | 为什么不能反 |
         * |---|---|---|
         * | 正在检查 | 一切 | 失败时 `loaded` 也是 false，早先把「失败」显示成「正在检查…」，用户永远等不到结果 |
         * | 发现新版 | 这次没查上 | [error] 装的是"这一轮的毛病"（失败原因，或"镜像还在返回旧数据"）；手里那份新版是真读回来的，不该被一句"没查上"盖掉 |
         * | 这次没查上 | 由商店更新 | F-Droid 版查失败也是失败了，说"交给商店"等于把问题藏起来 |
         * | 发现新版 | 已是最新 | [updateAvailable] 会被「稍后提醒」压成 false，所以判"有新版"只认 [newerVersionExists]，被压住的那种单独说一句 |
         *
         * 「已是最新」只在**这一轮真的核对过版本**时才说：`latestVersionCode <= 0` 表示这份 feed
         * 压根不带版本信息（只推公告），那时宁可不写字 —— 凭没有的数据下"已是最新"的结论，
         * 和抽屉里那句假「已是最新版本」是同一种谎。时间口径与修课快照共用 [relativeSinceLabel]。
         */
        fun checkTrace(now: Long = System.currentTimeMillis()): String = when {
            checking -> "正在检查…"
            !newerVersionExists && error != null -> "这次没查上，可再点一次"
            !updatesEnabled -> "版本由应用商店更新"
            newerVersionExists && !updateAvailable -> "新版 v$latestVersionName$updateBadge（你选了稍后提醒）"
            newerVersionExists -> "发现新版 v$latestVersionName$updateBadge"
            lastCheckedAt <= 0L || latestVersionCode <= 0 -> ""
            else -> "已是最新 · ${relativeSinceLabel(lastCheckedAt, "查过", now)}"
        }
    }

    /**
     * 内测开关拨完立刻见效，**不用重新联网** —— feed 里两个通道的块本来就都在，
     * 只是渲染时选哪一份的区别。
     */
    fun reapplyChannel() = markSeenRefreshing()

    /**
     * 拉一次，带节流。
     *
     * @param force true = 无视 [TTL_MS] 立刻联网（用户点「检查更新」、或后台任务）
     */
    suspend fun refresh(force: Boolean = false): State = mutex.withLock {
        val now = System.currentTimeMillis()
        val lastCheck = store.lastCheckAt()
        val cached = store.cachedFeedJson()

        // 节流：缓存新鲜就不用联网。注意**第一次**（cached == null）必须联网 ——
        // 否则用户装上第一个版本后永远看不到公告。
        if (!force && cached != null && now - lastCheck < TTL_MS) {
            return@withLock publish(
                parseCached(cached), lastCheckAt = lastCheck, error = null, loaded = true
            )
        }

        // 先把缓存亮出来（冷启动第一次联网时界面别空白），再亮「正在查」。
        // 用户点「检查更新」时看到的立即响应就是这一步给的。
        if (cached != null && !_state.value.loaded) {
            publish(parseCached(cached), lastCheck, null, loaded = true)
        }

        // 先把「正在查」亮出来 —— UI 靠它区分「还在跑」和「跑挂了」
        _state.value = _state.value.copy(checking = true)
        val outcome = try {
            // 并行竞速（Gitee raw / GitHub raw / contents API 同时发，版本最高者胜）：
            // 带 bustCache=true 打掉 CDN 边缘缓存（Gitee raw max-age=60、GitHub raw 300），
            // 否则发版后几分钟内点检查会读到旧 feed，被误判成「已是最新版本」。
            checker.fetchFeedRacing(store.preferredChannel(), bustCache = true, localVersionCode = versionCodeProvider())
        } catch (t: Throwable) {
            // 兜底：任何没被 catch 住的东西都不该让状态永远停在「正在检查」
            FeedFetch.Failed(
                "请求出错（${t.javaClass.simpleName}" +
                    (t.message?.take(120)?.let { "：$it" } ?: "") + "）"
            )
        }
        if (outcome is FeedFetch.Ok) {
            val incoming = outcome.document.feed
            val cachedFeed = cached?.let { parseCached(it) }
            var shown = incoming
            var stale: String? = null
            // 不回退：镜像偶尔会返回比本地缓存更旧的副本（边缘缓存/版本回滚）。
            // 若照单全收，用户就会从「有新版」被打回「已是最新」—— 正是本次要修的病。
            if (cachedFeed != null && cachedFeed.versionCode > incoming.versionCode) {
                shown = cachedFeed
                stale = "镜像还在返回旧数据（vc ${incoming.versionCode} < ${cachedFeed.versionCode}），本次不覆盖本地记录"
            } else {
                store.cacheFeed(outcome.document.rawJson)
            }
            // 接口地址跟着 feed 走：搬家只改 feed.json，不用发版。
            // 写 null 也得写 —— 运营把这条撤掉时要让它退回编译期默认值。
            store.setFeedbackEndpoint(incoming.endpoints.feedback)
            store.setLastCheckAt(now)
            return@withLock publishChecked(shown, now, stale, true)
        }
        val why = (outcome as FeedFetch.Failed).reason.ifBlank { "网络不通" }
        // 失败原因原样告诉用户（哪个镜像、HTTP 几、什么异常）：
        // 光说「检查失败」既不能自证，也没法反馈。
        return@withLock if (cached != null) {
            publishChecked(parseCached(cached), lastCheck, "检查失败：$why（显示的是上次的内容）", true)
        } else {
            publishChecked(null, lastCheck, "检查失败：$why", false)
        }
    }

    /** publish 之后把 checking 归位（成功失败都要走这里，别让 UI 卡在「正在检查」） */
    private fun publishChecked(
        feed: AppReleaseFeed?,
        lastCheckAt: Long,
        error: String?,
        loaded: Boolean,
    ): State {
        val next = publish(feed, lastCheckAt, error, loaded)
        _state.value = next.copy(checking = false)
        return next
    }

    /** 用户点开/看过一条公告 */
    fun markSeen(id: String) {
        store.markSeen(id)
        _state.value = AppFeedView.build(
            feed = lastFeed ?: return,
            currentVersionCode = versionCodeProvider(),
            nowMillis = System.currentTimeMillis(),
            seen = store.seenIds(),
            snoozedVersionCode = store.snoozedVersionCode(),
            lastCheckedAt = store.lastCheckAt(),
            loaded = _state.value.loaded,
            rolloutId = store.rolloutId(),
            betaEnabled = store.betaEnabled(),
            pushEnabled = store.pushEnabled(),
        )
    }

    fun markAllSeen() {
        val ids = _state.value.announcements.map { it.id }
        if (ids.isEmpty()) return
        store.markSeen(ids)
        markSeenRefreshing()
    }

    /** 「稍后提醒」：这个版本不再进红点，但页面里仍然看得见 */
    fun snoozeCurrentVersion() {
        val code = _state.value.latestVersionCode
        if (code > 0) store.snoozeVersion(code)
        markSeenRefreshing()
    }

    private fun markSeenRefreshing() {
        val feed = lastFeed ?: return
        _state.value = AppFeedView.build(
            feed = feed,
            currentVersionCode = versionCodeProvider(),
            nowMillis = System.currentTimeMillis(),
            seen = store.seenIds(),
            snoozedVersionCode = store.snoozedVersionCode(),
            lastCheckedAt = store.lastCheckAt(),
            loaded = _state.value.loaded,
            rolloutId = store.rolloutId(),
            betaEnabled = store.betaEnabled(),
            pushEnabled = store.pushEnabled(),
        )
    }

    private fun publish(feed: AppReleaseFeed?, lastCheckAt: Long, error: String?, loaded: Boolean): State {
        lastFeed = feed
        val next = AppFeedView.build(
            feed = feed,
            currentVersionCode = versionCodeProvider(),
            nowMillis = System.currentTimeMillis(),
            seen = store.seenIds(),
            snoozedVersionCode = store.snoozedVersionCode(),
            lastCheckedAt = lastCheckAt,
            loaded = loaded,
            error = error,
            rolloutId = store.rolloutId(),
            betaEnabled = store.betaEnabled(),
            pushEnabled = store.pushEnabled(),
        )
        _state.value = next
        return next
    }

    private fun parseCached(json: String): AppReleaseFeed? = AppReleaseFeedParser.parse(json)

    @Volatile
    private var lastFeed: AppReleaseFeed? = null

    // 给后台 worker 用：拿最近一次的内存态（没有就现拉）
    fun peek(): State = _state.value

    companion object {
        /** L1 的节流：30 分钟内重复打开 App 不重复联网 */
        const val TTL_MS = 30 * 60 * 1000L

        /** L2 的周期：15 分钟 —— 已是 WorkManager 允许的最小周期（再小会被系统抬回 15 分钟） */
        const val WORK_INTERVAL_MINUTES = 15L
    }
}

/**
 * 纯函数：把「一份 feed + 当前版本 + 已读集合」算成 [AppFeedRepository.State]。
 *
 * 独立出来是为了**能在单测里不碰 Context 就验证红点、已读、强更这些容易出错的判定**。
 */
internal object AppFeedView {

    fun build(
        feed: AppReleaseFeed?,
        currentVersionCode: Int,
        nowMillis: Long,
        seen: Set<String>,
        snoozedVersionCode: Int,
        lastCheckedAt: Long,
        loaded: Boolean,
        error: String? = null,
        /** 设备本地灰度 UUID；传 null = 不判灰度（老调用点行为不变） */
        rolloutId: String? = null,
        /** 本机是否加入了内测（`AppFeedStore.betaEnabled()`） */
        betaEnabled: Boolean = false,
        /**
         * 是否允许在应用内提示/下载新版本。
         *
         * F-Droid 构建（`-Pfdroid=true`）传 false：更新由 F-Droid 自己管，
         * 应用内再挂一个「下载安装包」的入口既不符合它的分发模型，也容易被审核追问。
         * 公告照常显示（`announcements` 不受这个开关影响）。
         */
        updatesEnabled: Boolean = !BuildConfig.FDROID,
        /** 本机「主动提醒更新」开关（`AppFeedStore.pushEnabled()`），只影响弹/推送/红点 */
        pushEnabled: Boolean = true,
    ): AppFeedRepository.State {
        if (feed == null) {
            return AppFeedRepository.State(
                lastCheckedAt = lastCheckedAt,
                loaded = loaded,
                error = error,
            )
        }

        // 通道选择：开了内测、feed 带了 beta 块、beta 比本机新、而且这台机器命中了 beta 自己的
        // 灰度 —— 才用 beta 那份；**少任何一条都退回 stable**。
        // 退回而不是"什么都不给"：内测开关是**加一条通道**，不是换一个闸门 ——
        // beta 还没出包 / 被 rollout:0 刹住时，用户也该照常收到稳定版的更新，
        // 否则"拨了内测开关反而收不到更新"这种反馈一定会回来。
        val betaBlock = if (betaEnabled) {
            feed.beta?.takeIf { block ->
                block.hasApp && block.versionCode > currentVersionCode &&
                    (rolloutId == null || ReleaseGate.isRolloutHit(rolloutId, block.versionCode, block.rollout))
            }
        } else {
            null
        }
        val shown = betaBlock ?: feed

        // ⚠️ 强更**不吃灰度**：`minSupported` 说的是"手上这个版本已经不能用了"（接口断版、
        // 数据格式变了），跟新版本放多少量无关。要是把它也压掉，没进灰度桶的用户会停在
        // 一个坏版本上连提示都收不到 —— 那是最坏的结局。所以先判强更，
        // 命中强更就直接放量给用户（apkUrl 一直都在，装得上）。
        // beta 用户要判**两块**：他现在装的可能是 stable 包，stable 的下限对他同样成立。
        val force = ReleaseGate.mustUpdate(shown, currentVersionCode) ||
            (betaBlock != null && ReleaseGate.mustUpdate(feed, currentVersionCode))

        // 灰度没命中 = 这台机器**现在**看不到这个版本（等同 feed 不带 app），但公告照常渲染。
        // 把 versionCode 归零再往下走，红点、通知、气泡、稍后提醒全部自动跟着灭。
        // （beta 通道的灰度在上面选通道时已经判过，这里只会压稳定版。）
        val gated = if (rolloutId != null && !force && shown.hasApp &&
            !ReleaseGate.isRolloutHit(rolloutId, shown.versionCode, shown.rollout)
        ) {
            shown.copy(versionCode = 0)
        } else {
            shown
        }

        // 公告永远只认根上那一份：内测用户不该因为在内测就漏掉闭馆通知
        val visible = ReleaseGate.visible(feed, nowMillis, currentVersionCode)
        val unread = visible.count { it.id !in seen }
        val hasApp = gated.hasApp && ReleaseGate.isNewer(gated, currentVersionCode)
        // 「稍后提醒」只压红点，不压可见性：更新卡该在还在，只是不再催
        val updateAvailable = updatesEnabled && hasApp && !(snoozedVersionCode == gated.versionCode && !force)

        // 必须确认的公告：用 seen 当「已确认」的账本 —— 点过「知道了」就不再拦人。
        // 强更时不叠公告对话框（强更那张已经在挡着了）。
        val blockingAnnouncement = if (force) {
            null
        } else {
            visible.firstOrNull { it.id !in seen && it.needsAck }
        }

        return AppFeedRepository.State(
            announcements = visible,
            unread = unread,
            updateAvailable = updateAvailable,
            forceUpdate = force,
            blocking = blockingAnnouncement,
            latestVersionCode = if (gated.hasApp) gated.versionCode else 0,
            latestVersionName = if (gated.hasApp) gated.versionName else "",
            apkUrl = gated.apkUrl,
            sha256 = gated.sha256,
            notes = gated.notes,
            lastCheckedAt = lastCheckedAt,
            error = error,
            loaded = loaded,
            // 只有"真的在推这个包"时才标内测：灰度把 beta 版压掉之后已经没有包可装了
            updateIsBeta = betaBlock != null && gated.hasApp,
            // 不经 `updatesEnabled`、也不经「稍后提醒」：这就是"数据上有更新的东西"这件事本身
            newerVersionExists = hasApp,
            updatesEnabled = updatesEnabled,
            pushEnabled = pushEnabled,
        )
    }
}
