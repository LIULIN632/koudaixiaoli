package com.pocket.xiaoli.ui.treasurebox

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务页子页路由的**完备性**单测（源码静态断言）。
 *
 * ## 这个测试的历史：它守过的东西已经"结构上不可能发生"了
 *
 * 2026-10 之前，`TreasureBoxScreen` 用 **24 个 `showXxx: Boolean`** 手工管理子页，
 * 于是同一个概念要在**三处各写一遍**：
 *
 * ```
 * var showA by remember { … }                          // ① 声明 24 行
 * val subPageVisible = showA || showB || …             // ② 汇总 24 项
 * BackHandler(subPageVisible) { showA = false; … }     // ③ 复位 24 行
 * ```
 *
 * 当时这个测试守的是「②③ 的集合与 ① 相等」。
 * **漏掉②的后果不是"返回键没反应"**：`subPageVisible` 仍为 `false` ⇒ `BackHandler`
 * 根本没装上 ⇒ 系统返回键**直接退出整个 App**，同时底栏也不会隐藏。
 * 2026-10-02 实测这里漏了 5 个（`QuickShowServiceEdit` / `ExpressPickup` / `GymInfo` /
 * `DrivingInfo` / `CardAccountDialog`）；同类事故在 `ProfileScreen.hasSubPage` 上也发生过。
 *
 * ⚠️ **2026-10 已换成 sealed 路由**（[ServiceSubPage]）：三处塌成一处 ——
 * `subPage != null` 就是"有子页"，`subPage = null` 就是"复位"。
 * 所以**原来那三条断言已经没有可断言的对象了**，本测试随之改写为守卫**新结构的不变量**。
 *
 * ## 它现在守的五条
 *
 * | # | 不变量 | 挡住什么 |
 * |---|---|---|
 * | 1 | `subPageVisible` 必须**派生自** `subPage` | 有人把枚举式汇总加回来 |
 * | 2 | 源码里不许再有 `var showXxx by remember` | 有人把 24 个布尔量的老写法加回来 |
 * | 3 | `BackHandler` 复位体是**单一** `subPage = null` | 有人在复位体里逐项枚举（老病的复发口）|
 * | 4 | **每个 `ServiceSubPage` 条目都被接线**：至少一次置位（`subPage = ServiceSubPage.X`）+ 一次比较（`subPage == ServiceSubPage.X`）| 加了新子页却忘了接线 —— **这是原"集合相等"在新结构下的对应物，而且更强**（同时挡住"无法到达的子页"和"漏进 dispatcher 的子页"）|
 * | 5 | 列表滚动状态声明在**子页分发之前**，两个列表分支共用 | 状态跟着 `return` 一起被销毁，从子页返回时跳回顶部（2026-10 用户反馈）|
 *
 * ⚠️ 用源码扫描（非反射）的理由与原版相同：`subPage` 是 Composable 内的局部状态，
 * JVM 单测拿不到。路径按 Gradle Test worker 的工作目录（模块根）解析，
 * 与项目既有源码扫描型测试同一思路。
 */
class TreasureBoxSubPageCoverageTest {

    private fun readSource(relPath: String): String {
        // Gradle Test worker 的 CWD 是模块目录（app/），兼容从仓库根跑的情况。
        val candidates = listOf(File(relPath), File("app/$relPath"))
        val hit = candidates.firstOrNull { it.exists() }
        assertTrue(
            "找不到源文件 $relPath；已尝试：${candidates.joinToString { it.absolutePath }}",
            hit != null,
        )
        return hit!!.readText()
    }

    private val screen: String by lazy {
        readSource("src/main/java/com/pocket/xiaoli/ui/treasurebox/TreasureBoxScreen.kt")
    }

    private val routeType: String by lazy {
        readSource("src/main/java/com/pocket/xiaoli/ui/treasurebox/ServiceSubPage.kt")
    }

    /** `ServiceSubPage` 里声明的全部条目名。 */
    private fun declaredEntries(): List<String> =
        Regex("""data\s+object\s+(\w+)\s*:\s*ServiceSubPage""")
            .findAll(routeType)
            .map { it.groupValues[1] }
            .toList()

    /**
     * ⚠️ 已知「**没有任何入口能进得去**」的目的地。
     *
     * **这是本次 sealed 路由重构时守卫抓出来的前置缺陷，不是重构造成的**：
     *
     * - `ServiceEntry("选课", …)` 的 `onClick` 走的是
     *   `openCampusService(CampusServiceKeys.COURSE_SELECT, "选课")` —— **外部/网页**路径；
     * - 而原生 `CourseSelectScreen` 虽然完整写好了、也接进了 dispatcher，
     *   却**没有任何地方置位它** → 那个原生页当前是**死代码**。
     *   （它的 KDoc 自述定位是"安全入口 + 登录态复用 + 流程引导，不代选"。）
     *
     * **两条路要产品决定，别擅自改**：
     * 1. 让「选课」入口进原生页（原生页再给出网页/外部出口）；或
     * 2. 认定"入口直连外部"是对的，于是**删掉** `CourseSelectScreen` 与这条路由。
     *
     * 在定下来之前它被显式列在这里 —— 这样守卫**仍然拦得住将来新加的、忘了接线的子页**，
     * 而不会因为这一条老账整体失效。配套的 `白名单不得过期` 测试会在它被接上线时提醒删掉本项。
     *
     * ---
     *
     * **2026-10 已清空**：需求方选择"让「选课」入口进原生页"，
     * `ServiceEntry("选课")` 的 onClick 已改为 `subPage = ServiceSubPage.CourseSelect`，
     * 外部/网页出口由该页自己的 `onOpenWeb` 提供（外部路径没有丢失）。
     * 配套的 `白名单不得过期` 测试立刻变红，逼着把这条豁免删掉 —— 机制按设计工作。
     *
     * ⚠️ **当前为空**。将来若又发现"写好了但没入口"的子页，**先当缺陷处理**
     *（要么接线、要么删掉那个页），只有在确实需要挂账时才加到这里。
     */
    private val knownUnreachable = emptySet<String>()

    /** 从某个起始标记截到结束标记（不含后者）的代码片段。 */
    private fun slice(startMarker: String, endMarker: String): String {
        val start = screen.indexOf(startMarker)
        assertTrue("源码里找不到标记：$startMarker", start >= 0)
        val end = screen.indexOf(endMarker, start)
        assertTrue("源码里找不到结束标记：$endMarker", end > start)
        return screen.substring(start, end)
    }

    @Test
    fun `subPageVisible 必须派生自 subPage，而不是枚举式汇总`() {
        assertTrue(
            "`subPageVisible` 必须写成 `subPage != null`。若这里红了，说明有人把" +
                "「24 项 || 链」的写法加回来了 —— 那正是返回键直接退出 App 的成因。",
            screen.contains("val subPageVisible = subPage != null"),
        )
    }

    @Test
    fun `24 个布尔标志不得回归`() {
        val revived = Regex("""var\s+(show\w+)\s+by\s+remember""")
            .findAll(screen)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            "源码里又出现了布尔子页标志；子页状态必须走 `subPage: ServiceSubPage?`：" +
                revived.joinToString("、"),
            emptyList<String>(),
            revived,
        )
    }

    @Test
    fun `BackHandler 复位体是单一的 subPage = null`() {
        // 截到第一个子页分发分支为止（`if (subPage == ` 是分发区的开头）。
        val block = slice("BackHandler(enabled = subPageVisible)", "if (subPage == ")
        assertEquals(
            "复位的写法必须与声明/汇总同源：除了 `subPage = null` 之外不该有别的东西：" +
                block.trim(),
            1,
            Regex("""subPage\s*=\s*null""").findAll(block).count(),
        )
    }

    @Test
    fun `每个 ServiceSubPage 条目都被接线（置位 + 比较各至少一次）`() {
        val entries = declaredEntries()
        assertTrue(
            "没解析到任何 ServiceSubPage 条目，正则可能已失效（路由类型被改写了？）",
            entries.isNotEmpty(),
        )

        val noSetter = entries
            .filterNot { screen.contains("subPage = ServiceSubPage.$it") }
            .filterNot { it in knownUnreachable }
        val noCompare = entries.filterNot { screen.contains("subPage == ServiceSubPage.$it") }

        assertEquals(
            "这些子页目的地**没有任何地方会进得去**（缺 `subPage = ServiceSubPage.X`）：" +
                noSetter.joinToString("、"),
            emptyList<String>(),
            noSetter,
        )
        assertEquals(
            "这些子页目的地**没有对应的渲染分支**（缺 `subPage == ServiceSubPage.X`）：" +
                noCompare.joinToString("、") + " —— 用户进去了会是一片空白",
            emptyList<String>(),
            noCompare,
        )
    }

    @Test
    fun `白名单不得过期`() {
        // 反向检查：白名单里那一条一旦被接上线，本测试就红 —— 逼着把豁免删掉，
        // 否则白名单会悄悄变成"永久免罪符"。
        val stale = knownUnreachable.filter { screen.contains("subPage = ServiceSubPage.$it") }
        assertEquals(
            "这些目的地已经能被访问了，请把它们从 `knownUnreachable` 白名单里删掉：" +
                stale.joinToString("、"),
            emptyList<String>(),
            stale,
        )
    }

    /**
     * 2026-10 用户反馈：从服务页点进任意二级页再返回，列表回到了顶部。
     *
     * 成因是结构性的：所有二级页都用 `return` 独占整屏，列表会**整体离开组合**。
     * `rememberScrollState()` 若写在 `if (asTab)` 分支里，会跟分支一起被销毁 ——
     * 返回时重新从 0 开始。状态必须声明在第一个子页分发分支**之前**，两个列表分支共用。
     */
    @Test
    fun `列表滚动状态必须声明在子页分发之前`() {
        val declared = screen.indexOf("val servicesScrollState = rememberScrollState()")
        assertTrue(
            "找不到 `val servicesScrollState = rememberScrollState()`：列表滚动状态必须由它提供",
            declared >= 0,
        )
        val firstDispatch = screen.indexOf("if (subPage == ServiceSubPage.")
        assertTrue("源码里找不到子页分发分支", firstDispatch >= 0)
        assertTrue(
            "`servicesScrollState` 必须声明在第一个 `if (subPage == …)` 之前：" +
                "二级页 `return` 时它会被一起销毁，返回后滚动位置归零。",
            declared < firstDispatch,
        )
        assertEquals(
            "两个列表分支（底栏 tab / 百宝箱）都要把同一个状态交给 `verticalScroll`：" +
                "任一处漏掉，那个入口返回后仍会跳回顶部。",
            2,
            Regex("""verticalScroll\(servicesScrollState\)""").findAll(screen).count(),
        )
    }

    @Test
    fun `路由类型里的条目数量与声明处一致`() {
        // 一条廉价的防呆：条目数不能是 0，也不能突然掉到个位数（误删整段）。
        val n = declaredEntries().size
        assertTrue("ServiceSubPage 条目只有 $n 个，疑似被误删", n >= 20)
    }
}
