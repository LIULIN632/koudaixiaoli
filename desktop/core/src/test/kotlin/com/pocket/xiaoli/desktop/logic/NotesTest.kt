package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.notes.NoteItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 桌面笔记的展示规则：排序、摘要、空标题兜底。
 *
 * 时间戳一律显式传入 —— 靠真实时钟的话，"谁排在前面"这种断言当天就红。
 */
class NotesTest {

    private fun note(id: String, title: String = "", content: String = "", at: Long = 0L) =
        NoteItem(id = id, title = title, content = content, updatedAt = at)

    @Test
    fun `新的排在前面`() {
        val ordered = sortedNotes(listOf(note("a", at = 100), note("b", at = 300), note("c", at = 200)))
        assertEquals(listOf("b", "c", "a"), ordered.map { it.id })
    }

    @Test
    fun `同一时刻写入的按 id 稳定排序`() {
        val ordered = sortedNotes(listOf(note("x_2", at = 500), note("x_1", at = 500)))
        assertEquals(listOf("x_2", "x_1"), ordered.map { it.id })
    }

    @Test
    fun `摘要取第一个非空行并截断`() {
        assertEquals("第一行", noteExcerpt(note("a", content = "\n  \n第一行\n第二行")))
        val long = "一".repeat(60)
        val excerpt = noteExcerpt(note("b", content = long), maxChars = 40)
        assertEquals(41, excerpt.length, "40 字 + 一个省略号")
        assertTrue(excerpt.endsWith("…"))
    }

    @Test
    fun `空正文的摘要是空串而不是空白`() {
        assertEquals("", noteExcerpt(note("a", content = "   \n  ")))
    }

    @Test
    fun `标题为空时给出看得见的名字`() {
        assertEquals("空白笔记", noteTitle(note("a")))
        assertEquals("未命名笔记", noteTitle(note("b", content = "只有正文")))
        assertEquals("结构力学", noteTitle(note("c", title = "结构力学", content = "x")))
    }
}
