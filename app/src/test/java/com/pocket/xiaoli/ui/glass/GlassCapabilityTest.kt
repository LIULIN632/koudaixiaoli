package com.pocket.xiaoli.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 液态玻璃的**三档能力分档**。
 *
 * 为什么要单测：这三档在真机上要凑齐 Android 10 / 12 / 13 三台设备才能各看一眼，
 * 而"档位判错"的表现是**静默降级**（用户在 Android 12 上看到 Android 13 的承诺，
 * 或者反过来，明明能做模糊却贴了一张半透明纸），不会报错、不会崩，只有用户觉得"不好看"。
 *
 * 所以把边界钉在纯函数上：`of(sdkInt)` 显式收参，测的是**分档规则**而不是"和 0 比较的结果"。
 */
class GlassCapabilityTest {

    @Test
    fun `API 33 及以上是折射档`() {
        assertEquals(GlassCapability.REFRACTION, GlassCapability.of(33))
        assertEquals(GlassCapability.REFRACTION, GlassCapability.of(34))
        assertEquals(GlassCapability.REFRACTION, GlassCapability.of(99))
    }

    @Test
    fun `API 31 与 32 是模糊档`() {
        assertEquals(GlassCapability.BLUR, GlassCapability.of(31))
        assertEquals(GlassCapability.BLUR, GlassCapability.of(32))
    }

    @Test
    fun `API 29 与 30 是磨砂档`() {
        // 本项目 minSdk = 29，所以这两档会真实发生在用户手上，不是理论分支
        assertEquals(GlassCapability.MATTE, GlassCapability.of(29))
        assertEquals(GlassCapability.MATTE, GlassCapability.of(30))
    }

    @Test
    fun `低于 minSdk 的取值也不崩 一律当磨砂`() {
        // 防御性：单测环境 Build.VERSION.SDK_INT 恒为 0，真机理论上不会更低，
        // 但分档函数不该因为一个意料之外的数值抛异常
        assertEquals(GlassCapability.MATTE, GlassCapability.of(0))
        assertEquals(GlassCapability.MATTE, GlassCapability.of(28))
        assertEquals(GlassCapability.MATTE, GlassCapability.of(-1))
    }

    @Test
    fun `档位随系统版本单调不倒退`() {
        // 「升级系统反而少一种能力」说明阈值写反了，这条能直接抓到
        val order = listOf(GlassCapability.MATTE, GlassCapability.BLUR, GlassCapability.REFRACTION)
        var lastRank = -1
        (28..35).forEach { sdk ->
            val rank = order.indexOf(GlassCapability.of(sdk))
            assertTrue(
                "SDK $sdk 的能力档($rank)比更低的版本还弱，阈值写反了",
                rank >= lastRank
            )
            lastRank = rank
        }
    }

    @Test
    fun `能力谓词与档位一致`() {
        assertTrue(GlassCapability.REFRACTION.canRefract)
        assertTrue(GlassCapability.REFRACTION.canBlur)

        assertFalse("Android 12 做不了折射，UI 不许承诺", GlassCapability.BLUR.canRefract)
        assertTrue(GlassCapability.BLUR.canBlur)

        assertFalse(GlassCapability.MATTE.canRefract)
        assertFalse("Android 10-11 连模糊都没有，只能半透明", GlassCapability.MATTE.canBlur)
    }

    @Test
    fun `折射阈值必须与底栏设置项的声明一致`() {
        // BottomBarStyle.LIQUID_GLASS.tierNote() 的分支直接读这两个谓词。
        // 两处一旦分叉，表现是"设置页说本机有折射、底栏却看不清"。
        assertTrue("33 必须能折射，否则设置页的承诺是假的", GlassCapability.of(33).canRefract)
        assertFalse("32 不能折射，设置页必须如实说明", GlassCapability.of(32).canRefract)
    }
}
