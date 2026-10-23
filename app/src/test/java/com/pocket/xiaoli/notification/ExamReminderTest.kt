package com.pocket.xiaoli.notification

import com.pocket.xiaoli.data.entity.ExamEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试提醒排期的测试。
 *
 * ## 为什么必须测
 *
 * 提醒算错 = 用户**错过考试**。而且这类 bug 在界面上看不出来
 * （没有提醒时用户不会知道"本该有提醒"），只能靠测试兜住。
 *
 * ## 时间处理
 *
 * 全部用固定的 [ZONE]（Asia/Shanghai）与显式的 `fromMillis`，
 * **不依赖 `System.currentTimeMillis()`** —— 否则测试会随时间漂移。
 */
class ExamReminderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 1, 10)

    private fun millisOf(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun exam(
        name: String = "高等数学",
        date: String = "2026-01-15",
        time: String = "14:00-16:00",
        location: String = "教三楼305",
        seat: String = "12",
        completed: Boolean = false,
    ) = ExamEntity(
        courseName = name, date = date, time = time,
        location = location, seat = seat, completed = completed,
    )

    // ── 日期解析 ──

    @Test
    fun `parses iso date`() {
        assertEquals(LocalDate.of(2026, 1, 15), ExamReminderScheduler.parseExamDate("2026-01-15", today))
    }

    @Test
    fun `parses chinese date without year using current year`() {
        assertEquals(LocalDate.of(2026, 1, 15), ExamReminderScheduler.parseExamDate("1月15日", today))
    }

    @Test
    fun `parses chinese date with year`() {
        assertEquals(LocalDate.of(2027, 3, 2), ExamReminderScheduler.parseExamDate("2027年3月2日", today))
    }

    @Test
    fun `chinese date far in the past rolls to next year`() {
        // 1月1日 相对 1月10日 只过去 9 天 → 仍是今年（还没到"过去半年"）
        assertEquals(LocalDate.of(2026, 1, 1), ExamReminderScheduler.parseExamDate("1月1日", today))
        // 但相对 8 月 1 日看，1月1日已过去 7 个月 → 应算明年
        val aug = LocalDate.of(2026, 8, 1)
        assertEquals(LocalDate.of(2027, 1, 1), ExamReminderScheduler.parseExamDate("1月1日", aug))
    }

    @Test
    fun `unparseable date returns null`() {
        assertNull(ExamReminderScheduler.parseExamDate("", today))
        assertNull(ExamReminderScheduler.parseExamDate("待定", today))
        assertNull(ExamReminderScheduler.parseExamDate("13月45日", today))
    }

    // ── 排期 ──

    @Test
    fun `schedules the day before at 20 00`() {
        val from = millisOf(2026, 1, 10, 9, 0)
        val r = ExamReminderScheduler.planNextExamReminder(listOf(exam(date = "2026-01-15")), from, zone)
        assertNotNull(r)
        // 1月14日 20:00
        assertEquals(millisOf(2026, 1, 14, 20, 0), r!!.triggerAtMillis)
        assertEquals("高等数学", r.courseName)
    }

    @Test
    fun `completed exams are skipped`() {
        val from = millisOf(2026, 1, 10, 9, 0)
        val r = ExamReminderScheduler.planNextExamReminder(
            listOf(exam(date = "2026-01-15", completed = true)),
            from, zone,
        )
        assertNull(r)
    }

    @Test
    fun `earliest exam wins`() {
        val from = millisOf(2026, 1, 10, 9, 0)
        val r = ExamReminderScheduler.planNextExamReminder(
            listOf(
                exam(name = "后考的", date = "2026-01-20"),
                exam(name = "先考的", date = "2026-01-15"),
            ),
            from, zone,
        )
        assertEquals("先考的", r!!.courseName)
    }

    @Test
    fun `a past trigger is skipped not fired immediately`() {
        // 考试就在明天，但今天已经过了 20:00 → 提醒时刻已过，
        // 应跳过而不是塞一个过去的闹钟（那会立刻弹一条过时通知）
        val from = millisOf(2026, 1, 14, 21, 0)
        val r = ExamReminderScheduler.planNextExamReminder(listOf(exam(date = "2026-01-15")), from, zone)
        assertNull(r)
    }

    @Test
    fun `still schedules when trigger is in the future on the same day`() {
        val from = millisOf(2026, 1, 14, 9, 0)
        val r = ExamReminderScheduler.planNextExamReminder(listOf(exam(date = "2026-01-15")), from, zone)
        assertNotNull(r)
        assertEquals(millisOf(2026, 1, 14, 20, 0), r!!.triggerAtMillis)
    }

    @Test
    fun `skips exams with unparseable dates and still finds others`() {
        val from = millisOf(2026, 1, 10, 9, 0)
        val r = ExamReminderScheduler.planNextExamReminder(
            listOf(
                exam(name = "日期待定", date = "见通知"),
                exam(name = "有日期", date = "2026-01-18"),
            ),
            from, zone,
        )
        assertEquals("有日期", r!!.courseName)
    }

    @Test
    fun `no exams yields null`() {
        assertNull(ExamReminderScheduler.planNextExamReminder(emptyList(), millisOf(2026, 1, 10, 9, 0), zone))
    }

    @Test
    fun `chinese date exam is scheduled too`() {
        // 与界面倒计时同口径：中文写法也应能排期，否则会出现
        // "界面显示还有 3 天，却收不到提醒"
        val from = millisOf(2026, 1, 10, 9, 0)
        val r = ExamReminderScheduler.planNextExamReminder(listOf(exam(date = "1月15日")), from, zone)
        assertNotNull(r)
        assertEquals(millisOf(2026, 1, 14, 20, 0), r!!.triggerAtMillis)
    }

    // ── 通知文案 ──

    @Test
    fun `describe joins non blank parts`() {
        val r = ExamReminder(0, "高数", "2026-01-15", "14:00", "教三楼305", "12")
        val text = r.describe()
        assertTrue(text.contains("2026-01-15"))
        assertTrue(text.contains("14:00"))
        assertTrue(text.contains("教三楼305"))
        assertTrue(text.contains("座位 12"))
    }

    @Test
    fun `describe falls back when everything is blank`() {
        val r = ExamReminder(0, "高数", "", "", "", "")
        assertEquals("时间地点见通知", r.describe())
    }
}
