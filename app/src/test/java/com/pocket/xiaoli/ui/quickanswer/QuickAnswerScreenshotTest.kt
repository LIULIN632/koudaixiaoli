package com.pocket.xiaoli.ui.quickanswer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.quickanswer.QuickAnswerChapter
import com.pocket.xiaoli.data.quickanswer.QuickAnswerCourse
import com.pocket.xiaoli.data.quickanswer.QuickAnswerQuestion
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
 * 「快问快答」外观基线（课程 chips + 章节小标题 + 题目行 + 展开后的答案要点）。
 *
 * ## 为什么要有这一组
 *
 * 这一页靠"折叠/展开"承载信息，三个地方编译全绿也看不出来：
 * **展开后答案要点会不会把「会了 / 还不会」挤到屏幕外**、
 * **课程 chips 横向排不下时会不会被裁掉**、
 * **底部「已经会了 1/4」那行与最后一张卡片是否贴在一起**。
 * 钉三张：190 常态（一条展开）、191 深色、192 搜索态。
 *
 * 卡片走全站统一的 `AppCard`；顶栏是 MIUIX 的 `SmallTopAppBar`，与 `LibraryScreenshotTest` 同壳。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class QuickAnswerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    private val course = QuickAnswerCourse(
        id = "ds",
        name = "数据结构",
        subject = "计算机",
        teacher = "王老师",
        chapters = listOf(
            QuickAnswerChapter(
                index = 2,
                title = "线性表",
                questions = listOf(
                    QuickAnswerQuestion(
                        id = "ds-2-1",
                        question = "顺序表和链表各自的优缺点是什么？",
                        answerPoints = listOf(
                            "顺序表：随机访问 O(1)，但插入删除要搬数据",
                            "链表：插入删除只改指针，但找第 k 个要顺着走",
                            "答一句取舍：查得多用顺序表，改得多用链表",
                        ),
                        hint = "老师通常接着问「那 ArrayList 为什么默认扩容 1.5 倍」",
                    ),
                    QuickAnswerQuestion(
                        id = "ds-2-2",
                        question = "头结点到底有什么用？",
                        answerPoints = listOf(
                            "让空表和非空表的插入删除代码统一，不用特判第一个位置",
                        ),
                    ),
                ),
            ),
            QuickAnswerChapter(
                index = 3,
                title = "栈与队列",
                questions = listOf(
                    QuickAnswerQuestion(
                        id = "ds-3-1",
                        question = "循环队列为什么要空出一个位置？",
                        answerPoints = listOf(
                            "为了区分「队空」和「队满」：两种状态 front == rear 会撞车",
                        ),
                    ),
                ),
            ),
        ),
    )

    /** 第二门课：只为让「学科 → 课程」两行 chip 同时出现在基线里。 */
    private val mathCourse = QuickAnswerCourse(
        id = "math",
        name = "高等数学",
        subject = "数学",
        chapters = listOf(
            QuickAnswerChapter(
                index = 1,
                title = "极限与连续",
                questions = listOf(
                    QuickAnswerQuestion(
                        id = "math-1-1",
                        question = "为什么 0/0 型要用洛必达？",
                        answerPoints = listOf("未定式上下各自求导，把无穷小的比换成导数之比"),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `快问快答外观截图`() {
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
                    QuickAnswerContent(
                        ui = QuickAnswerUi(
                            courses = listOf(course, mathCourse),
                            course = course,
                            subjects = listOf("计算机", "数学"),
                            // 第一条展开：答案要点 + 提示 + 「会了 / 还不会」都在屏内吗
                            expandedId = "ds-2-1",
                            knownIds = setOf("ds-3-1"),
                            totalQuestions = 3,
                        ),
                        onQueryChange = {},
                        onSelectSubject = {},
                        onSelectCourse = {},
                        onToggle = {},
                        onMark = { _, _ -> },
                        onRefresh = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/190-quickanswer-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/191-quickanswer-dark.png")
    }

    @Test
    fun `快问快答搜索态截图`() {
        val bank = com.pocket.xiaoli.data.quickanswer.QuickAnswerBank(courses = listOf(course))
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
                    QuickAnswerContent(
                        ui = QuickAnswerUi(
                            courses = listOf(course, mathCourse),
                            course = course,
                            subjects = listOf("计算机", "数学"),
                            query = "队列",
                            hits = bank.search("队列", course.id),
                            totalQuestions = 3,
                        ),
                        onQueryChange = {},
                        onSelectSubject = {},
                        onSelectCourse = {},
                        onToggle = {},
                        onMark = { _, _ -> },
                        onRefresh = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/192-quickanswer-search.png")
    }
}
