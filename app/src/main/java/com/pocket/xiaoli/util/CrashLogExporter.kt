package com.pocket.xiaoli.util

import android.app.Application
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃/诊断日志导出器 —— 保证**导出永远有内容**。
 *
 * ## 修的是什么问题
 *
 * 原实现（`ProfileScreen` 的 `crashExportLauncher`）：
 *
 * ```kotlin
 * val logFile = app.crashHandler.getLatestCrashLog()
 * if (logFile != null && logFile.exists()) { 写文件 }   // ← 没崩溃时整个 if 跳过
 * // 之后无条件提示「崩溃日志已导出」
 * ```
 *
 * 从没崩溃过的用户点导出，会得到一个**空文件**外加一句"已导出"——
 * 既拿不到任何信息，也不知道为什么是空的。
 *
 * ## 现在的策略
 *
 * 导出内容按「有没有崩溃过」分两种，但**都不会为空**：
 *
 * | 情况 | 导出内容 |
 * |---|---|
 * | 有崩溃记录 | 完整堆栈 + 诊断报告 + 网络日志 |
 * | 没有崩溃记录 | 明确说明"本次会话未记录到崩溃" + 诊断报告 + 网络日志 |
 *
 * 后者对排查**非崩溃类问题**（卡顿、闪退但被系统吞掉、功能异常）同样有用 ——
 * 所以不把它当成错误路径，而是正常输出。
 *
 * ## 脱敏的口径
 *
 * 复用 [DiagnosticReport.redact] 处理**用户数据**（长数字 id、真实文件路径、邮箱），
 * 但堆栈里的 **类名与行号必须保留** —— 那是定位问题的唯一线索。
 * 两者不冲突：`redact` 只动 `/data/...` 这类绝对路径，不会碰 Kotlin 类名。
 */
object CrashLogExporter {

    private val STAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val HUMAN_TIME = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** 导出文件名（给系统"保存到…"对话框用的默认名） */
    fun defaultFileName(): String = "xiaoli_log_${STAMP.format(Date())}.txt"

    /**
     * 组装导出正文。
     *
     * @param reportBody 由调用方用 [DiagnosticReport.build] 生成的诊断正文
     *                   （调用方持有 AppContainer，能取到各项数据量）
     * @param crashLogFile 最近一次崩溃日志；为 null 表示本次会话没崩过
     * @param networkLogs 内存中的网络日志行（可为空）
     */
    fun build(
        reportBody: String,
        crashLogFile: File?,
        networkLogs: List<String>,
    ): String = buildString {
        appendLine("════════════════════════════════════════")
        appendLine("  口袋小鲤 · 日志导出")
        appendLine("════════════════════════════════════════")
        appendLine("导出时间：${HUMAN_TIME.format(Date())}")
        appendLine("设备：${Build.MODEL} (${Build.MANUFACTURER})")
        appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()

        // ── 一、崩溃堆栈（若有）──
        appendLine("【一、崩溃记录】")
        val crashText = readCrashLog(crashLogFile)
        if (crashText != null) {
            appendLine("来源：${crashLogFile?.name}")
            appendLine()
            appendLine(crashText)
        } else {
            // 不是错误，而是正常情况 —— 明确说出来，避免用户以为导出坏了
            appendLine("本次未记录到崩溃。")
            appendLine("（应用只保留「应用内捕获的未捕获异常」。若 App 是被系统直接杀掉（如低内存）、")
            appendLine("  或崩溃发生在原生代码中，这里不会有记录。）")
        }
        appendLine()

        // ── 二、诊断报告 ──
        appendLine("【二、诊断报告】")
        appendLine(reportBody)
        appendLine()

        // ── 三、网络日志 ──
        appendLine("【三、最近网络请求】")
        if (networkLogs.isEmpty()) {
            appendLine("（本次会话无记录。网络日志存在内存中，应用重启即清空。）")
        } else {
            networkLogs.forEach { appendLine(it) }
        }
        appendLine()
        appendLine("════════════════════════════════════════")
        appendLine("  导出结束")
        appendLine("════════════════════════════════════════")
    }

    /**
     * 读取崩溃日志正文。
     *
     * 做**保守脱敏**：只屏蔽长数字串（可能是学号/身份证），
     * **保留类名、方法名、行号与异常类型** —— 那是定位问题的唯一线索。
     * 不用 [DiagnosticReport.redact] 的路径规则，因为堆栈里出现路径是正常的、
     * 且对判断"崩在文件读写还是 UI"有参考价值。
     */
    private fun readCrashLog(file: File?): String? {
        if (file == null || !file.exists() || file.length() == 0L) return null
        return try {
            file.readText(Charsets.UTF_8)
                .replace(Regex("""\d{11,}"""), "***")
                .take(60_000) // 极端情况下防止超大文件撑爆分享
        } catch (_: Exception) {
            null
        }
    }
}
