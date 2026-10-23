package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
 * 更新日志两张卡的外观基线。
 *
 * 2026-10 把 M3 `Card` + 硬投影换成全 App 统一的 `AppCard`（皮肤圆角 + 弥散阴影）。
 * 没有整页快照：`ChangelogScreen` 要 `appContainer`（Room / prefs），
 * 所以直接把两张纯参数驱动的卡渲出来 —— 这也是它们 2026-10 从 private 改成
 * internal 的原因。
 *
 * 深浅各一张：深色下卡与页底的层级只靠明度差，和设置页是同一个风险点。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ChangelogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `更新日志卡片浅色截图`() {
        composeRule.setContent { Screen(dark = false) { Cards() } }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/173-changelog-light.png")
    }

    @Test
    fun `更新日志卡片深色截图`() {
        composeRule.setContent { Screen(dark = true) { Cards() } }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/174-changelog-dark.png")
    }

    @Composable
    private fun Cards() {
        ReleaseNotesCard(
            versionName = "v0.1.69",
            notes = listOf(
                "消息页改成整页列表，公告正文不再被对话框压成碎句",
                "顶栏可以手动重新检查",
            ),
        )
        VersionCard(
            log = VersionLog(
                version = "v0.1.41",
                date = "2026-10-17",
                features = listOf("新增更新与公告：打开 App 自动检查"),
                fixes = listOf("修：通知抽屉一直显示「正在检查…」"),
                improvements = listOf("改：自动更新校园数据移入「数据与诊断」"),
            ),
            isFirst = true,
        )
    }

    @Composable
    private fun Screen(dark: Boolean, content: @Composable () -> Unit) {
        PocketXiaoliTheme(
            appearanceMode = AppearanceMode.RAINBOW,
            skinStyle = SkinStyle.VIVID,
            darkTheme = dark,
        ) {
            val colors = LocalPocketXiaoliColors.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceBase)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}
