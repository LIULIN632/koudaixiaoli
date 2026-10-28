package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [relativeSinceLabel] 的口径。
 *
 * 这套分档原先只长在修课快照里（`AcademicSnapshotCodecTest` 钉着「刚更新 / 30 分钟前更新 /
 * 5 小时前更新 / 3 天前更新」四句），抽成公共实现之后那边照旧绿；这里补的是**抽出来才成立**的两件事：
 * 动词是可换的（同一档位要说「查过」），以及超过三十天改报日期。
 */
class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `换动词就是换那句话`() {
        assertEquals("刚查过", relativeSinceLabel(now - 30_000, "查过", now))
        assertEquals("5 分钟前查过", relativeSinceLabel(now - 5 * 60_000, "查过", now))
        assertEquals("3 天前查过", relativeSinceLabel(now - 3 * 86_400_000, "查过", now))
    }

    @Test
    fun `超过三十天不再报天数`() {
        // "45 天前"读不出是哪一回，"2026-09-12"能直接和别的事对上
        val label = relativeSinceLabel(now - 31L * 86_400_000, "查过", now)

        assertFalse("该改口报日期了：$label", label.contains("天前"))
        assertTrue("日期后面仍要带上动作：$label", label.endsWith(" 查过"))
        assertTrue("形如 2023-10-14 查过：$label", label.matches(Regex("""\d{4}-\d{2}-\d{2} 查过""")))
    }

    @Test
    fun `没有时间戳时一个字也不说`() {
        // 说"刚查过"就是凭空造一个结论；老用户第一次进来该看到的是空白
        assertEquals("", relativeSinceLabel(0L, "查过", now))
        assertEquals("", relativeSinceLabel(-1L, "查过", now))
    }
}
