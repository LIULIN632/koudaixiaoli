package com.pocket.xiaoli.data.courseware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 解压的四个判据：**路径不越界、总量有上限、必须有 index.html、失败不留半个目录**。
 *
 * 这些包来自网络（Release 附件），所以"恶意 zip"是必须防的一类输入：
 * `../` 能把文件写到 `filesDir` 之外，zip bomb 能把手机存储塞满。
 */
class CoursewareDownloaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (path, content) ->
                out.putNextEntry(ZipEntry(path))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `正常包解压后 index_html 在`() {
        val dest = File(temp.root, "beam")
        val zip = zip("ok.zip", mapOf("index.html" to "<html/>", "src/main.js" to "console.log(1)"))
        assertNull(CoursewareDownloader.unzip(zip, dest))
        assertTrue(File(dest, "index.html").isFile)
        assertTrue(File(dest, "src/main.js").isFile)
    }

    @Test
    fun `包内路径带两点被整体拒绝`() {
        val dest = File(temp.root, "evil")
        val zip = zip("evil.zip", mapOf("index.html" to "x", "../escaped.txt" to "boom"))
        assertNotNull(CoursewareDownloader.unzip(zip, dest))
        assertTrue(!dest.exists())
        assertTrue(!File(temp.root, "escaped.txt").exists())
    }

    @Test
    fun `绝对路径被拒绝`() {
        val dest = File(temp.root, "abs")
        val zip = zip("abs.zip", mapOf("index.html" to "x", "/etc/passwd" to "boom"))
        assertNotNull(CoursewareDownloader.unzip(zip, dest))
        assertTrue(!dest.exists())
    }

    @Test
    fun `没有 index_html 的包被拒绝`() {
        val dest = File(temp.root, "noindex")
        val zip = zip("noindex.zip", mapOf("readme.txt" to "hi"))
        assertNotNull(CoursewareDownloader.unzip(zip, dest))
        assertTrue(!dest.exists())
    }

    @Test
    fun `shared 包不要求 index_html`() {
        val dest = File(temp.root, "vendor")
        val zip = zip("vendor.zip", mapOf("three.module.min.js" to "//js", "jsm/controls/OrbitControls.js" to "//ctl"))
        assertNull(CoursewareDownloader.unzip(zip, dest, requireIndex = false))
        assertTrue(File(dest, "three.module.min.js").isFile)
        assertTrue(File(dest, "jsm/controls/OrbitControls.js").isFile)
    }

    @Test
    fun `重复解压会替换旧目录而不是合并`() {
        val dest = File(temp.root, "replace")
        assertNull(CoursewareDownloader.unzip(zip("a.zip", mapOf("index.html" to "v1", "old.js" to "1")), dest))
        assertTrue(File(dest, "old.js").isFile)
        assertNull(CoursewareDownloader.unzip(zip("b.zip", mapOf("index.html" to "v2")), dest))
        assertEquals("v2", File(dest, "index.html").readText())
        assertTrue(!File(dest, "old.js").exists())
    }

    @Test
    fun `sha256 与体积文案`() {
        val file = File(temp.root, "hash.txt").apply { writeText("abc") }
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CoursewareDownloader.sha256(file),
        )
        assertEquals("未知大小", CoursewareDownloader.sizeText(0))
        assertEquals("25 KB", CoursewareDownloader.sizeText(25 * 1024))
        assertEquals("1.5 MB", CoursewareDownloader.sizeText(1024 * 1024 * 3 / 2))
    }
}
