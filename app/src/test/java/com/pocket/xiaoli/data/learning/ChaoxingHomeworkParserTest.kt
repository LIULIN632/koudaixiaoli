package com.pocket.xiaoli.data.learning

import com.pocket.xiaoli.data.DueStage
import com.pocket.xiaoli.data.dueStageOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 学习通作业列表解析的离线守门人。
 *
 * 抓取链路（课程列表 → 课程主页 → 带 enc 的作业入口）必须真实账号，解析不需要：
 * 页面结构一变，这里先红，而不是等用户看到"拉回来一堆没有截止的作业"。
 */
class ChaoxingHomeworkParserTest {

    private val today = LocalDate.of(2026, 11, 3)

    private val workListHtml = """
        <table>
          <tr>
            <td>第一章 概述</td>
            <td><a href="/work/doWork?workId=1&amp;enc=abc">第一章作业</a></td>
            <td>截止时间：2026-11-05 23:59</td>
            <td>状态：未提交</td>
          </tr>
          <tr>
            <td><a href="/work/doWork?workId=2&amp;enc=def">第二章作业</a></td>
            <td>截止时间：11月8日 22:00</td>
            <td>状态：已完成</td>
          </tr>
          <tr>
            <td><a href="/work/doWork?workId=2&amp;enc=def">第二章作业</a></td>
            <td>重复的一行</td>
          </tr>
          <tr>
            <td><a href="/work/doWork?workId=3&amp;enc=ghi">没有截止的作业</a></td>
            <td>剩余时间 3 天</td>
          </tr>
        </table>
    """.trimIndent()

    @Test
    fun `作业行解析出标题 截止与完成态 同名只留一条`() {
        val items = ChaoxingHomeworkParser.parseWorkList(workListHtml, "高等数学", today)
        assertEquals(3, items.size)
        assertEquals("第一章作业", items[0].title)
        assertEquals("2026-11-05 23:59", items[0].dueDate)
        assertFalse(items[0].completed)
        assertEquals("2026-11-08 22:00", items[1].dueDate)
        assertTrue(items[1].completed)
        assertEquals("没有截止的作业", items[2].title)
        assertEquals("", items[2].dueDate)
        assertTrue(items.all { it.courseName == "高等数学" })
    }

    @Test
    fun `中文截止不带年份 按半年规则补年`() {
        assertEquals(
            "2027-01-15",
            ChaoxingHomeworkParser.extractDueDate("截止时间：1月15日", LocalDate.of(2026, 12, 20))
        )
        assertEquals(
            "2026-11-08 22:00",
            ChaoxingHomeworkParser.extractDueDate("截止时间：2026年11月8日 22:00", today)
        )
    }

    @Test
    fun `没有截止就留空 不编日期`() {
        assertEquals("", ChaoxingHomeworkParser.extractDueDate("剩余时间 3 天", today))
        assertEquals("", ChaoxingHomeworkParser.extractDueDate("", today))
    }

    @Test
    fun `解析出的截止能直接喂给首页倒计时`() {
        val due = ChaoxingHomeworkParser.extractDueDate("截止时间：2026-11-05 23:59", today)
        assertEquals(DueStage.SOON, dueStageOf(due, LocalDateTime.of(2026, 11, 3, 10, 0)))
    }
}
