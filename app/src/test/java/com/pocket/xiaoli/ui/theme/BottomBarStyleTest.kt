package com.pocket.xiaoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏样式的**默认值与降级口径**。
 *
 * 为什么必须测：这条规则决定两件用户直接看得见、但在真机上极难复现的事 ——
 * 1. **默认给谁**：新用户第一次打开看到的是玻璃还是胶囊；
 * 2. **老系统上说什么**：`tierNote()` 是设置页唯一的说明来源，它写错就等于对用户撒谎。
 *
 * 2026-09-27 的**口径变更**（需求方决定）：
 * 原先「液态玻璃」在 Android 13 以下会被**禁用并回落成另一种组件**（悬浮胶囊），
 * 现在改为**同一个组件三档降级**（折射 / 模糊 / 磨砂），因为"我选的样式变成了别的东西"
 * 比"效果弱一点"更像 bug。所以 `isSupported` 这个成员被删掉了 ——
 * 它承载的"选不了"语义已经不成立，留着会让人误以为玻璃在老设备上不可用。
 */
class BottomBarStyleTest {

    @Test
    fun `未知或空 key 回落到默认样式`() {
        assertEquals(BottomBarStyle.default, BottomBarStyle.fromKey(null))
        assertEquals(BottomBarStyle.default, BottomBarStyle.fromKey(""))
        assertEquals(BottomBarStyle.default, BottomBarStyle.fromKey("liquid_glass_v2"))
        assertEquals(BottomBarStyle.default, BottomBarStyle.fromKey("GLASS"))
    }

    @Test
    fun `key 能往返`() {
        BottomBarStyle.entries.forEach { style ->
            assertEquals("key「${style.key}」应能解回自身", style, BottomBarStyle.fromKey(style.key))
        }
    }

    @Test
    fun `key 互不重复`() {
        val keys = BottomBarStyle.entries.map { it.key }
        assertEquals("key 重复会让存储值指向错误的样式", keys.size, keys.toSet().size)
    }

    @Test
    fun `两种样式在任何设备上都能选`() {
        // 玻璃不再"选不了"：它按系统能力三档降级，而不是被禁用。
        // 所以这个枚举里不该再有任何"当前设备不可用"的成员。
        BottomBarStyle.entries.forEach { style ->
            assertFalse("「${style.label}」应当是任何设备都能选的", style.key.isBlank())
        }
        assertEquals("只有两种样式，删成员时要同步这段断言", 2, BottomBarStyle.entries.size)
    }

    @Test
    fun `只有玻璃有档位说明 胶囊是空的`() {
        // 胶囊不依赖系统能力，副标题里不该出现任何"回落到…"的字样
        assertEquals("", BottomBarStyle.SOFT.tierNote())
        assertTrue(
            "玻璃必须说明本机会看到哪一档",
            BottomBarStyle.LIQUID_GLASS.tierNote().isNotBlank()
        )
    }

    @Test
    fun `玻璃的档位说明不会承诺折射给不支持的设备`() {
        // JVM 单测里 Build.VERSION.SDK_INT 恒为 0 → 必定是最低档。
        // 这条守的是：低档位文案里**不能出现"折射"**，否则就是对老系统的用户撒谎。
        val note = BottomBarStyle.LIQUID_GLASS.tierNote()
        assertFalse(
            "最低档的说明不该提折射，当前是「$note」",
            note.contains("折射")
        )
        assertTrue("最低档应说明只显示磨砂", note.contains("磨砂"))
    }

    @Test
    fun `玻璃的 key 与标签是稳定契约`() {
        // key 会被写进 SharedPreferences：改它等于让所有老用户的设置失效（回落到默认），
        // 所以钉住它，改动时必须是有意识的决定
        assertEquals("liquid_glass", BottomBarStyle.LIQUID_GLASS.key)
        assertEquals("soft", BottomBarStyle.SOFT.key)
        assertEquals("液态玻璃", BottomBarStyle.LIQUID_GLASS.label)
        assertEquals("悬浮胶囊", BottomBarStyle.SOFT.label)
    }

    @Test
    fun `默认样式是液态玻璃`() {
        // 需求方 2026-09-27 决定：默认开玻璃。
        // 理由：Android 13+ 开箱即见完整效果（这是目前最"现代"的一块），
        // 低版本看到的是同形状的磨砂版，观感仍然成立。
        assertEquals(BottomBarStyle.LIQUID_GLASS, BottomBarStyle.default)
        assertEquals(
            "设置页按 entries 顺序展示，默认项应排在最前",
            BottomBarStyle.LIQUID_GLASS,
            BottomBarStyle.entries.first()
        )
    }

    @Test
    fun `每个样式都有非空的说明文案`() {
        BottomBarStyle.entries.forEach { style ->
            assertFalse("「${style.label}」缺少 description，设置页会显示空行", style.description.isBlank())
        }
    }
}
