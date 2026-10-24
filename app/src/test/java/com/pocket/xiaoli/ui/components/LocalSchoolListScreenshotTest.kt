package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
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
 * 共享选校列表（[localSchoolItems] / [LocalSchoolsEmptyState]）的外观快照。
 *
 * 教务直连导入与网页导入共用这份列表骨架，但它本身不是一个可单独渲染的 Composable
 * （`LazyListScope` 扩展），此前两个入口各抄一份时也**都没有基线** ——
 * 改坏分组顺序、把自定义学校的编辑/删除弄丢，测试都看不出来。
 *
 * | 文件 | 内容 |
 * |---|---|
 * | `310-school-list` | 搜索框 + 收藏分组 + 烟台学校 + 自定义学校（含编辑/删除） |
 * | `311-school-list-dark` | 同上，深色 |
 * | `312-school-list-empty` | 搜索无结果的空态（给「反馈」出口） |
 *
 * 学校名与地址都用示例数据，不用任何真实院校的教务地址。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class LocalSchoolListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val schools = listOf(
        School("YIT", "烟台理工学院", "烟台", JwSystemType.ZHENGFANG, "jw.example.edu.cn"),
        School("YTU", "烟台大学", "烟台", JwSystemType.ZHENGFANG, "jw2.example.edu.cn"),
        School("SDBI", "山东工商学院", "烟台", JwSystemType.QIANGZHI, "jw3.example.edu.cn"),
        School("custom_1", "我的学校", "外地", JwSystemType.ZHENGFANG, "jw4.example.edu.cn"),
    )

    private data class Case(
        val name: String,
        val dark: Boolean,
        val query: String,
        val empty: Boolean = false,
    )

    private val cases = listOf(
        Case(name = "310-school-list", dark = false, query = ""),
        Case(name = "311-school-list-dark", dark = true, query = ""),
        Case(name = "312-school-list-empty", dark = false, query = "不存在的大学", empty = true),
    )

    @Test
    fun `选校列表截图`() {
        var current by mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                SchoolListPage(case)
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }

    @Test
    fun `搜索无结果时给反馈出口`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                LocalSchoolsEmptyState(query = "不存在的大学", onFeedback = {})
            }
        }
        composeRule.onNodeWithText("未找到「不存在的大学」").assertExists()
        composeRule.onNodeWithText("不是你要找的学校？反馈").assertExists()
    }

    @Composable
    private fun SchoolListPage(case: Case) {
        val skin = LocalSkin.current
        val colors = LocalPocketXiaoliColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
        ) {
            LocalSchoolSearchField(query = case.query, onQueryChange = {})
            if (case.empty) {
                LocalSchoolsEmptyState(query = case.query, onFeedback = {})
            } else {
                LazyColumn(modifier = Modifier.padding(top = skin.spaceSm).weight(1f)) {
                    localSchoolItems(
                        schools = schools,
                        favoriteIds = setOf("YTU"),
                        selectedSchoolId = "YIT",
                        onSelect = {},
                        onToggleFavorite = {},
                        onEdit = {},
                        onDelete = {},
                        footer = "列表只收烟台院校，外地学校可点上方「添加自定义学校」手动添加",
                    )
                }
            }
        }
    }
}
