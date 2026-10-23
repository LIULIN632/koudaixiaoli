package com.pocket.xiaoli.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自建分发地址的拼接规则。
 *
 * 这层逻辑很薄，但错了会很痛：**没配好就等于把 App 弄瞎**（所有数据源都 404）。
 * 所以这里把三条不变量钉住：没配置时原样返回、配置后只加一层、原有兜底一个都不能少。
 */
class SelfHostTest {

    private val fallback = listOf(
        "https://gitee.com/liulin62633/pocket-carp/raw/master/school_index.json",
        "https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/school_index.json",
    )

    @Test
    fun notConfiguredKeepsOriginalChain() {
        // 未配置（含空串与只有空格）时，链路必须**逐元素相同**
        assertEquals(fallback, SelfHost.prepend("", "/index/school_index.json", fallback))
        assertEquals(fallback, SelfHost.prepend("   ", "/index/school_index.json", fallback))
        // 路径为空也当作没配置，而不是拼出一个 "https://host" 这样的裸地址
        assertEquals(fallback, SelfHost.prepend("https://xy.example.com", "", fallback))
    }

    @Test
    fun configuredPutsSelfHostFirstAndKeepsEveryFallback() {
        val chain = SelfHost.prepend("https://xy.example.com/", "/index/school_index.json", fallback)

        assertEquals("https://xy.example.com/index/school_index.json", chain.first())
        // 末尾的斜杠要吃掉，别拼成 //index
        assertFalse(chain.first().contains("//index"))
        // 原有镜像一个都不能少，顺序也不能乱
        assertEquals(fallback, chain.drop(1))
        assertEquals(fallback.size + 1, chain.size)
    }

    @Test
    fun holidayMirrorsKeepYearPlaceholder() {
        val mirrors = SelfHost.prepend(
            "https://xy.example.com",
            "/index/holiday/%d.json",
            listOf("https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json"),
        )
        // 原实现用 %d 填年份；自建这条必须保留同样的占位符，否则年份填不进去
        assertEquals("https://xy.example.com/index/holiday/%d.json", mirrors.first())
        assertTrue(mirrors.first().contains("%d"))
    }

    @Test
    fun promoUrlIsNullUntilConfigured() {
        // 当前仓库里的常量是空的（部署完才填），所以这里断言"没配置就是 null"
        if (!SelfHost.enabled) assertNull(SelfHost.promoUrl())
        else assertTrue(SelfHost.promoUrl()!!.endsWith("/index/promos.json"))
    }

    @Test
    fun knowledgeUrlsPointAtKnowledgeJson() {
        val chain = SelfHost.prepend("https://xy.example.com/", "/index/knowledge.json", fallback)

        assertEquals("https://xy.example.com/index/knowledge.json", chain.first())
        // 原有镜像一个都不能少，顺序也不能乱
        assertEquals(fallback, chain.drop(1))
    }

    @Test
    fun knowledgeUrlsKeepEveryMirrorWhenNotConfigured() {
        // 知识库与学校索引共用同一条回退链规则：没配置自建时**逐元素相同**
        if (!SelfHost.enabled) {
            assertEquals(fallback, SelfHost.knowledgeUrls(fallback))
        } else {
            assertTrue(SelfHost.knowledgeUrls(fallback).first().endsWith("/index/knowledge.json"))
        }
    }

    // ── 打缓存（2026-10-18 故障：发版几分钟内点检查仍显示「已是最新版本」）──

    @Test
    fun bustCacheAppendsTimestampWhenAsked() {
        val urls = listOf("https://gitee.com/a/raw/master/announce/feed.json")

        val busted = SelfHost.withCacheBuster(urls, bustCache = true, nowMillis = 1_760_000_000_000L)

        assertEquals("每个镜像都要打，不能只打第一个", urls.size, busted.size)
        assertTrue(busted.first(), busted.first().endsWith("?t=1760000000000"))
    }

    @Test
    fun bustCacheKeepsUrlUntouchedWhenNotAsked() {
        val urls = listOf("https://raw.githubusercontent.com/a/b/main/announce/feed.json")

        assertEquals(urls, SelfHost.withCacheBuster(urls, bustCache = false, nowMillis = 1L))
    }

    @Test
    fun bustCacheUsesAmpersandWhenUrlAlreadyHasQuery() {
        // Gitee 的 contents API 地址就带 ?ref=；再拼 ?t= 会变成两个问号，请求直接废掉
        val urls = listOf("https://gitee.com/api/v5/repos/a/b/contents/announce/feed.json?ref=v1")

        val busted = SelfHost.withCacheBuster(urls, bustCache = true, nowMillis = 42L)

        assertEquals("https://gitee.com/api/v5/repos/a/b/contents/announce/feed.json?ref=v1&t=42", busted.single())
    }

    @Test
    fun feedUrlsWithoutBustIsUnchanged() {
        // 自动轮询走这条：不带时间戳，省流量也不催边缘缓存
        assertEquals(SelfHost.feedUrls(fallback), SelfHost.feedUrls(fallback, bustCache = false))
    }
}
