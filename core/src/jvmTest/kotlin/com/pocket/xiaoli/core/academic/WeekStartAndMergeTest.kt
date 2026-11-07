package com.pocket.xiaoli.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 正方课表的两道清洗（照拾光课程表的适配规则）：
 * 1. `qsxqj`（一周起始日）不是周一时，`xqj` 是按那天起算的列号，要旋转回周一=1；
 * 2. 同一门课被拆成相邻两条时合并成一个块。
 */
class WeekStartAndMergeTest {

    private fun course(
        name: String,
        day: Int,
        start: Int,
        end: Int,
        teacher: String = "王老师",
        room: String = "2-101",
        weeks: String = "1,2,3,4",
    ) = """
        {
          "kcmc": "$name",
          "xm": "$teacher",
          "cdmc": "$room",
          "xqj": "$day",
          "jcs": "$start-$end",
          "zcd": "$weeks"
        }
    """.trimIndent()

    private fun json(vararg items: String, qsxqj: String? = null): String {
        val head = if (qsxqj != null) "\"qsxqj\": \"$qsxqj\"," else ""
        return """{ $head "kbList": [ ${items.joinToString(",")} ] }"""
    }

    @Test
    fun `qsxqj 为周日时 xqj=1 落在周日`() {
        val parsed = ZhengfangScheduleParser.parseJsonSchedule(
            json(course("土木工程施工", day = 1, start = 5, end = 8), qsxqj = "7")
        )
        assertEquals(7, parsed.single().dayOfWeek)
    }

    @Test
    fun `qsxqj 为周日时 xqj=2 落在周一`() {
        val parsed = ZhengfangScheduleParser.parseJsonSchedule(
            json(course("工程会计", day = 2, start = 1, end = 2), qsxqj = "7")
        )
        assertEquals(1, parsed.single().dayOfWeek)
    }

    @Test
    fun `qsxqj 缺失时 xqj 原样当星期`() {
        val parsed = ZhengfangScheduleParser.parseJsonSchedule(
            json(course("项目管理", day = 3, start = 3, end = 4))
        )
        assertEquals(3, parsed.single().dayOfWeek)
        assertEquals(1, ZhengfangScheduleParser.lastWeekStartDay)
    }

    @Test
    fun `qsxqj 放在 xsxx 里也能读到`() {
        val payload = """
            { "xsxx": { "qsxqj": "2" }, "kbList": [ ${course("项目管理", day = 1, start = 3, end = 4)} ] }
        """.trimIndent()
        val parsed = ZhengfangScheduleParser.parseJsonSchedule(payload)
        // qsxqj=2（周二为首）：xqj=1 → 周二
        assertEquals(2, parsed.single().dayOfWeek)
        assertEquals(2, ZhengfangScheduleParser.lastWeekStartDay)
    }

    @Test
    fun `相邻两节合并成一个块`() {
        val parsed = ZhengfangScheduleParser.parseJsonSchedule(
            json(
                course("大学英语", day = 2, start = 3, end = 3),
                course("大学英语", day = 2, start = 4, end = 4),
            )
        )
        val only = parsed.single()
        assertEquals(3, only.startSlot)
        assertEquals(4, only.endSlot)
    }

    @Test
    fun `中间隔了别的课就不合并`() {
        val parsed = ZhengfangScheduleParser.parseJsonSchedule(
            json(
                course("大学英语", day = 2, start = 1, end = 2),
                course("大学物理", day = 2, start = 3, end = 4),
                course("大学英语", day = 2, start = 5, end = 6),
            )
        )
        assertEquals(3, parsed.size)
        assertEquals(listOf(1, 3, 5), parsed.sortedBy { it.startSlot }.map { it.startSlot })
    }

    @Test
    fun `不同教室的相邻节次不合并`() {
        val parsed = ZhengfangScheduleParser.parseJsonSchedule(
            json(
                course("工程会计", day = 1, start = 1, end = 2, room = "2-101"),
                course("工程会计", day = 1, start = 3, end = 4, room = "3-402"),
            )
        )
        assertEquals(2, parsed.size)
    }
}
