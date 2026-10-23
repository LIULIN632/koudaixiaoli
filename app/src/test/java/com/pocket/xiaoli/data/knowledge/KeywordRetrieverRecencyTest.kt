package com.pocket.xiaoli.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间加权。
 *
 * ## 关于权重的取值
 *
 * `DEFAULT_RECENCY_WEIGHT = 0.15` 是**先验值**（设计文档 §11 欠账 3），它的设计意图是
 * 「足以让今年/去年的同类通知翻盘，不足以让弱相关但很新的内容压过强相关」。
 * 也就是说**默认权重只翻得动接近平手的分差**。
 *
 * 所以「翻盘」那条测试用显式的 `recencyWeight = 1.0` 来验证**机制本身**，
 * 并额外断言「原始分确实是旧文档更高」—— 否则测试可能只是碰巧排序正确而没验证加权。
 * 这比硬凑一个刚好能被 0.15 翻过来的夹具诚实，也更不容易在将来误红。
 */
class KeywordRetrieverRecencyTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun document(
        id: String,
        title: String,
        body: String,
        updatedAt: Long? = null,
    ) = KnowledgeDocument(
        id = id,
        title = title,
        body = body,
        sourceLabel = "test",
        updatedAt = updatedAt,
    )

    // ── 新鲜度函数本身 ────────────────────────────────────────────

    @Test
    fun `没有日期的文档新鲜度为 0`() {
        assertEquals(0.0, KeywordRetriever.recencyFactor(null, now), 1e-9)
    }

    @Test
    fun `时间戳为零或负数的新鲜度为 0`() {
        assertEquals(0.0, KeywordRetriever.recencyFactor(0L, now), 1e-9)
        assertEquals(0.0, KeywordRetriever.recencyFactor(-1L, now), 1e-9)
    }

    @Test
    fun `刚更新的新鲜度为 1`() {
        assertEquals(1.0, KeywordRetriever.recencyFactor(now, now), 1e-9)
    }

    @Test
    fun `半衰期一年后约为一半`() {
        assertEquals(0.5, KeywordRetriever.recencyFactor(now - 365 * day, now), 0.01)
    }

    @Test
    fun `时间戳在未来时不产生超过 1 的新鲜度`() {
        // 设备时钟错乱（或产物来自"未来"）不能让某篇文档获得暴击式加权
        assertEquals(1.0, KeywordRetriever.recencyFactor(now + 10 * day, now), 1e-9)
    }

    // ── 排序行为 ─────────────────────────────────────────────────

    @Test
    fun `更新的文档能靠时间加权翻盘原始分更高的旧文档`() {
        val body = "补考重修相关说明"
        // 旧文档标题里含检索词 → 原始分更高（title 命中 +8）
        val older = document(id = "old", title = "补考安排", body = body, updatedAt = now - 5 * 365 * day)
        val newer = document(id = "new", title = "重修说明", body = body, updatedAt = now)

        val unweighted = KeywordRetriever.search(
            query = "补考",
            documents = listOf(older, newer),
            recencyWeight = 0.0,
            nowMillis = now,
        )
        assertEquals("前提：旧文档的原始分确实更高", "old", unweighted.first().document.id)
        assertTrue(
            "前提：原始分排序下旧文档分数确实更高",
            unweighted.first().score > unweighted[1].score,
        )

        val weighted = KeywordRetriever.search(
            query = "补考",
            documents = listOf(older, newer),
            recencyWeight = 1.0,
            nowMillis = now,
        )
        assertEquals("加权后应该翻盘", "new", weighted.first().document.id)
    }

    @Test
    fun `权重为 0 时排序与不加权完全一致`() {
        val documents = listOf(
            document("a", "补考安排", "补考重修相关说明", updatedAt = now - 3 * 365 * day),
            document("b", "重修说明", "补考重修相关说明", updatedAt = now),
            document("c", "考试安排", "补考重修相关说明", updatedAt = now - 100 * day),
        )

        val zero = KeywordRetriever.search("补考", documents, recencyWeight = 0.0, nowMillis = now)
        val expected = zero.sortedByDescending { it.score }.map { it.document.id }

        assertEquals("权重为 0 时 rankScore 必须退化成 score", expected, zero.map { it.document.id })
    }

    @Test
    fun `没有日期的文档不会因为别的新文档而被扣分`() {
        val noDate = document("nodate", "补考安排", "补考重修相关说明", updatedAt = null)
        val fresh = document("fresh", "补考安排", "补考重修相关说明", updatedAt = now)

        val hits = KeywordRetriever.search("补考", listOf(noDate, fresh), nowMillis = now)
        val noDateHit = hits.first { it.document.id == "nodate" }

        assertEquals(
            "无日期 = 不加权，不是「减分」",
            noDateHit.score.toDouble(),
            noDateHit.rankScore,
            1e-9,
        )
    }

    @Test
    fun `同分同日期的文档保持输入顺序`() {
        val same = now - 10 * day
        val first = document("a", "放假安排", "放假安排具体内容", updatedAt = same)
        val second = document("b", "放假安排", "放假安排具体内容", updatedAt = same)

        val hits = KeywordRetriever.search("放假安排", listOf(first, second), nowMillis = now)

        assertEquals(listOf("a", "b"), hits.map { it.document.id })
    }

    @Test
    fun `无日期的既有文档排序不受影响 —— 回归护栏`() {
        // 内置 FAQ 与既有测试的文档都没有 updatedAt，
        // 这条保证本次改造对它们完全无感（rankScore 退化为 score）。
        val documents = listOf(
            document("map", "校园地图怎么用？", "在服务页进入校园地图，可以搜索教学楼和食堂。"),
            document("import", "如何导入课表？", "支持网页导入、教务直连导入和文件导入。"),
            document("grade", "成绩存档是什么？", "登录正方教务后可以把成绩保存到本机。"),
        )

        val hits = KeywordRetriever.search("校园地图怎么用", documents, nowMillis = now)

        assertEquals("map", hits.first().document.id)
        hits.forEach { assertEquals(it.score.toDouble(), it.rankScore, 1e-9) }
    }

    @Test
    fun `负权重被夹到 0 而不是把排序颠倒`() {
        val documents = listOf(
            document("a", "补考安排", "补考重修相关说明", updatedAt = now - 5 * 365 * day),
            document("b", "重修说明", "补考重修相关说明", updatedAt = now),
        )

        val negative = KeywordRetriever.search("补考", documents, recencyWeight = -1.0, nowMillis = now)
        val zero = KeywordRetriever.search("补考", documents, recencyWeight = 0.0, nowMillis = now)

        assertEquals(zero.map { it.document.id }, negative.map { it.document.id })
    }
}
