package com.pocket.xiaoli.ui.components.infocard

import com.pocket.xiaoli.data.entity.SavedGradeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成绩分档的单元测试。
 *
 * 分档规则是**业务规则**（几分算优秀、多少分不及格），一旦写错，
 * 图和标签会一起错，而且界面上不容易看出来（谁会去数 89.99 该落哪档）。
 * 所以这里把边界值逐个钉住。
 */
class GradeDistributionTest {

    private fun grade(score: String, credits: String = "2", gpa: String = "3.0") =
        SavedGradeEntity(
            courseName = "课程",
            credits = credits,
            totalScore = score,
            gpa = gpa,
            termLabel = "2025-2026-1",
        )

    @Test
    fun `empty list produces zeroed buckets`() {
        val d = GradeDistribution.of(emptyList())
        assertTrue(d.isEmpty)
        assertEquals(0, d.scored)
        assertEquals(4, d.buckets.size)
        assertTrue(d.buckets.all { it.count == 0 })
    }

    @Test
    fun `bucket boundaries are exact`() {
        val d = GradeDistribution.of(
            listOf(
                grade("100"),  // 90+
                grade("90"),   // 90+（下边界含）
                grade("89.99"),// 80-89（上边界含）
                grade("80"),   // 80-89
                grade("79.99"),// 60-79
                grade("60"),   // 60-79（及格下边界含）
                grade("59.99"),// <60
                grade("0"),    // <60
            )
        )
        val byLabel = d.buckets.associate { it.label to it.count }
        assertEquals(2, byLabel["90+"])
        assertEquals(2, byLabel["80-89"])
        assertEquals(2, byLabel["60-79"])
        assertEquals(2, byLabel["<60"])
        assertEquals(8, d.scored)
    }

    @Test
    fun `unparseable scores are skipped entirely`() {
        val d = GradeDistribution.of(
            listOf(
                grade("85"),
                grade(""),      // 空
                grade("—"),     // 破折号
                grade("优秀"),   // 等级制
                grade("   "),   // 空白
            )
        )
        assertEquals(1, d.scored)
        assertEquals(1, d.buckets.sumOf { it.count })
    }

    @Test
    fun `whitespace around a number is tolerated`() {
        val d = GradeDistribution.of(listOf(grade(" 88 ")))
        assertEquals(1, d.scored)
        assertEquals(1, d.buckets.first { it.label == "80-89" }.count)
    }

    @Test
    fun `bands map to the same semantics as ScoreBand`() {
        val d = GradeDistribution.of(listOf(grade("95"), grade("70"), grade("50")))
        val byLabel = d.buckets.associateBy { it.label }
        assertEquals(ScoreBand.EXCELLENT, byLabel["90+"]!!.band)
        assertEquals(ScoreBand.PASS, byLabel["80-89"]!!.band)
        assertEquals(ScoreBand.PASS, byLabel["60-79"]!!.band)
        // 与 ScoreBand 一致：<60 即 FAIL，而不是另一套判断
        assertEquals(ScoreBand.FAIL, byLabel["<60"]!!.band)
    }

    @Test
    fun `maxCount is at least one to avoid division by zero`() {
        // 全部为空时 maxCount 仍须 >= 1，否则画条时 fraction 会除零
        assertTrue(GradeDistribution.of(emptyList()).maxCount >= 1)
        assertTrue(GradeDistribution.of(listOf(grade("50"))).maxCount >= 1)
    }

    @Test
    fun `maxCount reflects the largest bucket`() {
        val d = GradeDistribution.of(
            listOf(grade("95"), grade("92"), grade("91"), grade("88"))
        )
        assertEquals(3, d.maxCount)
    }

    @Test
    fun `distribution is not empty when at least one score parses`() {
        assertFalse(GradeDistribution.of(listOf(grade("60"))).isEmpty)
    }
}
