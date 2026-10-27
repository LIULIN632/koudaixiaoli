package com.pocket.xiaoli.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.pocket.xiaoli.data.SelfHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * 软件更新检查：**先读 feed.json，读不到再退回 releases/latest**。
 *
 * ## 为什么是两层
 *
 * - **第一层 feed.json**：带 `versionCode` / `minSupported` / `sha256` / 公告，能表达
 *   「强更」「灰度」「下载校验」「不发版也能推公告」。这是新增的正路。
 * - **第二层 releases/latest**：今天已经在用的老路，只比 tag。保留它是为了
 *   **feed.json 还没部署、或两个镜像同时挂掉时不至于让用户永远收不到更新**。
 *
 * 判定口径：有 `versionCode` 就比整数（`gradle.properties` 是唯一真源，只增不减）；
 * 只有版本名时退回三段式数字比较（见 [isNewer]）。
 *
 * ## 两个镜像的形态不一样（实测）
 *
 * | 源 | 响应 | 处理 |
 * |---|---|---|
 * | Gitee `api/v5/.../contents/路径` | `{"content":"<base64>","encoding":"base64",…}` | 解信封再解析 |
 * | GitHub `raw.githubusercontent.com` | 文件原文 | 直接解析 |
 *
 * ⚠️ Gitee 主仓的默认分支是 `master`，GitHub 仓是 `main` —— 标题里的分支名别互抄。
 */

/**
 * feed 拉取结果：成功带文档，失败带**给人看的原因**（UI 要原样显示它）。
 *
 * 放在类外（而不是 `AppUpdateChecker.FeedFetch`）是为了让 `data/announcement` 那层
 * 能直接 `import com.pocket.xiaoli.network.FeedFetch`，不必把 checker 类型带出去。
 */
sealed interface FeedFetch {
    data class Ok(val document: FetchedFeed) : FeedFetch
    data class Failed(val reason: String) : FeedFetch
}

class AppUpdateChecker {

    companion object {
        // ⚠️ 2026-10-14 仓库已落地。**Gitee 才是主渠道**：同一天实测，
        // 同一个 26.8 MB 的 APK，Gitee 发行版 1,988 KB/s（13.5 秒），
        // GitHub 只有 58 KB/s（462 秒）且 github.com 经常连不上 —— 见下面两个常量。
        /** 更新渠道：自动（Gitee 优先）/ 只用 Gitee / 只用 GitHub。 */
        const val CHANNEL_AUTO = "auto"
        const val CHANNEL_GITEE = "gitee"
        const val CHANNEL_GITHUB = "github"
        /** 首选：Gitee 发行版（国内直连，实测 ~2 MB/s）。 */
        const val GITEE_RELEASES_URL = "https://gitee.com/api/v5/repos/liulin62633/pocket-carp/releases/latest"

        /** 兜底：GitHub 发行版（实测仅 ~58 KB/s，且 github.com 常连不上）。 */
        const val GITHUB_RELEASES_URL = "https://api.github.com/repos/LIULIN632/koudaixiaoli/releases/latest"

        /**
         * 主镜像：Gitee 的 **raw** 文件地址（不是 OpenAPI）。
         *
         * 2026-10-17 实测：raw 走 302 → `raw.giteeusercontent.com`（带签名的 CDN 链接），
         * 匿名可达、200、且**不占 OpenAPI 配额**；
         * 而 `api/v5/.../contents/...` 匿名请求会 403 `Rate Limit Exceeded`
         * （同一时刻岱宗盒子的 releases OpenAPI 也一起 403 —— 那是**按 IP** 的配额，
         * 与仓库无关；我这边连续发版把它打满了，用户设备不会这么用）。
         *
         * 岱宗的 bulletin 也是走 raw 而不是 OpenAPI，这就是它「一直可以」的原因。
         */
        /**
         * ⚠️ 2026-10-19：分支名从 `master` 改成 **`main`**，并保留 `master` 做兜底。
         *
         * 起因是一次「新版已经发了，App 还说已是最新」（v0.1.68 → v0.1.69）：
         * Gitee 仓里同时有 `main` 和 `master` 两个分支，**发版推的是 `main`**，
         * 而 `master` 停在 v0.1.68 不动 —— 本常量当时写的是 `/raw/master/`，
         * 于是 raw 永远返回旧 feed。客户端再有「最新者胜」也救不回来：
         * 两个镜像都读到 vc=69，本地版本也是 69，结论就是「已是最新」。
         *
         * 两处一起改：① 主镜像指向 `main`；② `master` 降级成兜底（老仓库可能反过来）。
         */
        const val GITEE_RAW_FEED_URL =
            "https://gitee.com/liulin62633/pocket-carp/raw/main/announce/feed.json"

        /** 同一个文件的 `master` 分支（历史上就是这条，留着防止哪天真反过来）。 */
        const val GITEE_RAW_FEED_URL_MASTER =
            "https://gitee.com/liulin62633/pocket-carp/raw/master/announce/feed.json"

        /**
         * 次选：Gitee 的 contents API（会把文件包成 base64 信封，[unwrapContentsEnvelope] 负责拆）。
         *
         * 留着它的理由：raw 的 302 目标域名换过（`gitee.com/.../raw/` → `raw.giteeusercontent.com`），
         * 万一哪天签名 CDN 收紧，API 这一条还能顶上来。
         */
        const val GITEE_FEED_URL =
            "https://gitee.com/api/v5/repos/liulin62633/pocket-carp/contents/announce/feed.json"

        /** 备用镜像：GitHub 仓的同一份文件（国内可能很慢，但胜在稳）。 */
        const val GITHUB_FEED_URL =
            "https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/announce/feed.json"

        /**
         * 显式带 UA。OkHttp 默认的 `okhttp/4.x` 在部分 CDN/WAF 上会被当成爬虫，
         * 而 GitHub 的 API 对**无 UA** 的请求直接 403 —— 统一成自家标识最省事。
         */
        private const val USER_AGENT = "PocketXiaoli-Android"

        /**
         * 一次 fetch 的总预算（**含**发行版正文兜底那一轮，见 [fetchFeedRacing]）。
         *
         * 2026-10-21 从 8 秒收到 4.5 秒：本机实测 Gitee raw 333~1029ms、contents API
         * 184~649ms，正常网络 1 秒内必有结果；4.5 秒还拿不到，说明 Gitee 正在限流
         * （按 IP 的 403 见过），再等下去只是让用户盯着「正在检查…」。
         */
        private const val FETCH_BUDGET_MS = 4_500L

        /**
         * 兜底那一轮至少还剩这么多预算才值得发请求 —— 预算是 4.5 秒、
         * 一个 release 页面 20~40 KB，低于这个数基本只会换来一次超时。
         */
        private const val FALLBACK_MIN_BUDGET_MS = 1_200L

        /** 连上一个镜像的时间上限；连不上就该让并行的另一个上位，而不是干等 */
        private const val CONNECT_TIMEOUT_S = 4L

        /** 读到第一个字节之后的时间上限（feed 只有 1~2 KB，6 秒够慢网了） */
        private const val READ_TIMEOUT_S = 6L

        /**
         * 老路（releases/latest）的预算。feed 那一轮已经用掉最多 4.5 秒，
         * 兜底不该再来两轮 4.5 秒（Gitee 一个、GitHub 一个）：两路并行，2.5 秒
         * 给不出结论就直接报错 —— 兜底是「万一 feed 没部署」的保险，不是主路。
         */
        private const val RELEASE_FALLBACK_BUDGET_MS = 2_500L
    }

    private val client = OkHttpClient.Builder()

        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(FETCH_BUDGET_MS, TimeUnit.MILLISECONDS)
        .build()

    sealed class Result {
        /**
         * 发现新版本。
         * @param url 下载页或 APK 直链（可能为空，调用方要兜住）
         * @param forceUpdate 低于 `minSupported`，**不许取消**
         * @param sha256 期望校验值（小写十六进制）；空 = 没有提供，跳过校验
         */
        data class Latest(
            val version: String,
            val url: String,
            val forceUpdate: Boolean = false,
            val sha256: String = "",
        ) : Result()

        /** 已是最新（或无 Release）；公告请另走 [fetchFeed] */
        data object UpToDate : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * 拉一份 feed（公告层也用它，所以是 public）。
     *
     * @return 全部镜像都拿不到时返回 null（**不是** "没有更新" —— 那是两件事）
     */
    fun fetchFeed(): AppReleaseFeed? = fetchFeedDocument()?.feed

    /**
     * 同 [fetchFeed]，但把**原文**也带出来 —— 数据层要缓存它，
     * 这样下次网络挂了还能显示上一份公告（而不是白屏）。
     */
    fun fetchFeedDocument(): FetchedFeed? = (fetchFeedResult() as? FeedFetch.Ok)?.document

    /**
     * 用户选的「更新渠道」，决定先问哪个仓。
     *
     * 不是二选一而是**排序**：选 Gitee 就在 Gitee 内部试完（raw → contents API）再放弃，
     * 选 GitHub 就先去 GitHub。之所以仍保留后面的兜底，是因为渠道之间的可用性会变
     * （2026-10-17：Gitee OpenAPI 按 IP 限流 403、GitHub raw 有 CDN 滞后），
     * 选谁都不该变成「只剩这一条路」。
     */
    private fun feedMirrors(channel: String): List<String> = preferLastWinner(when (channel) {
        CHANNEL_GITHUB -> listOf(GITHUB_FEED_URL, GITEE_RAW_FEED_URL, GITEE_FEED_URL, GITEE_RAW_FEED_URL_MASTER)
        CHANNEL_GITEE -> listOf(GITEE_RAW_FEED_URL, GITEE_FEED_URL, GITEE_RAW_FEED_URL_MASTER, GITHUB_FEED_URL)
        else -> listOf(GITEE_RAW_FEED_URL, GITEE_FEED_URL, GITEE_RAW_FEED_URL_MASTER, GITHUB_FEED_URL)
    })

    /**
     * 上次**成功拿到 feed 的那个镜像**，提到队首。
     *
     * 只影响排序、不影响集合：仍然四路并行，只是先拨的那条大概率不用重新
     * DNS/TLS 握手（实测同镜像二次请求比首次快 100~300ms）。拉到的地址可能带
     * `?t=` 破缓存参数，所以比较用 [stableKey] 去掉查询串。
     */
    @Volatile
    internal var lastWinnerUrl: String? = null

    /**
     * 非首选镜像要等多久才发车。
     *
     * uto **不等**：用户没表达偏好，这时候"谁快用谁"就是最优解，让任何一路
     * 等 450ms 都只会变慢。只有用户明确选了 Gitee 或 GitHub 时才让那个源先跑，
     * 这样"选了 GitHub"在他自己的网络里真的会更快——否则这个设置等于装饰。
     */
    internal fun headStartMs(channel: String): Long =
        if (channel == CHANNEL_AUTO) 0L else CHANNEL_HEAD_START_MS
    /** 只给测试看的镜像顺序快照：生产路径只该经 [fetchFeedRacing] 取链路。 */
    internal fun feedMirrorsForTest(channel: String): List<String> = feedMirrors(channel)
    internal fun preferLastWinner(urls: List<String>): List<String> {
        val last = lastWinnerUrl ?: return urls
        val index = urls.indexOfFirst { stableKey(it) == last }
        if (index <= 0) return urls
        return listOf(urls[index]) + urls.filterIndexed { i, _ -> i != index }
    }

    /** 去掉 `?query`，只留「哪个接口」 */
    internal fun stableKey(url: String): String = url.substringBefore('?')

    /**
     * 带诊断的版本：失败时**说清楚是哪个镜像、挂在哪一步**。
     *
     * 为什么值得多写这几十行：消费者反馈「一直显示正在检查」时，
     * 光有 null 无法区分「没网 / Gitee 返回了别的东西 / 内容不是 JSON」，
     * 而这三种的处理方式完全不同。原因会一路传到通知抽屉里给用户看。
     */
    fun fetchFeedResult(preferChannel: String = CHANNEL_AUTO): FeedFetch {
        val errors = mutableListOf<String>()
        val deadline = System.currentTimeMillis() + FETCH_BUDGET_MS

        // 第一、二镜像：仓库里的 feed.json（按用户选的渠道排先后，见 feedMirrors）
        for (url in SelfHost.feedUrls(feedMirrors(preferChannel))) {
            if (System.currentTimeMillis() > deadline) {
                errors += "总耗时超过 ${FETCH_BUDGET_MS / 1000} 秒"
                break
            }
            val body = fetchText(url, errors) ?: continue
            val text = unwrapContentsEnvelope(body)
            val parsed = AppReleaseFeedParser.parse(text)
            if (parsed == null) {
                errors += "${hostOf(url)} 的内容不是可用的 feed"
                continue
            }
            return FeedFetch.Ok(FetchedFeed(parsed, text))
        }

        // 第三镜像：发行版正文里的 `<!-- feed {...} -->`（与前两个走的是不同的接口）
        // ⚠️ 必须带上同一个 deadline：不然「镜像全挂」时用户要等两轮预算（8 秒 + 8 秒）
        feedFromReleaseComment(errors, deadline)?.let { return FeedFetch.Ok(it) }

        val reason = errors.distinct().take(3).joinToString("；")
        return FeedFetch.Failed(reason.ifBlank { "没有可用的更新地址" })
    }

    /**
     * 并行竞速版：**哪个镜像先给出可用的 feed 就用哪个**。
     *
     * 为什么值得单独写一份：串行版本碰上「第一个镜像不可达」时，用户要干等一个
     * 连接超时（4 秒）才轮到下一个。而 Gitee raw 与 GitHub raw 的耗时是两个
     * 独立随机变量（实测 0.3~0.6s vs 某些网络下根本不通），并行取最小值才是
     * 「点检查更新」该有的手感：最坏耗时 = 最慢那个镜像，而不是它们的和。
     *
     * 失败时仍返回 [FeedFetch.Failed]，原因里带上是哪几个镜像挂了。
     *
     * **用户选的渠道真的有作用**（v0.1.95）：[feedMirrors] 排在第一的那条**立刻发车**，
     * 其余镜像等 [CHANNEL_HEAD_START_MS] 再发。以前四条同时发车，"选 Gitee 还是
     * GitHub"对检查耗时**没有任何影响**（谁快谁赢），用户会感觉这个开关是装饰品。
     * 现在选了 GitHub 就让 GitHub 先跑 700ms —— 在你的网络里 GitHub 更快的话，
     * 结果就是 GitHub 那个答案；700ms 内它没回来才让 Gitee 补位，两边都不吃亏。
     *
     * @param localVersionCode 本机 versionCode；拿到一个**比本机新**的答案时不再干等
     *   满 [FRESH_GRACE_MS]（见 [pickNewest] 的 decisive），只留 [DECISIVE_GRACE_MS]
     *   收一下几乎同时到达的第二名。传 0 表示不知道，退回旧行为。
     */
    suspend fun fetchFeedRacing(
        preferChannel: String = CHANNEL_AUTO,
        bustCache: Boolean = false,
        localVersionCode: Int = 0,
    ): FeedFetch = coroutineScope {
        val startedAt = System.currentTimeMillis()
        val all = SelfHost.feedUrls(feedMirrors(preferChannel), bustCache)
        // 手动检查（bustCache）无视冷却：用户就是要现在问一次；自动轮询跳过刚失败的镜像
        val urls = if (bustCache) all else all.filterNot { mirrorCoolingDown(it) }.ifEmpty { all }
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val channel = Channel<Pair<String, FetchedFeed>>(Channel.UNLIMITED)
        val jobs = urls.mapIndexed { index, url ->
            async(Dispatchers.IO) {
                // 第一顺位（用户选的渠道）立刻发车，其余让出 700ms 的头位
                if (index > 0) delay(headStartMs(preferChannel))
                val doc = runCatching { fetchFeedAt(url, errors) }.getOrNull()
                if (doc != null) {
                    markMirrorUp(url)
                    channel.send(url to doc)
                } else {
                    markMirrorDown(url)
                }
            }
        }
        // 所有镜像都回来了就把通道关掉：`pickNewest` 见到「通道已关」会立刻收手。
        // 不关的话，哪怕已经没有第二个结果可等，它也要陪跑满 900ms 的 grace ——
        // 这是「检查更新比岱宗慢半拍」最容易被忽略的一截。
        val closer = launch {
            jobs.joinAll()
            channel.close()
        }
        val best = try {
            pickNewest(
                channel,
                FETCH_BUDGET_MS,
                FRESH_GRACE_MS,
                versionOf = { it.second.feed.versionCode },
                decisive = { localVersionCode > 0 && it.second.feed.versionCode > localVersionCode },
                decisiveGraceMs = DECISIVE_GRACE_MS,
            )
        } finally {
            closer.cancel()
            jobs.forEach { it.cancel() }
        }
        if (best != null) {
            lastWinnerUrl = stableKey(best.first)
            return@coroutineScope FeedFetch.Ok(best.second)
        }

        // 竞速全灭才退到第三镜像（不同接口，必须串行再试一次）。
        // 预算按「从进方法起算」给，所以这条兜底最坏也不会把总耗时顶到两倍。
        feedFromReleaseComment(errors, startedAt + FETCH_BUDGET_MS)?.let {
            return@coroutineScope FeedFetch.Ok(it)
        }
        val reason = errors.toList().distinct().take(3).joinToString("；")
        FeedFetch.Failed(reason.ifBlank { "没有可用的更新地址" })
    }

    /**
     * **版本最高者胜**，不是最先返回者胜。
     *
     * 手动检查时两个镜像都带 `?t=` 打缓存，但边缘节点可能一个已经回源、一个还在吐旧
     * 副本。若按"谁先回来用谁"，用户就会被旧数据骗成「已是最新版本」（本仓库真实
     * 故障）。所以第一个结果到手后再等 [FRESH_GRACE_MS]，若另一路给出更高的版本号则
     * 采用更高者。
     */
    private val FRESH_GRACE_MS = 900L

    /** 已经拿到「比本机新」的答案后，只再等这么点收尾（不再等满 grace） */
    private val DECISIVE_GRACE_MS = 350L

    /** 非首选渠道的镜像让出的头位：用户选的那个源先跑 700ms */
    private val CHANNEL_HEAD_START_MS = 450L

    /** 失败镜像的冷却窗口：这段时间内的自动轮询不再碰它 */
    private val MIRROR_COOLDOWN_MS = 5 * 60 * 1000L

    private val mirrorDownAt = Collections.synchronizedMap(mutableMapOf<String, Long>())

    private fun markMirrorDown(url: String) {
        mirrorDownAt[url] = System.currentTimeMillis()
    }

    private fun markMirrorUp(url: String) {
        mirrorDownAt.remove(url)
    }

    internal fun mirrorCoolingDown(url: String): Boolean {
        val at = mirrorDownAt[url] ?: return false
        return System.currentTimeMillis() - at < MIRROR_COOLDOWN_MS
    }

    /**
     * 并行跑 [urls]，**第一个拿到非空结果的赢**，其余立刻取消；超预算返回 null。
     *
     * 抽成泛型函数是为了能在单测里注入假的 fetch：验证「总耗时 = 最快那个」
     * 而不是「所有镜像耗时之和」这唯一重要的性质。
     */
    internal suspend fun <T : Any> raceFirstSuccess(
        urls: List<String>,
        budgetMs: Long,
        fetch: suspend (String) -> T?,
    ): T? = coroutineScope {
        val winner = CompletableDeferred<T>()
        val jobs = urls.map { url ->
            async(Dispatchers.IO) {
                val r = runCatching { fetch(url) }.getOrNull()
                if (r != null) winner.complete(r)
                r
            }
        }
        val first = withTimeoutOrNull(budgetMs) { winner.await() }
        jobs.forEach { it.cancel() }
        first
    }

    /**
     * 从竞速通道里挑**版本号最高**的那个。
     *
     * - [budgetMs] 从调用起算，第一个结果都没等到就返回 null（不抛异常）。
     * - 第一个结果到手后再等 [graceMs]，给「更慢但更新」的镜像一个机会。
     * - **通道关闭 = 所有镜像都回来了**，这时立刻收手：不存在"还会来"的结果，
     *   再等就是纯白等。
     * - [decisive] 为真（通常意味着"这个版本已经比本机新"）时，只再等
     *   [decisiveGraceMs] 收一下几乎同时到达的第二名，不再干等满 [graceMs]：
     *   反正已经有可装的版本，慢镜像多给的只是"更新的新版"，下次检查照样能发现。
     */
    internal suspend fun <T : Any> pickNewest(
        channel: ReceiveChannel<T>,
        budgetMs: Long,
        graceMs: Long,
        decisive: (T) -> Boolean = { false },
        decisiveGraceMs: Long = graceMs,
        versionOf: (T) -> Int,
    ): T? = withTimeoutOrNull(budgetMs) {
        var top: T? = null
        var topVersion = Int.MIN_VALUE
        while (true) {
            val current = top
            val wait = when {
                current == null -> budgetMs
                decisive(current) -> decisiveGraceMs
                else -> graceMs
            }
            if (wait <= 0L) break
            val got = withTimeoutOrNull(wait) { channel.receiveCatching().getOrNull() } ?: break
            val version = versionOf(got)
            if (version > topVersion) {
                top = got
                topVersion = version
            }
        }
        top
    }

    /**
     * 发行版接口的两种「有效回答」。
     *
     * 404 必须算有效：它表示仓库确实没发过 Release，语义上是「已是最新」而不是
     * 「这个源失败了」—— 不能因为另一路更慢就把它当失败继续等。
     */
    private sealed interface ReleaseProbe {
        data class Body(val json: String) : ReleaseProbe
        data object NotFound : ReleaseProbe
    }

    /** 拉单个 releases/latest；失败记原因并返回 null，让竞速换下一个源 */
    private fun probeRelease(
        http: OkHttpClient,
        url: String,
        errors: MutableList<String>,
    ): ReleaseProbe? = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
            .execute().use { resp ->
                when {
                    resp.code == 404 -> ReleaseProbe.NotFound
                    resp.isSuccessful -> resp.body?.string()?.takeIf { it.isNotBlank() }
                        ?.let(ReleaseProbe::Body)
                        ?: run { errors += "${hostOf(url)} 返回了空内容"; null }
                    else -> {
                        errors += "网络错误（HTTP ${resp.code}）"
                        null
                    }
                }
            }
    } catch (e: Exception) {
        errors += "无法连接 ${hostOf(url)}（${e.javaClass.simpleName}）"
        null
    }

    /** 拉单个 feed 镜像并解析；失败原因进 [errors] */
    private fun fetchFeedAt(url: String, errors: MutableList<String>): FetchedFeed? {
        val body = fetchText(url, errors) ?: return null
        val text = unwrapContentsEnvelope(body)
        val parsed = AppReleaseFeedParser.parse(text)
        if (parsed == null) {
            errors += "${hostOf(url)} 的内容不是可用的 feed"
            return null
        }
        return FetchedFeed(parsed, text)
    }

    /**
     * 第三镜像：从发行版正文的 `<!-- feed {...} -->` 里捞。
     *
     * @param deadline 整个检查动作的截止时刻（毫秒时间戳）。剩不到
     * [FALLBACK_MIN_BUDGET_MS] 就直接放弃 —— 宁可给用户一句「检查失败」，
     * 也不要让「两个镜像都挂了」变成 4.5 秒 + 又一轮 4.5 秒。
     */
    private fun feedFromReleaseComment(
        errors: MutableList<String>,
        deadline: Long = Long.MAX_VALUE,
    ): FetchedFeed? {
        for (url in SelfHost.updateUrls(listOf(GITEE_RELEASES_URL, GITHUB_RELEASES_URL))) {
            val left = deadline - System.currentTimeMillis()
            if (left < FALLBACK_MIN_BUDGET_MS) {
                errors += "总耗时超过 ${FETCH_BUDGET_MS / 1000} 秒，跳过发行版正文兜底"
                return null
            }
            val body = fetchText(url, errors, left) ?: continue
            val parsed = AppReleaseFeedParser.parseFeedComment(body) ?: run {
                errors += "${hostOf(url)} 里没有 feed 块"
                continue
            }
            // 正文里抽出来的没有原文，序列化一份存缓存（只为下次离线可读）
            return FetchedFeed(parsed, Gson().toJson(parsed))
        }
        return null
    }

    /** 报错里只留域名，完整 URL 太长且带 token 时会漏出去 */
    private fun hostOf(url: String): String =
        url.substringAfter("://", url).substringBefore('/')

    /**
     * 查询最新版本。挂起函数；feed 那一段内部仍是阻塞 IO，调用方要在 IO 线程上跑。
     *
     * @param currentVersion 当前 versionName（`PackageManager` 读出来的，见 `util/AppVersion`）
     * @param currentVersionCode 当前 versionCode；**传 0 表示不知道**，此时退回版本名比较
     * @param skipFeed 调用方**刚刚**已经并行试过 feed 了（例如仓库层刷新的结果就是失败），
     *   直接走 releases/latest。以前没有这个开关，等于把同一组镜像串行再请求一遍，
     *   feed + releases 各自算预算 —— 网络差时用户要等十几秒才看到一句「检查失败」。
     *
     * ⚠️ 这里**不判灰度**：灰度只在 `AppFeedView.build` 判一次（那里才知道设备的 `rolloutId`，
     * 也才知道"强更不吃灰度"）。以前这个方法带过一个 `rolloutId` 参数，但唯一的调用点
     * 从不传它（而且只在 feed 读不到时才调用，那条路上根本没有 rollout 字段）——
     * 两份判定迟早会分叉，所以删掉。
     */
    suspend fun checkLatest(
        currentVersion: String,
        currentVersionCode: Int = 0,
        skipFeed: Boolean = false,
    ): Result {
        // ── 第一层：feed.json ───────────────────────────────────────────
        if (!skipFeed) {
            fetchFeed()?.takeIf { it.hasApp }?.let { feed ->
                val hasNewer = if (currentVersionCode > 0) {
                    ReleaseGate.isNewer(feed, currentVersionCode)
                } else {
                    isNewer(feed.versionName, currentVersion)
                }
                if (!hasNewer) return Result.UpToDate
                return Result.Latest(
                    version = feed.versionName,
                    url = feed.apkUrl,
                    forceUpdate = ReleaseGate.mustUpdate(feed, currentVersionCode),
                    sha256 = feed.sha256,
                )
            }
        }

        // ── 第二层：releases/latest（老路，只比 tag）─────────────────────
        // 自建地址（SelfHost.BASE_URL）排在最前，然后是 Gitee 发行版、GitHub 发行版兜底。
        // **并行**发：串行时 Gitee 超时一次、GitHub 再超时一次，最坏 9 秒；
        // 现在整段共用 [RELEASE_FALLBACK_BUDGET_MS]，谁先给出 200/404 听谁的。
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val probeClient = client.newBuilder()
            .callTimeout(RELEASE_FALLBACK_BUDGET_MS, TimeUnit.MILLISECONDS)
            .build()
        val json = when (
            val probe = raceFirstSuccess(
                SelfHost.updateUrls(listOf(GITEE_RELEASES_URL, GITHUB_RELEASES_URL)),
                RELEASE_FALLBACK_BUDGET_MS,
            ) { url -> probeRelease(probeClient, url, errors) }
        ) {
            null -> return Result.Failed(
                errors.toList().distinct().take(2).joinToString("；")
                    .ifBlank { "无法连接更新服务器" }
            )
            // 仓库未发布 Release
            ReleaseProbe.NotFound -> return Result.UpToDate
            is ReleaseProbe.Body -> probe.json
        }
        val (tag, url, sha) = try {
            val obj = JsonParser.parseString(json).asJsonObject
            // 优先取 .apk 附件直链：Gitee 的 assets 里除了 APK 还会自动附上源码 zip/tar.gz，
            // 盲取 assets[0] 有可能把用户引到源码包上（2026-10-14 实测确认）。
            val apkAsset = runCatching {
                obj.getAsJsonArray("assets")
                    ?.map { it.asJsonObject }
                    ?.firstOrNull {
                        it.get("browser_download_url")?.asString?.endsWith(".apk", ignoreCase = true) == true
                    }
            }.getOrNull()
            val apk = apkAsset?.get("browser_download_url")?.asString
            // GitHub 的 asset 自带 digest = "sha256:…"（2026-10-17 实测），能免费拿到校验值
            val digest = apkAsset?.get("digest")?.asString?.substringAfter("sha256:", "")?.lowercase()
            Triple(
                obj.get("tag_name")?.asString ?: "",
                apk ?: obj.get("html_url")?.asString ?: "",
                digest.orEmpty(),
            )
        } catch (e: Exception) {
            return Result.Failed("更新信息解析失败")
        }
        if (tag.isBlank()) return Result.UpToDate
        return if (isNewer(tag, currentVersion)) Result.Latest(tag.removePrefix("v"), url, sha256 = sha)
        else Result.UpToDate
    }

    // ── 网络小工具 ──────────────────────────────────────────────────────

    /**
     * 取文本；失败返回 null 并把原因记进 [errors]，由调用方换下一个镜像。
     *
     * 只 catch Exception 是**故意的**：Error（OOM 之类）该冒上去，
     * 但冒上去也不会让 UI 卡住 —— 调用方 `AppFeedRepository` 兜了 Throwable。
     */
    private fun fetchText(
        url: String,
        errors: MutableList<String>,
        timeoutMs: Long? = null,
    ): String? = try {
        // 兜底那几轮要能「按剩余预算」收手：共用 client 的 callTimeout 是全预算，
        // 单借一份带短超时的 client 才不会把总耗时拖长（连接池/调度器仍是同一套）。
        val call = (if (timeoutMs != null) {
            client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        } else client).newCall(
            Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        )
        call.execute().use { resp ->
            if (resp.isSuccessful) {
                resp.body?.string()?.takeIf { it.isNotBlank() }
                    ?: run { errors += "${hostOf(url)} 返回了空内容"; null }
            } else {
                errors += "${hostOf(url)}：HTTP ${resp.code}"
                null
            }
        }
    } catch (e: Exception) {
        errors += "${hostOf(url)}：${e.javaClass.simpleName}" +
                (e.message?.take(80)?.let { "：$it" } ?: "")
        null
    }

    /**
     * 拆 Gitee contents API 的信封：`{"content":"<base64>","encoding":"base64"}` → 文件原文。
     * 不是信封（GitHub raw 的文件原文）则原样返回。
     *
     * 单测见 `AppUpdateCheckerTest.unwrapContentsEnvelope_*`。
     */
    internal fun unwrapContentsEnvelope(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return text
        val obj = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull() ?: return text
        val encoding = runCatching { obj.get("encoding")?.asString }.getOrNull()
        if (encoding != "base64") return text
        val content = runCatching { obj.get("content")?.asString }.getOrNull() ?: return text
        return runCatching {
            // MIME 解码器容忍换行 —— Gitee 的 base64 里会插 \n
            String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
        }.getOrNull() ?: text
    }

    /**
     * 版本比较（纯逻辑，可单测）：tag 比 current 新才返回 true。
     *
     * 2026-10-17 起改用 [VersionCompare]（移植自岱宗盒子）：老实现只认三段整数，
     * 遇到 `0.1.41-dev.3` 这种预发布 tag 会直接判「不是新版」，于是预览版永远收不到。
     */
    internal fun isNewer(tag: String, current: String): Boolean = VersionCompare.isNewer(tag, current)
}
