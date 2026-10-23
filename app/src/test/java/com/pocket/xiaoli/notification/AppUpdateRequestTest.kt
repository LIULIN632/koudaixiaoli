package com.pocket.xiaoli.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 通知里「点一下直接更新」的请求对象。
 *
 * 这些断言守的是**哑火**：通知能点、但点到 Worker 里才发现地址为空，
 * 用户看到的就是"点了没反应"。所以在 Receiver 入口就把不合法的请求挡掉。
 */
class AppUpdateRequestTest {

    @Test
    fun `strips v prefix and whitespace`() {
        val request = AppUpdateRequest.of("  v0.1.59 ", " https://example.com/a.apk ", " ABCDEF ")
        assertEquals("0.1.59", request.versionName)
        assertEquals("https://example.com/a.apk", request.apkUrl)
        assertEquals("ABCDEF", request.sha256)
    }

    @Test
    fun `accepts version without prefix`() {
        assertEquals("0.1.60", AppUpdateRequest.of("0.1.60", "https://e/a.apk", "").versionName)
    }

    @Test
    fun `missing url is invalid`() {
        assertFalse(AppUpdateRequest.of("0.1.59", "   ", "abc").isValid)
        assertFalse(AppUpdateRequest.of("0.1.59", null, null).isValid)
    }

    @Test
    fun `missing version is invalid`() {
        assertFalse(AppUpdateRequest.of("", "https://e/a.apk", "abc").isValid)
        assertFalse(AppUpdateRequest.of(null, "https://e/a.apk", "abc").isValid)
    }

    @Test
    fun `empty sha is still valid but skips verification`() {
        val request = AppUpdateRequest.of("0.1.59", "https://e/a.apk", "  ")
        assertTrue(request.isValid)
        assertEquals("", request.sha256)
    }

    @Test
    fun `target file carries version so retries reuse it`() {
        val dir = File("updates")
        assertEquals(
            File(dir, "pocket-xiaoli-v0.1.59.apk").path,
            AppUpdateRequest.of("v0.1.59", "https://e/a.apk", "").targetFile(dir).path,
        )
    }
}
