package com.pocket.xiaoli.ui.assistant

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 鲤鱼页的 LazyColumn key 不能重复。
 *
 * 2026-11-07 线上崩溃（v0.2.24，vivo 2307A / Android 16）：
 * ```
 * java.lang.IllegalArgumentException: Key "not-configured" was already used.
 * ```
 * 原因是「未配置模型」那张卡片被插了两处 —— 空态分支一次、文末一次。
 * `messages.isEmpty()` 时两个 item 都进列表，key 撞车，组合期直接抛。
 *
 * 为什么单测抓不到：`AssistantScreen` 需要 `AppContainer`（真数据库 + 真 prefs + 真
 * ViewModel），没有整页快照；而 LazyColumn 的 key 冲突只在**真的组合**时才检查。
 * 所以这里退一步守源码：同一个 key 只许出现一次。读文件、数出现次数，
 * 比 mock 一整个容器便宜也更直接。
 */
class AssistantLazyColumnKeyTest {

    private val source: String by lazy {
        // 测试的工作目录是 app/，源码在 src/main/…
        File("src/main/java/com/pocket/xiaoli/ui/assistant/AssistantScreen.kt").readText()
    }

    @Test
    fun `not-configured 这个 key 只出现一次`() {
        val count = Regex("""item\(\s*key\s*=\s*"not-configured"""").findAll(source).count()
        assertEquals("LazyColumn 里 key 撞了会在组合期崩溃（0.2.24 线上事故），只许插一次", 1, count)
    }

    @Test
    fun `未配置卡片只在文末插入`() {
        // 空态分支（messages.isEmpty）里不该再出现这张卡片
        val emptyBranch = source.substringAfter("if (state.messages.isEmpty()) {")
            .substringBefore("itemsIndexed(")
        assertTrue(
            "空态分支里不要再插 NotConfiguredCard：它已经在文末统一插过一次了",
            !emptyBranch.contains("NotConfiguredCard"),
        )
    }
}
