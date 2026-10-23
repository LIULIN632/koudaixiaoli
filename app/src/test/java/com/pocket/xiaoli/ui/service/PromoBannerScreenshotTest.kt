package com.pocket.xiaoli.ui.service

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.promo.Promo
import com.pocket.xiaoli.data.promo.PromoSlot
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 服务页顶部 banner 的**轮播**基线（2026-10 新增）。
 *
 * ## 它守的是什么
 *
 * 改造前 `PromoBanner` 只取 `priority` 最高的**一张**，其余全部不展示。
 * 现在 ≥2 条走横向分页 + 小圆点 + 自动播放。这条路径此前**零覆盖**。
 *
 * 两条基线各自守一件事：
 *
 * | 基线 | 守的 |
 * |---|---|
 * | `24-promo-carousel` | 轮播态本身（分页 + 圆点 + 「推广」角标）|
 * | `25-promo-single` | ⭐ **退化路径与改造前逐像素一致** —— 单条服务端不该看到圆点之类的多余元素 |
 *
 * ## ⚠️ 两个确定性前提（不满足则基线自己会红）
 *
 * 1. **必须传 `autoPlay = false`** —— 自动播放属于本项目《UI验收手册》点名的
 *    "无限动画"不确定性（同 `AiAvatar` 的脉冲、`MascotAvatar` 的眨眼）。
 * 2. **必须预置位图**：`PromoBanner` 在位图未到手时**整块不渲染**，而 Robolectric
 *    里没有网络 —— 不预置的话抓到的会是一张**空图**，等于没测。
 *    所以 `promoImageCache` 是 `internal` 的（见它的 KDoc），这里直接塞进去。
 *
 * 合成位图是**纯色块**：这两张基线验的是**分页 / 圆点 / 角标 / 比例**，
 * 不是海报画质（海报内容由服务端决定，本来就不该进基线）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class PromoBannerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 纯色合成位图（不下载、不依赖网络）。 */
    private fun stubBitmap(colorArgb: Long): Bitmap =
        Bitmap.createBitmap(480, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(colorArgb.toInt())
        }

    private fun promo(id: String, url: String, priority: Int) = Promo(
        id = id,
        slot = PromoSlot.SERVICE_HOME,
        title = "推广位 $id",
        body = "",
        imageUrl = url,
        action = null,
        startAt = null,
        endAt = null,
        priority = priority,
    )

    @Composable
    private fun Host(promos: List<Promo>) {
        PocketXiaoliTheme(
            appearanceMode = AppearanceMode.RAINBOW,
            skinStyle = SkinStyle.VIVID,
            darkTheme = false,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalPocketXiaoliColors.current.surfaceBase)
                    .padding(16.dp),
            ) {
                // ⚠️ autoPlay = false：自动播放会让基准图自己变红
                PromoBanner(promos = promos, autoPlay = false, onClick = {})
            }
        }
    }

    @Test
    fun `轮播三页截图`() {
        val urls = listOf(
            "https://example.invalid/promo-a.png",
            "https://example.invalid/promo-b.png",
            "https://example.invalid/promo-c.png",
        )
        // 预置位图：没有它 PromoBanner 整块不渲染
        promoImageCache.put(urls[0], stubBitmap(0xFF7B9FF9))
        promoImageCache.put(urls[1], stubBitmap(0xFF9ED8B8))
        promoImageCache.put(urls[2], stubBitmap(0xFFE8B4B8))

        composeRule.setContent {
            Host(urls.mapIndexed { i, u -> promo("p$i", u, priority = 10 - i) })
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/24-promo-carousel.png")
    }

    /**
     * ⭐ 退化路径：只有一条时，必须与改造前的渲染一致 ——
     * 无圆点、不自动播，只有图 + 「推广」角标。
     */
    @Test
    fun `单条退化为原样截图`() {
        val url = "https://example.invalid/promo-only.png"
        promoImageCache.put(url, stubBitmap(0xFFF0C89A))

        composeRule.setContent { Host(listOf(promo("only", url, priority = 1))) }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/25-promo-single.png")
    }
}
