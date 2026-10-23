package com.pocket.xiaoli.ui.zhengfang

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「进页面**不许**自动联网」——源码扫描守卫。
 *
 * ## 为什么需要一个扫源码的守卫
 *
 * 2026-10 用户报：「一些功能点进去自动开始，比如调入课表自动开始，查成绩自动开始」。
 * 根因是三个页面在进入时无条件调 `autoLoginIfBound()` / 自动 `startLogin()` ——
 * 用户没要求就联网登录教务，学校要验证码时还会**立刻弹验证码框**打断。
 *
 * 改成"只预填表单、由用户点一下"之后，这件事**没有任何运行时信号可以断言**：
 * 页面渲出来是一样的（表单本来就会预填），单测与基准图全绿，只有真机上那一下自动联网能看出来。
 * 所以判据只能落在**源码**上 —— 与 `TabRouteInputFocusTest` / `HomeNoBubbleTipTest` 同一思路。
 *
 * ## 例外
 *
 * `SavedGradesScreen`（我的课程）里的 `autoLoginIfBound()` 是**用户自己点**「一键同步」触发的，
 * 属于"用户发起的动作"，必须保留。所以白名单只有它一个。
 */
class NoAutoLoginOnEntryTest {

    private val uiRoot = File("src/main/java/com/pocket/xiaoli/ui")

    /** 允许出现自动登录调用的文件（用户点「一键同步」那条路） */
    private val allowlist = setOf("SavedGradesScreen.kt")

    private fun sourceOf(relative: String): String {
        val f = File(uiRoot, relative)
        assertTrue("找不到源文件：${f.path}（工作目录可能不是 app/）", f.isFile)
        return f.readText()
    }

    @Test
    fun `进页面不许自动登录教务`() {
        val files = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in allowlist }
            .filter { f ->
                val text = f.readText()
                CALL_SITE_REGEX.containsMatchIn(text) || ENTRY_LAUNCH_REGEX.containsMatchIn(text)
            }
            .map { it.name }
            .sorted()
            .toList()

        assertEquals(
            "这些文件在进页面时会自动开始联网（自动登录 / 自动导入）。" +
                "改成只预填表单、由用户点一下；用户主动触发的（如「一键同步」）请加进白名单并写明理由。",
            emptyList<String>(),
            files,
        )
    }

    /** 守卫本身有效吗：拿两段真实的历史写法验它抓得到（不然这条守卫可能永远绿） */
    @Test
    fun `守卫抓得到历史写法`() {
        // ① 进页面自动登录（成绩查询 / 修课情况曾经就是这样）
        assertTrue(
            CALL_SITE_REGEX.containsMatchIn("LaunchedEffect(Unit) { viewModel.autoLoginIfBound() }")
        )
        // ② 进页面自动导入（导入页曾经就是这样）
        assertTrue(
            ENTRY_LAUNCH_REGEX.containsMatchIn(
                """
                LaunchedEffect(selectedSchool, viewModel.campusOnly) {
                    if (viewModel.campusOnly && bound != null && !autoImportTriggered) {
                        autoImportTriggered = true
                        viewModel.startLogin(bound.first, bound.second)
                    }
                }
                """.trimIndent()
            )
        )
    }

    /**
     * 守卫**不该**误报的两件事：定义处与注释/文档里提到它。
     * （第一版就是栽在这儿：`ZhengfangLoginViewModel.kt` 是定义它的地方，
     * `GradeQueryViewModel.kt` 只在注释里提了一句，两个都被判成了违规。）
     */
    @Test
    fun `定义与注释不算违规`() {
        assertTrue(!CALL_SITE_REGEX.containsMatchIn("override fun autoLoginIfBound() {"))
        assertTrue(!CALL_SITE_REGEX.containsMatchIn("// 重新登录：`autoLoginIfBound()` 已把绑定凭据写进表单"))
        // 用户自己点击触发的 startLogin（onClick 里）不算"进页面自动开始"
        assertTrue(!ENTRY_LAUNCH_REGEX.containsMatchIn("TextButton(onClick = { viewModel.startLogin() })"))
    }

    /** 顺带钉住"只有我的课程那一条路"用了自动登录 */
    @Test
    fun `只有我的课程的一键同步保留自动登录`() {
        assertTrue(sourceOf("treasurebox/SavedGradesScreen.kt").contains("autoLoginIfBound()"))
    }

    private companion object {
        /** 调用点：`viewModel.autoLoginIfBound()`（带接收者，因此定义处与注释都不会命中） */
        val CALL_SITE_REGEX = Regex("""\w+\.autoLoginIfBound\(\)""")

        /** 「进页面就登录」的形状：`LaunchedEffect(...) { ... startLogin(` */
        val ENTRY_LAUNCH_REGEX = Regex("""LaunchedEffect\([^)]*\)[^{]*\{[^}]*startLogin\(""")
    }
}
