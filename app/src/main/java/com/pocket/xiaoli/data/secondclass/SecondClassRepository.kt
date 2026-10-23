package com.pocket.xiaoli.data.secondclass

/**
 * 第二课堂的 Repository —— **UI 唯一能碰的数据入口**（架构文档 §2.3）。
 *
 * 职责边界：
 *
 * | 它做什么 | 它不做什么 |
 * |---|---|
 * | 把三个查询转给 [SecondClassDataSource] | **不发 HTTP**（那是 DataSource 的事） |
 * | **会话失效的收敛**（见下） | 不解析 JSON、不映射 DTO |
 * | 将来：缓存决策、分页组装 | 不知道界面长什么样 |
 *
 * ### ⚠️ 会话失效为什么**必须**收敛在这里
 *
 * 架构文档 §2.3 原话：`Unauthorized` → 清会话 → 发 `NeedLogin`，**不散落到各 DataSource**，
 * 否则 A/B 两条链路的失效行为会漂移。但真正让它必须有一个**唯一出口**的理由更实际：
 *
 * 登录态静默过期时，cookie 还在 jar 里 —— 于是 [SecondClassCookieJar.hasCookiesFor] 一直说
 * "我有 cookie"、UI 一直以为已登录、每次查询都失败，用户看到的是一页**反复的"加载失败"**，
 * 而**没有任何一处告诉他要去重新登录**。清掉会话就是打破这个循环的那一下。
 *
 * 所以本类的三个查询方法**全部**经过 [converge]，没有旁路。
 *
 * ### 为什么清会话要"只清一次"
 *
 * UI 在失败后通常会允许用户点"重试"。若每次 401 都清一次盘（`jar.clear()` 会连带写存储），
 * 连点几次就是几次无谓的写。所以用一个闩锁：[SecondClassResult.Unauthorized] 只触发一次回调，
 * **直到有一次成功**（说明会话又好了）才重新武装。
 *
 * ⚠️ 这个闩锁是普通 `var`，不是线程安全的。前提是调用方（ViewModel）**串行**地发起查询 ——
 * 那是本项目既有 ViewModel 的用法（单协程收集）。要改成并发调用，先把它换成原子操作。
 *
 * @param onSessionInvalidated 会话失效时调用。**必须幂等**（默认实现清 jar，本身就是幂等的；
 *   WebView 那一侧的清理由 `WebViewCookieSync` 负责，两处都要清，缺一处就是"退出了但还登着"）。
 */
class SecondClassRepository(
    private val dataSource: SecondClassDataSource,
    private val onSessionInvalidated: () -> Unit = {},
) {

    /** 会话是否已被判定失效（闩锁：失效只清一次，直到再次成功）。 */
    private var sessionInvalidated = false

    suspend fun transcript(): SecondClassResult<SecondClassTranscript> =
        converge(dataSource.fetchTranscript())

    suspend fun activities(
        page: Int,
        pageSize: Int = SecondClassDataSource.DEFAULT_PAGE_SIZE,
    ): SecondClassResult<SecondClassPage<SecondClassActivity>> =
        converge(dataSource.fetchActivities(page, pageSize))

    suspend fun activityDetail(activityId: String): SecondClassResult<SecondClassActivityDetail> =
        converge(dataSource.fetchActivityDetail(activityId))

    /**
     * 会话失效的**唯一收敛点**。
     *
     * 注意它**不改变**返回值：`Unauthorized` 原样交给 UI —— 由 UI 决定显示
     * "请重新登录到梦空间"（而不是"加载失败"）。这里只负责把本机那个已经没用的会话清掉。
     */
    private fun <T> converge(result: SecondClassResult<T>): SecondClassResult<T> {
        when (result) {
            is SecondClassResult.Unauthorized -> if (!sessionInvalidated) {
                sessionInvalidated = true
                // 回调失败不该把一次"未登录"升级成崩溃：清理是尽力而为。
                runCatching { onSessionInvalidated() }
            }

            is SecondClassResult.Ok -> sessionInvalidated = false

            // 限流与其它失败都不代表会话坏了：**不动**闩锁，
            // 否则一次网络抖动会让下一次 401 少清一次会话。
            is SecondClassResult.RateLimited, is SecondClassResult.Failed -> Unit
        }
        return result
    }
}
