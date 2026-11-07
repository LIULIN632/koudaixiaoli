package com.pocket.xiaoli.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 课表 JSON 里「星期」到底从哪个字段读。
 *
 * 背景：2026-11-06 用户报「教务直连导入的课全挤在周日那一列」。真实返回里
 * `xqj` 这一列可能不是「周几」而是「表格列号」（多一列节次就整体右移一位），
 * 而 `xqjmc`（"周三"）是文字，不受列偏移影响。
 *
 * 判据：**名称优先于数字**；没有名称时仍回落到数字，两者都没有就拒绝该课
 * （宁可少一门，也不把课塞到错误的星期）。
 */
class DayFieldPreferenceTest {

    private fun json(vararg courses: String) = courses.joinToString(",", "[", "]")

    @Test
    fun `xqj 是列号时以 xqjmc 为准`() {
        val body = json(
            """{"kcmc":"工程会计","xm":"郝冰","cdmc":"文一教313","xqj":"7","xqjmc":"周三","jcs":"1-2节","zcd":"1-16周"}""",
            """{"kcmc":"项目管理","xm":"赵彦","cdmc":"文一教512","xqj":"7","xqjmc":"周五","jcs":"3-4节","zcd":"1-16周"}""",
        )
        val courses = ZhengfangScheduleParser.parseJsonSchedule(body)
        assertEquals(2, courses.size)
        assertEquals(3, courses[0].dayOfWeek)
        assertEquals(5, courses[1].dayOfWeek)
        assertTrue(ZhengfangScheduleParser.lastDayNotes.any { it.contains("周三") })
    }

    @Test
    fun `没有名称时按数字 xqj`() {
        val body = json(
            """{"kcmc":"高等数学","xm":"张三","cdmc":"D101","xqj":"3","jcs":"1-2节","zcd":"1-16周"}""",
        )
        val courses = ZhengfangScheduleParser.parseJsonSchedule(body)
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].dayOfWeek)
    }

    @Test
    fun `名称与数字都读不出星期就丢掉这门课`() {
        val body = json(
            """{"kcmc":"神秘课程","xm":"某人","cdmc":"某处","xqj":"","xqjmc":"","jcs":"1-2节","zcd":"1-16周"}""",
        )
        assertEquals(0, ZhengfangScheduleParser.parseJsonSchedule(body).size)
    }

    @Test
    fun `星期日写成星期天也算周日`() {
        val body = json(
            """{"kcmc":"体育","xm":"王五","cdmc":"体育馆","xqj":"1","xqjmc":"星期天","jcs":"5-6节","zcd":"1-16周"}""",
        )
        val courses = ZhengfangScheduleParser.parseJsonSchedule(body)
        assertEquals(1, courses.size)
        assertEquals(7, courses[0].dayOfWeek)
    }
}
