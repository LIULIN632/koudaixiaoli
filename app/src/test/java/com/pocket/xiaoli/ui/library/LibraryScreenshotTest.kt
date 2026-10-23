package com.pocket.xiaoli.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.library.LibraryFile
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「资料站」外观基线（顶栏 + 搜索框 + 按课程分组 + 文件行 + 三种状态）。
 *
 * ## 为什么要有这一组
 *
 * 这一页最容易翻车的四件事都编译不出来：**长文件名把「下载」图标挤掉**、
 * **分组标题与卡片挨得太近（或太远）**、「待上传」与「下载进度」两个尾部状态混在一起、
 * **一行小字状态提示**（索引已就绪 / 没能连上）压到内容上。所以钉三张图：
 *
 * 1. 180：两条课程、四个文件，其中一个「待上传」、一个正在下载；
 * 2. 181：同上深色 —— 卡片底色与页底是否还分得开；
 * 3. 182：空态 —— 「还没有资料」要能说清资料从哪来。
 *
 * 卡片走全站统一的 `AppCard`（普通 Column + 皮肤推导，不依赖 squircle），
 * 顶栏走 MIUIX 的 `SmallTopAppBar`（见 `memory/03-pitfalls.md` §8）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class LibraryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    private val files = listOf(
        LibraryFile(
            id = "ds-ch1-slides",
            name = "数据结构 第1章 绪论 课件.pdf",
            subject = "计算机",
            course = "数据结构",
            kind = "课件",
            sizeBytes = 4_823_449L,
            urls = listOf("https://gitee.com/x/y/releases/download/lib/ds-ch1.pdf"),
        ),
        LibraryFile(
            id = "ds-exam-2023",
            name = "数据结构 2023 期末真题（含答案）.pdf",
            subject = "计算机",
            course = "数据结构",
            kind = "历年卷",
            sizeBytes = 1_204_582L,
            urls = listOf("https://gitee.com/x/y/releases/download/lib/ds2023.pdf"),
        ),
        LibraryFile(
            id = "ds-ch2-slides",
            name = "数据结构 第2章 线性表 课件.pdf",
            subject = "计算机",
            course = "数据结构",
            kind = "课件",
            sizeBytes = 6_010_112L,
            urls = emptyList(),
        ),
        LibraryFile(
            id = "math-homework",
            name = "高等数学 习题答案（第3-5章）.docx",
            subject = "数学",
            course = "高等数学",
            kind = "习题答案",
            sizeBytes = 812_000L,
            urls = listOf("https://gitee.com/x/y/releases/download/lib/math.docx"),
        ),
    )

    @Test
    fun `资料站外观截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides
                        rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    LibraryContent(
                        ui = LibraryUi(
                            files = files,
                            subjects = listOf("计算机", "数学"),
                            // 一条正在下载：尾部显示 42%，其余显示下载图标或「待上传」。
                            busyId = "ds-exam-2023",
                            progress = 42,
                        ),
                        onQueryChange = {},
                        onSelectSubject = {},
                        onRefresh = {},
                        onFileClick = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/180-library-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/181-library-dark.png")
    }

    @Test
    fun `资料站空态截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides
                        rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    LibraryContent(
                        ui = LibraryUi(error = "没能连上资料站，先用上次的索引"),
                        onQueryChange = {},
                        onSelectSubject = {},
                        onRefresh = {},
                        onFileClick = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/182-library-empty.png")
    }
}
