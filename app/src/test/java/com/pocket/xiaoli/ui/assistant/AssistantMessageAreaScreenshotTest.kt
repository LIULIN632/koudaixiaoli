package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.agent.ChatRole
import com.pocket.xiaoli.data.knowledge.AssistantCard
import com.pocket.xiaoli.data.knowledge.AssistantSource
import com.pocket.xiaoli.data.knowledge.AssistantTool
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
 * **消息区的视觉验收基线** —— 渲染真实的 `ChatTurn` 组件。
 *
 * ## 它为什么必须先于任何视觉改动存在
 *
 * 消息区是整个鲤鱼界面的**主战场**：对话 90% 的像素是这里的正文，
 * 而"排版 / 间距节奏 / 颜色克制"这三件决定好看与否的事全部发生在这一块。
 * 但在此之前**没有任何验收手段** —— golden 只覆盖了皮肤、服务卡与输入框，
 * 改消息区的排版等于闭着眼睛改。
 *
 * 这份基线一旦存在，后面这些改动才谈得上"改完能验收"：
 * 行高归一化、代码块样式、工具脚注三态、流式指示、错误卡片、暗色审计。
 *
 * ## ⚠️ 数据用**生产同一份**合并逻辑构造
 *
 * 用的是 `toTurns()` —— 与界面里跑的是同一个函数。若在测试里手工拼 `ChatTurn(...)`，
 * 就又回到"预览与实际漂移"的老问题（`DesignTokens.kt` 的 `LocalBaseColors` KDoc 的 KDoc 记着这个跟头）。
 *
 * ## 覆盖的状态
 *
 * 与"视觉改动"的风险面一一对应：正常轮 / 思考链展开中 / 工具执行中 / 工具失败 /
 * 整轮出错 / 暗色。
 *
 * ⚠️ **思考链的"展开"态靠 `reasoningStreaming = true` 取得**，不是加测试专用参数：
 * `ReasoningBar` 的展开状态是 `streaming || userExpandedByHand`（用户手点无法从外部注入），
 * 所以用"正在思考"这一真实状态去覆盖展开后的版式 —— 那是它最该被看到的样子。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AssistantMessageAreaScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val ANSWER = "**明天有 3 节课**，第一节在 A-302：\n" +
            "\n" +
            "- 08:00 高等数学 · A-302\n" +
            "- 10:00 大学物理 · B-105（实验）\n" +
            "- 14:00 线性代数 · A-201\n" +
            "\n" +
            "```kotlin\n" +
            "val next = courses.first()\n" +
            "```\n"
    }

    /** 一个渲染用例：文件名 + 明暗 + 消息序列 + 追加参数。 */
    private data class Case(
        val name: String,
        val dark: Boolean = false,
        val messages: List<AssistantUiMessage>,
        val runningTool: String? = null,
        val streaming: Boolean = false,
    )

    private fun user(text: String) = AssistantUiMessage(ChatRole.USER, text)

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
        preview: String,
        failed: Boolean = false,
        running: Boolean = false,
        card: AssistantCard? = null,
        sources: List<AssistantSource> = emptyList(),
    ) =
        AssistantUiMessage(
            role = ChatRole.TOOL,
            content = preview,
            toolName = name,
            toolFailed = failed,
            card = card,
            sources = sources,
            id = if (running) RUNNING_TOOL_ID else java.util.UUID.randomUUID().toString(),
        )

    /** 四轮对话：前两轮会被折起来，后两轮保持展开。 */
    private val longConversation = listOf(
        user("明天第一节在哪上课？"),
        answer("第一节在 **A-302**，08:00 开始，别迟到。"),
        user("图书馆几点开门？"),
        answer("平时 **07:00-22:00**，考试周延长到 23:30。"),
        user("我绩点多少？"),
        answer("你的平均绩点是 **3.42**。"),
        user("今天有什么课？"),
        answer("今天 3 节课：高数、大物、线代。"),
    )

    private val cases = listOf(
        // ① 正常一轮：用户气泡 + 无气泡正文（含加粗/列表/代码块）+ 轮末操作条
        Case(
            name = "40-turn-basic",
            messages = listOf(
                user("帮我看看明天的课表"),
                answer(ANSWER),
            ),
        ),
        // ② 思考链展开中（靠 streaming 取得展开态）+ 正文流式 + 呼吸点
        Case(
            name = "41-turn-reasoning-live",
            streaming = true,
            messages = listOf(
                user("明天第一节在哪上课？"),
                answer(
                    text = "第一节在 **A-302**。",
                    reasoning = "需要先查课表，确认明天第一节的课程与教室。" +
                        "课表里第一节是高等数学，教室字段是 A-302。",
                    reasoningStartedAt = 1_000L,
                    streaming = true,
                ),
            ),
        ),
        // ③ 工具执行中：脚注收起态显示"正在查…"
        Case(
            name = "42-turn-tools-running",
            streaming = true,
            runningTool = "今日课表",
            messages = listOf(
                user("明天有什么课"),
                tool("今日课表", "", running = true),
            ),
        ),
        // ④ 工具失败：收起态也必须带警示色 + 失败数，否则用户以为模型在胡编
        Case(
            name = "43-turn-tools-failed",
            messages = listOf(
                user("帮我查成绩"),
                tool("我的成绩", "查到 12 门课"),
                tool("成绩详情", "请求超时", failed = true),
                answer("成绩查询超时了，等一下再试。"),
            ),
        ),
        // ⑤ 整轮出错：错误卡片（短句 + 可展开详情 + 重试）
        Case(
            name = "44-turn-error",
            messages = listOf(
                user("明天有什么课"),
                AssistantUiMessage(
                    role = ChatRole.ASSISTANT,
                    content = "网络连接失败，请检查网络后重试",
                    isError = true,
                    errorDetail = "java.net.SocketTimeoutException: timeout\n" +
                        "HTTP 504 Gateway Timeout\n" +
                        "  at okhttp3.internal.connection.RealCall…",
                    needsRetry = true,
                ),
            ),
        ),
        // ⑥ 暗色
        Case(
            name = "45-turn-basic-dark",
            dark = true,
            messages = listOf(
                user("帮我看看明天的课表"),
                answer(ANSWER),
            ),
        ),
        // ⑦ 数据卡片：回答里夹一张**成绩统计卡**（绩点 / 均分 / 挂科三格）
        //
        // 这是"文本 + 数据卡片混排"那一版的基线：正文在上、卡片在下，
        // 两者同在回答气泡里（气泡是 2026-10 用户要求加回来的，见 `AssistantBubble` 的 KDoc）。
        // 卡片的数字与工具文本说的是同一份数据 —— 这里刻意让两者一致，不一致就是 bug。
        Case(
            name = "46-turn-grade-card",
            messages = listOf(
                user("我绩点多少？"),
                tool(
                    name = "成绩",
                    preview = "已存档 41 门课程，平均分 78.3，平均绩点 3.42，其中 2 门低于 60 分。",
                    card = AssistantCard.Grades(
                        stats = AssistantCard.Grades.Stats(
                            total = 41,
                            averageScore = 78.3,
                            averageGpa = 3.42,
                            failedCount = 2,
                        ),
                    ),
                ),
                answer("你的平均绩点是 **3.42**，平均分 78.3。有 2 门低于 60 分，建议先看这两门。"),
            ),
        ),
        // ⑧ 数据卡片：**培养方案进度卡**（要求 / 已获 / 还差三格 + 缺学分的类型明细）
        //
        // ⚠️ 这一格盯三件事，都只有看图才发现得了：
        // ① 标题里的「更新于…」有没有被挤掉（培养方案每学期会调，不写就是把旧要求当现行要求）；
        // ② 三格数字与下面几行的行高会不会把卡片撑得比气泡还宽（`InfoCell` 是等分三格）；
        // ③ 「还差 75.0」这种长数字在等分格里的换行情况。
        Case(
            name = "49-turn-academic-card",
            messages = listOf(
                user("我还差多少学分？"),
                tool(
                    name = "培养方案",
                    preview = "培养方案进度（3 小时前更新）：总要求 170 学分，已获 95.0 学分，" +
                        "还差 75.0 学分。缺学分的类型 2 个，未修/未过课程 6 门。",
                    card = AssistantCard.AcademicProgress(
                        stats = AssistantCard.AcademicProgress.Stats(
                            requiredCredit = "170",
                            earnedCredit = "95.0",
                            missedCredit = "75.0",
                            shortTypeCount = 2,
                            untakenCourseCount = 6,
                        ),
                        rows = listOf(
                            AssistantCard.AcademicProgress.Row(
                                title = "学科专业教育平台",
                                detail = "已获 36.5 / 要求 76.5",
                                tag = "缺 40.0 学分",
                            ),
                            AssistantCard.AcademicProgress.Row(
                                title = "通识必修课程模块",
                                detail = "已获 39.5 / 要求 45.5",
                                tag = "缺 6.0 学分",
                            ),
                        ),
                        updatedLabel = "3 小时前更新",
                    ),
                ),
                answer("你还差 **75.0 学分**。缺得最多的是「学科专业教育平台」（还差 40.0 学分）。"),
            ),
        ),
        // ⑨ 引用来源：回答正文保留 [K1]/[K2]，下方来源条默认收起
        //
        // 这一格盯的是"正文编号与来源条编号是否对得上"，以及来源条会不会把回答顶得太远。
        // 展开态见单独的 `引用来源展开截图`（折叠栏是用户手点的 rememberSaveable，
        // 展开态只能靠真实点击取得，与思考链/工具脚注同一理由）。
        Case(
            name = "52-turn-sources",
            messages = listOf(
                user("图书馆几点开门？"),
                tool(
                    name = "校园知识库",
                    preview = "[K1] 图书馆开放时间 / 更新于 2025-09-20 / 责任人：图书馆",
                    sources = listOf(
                        AssistantSource(
                            marker = "K1",
                            title = "图书馆开放时间",
                            meta = "来源：图书馆官网\n更新于 2025-09-20 · 责任人：图书馆",
                            snippet = "平时开放时间：周一至周五 07:00-22:00；考试周延长至 23:30。",
                        ),
                        AssistantSource(
                            marker = "K2",
                            title = "考试周延长开放通知",
                            meta = "来源：图书馆通知 · 更新于 2025-12-15",
                            snippet = "期末考试周期间，图书馆闭馆时间由 22:00 延长至 23:30。",
                            url = "https://www.example.edu.cn/library-hours",
                        ),
                    ),
                ),
                answer(
                    "图书馆平时 **07:00-22:00** 开放，考试周延长到 23:30[K1][K2]。\n\n" +
                        "建议考试周去之前再看一眼当天的通知。"
                ),
            ),
        ),
        // ⑩ 长对话折叠：4 轮时前两轮自动收成一行摘要，最近两轮保持展开
        //
        // 这一格盯的是「折叠后还认不认得出聊过什么」：摘要必须是用户原话，
        // 而且折叠条与展开轮之间的节奏不能被压平（否则看起来像一条分隔线）。
        Case(
            name = "55-turn-folded",
            messages = longConversation,
        ),
        // 暗色：折叠条用的是 surfaceCard + 主色浅描边，必须确认它在深底上仍有边界，
        // 而不是糊成一条与背景同色的横带
        Case(
            name = "57-turn-folded-dark",
            dark = true,
            messages = longConversation,
        ),
    )

    /** 与 `52-turn-sources` 同一份内容，只用于点开后的展开态基线。 */
    private val sourcesExpandedCase = cases.first { it.name == "52-turn-sources" }

    @Test
    fun `消息区各状态截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                MessageArea(case)
            }
        }
        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }

    /**
     * 把消息序列按**生产同款容器与间距**铺开。
     *
     * ## ⚠️ 必须用真的 `LazyColumn`，不能用 `Column(fillMaxSize())`
     *
     * 这不是风格问题，是**约束不同会导致渲染不同**：
     * `ReasoningBar` / `ToolFootnote` 里的竖线用 `Modifier.fillMaxHeight()`，
     * 它填的是**有界**的高度。`LazyColumn` 的 item 拿到的是**无界**高度约束 → 竖线贴合文字；
     * 而 `Column(fillMaxSize())` 的子项拿到有界高度 → **竖线被拉满整屏**。
     * （本测试第一版就是这么写的，渲出来的图里那条线一直垂到底 —— 是脚手架的缺陷，不是产品的。）
     *
     * 同理，轮间间距（`skin.spaceMd`）照抄 `AssistantScreen` 里 LazyColumn 的
     * `verticalArrangement` —— 基线不复现真实节奏，"轮间 vs 轮内"这类
     * 只靠对比才看得出的问题就会被抹平（而它正是排版改动的核心观察点）。
     */
    @Composable
    private fun MessageArea(case: Case) {
        val skin = LocalSkin.current
        val colors = LocalPocketXiaoliColors.current
        val rounds = case.messages.toTurns().toRounds()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase),
            contentPadding = PaddingValues(horizontal = skin.spaceLg, vertical = skin.spaceWide),
            // 轮距逐项算，与生产同一份策略（roundGapOf）—— 不在这里另写一套间距
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(items = rounds, key = { _, round -> round.key }) { index, round ->
                Column(modifier = Modifier.padding(top = roundGapOf(index).toDp())) {
                    FoldableRound(
                        index = index,
                        total = rounds.size,
                        round = round,
                        // 与生产一致：「正在查 / 正在答」只挂在最后一段对话上
                        runningTool = case.runningTool.takeIf { index == rounds.lastIndex },
                        streaming = case.streaming && index == rounds.lastIndex,
                        onRetry = {},
                        onRegenerate = {},
                        showReasoning = true,
                    )
                }
            }
        }
    }

    /**
     * 引用来源的**展开态**。
     *
     * 与 `47` / `48` 同一理由单独渲：折叠状态是 `rememberSaveable`，没法从外部注入，
     * 只能用真实点击把它打开 —— 顺便把"读屏能不能说出开合状态"与触摸高度一起守住。
     */
    @Test
    fun `引用来源展开截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                MessageArea(sourcesExpandedCase)
            }
        }

        val pill = composeRule.onNodeWithText("来源 2 条")
        pill.assertIsToggleable()
        pill.assertIsOff()
        pill.assertHeightIsAtLeast(44.dp)
        pill.performClick()
        pill.assertIsOn()

        composeRule.onNodeWithText("图书馆开放时间").assertExists()
        composeRule.onNodeWithText("打开原文").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/53-turn-sources-expanded.png")
    }

    /**
     * 「查了 N 项」展开后的**原始行**（P1：让"AI 查了真数据"被看见）。
     *
     * ⚠️ 这一张**只能单独渲**，不能靠上面那组整轮快照覆盖：脚注的展开态是用户手点的
     * `rememberSaveable`，无法从外部注入 —— 与思考链不同，那边有 `reasoningStreaming`
     * 这个**真实状态**可用。所以把表格抽成 `internal` 组件单独渲，
     * 与该手册里 `AccountDangerCard` 的处理一致。
     *
     * 第三个形状是 58 分：低于 60 的分数走 `danger` 色，正是这张表存在的理由（一眼看见挂科）。
     */
    @Test
    fun `成绩依据表格截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    GradeEvidenceTable(
                        rows = listOf(
                            AssistantCard.GradeRow("高等数学", "2024-2025-1", "92", "4.0"),
                            AssistantCard.GradeRow("大学物理", "2024-2025-1", "58", "0"),
                            AssistantCard.GradeRow("线性代数", "2024-2025-2", "77", ""),
                        ),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/47-grade-evidence.png")
    }

    /**
     * 回答下方的**追问 chips**（P1）。
     *
     * 与 `47` 同一理由单独渲：宿主是整页 `AssistantScreen`，而它要 ViewModel 与 DI 容器。
     * 这张图同时是"5 条候选词与空态欢迎页同源"的视觉证据 —— 它们取自 `ASSISTANT_PROMPTS`。
     */
    @Test
    fun `追问 chips 截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(vertical = 24.dp),
                ) {
                    FollowUpChips(onPick = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/48-followup-chips.png")

        // ⚠️ 顺带守触摸高度：chip 天然长得矮，而 44dp 是本项目的无障碍底线。
        // 这一层在截图上**完全看不出来**（12dp 与 14dp 的差别目测不出来）。
        composeRule.onNodeWithText("今天有什么课？").assertHeightIsAtLeast(44.dp)
    }

    /**
     * 上下文追问：本轮查过课表、且用户已经问过「今天有什么课？」之后，下方剩哪几条。
     *
     * 与 `48` 的差别是**数量与顺序都由 [FollowUpPlanner] 现算**：
     * 问过的那条被剔除，剩下的按候选词原顺序补足三条。
     * 单独留一张图，是为了让"推荐会随上下文变"这件事有视觉证据 ——
     * 只看 UI 代码看不出 5 条是怎么变成 3 条的。
     */
    @Test
    fun `上下文推荐追问截图`() {
        val prompts = FollowUpPlanner.plan(
            tools = setOf(AssistantTool.TODAY_COURSES),
            asked = listOf("今天有什么课？"),
        )
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(vertical = 24.dp),
                ) {
                    FollowUpChips(onPick = {}, prompts = prompts)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/54-followup-context.png")

        composeRule.onNodeWithText("下节课在哪？").assertHeightIsAtLeast(44.dp)
        composeRule.onNodeWithText("今天有什么课？").assertDoesNotExist()
    }

    /**
     * 旧轮次折叠：**折叠不是"删掉历史"**，而是收成一行可点开的摘要。
     *
     * 这条断言守的是四件事，全都没法只靠看图确认：
     * ① 折叠条带开合语义（读屏要能说出"已收起"）；
     * ② 触摸高度 ≥44dp；
     * ③ 收起时正文**真的不渲染**（不是透明度 0，否则读屏仍会念出来）；
     * ④ 点一下之后原文回来 —— 摘要条与正文能互换，而不是单向消失。
     *
     * 最近两轮不折：这里的第三、四轮在任何时刻都得是展开的。
     */
    @Test
    fun `旧轮次折叠展开截图`() {
        val folded = cases.first { it.name == "55-turn-folded" }
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                MessageArea(folded)
            }
        }

        val header = composeRule.onNodeWithText("明天第一节在哪上课？")
        header.assertIsToggleable()
        header.assertIsOff()
        header.assertHeightIsAtLeast(44.dp)
        // 收起的轮次正文不在组合树里 —— 历史仍可从摘要条点开
        composeRule.onNodeWithText("第一节在 A-302", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("平时 07:00-22:00", substring = true).assertDoesNotExist()
        // 最近两轮保持展开，否则用户一进来只看到两行标题，会以为回答没了
        composeRule.onNodeWithText("3.42", substring = true).assertExists()
        composeRule.onNodeWithText("今天 3 节课", substring = true).assertExists()

        header.performClick()
        // 展开后标题改成"收起第 N 轮"，所以要用新文案重新找那个开合节点
        composeRule.onNodeWithText("收起第 1 轮").assertIsOn()
        composeRule.onNodeWithText("第一节在 A-302", substring = true).assertExists()

        composeRule.onRoot().captureRoboImage("src/test/screenshots/56-turn-folded-expanded.png")
    }
}
