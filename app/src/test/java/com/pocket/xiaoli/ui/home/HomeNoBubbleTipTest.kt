package com.pocket.xiaoli.ui.home

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * **首页不放说明气泡**（`BubbleTip`「?」）。
 *
 * ## 由来（2026-10，用户原话）
 *
 * 1. "首页的下一节课提示卡片，改回之前的" —— 那张卡相对上一版**只多了一枚「?」**；
 * 2. 退掉之后他说："**还有首页那些问号的都去掉**"。
 *
 * 于是首页的处理口径定死：**一枚都不要**。首页是"扫一眼就出门"的屏，多一个「?」
 * 就多一个要判读的符号，而它解释的那些事（数据从哪来、什么时候提醒）**不是这一屏的任务**。
 *
 * ## 为什么用源码扫描而不是截图
 *
 * 一枚 20dp 的「?」在整屏快照里占几十个像素，**图上看漏了很正常**，
 * 而这类东西恰恰是"悄悄加回来"的典型（这次就是加在卡片标题旁的一张图里被忽略了三周）。
 * 判据落在源码上就没有含糊空间 —— 同 `TabRouteInputFocusTest` / `InlinePasswordTransformationTest`。
 *
 * ## ⚠️ 它守哪些文件
 *
 * 只守**首页正文**这两个文件（`HomeScreen.kt` 渲染正文 + `TodoRows.kt` 是它渲染的待办区）。
 * 新增"首页正文里的子组件"时**要把它加进 [homeBodyFiles]**，否则本测试不知道它存在。
 * 其余页面（如「AI 设置」的主动提醒段）**照常有**「?」—— 那是设置页，本来就该解释清楚。
 */
class HomeNoBubbleTipTest {

    /** 首页正文相关文件（手工清单，见类 KDoc）。 */
    private val homeBodyFiles = listOf(
        "src/main/java/com/pocket/xiaoli/ui/home/HomeScreen.kt",
        "src/main/java/com/pocket/xiaoli/ui/home/TodoRows.kt",
    )

    @Test
    fun `首页正文里不许出现说明气泡`() {
        val offenders = homeBodyFiles.filter { relative ->
            CALL.containsMatchIn(stripComments(readSource(relative)))
        }

        assertTrue(
            "首页这些文件里**调用了**说明气泡（`BubbleTip`，就是那枚「?」）：\n" +
                offenders.joinToString("\n") { "  - $it" } + "\n\n" +
                "用户 2026-10 明确要求：**首页那些问号的都去掉**。\n" +
                "要解释「数据从哪来 / 什么时候提醒」，请写进对应的详情页或「AI 设置」，\n" +
                "别挂在首页的卡片与分区标题上 —— 首页是扫一眼就出门的屏。\n" +
                "若确实要恢复这个决定，请先改这条测试并写明理由（它是刻意的守门人，不是过时约束）。",
            offenders.isEmpty(),
        )
    }

    /**
     * 只找**调用**（`BubbleTip(` / `BubbleTip {`），不找注释与文档里的提及 ——
     * 首页文件里正大光明地留着"这里曾经挂过一枚说明气泡、为什么退掉"的注释，
     * 那是要留的知识，不该被这条守卫当成违规。
     */
    private val CALL = Regex("""BubbleTip\s*[({]""")

    /**
     * 粗暴剥掉 `//` 行注释与 `/* */` 块注释。
     *
     * ⚠️ 不做字符串字面量感知（会被 `"…//…"` 这种内容骗到）—— 与
     * `TabRouteInputFocusTest` 里的配对逻辑同一档次的启发式。它只用来**减少误报**，
     * 判错的代价是"多报一次、人来确认"，不是漏放。
     */
    private fun stripComments(source: String): String =
        source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

    /**
     * 守卫**自己**的有效性：证明它抓得到调用、且不会被注释误伤。
     *
     * 为什么要写这条：一条只会"永远绿"的源码扫描守卫等于没有守卫
     * （判据错了、或者正则写成了匹配不到任何东西，它照样每次都过）。
     * 这里用合成的源码片段把两种行为都钉住。
     */
    @Test
    fun `守卫本身有效：抓得到调用、不误伤注释`() {
        assertTrue(
            "内联调用必须被抓到 —— 否则这条守卫等于没有",
            CALL.containsMatchIn(stripComments("""val tip = BubbleTip(text = "x")""")),
        )
        assertTrue(
            "尾随 lambda 的调用形式也要抓到",
            CALL.containsMatchIn(stripComments("BubbleTip { Text(it) }")),
        )
        assertTrue(
            "注释里的提及不该误报（首页文件里留着「这里曾经挂过气泡、为什么退掉」的说明）",
            !CALL.containsMatchIn(stripComments("// 2026-10 这里曾经挂过 BubbleTip(text = …)，已退掉")),
        )
        assertTrue(
            "块注释同理",
            !CALL.containsMatchIn(stripComments("/* BubbleTip(text = …) */")),
        )
    }

    private fun readSource(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val hit = candidates.firstOrNull { it.exists() }
        if (hit == null) {
            fail(
                "找不到源文件 $relative；已尝试：${candidates.joinToString { it.absolutePath }}\n" +
                    "若首页正文换了文件名/位置，请同步更新本测试的 [homeBodyFiles] 清单。",
            )
        }
        return hit!!.readText()
    }
}
