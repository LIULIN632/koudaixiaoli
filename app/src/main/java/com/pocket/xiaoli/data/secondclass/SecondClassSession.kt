package com.pocket.xiaoli.data.secondclass

/**
 * 到梦空间的**会话**：一次登录换来的东西，全是"我自己是谁"。
 *
 * ## 为什么 token 不在这个类里被"加密保存"或"另存一份"
 *
 * 真源是 WebView 的 cookie（`dmkj_web_token`），由 [SecondClassCookieJar] 持有并落盘
 * （用户手工登录 → `applyWebViewSnapshot` → 强制落盘，见那里的 KDoc）。
 * 本类只是**一次取数期间的快照**，不落自己的盘、不另存凭据 —— 架构文档 B1：
 * 不存第二套凭据。
 *
 * ## ⚠️ 读 token 必须用 [SecondClassCookieJar.valueOf]，不能用 `syncableSnapshot`
 *
 * `dmkj_web_token` 是**会话 cookie**（平台用 `$.cookie(..., {path:"/"})` 写入，没带 `expires`），
 * 而 `syncableSnapshot()` 的过滤条件 `canBePushedToWebView = !isSession && !httpOnly`
 * **恰好会把它滤掉** → 永远读到 null → 永远判"未登录"。
 * 这条坑写在 `SecondClassCookieJar.valueOf` 的 KDoc 里，[tokenFrom] 就是它的唯一调用点。
 */
data class SecondClassSession(
    /** 会话令牌。空串 = 还没登录过。 */
    val token: String = "",
    /** `checkToken` 的 `result.id`：**接口要的 `userid`**（见 §11.9 的请求契约）。 */
    val userId: String? = null,
    val schoolId: String? = null,
    /**
     * `checkToken` 的 `result.activatflag`。
     *
     * ⚠️ 平台页面在它等于 [ACTIVATE_FLAG_STUDENT_MENU_HIDDEN] 时**不渲染学生菜单**，
     * 且 `login.js` 会把用户送回首页（见 §11.9）。所以它不是"错误"，而是一个**账号未就绪**的状态。
     */
    val activateFlag: String? = null,
) {
    val hasToken: Boolean get() = token.isNotBlank()

    /**
     * 学生功能此刻是否可用 —— 用的就是平台页面自己那条判据（`activatflag != "2"`）。
     *
     * ⚠️ 字段缺失时判**可用**：拿不到这个字段就不该拦着用户，真正的结论由请求结果说话
     * （不行会回 `Unauthorized`，那才是可靠信号）。
     */
    val studentFeaturesAvailable: Boolean
        get() = activateFlag != ACTIVATE_FLAG_STUDENT_MENU_HIDDEN

    companion object {
        /** 会话 cookie 名（实测自 `login.js`）/ 可作 `?token=` 查询参数。 */
        const val TOKEN_COOKIE = "dmkj_web_token"

        /** 平台自己的"学生菜单不渲染"取值（实测自首页内联的 `headerTmpl`）。 */
        const val ACTIVATE_FLAG_STUDENT_MENU_HIDDEN = "2"

        /** 从 jar 里取此刻可用的 token。**必须**走 [SecondClassCookieJar.valueOf]（见类 KDoc）。 */
        fun tokenFrom(jar: SecondClassCookieJar): String? = jar.valueOf(TOKEN_COOKIE)

        /**
         * 解析 `token/checkToken` 的响应。
         *
         * 成功形状（字段名实测自 `index.js` / `login.js` 的消费代码）：
         * `{"result": {"id": …, "schoolid": …, "activatflag": …}}`
         * —— 注意成功响应里**没有 `code`**（见 §11.8）。
         *
         * ⚠️ [SecondClassResult.Unauthorized] 必须原样上抛（走 [mapValidated]），
         * 不许在这里变成一句"登录失败"：UI 要靠它决定"显示重新登录"还是"显示加载失败"。
         */
        fun parseCheckToken(body: String?, token: String): SecondClassResult<SecondClassSession> =
            SecondClassEnvelope.validate(body).mapValidated { root ->
                val result = root.objOrNull("result")
                    ?: return@mapValidated SecondClassResult.Failed(
                        null,
                        "登录响应里没有 result 字段（到梦空间改过协议？）",
                    )
                SecondClassResult.Ok(
                    SecondClassSession(
                        token = token,
                        userId = result.stringOrNull("id"),
                        schoolId = result.stringOrNull("schoolid"),
                        activateFlag = result.stringOrNull("activatflag"),
                    ),
                )
            }
    }
}
