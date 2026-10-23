package com.pocket.xiaoli.data.secondclass

/**
 * 第二课堂数据源 —— **能力边界写在这里**。
 *
 * 这个接口只有三个**读**方法，没有 `enroll()` / `cancel()` / `signIn()` / `submit()`。
 * 这不是「暂时没写」，是刻意让**能力在类型系统里不存在**（架构文档 §1 B3/B5）：
 * 后来者想加报名或签到，必须先改这个接口 —— 那一步就是有意设计的摩擦点，
 * 比在注释里写「请勿添加写操作」有用得多。
 *
 * 与此同时刻意不存在的还有（B1/B2/B4/B6/B7）：
 * - 没有多账号 / 账号池 / `List<Account>` 参数（只服务用户本人账号）；
 * - 没有验证码识别入口（验证码一律交 WebView 让用户手输）;
 * - 没有凭据导入导出、没有「帮朋友查」的他人账号参数；
 * - 没有定时轮询语义（单用户、低频，不做并发预热）。
 *
 * ### 为什么返回 [SecondClassResult] 而不是裸类型
 * 架构文档 §4 的代码片段把签名写成了 `suspend fun fetchActivities(...): List<SecondClassActivity>`，
 * 但同一节紧接着论证「`Unauthorized` 要独立成一支，否则 UI 只会显示『加载失败』」。
 * 两者不能同时成立：裸类型要表达「登录态失效」就只能靠抛异常，而异常又会被 UI 收敛成
 * 笼统的「加载失败」——恰恰是那段论证要避免的结果。**所以这里按论证取 `SecondClassResult`**，
 * 裸签名视为文档的简写。这一处偏差已回报给维护者，若文档要改，改的是 §4 的片段。
 *
 * ### 实现者必须遵守
 * - 每次网络请求前**必须**经过共享的 [SecondClassRateLimiter]（B8 的执行点，不是建议）；
 * - 会话失效一律返回 [SecondClassResult.Unauthorized]，**不要**在实现里做重登；
 * - 有自己的 `OkHttpClient`（CookieJar / 超时 / 限流策略与教务、图书馆完全不同，共用会互相污染 Cookie）。
 */
interface SecondClassDataSource {

    /**
     * 活动列表分页。页码从 1 开始。
     *
     * ⚠️ 返回 [SecondClassPage] 而**不是** `List`：实测的响应自带 `records`（总条数）、
     * `total`（总页数）、`page`（回显页码）（见架构文档 §11.8）。只回一个 `List` 会把分页信息
     * 丢在半路，UI 就没法画"第 2 / 共 5 页"，也没法判断还有没有下一页 ——
     * 而 `total` 与"按 `records/rows` 自己算"是**可以互相印证**的（那是 §11.8 的一条实测结论）。
     * 这是对架构文档 §4 那段签名的**第二处修正**（第一处是整体改用 [SecondClassResult]，理由见下）。
     */
    suspend fun fetchActivities(page: Int, pageSize: Int): SecondClassResult<SecondClassPage<SecondClassActivity>>

    suspend fun fetchActivityDetail(activityId: String): SecondClassResult<SecondClassActivityDetail>

    suspend fun fetchTranscript(): SecondClassResult<SecondClassTranscript>

    companion object {
        /** 实测的服务端默认页大小（§11.8：20 行 × `records:"88"` ⇒ `total:"5"`）。 */
        const val DEFAULT_PAGE_SIZE = 20
    }
}
