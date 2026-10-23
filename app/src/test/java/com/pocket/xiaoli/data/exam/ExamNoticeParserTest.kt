package com.pocket.xiaoli.data.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试通知解析器的测试。
 *
 * 这个解析器直接决定「待办里会不会出现错误的考试提醒」——
 * **错误的提醒比没有提醒更糟**（用户按错时间去考场）。
 * 所以这里把各种日期/时间写法、表头、噪音行、以及"该认不出就不认"的
 * 边界情况全部钉住。
 */
class ExamNoticeParserTest {

    // ── 主路径：粘贴的表格 ──

    @Test
    fun `parses a whitespace separated row`() {
        val r = ExamNoticeParser.parse("高等数学A    2026-01-15    14:00-16:00    教三楼305    12")
        assertEquals(1, r.exams.size)
        val e = r.exams[0]
        assertEquals("高等数学A", e.courseName)
        assertEquals("2026-01-15", e.date)
        assertEquals("14:00-16:00", e.time)
        assertEquals("教三楼305", e.location)
        assertEquals("12", e.seat)
    }

    @Test
    fun `parses multiple rows`() {
        val text = """
            大学英语    2026-01-16    09:00-11:00    教二楼201    5
            线性代数    2026-01-18    14:00-16:00    教三楼305    22
        """.trimIndent()
        val r = ExamNoticeParser.parse(text)
        assertEquals(2, r.exams.size)
        assertEquals(listOf("大学英语", "线性代数"), r.exams.map { it.courseName })
    }

    // ── 日期写法 ──

    @Test
    fun `accepts slash and dot date separators`() {
        // 分隔符统一成 -：三种写法解析结果一致，便于排序与去重
        assertEquals("2026-1-15", ExamNoticeParser.parse("高数 2026/1/15 14:00").exams[0].date)
        assertEquals("2026-1-15", ExamNoticeParser.parse("高数 2026.1.15 14:00").exams[0].date)
    }

    @Test
    fun `accepts chinese year month day`() {
        val e = ExamNoticeParser.parse("高等数学 2026年1月15日 14:00-16:00 教三楼305").exams[0]
        assertEquals("2026年1月15日", e.date)
    }

    @Test
    fun `accepts month day without year`() {
        // 通知里常见"1月15日"，年份由上下文决定 —— 先原样保留
        val e = ExamNoticeParser.parse("高等数学 1月15日 14:00-16:00 教三楼305").exams[0]
        assertEquals("1月15日", e.date)
    }

    // ── 时间写法 ──

    @Test
    fun `accepts various time range separators`() {
        for (sep in listOf("-", "—", "~", "～", "至", "到")) {
            val e = ExamNoticeParser.parse("高数 2026-01-15 14:00${sep}16:00 教三楼305").exams[0]
            assertEquals("time with sep=$sep", "14:00-16:00", e.time)
        }
    }

    @Test
    fun `normalizes full width colon in time`() {
        val e = ExamNoticeParser.parse("高数 2026-01-15 14：00-16：00 教三楼305").exams[0]
        assertEquals("14:00-16:00", e.time)
    }

    @Test
    fun `single time is treated as start time`() {
        val e = ExamNoticeParser.parse("高数 2026-01-15 14:00 教三楼305").exams[0]
        assertEquals("14:00", e.time)
    }

    // ── 应当被忽略的行 ──

    @Test
    fun `ignores header rows without a date`() {
        val text = """
            课程名称    考试时间    考场    座位号
            高等数学A    2026-01-15    14:00-16:00    教三楼305    12
        """.trimIndent()
        val r = ExamNoticeParser.parse(text)
        assertEquals(1, r.exams.size)
        assertEquals("高等数学A", r.exams[0].courseName)
    }

    @Test
    fun `ignores noise lines like notice boilerplate`() {
        val text = """
            关于2025-2026学年第一学期期末考试的通知
            注意事项：请携带学生证
            特此通知
            教务处
            高等数学A    2026-01-15    14:00-16:00    教三楼305    12
        """.trimIndent()
        val r = ExamNoticeParser.parse(text)
        assertEquals(1, r.exams.size)
        assertEquals("高等数学A", r.exams[0].courseName)
    }

    @Test
    fun `a line without a date is not an exam`() {
        // 最关键的安全属性：宁可少认，不可认错
        val r = ExamNoticeParser.parse("请同学们认真复习\n考场纪律要求\n高等数学")
        assertTrue(r.exams.isEmpty())
    }

    @Test
    fun `blank input yields nothing`() {
        assertTrue(ExamNoticeParser.parse("").exams.isEmpty())
        assertTrue(ExamNoticeParser.parse("   \n\n  \t ").exams.isEmpty())
    }

    // ── 容错 ──

    @Test
    fun `tolerates commas and semicolons as separators`() {
        val e = ExamNoticeParser.parse("高等数学A，2026-01-15，14:00-16:00，教三楼305").exams[0]
        assertEquals("高等数学A", e.courseName)
        assertEquals("2026-01-15", e.date)
        assertEquals("教三楼305", e.location)
    }

    @Test
    fun `tolerates full width comma`() {
        val e = ExamNoticeParser.parse("高等数学A，2026-01-15，教三楼305").exams[0]
        assertEquals("高等数学A", e.courseName)
    }

    @Test
    fun `seat can be absent`() {
        val e = ExamNoticeParser.parse("高等数学 2026-01-15 14:00-16:00 教三楼305").exams[0]
        assertEquals("", e.seat)
        assertEquals("教三楼305", e.location)
    }

    @Test
    fun `room code alone serves as location`() {
        val e = ExamNoticeParser.parse("高等数学 2026-01-15 14:00 A101").exams[0]
        assertEquals("高等数学", e.courseName)
        assertEquals("A101", e.location)
    }

    @Test
    fun `deduplicates identical rows`() {
        // 跨页 / 重复粘贴会出现同一场考试两次
        val text = """
            高等数学A    2026-01-15    14:00-16:00    教三楼305
            高等数学A    2026-01-15    14:00-16:00    教三楼305
        """.trimIndent()
        assertEquals(1, ExamNoticeParser.parse(text).exams.size)
    }

    @Test
    fun `scanned counts non blank non noise lines`() {
        val text = "高等数学A 2026-01-15 14:00 教三楼305\n\n   \n教务处"
        val r = ExamNoticeParser.parse(text)
        // 空行与噪音行不计；剩下「考试行」1 条
        assertEquals(1, r.scanned)
    }

    @Test
    fun `summary omits blanks`() {
        assertEquals("2026-01-15  14:00  教三楼305", ExamSchedule("高数", "2026-01-15", "14:00", "教三楼305").summary)
        assertEquals("时间地点见通知", ExamSchedule("高数").summary)
        assertEquals("2026-01-15", ExamSchedule("高数", "2026-01-15").summary)
    }
}
