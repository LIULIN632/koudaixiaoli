package com.pocket.xiaoli.data.legal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 开源许可清单的**门禁**。
 *
 * 这张清单最大风险不是"写错"，而是**悄悄过期**：明天加一条依赖，页面还是老样子 ——
 * 用户看到的是"这 App 用了这些开源库"，而真相里少了新加的那个。所以这里直接读
 * `app/build.gradle.kts`，把每一条 `implementation("group:artifact…")` 与清单比对，
 * 没登记就测红（与 `SchoolSupportMatrixTest` 同一个思路：让文档跟着代码变红）。
 */
class OssLicensesTest {

    private fun buildFile(): File {
        // 单测的工作目录是模块目录（app/）；从仓库根跑则是根目录 —— 两种都认
        val candidates = listOf(File("build.gradle.kts"), File("app/build.gradle.kts"))
        return candidates.firstOrNull { it.isFile && it.readText().contains("dependencies {") }
            ?: error("找不到 app/build.gradle.kts（工作目录=${File(".").absolutePath}）")
    }

    /** `implementation("g:a:v")` / `implementation("g:a")` → `g:a`。 */
    private fun declaredCoordinates(): Set<String> {
        val text = buildFile().readText()
        val regex = Regex("""implementation\(\s*"([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+)(?::[^"]*)?"\s*\)""")
        return regex.findAll(text).map { "${it.groupValues[1]}:${it.groupValues[2]}" }.toSet()
    }

    @Test
    fun `每条直接依赖都登记了许可证`() {
        val registered = ossLicenses.flatMap { it.coordinates }.toSet()
        val declared = declaredCoordinates()
        assertTrue("解析不到任何依赖，门禁本身失效了", declared.isNotEmpty())
        val missing = declared - registered
        assertEquals(
            "这些依赖没有登记许可证（在 data/legal/OssLicenses.kt 补一条）：$missing",
            emptySet<String>(),
            missing,
        )
    }

    @Test
    fun `清单里没有已删除的依赖`() {
        // 反向也要卡：依赖删了、清单还留着，等于告诉用户"我们用了某库"而实际没用
        val declared = declaredCoordinates()
        val stale = ossLicenses.flatMap { it.coordinates }.toSet() - declared
        assertEquals("这些坐标已经不在依赖里了：$stale", emptySet<String>(), stale)
    }

    @Test
    fun `许可证都在白名单内`() {
        val offenders = ossLicenses.filter { it.license !in AllowedLicenses }
        assertEquals("白名单外的许可证要单独决策：${offenders.map { it.name }}", emptyList<OssLicense>(), offenders)
    }

    @Test
    fun `坐标不重复且每条都有出处`() {
        val coordinates = ossLicenses.flatMap { it.coordinates }
        assertEquals("同一个坐标被登记了两次", coordinates.size, coordinates.toSet().size)
        ossLicenses.forEach {
            assertTrue("缺少项目主页：${it.name}", it.url.startsWith("https://"))
            assertTrue("名字不能为空", it.name.isNotBlank())
        }
    }

    @Test
    fun `分组按许可证聚合并按名字排序`() {
        val groups = groupLicensesByLicense(ossLicenses)
        assertEquals(listOf("Apache-2.0", "MIT"), groups.map { it.first })
        val mit = groups.first { it.first == "MIT" }.second
        assertEquals(listOf("jsoup"), mit.map { it.name })
        groups.forEach { (_, list) ->
            assertEquals(list.map { it.name }.sorted(), list.map { it.name })
        }
        // 分组不丢条目
        assertEquals(ossLicenses.size, groups.sumOf { it.second.size })
    }
}
