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
 * - 只有六个事件名，没有自定义属性、没有页面路径、没有学号 —— 加字段前先读
 *   `docs/adr/0001-匿名运行统计.md` 的决策边界。
 * - 上报永远在 IO 线程、永远吞异常。统计坏了不许影响功能，这是硬约束。
 */
object Telemetry {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var store: TelemetryStore? = null

    @Volatile
    private var uploader: TelemetryUploader? = null

    /** 在 `Application.onCreate` 里调一次；重复调用无副作用。 */
    fun init(context: Context) {
        if (store != null) return
        val s = TelemetryStore(context.applicationContext)
        store = s
        uploader = TelemetryUploader(SelfHost.TELEMETRY_URL, SelfHost.TELEMETRY_KEY)
        scope.launch { onStart(s) }
    }

    // ── 设置页用的三个入口 ──────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean =
        store?.enabled() ?: TelemetryStore(context.applicationContext).enabled()

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

    /** 首启那条说明还没给他看过 */
    fun shouldShowNotice(context: Context): Boolean =
        !(store ?: TelemetryStore(context.applicationContext)).noticeSeen()

    fun markNoticeShown(context: Context) {
        (store ?: TelemetryStore(context.applicationContext)).markNoticeSeen()
    }

    // ── 记账 ────────────────────────────────────────────────────────────

    fun track(event: TelemetryEvent, fromVc: Int? = null) {
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
        s.enqueue(event.toJson(now, effectiveFrom))
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
    internal fun shouldRecord(enabled: Boolean, configured: Boolean): Boolean = enabled && configured

    private fun TelemetryEvent.toJson(nowMs: Long, fromVc: Int?): String = buildString {
        append("{\"n\":\"").append(wire).append('"')
        append(",\"t\":").append(nowMs)
        append(",\"eid\":\"").append(UUID.randomUUID()).append('"')
        if (fromVc != null) append(",\"fromVc\":").append(fromVc)
        append('}')
    }
}
