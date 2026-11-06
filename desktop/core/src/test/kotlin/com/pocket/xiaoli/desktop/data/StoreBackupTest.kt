package com.pocket.xiaoli.desktop.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 备份与占位数据识别的测试。
 *
 * 全部在临时目录里跑 —— `Store.backup()` 的真实路径是用户的数据目录，
 * 单测不能往那里写文件，所以备份实现拆成了 [Store.backupFile]。
 */
class StoreBackupTest {

    private fun tempDir(): File = File.createTempFile("xiaoli-backup", "").apply { delete(); mkdirs() }

    @Test
    fun `backup copies the data file with a timestamped name`() {
        val dir = tempDir()
        val source = File(dir, "pocket-desktop.json").apply { writeText("""{"version":1}""", Charsets.UTF_8) }
        val out = Store.backupFile(source, dir)
        assertNotNull(out)
        assertTrue(out.name.startsWith("pocket-desktop.bak-"), out.name)
        assertEquals("""{"version":1}""", out.readText(Charsets.UTF_8))
        dir.deleteRecursively()
    }

    @Test
    fun `missing data file yields no backup`() {
        val dir = tempDir()
        assertNull(Store.backupFile(File(dir, "nope.json"), dir))
        dir.deleteRecursively()
    }

    @Test
    fun `only the newest ten backups survive`() {
        val dir = tempDir()
        val source = File(dir, "pocket-desktop.json").apply { writeText("x", Charsets.UTF_8) }
        // 12 份历史备份（时间戳可排序）
        val old = (1..12).map { i ->
            File(dir, "pocket-desktop.bak-20260101-0000%02d.json".format(i)).apply { writeText("old$i") }
        }
        Store.backupFile(source, dir, keep = 10)
        val survivors = old.filter { it.exists() } + listOfNotNull(
            dir.listFiles()?.filter { it.name.startsWith("pocket-desktop.bak-2026") && it.readText() == "x" }
        ).flatten()
        assertTrue(survivors.size <= 10, "备份没剪到 10 份：${survivors.size}")
        // 最旧的两份（01、02）必须被剪掉
        assertTrue(!old[0].exists() && !old[1].exists())
        assertTrue(old.last().exists())
        dir.deleteRecursively()
    }

    @Test
    fun `sample ids match the seed data and never match imported or manual ids`() {
        val seed = seedData()
        assertEquals(seed.courses.map { it.id }.toSet(), Store.sampleCourseIds())
        assertEquals(seed.todos.map { it.id }.toSet(), Store.sampleTodoIds())
        assertTrue(seed.courses.all { it.id.startsWith("c-") })
        // 教务导入 `jw_…` 与手动新建 `c_…` 都不在示例 id 里，清占位不会误删它们
        assertTrue("jw_term-1_0" !in Store.sampleCourseIds())
        assertTrue("c_m1abc123456" !in Store.sampleCourseIds())
        assertEquals(seed.courses.map { it.teacher }.filter { it.isNotBlank() }.toSet(), Store.sampleTeacherNames())
    }
}
