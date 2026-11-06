package com.pocket.xiaoli.desktop

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 版本号守门：桌面端、打包脚本、安装脚本必须共用仓库根 `gradle.properties` 的 `versionName`。
 *
 * 为什么要有这条测试：0.1.92 / 1.0.0 / 0.1.92-desktop.1 三处各写一份，发出去之后没人能说清
 * 哪个才是真版本。这里把「再手抄一份」变成红灯，而不是靠 review 记得。
 */
class DesktopVersionTest {

    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .firstOrNull { File(it, "gradle.properties").isFile && File(it, "desktop").isDirectory }
        ?: error("找不到仓库根目录（应从 desktop/app 向上找含 gradle.properties + desktop/ 的目录）")

    private val versionName: String = Properties().apply {
        File(repoRoot, "gradle.properties").inputStream().use(::load)
    }.getProperty("versionName")?.trim().orEmpty()

    @Test
    fun generatedConstantFollowsRootVersionName() {
        assertTrue(Regex("""^\d+\.\d+\.\d+$""").matches(versionName), "versionName 不是 x.y.z：$versionName")
        assertEquals(versionName, DESKTOP_VERSION, "生成的 DESKTOP_VERSION 与根 gradle.properties 不一致")
    }

    @Test
    fun packagingScriptsReadTheSingleSource() {
        listOf("tools/package.ps1", "tools/installer.ps1").forEach { relative ->
            val script = File(repoRoot, "desktop/$relative").readText()
            assertTrue(
                script.contains("versionName"),
                "$relative 没有从 gradle.properties 读取 versionName（版本真源必须唯一）",
            )
            assertTrue(
                !Regex("""\$(appVersion|version)\s*=\s*'\d+\.\d+\.\d+'""").containsMatchIn(script),
                "$relative 里还有手抄的版本号字面量",
            )
        }
    }

    @Test
    fun installerDisplaysTheBuildVersion() {
        val program = File(repoRoot, "desktop/installer/app/Program.cs").readText()
        assertTrue(
            !Regex("const\\s+string\\s+Version\\s*=\\s*\"\\d+\\.\\d+\\.\\d+\"").containsMatchIn(program),
            "Program.cs 里还有手抄的版本号；应从程序集 <Version> 读取（tools/installer.ps1 传入）",
        )
    }
}
