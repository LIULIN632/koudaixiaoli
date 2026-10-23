package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页搜索入口的基线（2026-10 补）。
 *
 * 入口从「白底 + 描边」升级为 [com.pocket.xiaoli.ui.components.AppCard] 外壳
 * （阴影随皮肤）+ 32dp 强调色淡底圆放大镜。它是首页常驻的第一块内容，
 * 改动频率不低，但此前没有任何图能证明它长什么样 —— 与 [NextCourseCard] 同样的理由。
 *
 * 两套皮肤各一张：极简应当**无阴影**、鲜活应当有弥散阴影；
 * 若哪天外壳又被换回普通 `Surface`，这两张会立刻看见差别。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomeSearchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val cases = listOf(
        SkinStyle.VIVID to "117-home-search-vivid",
        SkinStyle.MINIMAL to "118-home-search-minimal",
    )

    @Test
    fun `首页搜索入口截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = case.first,
                darkTheme = false,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = skin.screenH, vertical = skin.spaceMd),
                ) {
                    HomeSearchEntry(onClick = {})
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.second}.png")
        }
    }
}
