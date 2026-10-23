package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **把 App 的 typography 表整张钉住**（15 个角色 × 字号 / 行高 / 字重）。
 *
 * ## 它守的是什么：一个曾经"谁都没选过"的字号
 *
 * 改造前 `AppTypography` 只显式声明 **5** 个角色，另外 10 个**静默继承 Material3 出厂值** ——
 * 从源码上看不出它们是几号字。后果不是"少写几行"，而是：
 *
 * > 任意 `Text(...)` **既不传 `style` 也不传 `fontSize`** 时读 `LocalTextStyle` → `bodyLarge`
 * > → **16sp**，而 16sp 既不在 `PocketTypography` 的 7 档里、也不在那 5 个覆盖里。
 *
 * 这个幽灵字号在 1/3 的界面里**承重**：实测只把 `bodyLarge` 改成 14sp，
 * **70 张基线里 22 张失败**。所以它必须先"看得见"，才谈得上"改得动"。
 *
 * ## 现在这张表是**自足**的
 *
 * 15 个角色全部硬编码在本文件里 → **不再受 Compose 升级带来的出厂值漂移影响**。
 * 本测试因此断言的是 **AppTypography 自己的值**，而不是 M3 的默认值。
 *
 * ## 为什么连 `letterSpacing` 一起钉
 *
 * 那 5 个 app 覆盖项**刻意没有设 `letterSpacing`**（= `Unspecified`，实际按 0 渲染），
 * 这与 M3 出厂值不同（如 `bodyMedium` 出厂是 `0.2sp`）。
 * **给它补上就会改像素** —— 那是 Phase C 的决定，不该被谁顺手做掉。
 * 所以那条断言写的是 `isUnspecified`，它会拦住"顺手补齐"。
 *
 * ⚠️ 本测试**红了不等于代码坏了**：它是"改字号必须先想清楚并同步更新这里"的闸门，
 * 与 `ThemeContrastTest` 的 ratchet 同一思路。
 */
class AppTypographyTest {

    private val t = AppTypography

    @Test
    fun `本 App 自己的 5 个覆盖项`() {
        assertEquals(32f, t.displayLarge.fontSize.value)
        assertEquals(40f, t.displayLarge.lineHeight.value)
        assertEquals(FontWeight.Bold, t.displayLarge.fontWeight)

        assertEquals(20f, t.headlineMedium.fontSize.value)
        assertEquals(28f, t.headlineMedium.lineHeight.value)
        assertEquals(FontWeight.SemiBold, t.headlineMedium.fontWeight)

        assertEquals(16f, t.titleMedium.fontSize.value)
        assertEquals(24f, t.titleMedium.lineHeight.value)
        assertEquals(FontWeight.Medium, t.titleMedium.fontWeight)

        assertEquals(14f, t.bodyMedium.fontSize.value)
        assertEquals(20f, t.bodyMedium.lineHeight.value)
        assertEquals(FontWeight.Normal, t.bodyMedium.fontWeight)

        assertEquals(12f, t.labelMedium.fontSize.value)
        assertEquals(16f, t.labelMedium.lineHeight.value)
        assertEquals(FontWeight.Medium, t.labelMedium.fontWeight)
    }

    @Test
    fun `5 个覆盖项刻意不设 letterSpacing——补上就会改像素`() {
        listOf(
            "displayLarge" to t.displayLarge,
            "headlineMedium" to t.headlineMedium,
            "titleMedium" to t.titleMedium,
            "bodyMedium" to t.bodyMedium,
            "labelMedium" to t.labelMedium,
        ).forEach { (name, style) ->
            assertTrue(
                "$name 的 letterSpacing 不是 Unspecified —— 说明有人「顺手」给它补了值。" +
                    "M3 出厂值确实带 letterSpacing，但本 App 的这 5 项从来按 0 渲染，" +
                    "补上会让 70 张基线里的若干张变化。要做请当作一次 Phase C 的显式改动。",
                style.letterSpacing.type == TextUnitType.Unspecified,
            )
        }
    }

    /**
     * ⭐⭐ **全局继承默认文字样式**（2026-10 起正式接管）。
     *
     * 不传 `style` 的 `Text(...)` 落到它；更关键的是 —— **全 App 的 `Text` 都从它继承
     * 自己没设的 `lineHeight` / `letterSpacing`**（`Text` 内部走
     * `LocalTextStyle.current.merge(...)`，见 `Theme.kt` 里那段 KDoc 与隔离实验证据）。
     *
     * 所以这里钉的**不是"某一档正文"，而是整个 App 的文字层次基准**：
     * 一个 10sp 的小字也顶它的行盒。改它请当作一次 Phase C 级别的改动 ——
     * 实测会同时影响 70 张基线里的 22 张。
     */
    @Test
    fun `全局继承默认样式 bodyLarge 钉在 PocketTypography_body`() {
        assertEquals(14f, t.bodyLarge.fontSize.value)
        assertEquals(22f, t.bodyLarge.lineHeight.value)
        assertEquals(FontWeight.Normal, t.bodyLarge.fontWeight)
        assertEquals(PocketTypography.body.fontSize.value, t.bodyLarge.fontSize.value)
        assertEquals(PocketTypography.body.lineHeight.value, t.bodyLarge.lineHeight.value)
        assertTrue(
            "刻意不保留 M3 出厂的 0.5sp 字距 —— 字距是拉丁文字的特性，中文加它只是把字撑松",
            t.bodyLarge.letterSpacing.type == TextUnitType.Unspecified,
        )
    }

    @Test
    fun `另外 9 个 M3 角色的显式值`() {
        assertEquals(45f, t.displayMedium.fontSize.value)
        assertEquals(52f, t.displayMedium.lineHeight.value)
        assertEquals(36f, t.displaySmall.fontSize.value)
        assertEquals(44f, t.displaySmall.lineHeight.value)

        assertEquals(32f, t.headlineLarge.fontSize.value)
        assertEquals(40f, t.headlineLarge.lineHeight.value)
        assertEquals(24f, t.headlineSmall.fontSize.value)
        assertEquals(32f, t.headlineSmall.lineHeight.value)

        assertEquals(22f, t.titleLarge.fontSize.value)
        assertEquals(28f, t.titleLarge.lineHeight.value)
        assertEquals(14f, t.titleSmall.fontSize.value)
        assertEquals(20f, t.titleSmall.lineHeight.value)
        assertEquals(FontWeight.Medium, t.titleSmall.fontWeight)

        assertEquals(12f, t.bodySmall.fontSize.value)
        assertEquals(16f, t.bodySmall.lineHeight.value)

        assertEquals(14f, t.labelLarge.fontSize.value)
        assertEquals(20f, t.labelLarge.lineHeight.value)
        assertEquals(FontWeight.Medium, t.labelLarge.fontWeight)

        assertEquals(11f, t.labelSmall.fontSize.value)
        assertEquals(16f, t.labelSmall.lineHeight.value)
        assertEquals(FontWeight.Medium, t.labelSmall.fontWeight)
    }

    /**
     * ⭐ **本测试存在的根本理由**：表必须是自足的。
     *
     * 15 个角色一个都不能缺 —— 缺的那个就会去继承 M3 出厂值，幽灵字号就是这么来的。
     * 这条断言与 `Typography` 的构造参数一一对应，将来 M3 加第 16 个角色时它**不会**红
     *（那也确实不需要本文件负责），但**本文件的 15 个一个都不能少**。
     */
    @Test
    fun `15 个角色没有一个是"空值"`() {
        val roles = listOf(
            "displayLarge" to t.displayLarge, "displayMedium" to t.displayMedium,
            "displaySmall" to t.displaySmall, "headlineLarge" to t.headlineLarge,
            "headlineMedium" to t.headlineMedium, "headlineSmall" to t.headlineSmall,
            "titleLarge" to t.titleLarge, "titleMedium" to t.titleMedium,
            "titleSmall" to t.titleSmall, "bodyLarge" to t.bodyLarge,
            "bodyMedium" to t.bodyMedium, "bodySmall" to t.bodySmall,
            "labelLarge" to t.labelLarge, "labelMedium" to t.labelMedium,
            "labelSmall" to t.labelSmall,
        )
        assertEquals("M3 的 typography 角色是 15 个", 15, roles.size)
        roles.forEach { (name, style) ->
            assertTrue("$name 没有字号", style.fontSize.value > 0f)
            assertTrue("$name 没有行高", style.lineHeight.value > 0f)
        }
    }
}
