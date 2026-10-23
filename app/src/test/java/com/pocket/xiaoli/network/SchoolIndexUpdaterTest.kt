package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SchoolIndexUpdater 决策逻辑边界用例：协议版本 / 时间戳版本 / 防回退。
 */
class SchoolIndexUpdaterTest {

    private fun index(protocol: Int = 1, versionId: String) = SchoolIndex(
        protocolVersion = protocol,
        versionId = versionId,
        schools = emptyMap()
    )

    @Test
    fun evaluate_protocolHigherThanClient_rejected() {
        val decision = evaluateIndex(
            remote = index(protocol = 2, versionId = "TIME_20260831120000_001"),
            local = null
        )
        assertTrue(decision is IndexDecision.Rejected)
    }

    @Test
    fun evaluate_protocolEqualOrLower_apply() {
        val d1 = evaluateIndex(index(1, "TIME_20260831120000_001"), null)
        assertEquals(IndexDecision.Apply, d1)
        val d0 = evaluateIndex(index(0, "TIME_20260831120000_001"), null)
        assertEquals(IndexDecision.Apply, d0)
    }

    @Test
    fun evaluate_sameVersion_upToDate() {
        val local = index(versionId = "TIME_20260831120000_001")
        val decision = evaluateIndex(
            remote = index(versionId = "TIME_20260831120000_001"),
            local = local
        )
        assertEquals(IndexDecision.UpToDate, decision)
    }

    @Test
    fun evaluate_remoteNewer_apply() {
        // 时间戳字典序即时间序：同一天晚 1 秒的后缀更大 → 更新
        val local = index(versionId = "TIME_20260831120000_001")
        val decision = evaluateIndex(
            remote = index(versionId = "TIME_20260831120001_001"),
            local = local
        )
        assertEquals(IndexDecision.Apply, decision)
    }

    @Test
    fun evaluate_remoteOlder_rejected() {
        // 远程比本地旧 = 数据异常（防回退）
        val local = index(versionId = "TIME_20260831120000_001")
        val decision = evaluateIndex(
            remote = index(versionId = "TIME_20260830120000_001"),
            local = local
        )
        assertTrue(decision is IndexDecision.Rejected)
    }

    @Test
    fun evaluate_sameTimestampDifferentSuffix_apply() {
        // 同秒重复生成 → 后缀 +1 → 字典序更大 → 更新
        val local = index(versionId = "TIME_20260831120000_001")
        val decision = evaluateIndex(
            remote = index(versionId = "TIME_20260831120000_002"),
            local = local
        )
        assertEquals(IndexDecision.Apply, decision)
    }

    @Test
    fun evaluate_noLocalIndex_apply() {
        assertEquals(IndexDecision.Apply, evaluateIndex(index(1, "TIME_20260831120000_001"), null))
    }

    @Test
    fun evaluate_remoteNewerButHigherProtocol_rejected() {
        // 协议不兼容优先于版本更新
        val decision = evaluateIndex(
            remote = index(protocol = 2, versionId = "TIME_20260901120000_001"),
            local = index(versionId = "TIME_20260831120000_001")
        )
        assertTrue(decision is IndexDecision.Rejected)
    }
}
