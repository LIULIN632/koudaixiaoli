package com.pocket.xiaoli.focus

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 番茄钟的**真源**：正在跑的那一段 + 用户的时长配置。
 *
 * ## 为什么是 SharedPreferences 快照，而不是 Room
 *
 * "正在跑的番茄钟"这个数据的特征与 Room 恰好相反：
 *
 * | | 它需要什么 | Room 给什么 |
 * |---|---|---|
 * | 读写频率 | 每次开始/暂停/继续/切段各一次，一天几十次 | 事务 + 编译过的语句，为"高频结构化查询"设计 |
 * | 生命周期 | 进程被杀要能立刻读回来 | 打开库要走迁移链，冷启动更慢 |
 * | 查询需求 | 只读**一条**，从不按条件筛 | JOIN / WHERE / ORDER BY 全用不上 |
 *
 * 而真正需要统计的那些（"今天几个番茄""这周每天多久"）都是**已经结束的段**，
 * 天然属于 `focus_sessions` 表。所以这里的分工是：
 * **瞬态只读一条 → 快照；历史要聚合 → Room。**
 *
 * ## 为什么快照里要存两个截止时间
 *
 * [FocusRuntime.deadlineElapsedMs] 是日常用的（抗改时间），
 * [FocusRuntime.deadlineWallClockMs] 只在**冷路径**重建基准用 ——
 * 单调时钟跨不过重启。两者分工的完整说明在 `FocusEngine.rebase`。
 *
 * ## 为什么用 StateFlow 而不是"读属性"
 *
 * `SharedPreferences` 的 getter **不参与 Compose 快照系统**：改了它不会触发重组
 *（这个坑在本项目的会话列表与底栏样式上各踩过一次，见 `ProactiveStore` 的 KDoc）。
 * 页面要即时反映"服务那边刚把这一段跑完了"，就必须有一个 Flow。
 * 同一个进程里 Service 与 Activity 共用这一个实例，所以内存里的 Flow 就是权威镜像；
 * 跨进程不存在（服务没跑在独立进程）。
 *
 * ⚠️ **写入一律走 [edit]，不许别处再 `prefs.edit()` 一遍** ——
 * 那样 Flow 就不会更新，服务改了状态而页面不知道。
 */
class FocusStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _runtime = MutableStateFlow(readRuntime())
    /** 当前这一段；`null` = 没有会话（空态）。 */
    val runtime: StateFlow<FocusRuntime?> = _runtime

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<FocusConfig> = _config

    // ── 会话快照 ──

    /**
     * 从磁盘现读一次。
     *
     * ⚠️ `runCatching` 之外还要判 [looksUsable]：Gson 遇到结构对了但内容离谱的 JSON
     *（上个版本写进去的、字段全是 0）不会抛异常，只会给回一个 `plannedMs = 0` 的壳。
     * 那壳流进内核就是"进度恒为 0、永远不过期"的一段。**读不出来就当没有会话**，
     * 不拿默认值猜一段出来（同校历那条"读不出来就空态"的取向）。
     */
    fun readRuntime(): FocusRuntime? {
        val raw = prefs.getString(KEY_RUNTIME, null)?.takeIf { it.isNotBlank() } ?: return null
        val parsed = runCatching { gson.fromJson(raw, FocusRuntime::class.java) }.getOrNull() ?: return null
        return parsed.takeIf { it.looksUsable() }
    }

    /**
     * 写下这一段；传 `null` 表示会话结束（没有会话就没有快照，不留半截记录）。
     *
     * 用 `commit()` 而不是 `apply()`：这个方法会在**服务被系统回收的瞬间**被调
     *（`onDestroy` 里做最后一次落盘），而 `apply()` 只是排进内存队列、由后台线程慢慢写 ——
     * 进程随后被杀，这次写入就丢了。整段会话的状态只有几十字节，这一下的同步开销可以接受。
     */
    fun edit(runtime: FocusRuntime?) {
        if (runtime == null) {
            prefs.edit().remove(KEY_RUNTIME).commit()
        } else {
            prefs.edit().putString(KEY_RUNTIME, gson.toJson(runtime)).commit()
        }
        _runtime.value = runtime
    }

    /** 内存镜像（不碰磁盘）。给"刚刚已经在别处写过盘"的调用点用，避免重复序列化。 */
    fun publish(runtime: FocusRuntime?) {
        _runtime.value = runtime
    }

    private fun FocusRuntime.looksUsable(): Boolean = plannedMs > 0L

    // ── 配置 ──

    fun readConfig(): FocusConfig {
        val raw = prefs.getString(KEY_CONFIG, null)?.takeIf { it.isNotBlank() } ?: return FocusConfig.DEFAULT
        val parsed = runCatching { gson.fromJson(raw, FocusConfig::class.java) }.getOrNull()
        return (parsed ?: FocusConfig.DEFAULT).sanitized()
    }

    fun setConfig(config: FocusConfig) {
        val clean = config.sanitized()
        prefs.edit().putString(KEY_CONFIG, gson.toJson(clean)).apply()
        _config.value = clean
    }

    /** 恢复出厂：把番茄钟的全部本地状态清掉（供「我的 → 一键清空」调用）。 */
    fun clear() {
        prefs.edit().remove(KEY_RUNTIME).remove(KEY_CONFIG).commit()
        _runtime.value = null
        _config.value = FocusConfig.DEFAULT
    }

    companion object {
        /**
         * 独立文件，不塞进主 `pocket_xiaoli_prefs`。
         *
         * 理由同 `ProactiveStore`：这几个键只有番茄钟用，而且"没有会话"与"有会话"
         * 是两种完全不同的状态，独立文件让"清掉番茄钟状态"变成一次 `clear()`，
         * 不必去主 prefs 里逐个 remove（漏一个就会在下次起钟时诈尸）。
         */
        private const val PREFS = "pocket_focus"

        /** 正在跑的那一段的快照。 */
        private const val KEY_RUNTIME = "focus_runtime"

        /** 时长配置。 */
        private const val KEY_CONFIG = "focus_config"
    }
}
