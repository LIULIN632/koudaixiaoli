package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.Course
import com.pocket.xiaoli.desktop.data.Term
import com.pocket.xiaoli.desktop.data.TimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 桌面 ICS 导出：周次必须被尊重。
 *
 * 历史实现用 `RRULE:FREQ=WEEKLY;UNTIL` 一条事件盖整段 —— 单双周课会把没课的周也导出去。
 * 现在按周展开委托共享核心（与 Android 同一份），这里锁住接线：单周课只出偶数条里的一半，
 * 且**不再出现 RRULE**。
 */
class ScheduleIcsTest {

    private val slots = (1..12).map { index ->
        val startMin = 8 * 60 + (index - 1) * 55
        TimeSlot(index = index, label = "第 $index 节", start = clock(startMin), end = clock(startMin + 45))
    }

    private fun clock(minute: Int): String =
        (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')

    private fun course(
        weekType: String = "all",
        weeks: String = "",
        dayOfWeek: Int = 1,
        startSlot: Int = 1,
        endSlot: Int = 2,
    ) = Course(
        id = "c1",
        name = "数据结构",
        teacher = "李慧敏",
        room = "三教 B-201",
        dayOfWeek = dayOfWeek,
        startSlot = startSlot,
        endSlot = endSlot,
        weekType = weekType,
        weeks = weeks,
    )

    private val term = Term(id = "t1", name = "2026-2027 学年 第一学期", startDate = "2026-08-31", totalWeeks = 20)

    @Test
    fun `单周课只导出单周 不出现 RRULE`() {
        val ics = buildIcs(courses = listOf(course(weekType = "odd", weeks = "1-16")), term = term, slots = slots)
        assertFalse(ics.contains("RRULE"), "不应再用 RRULE 覆盖整段")
        assertEquals(8, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test
    fun `空周次视为全学期`() {
        val ics = buildIcs(
            courses = listOf(course(weeks = "")),
            term = term.copy(totalWeeks = 4),
            slots = slots,
        )
        assertEquals(4, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test
    fun `日期与时间落在正确的周次和节次`() {
        val ics = buildIcs(
            courses = listOf(course(weeks = "1", dayOfWeek = 3, startSlot = 3, endSlot = 4)),
            term = term,
            slots = slots,
        )
        // 2026-08-31 周一 → 第 1 周周三 2026-09-02；第 3 节 09:50 起、第 4 节 11:30 止
        assertTrue(ics.contains("DTSTART:20260902T095000"))
        assertTrue(ics.contains("DTEND:20260902T113000"))
        assertTrue(ics.contains("LOCATION:三教 B-201"))
    }
}
