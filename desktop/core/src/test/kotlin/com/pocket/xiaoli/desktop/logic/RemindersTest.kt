package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.seedData
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemindersTest {

    private val data = seedData(LocalDate.of(2026, 10, 31))

    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text)

    @Test
    fun `开课前十分钟窗口内提醒最近的一门课`() {
        val reminder = pendingClassReminder(data, at("2026-11-02T07:52"), leadMinutes = 10)
        assertEquals("计算机网络", reminder?.courseName)
        assertEquals(8, reminder?.minutesUntilStart)
    }

    @Test
    fun `窗口边界包含恰好提前十分钟`() {
        assertEquals("计算机网络", pendingClassReminder(data, at("2026-11-02T07:50"), 10)?.courseName)
    }

    @Test
    fun `超出窗口不提醒`() {
        assertNull(pendingClassReminder(data, at("2026-11-02T07:49"), 10))
        assertNull(pendingClassReminder(data, at("2026-11-02T08:00"), 10))
        assertNull(pendingClassReminder(data, at("2026-11-02T08:30"), 10))
    }

    @Test
    fun `多门课时取最近的一门`() {
        // 周一 10:00 数据结构（1-2 节 08:00 已过，3-4 节 10:00）
        val reminder = pendingClassReminder(data, at("2026-11-02T09:55"), 10)
        assertEquals("数据结构", reminder?.courseName)
        assertEquals(5, reminder?.minutesUntilStart)
    }

    @Test
    fun `本周没课的课程不提醒`() {
        // 形势与政策只在 1-8 周；11 月 6 日是第 10 周
        assertNull(pendingClassReminder(data, at("2026-11-06T09:50"), 10))
    }

    @Test
    fun `学期结束后不提醒`() {
        assertNull(pendingClassReminder(data, at("2027-03-01T07:52"), 10))
    }

    @Test
    fun `提前量非法直接返回空`() {
        assertNull(pendingClassReminder(data, at("2026-11-02T07:52"), 0))
    }

    @Test
    fun `去重键包含课程 日期与开始分钟`() {
        val reminder = pendingClassReminder(data, at("2026-11-02T07:52"), 10)!!
        assertEquals("${reminder.courseId}|2026-11-02|480", reminder.key)
    }

    // ── 待办到期提醒 ──

    @Test
    fun `当天到期的待办九点后提醒一次`() {
        // seed 的 t1：复习数据结构第 5 章，截止 2026-11-01
        val reminder = pendingTodoReminder(data, at("2026-11-01T09:05"))
        assertEquals(listOf("复习数据结构第 5 章"), reminder?.titles)
        assertEquals("今天截止：复习数据结构第 5 章", reminder?.message)
    }

    @Test
    fun `九点前不提醒`() {
        assertNull(pendingTodoReminder(data, at("2026-11-01T08:59")))
    }

    @Test
    fun `已完成的待办不提醒`() {
        val done = data.copy(todos = data.todos.map { if (it.id == "t1") it.copy(done = true) else it })
        assertNull(pendingTodoReminder(done, at("2026-11-01T09:05")))
    }

    @Test
    fun `同一天多条待办合并成一条`() {
        val extra = data.todos.first { it.id == "t1" }.copy(id = "t-extra", title = "交实验报告", priority = 2)
        val withExtra = data.copy(todos = data.todos + extra)
        val reminder = pendingTodoReminder(withExtra, at("2026-11-01T09:05"))!!
        assertEquals(2, reminder.count)
        assertEquals("今天有 2 项待办截止", reminder.message)
        // 优先级高的排前面
        assertEquals("交实验报告", reminder.titles.first())
    }

    @Test
    fun `没有截止日期的待办不提醒`() {
        val noDue = data.copy(todos = data.todos.map { if (it.id == "t1") it.copy(due = "") else it })
        assertNull(pendingTodoReminder(noDue, at("2026-11-01T09:05")))
    }
}
