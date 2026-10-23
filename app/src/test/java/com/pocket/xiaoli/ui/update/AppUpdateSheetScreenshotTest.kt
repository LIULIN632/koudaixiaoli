package com.pocket.xiaoli.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * 更新抽屉内容基线。
 *
 * `WindowBottomSheet` 是独立窗口，JVM 截图抓不到（与 `RecommendSheet` 同一条限制），
 * 所以这里只渲内容本体，但套上同样的「底部卡片」外壳，盯住三件最容易改坏的事：
 *
 * 1. 三条说明在窄屏上是否被压成碎句；
 * 2. 下载按钮与「稍后提醒」的间距、点击高度；
 * 3. 强更的危险色文案与「没有稍后出口」是否仍然成立。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AppUpdateSheetScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    private val notes = listOf(
        "消息页改成整页列表，公告正文不再被对话框压成碎句",
        "更新检查支持换源重试，坏包自动删掉",
        "下载进度显示速度与剩余大小",
    )

    @Test
    fun `普通更新抽屉内容截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                SheetFrame {
                    AppUpdateSheetBody(
                        force = false,
                        notes = notes,
                        message = null,
                        downloading = false,
                        progress = 0,
                        downloadLabel = null,
                        pendingInstall = false,
                        hasDownload = true,
                        isApk = true,
                        onDownload = {},
                        onLater = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/251-update-sheet-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/252-update-sheet-dark.png")
    }

    @Test
    fun `强制更新抽屉内容截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                SheetFrame {
                    AppUpdateSheetBody(
                        force = true,
                        notes = notes.take(2),
                        message = "当前版本已不受支持，请更新到 v0.1.74 后继续使用。",
                        downloading = false,
                        progress = 0,
                        downloadLabel = null,
                        pendingInstall = false,
                        hasDownload = true,
                        isApk = true,
                        onDownload = {},
                        onLater = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/253-update-sheet-force.png")
    }
}

/** 模拟底部抽屉在页面上的位置与底色（只是外壳，不参与业务） */
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(colors.surfaceCard)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            content()
        }
    }
}
