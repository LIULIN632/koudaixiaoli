package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.SelfHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回退链结构。
 *
 * 这层逻辑不联网也能测，而且**必须测**：`SelfHostTest` 已经把这条不变量写死了 ——
 * 「原有兜底一个都不能少」。知识库的回退链抄的是同一套规则，抄错了就是某个镜像
 * 永远拿不到（国内用户首当其冲）。路径写错一个字符，单元测试不红、线上静默失效。
 */
class KnowledgeUpdaterTest {

    /**
     * 与 `KnowledgeUpdater.URLS` 里手写的清单逐字对应（自建之外的镜像）。
     *
     * ⚠️ 2026-10-19：Gitee 的 **`main` 必须排在 `master` 前面**。
     * 发版推的是 `main`，而 `master` 曾经一路停在 v0.1.68 —— 排在前面就等于永远读旧数据
     * （同一天在 `AppUpdateChecker` 的 feed 镜像上就踩了这个坑）。
     */
    private val mirrors = listOf(
        "https://gitee.com/liulin62633/pocket-carp/raw/main/knowledge.json",
        "https://gitee.com/liulin62633/pocket-carp/raw/master/knowledge.json",
        "https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/knowledge.json",
        "https://cdn.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/knowledge.json",
        "https://fastly.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/knowledge.json",
        "https://gcore.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/knowledge.json",
    )

    @Test
    fun `每个镜像都指向 knowledge_json`() {
        assertTrue(KnowledgeUpdater.URLS.isNotEmpty())
        assertTrue(KnowledgeUpdater.URLS.all { it.endsWith("/knowledge.json") })
    }

    @Test
    fun `没有把学校索引的路径抄进来`() {
        assertFalse(KnowledgeUpdater.URLS.any { it.contains("school_index") })
    }

    @Test
    fun `Gitee 排在最前 —— 国内直连免代理`() {
        assertEquals(mirrors.first(), KnowledgeUpdater.URLS.first())
    }

    @Test
    fun `原有兜底一个都不能少`() {
        if (!SelfHost.enabled) {
            assertEquals(mirrors, KnowledgeUpdater.URLS)
        } else {
            assertEquals(mirrors, KnowledgeUpdater.URLS.drop(1))
        }
    }

    @Test
    fun `jsDelivr 保留了三个不同域名`() {
        val jsdelivr = KnowledgeUpdater.URLS.filter { it.contains("jsdelivr") }
        assertEquals("cdn / fastly / gcore 缺一不可（一个被墙还有别的）", 3, jsdelivr.size)
        assertEquals(3, jsdelivr.map { it.substringBefore("/gh/") }.distinct().size)
    }

    @Test
    fun `没有重复的镜像地址`() {
        assertEquals(KnowledgeUpdater.URLS.size, KnowledgeUpdater.URLS.distinct().size)
    }

    @Test
    fun `两个分支名都列了 —— 分支名不符时是快速 404，不会浪费超时`() {
        val gitee = KnowledgeUpdater.URLS.filter { it.contains("gitee.com") }
        assertEquals(2, gitee.size)
        assertTrue(gitee.any { it.contains("/master/") })
        assertTrue(gitee.any { it.contains("/main/") })
    }

    @Test
    fun `自动更新间隔是 24 小时`() {
        assertEquals(24 * 60 * 60 * 1000L, KnowledgeStore.CHECK_INTERVAL_MS)
    }
}
