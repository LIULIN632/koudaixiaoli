package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「大学任务清单」。
 *
 * 这一页的视觉早就归好口了（`ServicePageTopBar` + 主视觉卡 + `AppCard`），
 * 但**一张基线都没有**，而且有两处只有看代码才会发现的问题，都在这里钉住：
 *
 * 1. **顺序**：以前已完成的任务排在最前面，于是主视觉卡刚说完"先挑一件最容易的开始"，
 *    第一屏全是划掉的旧账。现在没完成的在前（`taskDisplayOrder` 是纯函数，直接断言）。
 * 2. **删除键看不见**：图标透明度写死 0.4，压在白卡上几乎找不到 ——
 *    一个看不见的删除键比没有更危险，现在用正常的弱化色。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class TaskChecklistScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun task(name: String, completed: Boolean, order: Int) =
        TaskEntity(id = order.toLong(), name = name, completed = completed, sortOrder = order)

    /** 两条并排：一条没完成、一条已完成，划掉线与弱化色要一眼分得开。 */
    @Test
    fun `任务行`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    TaskItem(task("考普通话二甲", completed = false, order = 0), onToggle = {}, onDelete = {})
                    TaskItem(task("交社团周报", completed = true, order = 1), onToggle = {}, onDelete = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("考普通话二甲").assertExists()
        // 删除键是"看得见"的：两行各有一枚，语义树里都找得到
        //（以前 tint 写死 0.4 透明度，压在白卡上几乎看不见 —— 看不见的删除键比没有更危险）
        composeRule.onAllNodesWithContentDescription("删除").assertCountEquals(2)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/383-task-item.png")
    }

    @Test
    fun `没完成的排前面`() {
        val tasks = listOf(
            task("先交的", completed = true, order = 0),
            task("后做的", completed = false, order = 5),
            task("也做完", completed = true, order = 1),
            task("最急的", completed = false, order = 9),
        )

        val order = taskDisplayOrder(tasks).map { it.name }

        // 未完成组在前，组内仍按用户自己排的 sortOrder
        assertEquals(listOf("后做的", "最急的", "先交的", "也做完"), order)
    }
}
