package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.library.LibraryDoc
import com.pocket.xiaoli.data.library.LibraryFile
import com.pocket.xiaoli.data.quickanswer.QuickAnswerBank
import com.pocket.xiaoli.data.quickanswer.QuickAnswerChapter
import com.pocket.xiaoli.data.quickanswer.QuickAnswerCourse
import com.pocket.xiaoli.data.quickanswer.QuickAnswerQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四源合一：资料库的适配层。
 *
 * ## 为什么这些断言是"守门"而不是"覆盖率"
 *
 * 1. **id 前缀只有一处产生** —— 检索结果只回 documentId，
 *    `ContentSource.of` 靠前缀认来源。前缀写错一个字符，来源条会全部落到「校园知识库」，
 *    而界面看起来完全正常（只是标签不对）—— 这种错只能靠断言钉住。
 * 2. **没摘要的文件对 AI 不可见** —— 让模型从文件名硬猜内容是最容易编造的分支；
 *    设计里定的是"作者写一句摘要"，没写就不进检索。
 * 3. **引用编号按来源分前缀** —— 正文里的 `[L1]` 与来源条上的 `L1` 必须同源。
 */
class UnifiedContentTest {

    private val bank = QuickAnswerBank(
        courses = listOf(
            QuickAnswerCourse(
                id = "ds",
                name = "数据结构",
                subject = "计算机",
                chapters = listOf(
                    QuickAnswerChapter(
                        index = 3,
                        title = "线性表",
                        questions = listOf(
                            QuickAnswerQuestion(
                                id = "ds-3-1",
                                question = "顺序表和链表怎么选？",
                                answerPoints = listOf("按随机访问需求选顺序表", "频繁插入删除选链表"),
                                hint = "这题必考",
                                tags = listOf("线性表"),
                            ),
                            // 没有答案要点的题：模型只能读到这里，一条空答案等于让它编。
                            QuickAnswerQuestion(id = "ds-3-2", question = "空题", answerPoints = emptyList()),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `快问快答压成文档，题干当标题，要点拼正文`() {
        val docs = UnifiedContent.quickAnswers(bank)
        assertEquals(1, docs.size)
        val doc = docs.single()
        assertEquals("qa:ds-3-1", doc.id)
        assertEquals("顺序表和链表怎么选？", doc.title)
        assertTrue(doc.body.contains("数据结构 · 第3章 线性表"))
        assertTrue(doc.body.contains("频繁插入删除选链表"))
        assertTrue(doc.body.contains("这题必考"))
        assertEquals("快问快答（数据结构）", doc.sourceLabel)
    }

    @Test
    fun `没摘要的资料文件不进检索`() {
        val files = listOf(
            LibraryFile(id = "a", name = "有摘要.pdf", summary = "第一章课件。"),
            LibraryFile(id = "b", name = "没摘要.pdf"),
        )
        val docs = UnifiedContent.libraryFiles(files)
        assertEquals(1, docs.size)
        assertEquals("lib:file:a", docs.single().id)
        assertTrue(docs.single().body.startsWith("第一章课件。"))
        assertTrue("正文要告诉模型这是可下载文件", docs.single().body.contains("服务 → 资料站"))
    }

    @Test
    fun `正文条目有全文用全文，没有就退回摘要`() {
        val docs = UnifiedContent.libraryDocs(
            listOf(
                LibraryDoc(id = "full", title = "全文条目", summary = "摘要", body = "1.1 正文"),
                LibraryDoc(id = "brief", title = "摘要条目", summary = "只有摘要"),
            )
        )
        assertEquals(2, docs.size)
        assertEquals("lib:doc:full", docs[0].id)
        assertTrue(docs[0].body.contains("1.1 正文"))
        assertFalse(docs[0].body.contains("摘要"))
        assertEquals("只有摘要", docs[1].body)
    }

    @Test
    fun `id 前缀决定来源，未知前缀不丢来源`() {
        assertEquals(ContentSource.KNOWLEDGE, ContentSource.of("kb:图书馆/开放时间"))
        assertEquals(ContentSource.QUICK_ANSWER, ContentSource.of("qa:ds-3-1"))
        assertEquals(ContentSource.FAQ, ContentSource.of("faq:faq_import_way"))
        assertEquals(ContentSource.LIBRARY, ContentSource.of("lib:file:a"))
        assertEquals(ContentSource.LIBRARY, ContentSource.of("lib:doc:a"))
        // 未知前缀按知识库兜底：宁可标签不对，也不要让来源条消失。
        assertEquals(ContentSource.KNOWLEDGE, ContentSource.of("mystery:1"))
    }

    @Test
    fun `四源同一份检索器，引用编号按来源分前缀且来源条同源`() {
        val documents = buildList {
            add(
                KnowledgeDocument(
                    id = ContentSource.KNOWLEDGE.prefix + "图书馆/开放时间",
                    title = "图书馆开放时间",
                    body = "平时 07:00-22:00 开放，周末 08:00-21:00。",
                    sourceLabel = "图书馆",
                )
            )
            addAll(UnifiedContent.libraryFiles(listOf(
                LibraryFile(
                    id = "gaoshu-final",
                    name = "高等数学 期末真题.pdf",
                    subject = "数学",
                    course = "高等数学",
                    kind = "历年卷",
                    sizeBytes = 2_048_000L,
                    summary = "2024 与 2025 两套期末真题，含参考答案。",
                )
            )))
        }

        val hits = KeywordRetriever.search("期末真题", documents, topK = 5)
        assertTrue("资料站条目要能被同一个检索器命中", hits.any { it.document.id.startsWith("lib:") })

        val outcome = AssistantFormatters.library(hits, documents.size, "期末真题")
        val libIndex = hits.indexOfFirst { it.document.id.startsWith("lib:") }
        val marker = "L${libIndex + 1}"
        assertTrue("正文里的编号是 $marker，实际：${outcome.text}", outcome.text.contains("[$marker]"))
        assertTrue(outcome.text.contains("来源：资料站"))
        assertEquals(marker, outcome.sources[libIndex].marker)
        assertEquals("来源条与正文必须是同一次映射", "高等数学 期末真题.pdf", outcome.sources[libIndex].title)
    }

    @Test
    fun `资料库没命中时的文案指向资料站而不是知识库`() {
        val outcome = AssistantFormatters.library(emptyList(), totalDocuments = 3, query = "量子纠缠")
        assertTrue(outcome.text.contains("资料库没有查到"))
        assertTrue(outcome.text.contains("服务 → 资料站"))
        assertTrue(outcome.sources.isEmpty())
    }
}
