package com.pocket.xiaoli.data.learning

import android.content.Context
import android.content.SharedPreferences

/**
 * 学习通「作业列表」链接缓存。
 *
 * 为什么要缓存：作业列表接口要 `enc` / `stuenc` 两个签名参数，本项目**不逆向签名算法**，
 * 而是从课程主页 HTML 里现取那条带签名的 `getAllWork` 链接（见 [ChaoxingClient]）。
 * 代价是每门课每次同步都要先多请求一次课程主页 —— 10 门课就是 20 次串行请求，
 * 用户体感就是「同步作业要转很久」。
 *
 * 而这两个参数实测**按（课程, 班级, 学生）跨会话稳定**（社区实现 chaoxing-mcp 的做法与此一致：
 * 它干脆让人手动抄一次 enc 存起来长期复用）。既然稳定，就可以把「解析出来的完整 URL」缓存下来，
 * 下次直接用；拿到 403/解析不到再回退去重新解析。
 *
 * 边界：只存 URL（其中含课程 ID 与签名），存本机、不上传、登出时清空；
 * 有效期 6 小时 —— 签名参数虽然稳定，但请求里还带着时间戳与 `ut`，隔太久容易失效，
 * 与其缓存 30 天然后偶发失败，不如短一些、失败时自动回退。
 */
object ChaoxingWorkLinks {

    private const val PREFS = "chaoxing_work_links"
    private const val TTL_MS = 6L * 60 * 60 * 1000

    private var prefs: SharedPreferences? = null

    /** 在 Application 启动时调用一次；没初始化时缓存退化为"不缓存"，功能不受影响。 */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 课程的唯一键：同一门课在不同班级下作业列表不同，所以班级要进键。 */
    fun key(courseId: String, clazzId: String, cpi: String): String = "$courseId|$clazzId|$cpi"

    fun get(courseKey: String, now: Long = System.currentTimeMillis()): String? {
        val p = prefs ?: return null
        val url = p.getString(courseKey, null) ?: return null
        val at = p.getLong("at:$courseKey", 0L)
        if (url.isBlank() || now - at > TTL_MS) return null
        return url
    }

    fun put(courseKey: String, url: String, now: Long = System.currentTimeMillis()) {
        val p = prefs ?: return
        if (url.isBlank()) return
        p.edit().putString(courseKey, url).putLong("at:$courseKey", now).apply()
    }

    /**
     * 丢掉一门课的缓存。
     *
     * 什么时候必须调：用缓存链接去请求，拿回来的不是作业列表（403/登录页/改版）。
     * 这种条目留着只会让下一次同步再白等一次失败请求 —— 实测"一次失败"在弱网下要两三秒。
     */
    fun remove(courseKey: String) {
        prefs?.edit()?.remove(courseKey)?.remove("at:$courseKey")?.apply()
    }

    /** 登出学习通时调用：缓存里是带签名的个人链接，不该留着。 */
    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
