package com.pocket.xiaoli.data

import com.pocket.xiaoli.data.entity.HomeworkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 作业截止解析 / 判档 / 文案 / 排期 的纯函数守门人。
 *
 * 钉的是三类历史回归：
 * 1. **带时刻的截止**（学习通抓来的 `2026-11-05 23:59`）以前在首页解析失败，
 *    截止日当天早上既不标红也不算过期；
 * 2. **只给日期**时截止时刻是当天 23:59 而不是 00:00 —— 取 00:00 会让作业
 *    在截止日一开始就"已过期"；
 * 3. 提醒排期与界面倒计时**同一口径**：界面说还剩 1 天，闹钟就必须会响。
 */
class HomeworkDueTest {

    private val now = LocalDateTime.of(2026, 11, 3, 10, 0)

    private fun hw(due: String, completed: Boolean = false) =
        HomeworkEntity(courseName = "高等数学", content = "第七章习题", dueDate = due, completed = completed)

    @Test
    fun `只给日期时截止时刻是当天 23 点 59`() {
        assertEquals(LocalDateTime.of(2026, 11, 5, 23, 59), parseHomeworkDue("2026-11-05", now))
    }

    @Test
    fun `带时刻与斜杠写法都认`() {
        assertEquals(LocalDateTime.of(2026, 11, 5, 23, 0), parseHomeworkDue("2026-11-05 23:00", now))
        assertEquals(LocalDateTime.of(2026, 11, 5, 23, 0), parseHomeworkDue("2026/11/5 23:00", now))
        assertEquals(LocalDateTime.of(2026, 11, 5, 23, 59), parseHomeworkDue("2026年11月5日", now))
    }

    @Test
    fun `无年份中文写法补今年`() {
        assertEquals(LocalDateTime.of(2026, 11, 5, 23, 59), parseHomeworkDue("11月5日", now))
    }

    @Test
    fun `解析不出返回 null 而不是编一个时刻`() {
        assertNull(parseHomeworkDue("下周三之前", now))
        assertNull(parseHomeworkDue("", now))
    }

    @Test
    fun `截止日当天早上不算过期 过了时刻才算`() {
        assertEquals(DueStage.TODAY, dueStageOf("2026-11-03 23:59", now))
        assertEquals(DueStage.OVERDUE, dueStageOf("2026-11-03 08:00", now))
        assertEquals(DueStage.OVERDUE, dueStageOf("2026-11-01", now))
    }

    @Test
    fun `临近与还早分档`() {
        assertEquals(DueStage.SOON, dueStageOf("2026-11-05", now))
        assertEquals(DueStage.LATER, dueStageOf("2026-11-20", now))
    }

    @Test
    fun `文案把倒计时放前面 不急时才带日期`() {
        assertEquals("已过期 2 天", dueLabelText("2026-11-01", now))
        assertEquals("已过期 3 小时", dueLabelText("2026-11-03 07:00", now))
        assertEquals("今天 23:59 截止", dueLabelText("2026-11-03 23:59", now))
        assertEquals("明天截止", dueLabelText("2026-11-04", now))
        assertEquals("还剩 2 天 · 11-05 截止", dueLabelText("2026-11-05", now))
        assertEquals("11-20 截止", dueLabelText("2026-11-20", now))
    }

    @Test
    fun `排期提前 24 小时 不足 24 小时退到前 2 小时`() {
        val later = hw("2026-11-05 10:00")
        val plan = planNextHomeworkReminder(listOf(later), atMillis(now))!!
        assertEquals(LocalDateTime.of(2026, 11, 4, 10, 0), atTime(plan.triggerAtMillis))

        val soon = hw("2026-11-03 20:00")
        val plan2 = planNextHomeworkReminder(listOf(soon), atMillis(now))!!
        assertEquals(LocalDateTime.of(2026, 11, 3, 18, 0), atTime(plan2.triggerAtMillis))
    }

    @Test
    fun `两个时刻都已过去的作业不排 已完成与解析不出的跳过`() {
        assertNull(planNextHomeworkReminder(listOf(hw("2026-11-01")), atMillis(now)))
        assertNull(planNextHomeworkReminder(listOf(hw("2026-11-05", completed = true)), atMillis(now)))
        assertNull(planNextHomeworkReminder(listOf(hw("下周三")), atMillis(now)))
    }

    @Test
    fun `多条作业取最先响的那条`() {
        val plan = planNextHomeworkReminder(
            listOf(hw("2026-11-08"), hw("2026-11-05")),
            atMillis(now),
        )!!
        assertTrue(plan.dueText.contains("11-05"))
        assertEquals(LocalDateTime.of(2026, 11, 4, 23, 59), atTime(plan.triggerAtMillis))
    }

    /**
     * 课表格子只有一条边框，一门课却可能挂好几条作业 —— 必须取**最紧**那一档。
     *
     * 反例：`[两周后, 已逾期]` 若取最后一条或取平均，逾期那条就被藏起来了，
     * 而"边框颜色"是这一格里唯一的提醒通道。
     */
    @Test
    fun `多条作业取最紧那一档`() {
        assertEquals(DueStage.LATER, worstDueStage(listOf("2026-11-20"), now))
        assertEquals(DueStage.SOON, worstDueStage(listOf("2026-11-20", "2026-11-05"), now))
        assertEquals(DueStage.TODAY, worstDueStage(listOf("2026-11-05", "2026-11-03"), now))
        assertEquals(DueStage.OVERDUE, worstDueStage(listOf("2026-11-03", "2026-11-01"), now))
    }

    @Test
    fun `解析不出的日期不参与取档但不会掩盖能解析的`() {
        assertEquals(DueStage.OVERDUE, worstDueStage(listOf("下周交", "2026-11-01"), now))
        assertNull(worstDueStage(emptyList(), now))
        assertNull(worstDueStage(listOf("", "下周交", "见群通知"), now))
    }

    private fun atMillis(time: LocalDateTime): Long =
        time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun atTime(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
}
