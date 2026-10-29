package com.pocket.xiaoli.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.model.NoteItem
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import com.pocket.xiaoli.util.relativeSinceLabel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「学习笔记」这一页。
 *
 * ## 这里守的最要紧的一件事：界面上不许有**不生效的开关**
 *
 * 这一页原先有一个「允许后续上传到笔记广场」的开关、一张「待公开」徽标，
 * 外加两处"之后可以同步到笔记广场"的文案 —— 而全仓库读 `isPublic` 的地方是 **0 个**：
 * 没有上传通道，也没有"笔记广场"这个东西。用户勾了它，什么也不会发生。
 *
 * 2026-10-29 按"撤掉假状态而不是化妆"处理：开关、徽标、两处承诺文案一起撤。
 * 所以这里既有像素判据，也有一条**源码扫描**判据（文案最容易在改样式时溜回来）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class NoteCardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 固定时间戳：早于 30 天，`relativeSinceLabel` 会退回 ISO 日期，图上才是稳定的字。 */
    private val note = NoteItem(
        id = "n1",
        title = "数据结构 第3章 树与二叉树",
        content = "中序 + 后序可以唯一确定一棵二叉树；前序 + 中序也可以。缺一个都不行。",
        updatedAt = 1_767_225_600_000L, // 2026-01-01 08:00 UTC+8
    )

    private fun render(dark: Boolean = false) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    NoteCard(note = note, onClick = {}, onDelete = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `笔记卡`() {
        render()
        composeRule.onNodeWithText("数据结构 第3章 树与二叉树").assertExists()
        composeRule.onNodeWithText("2026-01-01").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/381-note-card.png")
    }

    @Test
    fun `笔记卡 深色`() {
        render(dark = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/382-note-card-dark.png")
    }

    /** 「待公开」这类徽标不该再出现：它对应的开关已经撤掉了。 */
    @Test
    fun `不再有待公开徽标`() {
        render()
        composeRule.onAllNodesWithText("待公开").assertCountEquals(0)
        composeRule.onAllNodesWithText("笔记广场").assertCountEquals(0)
    }

    /**
     * 文案也要一起撤干净。
     *
     * 样式改完把"笔记广场"这种承诺留在某句小字里，是最典型的"看着还有这个功能"，
     * 而它已经没有任何实现 —— 源码扫描比截图更便宜地拦住这件事。
     */
    @Test
    fun `源码里不再承诺笔记广场`() {
        val candidates = listOf(
            "src/main/java/com/pocket/xiaoli/ui/notes/NoteListScreen.kt",
            "src/main/java/com/pocket/xiaoli/data/model/NoteItem.kt",
        )
        // 界面那一侧一个字都不许留；模型里允许留一句"这个开关为什么被撤了"的说明，
        // 所以那句注释不算违规，字段声明才算。
        val screen = File("src/main/java/com/pocket/xiaoli/ui/notes/NoteListScreen.kt").readText()
        assertFalse("界面上不该再出现「笔记广场」", screen.contains("笔记广场"))
        assertFalse("界面上不该再有「待公开」", screen.contains("待公开"))
        candidates.forEach { path ->
            val text = File(path).readText()
            assertFalse("$path 不该再有 isPublic 字段", text.contains("val isPublic"))
        }
    }

    /** 相对时间：三分钟前的笔记要说"3 分钟前"，而不是"10-29 15:40"。 */
    @Test
    fun `近的时间走相对档`() {
        val now = 1_767_225_600_000L
        assertEquals("3 分钟前", relativeSinceLabel(now - 3 * 60_000L, "", now))
        // 超过 30 天退回日期；动词为空串时那一档不能留尾空格
        val fortyDays = 40L * 24 * 60 * 60_000
        assertEquals("2026-01-01", relativeSinceLabel(now, "", now + fortyDays))
        assertFalse(relativeSinceLabel(now, "", now + fortyDays).endsWith(" "))
    }
}
