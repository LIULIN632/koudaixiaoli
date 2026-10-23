package com.pocket.xiaoli.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守门人：**状态栏那一条谁负责让位**。
 *
 * 2026-10-19 用户两次提到状态栏（先说「为啥不统一」、再要「跟顶栏一样是液态玻璃」），
 * 两次的根都不在某个页面里，而在这套「边到边 + 分层让位」的约定上。约定一旦被悄悄改坏，
 * 表现是「某些页面顶上多出一条带子」或「整页被顶下去一条状态栏」，单测和截图都看不出来
 *（Robolectric 不注入系统栏 inset）。所以这里用源码扫描把三件事钉死：
 *
 * 1. `MainActivity` 必须开边到边，否则任何页面都画不到状态栏底下，玻璃顶栏无从谈起；
 * 2. `AppNavigation` 必须 `consumeWindowInsets(hostPadding)`，否则子页面会二次让位；
 * 3. 用玻璃顶栏的页面清单必须与 `GLASS_TOP_ROUTES` 对齐 —— 新加一条玻璃顶栏的页面
 *    却忘了登记，就会退回「上面压一条纯色带」的老样子。
 */
class StatusBarInsetContractTest {

    private fun source(relative: String): String {
        val direct = File(relative)
        val file = if (direct.exists()) direct else File("app/$relative")
        assertTrue("找不到源文件：${file.absolutePath}", file.exists())
        return file.readText()
    }

    /** 只看代码行：注释里为了讲清历史会写到这些名字，那不算违规。 */
    private fun codeOnly(text: String): String = text.lines()
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
        .joinToString("\n")

    @Test
    fun `MainActivity 开启了边到边`() {
        val code = codeOnly(source("src/main/java/com/pocket/xiaoli/MainActivity.kt"))
        assertTrue("MainActivity 不再调用 enableEdgeToEdge：状态栏会重新被系统吃掉一条高度", code.contains("enableEdgeToEdge("))
        assertTrue("边到边的系统栏必须透明，否则会盖住玻璃顶栏", code.contains("SystemBarStyle.auto("))
    }

    @Test
    fun `路由层必须消费掉自己让出的 inset`() {
        val code = codeOnly(source("src/main/java/com/pocket/xiaoli/ui/navigation/AppNavigation.kt"))
        assertTrue(
            "AppNavigation 少了 consumeWindowInsets(hostPadding)：子页面会按窗口 inset 再让一次，" +
                "顶栏整体下移一条状态栏的高度",
            code.contains("consumeWindowInsets(hostPadding)"),
        )
    }

    /**
     * 用 `glassTopBarSurface` 的页面必须都在 `GLASS_TOP_ROUTES` 里登记。
     *
     * 反过来（登记了但页面已经不用玻璃）不在这里管：那种情况只是白让一条位，
     * 布局仍然正确，不值得为它增加一条脆弱的断言。
     */
    @Test
    fun `玻璃顶栏的页面都登记在 GLASS_TOP_ROUTES 里`() {
        val navCode = codeOnly(source("src/main/java/com/pocket/xiaoli/ui/navigation/AppNavigation.kt"))
        val declared = Regex("""private val GLASS_TOP_ROUTES = setOf\(([^)]*)\)""")
            .find(navCode)
            ?.groupValues
            ?.get(1)
            ?: error("AppNavigation 里找不到 GLASS_TOP_ROUTES 声明")
        val routes = Regex("""\"([a-z_]+)\"""").findAll(declared).map { it.groupValues[1] }.toSet()
        assertTrue("GLASS_TOP_ROUTES 解析为空，断言失去意义", routes.isNotEmpty())

        // 谁在用玻璃顶栏：按文件扫，文件名 -> 期望登记的路由
        val glassScreens = mapOf(
            "src/main/java/com/pocket/xiaoli/ui/assistant/AssistantScreen.kt" to "assistant",
            "src/main/java/com/pocket/xiaoli/ui/assistant/AssistantConversationListScreen.kt" to "assistant_history",
            "src/main/java/com/pocket/xiaoli/ui/assistant/AssistantAiSettingsScreen.kt" to "assistant_ai_settings",
        )
        glassScreens.forEach { (path, route) ->
            val code = codeOnly(source(path))
            assertTrue("$path 已经不画玻璃顶栏了，如果是故意的请同步改这个测试", code.contains("glassTopBarSurface("))
            assertTrue("$path 用了玻璃顶栏，但 $route 不在 GLASS_TOP_ROUTES 里：它的顶栏会被顶到状态栏下面", route in routes)
        }
        assertEquals("登记了用不到的路由会让那条路由白丢一条状态栏的高度", glassScreens.values.toSet(), routes)
    }
}
