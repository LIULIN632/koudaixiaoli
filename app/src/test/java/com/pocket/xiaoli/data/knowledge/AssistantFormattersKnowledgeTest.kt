package com.pocket.xiaoli.data.knowledge

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识库回答排版。
 *
 * 格式按 `docs/AssistantContract.md` §8 的约定：`[K1] 标题 / 更新时间 / 正文片段`。
 * 这里最要紧的是**两种退化情况的文案必须区分**：
 * 「库里是空的」和「有内容但没问中」对用户是完全不同的两件事。
 */
class AssistantFormattersKnowledgeTest {

    private fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun hit(
        title: String = "图书馆开放时间",
        snippet: String = "平时开放时间：07:00-22:00",
        updatedAt: Long? = null,
        effectiveFrom: LocalDate? = null,
        owner: String = "",
        sourceLabel: String = "图书馆官网",
        sourceUrl: String? = null,
    ): KnowledgeHit {
        val document = KnowledgeDocument(
            id = "场馆信息/图书馆开放时间",
            title = title,
            body = snippet,
            sourceLabel = sourceLabel,
            sourceUrl = sourceUrl,
            updatedAt = updatedAt,
            effectiveFrom = effectiveFrom,
            owner = owner,
        )
        return KnowledgeHit(
            chunk = KnowledgeChunk(
                id = document.id + "#0",
                documentId = document.id,
                order = 0,
                text = snippet,
            ),
            document = document,
            score = 10,
            rankScore = 10.0,
        )
    }

    @Test
    fun `命中时输出编号标题更新时间和责任人`() {
        val output = AssistantFormatters.knowledge(
            hits = listOf(
                hit(updatedAt = epochOf(LocalDate.of(2025, 9, 20)), owner = "图书馆"),
            ),
            totalDocuments = 3,
            query = "图书馆几点开门",
        ).text

        assertTrue(output.startsWith("[K1] 图书馆开放时间"))
        assertTrue(output.contains("更新于 2025-09-20"))
        assertTrue(output.contains("责任人：图书馆"))
        assertTrue(output.contains("平时开放时间：07:00-22:00"))
    }

    @Test
    fun `没有更新时间和责任人时相应段落省略`() {
        val output = AssistantFormatters.knowledge(
            hits = listOf(hit()),
            totalDocuments = 1,
            query = "图书馆",
        ).text

        assertEquals("[K1] 图书馆开放时间\n平时开放时间：07:00-22:00", output)
    }

    @Test
    fun `生效日期会显示出来`() {
        val output = AssistantFormatters.knowledge(
            hits = listOf(hit(effectiveFrom = LocalDate.of(2025, 9, 1))),
            totalDocuments = 1,
            query = "图书馆",
        ).text

        assertTrue(output.contains("自 2025-09-01 起生效"))
    }

    @Test
    fun `多条命中按顺序编号`() {
        val output = AssistantFormatters.knowledge(
            hits = listOf(hit(title = "甲"), hit(title = "乙"), hit(title = "丙")),
            totalDocuments = 3,
            query = "问题",
        ).text

        assertTrue(output.contains("[K1] 甲"))
        assertTrue(output.contains("[K2] 乙"))
        assertTrue(output.contains("[K3] 丙"))
        assertEquals(1, output.split("[K2]").size - 1)
    }

    @Test
    fun `库为空时说的是暂无内容而不是没查到`() {
        val output = AssistantFormatters.knowledge(
            hits = emptyList(),
            totalDocuments = 0,
            query = "图书馆几点开门",
        ).text

        assertTrue(output.contains("暂无内容"))
        assertFalse("库里没内容时不该说「没查到」，那会让用户以为自己问错了", output.contains("没有查到"))
    }

    @Test
    fun `有内容但没命中时回显检索词并给官方入口`() {
        val output = AssistantFormatters.knowledge(
            hits = emptyList(),
            totalDocuments = 12,
            query = "量子纠缠",
        ).text

        assertTrue(output.contains("没有查到"))
        assertTrue(output.contains("量子纠缠"))
        assertTrue("§8 要求上下文不足时给官方入口", output.contains("教务处") || output.contains("服务"))
    }

    @Test
    fun `检索词前后的空白被裁掉`() {
        val output = AssistantFormatters.knowledge(
            hits = emptyList(),
            totalDocuments = 1,
            query = "  补考  ",
        ).text

        assertTrue(output.contains("「补考」"))
        assertFalse(output.contains("「  补考  」"))
    }

    @Test
    fun `命中时来源条与正文编号同源`() {
        val outcome = AssistantFormatters.knowledge(
            hits = listOf(
                hit(
                    title = "甲",
                    updatedAt = epochOf(LocalDate.of(2025, 9, 20)),
                    owner = "图书馆",
                    sourceLabel = "图书馆官网",
                    sourceUrl = "https://lib.example.edu.cn/hours",
                ),
                hit(title = "乙", snippet = "另一段\n带换行的正文"),
            ),
            totalDocuments = 2,
            query = "开放时间",
        )

        assertEquals(2, outcome.sources.size)
        assertEquals("K1", outcome.sources[0].marker)
        assertEquals("K2", outcome.sources[1].marker)
        assertEquals("甲", outcome.sources[0].title)
        assertTrue(outcome.sources[0].meta.contains("来源：图书馆官网"))
        assertTrue(outcome.sources[0].meta.contains("更新于 2025-09-20"))
        assertTrue(outcome.sources[0].meta.contains("责任人：图书馆"))
        assertEquals("https://lib.example.edu.cn/hours", outcome.sources[0].url)
        // 来源条只留两行，换行要压平；回灌给模型的 text 仍保持原样
        assertEquals("另一段 带换行的正文", outcome.sources[1].snippet)
        assertTrue(outcome.text.contains("另一段\n带换行的正文"))
    }

    @Test
    fun `来源链接只接受 http 与 https`() {
        val outcome = AssistantFormatters.knowledge(
            hits = listOf(
                hit(sourceUrl = "javascript:alert(1)"),
                hit(title = "乙", sourceUrl = "http://example.edu.cn/a"),
            ),
            totalDocuments = 2,
            query = "图书馆",
        )

        assertEquals(null, outcome.sources[0].url)
        assertEquals("http://example.edu.cn/a", outcome.sources[1].url)
    }

    @Test
    fun `空库与未命中都不产出来源`() {
        val empty = AssistantFormatters.knowledge(emptyList(), 0, "图书馆")
        val miss = AssistantFormatters.knowledge(emptyList(), 5, "量子纠缠")

        assertTrue(empty.sources.isEmpty())
        assertTrue(miss.sources.isEmpty())
    }
}
