package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.exam.ExamSchedule
import com.pocket.xiaoli.desktop.data.AppData
import com.pocket.xiaoli.desktop.data.Exam
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 考试链路：粘贴解析 → 查重 → 排序 → 提醒窗口。
 *
 * 解析与倒计时本身在共享核心另有测试（`ExamNoticeParserCoreTest` / `ExamCountdownCoreTest`），
 * 这里只测桌面自己那三层：查重标记、展示顺序、提醒窗口与去重键。
 */
class ExamReminderTest {

    private val today = LocalDate.of(2026, 11, 1)

    private fun exam(
        id: String,
        course: String,
        date: String,
        time: String = "14:00-16:00",
        location: String = "教三楼305",
        completed: Boolean = false,
    ) = Exam(
        id = id,
        schedule = ExamSchedule(courseName = course, date = date, time = time, location = location, seat = "12"),
        completed = completed,
    )

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun dataWith(vararg exams: Exam) = AppData(exams = exams.toList())

    // ── 解析 + 查重 ──

    @Test
    fun `通知里的三行解析成三条考试`() {
        val notice = """
            课程名称    考试时间    考场    座位号
            大学英语    2026-11-03    09:00-11:00    教二楼201    5
            线性代数    2026-11-06    14:00-16:00    教三楼305    22
            高等数学A    2026-11-13    08:30-10:30    图书馆报告厅    12
        """.trimIndent()
        val result = parseExamNotice(notice, emptyList())
        assertEquals(3, result.pending.size)
        assertTrue(result.pending.all { it.selected && !it.duplicate })
        assertEquals(listOf("大学英语", "线性代数", "高等数学A"), result.pending.map { it.schedule.courseName })
    }

    @Test
    fun `已在列表里的考试默认不勾选`() {
        val notice = "大学英语    2026-11-03    09:00-11:00    教二楼201    5"
        val existing = exam("e1", "大学英语", "2026-11-03", "09:00-11:00", "教二楼201")
        val result = parseExamNotice(notice, listOf(existing))
        val pending = result.pending.single()
        assertTrue(pending.duplicate)
        assertTrue(!pending.selected)
    }

    @Test
    fun `座位号列不会被误当成考场`() {
        // 列序是 课程 | 日期 | 时间 | 考场 | 座位；把 "22" 当考场会整列错位
        val result = parseExamNotice("线性代数    2026-11-06    14:00-16:00    教三楼305    22", emptyList())
        val schedule = result.pending.single().schedule
        assertEquals("教三楼305", schedule.location)
        assertEquals("22", schedule.seat)
    }

    // ── 展示顺序 ──

    @Test
    fun `未完成的近的在前，解析不出日期的垫底`() {
        val exams = listOf(
            exam("far", "远期的课", "2026-12-20"),
            exam("soon", "大学英语", "2026-11-03"),
            exam("tbd", "形势与政策", "待定"),
            exam("done", "已考完的课", "2026-11-02", completed = true),
        )
        val order = sortedExams(exams, today).map { it.id }
        assertEquals(listOf("soon", "far", "tbd", "done"), order)
    }

    // ── 提醒窗口 ──

    @Test
    fun `开考前三天内取最近的一场`() {
        val data = dataWith(
            exam("e1", "大学英语", "2026-11-02"),
            exam("e2", "线性代数", "2026-11-04"),
        )
        val reminder = pendingExamReminder(data, at("2026-11-01T10:00"))!!
        assertEquals("e1", reminder.examId)
        assertEquals(1L, reminder.daysLeft)
        assertEquals("大学英语 明天开考 · 14:00-16:00 教三楼305", reminder.message)
    }

    @Test
    fun `超出提前窗口与已过去的都不提醒`() {
        val data = dataWith(
            exam("later", "高等数学A", "2026-11-20"),
            exam("past", "大学体育", "2026-10-20"),
        )
        assertNull(pendingExamReminder(data, at("2026-11-01T10:00")))
    }

    @Test
    fun `标了完成的不再提醒`() {
        val data = dataWith(exam("e1", "大学英语", "2026-11-02", completed = true))
        assertNull(pendingExamReminder(data, at("2026-11-01T10:00")))
    }

    @Test
    fun `没有日期的考试不提醒`() {
        val data = dataWith(exam("e1", "形势与政策", "待定"))
        assertNull(pendingExamReminder(data, at("2026-11-01T10:00")))
    }

    @Test
    fun `提前一次当天一次：去重键只分两档`() {
        val data = dataWith(exam("e1", "大学英语", "2026-11-04"))
        val ahead = pendingExamReminder(data, at("2026-11-01T10:00"))!!
        val alsoAhead = pendingExamReminder(data, at("2026-11-03T09:00"))!!
        assertEquals(ahead.key, alsoAhead.key)
        val onDay = pendingExamReminder(data, at("2026-11-04T08:00"))!!
        assertEquals("exam|e1|day|2026-11-04", onDay.key)
        assertTrue(ahead.key != onDay.key)
    }

    @Test
    fun `窗口关掉就不提醒`() {
        val data = dataWith(exam("e1", "大学英语", "2026-11-02"))
        assertNull(pendingExamReminder(data, at("2026-11-01T10:00"), leadDays = 0))
    }
}
