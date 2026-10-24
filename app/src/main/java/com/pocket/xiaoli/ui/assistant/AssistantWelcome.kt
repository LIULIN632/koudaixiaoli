package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.agent.AgentPhase
import com.pocket.xiaoli.data.knowledge.AssistantTool
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.AiAvatar
import com.pocket.xiaoli.ui.theme.CourseColors
import com.pocket.xiaoli.ui.theme.CourseColorsDark
import com.pocket.xiaoli.ui.theme.CourseTextColors
import com.pocket.xiaoli.ui.theme.CourseTextColorsDark
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.miuixPress

/**
 * 鲤鱼的**空态欢迎页**。
 *
 * ## 为什么单独一个文件
 *
 * 它是一整套自成体系的视觉件（问候卡 + 建议问题大卡 + 两排胶囊），
 * 与消息区、输入区都不共享状态，唯一的外部输入是 `AgentPhase`（头像光环）
 * 与 `onPick`（点了哪个建议问题）。
 *
 * "空态"与"有消息"是互斥的两条分支，把其中一条分支的 246 行单独放，
 * 读整页时就不必在两者之间来回跳。
 *
 * 视觉基线在 `AssistantWelcomeAndListScreenshotTest`（`50/51` 两态、`53` 纯净、
 * `244` 窄屏），另有语义用例守住"八条问题一条不少"。
 */

/**
 * 一个建议问题。
 *
 * [tintIndex] 指向课程色板（`CourseColors` / `CourseColorsDark`）里的一个低饱和色。
 * **复用课程色板而不是另起一套**：那 15 个色本来就是为"低饱和马卡龙 + 明暗两版"挑的，
 * 8 套主题下都已经配好，另起一套迟早会跟主题打架。
 */
internal data class AssistantPrompt(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val tintIndex: Int,

    /**
     * 面性（`Icons.Filled.*`）变体，供鲜活皮肤的 `IconStyle.FILLED` 使用；
     * 极简皮肤仍用 [icon]（线性）。默认值 = [icon]，未登记时行为不变。
     *
     * ⚠️ 放在 [tintIndex] **之后**：上面 4 个字段是**位置传参**的，
     * 插在中间会让 5 条 prompt 的实参整体错位。
     */
    val iconFilled: ImageVector = icon,

    /**
     * 这条问题依赖哪些工具。
     *
     * 追问 chips 用它做**上下文排序**：本轮刚查过课表，就优先推荐与课表相关的问题；
     * 空态欢迎页不看这个字段。刻意空集合也合法 —— 表示"与具体工具无关"，
     * 排序时落到兜底位。
     */
    val relatedTools: Set<AssistantTool> = emptySet(),
)

/**
 * 建议问题的**唯一真源**。
 *
 * 两个消费方：空态欢迎页（前四条做大卡、其余收成小胶囊）与回答下方的追问 chips
 *（读 [AssistantPrompt.title] 与 [AssistantPrompt.relatedTools]）。
 * 刻意**不各写一份**：两处一旦漂移，用户会看到"空态推荐问 A、追问推荐问 B"，
 * 而且删掉某个问题时必然漏掉一处。
 *
 * ⚠️ 每一条都必须是**今天就能真答出来**的：`get_today_courses` / `get_next_course` /
 * `get_pending_todos` / `get_grades` / `get_academic_progress` / `find_classroom` /
 * `get_weather` 就是这个列表的全部依据。
 * 想加「明天有什么课」这类问题，先确认工具有对应的入参 —— 现在的课程工具只有
 * "今天"和"下一节"两种查询，问"明天"会被它答成今天（**比答不上来更糟**）。
 * 同理，「宿舍几点断电」「还差几分保研」属于校园知识库，而知识库当前是空的
 *（`assets/knowledge.json` 的 `docs: []`），放上去只会得到"知识库暂无内容"。
 *
 * 「我还差多少学分？」（2026-10 新增）满足这条：它读的是「服务 → 修课情况」留下的
 * **本地快照**，与「我绩点多少？」同构（都需要用户先查过一次）—— 没查过时会得到
 * 一句"先去修课情况查一次"，那是**可操作的指引**，不是"答不上来"。
 *
 * 新增一条时**必须同时填 [AssistantPrompt.relatedTools]**：追问排序靠它判断
 * "这条问题与本轮刚查的数据相不相关"，留空会让它永远只能当兜底项。
 */
internal val ASSISTANT_PROMPTS: List<AssistantPrompt> = listOf(
    // 副标题压在 8 个字以内：360dp 的双列卡内容宽约 130dp，12sp 下 11 个字会折成两行，
    // 同一行的两张卡就一高一低。短一句反而更有力。
    AssistantPrompt(Icons.Outlined.CalendarMonth, "今天有什么课？", "列出今天全部课程", 0, iconFilled = Icons.Filled.CalendarMonth, relatedTools = setOf(AssistantTool.TODAY_COURSES)),
    AssistantPrompt(Icons.Outlined.Schedule, "下节课在哪？", "含楼栋、楼层和教室", 3, iconFilled = Icons.Filled.Schedule, relatedTools = setOf(AssistantTool.NEXT_COURSE)),
    AssistantPrompt(Icons.Outlined.TaskAlt, "还有什么作业没做？", "按截止日期排好", 6, iconFilled = Icons.Filled.TaskAlt, relatedTools = setOf(AssistantTool.TODOS)),
    AssistantPrompt(Icons.Outlined.Grade, "我绩点多少？", "读的是本机存档的成绩", 9, iconFilled = Icons.Filled.Grade, relatedTools = setOf(AssistantTool.MY_GRADES)),
    AssistantPrompt(Icons.Outlined.School, "我还差多少学分？", "读修课情况留下的数据", 12, iconFilled = Icons.Filled.School, relatedTools = setOf(AssistantTool.ACADEMIC_PROGRESS)),
    AssistantPrompt(Icons.Outlined.MeetingRoom, "A101 在哪？", "在教室分布图里定位", 15, iconFilled = Icons.Filled.MeetingRoom, relatedTools = setOf(AssistantTool.CAMPUS_LOCATION)),
    // 天气两问：都落在 `get_weather` 的报告范围内（未来 12 小时 + 今明两天），
    // 不多不少 —— 「这周末下雨吗」这类问题工具答不了，所以不放。
    AssistantPrompt(Icons.Outlined.Umbrella, "今天要带伞吗？", "看未来 12 小时降水", 1, iconFilled = Icons.Filled.Umbrella, relatedTools = setOf(AssistantTool.WEATHER)),
    AssistantPrompt(Icons.Outlined.Thermostat, "明天穿什么？", "按明天的温度给建议", 4, iconFilled = Icons.Filled.Thermostat, relatedTools = setOf(AssistantTool.WEATHER)),
)

/**
 * 「我还能」里的能力入口：chip 上只写**名词**，点下去发的是完整问句。
 *
 * 与 [ASSISTANT_PROMPTS] 分开：那一份是"建议问句"（回答下方的追问 chips 也用），
 * 把名词混进去会让追问条读起来一半是问题、一半是功能名。
 *
 * ⚠️ [question] 必须落在工具能力内：考试安排走 `get_exams`（读本地录入，没录过会
 * 指引去「服务 → 考试安排」），校园卡与电费走 `find_campus_service`（匹配服务目录）。
 * 刻意**不放「空教室」**：那个工具只能告诉你入口，答不了"现在哪间空着"。
 */
internal data class AssistantAbility(
    val icon: ImageVector,
    val iconFilled: ImageVector,
    val label: String,
    val question: String,
    val tintIndex: Int,
)

internal val ASSISTANT_ABILITIES: List<AssistantAbility> = listOf(
    AssistantAbility(
        Icons.AutoMirrored.Outlined.EventNote,
        Icons.AutoMirrored.Filled.EventNote,
        "考试安排",
        "我什么时候考试？",
        13,
    ),
    AssistantAbility(Icons.Outlined.CreditCard, Icons.Filled.CreditCard, "校园卡", "校园卡怎么绑定？", 8),
    AssistantAbility(Icons.Outlined.FlashOn, Icons.Filled.FlashOn, "电费", "电费在哪交？", 10),
)

/**
 * 空态欢迎页。
 *
 * 版式参考 DeepSeek 的空态：**大图标 + 居中问候语 + 一句副标题**，然后是建议问题。
 * 居中是刻意的 —— 左对齐的标题在空页面上会显得像"没做完的列表"，
 * 而居中的问候更像一个入口。
 *
 * 建议问题分两层：前四条用双列大卡（两列正好两行），其后四条收成「也可以这样问」
 * 的一排小胶囊。八个同规格的大卡会在首屏连成一堵色墙，反而挑不出该点哪个 ——
 * 更早还试过纵向全宽 pill，既占满一屏又浪费横向空间，而且当时没接点击回调。
 * 再往下是「我还能」：考试安排、校园卡、电费三个能力入口，把工具里没被问到的部分
 * 也露出来；三层由重到轻，首屏不会一上来就全是同一种卡。
 *
 * ## 头像同样走 `AiAvatar`，并且接的是**全局此刻**
 *
 * 这里原先是一个手写的 `Box(64dp) + Text("🐟", 32sp)`。换成 [AiAvatar] 的理由与
 * 消息区那颗相同（同一个 Agent 不该有两套表现），但**状态粒度不同**：
 * 消息区的头像是**本轮**推导的，而空态没有"本轮"—— 它该反映的是
 * "鲤鱼此刻在不在干活"。这正是 [phase] 从外层传进来的原因
 *（`container.aiAvatarPhase`，与「我的」页那道光环同源）。
 *
 * 顺带一个真实收益：用户在**上一段对话**里提问、趁它还在跑时点了「新对话」，
 * 欢迎页会显示主题色的脉冲光环，而不是一副"什么都没发生"的静止画面。
 *
 * ⚠️ 标 `internal` 而非 `private`：这是**唯一**没有快照基线的鲤鱼主界面，
 * 而它承载着最大的那个视觉件（64dp 头像）—— 没有基线就只能闭着眼睛改。
 * `AssistantWelcomeAndListScreenshotTest` 用真实组件渲它。**不要改回 private。**
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantWelcome(
    phase: AgentPhase,
    onPick: (String) -> Unit,
    /**
     * 替换问候语第二行的一句话（来自用户在别处**戳了一下**鲤鱼时的闲聊）。
     *
     * 默认 null = 用原本那句能力说明。加参数而不是加分支：空态页因此仍然只有"一条副标题"，
     * 不会出现气泡与问候同时各说一句。
     */
    heroLine: String? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    // 建议问题取自**唯一真源**（回答下方的追问 chips 用的是同一份）。
    // 大卡只放前四条（两列正好两行），其余收成小胶囊：八张同规格的卡在首屏上
    // 连成一片色块，反而挑不出该点哪个 —— 参考软件也是"四张卡 + 一排 chip"的分层。
    val prompts = ASSISTANT_PROMPTS
    val cards = prompts.take(4)
    val more = prompts.drop(4)
    val colorful = currentIconPresentation().colorful

    Column(modifier = Modifier.fillMaxWidth().padding(top = skin.spaceXxl)) {
        // 问候区**收进一张卡**。
        //
        // 散在背景上时，头像、标题、副标题三块是三个独立元素，页面上没有"第一层信息"，
        // 整页读起来像"还没排版"。装进一张卡之后它们成为一个整体，页面立刻有了主次。
        //
        // 彩虹模式给卡面一层极淡的主色渐变与同色光晕：白底白卡在鲜活皮肤下只靠
        // 1dp 描边分层，视觉上太"平"；纯净 / 极简模式仍保持中性白卡。
        val heroShape = RoundedCornerShape(skin.radiusLarge)
        val heroBrush = if (colorful) {
            Brush.linearGradient(
                listOf(
                    colors.accentMain.copy(alpha = if (isDark) 0.26f else 0.16f),
                    colors.accentMain.copy(alpha = if (isDark) 0.10f else 0.05f),
                    colors.surfaceCard,
                )
            )
        } else null
        val heroShadow = if (colorful && skin.shadowBlur > 0.dp) {
            Modifier.dropShadow(
                shape = heroShape,
                shadow = Shadow(
                    radius = skin.shadowBlur,
                    spread = 0.dp,
                    color = colors.accentMain.copy(alpha = if (isDark) 0.28f else 0.14f),
                    offset = DpOffset(0.dp, skin.shadowOffsetY),
                ),
            )
        } else Modifier
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(heroShadow)
                .clip(heroShape)
                .then(if (heroBrush != null) Modifier.background(heroBrush) else Modifier.background(colors.surfaceCard))
                // 鲜活模式也给一圈主色描边：渐变比纯色更容易和页底糊在一起，
                // 尤其深色下 0.26 的主色底与页底明度接近，没有边界卡就"化"开了。
                .border(
                    width = 1.dp,
                    color = if (colorful) {
                        colors.accentMain.copy(alpha = if (isDark) 0.28f else 0.16f)
                    } else {
                        colors.borderDivider
                    },
                    shape = heroShape,
                )
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 头像与消息区同一个组件、同一套状态语言（皮肤/主题/Agent 阶段都由它负责）。
            // 尺寸 64dp 是"空态主视觉"的档位，与消息区那颗 44dp 的锚点不同用途。
            //
            // 鲜活皮肤在鱼身后面加一圈主色径向光晕：空态的卡片本身是浅色渐变，
            // 64dp 的头像单靠光环会"浮"在卡面上；那圈弥散光把它焊进卡里，
            // 也让第一眼落在鱼身上。外层的 height(64dp) 是刻意的 —— 光晕用
            // requiredSize 超出也不撑高版式（否则问候语会被挤下去 56dp）。
            Box(
                modifier = Modifier.height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (skin.shadowBlur > 0.dp) {
                    Box(
                        modifier = Modifier
                            .requiredSize(124.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(
                                        colors.accentMain.copy(alpha = if (isDark) 0.30f else 0.20f),
                                        colors.accentMain.copy(alpha = 0f),
                                    )
                                ),
                                shape = CircleShape,
                            )
                    )
                }
                AiAvatar(state = phase, size = 64.dp)
            }
            Spacer(Modifier.height(skin.spaceMd))
            Text(
                text = "你好，我是鲤鱼",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                text = heroLine ?: "课表、作业、成绩、天气、教室位置，都可以问我",
                fontSize = 13.sp,
                color = colors.textTertiary,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(skin.spaceMd))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                // ⚠️ 胶囊里放的是**产品事实**，不是编造的数据。
                // 参考软件那两枚（"中秋节""距国庆节 4 天"）是数据驱动的，
                // 而鲤鱼页手里没有那类数据 —— 与其编一个好看的数字，不如说一句真的。
                WelcomeChip(Icons.Outlined.Lock, "数据不出本机")
                WelcomeChip(Icons.Outlined.Block, "无广告 · 无追踪")
            }
        }

        Spacer(Modifier.height(skin.spaceXl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionCaption("试试这些问题")
            Spacer(Modifier.weight(1f))
            Text(
                text = "点一下直接问",
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(skin.spaceSm))

        // 双列网格：两两成行；单数个时最后一个独占一行
        cards.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = skin.spaceSm),
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                row.forEach { prompt ->
                    WelcomeCard(
                        prompt = prompt,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(prompt.title) },
                    )
                }
                // 补齐空位，保证单数行时左边的卡片不会被拉伸成整行宽
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (more.isNotEmpty()) {
            Spacer(Modifier.height(skin.spaceXs))
            SectionCaption("也可以这样问")
            Spacer(Modifier.height(skin.spaceSm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                more.forEach { prompt ->
                    QuickPromptChip(prompt = prompt, onClick = { onPick(prompt.title) })
                }
            }
        }

        if (ASSISTANT_ABILITIES.isNotEmpty()) {
            Spacer(Modifier.height(skin.spaceMd))
            SectionCaption("我还能")
            Spacer(Modifier.height(skin.spaceSm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                ASSISTANT_ABILITIES.forEach { ability ->
                    AbilityChip(ability = ability, onClick = { onPick(ability.question) })
                }
            }
        }
    }
}

/** 欢迎卡里的一枚小胶囊。**文字必须是事实**（隐私承诺 / 能力说明），不要放编造的数据。 */
@Composable
private fun WelcomeChip(icon: ImageVector, text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                color = colors.accentMain.copy(alpha = 0.10f),
                shape = shape,
            )
            .border(1.dp, colors.accentMain.copy(alpha = 0.16f), shape)
            .padding(horizontal = skin.spaceMd, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 一枚小图标让胶囊不再只是"一行小字"：两颗胶囊并排时，
        // 眼睛先认出图标再读字，两句话的分界更清楚。
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accentMain,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accentMain,
        )
    }
}

/**
 * 建议问题卡与胶囊共用的一套取色：浅底色 + 配套文字色。
 * 中性模式下退回卡片底 + 主色 —— 两处必须一致，否则同一条问题在大卡和小胶囊上会是两种颜色。
 */
private data class PromptTint(val background: Color, val foreground: Color)

@Composable
private fun promptTint(tintIndex: Int): PromptTint {
    val colors = LocalPocketXiaoliColors.current
    if (!currentIconPresentation().colorful) {
        return PromptTint(colors.surfaceCard, colors.accentMain)
    }
    val isDark = LocalDarkMode.current
    val palette = if (isDark) CourseColorsDark else CourseColors
    val textPalette = if (isDark) CourseTextColorsDark else CourseTextColors
    return PromptTint(
        background = palette[tintIndex % palette.size],
        foreground = textPalette[tintIndex % textPalette.size],
    )
}

/**
 * 「也可以这样问」里的小胶囊：图标 + 问句，点一下直接发问。
 *
 * 与上面的大卡同一份真源、同一套课程色板，只把信息压到一行高 —— 首屏的视觉重心
 * 留给四张卡，剩下的四条用密度换完整，既不折叠也不删。
 */
@Composable
private fun QuickPromptChip(
    prompt: AssistantPrompt,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val iconPresentation = currentIconPresentation()
    val tint = promptTint(prompt.tintIndex)
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(tint.background)
            .border(
                width = 1.dp,
                color = if (iconPresentation.colorful) {
                    tint.foreground.copy(alpha = 0.24f)
                } else {
                    colors.borderDivider
                },
                shape = shape,
            )
            // 44dp 是本项目可点元素的触摸下限（与回答下方的追问 chips 同一条口径）
            .heightIn(min = 44.dp)
            .miuixPress(onClick = onClick)
            .padding(horizontal = skin.spaceMd, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (iconPresentation.filled) prompt.iconFilled else prompt.icon,
            contentDescription = null,
            tint = tint.foreground,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = prompt.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * 「我还能」的能力胶囊：中性卡面 + 一枚彩色小图标块。
 *
 * 刻意和上面两排粉彩问句拉开：那两排是"问我"，这一排是"我还能做什么" ——
 * 用中性底 + 图标块而不是整块染色，层级一眼能分开。
 */
@Composable
private fun AbilityChip(
    ability: AssistantAbility,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val iconPresentation = currentIconPresentation()
    val colorful = iconPresentation.colorful
    val tint = promptTint(ability.tintIndex)
    val shape = RoundedCornerShape(skin.radiusChip)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.surfaceCard)
            .border(
                width = 1.dp,
                color = if (colorful) tint.foreground.copy(alpha = 0.18f) else colors.borderDivider,
                shape = shape,
            )
            .heightIn(min = 44.dp)
            .miuixPress(onClick = onClick)
            .padding(start = 6.dp, top = 6.dp, end = skin.spaceMd, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (colorful) tint.background
                    else tint.foreground.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (iconPresentation.filled) ability.iconFilled else ability.icon,
                contentDescription = null,
                tint = tint.foreground,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = ability.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun WelcomeCard(
    prompt: AssistantPrompt,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    // 图标 / 用色策略：皮肤上限 × 用户用色强度（见 IconPresentation.resolveIcons）。
    //
    // ⚠️ 这组欢迎卡此前**完全没有**受「用色强度」约束 —— 无论纯净还是彩虹都是彩色。
    // 而 `docs/DesignSystem.md` 写明纯净模式是"只有课程表多色，其余界面统一柔蓝白卡片"，
    // 所以那是个既有不一致。接上合成结果后，纯净模式（以及极简皮肤）下这组卡收成中性，
    // 与文档描述和历史行为对齐。
    val iconPresentation = currentIconPresentation()
    val colorful = iconPresentation.colorful
    val tint = promptTint(prompt.tintIndex)

    val shape = RoundedCornerShape(skin.radiusLarge)
    // 渐变让卡片从"一块单纯的浅色"变成有方向的色面；同色光晕把它从页面上托起来。
    // 纯净 / 极简仍是中性卡，必须补一圈描边 —— 否则白卡压在纯白页底上等于隐形。
    val cardBrush = if (colorful) {
        Brush.linearGradient(
            listOf(
                tint.background,
                lerp(tint.background, colors.surfaceCard, if (isDark) 0.30f else 0.55f),
            )
        )
    } else null
    val cardShadow = if (colorful && skin.shadowBlur > 0.dp) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = skin.shadowBlur,
                spread = 0.dp,
                color = tint.foreground.copy(alpha = if (isDark) 0.30f else 0.16f),
                offset = DpOffset(0.dp, skin.shadowOffsetY),
            ),
        )
    } else Modifier

    Box(
        modifier = modifier
            .then(cardShadow)
            .clip(shape)
            .then(if (cardBrush != null) Modifier.background(cardBrush) else Modifier.background(tint.background))
            .then(if (!colorful) Modifier.border(1.dp, colors.borderDivider, shape) else Modifier)
            .miuixPress(onClick = onClick),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 右下角的大号淡装饰图 —— 参考软件那种"贵"感主要来自这里。
            // 卡片原本是"图标 + 一行字"的白按钮；压上一个放大、几乎透明的图标之后，
            // 它立刻变成一张"设计过的卡"。刻意让它出血（offset 超出卡片）由容器裁掉一角，
            // 完整地待在卡片里反而像贴纸。
            //
            // 尺寸和出血量都从**卡片实际宽度**推出来（这组卡是双列 `weight(1f)`，
            // 宽度随屏幕变），原来写死的 `size(76.dp) + offset(16,12)` 在窄屏上占了卡宽的一半、
            // 在宽屏上又显得小气，而且比例和边框的相对位置会随屏漂移。
            // 内层容器同时补了 `fillMaxWidth`：水印该锚在**卡片**的右下角，
            // 而不是旁边那段标题文字的右边缘 —— 标题长短不同，位置就跟着晃。
            val watermark = maxWidth * 0.46f
            Icon(
                imageVector = if (iconPresentation.filled) prompt.iconFilled else prompt.icon,
                contentDescription = null,
                // 水印用色板配套的**文字色**淡描：底色已经是同一个色相，
                // 再拿它自己当水印就完全看不见了
                tint = tint.foreground.copy(alpha = if (isDark) 0.16f else 0.12f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = watermark * 0.21f, y = watermark * 0.16f)
                    .size(watermark),
            )
            Column(modifier = Modifier.padding(horizontal = skin.spaceMd, vertical = skin.spaceMd)) {
                // 圆角图标块：把图标从"一根线"变成"一块砖"，卡片的骨架就立起来了。
                // 彩色卡上用白底块（与「服务」页的彩色卡同一套语言），图标本身仍取色板文字色；
                // 中性卡上退回主色的淡底。
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .background(
                            if (colorful) colors.surfaceCard.copy(alpha = if (isDark) 0.92f else 0.90f)
                            else tint.foreground.copy(alpha = if (isDark) 0.24f else 0.20f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (iconPresentation.filled) prompt.iconFilled else prompt.icon,
                        contentDescription = null,
                        // 用色板配套的文字色，保证在对应的淡彩底上一定读得清
                        tint = tint.foreground,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.height(skin.spaceSm))
                Text(
                    text = prompt.title,
                    // 14sp 而非 15sp：双列卡在 360dp 屏上内容宽只有约 130dp，
                    // 9 个字的长问句（「还有什么作业没做？」）按 15sp 算正好越界、被裁成半个字。
                    // 降 1sp 后最长那条约 126dp，窄屏也能整句放下。
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = prompt.subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}
