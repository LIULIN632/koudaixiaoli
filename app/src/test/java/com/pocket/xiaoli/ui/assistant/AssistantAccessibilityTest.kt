package com.pocket.xiaoli.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.agent.ChatRole
import com.pocket.xiaoli.data.knowledge.AssistantSource
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 鲤鱼消息区的**无障碍门禁** —— 用真实的 `ChatTurn` 组件查语义树。
 *
 * ## 为什么必须有这条测试，而不是"看着还行"
 *
 * 消息区里最核心的两个控件（思考链药丸、工具脚注药丸）都是**自绘的**：
 * 它们不是 `Button`，而是 `Row + background + clickable`。自绘控件不会继承
 * Material 的任何无障碍保证，而它们恰好又是**唯一能展开过程信息的入口** ——
 * TalkBack 用户如果点不到、或者听不出这是个可展开的控件，就等于读不到推理与工具明细。
 *
 * ## 改造前实测（⚠️ 其中一条推翻了我最初的判断，记在这儿免得后人重踩）
 *
 * | 问题 | 实测 | 结论 |
 * |---|---|---|
 * | 药丸的触摸高度 | **45dp** | ✅ 达标。Compose 对 `clickable` **自动注入**了最小交互尺寸（`LocalMinimumInteractiveComponentSize`，默认 48dp）。我最初判它"内容 18dp + 上下 5dp = 28dp、低于 44dp"是**错的** —— 那个算法只算了视觉盒，没算自动注入的触摸层。这条留在文档里，是因为"自绘控件的触摸区必然不足"是个非常容易想当然的判断 |
 * | 两个 `CircularProgressIndicator` | **已有** `progressBarRangeInfo` | ✅ 达标（Material3 自带）。同一类"以为缺了"的误判 |
 * | 药丸**说不出自己是开还是关** | `clickable` 只给 `Role.Button`，**没有** `ToggleableState` | ❌ **真实缺口**。读屏念"思考了 12 秒，按钮"，按下去之后还是同一句，听不出过程内容到底展开了没有 |
 *
 * 所以这条用例的**主断言是状态语义**（`ToggleableState`），尺寸那两条降级为回归护栏：
 * 将来若把 `toggleable`/`clickable` 换成裸 `pointerInput`、或自己实现点击，
 * 自动注入的最小尺寸就没了，那时它们会红 —— 这正是护栏该干的事。
 *
 * ## 断言的是"语义"，不是"像素"
 *
 * 用的是 `hasToggleableState` / `assertHeightIsAtLeast(44.dp)` / `hasProgressBarRangeInfo`，
 * 而不是比对快照图 —— 视觉快照守的是"看起来对不对"，这条守的是"读屏与手指能不能用"，
 * 两者互补，缺一不可（`docs/UI验收手册.md` 第一节的"两条都要跑"）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun answer(
        text: String,
        reasoning: String = "",
        reasoningStartedAt: Long = 0L,
        reasoningEndedAt: Long = 0L,
        streaming: Boolean = false,
    ) = AssistantUiMessage(
        role = ChatRole.ASSISTANT,
        content = text,
        reasoning = reasoning,
        reasoningStartedAt = reasoningStartedAt,
        reasoningEndedAt = reasoningEndedAt,
        streaming = streaming,
    )

    private fun tool(
        name: String,
        sources: List<AssistantSource> = emptyList(),
    ) = AssistantUiMessage(
        role = ChatRole.TOOL,
        content = "查到 3 条",
        toolName = name,
        sources = sources,
    )

    private fun user(text: String) = AssistantUiMessage(ChatRole.USER, text)

    @Composable
    private fun Turn(
        messages: List<AssistantUiMessage>,
        runningTool: String? = null,
        streaming: Boolean = false,
    ) {
        PocketXiaoliTheme(
            appearanceMode = AppearanceMode.RAINBOW,
            skinStyle = SkinStyle.VIVID,
        ) {
            messages.toTurns().forEach { turn ->
                ChatTurn(
                    turn = turn,
                    runningTool = runningTool,
                    streaming = streaming,
                    onRetry = {},
                    onRegenerate = {},
                )
            }
        }
    }

    /**
     * 思考链药丸：**必须带"展开/收起"状态语义**，且点开后推理正文可被读屏读到。
     *
     * 这是这条用例的主断言。`clickable` 也"能点"，但它对读屏只说"按钮"；
     * 折叠栏的核心信息是**开还是关**，没有 `ToggleableState` 就等于这栏对读屏是黑盒。
     */
    @Test
    fun `思考链药丸带展开收起状态语义且可点开`() {
        composeRule.setContent {
            Turn(
                messages = listOf(
                    user("明天第一节在哪"),
                    // `reasoningEndedAt` 是**必需**的，不是凑数：思考链的摘要文字由它决定
                    //（有确切秒数才显示"思考了 N 秒"，否则只说"思考过程"）。
                    // 第一版漏了它，于是找 "思考过程" 这个节点直接找不到 —— 而失败信息
                    // 只说"没有匹配的节点"，看不出是文案分了三支。
                    answer(
                        "在 A-302。",
                        reasoning = "先查课表，定位第一节课。",
                        reasoningStartedAt = 1_000L,
                        reasoningEndedAt = 2_000L,
                    ),
                ),
            )
        }

        val pill = composeRule.onNodeWithText("思考了 1 秒")
        // ① 状态语义：折叠栏必须能说出自己是开还是关
        pill.assertIsToggleable()
        pill.assertIsOff()
        pill.assertHeightIsAtLeast(44.dp)

        // ② 点开之后：状态真的翻转，且推理正文进了语义树（可读屏朗读）
        pill.performClick()
        pill.assertIsOn()
        composeRule.onNodeWithText("先查课表，定位第一节课。").assertExists()

        // ③ 再点一次要收得回去（只验"能开"会漏掉"关不上"这类对称性 bug）
        pill.performClick()
        pill.assertIsOff()
    }

    /** 工具脚注药丸：同一条规矩（它也是折叠栏，同样只能靠状态语义表达开合）。 */
    @Test
    fun `工具脚注药丸带展开收起状态语义`() {
        composeRule.setContent {
            Turn(messages = listOf(user("帮我查成绩"), tool("我的成绩"), answer("查到 12 门。")))
        }

        val pill = composeRule.onNodeWithText("查了 1 项")
        pill.assertIsToggleable()
        pill.assertIsOff()
        pill.assertHeightIsAtLeast(44.dp)

        pill.performClick()
        pill.assertIsOn()
        composeRule.onNodeWithText("我的成绩").assertExists()
    }

    /**
     * 来源条：与两个过程药丸同一条规矩 —— 可展开、能说出开合状态、触摸高度达标。
     *
     * 来源是"回答凭什么这么说"的唯一入口，读屏用户如果打不开它，
     * 就等于只能相信正文里那句没有出处的结论。
     */
    @Test
    fun `来源条带展开收起状态语义且可点开`() {
        composeRule.setContent {
            Turn(
                messages = listOf(
                    user("图书馆几点开门"),
                    tool(
                        name = "校园知识库",
                        sources = listOf(
                            AssistantSource(
                                marker = "K1",
                                title = "图书馆开放时间",
                                meta = "更新于 2025-09-20",
                                snippet = "平时 07:00-22:00",
                            )
                        ),
                    ),
                    answer("平时 07:00-22:00 开放[K1]。"),
                ),
            )
        }

        val pill = composeRule.onNodeWithText("来源 1 条")
        pill.assertIsToggleable()
        pill.assertIsOff()
        pill.assertHeightIsAtLeast(44.dp)

        pill.performClick()
        pill.assertIsOn()
        composeRule.onNodeWithText("图书馆开放时间").assertExists()
    }

    /**
     * 进行中的两个转圈都必须是**可播报的进度条**。
     *
     * 改造前它们是裸的 `CircularProgressIndicator`：读屏不会播报"正在加载"，
     * 用户只听到一句孤立的"思考中…"，无法判断是"在跑"还是"卡住了"。
     *
     * 用 `hasProgressBarRangeInfo(Indeterminate)` 断言，而不是"有没有那个图标"：
     * 后者测的是实现，前者测的才是读屏能拿到的东西。
     */
    @Test
    fun `思考中的转圈是可播报的进度条`() {
        composeRule.setContent {
            Turn(
                streaming = true,
                messages = listOf(
                    user("明天第一节在哪"),
                    answer("", reasoning = "先查课表…", reasoningStartedAt = 1_000L, streaming = true),
                ),
            )
        }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun `查工具时的转圈也是可播报的进度条`() {
        composeRule.setContent {
            Turn(
                runningTool = "今日课表",
                messages = listOf(
                    user("明天有什么课"),
                    AssistantUiMessage(
                        role = ChatRole.TOOL,
                        content = "",
                        toolName = "今日课表",
                        id = RUNNING_TOOL_ID,
                    ),
                ),
            )
        }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeRule.onNodeWithText("正在查今日课表…").assertExists()
    }
}
