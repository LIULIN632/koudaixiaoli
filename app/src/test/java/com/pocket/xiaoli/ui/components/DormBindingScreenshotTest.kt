package com.pocket.xiaoli.ui.components

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
 * 宿舍绑定表单（`DormBindingContent`）的外观快照。
 *
 * ## 为什么补这组
 *
 * 2026-10 用户反馈"宿舍绑定不好用了"，查下来根子是**房间号转盘**：
 * 改造前它是一个 720 项的长轮（1-18 层 × 01-40 间拼成 `101`…`1840`），
 * 选「412」要在一长串里滚过去；而且它**根本不认南校的房号规则**
 *（南校是 `n602` 这种"北/南侧 + 楼层 + 房号"的写法）—— 南校同学在轮子里找不到自己的房。
 *
 * 现在拆成"楼栋 / 朝向 / 楼层 / 房间"几级短轮，并给选中行加了高亮条。
 * 这些改动**只在图上看得出来**（轮子够不够短、高亮够不够明显），所以补基线。
 *
 * ## ⚠️ 已知限制：转盘本体在 Robolectric 下渲不出来
 *
 * 转盘是 `AndroidView(NumberPicker)`，而 Robolectric 对它只做影子实现 ——
 * 基准图里那几块会是**空白**（这正是"别只看图就下结论"的又一个例子：
 * 轮子里的文字看不到，但**布局与高亮条的位置**看得到，卡间距、轮宽比例、
 * 选中条有没有对齐也都能验）。真机观感仍以 `docs/UI验收手册.md` 的真机协议为准。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DormBindingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Case(
        val name: String,
        val building: String,
        val room: String,
        val dormCode: String,
    )

    private val cases = listOf(
        // 本校区：房号 = 楼层 + 两位（412），没有"朝向"那级轮
        Case(
            name = "89-dorm-binding-main",
            building = "3号宿舍楼",
            room = "412",
            dormCode = "",
        ),
        // 南校：房号 = 朝向 + 楼层 + 两位（s602），多出"朝向"轮；且带宿舍编号
        Case(
            name = "89-dorm-binding-south",
            building = "10号宿舍楼",
            room = "s602",
            dormCode = "BS10S0602",
        ),
    )

    @Test
    fun `宿舍绑定表单截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                // 与真实二级页相同的边距（左右 screenH、顶 spaceMd）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(
                            start = skin.screenH,
                            end = skin.screenH,
                            top = skin.spaceMd,
                        ),
                ) {
                    DormBindingContent(
                        initialBuilding = case.building,
                        initialRoom = case.room,
                        initialDormCode = case.dormCode,
                        canUnbind = true,
                        onConfirm = { _, _, _, _ -> },
                        onUnbind = {},
                    )
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}
