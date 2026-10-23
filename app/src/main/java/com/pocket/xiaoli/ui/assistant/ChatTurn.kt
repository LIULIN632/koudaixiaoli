package com.pocket.xiaoli.ui.assistant

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// ⚠️ 见 AssistantComposer.kt 的说明：`by` 委托用的 getValue/setValue 是运算符扩展，
// 按符号扫 import 扫不到，漏了会报 "has no method 'setValue'"。
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.agent.AgentPhase
import com.pocket.xiaoli.data.agent.ChatRole
import com.pocket.xiaoli.data.knowledge.AssistantCard
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.infocard.InfoCell
import com.pocket.xiaoli.ui.components.infocard.InfoRow
import com.pocket.xiaoli.ui.theme.AiAvatar
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.Motion
import com.pocket.xiaoli.ui.theme.motionAwareDuration
import com.pocket.xiaoli.ui.theme.PocketTypography
import kotlinx.coroutines.flow.filter

/**
 * 鲤鱼的**一轮对话**：数据模型 + 合并规则 + 各段渲染。
 *
 * ## 为什么单独一个文件
 *
 * 「什么算一轮」是整个消息区最核心的一条领域规则（多步任务会把 5~6 条消息
 * 合成一轮），而它的渲染件（气泡 / 思考链 / 工具脚注 / 操作条）全部只服务于这一条规则。
 * 它们原先与整页的滚动跟随、顶栏、欢迎页挤在 `AssistantScreen.kt`（1704 行）里，
 * 于是"改一轮的版式"和"改整页的滚动"要在同一个文件里互相干扰。
 *
 * 它和整页逻辑之间**没有共享状态**，切开边界是干净的。
 *
 * 纯移动，**逻辑一行未改**。验证方式是 `AssistantMessageAreaScreenshotTest`
 *（`40..45` 六态快照）+ `AssistantAccessibilityTest` + `MessageLayoutPolicyTest`
 * 三组在移动前后必须全绿。
 */
// ═══════════════════════════════════════════════════════════════════════════
//  一轮 = 一个条目
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 展开后的思考链 / 工具明细的**高度上限**。
 *
 * 288dp 借鉴 DSH 那个 `ui-conversation-folded` 插件（它用 288px）：约等于半屏多一点，
 * 够看清推理在说什么、又能让下面的答案始终留在视野里。
 *
 * 为什么必须有这个上限：一次推理动辄上千字。不限高的话，用户一旦展开，
 * 答案就被推出一屏之外 —— 那等于"展开过程"这个动作把结果藏了，
 * 与"折叠是为了让答案更好读"的初衷正好相反。超出的部分内部滚动。
 *
 * ⚠️ 拆分时它从 `AssistantScreen.kt` 搬到这里：**唯一的消费者是 [ReasoningBar] 与
 * [ToolFootnote]**（都在本文件）。留在屏幕文件里会让"改折叠区高度"要跨文件找常量。
 */
private val REASONING_MAX_HEIGHT = 288.dp

/**
 * 一条用户发言，或「一次回答 + 它调过的工具」。
 *
 * 存在的理由：多步任务（查课表 → 查教室 → 回答）此前会刷出 5~6 个独立气泡，
 * 真正那句话被夹在中间，用户得自己往下翻才能找到答案。岱宗盒子的做法是把工具
 * 折进回答末尾的脚注区 —— 这条值得抄，因为问题是真的。
 *
 * `internal`（而非 private）：合并规则是纯函数，必须能被单测直接覆盖 ——
 * "哪些消息算同一轮"正是最容易写错、又最难靠人眼在模拟器里验证的一环。
 */
internal data class ChatTurn(
    /** 稳定 key：取本 turn 第一条消息的 id（用户发言就是用户那条）。 */
    val key: String,
    val user: AssistantUiMessage?,
    /**
     * 本轮的思考链（可能为空 —— 服务商不回 `reasoning_content`，或者模型直接作答）。
     * 与正文分开：思考链要放在默认收起的折叠栏里，而正文是主体。
     */
    val reasoning: String,
    /**
     * 思考耗时（秒）。0 表示还在思考、或这一轮没有思考链。
     *
     * 取值方式与 DeepSeek 的摘要栏一致（"Thought for 21 seconds"）：
     * 有确切秒数才显示数字，没有就只说"思考过程"。
     */
    val reasoningSeconds: Int,
    /** 这一轮的思考链是否还在增长（界面据此显示"思考中…"）。 */
    val reasoningStreaming: Boolean,
    /** 本轮的全部助手文本，按发生顺序拼成一整段回答。 */
    val answer: String,
    val answerStreaming: Boolean,
    val tools: List<AssistantUiMessage>,
    val error: AssistantUiMessage?,
)

/** 把扁平的忠实历史切成「轮」：用户发言另起一轮，其余连续条目都归到同一轮。 */
internal fun List<AssistantUiMessage>.toTurns(): List<ChatTurn> {
    val turns = mutableListOf<ChatTurn>()
    var i = 0
    while (i < size) {
        val head = this[i]
        if (head.role == ChatRole.USER) {
            turns += ChatTurn(
                key = head.id,
                user = head,
                reasoning = "",
                reasoningSeconds = 0,
                reasoningStreaming = false,
                answer = "",
                answerStreaming = false,
                tools = emptyList(),
                error = null,
            )
            i++
            continue
        }
        // 连续的非用户消息 = 一轮
        val group = mutableListOf<AssistantUiMessage>()
        while (i < size && this[i].role != ChatRole.USER) {
            group += this[i]
            i++
        }
        val answers = group.filter { !it.isError && it.role == ChatRole.ASSISTANT }
        // 思考耗时：取本轮**第一段**的开始时刻与**最后一段**的结束时刻。
        // 用 max 而不是简单的 first/last：多段回答（说一句→调工具→再说）里，
        // 只有先开始、后结束的那一对才是有意义的区间
        val startedAt = answers.map { it.reasoningStartedAt }.filter { it > 0L }.minOrNull() ?: 0L
        val endedAt = answers.map { it.reasoningEndedAt }.filter { it > 0L }.maxOrNull() ?: 0L
        turns += ChatTurn(
            key = group.first().id,
            user = null,
            // 逐段拼接：一次回答可能被工具调用切成多段，每段各自带了那一段的思考链
            reasoning = answers.joinToString("") { it.reasoning },
            reasoningSeconds = if (startedAt > 0L && endedAt > startedAt) {
                ((endedAt - startedAt) / 1000L).toInt().coerceAtLeast(1)
            } else {
                0
            },
            reasoningStreaming = startedAt > 0L && endedAt == 0L,
            // 错误卡片自带正文，不要把它的文案重复拼进回答段
            answer = answers.joinToString("") { it.content },
            answerStreaming = group.any { it.streaming },
            tools = group.filter { it.role == ChatRole.TOOL },
            error = group.lastOrNull { it.isError },
        )
    }
    return turns
}

/**
 * 渲染一轮。版式自上而下：思考链折叠栏 → 用户气泡 → 回答正文 → 工具脚注 → 错误卡片。
 *
 * 关于顺序：模型可能"先说一句 → 调工具 → 再答"。刻意把两段文本**拼成一段**放在
 * 工具脚注**上方**，而不是严格按时间线交错 —— 交错会让一次回答看起来像两个气泡，
 * 正是"一轮一条目"要消灭的观感。代价是那句过程性的话（"让我查一下"）会和结论连在一起，
 * 这个代价可以接受：用户要的是"答案在哪"，不是"它当时怎么想的"。
 *
 * [runningTool] 只挂在最后一个 turn 上（它是"此刻在干什么"，不是历史事实）。
 *
 * ⚠️ 标 `internal` 而非 `private`，是为了让 `AssistantMessageAreaScreenshotTest`
 * 能用**真实组件**渲染各状态 —— 消息区（正文 / 思考链 / 工具脚注 / 错误卡片）
 * 是视觉改动的**主战场**，而没有快照基线就没有验收手段。
 * **不要改回 private。**
 */
@Composable
internal fun ChatTurn(
    turn: ChatTurn,
    runningTool: String?,
    streaming: Boolean,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    /** 是否展示思考链。来自「外观 → 鲤鱼 → 显示思考链」，关掉后不渲染 ReasoningBar。 */
    showReasoning: Boolean = true,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current

    if (turn.user != null) {
        UserBubble(turn.user)
        return
    }

    // 回答文本的纯文本版（复制用）：思考链与工具明细都不算"回答内容"，
    // 复制一段课表时把推理过程也带上会很奇怪
    val copyText = turn.answer

    // 本轮的头像状态。推导式（见 assistantAvatarPhase），所以历史轮恒为 IDLE、
    // 只有末轮会动 —— 不需要在这里额外判断"是不是最后一条"。
    val avatarPhase = assistantAvatarPhase(
        answerStreaming = turn.answerStreaming,
        hasError = turn.error != null,
        toolRunning = runningTool != null,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // 思考链在最上面：它是"回答之前发生的事"，放在正文下方会让人以为是补充说明
        // showReasoning 关闭时整块不渲染 —— 注意是"不渲染"而不是"折叠"：
        // 用户关掉说明他不想看到这段内容，留一个折叠条仍然会占位置、也要点一下
        if (showReasoning && turn.reasoning.isNotBlank()) {
            ReasoningBar(
                reasoning = turn.reasoning,
                seconds = turn.reasoningSeconds,
                streaming = turn.reasoningStreaming,
            )
            Spacer(Modifier.height(skin.spaceSm))
        }

        // 头像与正文同一行（头像只出现一次），见 AssistantBubble 的版式说明
        //
        // 本轮工具查到的**结构化数据**（目前只有成绩）在这里被取出来：它要渲染进**回答**里，
        // 与正文混排 —— 这正是"把数字从散文里解放出来"的地方。
        //
        // ⚠️ 卡片仍然挂在 TOOL 条目上（那是数据的出处，脚注的表格也从它取）。
        // 这里只是"同一份数据换个地方展示"，不是第二份数据。
        val cards = turn.tools.mapNotNull { it.card }
        if (turn.answer.isNotBlank() || turn.answerStreaming || cards.isNotEmpty()) {
            AssistantBubble(
                text = turn.answer,
                streaming = turn.answerStreaming,
                phase = avatarPhase,
                cards = cards,
            )
        } else if (avatarPhase != AgentPhase.IDLE) {
            // 还没吐字但确实在干活（正在查工具 / 已出错）：这时候**没有正文行**，
            // 头像就成了"它还活着"的唯一信号 —— 少了这一支，工具执行期间整轮是空白，
            // 用户会以为卡死了。
            AssistantAvatar(phase = avatarPhase)
        }

        val hasFootnote = turn.tools.isNotEmpty() || runningTool != null
        if (hasFootnote) {
            if (turn.answer.isNotBlank()) Spacer(Modifier.height(skin.spaceSm))
            ToolFootnote(tools = turn.tools)
        }

        turn.error?.let { error ->
            if (turn.answer.isNotBlank() || hasFootnote) Spacer(Modifier.height(skin.spaceSm))
            ErrorCard(
                title = "这次没答上来",
                message = error.content,
                detail = error.errorDetail,
                onRetry = onRetry.takeIf { error.needsRetry },
                modifier = Modifier.padding(start = assistantContentGutter()),
            )
        }

        // 轮末操作条：只在**这一轮已经定稿**时出现 —— 流式途中摆一个复制按钮，
        // 用户点了只会复制到半句话
        if (copyText.isNotBlank() && !turn.answerStreaming && !streaming) {
            TurnActions(
                onCopy = {
                    copyToClipboard(context, copyText)
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
                onRegenerate = onRegenerate,
            )
        }
    }
}

/**
 * 复制到剪贴板。失败不抛（部分机型在极端情况下会拒绝），只是没提示。
 *
 * `internal` 而非 private：代码块的复制按钮在 `MarkdownView` 里也要用它 ——
 * 两处各写一遍剪贴板代码，迟早有一处忘了 `runCatching`。
 */
internal fun copyToClipboard(context: android.content.Context, text: String) {
    runCatching {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("鲤鱼回答", text))
    }
}

/**
 * 用户发言。
 *
 * 版式抄 DeepSeek：**靠右的小胶囊**，用主色的**柔和底**（`accentMain` 低透明度）
 * 而不是反色实底。
 *
 * 为什么改成柔和底：反色实底（`inverseSurface`）是"强对比"手段，适合短标签；
 * 但用户的一句话可能有好几行，整块深色会显得很重、还会和右侧的助手回答抢注意力。
 * 柔和底既能标出"这是我的发言"，又不会在视觉上压过回答。
 *
 * 用 `accentMain` 而不是固定的浅蓝：8 套主题下主色不同（抹茶绿、樱花粉……），
 * 用户气泡跟着主色走才不会在换主题后变成一个突兀的色块。
 */
@Composable
private fun UserBubble(message: AssistantUiMessage) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    // 右下角收窄成"尖角"：气泡是说话的方向感，四角等圆会让问答双方读起来像两张卡片。
    // 只收一个角，另三角保持主题圆角 —— 极简皮肤下也不会破坏克制的形状语法。
    val shape = RoundedCornerShape(
        topStart = skin.radiusCard,
        topEnd = skin.radiusCard,
        bottomEnd = 6.dp,
        bottomStart = skin.radiusCard,
    )
    // 鲜活皮肤：主色由深到浅的渐变 + 同色光晕，让"这是我的发言"有明确的色面；
    // 极简皮肤保持纯色淡底，不引入装饰。
    val decorated = skin.shadowBlur > 0.dp
    val brush = if (decorated) {
        Brush.linearGradient(
            listOf(
                colors.accentMain.copy(alpha = if (isDark) 0.34f else 0.22f),
                colors.accentMain.copy(alpha = if (isDark) 0.16f else 0.08f),
            )
        )
    } else null
    val shadow = if (decorated) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = skin.shadowBlur,
                spread = 0.dp,
                color = colors.accentMain.copy(alpha = if (isDark) 0.28f else 0.16f),
                offset = DpOffset(0.dp, 2.dp),
            ),
        )
    } else Modifier
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(shadow)
                .clip(shape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(colors.accentMain.copy(alpha = 0.12f)))
                // 鲜活皮肤下补一圈同色描边：渐变底没有边界时，气泡右侧会"化"进页底。
                // 极简皮肤不描边 —— 它靠灰底白卡分层，额外描边会破坏克制感。
                .then(
                    if (decorated) {
                        Modifier.border(1.dp, colors.accentMain.copy(alpha = if (isDark) 0.30f else 0.16f), shape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = skin.spaceCompact, vertical = 11.dp),
        ) {
            Text(
                text = message.content,
                style = PocketTypography.body,
                color = colors.textPrimary,
            )
        }
    }
}

/**
 * 助手回答。
 *
 * ## ⚠️ 2026-10：气泡决策**反转过一次**，别再翻回去
 *
 * 原先这里是"刻意没有气泡"（理由是回答常是几百字的结构化内容，套一圈边框与底色会吃掉
 * 留白、代码块与气泡叠成两层框、整屏都是"块"）。那一版的**用户回执是"对话也加上气泡"** ——
 * 于是现在按用户要求加回来了，但把它做成了当初担心的问题的**解**而不是**来源**：
 *
 * - 气泡是**淡底**（`surfaceCard` → 极淡主色的渐变），不是一块实色板；
 *   鲜活皮肤再补一圈 1dp 主色低透明描边 + 同色光晕 —— 页底与卡色都近白，
 *   没有描边时气泡右缘会"化"进背景。极简皮肤保持纯色卡 + `borderDivider` 描边。
 * - 回答里的**内容块**（代码块 / 数据卡）用 `surfaceAlt` —— 与气泡**差一层色**。
 *   旧版真正的问题是"气泡与代码块同色"（那时气泡若也用 `surfaceAlt` 就会融在一起），
 *   解法是分层次，不是不要气泡。
 *
 * @param cards 本轮工具产出的结构化卡片（成绩统计 / 明细），渲染在正文**下方**：
 *   先给结论、再给数据，与用户"文本 + 数据卡片混排"的要求一致。
 */
@Composable
private fun AssistantBubble(
    text: String,
    streaming: Boolean,
    phase: AgentPhase,
    cards: List<AssistantCard> = emptyList(),
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val decorated = skin.shadowBlur > 0.dp
    // 左上角贴着头像那一侧收窄，让气泡"从头像长出来"；与用户气泡右下角的收角
    // 正好相对，两边的方向感才对得上。
    val shape = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = skin.radiusCard,
        bottomEnd = skin.radiusCard,
        bottomStart = skin.radiusCard,
    )
    // 回答气泡不是纯白一块：从卡色斜向过渡到极淡的主色，让"这是鲤鱼说的话"
    // 和用户气泡（主色更浓）在同一条色彩语言上，而不是一个彩色、一个白板。
    // 极简皮肤保持纯色卡 —— 它的分层规则是灰底白卡，渐变会破坏这套语法。
    val brush = if (decorated) {
        Brush.linearGradient(
            listOf(
                colors.surfaceCard,
                colors.surfaceCard,
                colors.accentMain.copy(alpha = if (isDark) 0.10f else 0.05f),
            )
        )
    } else null
    // 阴影用主色而不是黑色：白气泡压在近白的页底上时，中性黑影会发灰、显脏；
    // 同色淡光晕读起来像"浮起来"，也不会和用户气泡的阴影撞成两种颜色。
    val shadow = if (decorated) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 12.dp,
                spread = 0.dp,
                color = colors.accentMain.copy(alpha = if (isDark) 0.24f else 0.11f),
                offset = DpOffset(0.dp, 3.dp),
            ),
        )
    } else Modifier
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar(phase = phase)
        Spacer(Modifier.width(skin.spaceSm))
        Box(
            modifier = Modifier
                .weight(1f)
                .then(shadow)
                .clip(shape)
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(colors.surfaceCard))
                // Vivid 下页底与卡色都接近纯白，没有描边气泡会"融进背景"；
                // Minimal 是灰底白卡，描边只是加强轮廓，不会显重。
                .border(
                    1.dp,
                    if (decorated) {
                        colors.accentMain.copy(alpha = if (isDark) 0.24f else 0.14f)
                    } else {
                        colors.borderDivider
                    },
                    shape,
                ),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = skin.spaceCompact,
                    vertical = skin.spaceCompact,
                ),
            ) {
                if (text.isNotBlank()) {
                    // Markdown 渲染：模型的输出天然带加粗/列表/代码块，
                    // 以前是原样当纯文本显示（`**重点**` 直接露出星号），很影响观感
                    MarkdownText(text = text)
                }
                cards.forEach { card ->
                    if (text.isNotBlank()) Spacer(Modifier.height(skin.spaceSm))
                    AssistantCardBlock(card)
                }
                // 流式进行中：一个呼吸点，比「只等字出现」更明确
                if (streaming) {
                    Spacer(Modifier.height(skin.spaceSmall))
                    BreathingDot()
                }
            }
        }
    }
}

/**
 * 回答里的**数据块**（当前只有成绩）。
 *
 * ## 为什么不用 `InfoCard` 的容器
 *
 * `InfoCard` 的底色是 `surfaceCard` —— 而它现在就嵌在同样 `surfaceCard` 的回答气泡里，
 * 同色相叠等于没有卡片。这里只用它提供的**原语**（[InfoCell] / [InfoRow]），
 * 那正是 `infocard/InfoCard.kt` 的 KDoc 写明的用法（"本文件提供共享原语，不强行改造已有实现"）。
 *
 * ## 三个 stat 的取法
 *
 * 用户最在意的三个数字是**绩点 / 均分 / 挂科**（门数放进标题，它不需要占一格）。
 * 「挂科」在 0 门时是正向（success 色），>0 时是负向（danger）—— 这是整张卡的信息重点。
 */
@Composable
private fun AssistantCardBlock(card: AssistantCard) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    when (card) {
        is AssistantCard.Grades -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.radiusChip),
                color = colors.surfaceAlt,
            ) {
                Column(modifier = Modifier.padding(skin.spaceCompact)) {
                    card.stats?.let { stats ->
                        Text(
                            text = "已存档 ${stats.total} 门课程",
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(skin.spaceSm))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("平均绩点", stats.averageGpa?.let { "%.2f".format(it) } ?: "—")
                            InfoCell("平均分", stats.averageScore?.let { "%.1f".format(it) } ?: "—")
                            InfoCell(
                                label = "挂科",
                                value = if (stats.failedCount > 0) "${stats.failedCount} 门" else "无",
                                valueColor = if (stats.failedCount > 0) colors.danger else colors.success,
                            )
                        }
                    }
                    // 明细形态（问某门课时）：逐条「课程 · 学期 —— 分数」。
                    // 用 InfoRow 而不是自画表格：这里是**两列**，正是它的形状。
                    card.rows.forEach { row ->
                        InfoRow(
                            label = listOf(row.course, row.term)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            value = row.score + if (row.gpa.isNotBlank()) "  绩点 ${row.gpa}" else "",
                        )
                    }
                }
            }
        }

        is AssistantCard.AcademicProgress -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.radiusChip),
                color = colors.surfaceAlt,
            ) {
                Column(modifier = Modifier.padding(skin.spaceCompact)) {
                    card.stats?.let { stats ->
                        Text(
                            text = buildString {
                                append("培养方案进度")
                                // ⚠️ "更新于"不能省：培养方案每学期会调整，不写就是把旧要求当现行要求
                                if (card.updatedLabel.isNotBlank()) append(" · ").append(card.updatedLabel)
                            },
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(skin.spaceSm))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("要求学分", stats.requiredCredit.ifBlank { "—" })
                            InfoCell("已获学分", stats.earnedCredit.ifBlank { "—" })
                            InfoCell(
                                label = "还差",
                                value = stats.missedCredit.ifBlank { "—" },
                                valueColor = if ((stats.missedCredit.toFloatOrNull() ?: 0f) > 0f) {
                                    colors.accentMain
                                } else {
                                    colors.success
                                },
                            )
                        }
                        Spacer(Modifier.height(skin.spaceSm))
                        Text(
                            text = "缺学分的类型 ${stats.shortTypeCount} 个 · 未修/未过课程 ${stats.untakenCourseCount} 门",
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                    }
                    card.rows.forEach { row ->
                        AcademicProgressRow(row)
                    }
                }
            }
        }
    }
}

/**
 * 培养方案卡里的一行：**名称一行、说明一行**。
 *
 * ⚠️ 这里**不能**用 `InfoRow`：它是"左标签右值"的两列形状，标签那一列很窄，
 * 而培养方案的名字又长又必须完整（「学科专业教育平台」「通识必修课程模块」），
 * 于是 `学科专业教育平台 · 已获 36.5 / 要求 76.5` 会被折成三行、右侧标签孤零零挂在第一行
 * —— 基线图 `49-turn-academic-card` 就是这么发现的第一版。
 * 现在：名称单行截断、标签靠右，说明另起一行小字。
 */
@Composable
private fun AcademicProgressRow(row: AssistantCard.AcademicProgress.Row) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = PocketTypography.body,
                color = colors.textPrimary,
            )
            if (row.tag.isNotBlank()) {
                Spacer(Modifier.width(skin.spaceSm))
                Text(text = row.tag, style = PocketTypography.caption, color = colors.accentMain)
            }
        }
        if (row.detail.isNotBlank()) {
            Text(
                text = row.detail,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * 助手侧的小头像，给对话一个视觉锚点（之前完全没有「谁在说话」的暗示）。
 *
 * ## ⚠️ 必须走 `AiAvatar`，不要自己画一个圆形 + emoji
 *
 * 这里原先是一个手写的 `Box + background + Text("🐟")`，**静态**的。
 * 项目里已经有 `AiAvatar`（`ui/theme/AiAvatar.kt`），它把 `AgentPhase`
 * 渲染成光环颜色 + 呼吸节奏："正在查工具"时是 `info` 色的快脉冲、
 * "正在思考"时是主题色、"出错"时是红色 —— 而「我的」页的鲤鱼光环用的就是它。
 *
 * 也就是说同一个 Agent 在两个页面上有两套表现：切到「我的」页能看到它在跑，
 * 回到鲤鱼页反而变成一张静止的图。**状态只有一个真源（`AgentPhase`），
 * 表现也该只有一个。**
 *
 * ## 为什么状态是从本轮数据**推导**的，而不是直接读全局 phase
 *
 * 全局 `container.aiAvatarPhase` 描述的是"此刻"，而消息区要渲染的是
 * **每一条历史轮次**：给所有历史轮都套上"正在思考"的光环显然是错的。
 * 所以按本轮自己的字段推导（流式中 / 正在查工具 / 出错），推导规则见 [assistantAvatarPhase]。
 * 这样历史轮恒为 [AgentPhase.IDLE]，只有末轮会动 —— 也正好与
 * `runningTool` 只挂在末轮这条既有约定一致。
 */
@Composable
private fun AssistantAvatar(phase: AgentPhase) {
    AiAvatar(state = phase, size = AssistantAvatarSize)
}

/**
 * 本轮该显示哪个 Agent 状态。**纯函数，可单测**（见 `AssistantAvatarPhaseTest`）。
 *
 * 优先级：出错 > 查工具 > 思考 > 静息。理由：
 *
 * - **出错最优先**：它是需要用户动作的状态，被"正在思考"盖住就等于没提示；
 * - **查工具高于思考**：调工具的等待是另一种等待（`AiAvatar` 里它的脉冲更快），
 *   而工具执行中同时 `streaming=true` 是常态（正文还在流式），只看 streaming 会永远显示不出 TOOL；
 * - **只有"正文还在流"才算思考**：`reasoningStreaming` 期间正文还没开始，
 *   那属于"正在想"，用它会让光环在答案已经出来后还继续跳。
 */
internal fun assistantAvatarPhase(
    answerStreaming: Boolean,
    hasError: Boolean,
    toolRunning: Boolean,
): AgentPhase = when {
    hasError -> AgentPhase.ERROR
    toolRunning -> AgentPhase.TOOL
    answerStreaming -> AgentPhase.THINKING
    else -> AgentPhase.IDLE
}

/** 呼吸点：流式生成中的轻量指示。用透明度循环而不是转圈，避免视觉噪声。 */
@Composable
private fun BreathingDot() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val transition = rememberInfiniteTransition(label = "breath")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // 系统要求"减少动画"时时长取 0 ⇒ 三个点停在初始透明度（仍是"正在生成"的
            // 静态指示，但不闪）。这是本 App 里唯一一处"用动画表达状态"的地方，
            // 关掉动画后语义不能丢 —— 所以是**停住**而不是隐藏。
            animation = tween(motionAwareDuration(Motion.breath)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .padding(end = skin.spaceXs)
                    .size(5.dp)
                    .alpha(if (index == 0) alpha else alpha * (1f - index * 0.25f))
                    .background(colors.accentMain, CircleShape)
            )
        }
    }
}

/**
 * 轮末操作条：复制 / 重新生成。
 *
 * 抄的是 DeepSeek 的做法：动作放在**回答末尾**、默认只是几个淡灰细图标
 * （鼠标悬停才明显），不抢正文的注意力。以前这一排完全没有 ——
 * 用户想复制一段课表只能手动长按选中，想重答只能自己再打一遍。
 *
 * 只放两个动作，刻意不抄赞/踩：本项目约束是**禁止统计与埋点**，
 * 收不到反馈的赞踩按钮是纯装饰（点了没任何后果，反而让人以为坏了）。
 */
@Composable
private fun TurnActions(
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val shape = RoundedCornerShape(50)
    // 操作条收进一颗浅色胶囊：以前两个灰图标直接漂在空白里，既不知道是一组，
    // 也看不出"能点"（本项目对可点元素的纪律是**要有边界**）。
    // 但仍然是低对比的中性底，不抢正文 —— 它只是回答读完之后才需要的次要动作。
    Row(
        modifier = Modifier
            .padding(
                // 胶囊左边缘与回答气泡对齐（气泡起点 = 内容列缩进）。
                // 图标字形在自己的 44dp 触摸区里居中，不再需要旧的"半个宽度差"补偿。
                start = assistantContentGutter(),
                top = skin.spaceSm,
            )
            .clip(shape)
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderDivider.copy(alpha = 0.7f), shape)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ActionIcon(Icons.Outlined.ContentCopy, "复制回答", onCopy)
        if (onRegenerate != null) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(colors.borderDivider)
            )
            ActionIcon(Icons.Outlined.Refresh, "重新生成", onRegenerate)
        }
    }
}

/** 操作条上的一个淡灰细图标。触摸区比字形大得多，避免点空。 */
@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = Modifier
            // ⚠️ 触摸区必须 ≥44dp（Material 无障碍下限，本项目 lint 也查这条规则）。
            // 改造前是 `.size(28.dp)` —— 字形只有 15dp，28dp 的点按区在拇指下很容易点空。
            // 字形仍在 44dp 里居中，所以**视觉尺寸没变**，只是能点中的范围变大了。
            .size(AssistantIconTouchSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            // textSecondary：浅色下 textTertiary 只有 5:1，图标 15dp 太小，
            // 从"淡淡的装饰"提到"明确的按钮"。
            tint = colors.textSecondary,
            modifier = Modifier.size(AssistantIconGlyphSize),
        )
    }
}

/**
 * 思考链折叠栏 —— 抄 DeepSeek 的「Thought for N seconds」药丸。
 *
 * 视觉语法（这几个细节一起构成"看一眼就知道这是过程、不是答案"）：
 * - 收起态是一颗**无边框的浅灰药丸**，只有一个小图标 + 一行小字 + 箭头
 * - 摘要文字带**耗时**："思考了 12 秒"。这是这一栏最有用的一处信息 ——
 *   用户等的就是这个过程的耗时，不给数字他只会觉得"卡了很久"
 * - 展开后正文**缩进**，左侧一条**竖向细线**把过程与答案接起来。
 *   这条线是关键：没有它，展开的思考链会和下面的答案糊成一片，分不清哪段是过程
 * - 折叠区**有界高度**（[REASONING_MAX_HEIGHT] + 内部滚动）：一次推理可能上千字，
 *   不限高的话展开它会把底下的答案整个推出屏幕，用户得先滚过一整屏推理才看得到结论
 *
 * **答案开始流式时自动收起**：过程已经讲完了，这时把屏幕让给答案。用户手动展开过
 * 就不再自动收（[userToggled]）—— 他明确想看的时候不该被系统改回去。
 */
@Composable
private fun ReasoningBar(
    reasoning: String,
    seconds: Int,
    streaming: Boolean,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    // 用户是否亲手展开过。它是**唯一**的状态 —— 展开与否是推导出来的：
    //   「还在思考」= 一直展开（正是想看的时刻），否则看用户意愿。
    //
    // 为什么不用 "var expanded + var userToggled" 两个状态互相同步：
    // 两个可保存的 flag 在进程恢复（rememberSaveable 还原）时可能出现
    // "expanded=true 但 userToggled=false" 这种矛盾组合，下一个事件就会把它收掉。
    // 推导式没有这种中间态。
    var userExpandedByHand by rememberSaveable { mutableStateOf(false) }
    val expanded = streaming || userExpandedByHand

    Column(modifier = Modifier.padding(start = assistantContentGutter())) {
        // 药丸本体：只有点击区域，没有边框（边框会让它看起来像一个"内容卡片"）
        //
        // ⚠️ 用 `toggleable` 而不是 `clickable`：这是一颗**可展开的折叠栏**，
        // 而 `clickable` 只告诉读屏"这是个按钮"，**说不出它现在是开还是关** ——
        // 视障用户听到的是"思考了 12 秒，按钮"，按下去之后听到的还是同一句，
        // 根本无法判断过程内容到底展开了没有。
        // `toggleable` 会带上 `ToggleableState` 语义（读到"已展开/已收起"），
        // 并且它回传的是**目标状态**而不是"切一下"，所以这里不能再用 `!expanded`。
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                // 思考条从"灰色药丸"改成主色浅底：它和下面的回答是同一次生成的两段，
                // 用同一套色相把它们串起来，而不是让过程看起来像另一套系统。
                // 正在思考时底色与描边再浓一档，和静息的历史思考区分开。
                .background(
                    colors.accentMain.copy(
                        alpha = when {
                            streaming -> if (isDark) 0.20f else 0.11f
                            else -> if (isDark) 0.12f else 0.06f
                        }
                    )
                )
                .border(
                    1.dp,
                    colors.accentMain.copy(
                        alpha = when {
                            streaming -> if (isDark) 0.42f else 0.28f
                            else -> if (isDark) 0.24f else 0.14f
                        }
                    ),
                    RoundedCornerShape(50),
                )
                .toggleable(
                    value = expanded,
                    role = Role.Button,
                    onValueChange = { userExpandedByHand = it },
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (streaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = colors.accentMain,
                    trackColor = colors.accentMain.copy(alpha = 0.2f),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = colors.accentMain,
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceSmall))
            Text(
                text = when {
                    streaming -> "思考中…"
                    seconds > 0 -> "思考了 $seconds 秒"
                    else -> "思考过程"
                },
                style = PocketTypography.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.width(skin.spaceXs))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = colors.accentMain,
                modifier = Modifier.size(14.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(Motion.enter)) + expandVertically(tween(Motion.expand)),
            exit = fadeOut(tween(Motion.exit)) + shrinkVertically(tween(Motion.expand)),
        ) {
            Row(modifier = Modifier.padding(top = skin.spaceSm)) {
                // 竖向细线：把"过程"和下面的"答案"接起来（DeepSeek 同款语法）。
                // 用主色淡线而不是中性分割线 —— 它要与上方那颗主色药丸连成一条"过程"轴。
                Box(
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(colors.accentMain.copy(alpha = 0.30f))
                )
                Spacer(Modifier.width(skin.spaceMd))
                Text(
                    text = reasoning,
                    modifier = Modifier
                        // 有界高度 + 内部滚动：不限高的话，上千字的推理会把答案推出屏幕
                        .heightIn(max = REASONING_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                        .padding(end = skin.spaceXs),
                    // caption 档 = 12/18。改造前写的是 12/**19** —— 那个 19 是顺手写的，
                    // 同级的行高就是这么散成 6 种（16/17/18/19/20/22）的
                    style = PocketTypography.caption,
                    // textSecondary 而非 textTertiary：思考过程是用户主动展开要读的内容，
                    // 不是装饰性说明；12sp 再用弱灰就太吃力了。
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 工具脚注：**默认收起**的一行灰小字，展开才看细节。
 *
 * 版式抄 DeepSeek 的思考药丸：一个小图标 + 一行小字 + 箭头，无边框、
 * 浅灰底、圆角全满。这样它和上方的思考药丸在同一视觉层级上，
 * 一眼就能看出"这些是过程，答案在下面"。
 *
 * 为什么默认收起：一个多步任务能调 3~4 个工具，每个工具的结果摘要
 * 都铺出来会把回答淹掉 —— 那正是改造前的问题。用户真正要的是答案，
 * "它查了什么"应该是可核查的**依据**，不是主体。
 *
 * 但**失败必须漏出来**：只要有一个工具执行失败，收起态也带警示色 + 失败数，
 * 否则"查询失败"会被折叠藏起来，用户会以为模型在胡编。
 */
@Composable
private fun ToolFootnote(tools: List<AssistantUiMessage>) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    // 默认收起；展开状态按轮记忆（rememberSaveable 让横竖屏切换/进程恢复都不丢）
    var expanded by rememberSaveable { mutableStateOf(false) }

    // 正在执行的工具是列表里的一个临时条目（固定 id），
    // 所以"是不是在转圈"和"查了几项"都从 tools 推导，不必再从外部传一个 runningTool。
    // 这样两处状态不可能不同步 —— 以前它们分居 _state 与消息列表两边。
    val runningTool = tools.lastOrNull { it.id == RUNNING_TOOL_ID }?.toolName
    // 「正在查」那条只是进行中的占位，不该被算进"查了 N 项"
    val doneCount = tools.count { it.id != RUNNING_TOOL_ID }
    val failedCount = tools.count { it.toolFailed }

    Column(modifier = Modifier.padding(start = assistantContentGutter())) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                // 与思考条同一套主色浅底：它们都是"过程"，答案才是主体。
                // 但底色比思考条更淡一档，避免两条同时出现时互相抢。
                .background(colors.accentMain.copy(alpha = if (isDark) 0.10f else 0.05f))
                // ⚠️ 失败时的"警示色"由**这一圈描边**承载，而不是把 warning 当文字色。
                // 原因是实测的：`warning`(#FF9800) 在浅色主题的浅底上只有 **1.96:1**
                //（见 ThemeContrastTest）—— 而且它是构造函数的默认值，**8 套配色一套都没覆盖**，
                // 暗色底上约 9:1、浅色底上不到 2:1。拿它当文字色，等于
                // "本意是让失败漏出来、结果让它读不出来"。
                // 中性底 + 彩色描边是 `ErrorCard` 早就在用的处理方式，这里对齐它。
                .border(
                    width = 1.dp,
                    color = if (failedCount > 0) {
                        colors.warning.copy(alpha = 0.35f)
                    } else {
                        colors.accentMain.copy(alpha = if (isDark) 0.24f else 0.14f)
                    },
                    shape = RoundedCornerShape(50),
                )
                .toggleable(
                    value = expanded,
                    role = Role.Button,
                    enabled = tools.isNotEmpty(),
                    onValueChange = { expanded = it },
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (runningTool != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = colors.accentMain,
                    trackColor = colors.accentMain.copy(alpha = 0.2f),
                )
            } else {
                Icon(
                    imageVector = if (failedCount > 0) Icons.Outlined.WarningAmber else Icons.Outlined.Search,
                    contentDescription = null,
                    tint = if (failedCount > 0) colors.warning else colors.accentMain,
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceSmall))
            Text(
                text = when {
                    runningTool != null -> "正在查$runningTool…"
                    failedCount > 0 -> "查了 $doneCount 项 · $failedCount 项失败"
                    else -> "查了 $doneCount 项"
                },
                style = PocketTypography.caption,
                // 失败时用 textPrimary 而不是 warning：把"这次失败了"读清楚是首要的，
                // 颜色信号由同行的告警图标与外面那圈描边承担（理由见上面的 border 注释）
                color = if (failedCount > 0) colors.textPrimary else colors.textSecondary,
            )
            if (tools.isNotEmpty()) {
                Spacer(Modifier.width(skin.spaceXs))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起查询详情" else "展开查询详情",
                    tint = colors.accentMain,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(Motion.enter)) + expandVertically(tween(Motion.expand)),
            exit = fadeOut(tween(Motion.exit)) + shrinkVertically(tween(Motion.expand)),
        ) {
            // 与思考链同一套语法：左侧竖线 + 缩进内容 + 有界高度（超出内部滚动）
            Row(modifier = Modifier.padding(top = skin.spaceSm)) {
                Box(
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(colors.accentMain.copy(alpha = 0.30f))
                )
                Spacer(Modifier.width(skin.spaceMd))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // 工具多了（一次多步任务能调 4 个）同样不能无限撑高
                        .heightIn(max = REASONING_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                ) {
                    tools.forEach { tool -> ToolDetailRow(tool) }
                }
            }
        }
    }
}

/**
 * 展开后的一行工具明细：名字 + **真实数据**（有卡片就给表格，否则退回结果摘要）。
 *
 * ## 为什么要给表格（2026-10 用户要求）
 *
 * 原先把工具返回的那句摘要（`maxLines = 2`）铺出来了事。对"AI 查了真数据"这件事来说，
 * 那句话是**模型视角的转述**：用户看到的还是"它说它查了"，而不是"我亲眼看见那几行分数"。
 * 有卡片时可核查的原始行（课程 / 学期 / 分数）直接列出来，信任感来自**可核对**，
 * 不是来自措辞。
 *
 * 仍然保持 `maxLines = 2` 的**摘要**作为无卡片工具的展示 —— 那类工具的排版价值就在文本里。
 */
@Composable
private fun ToolDetailRow(tool: AssistantUiMessage) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isRunning = tool.id == RUNNING_TOOL_ID
    val rows = (tool.card as? AssistantCard.Grades)?.rows.orEmpty()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = skin.spaceXs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 失败信号走**图标**而不是文字色 —— 理由同工具脚注：
            // `warning` 在浅色主题上只有 1.96:1，当文字色读不出来
            if (tool.toolFailed) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(skin.spaceMicro))
            }
            Text(
                text = tool.toolName ?: "工具",
                style = PocketTypography.caption,
                fontWeight = FontWeight.Medium,
                color = if (tool.toolFailed) colors.textPrimary else colors.textSecondary,
            )
            if (rows.isNotEmpty()) {
                Spacer(Modifier.width(skin.spaceMicro))
                Text(
                    text = "${rows.size} 条",
                    style = PocketTypography.caption,
                    color = colors.textTertiary,
                )
            }
        }
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(skin.spaceMicro))
            GradeEvidenceTable(rows)
        } else if (tool.content.isNotBlank()) {
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                text = tool.content,
                style = PocketTypography.caption,
                color = colors.textSecondary,
                maxLines = 2,
            )
        } else if (isRunning) {
            Spacer(Modifier.height(skin.spaceMicro))
            Text("执行中…", style = PocketTypography.caption, color = colors.textSecondary)
        }
    }
}

/**
 * 「查到了什么」的原始行：课程 / 学期 / 分数。
 *
 * ⚠️ 刻意**不复用** `InfoRow`：它是「弱色标签 —— 主色值」的两列语义（第一列本来就该淡），
 * 而表格里第一列（课程名）才是主体。用它会得到一个"课名是灰的、分数是黑的"的怪表。
 * 阈值 60 的判定与 `AssistantFormatters.grades` 里算挂科数用的是同一条线。
 *
 * ⚠️ 标 `internal` 而非 `private`：它的宿主（工具脚注）的展开态是用户手点的
 * `rememberSaveable`，**无法从外部注入** —— 只能把它单独抽出来渲。
 * 与 `AccountDangerCard` 抽出来做基线是同一个理由（见 `docs/UI验收手册.md`）。
 */
@Composable
internal fun GradeEvidenceTable(rows: List<AssistantCard.GradeRow>) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.course,
                    style = PocketTypography.caption,
                    color = colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1.6f),
                )
                Text(
                    text = row.term.ifBlank { "—" },
                    style = PocketTypography.caption,
                    color = colors.textTertiary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.score,
                    style = PocketTypography.caption,
                    fontWeight = FontWeight.Medium,
                    // 低于 60 用 danger：一眼看见挂科正是这张表存在的理由
                    color = if ((row.score.trim().toFloatOrNull() ?: 100f) < 60f) colors.danger else colors.textPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.8f),
                )
            }
        }
    }
}
