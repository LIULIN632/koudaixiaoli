package com.pocket.xiaoli.data.promo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 推广位的**取处**与**配图域名**（2026-10-23 改造）。
 *
 * ## 背景：轮播从上线起就没显示过
 *
 * 原来配置只从 `SelfHost.BASE_URL + /index/promos.json` 取，而 `BASE_URL` 是空的、
 * 回退的 `cdn.yitsd.edu.cn` 实测 403，所以服务页顶部永远是空的（失败即隐藏，不报错）。
 * 现在配置进了仓库，和 `feed.json` 一样走 Gitee raw + GitHub raw。
 *
 * 这个文件钉住两件容易被改坏的事：
 *
 * 1. **取处顺序**：Gitee 必须在 GitHub 前面（境内实测 GitHub raw 经常整段不通），
 *    两条都必须是 https、都指向 `announce/promos.json`；
 * 2. **配图与跳转是两套白名单**：图可以放 Gitee（我们就存在仓库里），
 *    但**跳转目标仍然只能是学校域名** —— 有人把 `gitee.com` 加进 `ALLOWED_HOSTS`
 *    就等于把这扇门开给了整个 gitee，这条测试会红。
 */
class PromoSourceTest {

    // ── 取处 ─────────────────────────────────────────────────────

    @Test
    fun `取处顺序是 Gitee 在前 GitHub 在后`() {
        val urls = PromoRepository.CONFIG_URLS
        val gitee = urls.indexOfFirst { it.contains("gitee.com") }
        val github = urls.indexOfFirst { it.contains("github") }
        assertTrue("必须包含 Gitee 取处", gitee >= 0)
        assertTrue("必须包含 GitHub 兜底", github >= 0)
        assertTrue("Gitee 必须排在 GitHub 前面（境内 GitHub raw 常常整段不通）", gitee < github)
    }

    @Test
    fun `每条取处都是 https 且指向 announce 下的 promos_json`() {
        PromoRepository.CONFIG_URLS.forEach { url ->
            assertTrue("必须 https：$url", url.startsWith("https://"))
            assertTrue("必须指向 announce/promos.json：$url", url.endsWith("/announce/promos.json"))
        }
    }

    // ── 线上那份配置本身 ─────────────────────────────────────────

    private fun shippedConfig(): File {
        val candidates = listOf(
            File("../announce/promos.json"),
            File("announce/promos.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 announce/promos.json，候选：${candidates.map { it.absolutePath }}")
    }

    @Test
    fun `仓库里那份 promos_json 是合法的、且现在没有上架任何一条`() {
        val raw = shippedConfig().readText()
        val cfg = PromoValidator.parse(raw, nowSec = System.currentTimeMillis() / 1000)
        // 空数组是刻意的：没有真实海报时不许用示例文案占位（用户不该看到「示例：…」）
        assertTrue("默认应为空配置，实际 ${cfg.promos.size} 条", cfg.promos.isEmpty())
        assertTrue("至少要能解析出 version 字段", cfg.version >= 0)
    }

    // ── 配图 vs 跳转 ─────────────────────────────────────────────

    private fun promoWith(image: String?, actionUrl: String?): String = buildString {
        append("""{"version":1,"slots":[{""")
        append(""""id":"p1","slot":"服务首页","title":"测试","body":"正文"""")
        if (image != null) append(""","image":"$image"""")
        if (actionUrl != null) append(""","action":{"kind":"external","url":"$actionUrl"}""")
        append("}]}")
    }

    @Test
    fun `配图可以放 Gitee`() {
        val raw = promoWith(
            image = "https://gitee.com/liulin62633/pocket-carp/raw/main/announce/promo/kaoyan.jpg",
            actionUrl = null,
        )
        val promo = PromoValidator.parse(raw, nowSec = 0).promos.single()
        assertEquals(
            "Gitee 的图必须被接受（我们的海报就放在仓库里）",
            "https://gitee.com/liulin62633/pocket-carp/raw/main/announce/promo/kaoyan.jpg",
            promo.imageUrl,
        )
    }

    @Test
    fun `配图域名不在白名单时只丢图，不丢整条`() {
        val raw = promoWith(image = "https://evil.example.com/x.jpg", actionUrl = null)
        val promo = PromoValidator.parse(raw, nowSec = 0).promos.single()
        assertNull("陌生域名的图必须被丢掉", promo.imageUrl)
        assertEquals("文案仍然要展示", "测试", promo.title)
    }

    @Test
    fun `跳转目标不能借道 Gitee`() {
        val raw = promoWith(image = null, actionUrl = "https://gitee.com/liulin62633/pocket-carp")
        val promo = PromoValidator.parse(raw, nowSec = 0).promos.single()
        assertNull("跳转白名单里不该有 gitee.com —— 否则等于允许把用户带去任意 Gitee 页面", promo.action)
    }

    @Test
    fun `配图白名单里的域名都是小写且不含协议`() {
        PromoValidator.IMAGE_HOSTS.forEach { h ->
            assertEquals("白名单条目必须小写：$h", h.lowercase(), h)
            assertTrue("不该带协议或路径：$h", !h.contains("/") && !h.contains(":"))
        }
    }

    @Test
    fun `跳转白名单没有被配图白名单污染`() {
        assertTrue(
            "ALLOWED_HOSTS 不该包含 gitee.com / github.com",
            PromoValidator.ALLOWED_HOSTS.none { it.endsWith("gitee.com") || it.endsWith("github.com") },
        )
    }
}
