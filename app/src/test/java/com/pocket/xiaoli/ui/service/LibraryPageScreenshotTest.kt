package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.libraryopac.LibraryOpacBook
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
 * 「图书馆」入口页的快照。
 *
 * 这一页此前**一张基线都没有**：它是服务页视觉统一那几轮漏下的角落 ——
 * 别的二级页早就换成 `ServicePageTopBar` + 主视觉卡 + 图标行，
 * 它还停在 `TopAppBar` + 淡色块 + 无图标无按压的裸列表。
 * 没有基线就意味着"改坏了也没人知道"，所以先把四个检索态全钉下来。
 *
 * ## 为什么整页走 `LibraryPageContent` 而不是 `LibraryScreen`
 *
 * 检索中 / 有结果 / 没查到 / 失败这四个态原先是页面里的四个 `var`，
 * 测试没有任何入口能把它们摆出来（只能真联网等回包）。
 * 现在状态收成一个 [LibraryPageUi]，内容组件只吃数据 —— 四个态各渲一张。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class LibraryPageScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun renderPage(ui: LibraryPageUi = LibraryPageUi(), dark: Boolean = false) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                LibraryPageContent(
                    ui = ui,
                    onQueryChange = {},
                    onSearch = {},
                    onAction = {},
                    onOpenBook = {},
                    onOpenWebSearch = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** 只渲一条结果行（整页很长，`LazyColumn` 只组合视口内的项，落在折叠线以下就断言不到）。 */
    private fun renderBook(book: LibraryOpacBook, narrow: Boolean = false) {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (narrow) {
                        Box(modifier = Modifier.width(328.dp).fillMaxHeight()) { LibraryBookRow(book, onClick = {}) }
                    } else {
                        LibraryBookRow(book, onClick = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `整页基线`() {
        renderPage()
        // 没搜过的时候来源胶囊要在（它回答"打不开该怪哪套系统"）
        composeRule.onNodeWithText("馆藏来自 OPAC").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/371-library-page.png")
    }

    @Test
    fun `整页基线 深色`() {
        renderPage(dark = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/372-library-page-dark.png")
    }

    /** 五个功能入口都在，且座位那一条标着「原生」（它是唯一不出网页的动作）。 */
    @Test
    fun `功能入口齐全`() {
        renderPage()
        listOf("我的借阅", "图书馆座位", "电子资源", "馆藏分布", "借阅规则").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("原生").assertIsDisplayed()
    }

    /**
     * 校园网 / VPN 这件事**只说一遍**。
     *
     * 改造前它同时出现在主视觉卡第三行、「我的借阅」的副标题和页脚小字里 —— 三处。
     * 这一条把"不许再说第二遍"钉成判据，因为这种重复在人眼里是"这页很啰嗦"，
     * 但没人能指出具体哪一行多余。（写完这条断言当场又抓到第四处：「电子资源」的副标题
     * 也写着"VPN 使用说明" —— 它说的是那一页的内容，但同一个词在一屏里出现两次就是噪音，
     * 改成了"校外怎么访问"。）
     */
    @Test
    fun `VPN 提示只说一遍`() {
        renderPage()
        composeRule.onAllNodesWithText("VPN", substring = true).assertCountEquals(1)
    }

    @Test
    fun `检索中`() {
        renderPage(LibraryPageUi(query = "活着", searching = true))
        composeRule.onNodeWithText("正在检索馆藏…").assertIsDisplayed()
    }

    /** 有结果：索书号单独成块放在右边，而不是混进「作者 · 出版社 · 年份 · 索书号」的长句里。 */
    @Test
    fun `检索结果`() {
        renderPage(
            LibraryPageUi(
                query = "活着",
                searched = true,
                results = listOf(
                    LibraryOpacBook(
                        title = "活着",
                        author = "余华",
                        publisher = "作家出版社",
                        year = "2012",
                        callNumber = "I247.5/1043",
                        detailUrl = "https://lib.yitsd.edu.cn/opac/book/1",
                    ),
                    LibraryOpacBook(
                        title = "活着（附作者授权全新修订完整版）",
                        author = "余华",
                        publisher = "北京十月文艺出版社",
                        year = "2017",
                        callNumber = "I247.5/1043-2",
                        detailUrl = "https://lib.yitsd.edu.cn/opac/book/2",
                    ),
                ),
            )
        )
        composeRule.onNodeWithText("馆藏结果 · 2 本").assertIsDisplayed()
        // 搜过之后 hero 收起：来源胶囊让位，把结果顶回折叠线内
        composeRule.onAllNodesWithText("馆藏来自 OPAC").assertCountEquals(0)
        // 精确匹配：第二本的索书号是 "I247.5/1043-2"，按子串找会同时命中两个节点
        composeRule.onNodeWithText("I247.5/1043").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/373-library-results.png")
    }

    @Test
    fun `检索到了但没结果不是错误`() {
        renderPage(LibraryPageUi(query = "不存在的书名", searched = true))
        composeRule.onNodeWithText("没查到这本书").assertIsDisplayed()
        composeRule.onAllNodesWithText("失败", substring = true).assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/374-library-nomatch.png")
    }

    @Test
    fun `检索失败给原因和重试`() {
        renderPage(
            LibraryPageUi(
                query = "活着",
                searched = true,
                error = "图书馆检索返回 HTTP 502",
            )
        )
        composeRule.onNodeWithText("馆藏检索失败").assertIsDisplayed()
        composeRule.onNodeWithText("图书馆检索返回 HTTP 502", substring = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/375-library-error.png")
    }

    /**
     * 结果行的 **328dp 窄屏**版（= 360dp 真机去掉页面左右 16dp 后那一档）。
     *
     * 这一行右边同时有索书号块与长书名：窄屏上索书号会不会把书名挤成一个字，
     * 只有按真机宽度渲才看得出来。
     */
    @Test
    fun `结果行 窄屏`() {
        renderBook(
            LibraryOpacBook(
                title = "计算机网络：自顶向下方法（第 7 版）",
                author = "James F. Kurose",
                publisher = "机械工业出版社",
                year = "2018",
                callNumber = "TP393.04/288",
            ),
            narrow = true,
        )
        composeRule.onNodeWithText("TP393.04/288").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/376-library-book-narrow.png")
    }
}
