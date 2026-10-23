package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [ApkArtifact] 的校验逻辑 —— 这几条判据决定「用户按下安装时拿到的是 App 还是错误页」。
 * 用临时文件跑，不需要联网也不需要设备。
 */
class ApkArtifactTest {

    private fun tempApk(bytes: ByteArray): File =
        File.createTempFile("xiaoli-test", ".apk").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    /** 造一个「像 APK」的文件：PK 头 + 够长 */
    private fun validBytes(size: Int = 200_000): ByteArray =
        ByteArray(size).also {
            it[0] = 0x50
            it[1] = 0x4B
        }

    @Test
    fun `正常安装包通过校验`() {
        assertNull(ApkArtifact.verify(tempApk(validBytes())))
    }

    @Test
    fun `太小的文件当下载失败`() {
        val message = ApkArtifact.verify(tempApk(validBytes(size = 1024)))
        assertNotNull(message)
        assertTrue("应提到不完整：$message", message!!.contains("不完整"))
    }

    @Test
    fun `不是 zip 头就拒绝 比如 HTML 错误页`() {
        val html = "<!DOCTYPE html><html>404</html>".padEnd(200_000, ' ').toByteArray()
        val message = ApkArtifact.verify(tempApk(html))
        assertEquals("下载到的不是安装包（可能被网络拦截了）", message)
    }

    @Test
    fun `文件不存在时说清原因而不是崩`() {
        val missing = File("does-not-exist-${System.nanoTime()}.apk")
        assertEquals("安装包不存在", ApkArtifact.verify(missing))
    }

    @Test
    fun `给了 sha256 就必须对得上`() {
        val file = tempApk(validBytes())
        val actual = ApkArtifact.sha256(file)

        assertNull(ApkArtifact.verify(file, actual))
        assertNull(ApkArtifact.verify(file, "sha256:$actual")) // 带前缀也认
        assertNull(ApkArtifact.verify(file, actual.uppercase())) // 大小写不敏感
        assertEquals("安装包校验失败，请重新下载", ApkArtifact.verify(file, "0".repeat(64)))
    }

    @Test
    fun `没给 sha256 时跳过摘要校验`() {
        val file = tempApk(validBytes())
        assertNull(ApkArtifact.verify(file, null))
        assertNull(ApkArtifact.verify(file, ""))
        assertNull(ApkArtifact.verify(file, "   "))
    }

    @Test
    fun `sha256 归一化会去掉前缀与空白`() {
        assertEquals("abc123", ApkArtifact.normalizeSha256("sha256:ABC123"))
        assertEquals("abc123", ApkArtifact.normalizeSha256("  SHA256:abc123  "))
        assertEquals("abc123", ApkArtifact.normalizeSha256("abc123"))
        assertEquals("", ApkArtifact.normalizeSha256(null))
    }

    @Test
    fun `sha256 结果固定 64 位十六进制`() {
        val digest = ApkArtifact.sha256(tempApk(validBytes()))
        assertEquals(64, digest.length)
        assertTrue(digest.all { it in "0123456789abcdef" })
    }
}
