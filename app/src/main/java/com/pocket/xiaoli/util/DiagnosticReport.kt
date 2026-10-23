package com.pocket.xiaoli.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 诊断报告的输入（全部由调用方采集，本类只负责排版与脱敏，便于单测） */
data class DiagnosticInput(
    val appVersionName: String,
    val appVersionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val deviceModel: String,
    val dbVersion: Int,
    val courseCount: Int,
    val schemeCount: Int,
    val timeSlotCount: Int,
    val homeworkCount: Int,
    val taskCount: Int,
    val savedGradeCount: Int,
    val roomCount: Int,
    val poiCount: Int,
    val recentError: String? = null,
)

/**
 * 诊断报告生成。
 *
 * ## 为什么做脱敏
 * 报告是给开发者看的、要用户主动复制发出来的。原文里可能夹带学号、设备路径等，
 * 所以生成时**统一做一遍脱敏**，而不是指望用户自己删。
 *
 * 纯函数，无 Android 依赖，可单测。
 */
object DiagnosticReport {

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 连续 7 位以上数字：学号 / 身份证 / 手机号 / 超长 id */
    private val LONG_DIGITS = Regex("""\d{7,}""")

    /** 绝对路径：Android 与 Windows 两种形态 */
    private val ABS_PATHS = Regex("""(/data|/storage|/sdcard)/[^\s:'"]+|[A-Za-z]:\\[^\s:'"]+""")

    /** email 只保留域名前的首字母 */
    private val EMAILS = Regex("""([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+)""")

    /** 对任意文本做脱敏；错误摘要等自由文本都应先过这里 */
    fun redact(text: String): String = text
        .replace(LONG_DIGITS, "***")
        .replace(ABS_PATHS, "<path>")
        .replace(EMAILS) { m -> m.groupValues[1] + "***" + m.groupValues[2] }

    /**
     * 生成报告正文。
     * @param now 便于测试注入固定时间
     */
    fun build(input: DiagnosticInput, now: LocalDateTime = LocalDateTime.now()): String {
        val error = input.recentError
            ?.takeIf { it.isNotBlank() }
            ?.let { redact(it).take(1500) }
            ?: "（无）"

        return buildString {
            appendLine("── 口袋小鲤 · 诊断报告 ──")
            appendLine("生成时间：${now.format(TIME)}")
            appendLine("（已自动脱敏：学号等长数字、文件路径、邮箱）")
            appendLine()
            appendLine("【应用】")
            appendLine("  版本：v${input.appVersionName} (${input.appVersionCode})")
            appendLine("  数据库版本：v${input.dbVersion}")
            appendLine()
            appendLine("【设备】")
            appendLine("  Android：${input.androidRelease} (API ${input.sdkInt})")
            appendLine("  机型：${redact(input.deviceModel)}")
            appendLine()
            appendLine("【数据量】")
            appendLine("  课程：${input.courseCount}（方案 ${input.schemeCount} 个）")
            appendLine("  时间段：${input.timeSlotCount}")
            appendLine("  待办作业：${input.homeworkCount}")
            appendLine("  任务：${input.taskCount}")
            appendLine("  成绩存档：${input.savedGradeCount}")
            appendLine("  教室：${input.roomCount}")
            appendLine("  POI：${input.poiCount}")
            appendLine()
            appendLine("【最近错误】")
            appendLine(error)
        }
    }
}