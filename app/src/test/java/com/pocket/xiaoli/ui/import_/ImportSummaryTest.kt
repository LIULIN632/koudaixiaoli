package com.pocket.xiaoli.ui.import_

import com.pocket.xiaoli.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入摘要：把解析结果压成一行人话，并把「看着有课其实不对」的几种情况挑出来。
 *
 * 这些用例都是真实踩过的形态：全挤一天（星期读成常量）、周次空、教室空。
 */
class ImportSummaryTest {

    private fun course(
        name: String,
        day: Int,
        start: Int,
        end: Int,
        weeks: String = "1,2,3,4,5,6,7,8",
        teacher: String = "王老师",
        room: String = "2-101",
    ) = CourseEntity(
        name = name,
        teacher = teacher,
        room = room,
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weeks = weeks,
    )

    @Test
    fun `正常课表压成一行人话`() {
        val s = ImportSummary.of(
            listOf(
                course("建设法规", day = 1, start = 1, end = 2),
                course("大学英语", day = 2, start = 3, end = 4),
                course("工程会计", day = 5, start = 1, end = 2, weeks = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16"),
            )
        )
        assertEquals("3 门课 · 周一至周五 · 第 1–16 周 · 第 1–4 节", s.headline())
        assertTrue("不该有告警：" + s.warnings(), s.warnings().isEmpty())
    }

    @Test
    fun `全挤一天要提示星期可能读错`() {
        val s = ImportSummary.of(
            listOf(
                course("工程建设法规", day = 7, start = 1, end = 2),
                course("土木工程施工", day = 7, start = 3, end = 4),
            )
        )
        assertTrue(s.warnings().toString(), s.warnings().any { it.contains("全部课程都在周日") })
    }

    @Test
    fun `周次没读到要提示`() {
        val s = ImportSummary.of(listOf(course("形势与政策", day = 3, start = 5, end = 6, weeks = "")))
        assertTrue(s.warnings().toString(), s.warnings().any { it.contains("周次没读到") })
    }

    @Test
    fun `缺教室和缺教师分开数`() {
        val s = ImportSummary.of(
            listOf(
                course("A", day = 1, start = 1, end = 2, room = ""),
                course("B", day = 2, start = 1, end = 2, room = "", teacher = ""),
            )
        )
        assertEquals(2, s.missingRoom)
        assertEquals(1, s.missingTeacher)
        assertTrue(s.warnings().toString(), s.warnings().contains("2 门课缺教室"))
        assertTrue(s.warnings().toString(), s.warnings().contains("1 门课缺教师"))
    }

    @Test
    fun `一门都没解析出来时给最直白的提示`() {
        val s = ImportSummary.of(emptyList())
        assertEquals("0 门课", s.headline())
        assertTrue(s.warnings().toString(), s.warnings().any { it.contains("解析日志") })
    }

    @Test
    fun `单天单周单节时文案不出现范围`() {
        val s = ImportSummary.of(listOf(course("讲座", day = 6, start = 9, end = 9, weeks = "8")))
        assertEquals("1 门课 · 周六 · 第 8 周 · 第 9–9 节", s.headline())
    }
}
