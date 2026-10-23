package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.data.proactive.ProactiveEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「下一节课」的共用解析。
 *
 * 为什么它值得单独测：**首页卡片与主动气泡都读它**。
 * 它错了，用户看到的是"卡片写高数、气泡说英语"——那种不一致最伤信任，
 * 而两处各自看起来都"没坏"。所以这里把四种边界逐条钉死：
 * 已下课、跨天、法定放假日、单双周。
 */
class UpcomingCourseTest {

    /** 2026-10-08 是周四（dayOfWeek = 4）。 */
    private val thursday = LocalDate.of(2026, 10, 8)

    private val slots = mapOf(
        1 to TimeSlotEntity(1, "08:00", "08:45"),
        2 to TimeSlotEntity(2, "08:55", "09:40"),
        3 to TimeSlotEntity(3, "10:00", "10:45"),
        5 to TimeSlotEntity(5, "13:30", "14:15"),
    )

    private fun course(
        name: String,
        day: Int,
        startSlot: Int,
        endSlot: Int,
        weekType: String = "all",
        weeks: String = "",
    ) = CourseEntity(
        name = name,
        dayOfWeek = day,
        startSlot = startSlot,
        endSlot = endSlot,
        weekType = weekType,
        weeks = weeks,
    )

    @Test
    fun `取今天还没下课的最早一节`() {
        val courses = listOf(
            course("已上完的课", day = 4, startSlot = 1, endSlot = 2),
            course("高等数学", day = 4, startSlot = 3, endSlot = 3),
            course("大学英语", day = 4, startSlot = 5, endSlot = 5),
        )
        // 09:45 时第 1-2 节（08:55-09:40）已经下课，第 3 节（10:00-10:45）还没开始
        val next = findUpcomingCourse(courses, slots, week = 6, today = thursday, now = LocalTime.of(9, 45))
        assertEquals("高等数学", next?.course?.name)
        assertEquals(0, next?.dayOffset)
        assertEquals("10:00", next?.startTime)
    }

    @Test
    fun `今天的都下课了就找下一个有课的日子`() {
        val courses = listOf(
            course("今天的课", day = 4, startSlot = 1, endSlot = 2),
            course("周五的课", day = 5, startSlot = 1, endSlot = 2),
            course("下周一的课", day = 1, startSlot = 3, endSlot = 3),
        )
        val next = findUpcomingCourse(courses, slots, week = 6, today = thursday, now = LocalTime.of(20, 0))
        assertEquals("周五的课", next?.course?.name)
        assertEquals(1, next?.dayOffset)
    }

    @Test
    fun `今天没课就往后翻到有课的那天`() {
        val courses = listOf(course("周一的课", day = 1, startSlot = 3, endSlot = 3))
        // 周四往后翻：周五、周六、周日都没课 → 落到下周一（offset = 4）
        val next = findUpcomingCourse(courses, slots, week = 6, today = thursday, now = LocalTime.of(9, 0))
        assertEquals(4, next?.dayOffset)
    }

    @Test
    fun `法定放假日不排课`() {
        val courses = listOf(
            course("今天本来有课", day = 4, startSlot = 3, endSlot = 3),
            course("周五的课", day = 5, startSlot = 1, endSlot = 2),
        )
        val next = findUpcomingCourse(
            courses, slots, week = 6,
            today = thursday, now = LocalTime.of(9, 0),
            isOffDay = { it == thursday },
        )
        assertEquals("放假日不该把那天的课当成下一节", "周五的课", next?.course?.name)
    }

    @Test
    fun `放假日也不会被当作未来的那一天`() {
        val courses = listOf(course("周五的课", day = 5, startSlot = 1, endSlot = 2))
        val friday = thursday.plusDays(1)
        val next = findUpcomingCourse(
            courses, slots, week = 6,
            today = thursday, now = LocalTime.of(20, 0),
            isOffDay = { it == friday },
        )
        assertNull("唯一有课的那天放假了，就该没有下一节课", next)
    }

    @Test
    fun `不在本周出现的课不算`() {
        // 单周课在第 6 周（双周）不该出现
        val courses = listOf(course("单周课", day = 4, startSlot = 3, endSlot = 3, weekType = "odd"))
        assertNull(findUpcomingCourse(courses, slots, week = 6, today = thursday, now = LocalTime.of(8, 30)))

        assertEquals(
            "单周课",
            findUpcomingCourse(courses, slots, week = 5, today = thursday, now = LocalTime.of(8, 30))
                ?.course?.name,
        )
    }

    @Test
    fun `节次表缺这一节时仍然返回课程但时刻为空`() {
        val courses = listOf(course("体育", day = 4, startSlot = 11, endSlot = 11))
        val next = findUpcomingCourse(courses, slots, week = 6, today = thursday, now = LocalTime.of(9, 0))
        assertEquals("体育", next?.course?.name)
        assertEquals("", next?.startTime)
        assertNull("算不出时刻就不许编一个出来", next?.startAt)
    }

    @Test
    fun `开课时刻按给定日期算`() {
        val courses = listOf(course("明天的课", day = 5, startSlot = 1, endSlot = 2))
        val next = findUpcomingCourse(courses, slots, week = 6, today = thursday, now = LocalTime.of(20, 0))
        assertEquals(thursday.plusDays(1).atTime(8, 0), next?.startAt)
    }

    @Test
    fun `没有任何课就返回空`() {
        assertNull(findUpcomingCourse(emptyList(), slots, week = 1, today = thursday, now = LocalTime.of(9, 0)))
    }

    @Test
    fun `今天的课程清单在放假日为空`() {
        val courses = listOf(course("今天的课", day = 4, startSlot = 1, endSlot = 2))
        assertEquals(1, coursesOn(courses, week = 6, date = thursday).size)
        assertEquals(0, coursesOn(courses, week = 6, date = thursday) { true }.size)
    }
}

/**
 * 信号采集里两段**不碰 Android** 的逻辑：考试取哪一场、新成绩算几门。
 *
 * 采集的其余部分要 Room 与 Context（那部分靠真机验收），但这两段是纯函数，
 * 它们的错法很具体：把去年的考试报成"还有 -300 天"、把几十门历史成绩一次报成"新成绩"。
 */
class ProactiveEvaluatorTest {

    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun `取最靠前的未来考试`() {
        val exams = listOf(
            exam("大学英语", "2026-10-20"),
            exam("高等数学", "2026-10-10"),
            exam("线性代数", "2026-11-01"),
        )
        val signal = ProactiveEvaluator.examSignal(exams, today)
        assertEquals("高等数学", signal?.courseName)
        assertEquals(2L, signal?.daysLeft)
    }

    @Test
    fun `已经过去的考试不算`() {
        // 通知解析会留下一批老考试且没人勾完成 —— 不排除的话气泡会说
        // "高等数学已过去 300 天"
        val exams = listOf(exam("去年的课", "2025-12-01"), exam("后天的课", "2026-10-10"))
        val signal = ProactiveEvaluator.examSignal(exams, today)
        assertEquals("后天的课", signal?.courseName)
    }

    @Test
    fun `勾完成的考试不算`() {
        val exams = listOf(
            exam("已考完", "2026-10-09").copy(completed = true),
            exam("还没考", "2026-10-11"),
        )
        assertEquals("还没考", ProactiveEvaluator.examSignal(exams, today)?.courseName)
    }

    @Test
    fun `日期解析不出的考试被跳过`() {
        val exams = listOf(exam("时间待定", "见通知"), exam("下周的课", "2026-10-15"))
        assertEquals("下周的课", ProactiveEvaluator.examSignal(exams, today)?.courseName)
    }

    @Test
    fun `中文写法的考试日期也算得出来`() {
        val signal = ProactiveEvaluator.examSignal(listOf(exam("高数", "10月10日")), today)
        assertEquals(2L, signal?.daysLeft)
    }

    @Test
    fun `没有考试时没有信号`() {
        assertNull(ProactiveEvaluator.examSignal(emptyList(), today))
    }

    @Test
    fun `只数游标之后落库的成绩`() {
        val grades = listOf(
            grade("高数", savedAt = 100L),
            grade("英语", savedAt = 200L),
            grade("物理", savedAt = 300L),
        )
        assertEquals(2, ProactiveEvaluator.newGradeCount(grades, seenAt = 100L))
        assertEquals(0, ProactiveEvaluator.newGradeCount(grades, seenAt = 300L))
        assertEquals(3, ProactiveEvaluator.newGradeCount(grades, seenAt = 0L))
    }

    private fun exam(name: String, date: String) = ExamEntity(
        courseName = name,
        date = date,
        time = "14:00-16:00",
        location = "A101",
    )

    private fun grade(name: String, savedAt: Long) = SavedGradeEntity(
        courseName = name,
        termLabel = "2026-2027-1",
        savedAt = savedAt,
    )
}
