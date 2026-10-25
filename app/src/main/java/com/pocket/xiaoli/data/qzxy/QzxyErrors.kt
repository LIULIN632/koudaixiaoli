package com.pocket.xiaoli.data.qzxy

/**
 * 趣智接口错误的**语义分类**。
 *
 * 背景见 `docs/QzxyShower.md`：趣智不支持多设备同时在线，在别处登录会让当前
 * `loginCode` 失效，错误信息里通常带「登录 / token / 失效」或 HTTP 401/403。
 *
 * 这份分类只回答一件事：**这次失败是不是"登录失效"**。
 * 它决定界面显示什么 —— 以前所有失败都被当成"未签约"，用户看到的是
 * 「未签约免密支付」，而真正的原因是账号在别处被挤掉，重登一次就好了。
 *
 * ⚠️ 只是**客户端判据**：服务端没有稳定的错误码文档，所以宁可漏判也不误判成其他语义；
 * 判不出来时仍走原来的通用失败文案。
 */
object QzxyErrors {

    private val AUTH_HINTS = listOf(
        "登录",
        "token",
        "失效",
        "未授权",
        "unauthorized",
        "expired",
        "login",
    )

    fun isAuthExpired(message: String?): Boolean {
        val text = message?.trim()?.lowercase().orEmpty()
        if (text.isBlank()) return false
        return AUTH_HINTS.any { it in text }
    }

    /**
     * 开阀失败是不是因为「还没预约」。
     *
     * 官方流程要求先预约（选澡堂 → 选浴位 → 预约），不预约服务端会锁设备；
     * 预约接口没有公开（见 `docs/QzxyShower.md` §4.7），所以这里只做**识别 + 引导**
     * 去官方 App，绝不假装本机能替用户预约。
     */
    fun needsBooking(message: String?): Boolean =
        !message.isNullOrBlank() && "预约" in message
}
