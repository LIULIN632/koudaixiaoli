package com.pocket.xiaoli.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "减少动画"（Reduce Motion）闸门的契约。
 *
 * ## 为什么需要这份测试
 *
 * 2026-10 之前，全 App 对系统"移除动画"设置**零处理**（`rg` 实测 0 命中）。
 * 后果不是"动效不好看"，而是三类**用户关不掉**的持续运动：
 *
 * | 位置 | 形态 | 为什么系统设置管不到 |
 * |---|---|---|
 * | `PromoBanner` 自动轮播 | `while(true){ delay(4000) }` | 是**协程**不是动画框架，4 秒一转、无开关 |
 * | `AiAvatar` 脉冲 | `rememberInfiniteTransition` | 无限循环，停不下来 |
 * | `MascotAvatar` 眨眼 | `rememberInfiniteTransition` | 每 ~3.2 秒一次，永续 |
 * | `ChatTurn` 呼吸点 | `rememberInfiniteTransition` | 同上 |
 *
 * 这类改动**不会编译报错、也不会让别的测试变红**（默认缩放是 1，行为不变），
 * 所以必须有断言把它钉住 —— 否则下一个人加动画时又会绕过闸门。
 *
 * ## 测试怎么驱动这个开关
 *
 * 闸门读的是 `Settings.Global.ANIMATOR_DURATION_SCALE`（见 `Motion.reduceMotion` 的
 * KDoc 说明为什么不用 Compose 的 CompositionLocal：这个 Compose 版本里没有它）。
 * 测试**直接写那个系统设置**再断言函数结果 —— 不是在测试里重算一遍公式，
 * 而是真的让被测代码读到不同的输入。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ReduceMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 读取被测函数在当前系统设置下的三个结果。
     *
     * 不需要 `CompositionLocalProvider`：`setContent` 在 Robolectric 下本来就用
     * application context 提供 `LocalContext`，而闸门读的正是它的 `contentResolver`。
     */
    private fun probe(): Triple<Boolean, Int, Int> {
        var reduced = false
        var normalDuration = -1
        var infiniteDuration = -1
        composeRule.setContent {
            reduced = reduceMotion()
            normalDuration = motionAwareDuration(Motion.breath)
            infiniteDuration = motionAwareInfiniteDuration(Motion.breath)
        }
        composeRule.waitForIdle()
        return Triple(reduced, normalDuration, infiniteDuration)
    }

    private val resolver get() =
        ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun restoreDefault() {
        // ⚠️ 必须还原：这是**进程级**系统设置，漏还原会污染同一 JVM 里的后续测试
        //（尤其是快照测试 —— 动画时长变 0 会让它们抓到的帧不一样）。
        setAnimatorScale(1f)
    }

    @Test
    fun animatorScaleOneKeepsDurationsUnchanged() {
        setAnimatorScale(1f)
        val (reduced, normal, infinite) = probe()
        assertFalse("缩放为 1 时不该判定为'减少动画'", reduced)
        assertEquals("默认情况下时长必须原样传递", Motion.breath, normal)
        assertEquals("默认情况下无限动画时长也必须原样传递", Motion.breath, infinite)
    }

    @Test
    fun animatorScaleZeroZeroesEveryGatedDuration() {
        setAnimatorScale(0f)
        val (reduced, normal, infinite) = probe()
        assertTrue("缩放为 0（系统要求移除动画）时必须判定为'减少动画'", reduced)
        assertEquals("减少动画时时长必须归零（即时完成）", 0, normal)
        assertEquals("减少动画时无限循环动画也必须归零（停在初始帧）", 0, infinite)
    }

    /**
     * 半档缩放（0.5）**不算**减少动画：系统只是"变快"，没有要求"别动"。
     *
     * 这条防止有人把判据写成 `scaleFactor < 1f` —— 那会让"动画调快"的用户
     * 意外失去所有动效。
     */
    @Test
    fun halfScaleIsNotTreatedAsReduceMotion() {
        setAnimatorScale(0.5f)
        val (reduced, normal, _) = probe()
        assertFalse("0.5 缩放只是变快，不该被当成'减少动画'", reduced)
        assertEquals(Motion.breath, normal)
    }
}
