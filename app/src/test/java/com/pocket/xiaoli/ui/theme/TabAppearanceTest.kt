package com.pocket.xiaoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「各 tab 外观设置」的**档位 → 数值**换算，以及**默认档**。
 *
 * ## 为什么必须有这个测试（不是为了覆盖率）
 *
 * 这两个字段在 2026-10 之前是**声明了没有消费者的死字段**：
 * 「外观 → 我的 → 列表密度」与「外观 → 鲤鱼 → 回答字号」在 UI 上能选、能存、
 * hub 上还会显示"标准/宽松""字号小/大"，但**改了不生效** ——
 * 用户看到的就是"怎么调都还是那个大小"。
 *
 * 根因是 `AppearanceGuide.md` 专门警告过的"声明了没接线"：
 * **加外观字段必须同时确认消费者**。现在消费者是：
 *
 * | 字段 | 消费者 |
 * |---|---|
 * | [TabAppearance.profileRowVerticalPadding] | `LocalProfileRowPadding` → `ChecklistRow` / `DangerRow` / `SettingsMenuItem` / `SettingsGroupCard` / `AppearanceCategoryScreen` 的行 |
 * | [TabAppearance.assistantFontMultiplier] | `AssistantScreen`（把消息轮包进 `Density(fontScale = 系统 × 本值)`） |
 *
 * 而"有没有消费者"没法在单测里断言，所以这里守的是**最容易被改坏的那一半**：
 * 档位换算与默认值。谁把默认档改回"小"（0.9×）就会红 ——
 * 那会让**所有没碰过设置的用户**的回答字号凭空缩 10%。
 */
class TabAppearanceTest {

    @Test
    fun `我的列表密度三档的垂直内边距`() {
        assertEquals(10, TabAppearance(profileDensity = 0).profileRowVerticalPadding)
        assertEquals(14, TabAppearance(profileDensity = 1).profileRowVerticalPadding)
        assertEquals(18, TabAppearance(profileDensity = 2).profileRowVerticalPadding)
    }

    @Test
    fun `回答字号三档的倍率`() {
        assertEquals(0.9f, TabAppearance(assistantFontScale = 0).assistantFontMultiplier, 0.0001f)
        assertEquals(1.0f, TabAppearance(assistantFontScale = 1).assistantFontMultiplier, 0.0001f)
        assertEquals(1.15f, TabAppearance(assistantFontScale = 2).assistantFontMultiplier, 0.0001f)
    }

    /**
     * ⚠️ **默认档必须是「标准」**。
     *
     * `answerFontScale` 原先默认 `0`（= 小，0.9×）。在它没有消费者的时候这看不出问题；
     * 一旦按上倍率，默认 0 就等于**给所有没碰过设置的用户把回答字号缩掉 10%** ——
     * 既违反本文件 KDoc 写的"默认值取现状、老用户零感知"，也正是用户反馈的那件事。
     */
    @Test
    fun `默认档必须是标准而不是小`() {
        val defaults = TabAppearance()
        assertEquals("默认回答字号必须是标准档", 1, defaults.assistantFontScale)
        assertEquals("默认列表密度必须是标准档", 1, defaults.profileDensity)
        assertEquals(1.0f, defaults.assistantFontMultiplier, 0.0001f)
        assertEquals(14, defaults.profileRowVerticalPadding)
    }

    /**
     * 档位只有 0/1/2 三档（UI 是 ChoiceRow，不会给出别的值），
     * 但 prefs 里的旧值/手改值可能是任意 int —— 越界应该**回落到标准**，
     * 而不是算出一个没人选过的尺寸。
     */
    @Test
    fun `越界档位回落到标准`() {
        assertEquals(14, TabAppearance(profileDensity = 7).profileRowVerticalPadding)
        assertEquals(1.0f, TabAppearance(assistantFontScale = -3).assistantFontMultiplier, 0.0001f)
    }
}
