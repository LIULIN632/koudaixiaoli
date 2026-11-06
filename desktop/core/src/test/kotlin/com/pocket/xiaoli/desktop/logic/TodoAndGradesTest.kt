package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.academic.ZfGradeItem
import com.pocket.xiaoli.desktop.data.Todo
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 待办分桶、成绩 CSV 与统计、离线回答的判据。 */
class TodoAndGradesTest {

    private val today = LocalDate.of(2026, 11, 2)

    private fun todo(id: String, due: String = "", done: Boolean = false, created: Long = 1) =
        Todo(id = id, title = "T$id", due = due, done = done, createdAt = created)

    private fun grade(name: String, score: String, credits: String, gpa: String = "") =
        ZfGradeItem(
            courseName = name,
            credits = credits,
            totalScore = score,
            gpa = gpa,
            regular = "",
            final = "",
            midterm = "",
            examType = "正常考试",
            termLabel = "2026-第1学期",
        )

    // ── 待办分桶 ──

    @Test
    fun `按逾期 今天 本周 以后 无日期 已完成分组`() {
        val groups = groupTodos(
            listOf(
                todo("late", "2026-10-20"),
                todo("today", "2026-11-02"),
                todo("week", "2026-11-06"),
                todo("later", "2026-12-01"),
                todo("none", ""),
                todo("done", "2026-11-01", done = true),
            ),
            today,
        )
        assertEquals(
            listOf(TodoBucket.OVERDUE, TodoBucket.TODAY, TodoBucket.WEEK, TodoBucket.LATER, TodoBucket.NO_DUE, TodoBucket.DONE),
            groups.map { it.bucket },
        )
        assertEquals("已逾期", groups.first().label)
        assertEquals(listOf("late"), groups.first().todos.map { it.id })
    }

    @Test
    fun `空桶不出现`() {
        val groups = groupTodos(listOf(todo("only", "2026-11-02")), today)
        assertEquals(1, groups.size)
        assertEquals(TodoBucket.TODAY, groups.single().bucket)
    }

    @Test
    fun `看不懂的日期算没填而不是逾期`() {
        val groups = groupTodos(listOf(todo("weird", "1.15"), todo("cn", "待定")), today)
        assertEquals(listOf(TodoBucket.NO_DUE), groups.map { it.bucket })
    }

    @Test
    fun `逾期最久的排前面`() {
        val groups = groupTodos(listOf(todo("a", "2026-10-30"), todo("b", "2026-10-05")), today)
        assertEquals(listOf("b", "a"), groups.first().todos.map { it.id })
    }

    // ── 成绩 CSV 与统计 ──

    @Test
    fun `CSV 带 BOM 且每格加引号`() {
        val csv = gradesToCsv(listOf(grade("高等数学", "92", "4.0", "4.0"), grade("大学英语（三）", "85", "3")))
        assertTrue(csv.startsWith("﻿"), "Excel 需要 BOM 才不乱码")
        val lines = csv.removePrefix("﻿").trim().split("\r\n")
        assertEquals(3, lines.size)
        assertEquals("\"学期\",\"课程\",\"总分\",\"绩点\",\"学分\",\"考试类型\",\"平时\",\"期中\",\"期末\"", lines.first())
        assertTrue(lines[1].startsWith("\"2026-第1学期\",\"高等数学\",\"92\",\"4.0\",\"4.0\""), lines[1])
    }

    @Test
    fun `引号翻倍不破坏列数`() {
        val csv = gradesToCsv(listOf(grade("他说\"很难\"", "60", "2")))
        val row = csv.removePrefix("﻿").trim().split("\r\n")[1]
        assertEquals(9, row.split("\",\"").size, "转义后仍应是 9 列：$row")
        assertTrue("\"他说\"\"很难\"\"" in row, row)
    }

    @Test
    fun `统计只算能解析的数字`() {
        val stats = gradeStats(
            listOf(
                grade("A", "92", "4", "4.0"),
                grade("B", "88", "3", "3.5"),
                grade("C", "优秀", "2", ""),
            )
        )
        assertEquals(3, stats.count)
        assertEquals(90.0, stats.average)
        assertEquals(9.0, stats.credits)
        assertEquals(3.75, stats.gpa)
    }

    @Test
    fun `空列表统计不崩`() {
        val stats = gradeStats(emptyList())
        assertEquals(0, stats.count)
        assertEquals(null, stats.average)
        assertEquals(0.0, stats.credits)
        assertEquals(null, stats.gpa)
    }

    // ── 离线回答 ──

    @Test
    fun `同一篇文档的多段命中只说一遍`() {
        val doc = com.pocket.xiaoli.desktop.data.KnowledgeDoc(
            id = "lib-1",
            title = "图书馆开放时间",
            category = "校园",
            campus = "本校区",
            expiryPolicy = "",
            owner = "",
            sourceLabel = "",
            effectiveFrom = "",
            effectiveTo = "",
            updatedAt = 0L,
            tags = listOf("图书馆"),
            body = "周一至周五 08:00-22:30，周末 09:00-21:00。",
        )
        val hits = listOf(
            Knowledge.Hit(doc = doc, score = 9.0, snippet = "周一至周五 08:00-22:30", expired = false),
            Knowledge.Hit(doc = doc, score = 7.0, snippet = "周末 09:00-21:00", expired = false),
        )
        val answer = answerFromKnowledge(hits, "图书馆几点关门")!!
        assertEquals(1, answer.sourceTitles.size, "同文档不该重复：${answer.sourceTitles}")
        assertTrue("先按本机知识库答你" in answer.text)
    }

    @Test
    fun `关键词拆出连续段与二字组合`() {
        val keys = keywordsOf("数据结构 绪论")
        assertTrue("数据结构" in keys)
        assertTrue("据结" in keys, "中文需要二字组合才能匹配到部分写法：$keys")
    }

    @Test
    fun `快问快答按题干与标签匹配`() {
        val bank = sampleBank()
        val hits = matchQuickAnswer("绪论里讲了什么", bank, limit = 3)
        assertEquals(1, hits.size)
        assertEquals("第一章 绪论", hits.single().question)
        assertTrue(matchQuickAnswer("完全不相干的问题", bank).isEmpty())
    }

    private fun sampleBank() = listOf(
        com.pocket.xiaoli.desktop.data.QaCourse(
            id = "ds",
            subject = "数据结构",
            name = "数据结构",
            teacher = "李慧敏",
            chapters = listOf(
                com.pocket.xiaoli.desktop.data.QaChapter(
                    index = 1,
                    title = "绪论",
                    questions = listOf(
                        com.pocket.xiaoli.desktop.data.QaQuestion(
                            id = "q1",
                            question = "第一章 绪论",
                            answerPoints = listOf("研究的是什么", "基本术语"),
                            hint = "先说清数据结构三要素",
                            tags = listOf("绪论", "三要素"),
                        )
                    ),
                ),
                com.pocket.xiaoli.desktop.data.QaChapter(
                    index = 2,
                    title = "线性表",
                    questions = listOf(
                        com.pocket.xiaoli.desktop.data.QaQuestion(
                            id = "q2",
                            question = "顺序表与链表怎么选",
                            answerPoints = listOf("随机访问选顺序表"),
                            hint = "",
                            tags = listOf("线性表"),
                        )
                    ),
                ),
            ),
        )
    )
}
