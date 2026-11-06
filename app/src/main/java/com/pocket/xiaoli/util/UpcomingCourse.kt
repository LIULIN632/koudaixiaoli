package com.pocket.xiaoli.util

import com.pocket.xiaoli.core.course.UpcomingCandidate
import com.pocket.xiaoli.core.course.pickUpcomingCourse
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「下一节课」——课程本体 + 它离今天几天 + 起止时刻。
 *
 * [startAt] 是提醒唯一需要的东西（要算"还有几分钟上课"），解析不出节次时间时为 null。
 */
data class UpcomingCourse(
    val course: CourseEntity,
    val dayOffset: Int,
    val startTime: String,
    val endTime: String,
    /** 开课时刻；节次表里没有这一节（或时间格式坏掉）时 null。 */
    val startAt: LocalDateTime? = null,
)

/**
 * 找出「下一节课」。
 *
 * ## 为什么抽成公共函数（2026-10）
 *
 * 这段判断原先只长在 `ui/home/HomeViewModel.buildState` 里，服务首页那张「下一节课」卡片。
 * 主动气泡要说的第一句话就是「N 分钟后上 X」——**如果它自己再算一遍**，
 * 两处迟早会在边界上分叉（跨天、节假日、单双周、这节课已下课），
 * 而用户看到的是"卡片写高数、气泡说英语"。
 *
 * 与首页卡片**逐字同源**的规则（搬家，不是重写）：
 * 1. 只算本周出现的课（`isCourseInWeek`）；
 * 2. 今天不是法定放假日才排课；
 * 3. 今天先取「还没下课」的最早一节（`endTime` 缺失时视为没下课，与卡片的 `isFinished` 同口径）；
 * 4. 今天没有了，就往后翻最多 7 天，取**第一个有课的日子**里最早的一节。
 *
 * ⚠️ 返回的是"点"而不是列表：气泡与卡片都只需要一节。
 *
 * @param slots 节次表（`TimeSlotEntity.slot` → 起止时间），空表时时间为空串（卡片同样如此）
 * @param isOffDay 法定放假日判断，调用方传 `HolidayCalendar::isOffDay` 的偏应用
 */
fun findUpcomingCourse(
    courses: List<CourseEntity>,
    slots: Map<Int, TimeSlotEntity>,
    week: Int,
    today: LocalDate = LocalDate.now(),
    now: LocalTime = LocalTime.now(),
    isOffDay: (LocalDate) -> Boolean = { false },
): UpcomingCourse? {
    if (week <= 0) return null
    // 挑选规则在共享层（core/course/UpcomingCourseCore.kt）：这里只负责把实体翻译成
    // 判定需要的最小数、再把命中下标翻回实体 + 补时间。
    val candidates = courses.map { course ->
        UpcomingCandidate(
            dayOfWeek = course.dayOfWeek,
            startSlot = course.startSlot,
            startMinute = minuteOf(slots[course.startSlot]?.startTime),
            endMinute = minuteOf(slots[course.endSlot]?.endTime),
            occursThisWeek = isCourseInWeek(course, week),
        )
    }
    val pick = pickUpcomingCourse(
        candidates = candidates,
        todayDayOfWeek = today.dayOfWeek.value,
        nowMinute = now.hour * 60 + now.minute,
        isOffDay = { offset -> isOffDay(today.plusDays(offset.toLong())) },
    ) ?: return null

    val date = today.plusDays(pick.dayOffset.toLong())
    return courses[pick.index].toUpcoming(slots, date, pick.dayOffset)
}

/** `"08:00"` → 从 0 点起的分钟数；空串/坏格式 → -1（共享层把 -1 当「时间未知」）。 */
private fun minuteOf(value: String?): Int =
    parseTimeOrNull(value.orEmpty())?.let { it.hour * 60 + it.minute } ?: -1

/**
 * 某一天要上的课：本周出现的 + 那一天的 + 不是法定放假日的，按节次排序。
 *
 * 抽出来是为了让"今天有几节课"（闲聊用）与"下一节课"（气泡与首页卡片用）
 * 走**同一套筛选** —— 否则会出现"气泡说今天没课、课表明明有两节"。
 */
fun coursesOn(
    courses: List<CourseEntity>,
    week: Int,
    date: LocalDate,
    isOffDay: (LocalDate) -> Boolean = { false },
): List<CourseEntity> =
    if (isOffDay(date)) {
        emptyList()
    } else {
        courses.filter { isCourseInWeek(it, week) && it.dayOfWeek == date.dayOfWeek.value }
            .sortedBy { it.startSlot }
    }

private fun CourseEntity.toUpcoming(
    slots: Map<Int, TimeSlotEntity>,
    date: LocalDate,
    dayOffset: Int,
): UpcomingCourse {
    val startTime = slots[startSlot]?.startTime.orEmpty()
    return UpcomingCourse(
        course = this,
        dayOffset = dayOffset,
        startTime = startTime,
        endTime = slots[endSlot]?.endTime.orEmpty(),
        // 日期由调用方给定（而不是 `LocalDate.now()`）：单测要把「今天」钉死
        startAt = parseTimeOrNull(startTime)?.let { date.atTime(it) },
    )
}

/** `"08:00"` → [LocalTime]；空串或坏格式返回 null（与首页卡片一样不抛）。 */
fun parseTimeOrNull(value: String): LocalTime? = try {
    if (value.isBlank()) null else LocalTime.parse(value)
} catch (_: Exception) {
    null
}
