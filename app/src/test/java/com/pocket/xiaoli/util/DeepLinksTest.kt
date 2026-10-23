package com.pocket.xiaoli.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 链接可用性判定。
 *
 * 重点是**这批真实出现过的占位符必须全被判假** —— 它们是从
 * `TreasureBoxData.kt` 里逐条抄下来的，不是编的例子。
 */
class DeepLinksTest {

    // ── 必须判假：项目里真实出现过的 12 条占位符 ──

    private val actualPlaceholders = listOf(
        "https://b23.tv/gaoshu_songhao",
        "https://b23.tv/xiandaishu_3b1b",
        "https://b23.tv/gailvlun_kuang",
        "https://b23.tv/java_kuangshen",
        "https://b23.tv/python_cui",
        "https://b23.tv/suanfa_zuo",
        "https://b23.tv/cet46_liu",
        "https://b23.tv/yufa_parallel",
        "https://b23.tv/kaoyanzhengzhi_xu",
        "https://b23.tv/kaoyanmath_zhang",
        "https://b23.tv/ps_litao",
        "https://b23.tv/zhexue_wang",
    )

    @Test
    fun everySeedPlaceholderIsRejected() {
        actualPlaceholders.forEach { url ->
            assertFalse("应判为不可用: $url", DeepLinks.looksUsable(url))
        }
    }

    // ── 必须判假：其它形态 ──

    @Test
    fun blankIsRejected() {
        assertFalse(DeepLinks.looksUsable(null))
        assertFalse(DeepLinks.looksUsable(""))
        assertFalse(DeepLinks.looksUsable("   "))
    }

    @Test
    fun nonHttpIsRejected() {
        assertFalse(DeepLinks.looksUsable("b23.tv/abc123"))
        assertFalse(DeepLinks.looksUsable("mailto:a@b.com"))
        assertFalse(DeepLinks.looksUsable("javascript:void(0)"))
    }

    @Test
    fun reservedExampleDomainsAreRejected() {
        assertFalse(DeepLinks.looksUsable("https://jiaowu.example.edu.cn"))
        assertFalse(DeepLinks.looksUsable("https://example.com/path"))
        assertFalse(DeepLinks.looksUsable("https://localhost:8080/x"))
        assertFalse(DeepLinks.looksUsable("https://sub.example.org/a/b"))
    }

    @Test
    fun placeholderKeywordsAreRejected() {
        assertFalse(DeepLinks.looksUsable("https://school.edu.cn/placeholder"))
        assertFalse(DeepLinks.looksUsable("https://school.edu.cn/your-domain/page"))
        assertFalse(DeepLinks.looksUsable("https://school.edu.cn/xxx"))
    }

    @Test
    fun bareDomainIsAcceptedBecauseItHasAHomepage() {
        // 曾经把"没有路径"判为占位符，结果误杀了 https://lib.yitsd.edu.cn 这种真实链接。
        // 裸域名点开就是站点主页，它是可用的。
        assertTrue(DeepLinks.looksUsable("https://b23.tv"))
        assertTrue(DeepLinks.looksUsable("https://b23.tv/"))
        assertTrue(DeepLinks.looksUsable("https://lib.yitsd.edu.cn"))
    }

    // ── 必须判真：真实形态 ──

    @Test
    fun realBilibiliShortLinkIsAccepted() {
        assertTrue(DeepLinks.looksUsable("https://b23.tv/BV1xx411c7mD"))
        assertTrue(DeepLinks.looksUsable("https://www.bilibili.com/video/BV1xx411c7mD"))
    }

    @Test
    fun realCampusLinksAreAccepted() {
        assertTrue(DeepLinks.looksUsable("https://www.yitsd.edu.cn/"))
        assertTrue(DeepLinks.looksUsable("https://lib.yitsd.edu.cn"))
        assertTrue(DeepLinks.looksUsable("https://jwxt.yitsd.edu.cn/jwglxt"))
        assertTrue(DeepLinks.looksUsable("http://jy.yitsd.edu.cn"))
        assertTrue(DeepLinks.looksUsable("https://www.sdzk.cn/"))
        assertTrue(DeepLinks.looksUsable("https://cet.neea.edu.cn/"))
    }

    @Test
    fun realLinksWithDigitsAndNestingAreAccepted() {
        assertTrue(DeepLinks.looksUsable("https://b23.tv/BV1xx411c7mD"))
        assertTrue(DeepLinks.looksUsable("https://www.bilibili.com/video/BV1xx411c7mD?p=2"))
        // 带层级的路径，即使是裸域名 + /a 也算"有内容"
        assertTrue(DeepLinks.looksUsable("https://bd.bilibili.com/video"))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertTrue(DeepLinks.looksUsable("  https://www.yitsd.edu.cn  "))
        assertTrue(DeepLinks.looksUsable("  https://b23.tv/BV1xx411c7mD  "))
        // 占位符加了空格也还是占位符
        assertFalse(DeepLinks.looksUsable("  https://b23.tv/gaoshu_songhao  "))
    }

    @Test
    fun uppercaseSchemeIsTolerated() {
        assertTrue(DeepLinks.looksUsable("HTTPS://WWW.YITSD.EDU.CN"))
    }
}
