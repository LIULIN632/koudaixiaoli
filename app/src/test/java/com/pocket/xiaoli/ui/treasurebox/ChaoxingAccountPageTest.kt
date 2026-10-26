package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.learning.LearningCourse
import com.pocket.xiaoli.data.learning.LearningHomework
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.ui.theme.AppearanceMode
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
 * 学习通账号页：排序纯函数 + 三态渲染 + 动作接线 + 快照。
 *
 * 守的四件事：
 * 1. `pendingFirst` —— 已完成的排后面、没截止时间的排最后（把"下周截止"顶到最前是错的）；
 * 2. 未登录时**只给一个出口**（去登录），不渲染课程/作业骨架 —— 没有会话时那些列表永远是空的；
 * 3. 拉取失败但手上有旧数据时，**错误与旧数据同时出现** —— 只留其一就是静默造假；
 * 4. 这一页不写库：页面上必须把"要进待办去绑定信息里同步"说清楚，避免用户以为两边同步了。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ChaoxingAccountPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun course(name: String, teacher: String? = "王老师") =
        LearningCourse(platform = LearningPlatform.CHAOXING, name = name, teacher = teacher)

    private fun homework(
        course: String,
        title: String,
        due: String = "",
        done: Boolean = false,
    ) = LearningHomework(
        platform = LearningPlatform.CHAOXING,
        courseName = course,
        title = title,
        dueDate = due,
        completed = done,
    )

    @Test
    fun `待办排序把已完成与无截止推到最后`() {
        val input = listOf(
            homework("高等数学", "已交的那次", "2026-10-20", done = true),
            homework("线性代数", "没有截止时间"),
            homework("大学英语", "下周三交", "2026-11-05"),
            homework("体育", "这周五交", "2026-10-30"),
        )

        assertEquals(
            listOf("这周五交", "下周三交", "没有截止时间", "已交的那次"),
            pendingFirst(input).map { it.title },
        )
    }

    /** 一个用例：状态 + 是否深色 + 文件名。 */
    private data class Case(
        val file: String,
        val state: ChaoxingPageState,
        val dark: Boolean = false,
    )

    private fun render(case: Case, onOpenWeb: () -> Unit = {}, onLogout: () -> Unit = {}) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    ChaoxingAccountContent(
                        state = case.state,
                        onRetry = {},
                        onOpenWeb = onOpenWeb,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }

    private val loaded = ChaoxingPageState(
        bound = true,
        courses = listOf(course("高等数学"), course("大学英语", teacher = null)),
        homework = listOf(
            homework("高等数学", "第五次作业", "2026-11-02"),
            homework("大学英语", "Unit 3 作文", "2026-10-29"),
        ),
        syncedAt = 1_760_000_000_000L,
    )

    @Test
    fun `已登录时列出课程与待办并给出同步说明`() {
        var opened = 0
        render(Case("ignored", loaded), onOpenWeb = { opened++ })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("高等数学").assertExists()
        composeRule.onNodeWithText("第五次作业").assertExists()
        composeRule.onNodeWithText("2 门课程").assertExists()
        composeRule.onNodeWithText("2 项待办").assertExists()
        // 不写库这件事必须写在页面上，而不是藏在代码注释里
        composeRule.onNodeWithText(
            "要把作业搬进首页待办，去「我的 → 绑定信息 → 学习通 → 同步作业」。" +
                "这一页只负责看一眼，不改你的待办列表。",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithText("打开网页").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `未登录只给去登录一个出口`() {
        var opened = 0
        render(Case("ignored", ChaoxingPageState(bound = false)), onOpenWeb = { opened++ })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("还没登录学习通").assertExists()
        composeRule.onNodeWithText("待办作业").assertDoesNotExist()
        composeRule.onNodeWithText("去登录学习通").assertIsEnabled().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `拉取失败时错误与旧数据同时在场`() {
        render(
            Case(
                "ignored",
                loaded.copy(error = "学习通没响应，可能是登录过期了", loading = false),
            ),
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("这次没拉到").assertExists()
        composeRule.onNodeWithText("学习通没响应，可能是登录过期了").assertExists()
        // 旧内容不能被错误吞掉，否则用户以为数据没了
        composeRule.onNodeWithText("高等数学").assertExists()
        composeRule.onNodeWithText("下面显示的是上一次成功拉到的内容。").assertExists()
    }

    @Test
    fun `退出登录回调被接到按钮上`() {
        var loggedOut = 0
        render(Case("ignored", loaded), onLogout = { loggedOut++ })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("退出登录").performClick()
        assertEquals(1, loggedOut)
    }

    @Test
    fun `快照`() {
        val cases = listOf(
            Case("347-chaoxing-account", loaded),
            Case("348-chaoxing-unbound", ChaoxingPageState(bound = false)),
            Case(
                "349-chaoxing-error",
                loaded.copy(error = "学习通没响应，可能是登录过期了"),
                dark = true,
            ),
        )
        // setContent 每条用例只能调一次，所以三态共用一个组合、用状态切
        var current by mutableStateOf(cases.first())
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = current.dark,
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    ChaoxingAccountContent(
                        state = current.state,
                        onRetry = {},
                        onOpenWeb = {},
                        onLogout = {},
                    )
                }
            }
        }
        cases.forEach { case ->
            composeRule.runOnIdle { current = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.file}.png")
        }
    }
}
