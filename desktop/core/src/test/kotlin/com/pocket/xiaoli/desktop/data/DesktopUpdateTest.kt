package com.pocket.xiaoli.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 更新检查的解析与比较规则。
 *
 * 这些断言钉的是一件事：**只有真正的新版本才提示**。误报一次，
 * 用户点进去发现已经是最新，下次这条提示就没人信了。
 */
class DesktopUpdateTest {

    private val feed = """
        {
          "app": { "versionCode": 108 },
          "desktop": {
            "versionCode": 120,
            "versionName": "0.2.8",
            "url": "https://example.com/pocket-xiaoli-0.2.8.msi",
            "sha256": "abc",
            "notes": ["修了什么", "又修了什么"]
          }
        }
    """.trimIndent()

    @Test
    fun `解析 desktop 块`() {
        val release = DesktopUpdate.parse(feed)!!
        assertEquals(120, release.versionCode)
        assertEquals("0.2.8", release.versionName)
        assertEquals("https://example.com/pocket-xiaoli-0.2.8.msi", release.url)
        assertEquals("abc", release.sha256)
        assertEquals(2, release.notes.size)
    }

    @Test
    fun `没有 desktop 块时返回 null 而不是抛异常`() {
        assertNull(DesktopUpdate.parse("""{"app":{"versionCode":108}}"""))
    }

    @Test
    fun `desktop 是空对象时返回 null`() {
        assertNull(DesktopUpdate.parse("""{"desktop":{}}"""))
    }

    @Test
    fun `url 为空时返回 null —— 不能提示一个点不动的更新`() {
        assertNull(DesktopUpdate.parse("""{"desktop":{"versionCode":120,"url":""}}"""))
    }

    @Test
    fun `坏 JSON 不抛异常`() {
        assertNull(DesktopUpdate.parse("<html>502 Bad Gateway</html>"))
    }

    @Test
    fun `版本比较只认同号或更高`() {
        assertTrue(DesktopUpdate.isNewer(121, 120))
        assertFalse(DesktopUpdate.isNewer(120, 120))
        assertFalse(DesktopUpdate.isNewer(119, 120))
    }

    @Test
    fun `notes 里有非字符串项时跳过而不是整条失败`() {
        val release = DesktopUpdate.parse(
            """{"desktop":{"versionCode":120,"url":"https://x","notes":["ok",1,null]}}"""
        )!!
        assertEquals(listOf("ok"), release.notes)
    }
}
