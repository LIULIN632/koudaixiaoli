package com.pocket.xiaoli.ui.zhengfang

/**
 * 「绑定账号自动登录」的纯决策逻辑：
 * 只在「已绑定账号 + 未登录 + 未在登录中 + 还没自动试过」时触发一次，
 * 避免失败后无限重试（撞库观感 / 账号被锁）。
 *
 * 抽成纯函数以便单测，不引入 Android / 网络依赖。
 */
object BoundLogin {

    fun shouldAutoLogin(
        alreadyTriggered: Boolean,
        loggedIn: Boolean,
        loading: Boolean,
        hasSchool: Boolean,
        hasCredential: Boolean,
    ): Boolean = !alreadyTriggered && !loggedIn && !loading && hasSchool && hasCredential

    /**
     * 学号脱敏（用于 UI 展示「使用的是哪个账号」）。
     *
     * ⚠️ 实现已收敛到 [com.pocket.xiaoli.util.maskStudentId] —— 这里**只是保留原 API**，
     * 好让既有调用点与单测不动。全仓曾有 5 份实现、3 种规则，同一个学号在两个页面
     * 被遮成不同样子（详见那个文件的说明）。**不要再在别处抄一份脱敏。**
     */
    fun maskStudentId(value: String): String = com.pocket.xiaoli.util.maskStudentId(value)
}
