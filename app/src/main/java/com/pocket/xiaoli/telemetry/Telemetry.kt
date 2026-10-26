package com.pocket.xiaoli.telemetry

import android.content.Context
import android.os.Build
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.data.SelfHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/** 服务端白名单里的六个事件。名字是**线协议**，改一个字就等于换了一个指标。 */
enum class TelemetryEvent(val wire: String, val oncePerDay: Boolean = false) {
    AppOpen("app_open"),

    /** 更新弹窗弹出来（一天一次：反复弹只算一次曝光） */
    UpdateShown("update_shown", oncePerDay = true),

    UpdateClicked("update_clicked"),

    /** 下载完成（一天一次：同一天重复下载只算一次） */
    UpdateDownload("update_download", oncePerDay = true),

    /** 装完并打开了新版（启动时比对上次的 versionCode 得到） */
    UpdateInstall("update_install"),

    CrashOccurred("crash_occurred"),

    /**
     * 服务页某个入口被点开 —— **唯一带维度的事件**。
     *
     * 维度值是 [ServiceEntry.key]（服务标题，来自我们自己的常量表），
     * 不是用户输入、不是页面路径。详见 [featureUsed] 的说明。
     */
    FeatureUse("feature_use"),
}

/**
 * 匿名运行统计的唯一出口。
 *
 * ## 给之后改这块的人
 *
 * - **默认开**，首启告知一次；关掉之后 [track] 立即变成空操作，而且队列会被清空 ——
 *   不是"先记着回头再发"。用户在设置里关掉的那一刻，本机就不再有统计相关的数据。
 * - 没配 [SelfHost.TELEMETRY_URL] / [SelfHost.TELEMETRY_KEY] 时**连队列都不写**。
 *   所以"配置为空"和"用户关掉"在数据上是同一种状态：零。
 * - 只有七个事件名。**只有 [TelemetryEvent.FeatureUse] 带一个维度**，取值为服务标题
 *   （`ServiceEntry.key`，来自我们自己的常量表），且必须命中 [FEATURE_KEY_PATTERN]；
 *   其余事件没有任何自定义属性、没有页面路径、没有学号 —— 加字段前先读
 *   `docs/adr/0001-匿名运行统计.md` 的决策边界。
 * - 上报永远在 IO 线程、永远吞异常。统计坏了不许影响功能，这是硬约束。
 */
object Telemetry {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var store: TelemetryStore? = null

    @Volatile
    private var uploader: TelemetryUploader? = null

    /**
     * 在 `Application.onCreate` 里调一次；重复调用无副作用。
     *
     * ⚠️ F-Droid 构建（`-Pfdroid=true`）**直接不初始化**：没有 store、没有 uploader，
     * 于是 `track()` 第一行就 return —— 连队列都不会建，不是"记下来不发"。
     * 这样上架 F-Droid 时不带 Tracking 反特性，也不需要用户在设置里关一次。
     */
    fun init(context: Context) {
        if (BuildConfig.FDROID) return
        if (store != null) return
        val s = TelemetryStore(context.applicationContext)
        store = s
        uploader = TelemetryUploader(SelfHost.TELEMETRY_URL, SelfHost.TELEMETRY_KEY)
        scope.launch { onStart(s) }
    }

    // ── 设置页用的三个入口 ──────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean {
        if (BuildConfig.FDROID) return false
        return store?.enabled() ?: TelemetryStore(context.applicationContext).enabled()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val s = store ?: TelemetryStore(context.applicationContext).also { store = it }
        s.setEnabled(enabled)
        if (enabled) {
            scope.launch { drain(s) }
        } else {
            // 关掉 = 不留痕迹。已经排队的那几条也一并丢掉。
            s.clearQueue()
        }
    }

    /** 首启那条说明还没给他看过（F-Droid 版没有统计，也就不弹这条） */
    fun shouldShowNotice(context: Context): Boolean =
        !BuildConfig.FDROID && !(store ?: TelemetryStore(context.applicationContext)).noticeSeen()

    fun markNoticeShown(context: Context) {
        (store ?: TelemetryStore(context.applicationContext)).markNoticeSeen()
    }

    // ── 记账 ────────────────────────────────────────────────────────────

    fun track(event: TelemetryEvent, fromVc: Int? = null) {
        trackInternal(event, fromVc, key = null)
    }

    /**
     * 记录「某个服务入口被点开」。
     *
     * ## 为什么维度只到这里
     *
     * 需要的答案是"哪些入口有人用、哪些没人用"，所以维度只能是**入口名**。
     * 入口名来自 `ServiceEntry.key`（就是标题常量），不是用户输入、不是搜索词、
     * 不是子页里的任何内容 —— 否则就成了行为追踪。
     *
     * ## 三道闸
     *
     * 1. 必须命中 [FEATURE_KEY_PATTERN]（汉字/字母/数字/`·`/`-`/空格，≤24 字），
     *    不命中**直接丢**，连队列都不进；
     * 2. 服务端还有一层白名单校验（`EventWhitelist`），对不上整批 400；
     * 3. 用户关掉统计时 [track] 本身就是空操作。
     */
    fun featureUsed(key: String) {
        if (!isTrackableFeature(key)) return
        trackInternal(TelemetryEvent.FeatureUse, fromVc = null, key = key)
    }

    private fun trackInternal(event: TelemetryEvent, fromVc: Int?, key: String?) {
        val s = store ?: return
        val u = uploader ?: return
        if (!shouldRecord(s.enabled(), u.configured)) return

        val now = System.currentTimeMillis()
        if (event.oncePerDay && !s.firstTimeToday(event.wire, now)) return

        // 服务端只在这两个事件上接受 fromVc，其余一律不许带（带了整批 400）
        val effectiveFrom = when (event) {
            TelemetryEvent.UpdateDownload, TelemetryEvent.UpdateInstall ->
                fromVc ?: BuildConfig.VERSION_CODE
            else -> null
        }
        s.enqueue(event.toJson(now, effectiveFrom, key))
        flush()
    }

    fun flush() {
        val s = store ?: return
        scope.launch { drain(s) }
    }

    // ── 内部 ────────────────────────────────────────────────────────────

    private fun onStart(s: TelemetryStore) {
        val u = uploader ?: return
        if (!shouldRecord(s.enabled(), u.configured)) return

        val current = BuildConfig.VERSION_CODE
        val previous = s.lastVersionCode()
        // 从旧版升上来的第一次启动才算「新版启动」；全新安装 previous == 0，不算
        if (previous != 0 && previous != current) {
            track(TelemetryEvent.UpdateInstall, fromVc = previous)
        }
        s.setLastVersionCode(current)
        track(TelemetryEvent.AppOpen)
    }

    /** 循环把队列排空；一次最多五轮，剩下的留给下次启动。 */
    private fun drain(s: TelemetryStore) {
        val u = uploader ?: return
        if (!shouldRecord(s.enabled(), u.configured)) return
        repeat(5) {
            val pending = s.queued()
            if (pending.isEmpty()) return
            val batch = pending.take(TelemetryUploader.MAX_EVENTS)
            val accepted = u.upload(
                installId = s.installId(),
                versionCode = BuildConfig.VERSION_CODE,
                osVersion = Build.VERSION.SDK_INT,
                events = batch,
            )
            if (!accepted) return
            s.replaceQueue(pending.drop(batch.size))
        }
    }

    /** 纯函数：两个条件缺一个就一条都不发。 */
    internal fun shouldRecord(
    enabled: Boolean,
    configured: Boolean,
    fdroid: Boolean = BuildConfig.FDROID,
): Boolean = !fdroid && enabled && configured

    private fun TelemetryEvent.toJson(nowMs: Long, fromVc: Int?, key: String? = null): String =
        buildString {
            append("{\"n\":\"").append(wire).append('"')
            append(",\"t\":").append(nowMs)
            append(",\"eid\":\"").append(UUID.randomUUID()).append('"')
            if (fromVc != null) append(",\"fromVc\":").append(fromVc)
            if (key != null) append(",\"k\":\"").append(key).append('"')
            append('}')
        }

    /**
     * 入口名的字符集闸门。**不要放宽**：这是"维度不能携带用户内容"的唯一技术保证。
     *
     * 允许：汉字、ASCII 字母数字、`·`、`-`、空格；长度 1~24。
     * 不允许：标点符号、换行、斜杠、下划线以外的路径字符 —— 一旦放进来，
     * `k` 就能被拿来当自由文本通道，等于是给统计开了个后门。
     */
    fun isTrackableFeature(key: String): Boolean = FEATURE_KEY_PATTERN.matches(key)

    internal val FEATURE_KEY_PATTERN = Regex("^[\\p{IsHan}A-Za-z0-9·\\- ]{1,24}$")
}
