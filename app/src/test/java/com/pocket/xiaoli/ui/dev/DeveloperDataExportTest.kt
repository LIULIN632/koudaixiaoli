package com.pocket.xiaoli.ui.dev

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 数据导入导出：CSV 转义与 ICS 周次展开。 */
class DeveloperDataExportTest {

    @Test
    fun csvEscapesCommaAndQuote() {
        val csv = DeveloperDataExport.coursesToCsv(
            listOf(
                CourseEntity(
                    id = 1,
                    name = "高数, 上",
                    teacher = "张\"三\"",
                    room = "A101",
                    dayOfWeek = 1,
                    startSlot = 1,
                    endSlot = 2,
                    weeks = "1,2",
                )
            )
        )
        // ⚠️ 表头断言：2026-10 加了 shortName / colorPinned 两列（课表简称 / 颜色是否用户手选）。
        // 改列时**必须同时改值**，否则导出的 CSV 会整体错位 —— `coursesToCsv` 里也写着这条。
        assertTrue(csv.startsWith("id,name,shortName,credits,teacher,room,dayOfWeek"))
        assertTrue("shortName / colorPinned 两列都要在表头里", csv.contains("colorIndex,colorPinned,schemeId"))
        assertTrue(csv.contains("\"高数, 上\""))
        assertTrue(csv.contains("\"张\"\"三\"\""))
        assertTrue(csv.contains("\"1,2\""))
        // ⚠️ 别用 `split(",")` 数"表头列数 == 数据列数"：`"高数, 上"` 这种带引号的字段里
        // 本身就有逗号，朴素切分必然对不上（我自己先踩了一次）。要核列数就老老实实按 CSV 规则解。
        assertEquals(
            "表头列数应为 14（加了 shortName / colorPinned）",
            14,
            DeveloperDataExport.coursesToCsv(emptyList()).trim().split(",").size,
        )
    }

    @Test
    fun icsExpandsOddWeeksOnly() {
        val ics = DeveloperDataExport.coursesToIcs(
            courses = listOf(
                CourseEntity(
                    id = 7,
                    name = "高等数学",
                    teacher = "张老师",
                    room = "A101",
                    dayOfWeek = 1,
                    startSlot = 1,
                    endSlot = 2,
                    weekType = "odd",
                )
            ),
            timeSlots = listOf(
                TimeSlotEntity(1, "08:00", "08:45"),
                TimeSlotEntity(2, "08:55", "09:40"),
            ),
            semesterStart = "2026-09-21",
            totalWeeks = 4,
            now = LocalDateTime.of(2026, 9, 21, 0, 0),
        )

        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("SUMMARY:高等数学"))
        assertTrue(ics.contains("DTSTART:20260921T080000"))
        assertTrue(ics.contains("DTEND:20260921T094000"))
        assertTrue(ics.contains("DTSTART:20261005T080000"))
        assertFalse("双周不应导出", ics.contains("20260928"))
    }

    @Test
    fun icsWithoutSemesterStartReturnsEmptyCalendar() {
        val ics = DeveloperDataExport.coursesToIcs(
            courses = listOf(CourseEntity(id = 1, name = "高数")),
            timeSlots = emptyList(),
            semesterStart = "",
            totalWeeks = 20,
        )
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }
}
