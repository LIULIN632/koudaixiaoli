package com.pocket.xiaoli.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本决策：择优（防降级）与写入判定（防回退）。 */
class KnowledgeVersionTest {

    private fun index(versionId: String, protocolVersion: Int = 1, hash: String = "") =
        KnowledgeIndexJson(
            protocolVersion = protocolVersion,
            versionId = versionId,
            contentHash = hash,
        )

    // ── chooseKnowledgeIndex：防降级 ──────────────────────────────

    @Test
    fun `两边都没有时返回 null`() {
        assertNull(chooseKnowledgeIndex(cached = null, asset = null))
    }

    @Test
    fun `只有缓存时用缓存`() {
        val cached = index("TIME_20261003180000_001")
        assertEquals(cached, chooseKnowledgeIndex(cached, asset = null))
    }

    @Test
    fun `只有内置时用内置`() {
        val asset = index("TIME_20261003180000_001")
        assertEquals(asset, chooseKnowledgeIndex(cached = null, asset = asset))
    }

    @Test
    fun `缓存更新时用缓存`() {
        val cached = index("TIME_20261003190000_001")
        val asset = index("TIME_20261003180000_001")
        assertEquals(cached, chooseKnowledgeIndex(cached, asset))
    }

    @Test
    fun `内置更新时用内置`() {
        val cached = index("TIME_20261003180000_001")
        val asset = index("TIME_20261003190000_001")
        assertEquals(asset, chooseKnowledgeIndex(cached, asset))
    }

    @Test
    fun `版本相同时取缓存`() {
        val cached = index("TIME_20261003180000_001", hash = "缓存")
        val asset = index("TIME_20261003180000_001", hash = "内置")
        assertEquals("缓存", chooseKnowledgeIndex(cached, asset)?.contentHash)
    }

    // ── evaluateKnowledgeIndex：防回退 ────────────────────────────

    @Test
    fun `没有基线时直接写入`() {
        assertEquals(
            KnowledgeDecision.Apply,
            evaluateKnowledgeIndex(index("TIME_20261003180000_001"), baseline = null),
        )
    }

    @Test
    fun `基线版本为空串时直接写入`() {
        // 出厂内置的空骨架 version_id 就是这个形态
        val baseline = index("")
        assertEquals(
            KnowledgeDecision.Apply,
            evaluateKnowledgeIndex(index("TIME_20261003180000_001"), baseline),
        )
    }

    @Test
    fun `版本相同判为已是最新`() {
        val same = "TIME_20261003180000_001"
        assertEquals(
            KnowledgeDecision.UpToDate,
            evaluateKnowledgeIndex(index(same), index(same)),
        )
    }

    @Test
    fun `远程更旧时拒绝写入`() {
        val decision = evaluateKnowledgeIndex(
            index("TIME_20261003170000_001"),
            index("TIME_20261003180000_001"),
        )
        assertTrue(decision is KnowledgeDecision.Rejected)
    }

    @Test
    fun `远程更新时写入`() {
        assertEquals(
            KnowledgeDecision.Apply,
            evaluateKnowledgeIndex(
                index("TIME_20261003190000_001"),
                index("TIME_20261003180000_001"),
            ),
        )
    }

    @Test
    fun `协议版本过高时拒绝`() {
        val decision = evaluateKnowledgeIndex(
            index("TIME_20261003190000_001", protocolVersion = KNOWLEDGE_CLIENT_PROTOCOL_VERSION + 1),
            baseline = null,
        )
        assertTrue(decision is KnowledgeDecision.Rejected)
        assertTrue((decision as KnowledgeDecision.Rejected).message.contains("协议版本过高"))
    }

    @Test
    fun `协议版本相同或更低时放行`() {
        for (protocol in 0..KNOWLEDGE_CLIENT_PROTOCOL_VERSION) {
            assertEquals(
                "protocol=$protocol 应该放行",
                KnowledgeDecision.Apply,
                evaluateKnowledgeIndex(index("TIME_1", protocolVersion = protocol), baseline = null),
            )
        }
    }

    // ── 两个函数组合起来的真实场景 ────────────────────────────────

    @Test
    fun `内置比远程新时不该被降级覆盖`() {
        // 场景：App 内置的 assets 已经是 v5，但镜像上还停在 v3（Gitee 没同步）。
        // 若比较基线只读 filesDir 缓存（照抄 SchoolIndexUpdater 的写法），
        // 第一次更新时缓存为空 → baseline=null → 直接写入 v3，把内置的新版本覆盖掉。
        val cached: KnowledgeIndexJson? = null
        val asset = index("TIME_20261003190000_001", hash = "内置的新内容")

        val baseline = chooseKnowledgeIndex(cached, asset)
        val decision = evaluateKnowledgeIndex(index("TIME_20261003170000_001"), baseline)

        assertTrue(
            "基线必须包含内置版本，否则镜像上的旧数据会覆盖掉它",
            decision is KnowledgeDecision.Rejected,
        )
    }

    @Test
    fun `缓存比内置新时以缓存为基线`() {
        val cached = index("TIME_20261003200000_001")
        val asset = index("TIME_20261003180000_001")

        val baseline = chooseKnowledgeIndex(cached, asset)
        assertTrue(
            evaluateKnowledgeIndex(index("TIME_20261003190000_001"), baseline)
                is KnowledgeDecision.Rejected,
        )
    }
}
