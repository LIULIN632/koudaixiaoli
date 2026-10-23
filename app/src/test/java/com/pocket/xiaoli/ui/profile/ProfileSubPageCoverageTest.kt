package com.pocket.xiaoli.ui.profile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的」页子页路由的**完备性**守卫（源码静态断言）。
 *
 * ## 这个结构**真实漏过一次**
 *
 * `ProfileScreen` 用一组 `showXxx: Boolean` 管理子页，同一个概念要在**三处各写一遍**：
 *
 * ```
 * var showA by remember { … }                        // ① 声明
 * val hasSubPage = showA || showB || …               // ② 汇总（喂给 BackHandler 与底栏显隐）
 * BackHandler(hasSubPage) { when { showA -> … } }    // ③ 复位
 * ```
 *
 * 文件里 271 行留着原话：
 *
 * > 「「自定义首页」「开发者实验室」**原先就漏在列表外，本次一并补上**。」
 *
 * 而漏掉 ② 的后果**不是"返回键没反应"**：`hasSubPage = false` → `BackHandler` **根本没装上**
 * → 系统返回键**直接退出整个 App**，同时底栏也不会隐藏。
 * `TreasureBoxScreen` 上发生过同类事故（2026-10-02 漏了五个标志）。
 *
 * ## 它守的是**双向集合相等**
 *
 * | 方向 | 漏了的后果 |
 * |---|---|
 * | 在 ③ 里、不在 ② 里 | `BackHandler` 装不上 → **返回键退出 App** |
 * | 在 ② 里、不在 ③ 里 | `BackHandler` 装上了，但 `when` 没有命中分支 → **返回键没反应** |
 *
 * 两个方向都是真 bug，所以断言相等，而不是单向包含。
 *
 * ⚠️ **必须整体读文件、用跨行正则取区段** —— 本测试的第一版设计曾打算用
 * `Select-String` 式按行匹配去找 `hasSubPage`，但那条链是**跨 272-278 行的多行 `||` 表达式**，
 * 按行只会看到第一行，于是会得出"只汇总了 4 个"的错误结论。
 * **凡判断"表达式是否覆盖了 N 项"，必须跨行取区段。**
 */
class ProfileSubPageCoverageTest {

    private val screen: String by lazy {
        val rel = "src/main/java/com/pocket/xiaoli/ui/profile/ProfileScreen.kt"
        val candidates = listOf(File(rel), File("app/$rel"))
        val hit = candidates.firstOrNull { it.exists() }
        assertTrue(
            "找不到 ProfileScreen.kt；已尝试：${candidates.joinToString { it.absolutePath }}",
            hit != null,
        )
        hit!!.readText()
    }

    /** 取 [startMarker, endMarker) 之间的原文（**跨行**）。 */
    private fun region(startMarker: String, endMarker: String): String {
        val s = screen.indexOf(startMarker)
        assertTrue("找不到起始标记：$startMarker", s >= 0)
        val e = screen.indexOf(endMarker, s)
        assertTrue("找不到结束标记：$endMarker", e > s)
        return screen.substring(s, e)
    }

    /** 区段里出现的子页状态名（`showXxx` 与 `profileServiceWeb`）。 */
    private fun flagsOf(text: String): Set<String> =
        Regex("""\b(show[A-Z]\w*|profileServiceWeb)\b""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()

    private fun summaryChain() = region("val hasSubPage =", "LaunchedEffect(hasSubPage)")

    private fun backHandlerBlock() =
        region("BackHandler(enabled = hasSubPage)", "if (showTreasureBoxDialog) {")

    @Test
    fun `守卫本身有效——跨行区段能取全 16 个状态`() {
        // 反向自检：证明区段跨行成立（若哪天有人把链拆成多个 val，这条会红并提醒改本测试）。
        val chain = flagsOf(summaryChain())
        assertTrue(
            "hasSubPage 汇总链里只解析到 ${chain.size} 个状态，疑似区段取漏（原为 16 个）：$chain",
            chain.size >= 16,
        )
    }

    @Test
    fun `BackHandler 处理的每个状态都必须进 hasSubPage 汇总`() {
        val handled = flagsOf(backHandlerBlock())
        val summed = flagsOf(summaryChain())
        val missing = (handled - summed).sorted()
        assertEquals(
            "这些状态在 BackHandler 里有分支，却没进 hasSubPage —— " +
                "后果是 `hasSubPage=false` → BackHandler 根本没装上 → 系统返回键【直接退出整个 App】：" +
                missing.joinToString("、"),
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `hasSubPage 汇总里的每个状态都必须在 BackHandler 里有分支`() {
        val handled = flagsOf(backHandlerBlock())
        val summed = flagsOf(summaryChain())
        val dangling = (summed - handled).sorted()
        assertEquals(
            "这些状态进了 hasSubPage，却在 BackHandler 的 when 里没有分支 —— " +
                "后果是装上了 BackHandler 但按返回键【没反应】：" +
                dangling.joinToString("、"),
            emptyList<String>(),
            dangling,
        )
    }
}
