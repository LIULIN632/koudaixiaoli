package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ApkMirrors] —— 下载换源地址的推导。
 *
 * 这几条断言守的是「换源之后下载到的还是同一份字节」这件事：
 * 版本号拼不出来、附件名拼错、两个仓顺序颠倒，都会让兜底变成一次白下载。
 */
class ApkMirrorsTest {

    @Test
    fun `附件名两个仓一致`() {
        assertEquals("koudaixiaoli_v0.1.51.apk", ApkMirrors.assetName("0.1.51"))
        assertEquals("koudaixiaoli_v0.1.51.apk", ApkMirrors.assetName("v0.1.51"))
        assertEquals("koudaixiaoli_v0.1.51.apk", ApkMirrors.assetName(" 0.1.51 "))
    }

    @Test
    fun `gitee 地址带仓库前缀与附件名`() {
        val url = ApkMirrors.giteeUrl("0.1.51")
        assertTrue(url, url.startsWith("https://gitee.com/liulin62633/pocket-carp/releases/download/v0.1.51/"))
        assertTrue(url, url.endsWith("/koudaixiaoli_v0.1.51.apk"))
    }

    @Test
    fun `github 地址带仓库前缀与附件名`() {
        val url = ApkMirrors.githubUrl("v0.1.51")
        assertTrue(url, url.startsWith("https://github.com/LIULIN632/koudaixiaoli/releases/download/v0.1.51/"))
        assertTrue(url, url.endsWith("/koudaixiaoli_v0.1.51.apk"))
    }

    @Test
    fun `feed 给的地址永远排第一`() {
        val primary = "https://gitee.com/liulin62633/pocket-carp/releases/download/v0.1.51/koudaixiaoli_v0.1.51.apk"
        val list = ApkMirrors.candidates("0.1.51", primary)
        assertEquals(primary, list.first())
    }

    @Test
    fun `与 feed 地址重复的镜像不重复出现`() {
        val primary = ApkMirrors.giteeUrl("0.1.51")
        val list = ApkMirrors.candidates("0.1.51", primary)
        assertEquals(primary, list.first())
        assertTrue("应带上 GitHub 兜底：$list", list.any { it.contains("github.com") })
        assertEquals("地址不该有重复：$list", list.size, list.distinct().size)
    }

    @Test
    fun `版本号未知时只试 feed 那条`() {
        val primary = "https://example.com/a.apk"
        assertEquals(listOf(primary), ApkMirrors.candidates(null, primary))
        assertEquals(listOf(primary), ApkMirrors.candidates("  ", primary))
    }

    @Test
    fun `版本号未知且没有主地址时不猜地址`() {
        assertTrue(ApkMirrors.candidates(null, "").isEmpty())
    }

    @Test
    fun `提示里只出现仓库名不含整条链接`() {
        assertEquals("Gitee", ApkMirrors.label(ApkMirrors.giteeUrl("0.1.51")))
        assertEquals("GitHub", ApkMirrors.label(ApkMirrors.githubUrl("0.1.51")))
        assertEquals("备用源", ApkMirrors.label("https://example.com/a.apk"))
        assertFalse(ApkMirrors.label(ApkMirrors.giteeUrl("0.1.51")).contains("http"))
    }
}
