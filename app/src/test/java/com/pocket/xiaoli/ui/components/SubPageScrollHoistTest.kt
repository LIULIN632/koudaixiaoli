package com.pocket.xiaoli.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「进二级页再返回，列表跳回顶部」这一类 bug 的守卫（源码静态断言）。
 *
 * ## 成因
 *
 * 这些宿主页都用 `if (子页) { 子页(); return }` 的方式整屏切换。子页期间主列表
 * **离开组合**，写在主列表里的 `rememberScrollState()` / `rememberLazyListState()`
 * 会跟着被销毁；返回时重新创建 ⇒ 位置归零（2026-10 服务页真机反馈）。
 *
 * 解法一律是：把状态**提到所有整页 `return` 之前**，让它在子页期间一直活着
 *（能跨整页销毁的页面另用 `rememberCampusScrollState`）。
 *
 * ## 为什么用源码扫描
 *
 * 滚动位置是 Composable 内的局部状态，JVM 单测拿不到；快照测试也拍不到"返回之后
 * 停在哪"。唯一能钉住的就是"状态声明在分流的哪一侧"这个结构事实。
 */
class SubPageScrollHoistTest {

    private data class Host(
        val relPath: String,
        val declaration: String,
        val firstFullPageBranch: String,
        /** 消费该状态的调用（每个都必须出现）。 */
        val consumers: List<String>,
    )

    private val hosts = listOf(
        Host(
            relPath = "treasurebox/TreasureBoxScreen.kt",
            declaration = "val servicesScrollState = rememberScrollState()",
            firstFullPageBranch = "    if (subPage == ServiceSubPage.",
            consumers = listOf("verticalScroll(servicesScrollState)"),
        ),
        Host(
            relPath = "shower/ShowerScreen.kt",
            declaration = "val showerScrollState = rememberScrollState()",
            firstFullPageBranch = "    if (showScanner) {",
            consumers = listOf("verticalScroll(showerScrollState)"),
        ),
        Host(
            relPath = "seat/LibrarySeatScreen.kt",
            declaration = "val pageScrollState = rememberScrollState()",
            firstFullPageBranch = "    if (showLogin) {",
            consumers = listOf("verticalScroll(pageScrollState)"),
        ),
        Host(
            relPath = "timetable/TimetableScreen.kt",
            declaration = "val gridScrollState = rememberScrollState()",
            firstFullPageBranch = "    if (showSemesterSettings) {",
            consumers = listOf("scrollState = gridScrollState"),
        ),
        Host(
            relPath = "timetable/TimetableScreen.kt",
            declaration = "val agendaListState = rememberLazyListState()",
            firstFullPageBranch = "    if (showSemesterSettings) {",
            consumers = listOf("state = agendaListState"),
        ),
        Host(
            relPath = "dev/DeveloperLabScreen.kt",
            declaration = "val labScrollState = rememberScrollState()",
            firstFullPageBranch = "    if (showDataViewer) {",
            consumers = listOf("verticalScroll(labScrollState)"),
        ),
        Host(
            relPath = "profile/SettingsSubPages.kt",
            declaration = "val pageScrollState = remember(page) { ScrollState(0) }",
            firstFullPageBranch = "    if (agreementOpen) {",
            consumers = listOf("scrollState = pageScrollState"),
        ),
        Host(
            relPath = "profile/ProfileSettingsScreen.kt",
            declaration = "val homeScrollState = rememberCampusScrollState(\"profile_settings\")",
            // 设置首页会进二级页（本函数内 return）也可能被外层整页替换，所以用带记忆的实现
            firstFullPageBranch = "    if (activeSubPage != null) {",
            consumers = listOf("verticalScroll(homeScrollState)"),
        ),
    )

    private fun source(relPath: String): String {
        val candidates = listOf(
            File("src/main/java/com/pocket/xiaoli/ui/$relPath"),
            File("app/src/main/java/com/pocket/xiaoli/ui/$relPath"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("找不到 $relPath，候选：${candidates.map { it.absolutePath }}")
        return file.readText()
    }

    @Test
    fun `滚动状态声明在所有整页分流之前`() {
        hosts.forEach { host ->
            val src = source(host.relPath)
            val declared = src.indexOf(host.declaration)
            assertTrue("${host.relPath} 里找不到 `${host.declaration}`", declared >= 0)

            val branch = src.indexOf(host.firstFullPageBranch)
            assertTrue("${host.relPath} 里找不到分流标记 `${host.firstFullPageBranch}`", branch >= 0)

            assertTrue(
                "${host.relPath}：滚动状态必须声明在 `${host.firstFullPageBranch}` 之前 —— " +
                    "整页 `return` 时它会被一起销毁，返回后位置归零（2026-10 真机反馈）。",
                declared < branch,
            )

            host.consumers.forEach { call ->
                assertTrue(
                    "${host.relPath}：声明了状态却没交给滚动容器（找不到 `$call`），修复等于没做。",
                    src.contains(call),
                )
            }
        }
    }
}
