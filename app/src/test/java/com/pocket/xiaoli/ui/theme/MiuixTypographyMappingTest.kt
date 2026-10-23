package com.pocket.xiaoli.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MiuixTypographyMapping.kt` 的守门人。
 *
 * ## 为什么必须是源码扫描，而不是渲染断言
 *
 * 这条通道 **当前零消费者** —— 实测（`MiuixTheme(textStyles = 40sp)` 荒谬值）
 * 70 张基线一张都没变。所以：
 *
 * - **截图测试永远发现不了它写错了**（写了不生效）；
 * - **单元测试也拿不到它**（`miuixTextStyles()` 是 `@Composable`，
 *   且 `TextStyles` 内部是 `MutableState`，纯 JVM 断言不便构造）。
 *
 * 剩下的可靠判据只有**源码层面**的不变量 —— 与 `TabRouteInputFocusTest` /
 * `InlinePasswordTransformationTest` / `HomeNoBubbleTipTest` 同一思路：
 * **看不见的东西，判据落在源码上；剩下的一半靠人。**
 *
 * ## 守的四条
 *
 * | # | 不变量 | 挡住什么 |
 * |---|---|---|
 * | 1 | **14 个槽全部赋值** | MIUIX 升级加槽 / 手打漏一个 → 那个槽沿用 MIUI 的 17sp 字阶，与周围差一圈 |
 * | 2 | 每个值都来自 `PocketTypography.` | 有人"顺手写个更合适的字号"→ 出现第二个字阶真源 |
 * | 3 | 文件里**不许出现** `fontSize =` / `TextStyle(` | 同上，但拦的是绕过 §2 的写法 |
 * | 4 | ⭐ 它**真的被接线**（`Theme.kt` 里有 `textStyles = miuixTextStyles()`）| **"定义了没接"** —— 本项目记录在案过两次（`assistantFontMultiplier`、`servicesShowSectionTitle`）|
 *
 * ⚠️ 第 1 条的那 14 个名字是**从 AAR 里 javap 出来的**，不是抄文档：
 * `top.yukonga.miuix.kmp.theme.TextStyles` 的 14 个 `MutableState` 委托字段
 * = main / paragraph / body1 / body2 / button / footnote1 / footnote2 /
 * headline1 / headline2 / subtitle / title1 / title2 / title3 / title4。
 * **MIUIX 升级后如果它变了，这条会红** —— 那正是我们想知道的。
 */
class MiuixTypographyMappingTest {

    private fun readSource(relPath: String): String {
        val candidates = listOf(File(relPath), File("app/$relPath"))
        val hit = candidates.firstOrNull { it.exists() }
        assertTrue(
            "找不到源文件 $relPath；已尝试：${candidates.joinToString { it.absolutePath }}",
            hit != null,
        )
        return hit!!.readText()
    }

    private val mapping: String by lazy {
        readSource("src/main/java/com/pocket/xiaoli/ui/theme/MiuixTypographyMapping.kt")
    }

    private val theme: String by lazy {
        readSource("src/main/java/com/pocket/xiaoli/ui/theme/Theme.kt")
    }

    /**
     * 剥掉 KDoc 与行注释，只留代码。
     *
     * ⚠️ **第一版没有这一步，结果守卫被自己的 KDoc 判红** ——
     * 本文件（映射文件）的 KDoc 里写着"会拦下任何 `fontSize =` 字面量"这句话，
     * 而扫描器把**注释里的这句话**当成了真的字面量。
     *
     * 这是源码扫描型守卫的通病，本项目已有先例：`HomeNoBubbleTipTest` 专门带一条
     * "守卫本身有效"的用例（证明抓得到调用、**不误伤注释**）。
     * **凡是扫源码的守卫，都必须先剥注释。**
     */
    private fun codeOnly(src: String): String = src
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    /** MIUIX `TextStyles` 的 14 个槽（javap 自 `miuix-ui` 的 AAR）。 */
    private val slots = listOf(
        "main", "paragraph", "body1", "body2", "button",
        "footnote1", "footnote2", "headline1", "headline2", "subtitle",
        "title1", "title2", "title3", "title4",
    )

    @Test
    fun `14 个槽全部赋值`() {
        val code = codeOnly(mapping)
        val missing = slots.filterNot { Regex("""^\s*$it\s*=\s*PocketTypography\.""", RegexOption.MULTILINE).containsMatchIn(code) }
        assertEquals(
            "这些 MIUIX 槽没有赋值，会沿用 MIUI 的 17sp 字阶（比周围文字大一圈）：" +
                missing.joinToString("、"),
            emptyList<String>(),
            missing,
        )
        assertEquals("MIUIX 的 TextStyles 是 14 个槽", 14, slots.size)
    }

    @Test
    fun `每个值都来自 PocketTypography，没有第二个字阶真源`() {
        val assignments = Regex("""^\s*(\w+)\s*=\s*(.+?),\s*$""", RegexOption.MULTILINE)
            .findAll(codeOnly(mapping))
            .map { it.groupValues[1] to it.groupValues[2].trim() }
            .filter { (name, _) -> name in slots }
            .toList()

        assertEquals("应恰好解析到 14 条赋值", 14, assignments.size)

        val bad = assignments.filterNot { (_, value) -> value.startsWith("PocketTypography.") }
        assertEquals(
            "这些槽的值不是来自 PocketTypography —— 会造出第二个字阶真源：" +
                bad.joinToString("、") { "${it.first}=${it.second}" },
            emptyList<String>(),
            bad.map { it.first },
        )
    }

    @Test
    fun `文件里不许出现字号字面量`() {
        val offenders = Regex("""fontSize\s*=|TextStyle\(""")
            .findAll(codeOnly(mapping))
            .map { it.value }
            .toList()
        assertEquals(
            "映射文件里出现了字号字面量 —— 本文件只许【搬运】PocketTypography 的值：" +
                offenders.joinToString("、"),
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `守卫本身有效——剥注释后仍抓得到字面量`() {
        // 反向自检：证明上面那条不是"永远绿"的假守卫。
        val probe = "val x = TextStyle(fontSize = 99.sp)"
        assertTrue(
            "剥离注释后的扫描器抓不到字面量 —— 说明它已失效",
            Regex("""fontSize\s*=|TextStyle\(""").containsMatchIn(codeOnly(probe)),
        )
        // 且它确实不误伤注释（这就是第一版踩的坑）。
        assertTrue(
            "扫描器误伤了注释 —— 必须只扫代码",
            !Regex("""fontSize\s*=|TextStyle\(""").containsMatchIn(codeOnly("// 拦下任何 `fontSize =` 字面量")),
        )
    }

    @Test
    fun `它真的被接线到 MiuixTheme`() {
        assertTrue(
            "Theme.kt 里没有把 textStyles 传给 MiuixTheme —— " +
                "这就是『定义了没接』（本项目在 assistantFontMultiplier 与 " +
                "servicesShowSectionTitle 上各踩过一次）。",
            Regex("""textStyles\s*=\s*miuixTextStyles\(\)""").containsMatchIn(theme),
        )
    }
}
