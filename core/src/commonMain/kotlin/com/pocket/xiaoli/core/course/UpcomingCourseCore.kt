package com.pocket.xiaoli.core.course

/**
 * 「下一节课」挑选的输入形状。
 *
 * 为什么不直接收课程实体：两端的实体字段名不同（`CourseEntity.startSlot/endSlot` vs 桌面
 * `Course` + 节次表），把实体搬进共享层等于把一端的存储模型变成另一端的负担。这里只保留
 * **判定需要的四个数**，时间由调用方补齐。
 *
 * @param dayOfWeek 1=周一 … 7=周日（与 `java.time.DayOfWeek.value`、本项目实体同口径）
 * @param startSlot 起始节次，仅用于同一天多条课时排序
 * @param startMinute 从 0 点起的分钟数；-1 表示节次表里查不到
 * @param endMinute 同上；<=0 表示未知 —— 与 Android `HomeCourse.isFinished` 同口径：
 *   **下课时间未知一律算「还没下课」**，宁可多显示一条也不要漏掉即将开始的课
 * @param occursThisWeek 调用方用共享的周次规则（`weekSet`/`isCourseInWeek`）预先算好
 */
data class UpcomingCandidate(
    val dayOfWeek: Int,
    val startSlot: Int,
    val startMinute: Int,
    val endMinute: Int,
    val occursThisWeek: Boolean,
)

/** 选中结果：命中候选在输入里的下标 + 差几天（0=今天，1=明天 … 7）。 */
data class UpcomingPick(val index: Int, val dayOffset: Int)

/**
 * 挑「下一节课」。
 *
 * 规则（两端原本各写一份，现在只在这里写）：
 * 1. 先看**今天**：周次命中、且还没下课的课里，取起始节次最早的那条；今天是放假日直接跳过今天。
 * 2. 今天没有，就按 1..7 天往后扫，跳过放假日，命中第一个有课的日子。
 * 3. 都没有返回 null（一周内没课 / 周次非法由调用方先拦）。
 *
 * @param isOffDay 按「差几天」判断是否放假；调用方把法定假期/周末口径注入进来
 *   （Android 用 `HolidayCalendar`，桌面默认周六日）
 */
fun pickUpcomingCourse(
    candidates: List<UpcomingCandidate>,
    todayDayOfWeek: Int,
    nowMinute: Int,
    isOffDay: (dayOffset: Int) -> Boolean = { false },
): UpcomingPick? {
    fun sortedOf(dayOfWeek: Int): List<Pair<Int, UpcomingCandidate>> =
        candidates.withIndex()
            .filter { (_, c) -> c.occursThisWeek && c.dayOfWeek == dayOfWeek }
            .map { (index, c) -> index to c }
            .sortedBy { (_, c) -> c.startSlot }

    if (!isOffDay(0)) {
        val notFinished = sortedOf(todayDayOfWeek)
            .firstOrNull { (_, c) -> c.endMinute <= 0 || nowMinute < c.endMinute }
        if (notFinished != null) return UpcomingPick(notFinished.first, 0)
    }

    for (offset in 1..7) {
        if (isOffDay(offset)) continue
        val dayOfWeek = ((todayDayOfWeek - 1 + offset) % 7) + 1
        val hit = sortedOf(dayOfWeek).firstOrNull()
        if (hit != null) return UpcomingPick(hit.first, offset)
    }
    return null
}
