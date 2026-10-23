package com.pocket.xiaoli.data.knowledge

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时效过滤。
 *
 * 这里最要紧的一条是「今天正好等于失效日期时仍然有效」—— 日期比较写成 `today < expire`
 * 还是 `today <= expire` 差一天，而这天恰好是"通知最后一天还能用"的那天。
 */
class ExpiryPolicyTest {

    private val today = LocalDate.of(2026, 10, 3)

    private fun doc(
        expiryKind: ExpiryKind = ExpiryKind.HARD,
        effectiveFrom: LocalDate? = LocalDate.of(2025, 9, 1),
        effectiveTo: LocalDate? = null,
        body: String = "原始正文",
    ) = KnowledgeDocument(
        id = "通知/放假安排",
        title = "放假安排",
        body = body,
        sourceLabel = "校办公室",
        effectiveFrom = effectiveFrom,
        effectiveTo = effectiveTo,
        expiryKind = expiryKind,
    )

    @Test
    fun `没有失效日期时始终有效`() {
        assertEquals(ExpiryPolicy.Decision.Include, ExpiryPolicy.decide(doc(effectiveTo = null), today))
    }

    @Test
    fun `哨兵日期视为无期限`() {
        assertEquals(
            ExpiryPolicy.Decision.Include,
            ExpiryPolicy.decide(doc(effectiveTo = ExpiryPolicy.NO_EXPIRY), today),
        )
    }

    @Test
    fun `今天正好等于失效日期时仍然有效`() {
        assertEquals(
            "最后一天还没过期，差一天就是错误答案",
            ExpiryPolicy.Decision.Include,
            ExpiryPolicy.decide(doc(effectiveTo = today), today),
        )
    }

    @Test
    fun `过期的 hard 被剔除`() {
        assertEquals(
            ExpiryPolicy.Decision.Exclude,
            ExpiryPolicy.decide(doc(effectiveTo = today.minusDays(1)), today),
        )
    }

    @Test
    fun `过期的 soft 被保留并算出去期天数`() {
        assertEquals(
            ExpiryPolicy.Decision.IncludeExpired(today.minusDays(1), 1L),
            ExpiryPolicy.decide(
                doc(expiryKind = ExpiryKind.SOFT, effectiveTo = today.minusDays(1)),
                today,
            ),
        )
    }

    @Test
    fun `未来生效日期不导致排除`() {
        // 放假安排常常提前发布，排除掉反而是坏答案
        assertEquals(
            ExpiryPolicy.Decision.Include,
            ExpiryPolicy.decide(doc(effectiveFrom = today.plusDays(5)), today),
        )
    }

    @Test
    fun `apply 剔除过期的 hard`() {
        val documents = listOf(
            doc(effectiveTo = today.minusDays(30)),
            doc(effectiveTo = today.plusDays(30)),
        )
        assertEquals(1, ExpiryPolicy.apply(documents, today).size)
    }

    @Test
    fun `apply 保留过期的 soft 并在正文前插过期提醒`() {
        val expired = today.minusDays(10)
        val result = ExpiryPolicy.apply(
            listOf(doc(expiryKind = ExpiryKind.SOFT, effectiveTo = expired)),
            today,
        )

        assertEquals(1, result.size)
        assertEquals("⚠️ 此安排已于 $expired 过期（10 天前），最新安排请以官方为准。\n\n原始正文", result[0].body)
    }

    @Test
    fun `apply 保持输入顺序`() {
        val documents = listOf(
            doc(effectiveTo = today.plusDays(1), body = "第一"),
            doc(effectiveTo = today.plusDays(2), body = "第二"),
            doc(effectiveTo = today.plusDays(3), body = "第三"),
        )
        assertEquals(
            listOf("第一", "第二", "第三"),
            ExpiryPolicy.apply(documents, today).map { it.body },
        )
    }

    @Test
    fun `apply 不修改输入列表`() {
        val original = doc(expiryKind = ExpiryKind.SOFT, effectiveTo = today.minusDays(10))
        val documents = listOf(original)

        ExpiryPolicy.apply(documents, today)

        assertEquals("原始正文", original.body)
        assertEquals("原始正文", documents[0].body)
    }

    @Test
    fun `apply 对空列表返回空列表`() {
        assertTrue(ExpiryPolicy.apply(emptyList(), today).isEmpty())
    }

    @Test
    fun `过期提醒文案里含过期日期与天数`() {
        val notice = ExpiryPolicy.notice(daysAgo = 7, expiredOn = today)
        assertTrue(notice.contains("2026-10-03"))
        assertTrue(notice.contains("7 天前"))
    }
}
