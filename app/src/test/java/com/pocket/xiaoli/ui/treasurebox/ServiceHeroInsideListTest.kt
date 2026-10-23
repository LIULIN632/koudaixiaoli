package com.pocket.xiaoli.ui.treasurebox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守门人：**汇总卡（hero）必须活在列表里，不能钉在列表外面。**
 *
 * ## 为什么
 * 2026-10-22 用户报「点进服务后，往下滑整个卡片始终悬浮在内容上，有点遮挡内容」。
 * 根因不是悬浮，而是这些页面写成：
 *
 * ```
 * Column {
 *     ServiceHeroCard(...)        // 固定在外层，永远不滚
 *     ServiceSectionLabel(...)    // 也是固定的
 *     LazyColumn(weight(1f) / fillMaxSize()) { ... }   // 只有它滚
 * }
 * ```
 *
 * 于是往下滑时大卡一直挂在顶上，列表的第一张卡从它下沿"滑出来"被切掉一截 ——
 * 看起来就像卡片浮在内容上把内容盖住了。截图基线只在滚动位置 0 拍，
 * **看不见这个问题**，所以只能靠这条源码级断言兜住。
 *
 * ## 判据
 * 1. 文件里出现 `ServiceHeroCard(` ⇒ 必须同时有 `item(key = "hero")`（= hero 是列表项）；
 * 2. 文件里不能出现 `LazyColumn(` 后紧跟 `Modifier.fillMaxSize()` 的写法 ——
 *    那是「外层固定头 + 内嵌满屏列表」的指纹，正是这次要根除的形态。
 *    （内嵌列表只允许 `Modifier.weight(1f)`，且兄弟节点里不能再有 hero。）
 */
class ServiceHeroInsideListTest {

    /** 用 hero 卡做「主视觉」的服务页。新增同类页面时请补进来。 */
    private val heroPages = listOf(
        "app/src/main/java/com/pocket/xiaoli/ui/treasurebox/TreeHoleScreen.kt",
        "app/src/main/java/com/pocket/xiaoli/ui/treasurebox/TaskChecklistScreen.kt",
        "app/src/main/java/com/pocket/xiaoli/ui/treasurebox/GoodItemListScreen.kt",
        "app/src/main/java/com/pocket/xiaoli/ui/treasurebox/FoodPickerPage.kt",
        "app/src/main/java/com/pocket/xiaoli/ui/treasurebox/LearningResourcePage.kt",
        "app/src/main/java/com/pocket/xiaoli/ui/treasurebox/TeacherLookupScreen.kt",
        "app/src/main/java/com/pocket/xiaoli/ui/treasurebox/GradeQueryScreen.kt",
    )

    /** 仓库根：Gradle 的单测工作目录是 `app/`，所以要往上试一级。 */
    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 $path（工作目录 ${File(".").absolutePath}）")
    }

    @Test
    fun `hero 卡必须是列表项`() {
        heroPages.forEach { path ->
            val src = repoFile(path).readText()
            assertTrue("$path 里应该有 ServiceHeroCard", src.contains("ServiceHeroCard("))
            assertTrue(
                "$path 把 ServiceHeroCard 放在了列表外面 —— 往下滑它会一直挂着，" +
                    "把列表第一张卡切掉一截。请挪进 LazyColumn 的 item(key = \"hero\") 里。",
                src.contains("item(key = \"hero\")"),
            )
        }
    }

    @Test
    fun `不允许外层固定头加内嵌满屏列表`() {
        heroPages.forEach { path ->
            val src = repoFile(path).readText()
            // 把换行折叠成空格，避免格式化差异导致漏检
            val flat = src.replace(Regex("\\s+"), " ")
            assertFalse(
                "$path 里出现了 `LazyColumn(modifier = Modifier.fillMaxSize()` —— " +
                    "这是「外层固定头 + 内嵌满屏列表」的形态，内容会被固定头顶住/切掉。" +
                    "整页只留一条滚动列表（外层用 weight(1f) 或干脆不套权重）。",
                flat.contains("LazyColumn( modifier = Modifier.fillMaxSize()"),
            )
        }
    }

    @Test
    fun `列表里的 hero 都有稳定 key`() {
        heroPages.forEach { path ->
            val src = repoFile(path).readText()
            val heroItems = Regex("item\\(key = \"hero\"\\)").findAll(src).count()
            assertTrue("$path 的 hero 列表项应该恰好一处，实际 $heroItems", heroItems >= 1)
        }
    }
}
