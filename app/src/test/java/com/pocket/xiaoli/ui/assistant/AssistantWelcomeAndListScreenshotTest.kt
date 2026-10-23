package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.agent.AgentPhase
import com.pocket.xiaoli.data.agent.AssistantConversation
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
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
 * 鲤鱼两个**此前完全没有基线**的界面：空态欢迎页、历史对话列表。
 *
 * ## 为什么补这两组
 *
 * 鲤鱼的快照基线原本只覆盖输入框（`30..33`）与消息区（`40..45`），
 * 而**空态欢迎页是用户每次"开一段新对话"第一眼看到的东西**，
 * 历史列表是"我要找回上次那段"的唯一入口 —— 两个都是高曝光面，却一直靠肉眼。
 *
 * 这一轮改的正好是它们，于是先立基线再改：欢迎页的 64dp 头像从手写
 * `Box + Text("🐟")` 换成 `AiAvatar`（接 `AgentPhase`），列表行补上**选中语义**。
 *
 * ## 列表这组重点不在"好不好看"，而在**两种表达必须一致**
 *
 * "当前会话"有两处表达：视觉上的主色描边、语义上的 `selected`。
 * 截图只看得到前者（而且 1dp 描边在缩略图上几乎看不见），
 * 所以这组用例**三样都断言**：快照 + `assertIsSelected` + 选中行的删除按钮带标题。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AssistantWelcomeAndListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR

        /**
         * 固定的"现在"，与 `ConversationTimeTest` 用同一个基准值。
         *
         * ⚠️ 这里绝不能写 `System.currentTimeMillis()`。相对时刻会让渲染结果随日期变化，
         * 于是**基准图第二天自己变红**，而表现只是"某个日期串变了"、极难定位。
         * 注入固定基准之后，下面三个 `updatedAt` 才对应**确定**的三行标签：
         * 「今天」/「昨天」/「M月d日」各一支，`formatTime` 的三个分支全被这张图覆盖。
         */
        const val NOW = 1780826400000L
    }

    private val conversations = listOf(
        AssistantConversation(
            id = 3,
            title = "明天的课在哪个教室",
            preview = "明天第一节是高等数学，在 A-302",
            updatedAt = NOW - HOUR,
        ),
        AssistantConversation(
            id = 2,
            title = "我还有哪些作业没交",
            preview = "按截止日期排一下",
            updatedAt = NOW - DAY,
        ),
        AssistantConversation(
            id = 1,
            title = "上学期绩点是多少",
            preview = "读本机存档的成绩",
            updatedAt = NOW - 5 * DAY,
        ),
    )

    /** 一个欢迎页用例：文件名 + Agent 此刻的阶段。 */
    private data class WelcomeCase(val name: String, val phase: AgentPhase)

    private val welcomeCases = listOf(
        WelcomeCase("50-welcome-idle", AgentPhase.IDLE),
        WelcomeCase("51-welcome-thinking", AgentPhase.THINKING),
    )

    /**
     * 空态欢迎页：**静息 / 正在作答**两态。
     *
     * 两态都需要基线，因为差别全在头像光环上 —— 而"脉冲"是无限动画，
     * 快照会抓到某一帧，所以基线比的是**光环的颜色与浓度**，不是动画相位。
     * （`AiAvatar` 在 IDLE 时 `scale = 1f` 恒定，在 THINKING 时才逐帧变化。）
     */
    @Test
    fun `空态欢迎页各阶段截图`() {
        val current = mutableStateOf(welcomeCases.first())
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = skin.spaceLg),
                ) {
                    AssistantWelcome(phase = current.value.phase, onPick = {})
                }
            }
        }
        welcomeCases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }

    /**
     * 减卡之后仍要**八条问题全部可达**：前四条是大卡，后四条收成了小胶囊。
     *
     * 这条守的是 `take(4)` 最容易犯的错 —— 只留下四张卡、把 `drop(4)` 忘掉。
     * 截图看不出少没少（页面上照样有卡有 chip），所以逐条按文字断言。
     */
    @Test
    fun `欢迎页仍然给出全部八条建议问题`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                AssistantWelcome(phase = AgentPhase.IDLE, onPick = {})
            }
        }
        ASSISTANT_PROMPTS.forEach { prompt ->
            composeRule.onNodeWithText(prompt.title).assertExists()
        }
    }

    /**
     * 「我还能」不是装饰：点名字，发出去的是完整问句。
     *
     * 三条分别落在 `get_exams` 与 `find_campus_service` 上；这里钉住"标签 → 问句"
     * 的映射，避免以后只改标签、把发送内容改丢。
     */
    @Test
    fun `我还能的能力胶囊发出对应问句`() {
        val picked = mutableListOf<String>()
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                AssistantWelcome(phase = AgentPhase.IDLE, onPick = { picked += it })
            }
        }
        ASSISTANT_ABILITIES.forEach { ability ->
            composeRule.onNodeWithText(ability.label).performClick()
        }
        assertEquals(ASSISTANT_ABILITIES.map { it.question }, picked)
    }

    /**
     * 纯净模式的欢迎页。
     *
     * 这一张守的是**中性卡的可见性**：纯净模式下建议卡不再染课程色，
     * 而 Vivid 的页底与卡色都接近纯白 —— 没有描边就会"白卡压白底"、整组卡片消失。
     * 这类问题只在图上看得出，语义树里卡片元素一个都不少。
     */
    @Test
    fun `纯净模式的欢迎页仍有可见边界`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.PURE,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = skin.spaceLg),
                ) {
                    AssistantWelcome(phase = AgentPhase.IDLE, onPick = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/53-welcome-pure.png")
    }

    /**
     * 建议问题卡的 **360dp 窄屏**版（2026-10 布局修复轮第④阶段）。
     *
     * 卡片右下角那枚淡水印此前是写死的 `size(76.dp) + offset(16,12)`。这组卡是双列
     * `weight(1f)`，宽度跟着屏走，所以固定值在窄屏上吃掉近半张卡、在宽屏上又显得小气；
     * 更麻烦的是它锚的是**旁边标题文字的右边缘**（内层容器只有 wrap 宽），
     * 五个建议的长短不同，水印就跟着晃。
     * 现在尺寸和出血量都从卡宽推出来，内层容器 `fillMaxWidth` 把它钉回卡片自身。
     * 这张图与 `50-welcome-idle` 对着看，应当是"比例没变、整体随卡宽缩放"。
     */
    @Test
    fun `空态欢迎页 360dp 窄屏截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surfaceBase)
                            .padding(horizontal = skin.spaceLg),
                    ) {
                        AssistantWelcome(phase = AgentPhase.IDLE, onPick = {})
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/244-narrow360-welcome.png")
    }

    /**
     * 历史对话列表。
     *
     * ③ 那一行的 `updatedAt` 落在"前天"档，① 落在"今天"档 —— 顺带把
     * `formatTime` 的两个分支纳入视觉基线（纯逻辑分支已有 `ConversationTimeTest`）。
     */
    @Test
    fun `历史对话列表截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = skin.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
                    contentPadding = PaddingValues(vertical = skin.spaceWide),
                ) {
                    items(conversations, key = { it.id }) { item ->
                        ConversationRow(
                            item = item,
                            isCurrent = item.id == 3L,
                            onClick = {},
                            onDelete = {},
                            // 固定基准：不注入的话这张基准图会随日期漂移（见 NOW 的说明）
                            now = NOW,
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/52-conversation-list.png")
    }

    /**
     * ⚠️ **这条守的是"基准图的确定性"**，不是业务逻辑（业务逻辑在 `ConversationTimeTest`）。
     *
     * 列表行的时间标签是相对"现在"算的，而快照要求逐像素可比。若有人把
     * `ConversationRow(now = …)` 这个注入点删掉、或让测试重新用
     * `System.currentTimeMillis()` 造数据，**这张基准图会在第二天自己变红** ——
     * 那种失败最难查（表现只是某个日期串变了，看起来像随机 flake）。
     *
     * 所以这里把三行标签**逐字钉死**：只要它们确定，快照就确定。
     * 三个分支（今天 / 昨天 / M月d日）正好各占一行。
     */
    @Test
    fun `列表时间标签在固定基准下是确定的`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Column {
                    conversations.forEach { item ->
                        ConversationRow(
                            item = item,
                            isCurrent = false,
                            onClick = {},
                            onDelete = {},
                            now = NOW,
                        )
                    }
                }
            }
        }

        // 三支各一行：这样 `formatTime` 的分支一旦被改动，快照与这条会同时红
        composeRule.onNodeWithText("今天").assertExists()
        composeRule.onNodeWithText("昨天").assertExists()
        composeRule.onNodeWithText("6月2日").assertExists()
    }

    /**
     * ⚠️ **这条才是列表改动的正题**：选中态不只是一个颜色。
     *
     * 改造前"当前在聊哪一段"**只有**那圈主色描边（`isCurrent` 只喂给了 border），
     * 对读屏用户等于不存在 —— 他听到一串标题，分不出哪个是当前会话。
     * 现在 `Surface(selected = isCurrent)` 把同一件事也说给读屏。
     *
     * 用例刻意**同时**断言两行：光断言"当前那行是选中的"会漏掉
     * "所有行都变成选中"这种更糟的 bug。
     */
    @Test
    fun `列表的当前会话带选中语义而非当前的不带`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Column {
                    conversations.forEach { item ->
                        ConversationRow(
                            item = item,
                            isCurrent = item.id == 3L,
                            onClick = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(conversations[0].title).assertIsSelected()
        composeRule.onNodeWithText(conversations[1].title).assertIsNotSelected()
        composeRule.onNodeWithText(conversations[2].title).assertIsNotSelected()
    }

    /**
     * 每一行的删除按钮必须**自报家门**。
     *
     * 列表里所有删除按钮长得一模一样，若读屏只念"删除这条对话"，
     * 用户无法知道即将删掉哪一段 —— 而删除是不可恢复的（消息级联删）。
     */
    @Test
    fun `每行的删除按钮报出自己要删哪一段`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Column {
                    conversations.forEach { item ->
                        ConversationRow(item = item, isCurrent = false, onClick = {}, onDelete = {})
                    }
                }
            }
        }

        conversations.forEach { item ->
            composeRule.onNodeWithContentDescription("删除对话：${item.title}").assertExists()
        }
    }
}
