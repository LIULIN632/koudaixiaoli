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
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalAppearanceMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
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
 * 首页「下一节课」卡的**基线**（2026-10 补）。
 *
 * ## 为什么要补（这是踩出来的）
 *
 * 用户拿着一张首页截图说"下一节课提示卡片，改回之前的" —— 而我去找基线和它比时发现：
 * **整个首页一张基线图都没有**。于是查证过程只能靠翻 git 历史与读代码,
 * 而这一批恰好改过卡片外壳（`SoftCard`：MIUIX Card → 皮肤数据驱动的 `AppCard`，
 * 圆角 / 阴影 / 内边距全变），**没有任何图能证明它变成了什么样**。
 *
 * 这正是 `CLAUDE.md` 里那条"反馈说某处不好看时，要补整页基线"的又一个实例：
 * 没有基线，下一次改动同样会静默溜过去。
 *
 * ## 四态
 *
 * - **鲜活皮肤**（默认）：深绿渐变 hero 卡（上 `0xFF3A7A66` → 下 `0xFF23503F`）
 * - **极简皮肤 + PURE 外观**：强调色渐变 hero 卡（`0xFF7B9FF9` 往暗端收）
 * - **今天没有课**：白卡 + 两行文案，且**没有** hero 底色
 * - **还没导入课表**：同一张白卡，但文案改说"还没有课表"并给出「去导入课表」
 *
 * ⚠️ 三张图刻意用**同一次 setContent** 驱动（`setContent` 一个测试只能调一次），
 * 否则第二次会抛 "has already set content"。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomeCourseCardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Case(
        val name: String,
        val skin: SkinStyle,
        val appearance: AppearanceMode,
        val course: HomeCourse?,
        /** 该方案下有没有课。false 时卡片应说"还没有课表"而不是"今天没有课啦"。 */
        val hasTimetable: Boolean = true,
    )

    private val sample = HomeCourse(
        course = CourseEntity(
            name = "建设法规",
            teacher = "王若黎",
            room = "文二教109",
            dayOfWeek = 4,
            startSlot = 9,
            endSlot = 10,
        ),
        startTime = "18:30",
        endTime = "20:10",
        dayOffset = 0,
    )

    private val cases = listOf(
        Case("110-home-course-card-vivid", SkinStyle.VIVID, AppearanceMode.RAINBOW, sample),
        Case("111-home-course-card-minimal", SkinStyle.MINIMAL, AppearanceMode.PURE, sample),
        Case("112-home-course-card-noclass", SkinStyle.VIVID, AppearanceMode.RAINBOW, null),
        // 第四态（2026-10 补）：**零数据**。与 112 的区别是 `hasTimetable = false` ——
        // 112 是"有课表、今天没课"（那句"今天没有课啦"是事实），
        // 116 是"压根还没导入"（说"今天没有课啦"就是假话）。
        // 这两个态此前共用同一张卡，正是新生第一屏读到假话的原因。
        Case("116-home-course-card-empty-timetable", SkinStyle.VIVID, AppearanceMode.RAINBOW, null, hasTimetable = false),
    )

    @Test
    fun `首页下一节课卡截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = case.appearance,
                skinStyle = case.skin,
                darkTheme = false,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAppearanceMode provides case.appearance,
                    LocalCampusLayoutMode provides CampusLayoutMode.MODERN,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surfaceBase)
                            .padding(horizontal = skin.screenH, vertical = skin.spaceMd),
                    ) {
                        NextCourseCard(
                            course = case.course,
                            colors = colors,
                            onOpenTimetable = {},
                            onGoToClass = {},
                            hasTimetable = case.hasTimetable,
                        )
                    }
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
