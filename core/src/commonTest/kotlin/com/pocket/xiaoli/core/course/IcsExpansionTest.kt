package com.pocket.xiaoli.core.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 按周展开的边界。
 *
 * 最重要的一条：**单双周/指定周次必须在导出时被尊重** ——
 * 用 `RRULE:FREQ=WEEKLY;UNTIL` 偷懒会把没课的周也展开出来，
 * 导进日历就是多了一节不存在的课。
 *
 * 周次集合怎么算（空周次=全学期、单双周）由共享的 `weekSet` 负责，见 `CourseWeekRulesTest`。
 */
class IcsExpansionTest {

    private val start = 20_000L // 学期第一周周一（任意基准）

    private fun course(
        weeks: Set<Int> = (1..16).toSet(),
        dayOfWeek: Int = 1,
        startSlot: Int = 1,
        endSlot: Int = 2,
        startMinute: Int = -1,
        endMinute: Int = -1,
    ) = IcsCourseInput(
        id = "c1",
        name = "数据结构",
        teacher = "李慧敏",
        room = "三教 B-201",
        dayOfWeek = dayOfWeek,
        weeks = weeks,
        startSlot = startSlot,
        endSlot = endSlot,
        startMinute = startMinute,
        endMinute = endMinute,
    )

    private val slotStart = mapOf(1 to "08:00", 2 to "08:55")
    private val slotEnd = mapOf(1 to "08:45", 2 to "09:40")

    @Test
    fun `按周展开时只输出生效周`() {
        val events = expandIcsWeekEvents(
            courses = listOf(course(weeks = setOf(1, 3, 5))),
            slotStart = slotStart,
            slotEnd = slotEnd,
            semesterStartEpochDay = start,
            totalWeeks = 16,
        )
        assertEquals(listOf(1, 3, 5), events.map { it.week })
    }

    @Test
    fun `日期落在正确的星期与周次`() {
        val events = expandIcsWeekEvents(
            courses = listOf(course(weeks = setOf(2), dayOfWeek = 3)),
            slotStart = slotStart,
            slotEnd = slotEnd,
            semesterStartEpochDay = start,
            totalWeeks = 16,
        )
        // 第 2 周周三 = 基准 + 7 + 2
        assertEquals(compactDate(start + 9), events.single().dateCompact)
    }

    @Test
    fun `分钟级时间优先于节次表`() {
        val events = expandIcsWeekEvents(
            courses = listOf(course(weeks = setOf(1), startMinute = 14 * 60, endMinute = 15 * 60 + 35)),
            slotStart = slotStart,
            slotEnd = slotEnd,
            semesterStartEpochDay = start,
            totalWeeks = 16,
        )
        assertEquals("1400", events.single().startCompact)
        assertEquals("1535", events.single().endCompact)
    }

    @Test
    fun `分钟未填或越界时回退节次表`() {
        val events = expandIcsWeekEvents(
            courses = listOf(course(weeks = setOf(1), startMinute = -1, endMinute = 99_999)),
            slotStart = slotStart,
            slotEnd = slotEnd,
            semesterStartEpochDay = start,
            totalWeeks = 16,
        )
        assertEquals("0800", events.single().startCompact)
        assertEquals("0940", events.single().endCompact)
    }

    @Test
    fun `节次表缺失时用兜底时间`() {
        val events = expandIcsWeekEvents(
            courses = listOf(course(weeks = setOf(1), startSlot = 9, endSlot = 10)),
            slotStart = emptyMap(),
            slotEnd = emptyMap(),
            semesterStartEpochDay = start,
            totalWeeks = 16,
        )
        assertEquals("0800", events.single().startCompact)
        assertEquals("0940", events.single().endCompact)
    }

    @Test
    fun `非法星期与越界周次被跳过 周数上限 30`() {
        val invalidDay = expandIcsWeekEvents(
            courses = listOf(course(dayOfWeek = 0)),
            slotStart = slotStart, slotEnd = slotEnd,
            semesterStartEpochDay = start, totalWeeks = 16,
        )
        assertTrue(invalidDay.isEmpty())

        val outOfRange = expandIcsWeekEvents(
            courses = listOf(course(weeks = setOf(1, 40))),
            slotStart = slotStart, slotEnd = slotEnd,
            semesterStartEpochDay = start, totalWeeks = 40,
        )
        assertEquals(listOf(1), outOfRange.map { it.week })
    }

    @Test
    fun `ICS 转义覆盖反斜杠 分号 逗号 与换行`() {
        assertEquals("a\\\\b\\;c\\,d\\ne", icsEscape("a\\b;c,d\ne"))
    }
}
