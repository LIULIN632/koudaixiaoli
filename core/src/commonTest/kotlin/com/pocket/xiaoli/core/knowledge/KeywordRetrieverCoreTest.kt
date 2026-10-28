package com.pocket.xiaoli.core.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeywordRetrieverCoreTest {

    private val oneYearMillis = 365L * 86_400_000L

    @Test
    fun `empty query returns nothing`() {
        val docs = listOf(KnowledgeDoc("d1", "校园网", "需要实名认证"))
        assertEquals(emptyList(), KeywordRetrieverCore.search("   ", docs, nowMillis = 0L))
    }

    @Test
    fun `keyword hit ranks above unrelated document`() {
        val docs = listOf(
            KnowledgeDoc("vpn", "VPN 账户申请", "在网络中心办理，需要校园卡。"),
            KnowledgeDoc("lib", "图书馆开放时间", "每天 08:00-22:30。"),
        )
        val hits = KeywordRetrieverCore.search("VPN 申请", docs, nowMillis = 0L)
        assertEquals("vpn", hits.first().documentId)
        assertTrue(hits.size >= 1)
    }

    @Test
    fun `chunks split on blank lines`() {
        assertEquals(
            listOf("第一段", "第二段"),
            KeywordRetrieverCore.chunkTexts("第一段\n\n第二段\n"),
        )
    }

    @Test
    fun `recency weight can flip otherwise equal scores`() {
        val now = 10L * oneYearMillis
        val docs = listOf(
            // 内容相同 → 原始分相同；只有日期不同，加权后新文档应排前
            KnowledgeDoc("old", "放假安排", "国庆放假七天", updatedAt = now - oneYearMillis),
            KnowledgeDoc("new", "放假安排", "国庆放假七天", updatedAt = now - 86_400_000L),
        )
        val hits = KeywordRetrieverCore.search("放假安排", docs, nowMillis = now)
        assertEquals("new", hits.first().documentId)
        assertTrue(hits.first().rankScore > hits.last().rankScore)
    }

    @Test
    fun `missing updatedAt keeps rankScore equal to score`() {
        val docs = listOf(KnowledgeDoc("d1", "补办校园卡", "信息中心办理"))
        val hit = KeywordRetrieverCore.search("校园卡", docs, nowMillis = 0L).first()
        assertEquals(hit.score.toDouble(), hit.rankScore)
    }

    @Test
    fun `recency factor clamps future timestamps`() {
        val now = 1_000_000L
        // 未来时间戳：年龄 clamp 到 0 → 新鲜度 1.0（不产生暴击式加权）
        assertEquals(1.0, KeywordRetrieverCore.recencyFactor(now + 5 * oneYearMillis, now))
        assertEquals(0.0, KeywordRetrieverCore.recencyFactor(null, now))
    }
}
