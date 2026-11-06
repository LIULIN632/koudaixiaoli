package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.CalendarEvent
import com.pocket.xiaoli.desktop.data.Course
import com.pocket.xiaoli.desktop.data.KnowledgeDoc
import com.pocket.xiaoli.desktop.data.Todo
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTest {

    private fun course(
        id: String = "c1",
        name: String,
        shortName: String = "",
        teacher: String = "",
        room: String = "",
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
    ) = Course(
        id = id,
        name = name,
        shortName = shortName,
        teacher = teacher,
        room = room,
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
    )

    private fun todo(id: String = "t1", title: String, note: String = "", done: Boolean = false) =
        Todo(id = id, title = title, note = note, done = done, due = "2026-11-01")

    private fun doc(id: String = "k1", title: String, category: String = "办事流程") = KnowledgeDoc(
        id = id,
        title = title,
        category = category,
        campus = "",
        expiryPolicy = "",
        owner = "",
        sourceLabel = "",
        effectiveFrom = "",
        effectiveTo = "",
        updatedAt = 0,
        tags = emptyList(),
        body = "",
    )

    private fun hit(doc: KnowledgeDoc, rank: Double) = Knowledge.Hit(doc, rank, "", expired = false)

    @Test
    fun `空查询返回空`() {
        assertTrue(searchAll("", courses = listOf(course(name = "软件工程"))).isEmpty())
        assertTrue(searchAll("   ").isEmpty())
    }

    @Test
    fun `完全匹配排在包含匹配前面`() {
        val hits = searchAll(
            query = "软件",
            courses = listOf(
                course(id = "contains", name = "软件工程"),
                course(id = "exact", name = "软件"),
            ),
        )
        assertEquals(listOf("软件", "软件工程"), hits.map { it.title })
    }

    @Test
    fun `副字段也能命中 但分数低于标题`() {
        val byTitle = searchAll("王老师", courses = listOf(course(name = "操作系统", teacher = "王老师")))
        val byContains = searchAll("操作", courses = listOf(course(name = "操作系统", teacher = "王老师")))
        assertTrue(byTitle.single().score > 0)
        assertTrue(byTitle.single().score < byContains.single().score)
        assertEquals("课程 · 周一 第1-2节", byTitle.single().subtitle)
    }

    @Test
    fun `待办详情带上完成状态与截止日`() {
        val hit = searchAll("实验报告", todos = listOf(todo(title = "交数据结构实验报告"))).single()
        assertEquals(SearchKind.TODO, hit.kind)
        assertEquals("待办 · 未完成 · 截止 2026-11-01", hit.subtitle)
    }

    @Test
    fun `知识库名次保留 且带回原始查询`() {
        val hits = searchAll(
            query = "图书馆",
            knowledgeSearch = { _, _ ->
                listOf(hit(doc(id = "a", title = "图书馆开放时间"), 9.0), hit(doc(id = "b", title = "图书馆借书"), 5.0))
            },
        )
        assertEquals(listOf("图书馆开放时间", "图书馆借书"), hits.map { it.title })
        assertTrue(hits[0].score > hits[1].score)
        assertEquals("图书馆", hits[0].knowledgeQuery)
        assertEquals("a", hits[0].knowledgeId)
    }

    @Test
    fun `知识库同一文档多个分片只保留一条`() {
        val hits = searchAll(
            query = "图书馆",
            knowledgeSearch = { _, _ ->
                listOf(
                    hit(doc(id = "same", title = "图书馆开放时间"), 9.0),
                    hit(doc(id = "same", title = "图书馆开放时间"), 7.0),
                    hit(doc(id = "other", title = "图书馆借书"), 5.0),
                )
            },
        )
        assertEquals(listOf("图书馆开放时间", "图书馆借书"), hits.map { it.title })
    }

    @Test
    fun `校历命中带回开始日期`() {
        val hit = searchAll(
            "国庆",
            events = listOf(CalendarEvent(title = "国庆节", start = "2026-10-01", end = "2026-10-07", kind = "vacation", short = "国庆")),
        ).single()
        assertEquals(SearchKind.CALENDAR, hit.kind)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), hit.calendarStartEpochDay)
        assertEquals(3, hit.campusSection)
    }

    @Test
    fun `页面别名可命中`() {
        val pages = listOf(
            SearchPage(4, "我的", "设置", listOf("settings")),
            SearchPage(3, "服务", "校历 / 工具", listOf("campus", "校历")),
        )
        assertEquals(4, searchAll("settings", pages = pages).single().page)
        assertEquals(3, searchAll("校历", pages = pages).single().page)
    }

    @Test
    fun `同分按类型排序 并限制条数`() {
        val hits = searchAll(
            query = "课",
            courses = listOf(course(id = "c", name = "课程设计")),
            pages = listOf(SearchPage(1, "课表", "周课表", listOf("课程"))),
            limit = 1,
        )
        assertEquals(1, hits.size)
        assertEquals(SearchKind.COURSE, hits.single().kind)
    }
}
