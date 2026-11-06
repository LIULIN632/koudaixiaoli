package com.pocket.xiaoli.ui.proactive

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * **全 App 不放说明气泡**（`BubbleTip`「?」）。
 *
 * ## 由来（用户原话，两步）
 *
 * 1. 2026-10：「首页的下一节课提示卡片，改回之前的」—— 那张卡相对上一版**只多了一枚「?」**；
 *    退掉之后他接着说：「**还有首页那些问号的都去掉**」。
 * 2. 2026-10-31：设置页行尾问号撤干净之后，用户点头把剩下的也一起去掉 ——
 *    当时全 App 只剩「AI 设置 → 主动提醒」三档各一枚。组件随之删掉，这条守卫改成**全仓扫描**。
 *
 * ## 口径
 *
 * 一枚都不要。要解释的东西写进**常驻小字**（档位行、副标题、结果留痕），
 * 或者写进对应的详情页；不要挂在标题旁、行尾、图标边。
 * 首页是"扫一眼就出门"的屏，设置页也一样 —— 多一个「?」就多一个要判读的符号，
 * 而它解释的那些事（数据从哪来、什么时候提醒）不该藏起来，也不该打断阅读。
 *
 * ## 为什么用源码扫描而不是截图
 *
 * 一枚 20dp 的「?」在整屏快照里占几十个像素，**图上看漏了很正常**，
 * 而这类东西恰恰是"悄悄加回来"的典型（第一次就是加在卡片标题旁的一张图里被忽略了三周）。
 * 判据落在源码上就没有含糊空间 —— 同 `TabRouteInputFocusTest` / `InlinePasswordTransformationTest`。
 *
 * ## 扫描范围
 *
 * `src/main/java` 下**全部** `.kt`，不是手工清单：上一版本这里只列了首页两个文件，
 * 类 KDoc 还得叮嘱"新增子组件时要把它加进清单"—— 会漏的清单等于没有清单。
 */
class NoBubbleTipTest {

    @Test
    fun `全仓源码里不许出现说明气泡`() {
        val offenders = mainSources().filter { CALL.containsMatchIn(stripComments(it.readText())) }

        assertTrue(
            "这些文件里**调用了**说明气泡（`BubbleTip`，就是那枚「?」）：\n" +
                offenders.joinToString("\n") { "  - ${it.name}" } + "\n\n" +
                "用户 2026-10 与 2026-10-31 两次明确要求：**问号的都去掉**。\n" +
                "要解释「数据从哪来 / 这一档差在哪 / 为什么没成功」，请写进行为本身的小字\n" +
                "（档位行的 cadence、副标题、检查结果的行尾），别挂一枚「?」让人多点一次。\n" +
                "组件已删：`ui/proactive/BubbleTip.kt` 不在仓库里了，要用得先把它请回来 ——\n" +
                "若确实要恢复这个决定，请先改这条测试并写明理由（它是刻意的守门人，不是过时约束）。",
            offenders.isEmpty(),
        )
    }

    /**
     * 只找**调用**（`BubbleTip(` / `BubbleTip {`），不找注释与文档里的提及 ——
     * 页面文件里正大光明地留着"这里曾经挂过一枚说明气泡、为什么退掉"的注释，
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

    /** `src/main/java` 下所有 Kotlin 文件（`app/` 前缀兼容从仓库根或模块目录跑测试）。 */
    private fun mainSources(): List<File> =
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.exists() }
            ?.walkTopDown()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.toList()
            .orEmpty()
            .also {
                if (it.isEmpty()) fail("扫不到 src/main/java —— 工作目录变了，这条守卫会假绿，先修它")
            }

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
            "注释里的提及不该误报（页面文件里留着「这里曾经挂过气泡、为什么退掉」的说明）",
            !CALL.containsMatchIn(stripComments("// 2026-10 这里曾经挂过 BubbleTip(text = …)，已退掉")),
        )
        assertTrue(
            "块注释同理",
            !CALL.containsMatchIn(stripComments("/* BubbleTip(text = …) */")),
        )
    }
}
