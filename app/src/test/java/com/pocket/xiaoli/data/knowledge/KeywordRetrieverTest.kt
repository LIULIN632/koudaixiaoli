package com.pocket.xiaoli.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordRetrieverTest {

    private val docs = listOf(
        KnowledgeDocument(
            id = "doc_map",
            title = "校园地图怎么用？",
            body = "在服务页进入校园地图，可以搜索教学楼和食堂。",
            sourceLabel = "test",
            tags = listOf("地图", "教室"),
        ),
        KnowledgeDocument(
            id = "doc_import",
            title = "如何导入课表？",
            body = "支持网页导入、教务直连导入和文件导入。",
            sourceLabel = "test",
            tags = listOf("导入", "课表"),
        ),
        KnowledgeDocument(
            id = "doc_grade",
            title = "成绩存档是什么？",
            body = "登录正方教务后可以把成绩保存到本机。",
            sourceLabel = "test",
            tags = listOf("成绩"),
        ),
    )

    @Test
    fun `exact title query ranks the matching document first`() {
        val hits = KeywordRetriever.search("校园地图怎么用", docs)
        assertTrue(hits.isNotEmpty())
        assertEquals("doc_map", hits.first().document.id)
    }

    @Test
    fun `keyword query matches body and tags`() {
        val hits = KeywordRetriever.search("导入", docs)
        assertTrue(hits.any { it.document.id == "doc_import" })
    }

    @Test
    fun `cjk phrase without spaces still matches`() {
        val hits = KeywordRetriever.search("成绩存档", docs)
        assertTrue(hits.isNotEmpty())
        assertEquals("doc_grade", hits.first().document.id)
    }

    @Test
    fun `unknown query returns empty`() {
        assertTrue(KeywordRetriever.search("量子纠缠", docs).isEmpty())
    }

    @Test
    fun `blank query returns empty`() {
        assertTrue(KeywordRetriever.search("", docs).isEmpty())
        assertTrue(KeywordRetriever.search("   ", docs).isEmpty())
    }

    @Test
    fun `topK limits result size`() {
        val hits = KeywordRetriever.search("导入 课表 地图", docs, topK = 1)
        assertTrue(hits.size <= 1)
    }

    @Test
    fun `buildChunks splits paragraphs and keeps order`() {
        val document = KnowledgeDocument(
            id = "doc_paragraphs",
            title = "标题",
            body = "第一段\n\n第二段",
            sourceLabel = "test",
        )
        val chunks = KeywordRetriever.buildChunks(listOf(document))
        assertEquals(2, chunks.size)
        assertEquals(0, chunks[0].order)
        assertEquals(1, chunks[1].order)
        assertEquals("第一段", chunks[0].text)
        assertEquals("第二段", chunks[1].text)
    }

    @Test
    fun `local faq source exposes unique labelled documents`() {
        val documents = LocalFaqKnowledgeSource.documents
        assertTrue(documents.size >= 14)
        assertEquals(documents.size, documents.map { it.id }.distinct().size)
        assertTrue(documents.all { it.sourceLabel.isNotBlank() && it.title.isNotBlank() })
        assertTrue(documents.any { it.id == "faq_binding" })
    }
    @Test
    fun `hybrid similarity is positive for related phrases`() {
        assertTrue(KeywordRetriever.cosineSimilarity("成绩存档", "成绩保存") > 0.1)
    }

    @Test
    fun `hybrid similarity is zero for unrelated phrases`() {
        assertEquals(0.0, KeywordRetriever.cosineSimilarity("量子纠缠", "校园地图"), 0.001)
    }
}
