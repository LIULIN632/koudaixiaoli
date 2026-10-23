package com.pocket.xiaoli.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「动效时长只能来自 [Motion]」的源码静态守卫。
 *
 * ## 守的是什么
 *
 * 改造前全 App 有 26 处 `tween(...)` 直接写数字（120/150/180/260/400/700）。
 * 散着写的坏处不是"不好看"，而是**改不动**：数字不携带语义，
 * 想调快整体节奏就得逐个判断哪个是进场、哪个是退场。
 * [Motion] 收编之后，**新写的字面值会让这个 token 重新退化成摆设** ——
 * 所以需要一条守卫，而不是靠自觉。
 *
 * ## 判据
 *
 * 1. 除下方 [registeredLiterals] 登记的文件外，**任何源文件都不得出现字面时长**
 *   —— `tween(180)`、`durationMillis = 120`、以及 `tween(if (…) 520 else 900)`
 *   这类条件写法都算（判据是"`tween` 的参数体里有没有裸数字"，见 `hasLiteral`）；
 * 2. 登记过的文件必须**仍然**含字面时长 —— 否则说明它已经迁走了，
 *    这条登记就成了永远不会失效的豁免（本项目在别处栽过"守卫名存实亡"的跟头）。
 *
 * ## 为什么是"登记"而不是"硬禁"
 *
 * 剩下这三处的**语义与已收编的四档都不同**，强行归并等于替它们做设计决定：
 *
 * | 位置 | 值 | 为什么不是现有四档 |
 * |---|---|---|
 * | `GradeDistributionChart` | 400ms | 图表数值动画，数据可视化需要更长的时间让人跟上 |
 * | `HomeScreen` | 260ms + `delayMs` | **列表入场错峰**：时长配每项递增的延迟，是另一类语义 |
 * | `AiAvatar` | 520 / 900ms | **按阶段切换**的呼吸节奏（查工具比思考更急），是 [Motion.breath] 的变体 |
 *
 * 这与 `tools/lint_design.py` 用 BASELINE 而非硬门限是同一思路：
 * **新增违规才是回归**。
 */
class MotionTokenUsageTest {

    private companion object {
        /**
         * 取 `tween(...)` 的参数体，判断体内是否含**裸数字**。
         *
         * ⚠️ 不能只用 `tween\(\s*\d` 这种正则：那样会**漏掉条件形式** ——
         * `tween(if (state == TOOL) 520 else 900)` 里 `tween(` 后面不是数字。
         * 这个盲区是本测试的反向断言先发现的（它报「AiAvatar 的登记失效了」，
         * 而实际是探测模式看不见它）。
         *
         * 判据：参数体里出现任何 `\b\d+` 即为字面时长 —— 因为走 token 的写法
         * （`tween(Motion.enter)`）里**一个数字都没有**。
         */
        val BARE_NUMBER = Regex("""\b\d+""")

        /**
         * 已登记的例外：路径后缀 → 理由。
         *
         * ⚠️ 往这里加一条**必须有理由**，且应该优先考虑"它其实属于 [Motion] 的哪一档"。
         */
        val registeredLiterals = mapOf(
            "ui/components/infocard/GradeDistributionChart.kt" to
                "图表数值动画 400ms：数据可视化需要更长的时间让人跟上",
            "ui/home/HomeScreen.kt" to
                "列表入场错峰 260ms + 每项递增的 delayMs：与通用进出场不是一类语义",
            "ui/theme/AiAvatar.kt" to
                "按阶段切换的呼吸节奏 520/900ms：是 Motion.breath 的变体，未归并",
            "ui/proactive/ProactiveBubble.kt" to
                "气泡弹出：90/110/140/160ms 与两处 spring 是**成对调**的（淡入必须比缩放快得多收尾，" +
                    "退场比入场快），归进 120/150/180 任一档都会把这一对的相对节奏打散 —— " +
                    "数值直接照搬参考实现，观感是移植的一部分",
            "ui/map3d/Campus3DScreen.kt" to
                "3D 地图的镜头飞行 460ms（animateCameraTo）：它插值的是相机位置而不是界面元素的" +
                    "进入/展开，距离与视场变化都远大于通用转场，套 180ms 会像被甩过去；" +
                    "等这套相机动画定型后，再决定要不要给它单开一档",
        )

        /** 定义 [Motion] 自己的文件当然可以有数字（那里正是取值的真源）。 */
        const val MOTION_FILE = "ui/theme/Motion.kt"

        /** 返回 [openIndex] 处 `(` 到其配对 `)` 的区间；配对失败返回 null。 */
        fun matchingParen(source: String, openIndex: Int): IntRange? {
            var depth = 0
            var i = openIndex
            var inString = false
            while (i < source.length) {
                val c = source[i]
                when {
                    inString -> when {
                        c == '\\' -> i++
                        c == '"' -> inString = false
                    }
                    c == '"' -> inString = true
                    c == '(' -> depth++
                    c == ')' -> {
                        depth--
                        if (depth == 0) return openIndex..i
                    }
                }
                i++
            }
            return null
        }
    }

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/pocket/xiaoli"),
            File("app/src/main/java/com/pocket/xiaoli"),
        )
        val hit = candidates.firstOrNull { it.isDirectory }
        assertTrue(
            "找不到源码根目录；已尝试：${candidates.joinToString { it.absolutePath }}",
            hit != null,
        )
        return hit!!
    }

    /** 目录下所有 .kt 的相对路径 → 内容。 */
    private fun allSources(): Map<String, String> {
        val root = sourceRoot()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to file.readText()
            }
    }

    /** 文件里是否出现了"带字面时长的 tween"（含 `durationMillis = N` 与条件形式）。 */
    private fun hasLiteral(text: String): Boolean {
        if (Regex("""durationMillis\s*=\s*\d""").containsMatchIn(text)) return true
        var i = 0
        while (true) {
            val idx = text.indexOf("tween(", i)
            if (idx < 0) return false
            val open = idx + "tween".length
            val range = matchingParen(text, open) ?: return false
            val body = text.substring(range.first, range.last + 1)
            if (BARE_NUMBER.containsMatchIn(body)) return true
            i = range.last + 1
        }
    }

    @Test
    fun `除登记例外外，任何文件都不得写字面时长`() {
        val offences = allSources()
            .filterKeys { it != MOTION_FILE }
            .filterKeys { rel -> registeredLiterals.keys.none { rel.endsWith(it) } }
            .filterValues { hasLiteral(it) }
            .keys
            .sorted()

        assertTrue(
            "这些文件里出现了字面动效时长，没有走 `Motion`：\n" +
                offences.joinToString("\n") { "  - $it" } +
                "\n\n改法：用 `tween(Motion.enter)` 这类写法。\n" +
                "若它的语义**确实**不属于现有四档（进出场 / 展开 / 呼吸），\n" +
                "再考虑给 `Motion` 加一档 —— 并在 `MotionTokenUsageTest.registeredLiterals` 里说明理由，\n" +
                "而不是就地写个数字。",
            offences.isEmpty(),
        )
    }

    /**
     * 反向断言：登记过的文件必须**仍然**含字面时长。
     *
     * 否则文件被迁移之后，那条登记会变成**永远不会失效的豁免** ——
     * 守卫看起来还在，实际已经放行整个文件了。
     */
    @Test
    fun `登记例外必须仍然存在，否则应删除登记`() {
        val sources = allSources()
        val stale = registeredLiterals.keys.filter { suffix ->
            val match = sources.entries.firstOrNull { it.key.endsWith(suffix) }
            // 文件被删/改名、或者里面的字面时长已被迁走 → 登记都该清掉
            match == null || !hasLiteral(match.value)
        }
        assertTrue(
            "这些登记已失效（文件不存在，或里面的字面时长已被迁到 Motion）——\n" +
                "请从 registeredLiterals 里删掉，否则它是一条永不放行的豁免：\n" +
                stale.joinToString("\n") { "  - $it" },
            stale.isEmpty(),
        )
    }

    /** [Motion] 的四档取值本身要稳定 —— 它们被四处以上引用，改了就是一次全局节奏变更。 */
    @Test
    fun `Motion 的四档取值不得随意变动`() {
        assertTrue("exit 应为 120ms", Motion.exit == 120)
        assertTrue("enter 应为 150ms", Motion.enter == 150)
        assertTrue("expand 应为 180ms", Motion.expand == 180)
        assertTrue("breath 应为 700ms", Motion.breath == 700)
    }
}
