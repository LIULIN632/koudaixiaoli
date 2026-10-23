package com.pocket.xiaoli.data.electricity

/**
 * 判断烟大南校后勤缴费页的**「立即缴费」按钮在这台设备上能不能用**。
 *
 * ## 为什么需要这个判断（2026-09-30 实测结论）
 *
 * 那个缴费页（`/pms/wechat/ytdx/payItemIndex`）的内联 JS 长这样：
 *
 * ```javascript
 * function payItemCharge() {              // ← 不接受任何参数
 *     if("0" > "0"){ ...提示... return fasle; }
 *     var amount = $("#amount").val();
 *     if(re.test(amount)) {
 *         if(parseFloat(amount) + parseFloat("43.09") > 0){
 *             if("" == "1"){              // ← 服务端按状态渲染这个值
 *                 payItemCharge(amount);  // ← 调用自己，但自己不吃参数
 *                 return fasle;           // ← 于是永远到不了下面的跳转
 *             }
 *             window.location = ".../YDPost?...";   // ← 真正能缴费的那一步
 *         }
 *     }
 * }
 * ```
 *
 * 服务端把上面那个比较值的右半边渲染成 `"1"` 时，点击会进入
 * **`payItemCharge(amount)` 的自我调用 → 无限递归 → `Maximum call stack size exceeded`**，
 * 页面没有任何可见变化 —— 也就是用户报的「输金额点缴费没反应」。
 *
 * ## 后来补上的更关键一层（2026-09-30 用户实测反馈）
 *
 * 用户实际遇到的是：**输金额 → 点缴费 → 跳到了「增绑新用户」绑定页**。
 * 也就是说跳转是**发生**的，只是落点错。追下去才查清根因比递归更根本：
 *
 * - 点缴费会跳到 `YDPost`
 * - 而 `YDPost` **在我们发起的任何请求里都只返回一个空壳页**（2432 字节），
 *   页面上唯一可点的元素就是 `<a href="payItemAdd">增绑新用户</a>`
 *   —— 这正是"跳到绑定界面"的来源
 * - 已排除：会话 Cookie / 微信 UA / `Referer: servicewechat.com` /
 *   `X-Requested-With: com.tencent.mm` / GET 与 POST，**结果全部一样**
 *
 * ⇒ **服务端只在微信 openid 会话里给出支付入口**，这一层从 App 侧无法修。
 * 因此本对象的判定现在只是一个**降级提示**：用户若在 App 内自己点开了缴费页，
 * 我们提醒他"这一步要去微信"，而不是让他对着一个坏按钮反复点。
 * 正规路径见 `WeChatUrlLauncher`（电费页的「去微信缴费」）。
 *
 * ## 实测到的两种渲染（同一个账户、同一台机器）
 *
 * | 抓取 | 渲染值 | 结果 |
 * |---|---|---|
 * | 第一次 | `""`（空串）| 走 `window.location` 跳到 `YDPost` |
 * | 之后连续 7 次 | `"1"` | 落进递归死代码，**点了连跳转都没有** |
 *
 * 两种结局都到不了支付，只是失败方式不同。
 * 且**与 User-Agent / Cookie / Referer / X-Requested-With 都无关**。
 *
 * ⇒ 我们无法从这边修好它，只能**识别出来并把人送去微信**。
 * 往缴费页塞 JS 去改写它的按钮被否决：那是**金融操作页**，
 * 注入脚本一旦行为不合预期，后果比"缴不了费"严重得多。
 */
object ElectricityRechargeCapability {

    /**
     * 服务端把内联 JS 里那个比较值渲染成 `"1"` 了吗。
     *
     * ⚠️ 只认 `"1"`。其它任何值（含空串）都视为**可正常缴费** ——
     * 判错的两个方向代价不对称：
     * - 误判为「坏」→ 用户看到一句多余的提示，仍可自己点（只是白担心）
     * - 误判为「好」→ 用户反复点一个不响应的按钮，且不知道原因
     * 所以只把**确定会递归**的那一种钉死，其余一律放行。
     */
    fun isRechargeBroken(pageHtml: String?): Boolean {
        if (pageHtml.isNullOrBlank()) return false
        return INNER_BRANCH.containsMatchIn(pageHtml)
    }

    /**
     * 内联 JS 里的 `if("" == "x")`，只匹配右半边为 `"1"` 的情况。
     *
     * 用 `""` 与 `"1"` 两个字面量一起定位，避免误伤页面上其它 `== "1"` 判断。
     */
    private val INNER_BRANCH = Regex("""if\(""\s*==\s*"1"\)""")
}
