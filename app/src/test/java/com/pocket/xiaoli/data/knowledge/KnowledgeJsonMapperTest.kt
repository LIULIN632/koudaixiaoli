package com.pocket.xiaoli.data.knowledge

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON DTO → 领域模型。容错规则一条一条钉住。 */
class KnowledgeJsonMapperTest {

    private fun doc(
        id: String = "场馆信息/图书馆开放时间",
        title: String = "图书馆开放时间",
        body: String = "平时开放时间：07:00-22:00",
        category: String = "场馆信息",
        owner: String = "图书馆",
        sourceLabel: String = "图书馆官网",
        expiryPolicy: String = "hard",
        effectiveFrom: String = "2025-09-01",
        effectiveTo: String = "9999-12-31",
        updatedAt: Long = 1_758_297_600_000L,
        tags: List<String> = listOf("图书馆", "开放时间"),
    ) = KnowledgeDocJson(
        id = id, title = title, body = body, category = category, owner = owner,
        sourceLabel = sourceLabel, expiryPolicy = expiryPolicy,
        effectiveFrom = effectiveFrom, effectiveTo = effectiveTo,
        updatedAt = updatedAt, tags = tags,
    )

    private fun index(vararg docs: KnowledgeDocJson) = KnowledgeIndexJson(docs = docs.toList())

    @Test
    fun `全部字段正常映射`() {
        val result = KnowledgeJsonMapper.toDocuments(index(doc()))
        assertEquals(1, result.size)
        val document = result[0]
        assertEquals("场馆信息/图书馆开放时间", document.id)
        assertEquals("图书馆开放时间", document.title)
        assertEquals("场馆信息", document.category)
        assertEquals("图书馆", document.owner)
        assertEquals("图书馆官网", document.sourceLabel)
        assertEquals(LocalDate.of(2025, 9, 1), document.effectiveFrom)
        assertEquals(ExpiryPolicy.NO_EXPIRY, document.effectiveTo)
        assertEquals(1_758_297_600_000L, document.updatedAt)
        assertEquals(listOf("图书馆", "开放时间"), document.tags)
        assertEquals(ExpiryKind.HARD, document.expiryKind)
    }

    @Test
    fun `空文档列表映射为空`() {
        assertTrue(KnowledgeJsonMapper.toDocuments(KnowledgeIndexJson()).isEmpty())
    }

    @Test
    fun `id 为空白的条目被丢弃`() {
        assertTrue(KnowledgeJsonMapper.toDocuments(index(doc(id = "   "))).isEmpty())
    }

    @Test
    fun `标题为空白的条目被丢弃`() {
        assertTrue(KnowledgeJsonMapper.toDocuments(index(doc(title = ""))).isEmpty())
    }

    @Test
    fun `正文为空白的条目被丢弃`() {
        // 只有标签没有正文的条目送进检索只会产生垃圾命中
        assertTrue(KnowledgeJsonMapper.toDocuments(index(doc(body = ""))).isEmpty())
    }

    @Test
    fun `坏条目被丢弃但好条目仍保留`() {
        val result = KnowledgeJsonMapper.toDocuments(index(doc(id = ""), doc(title = "好的")))
        assertEquals(listOf("好的"), result.map { it.title })
    }

    @Test
    fun `非法日期变 null 而不是抛异常`() {
        val result = KnowledgeJsonMapper.toDocuments(
            index(doc(effectiveFrom = "2025/09/01", effectiveTo = "随便什么")),
        )
        assertNull(result[0].effectiveFrom)
        assertNull(result[0].effectiveTo)
    }

    @Test
    fun `空日期变 null`() {
        val result = KnowledgeJsonMapper.toDocuments(index(doc(effectiveFrom = "", effectiveTo = "")))
        assertNull(result[0].effectiveFrom)
        assertNull(result[0].effectiveTo)
    }

    @Test
    fun `日期解析容错`() {
        assertEquals(LocalDate.of(2025, 9, 1), KnowledgeJsonMapper.parseDate("2025-09-01"))
        assertEquals(LocalDate.of(2025, 9, 1), KnowledgeJsonMapper.parseDate("  2025-09-01  "))
        assertEquals(ExpiryPolicy.NO_EXPIRY, KnowledgeJsonMapper.parseDate("9999-12-31"))
        assertNull(KnowledgeJsonMapper.parseDate(""))
        assertNull(KnowledgeJsonMapper.parseDate("2025-13-01"))
        assertNull(KnowledgeJsonMapper.parseDate("2025-9-1"))
        assertNull(KnowledgeJsonMapper.parseDate("昨天"))
    }

    @Test
    fun `soft 大小写不敏感`() {
        assertEquals(ExpiryKind.SOFT, KnowledgeJsonMapper.expiryKindOf("soft"))
        assertEquals(ExpiryKind.SOFT, KnowledgeJsonMapper.expiryKindOf("SOFT"))
        assertEquals(ExpiryKind.SOFT, KnowledgeJsonMapper.expiryKindOf(" soft "))
    }

    @Test
    fun `未知的 expiry_policy 一律回落到 hard`() {
        // 安全侧：宁可少答，不能把过期信息当现行安排
        assertEquals(ExpiryKind.HARD, KnowledgeJsonMapper.expiryKindOf(""))
        assertEquals(ExpiryKind.HARD, KnowledgeJsonMapper.expiryKindOf("maybe"))
        assertEquals(ExpiryKind.HARD, KnowledgeJsonMapper.expiryKindOf("hard"))
    }

    @Test
    fun `updated_at 为 0 时变 null 不参与时间加权`() {
        val result = KnowledgeJsonMapper.toDocuments(index(doc(updatedAt = 0L)))
        assertNull(result[0].updatedAt)
    }

    @Test
    fun `updated_at 为负数时也变 null`() {
        val result = KnowledgeJsonMapper.toDocuments(index(doc(updatedAt = -1L)))
        assertNull(result[0].updatedAt)
    }

    @Test
    fun `标签里的空白项被过滤`() {
        val result = KnowledgeJsonMapper.toDocuments(index(doc(tags = listOf("图书馆", " ", ""))))
        assertEquals(listOf("图书馆"), result[0].tags)
    }

    @Test
    fun `保留输入顺序`() {
        val result = KnowledgeJsonMapper.toDocuments(
            index(doc(id = "a", title = "甲"), doc(id = "b", title = "乙"), doc(id = "c", title = "丙")),
        )
        assertEquals(listOf("甲", "乙", "丙"), result.map { it.title })
    }
}
