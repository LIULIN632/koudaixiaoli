package com.pocket.xiaoli.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 会话列表的时间显示。
 *
 * 为什么值得单测：这段逻辑有"划天"和"跨时区"两处容易写错的地方，
 * 而错了以后表现是"今天"标在昨天的时间上 —— 不会崩、没人报，只是让人不信任列表。
 * 所以固定 [now] 与固定东八区基准来断言。
 */
class ConversationTimeTest {

    /**
     * 固定的"现在"（UTC+8 口径的某一刻），与
     * `AssistantWelcomeAndListScreenshotTest.NOW` 是**同一个值**。
     *
     * ⚠️ 上游注释曾把这个数写成 `2026-09-26`，实际换算出来是 **2026-06-07 18:00 (UTC+8)**。
     * 本用例只断言"档位"（今天/昨天/前天/月日），不依赖具体是哪一天，所以那个错标注
     * 一直没造成失败；但**快照测试要的是逐像素一致**，标注错了会让人往错的方向查。
     * 这个值两个文件共用一份语义：凡是新增依赖它的用例，先算一遍真实日期再写注释。
     */
    private val now = 1780826400000L
    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun `今天之内显示今天`() {
        assertEquals("今天", formatTime(now, now))
        assertEquals("今天", formatTime(now - 60_000, now))
    }

    @Test
    fun `昨天与前天`() {
        assertEquals("昨天", formatTime(now - dayMs, now))
        assertEquals("前天", formatTime(now - 2 * dayMs, now))
    }

    @Test
    fun `更早显示月日`() {
        val result = formatTime(now - 5 * dayMs, now)

        // 不写死具体日期：跨时区跑测试时"哪一天"会变，但格式必须稳定
        assertEquals(true, result.endsWith("日"))
        assertEquals(true, result.contains("月"))
    }

    @Test
    fun `时间戳为零或负数时给兜底文案`() {
        assertEquals("刚刚", formatTime(0L, now))
        assertEquals("刚刚", formatTime(-1L, now))
    }

    @Test
    fun `跨过当天零点就算昨天 而不是按24小时算`() {
        // 今天 00:30 与昨天 23:30 相差只有 1 小时，但必须分别是"今天"和"昨天"
        val offset = 8L * 60 * 60 * 1000
        val startOfToday = now - (now + offset) % dayMs
        val todayEarly = startOfToday + 30 * 60 * 1000
        val yesterdayLate = startOfToday - 30 * 60 * 1000

        assertEquals("今天", formatTime(todayEarly, now))
        assertEquals("昨天", formatTime(yesterdayLate, now))
    }
}
