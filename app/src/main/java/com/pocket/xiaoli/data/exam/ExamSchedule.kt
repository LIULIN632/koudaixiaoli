package com.pocket.xiaoli.data.exam

import androidx.compose.runtime.Immutable

/**
 * 一条考试安排。
 *
 * ## 数据从哪来
 *
 * 教务系统**不提供**考试安排接口，考试信息通过**文件通知**下发
 * （教务通知附件的 PDF / 表格，或学院群里的图片）。
 * 所以这里的数据来自「用户把通知内容粘进来 → 解析」。
 *
 * 因此各字段都是**字符串**而非强类型（Date/LocalTime）：
 * 通知里的写法五花八门（`2026-01-15` / `1月15日` / `2026.1.15`），
 * 强行解析成时间类型会在格式不认识时丢数据。先原样保留，
 * 由展示层决定怎么排版，需要比较时再解析。
 */
@Immutable
data class ExamSchedule(
    /** 课程名，可能带课程代码（如「高等数学 A (MATH1001)」） */
    val courseName: String,
    /** 考试日期原文，如 "2026-01-15" / "1月15日" */
    val date: String = "",
    /** 考试时间原文，如 "14:00-16:00" */
    val time: String = "",
    /** 考场原文，如 "教三楼 305" / "A101" */
    val location: String = "",
    /** 座位号（部分通知才有） */
    val seat: String = "",
) {
    /** 是否有可用的时间信息（决定它能不能当"倒计时"用） */
    val hasTime: Boolean get() = date.isNotBlank()

    /** 一行摘要，用于列表展示与待办标题 */
    val summary: String
        get() = listOf(date, time, location)
            .filter { it.isNotBlank() }
            .joinToString("  ")
            .ifBlank { "时间地点见通知" }
}
