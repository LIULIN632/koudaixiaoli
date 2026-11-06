package com.pocket.xiaoli.desktop.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 数据写盘的原子性。
 *
 * `Store` 是整份 JSON 覆盖写，写坏的代价是用户一学期的课表。全部在临时目录里跑，
 * 不碰 `%APPDATA%`。
 */
class StoreAtomicWriteTest {

    private fun tempDir(): File = File.createTempFile("xiaoli-atomic", "").apply { delete(); mkdirs() }

    @Test
    fun `目标不存在时写出一份完整文件且不留临时件`() {
        val dir = tempDir()
        val target = File(dir, "pocket-desktop.json")
        assertTrue(Store.writeAtomic(target, """{"version":1}""").isSuccess)
        assertEquals("""{"version":1}""", target.readText(Charsets.UTF_8))
        assertFalse(File(dir, "pocket-desktop.json.tmp").exists(), "改名成功后不该留下 .tmp")
        dir.deleteRecursively()
    }

    @Test
    fun `覆盖写之后盘上只有新内容`() {
        val dir = tempDir()
        val target = File(dir, "pocket-desktop.json").apply { writeText("旧的一整份数据", Charsets.UTF_8) }
        assertTrue(Store.writeAtomic(target, "新的").isSuccess)
        assertEquals("新的", target.readText(Charsets.UTF_8))
        assertFalse(File(dir, "pocket-desktop.json.tmp").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `父目录还没建时先建再写`() {
        val dir = File(tempDir(), "logs/nested")
        val target = File(dir, "state.json")
        assertTrue(Store.writeAtomic(target, "{}").isSuccess)
        assertTrue(target.exists())
    }

    @Test
    fun `写不进去时返回失败而不是把原文件删掉`() {
        // 父路径是个文件 → mkdirs 与写入都会失败；原文件必须还在
        val dir = tempDir()
        val blocker = File(dir, "blocked").apply { writeText("我不是目录", Charsets.UTF_8) }
        val target = File(blocker, "pocket-desktop.json")
        val result = Store.writeAtomic(target, "新数据")
        assertTrue(result.isFailure, "父路径被文件占着时应当报失败")
        assertTrue(blocker.exists(), "失败不能把挡在原地的文件一起弄没")
        blocker.delete()
        dir.deleteRecursively()
    }

    @Test
    fun `中文与引号按 UTF-8 原样往返`() {
        val dir = tempDir()
        val target = File(dir, "pocket-desktop.json")
        val text = """{"courseName":"工程测量学","room":"实训楼A-402","note":"他说“明天带尺子”"}"""
        assertTrue(Store.writeAtomic(target, text).isSuccess)
        assertEquals(text, target.readText(Charsets.UTF_8))
        dir.deleteRecursively()
    }
}
