package com.pocket.xiaoli.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清单里声明的组件必须真的有对应的类。
 *
 * 2026-11-07 线上崩溃（v0.2.25，vivo V2307A / Android 16）：
 * ```
 * RuntimeException: Unable to instantiate receiver com.pocket.xiaoli.notification.BootReceiver
 * Caused by: ClassNotFoundException: Didn't find class "…BootReceiver"
 * ```
 * `android:name=".notification.BootReceiver"` 从 v0.1.80 就写在清单里，**类从来没写过**。
 * 编译不管、单测不管、R8 也不管 —— 只有系统在 `BOOT_COMPLETED` 时按名字实例化才炸。
 *
 * 这条守门测试做一件很笨但有效的事：把清单里所有以 `.` 开头的组件名
 * （activity / receiver / service / provider / application）逐个换算成源码路径，
 * 断言文件存在。第三方组件（`androidx…`）不检查。
 */
class ManifestComponentsExistTest {

    private val appDir: File = File(".").canonicalFile
    private val mainDir: File = File(appDir, "src/main")

    private val manifest: String by lazy {
        File(mainDir, "AndroidManifest.xml").readText()
    }

    private val javaRoot: File by lazy {
        File(mainDir, "java")
    }

    @Test
    fun `清单里声明的自有组件都有对应类`() {
        val declared = Regex("""android:(?:name)="(\.[A-Za-z0-9_.]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1].removePrefix(".") }
            .distinct()
            .toList()

        assertTrue("清单里没有找到任何自有组件？解析可能坏了", declared.size > 5)

        // 清单里的 `.notification.X` 是**相对 applicationId** 的，源码包根是 com.pocket.xiaoli。
        val packageRoot = "com/pocket/xiaoli"
        val missing = declared.filter { rel ->
            val file = File(File(javaRoot, packageRoot), rel.replace('.', '/') + ".kt")
            !file.exists()
        }

        assertEquals(
            "这些组件在 AndroidManifest.xml 里声明了，但没有对应的 .kt —— " +
                "系统实例化时会 ClassNotFoundException 直接崩：$missing",
            emptyList<String>(),
            missing,
        )
    }
}
