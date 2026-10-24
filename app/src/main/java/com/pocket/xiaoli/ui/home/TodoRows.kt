package com.pocket.xiaoli.ui.home

// ═══════════════════════════════════════════════════════════════════════
// 首页「待办区」的行组件
//
// 从 HomeScreen.kt 提取而来。触发原因很具体：加 ExamRow 后那个文件涨到 853 行，
// 越过设计体系 lint 的 800 行基线 —— 检查拦住了"巨型文件"的回潮。
//
// ## 2026-10：三种行合并成两种
//
// 用户口径是「首页把创建任务和作业改成一个，只保留待办」。所以：
// - `TaskRow`（自定义待办）与 `HomeworkRow`（作业）合并为同一个 [TodoRow]：
//   主标题 + 可选副标题（课程）+ 可选截止日期。界面上**不再出现「作业」二字**。
// - 行内的 `AddTaskRow`（输入框 + 圆按钮）删除 —— 新增入口统一到首页右下角的 FAB。
// - [ExamRow] 保持不变：考试有硬性时间点、错过无法补，它不是"待办"的一种。
//
// ⚠️ **存储层仍是两张表**（`tasks` / `homework`），这是存储细节、不是产品概念：
// `tasks` 表没有截止日期字段，所以「带截止日期的待办」落进 `homework` 表
//（那张表本来就是"带截止日期的待办"的既有存储，见 `HomeworkEntity`）。
// 想真正合并成一张表要先加 Room 迁移，那是另一个量级的改动，本次没做。
// ═══════════════════════════════════════════════════════════════════════

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.focus.pomodoroCountText
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.Motion
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.util.EXAM_SOON_DAYS
import com.pocket.xiaoli.util.examCountdownText
import com.pocket.xiaoli.util.examDaysLeft
import java.time.LocalDate
import kotlin.math.abs

/**
 * 首页待办区的**唯一**一行。
 *
 * @param title 待办本身（作业的正文 / 自定义待办的名字）
 * @param detail 副标题，目前只用来放课程名；空串则不占位
 * @param dueDate 截止日期，空串表示没有（自定义待办常态）
 * @param onStartFocus 对这条待办起一个番茄钟；null = 这一行不提供专注入口。
 *   只有**自定义待办**（`tasks` 表）传值：作业（`homework` 表）的 id 与
 *   `tasks.id` 是两套编号，拿它当 `focus_sessions.linkedTaskId` 会串表
 *   （P1 起钟时回写只针对 `tasks`）。
 * @param focusCount 这条待办已完整跑完的番茄数，0 就不显示那一行。
 *   来自 `focus_sessions` 的派生统计（`FocusSessionDao.observeCompletedByTask`），
 *   不是存在待办自己身上的字段。
 */
@Composable
internal fun TodoRow(
    title: String,
    detail: String,
    dueDate: String,
    completed: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onStartFocus: (() -> Unit)? = null,
    focusCount: Int = 0,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    SoftCard(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceCard,
        contentPadding = PaddingValues(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TodoCheck(
                checked = completed,
                label = title,
                onToggle = onToggle,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = title,
                    style = PocketTypography.cardTitle,
                    fontWeight = FontWeight.Medium,
                    // 已勾掉的行只可能在「专注」页的基线里出现（首页只列未完成），
                    // 但状态得是完整的：划掉 + 降级，才不用读文字也能认出来。
                    color = if (completed) colors.textTertiary else colors.textPrimary,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // 已经在这条待办上攒到的番茄数。0 就什么都不显示 —— 一整屏"0 个番茄"
                // 只是噪音，而番茄数是"投入过"的证据，有才值得说。
                if (focusCount > 0) {
                    Spacer(Modifier.height(skin.spaceMicro))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.accentMain.copy(alpha = 0.14f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = colors.accentMain,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                // 文案与专注页「今天 N 个番茄」共用同一个函数，
                                // 免得一处写"3 个番茄"、另一处写"番茄 ×3"。
                                text = pomodoroCountText(focusCount),
                                fontSize = 10.sp,
                                color = colors.accentMain,
                            )
                        }
                    }
                }
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = PocketTypography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 没有截止日期就**不写一行字**：以前这里会写"未设置截止时间"，
                // 而绝大多数自定义待办本来就没有截止日期 —— 那是每行都在喊的噪音。
                if (dueDate.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    val overdue = isOverdue(dueDate)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = if (overdue) colors.danger else colors.textTertiary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "截止 $dueDate",
                            style = PocketTypography.caption,
                            color = if (overdue) colors.danger else colors.textTertiary
                        )
                    }
                }
            }
            // 「起一个番茄钟」。只在未完成的待办上出现：钟是为"要去做的事"起的，
            // 一条已经勾掉的待办给专注入口只会让人怀疑点错了。
            if (onStartFocus != null && !completed) {
                TodoIconButton(
                    icon = Icons.Outlined.Timer,
                    contentDescription = "开始专注 $title",
                    tint = colors.accentMain,
                    background = colors.accentMain.copy(alpha = 0.10f),
                    onClick = onStartFocus,
                )
            }
            TodoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "删除待办",
                // 原来用 textTertiary：白卡上只有约 2.6:1，作为可点图标低于
                // 非文本控件的 3:1 门槛，看着像"灰掉的不可用状态"。升到次级文字色。
                tint = colors.textSecondary,
                background = colors.textSecondary.copy(alpha = 0.08f),
                onClick = onDelete,
            )
        }
    }
}

/**
 * 行首的圆形勾选。
 *
 * 换成自绘而不是 M3 [androidx.compose.material3.Checkbox]：默认方框的 48dp 视觉体量
 * 和这几行文字不匹配（一张卡里最抢眼的是那个方框）。圆形 + 细描边 + 勾选后填充，
 * 与「下一节课」卡里的胶囊是同一套语言。
 *
 * 触区仍是 48dp；`toggleable` 保留 Checkbox 语义（读屏会报"复选框、已选中"）。
 */
@Composable
private fun TodoCheck(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val fill by animateColorAsState(
        targetValue = if (checked) colors.accentMain else Color.Transparent,
        animationSpec = tween(Motion.expand),
        label = "todo-check-fill",
    )
    val stroke by animateColorAsState(
        // 未勾选也用强调色描边（80% ≈ 3.1:1，达到非文本控件的 3:1 门槛）：
        // 首页三行待办原本全是灰圆环，是整页最"闷"的一组元素；
        // 圆环是**可操作**的提示，用强调色比中性灰更像"这里可以点"。
        targetValue = if (checked) colors.accentMain else colors.accentMain.copy(alpha = 0.80f),
        animationSpec = tween(Motion.expand),
        label = "todo-check-stroke",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription =
                    if (checked) "标记「$label」为未完成" else "标记「$label」为完成"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = onAccentColor(),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * 行尾图标按钮：32dp 可见淡底圆 + [IconButton] 自带的 48dp 触区。
 *
 * 裸图标在白卡上没有边界，和勾选圆环、「N 个番茄」胶囊不在同一套语言里；
 * 这里沿用首页搜索框放大镜的"32dp 淡底圆"，让"能点"先于点按被看见。
 * 删除用中性底而不是警示红：它是次要的破坏性操作，不该比"完成 / 专注"更响。
 */
@Composable
private fun TodoIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 首页待办区的「考试」行。
 *
 * 与待办行（[TodoRow]）刻意长得不一样：考试有**硬性时间点**，
 * 错过无法补，所以用"考试"标签 + 日期时间地点，视觉上比待办更醒目。
 */
@Composable
internal fun ExamRow(
    exam: ExamEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    // 考试是"不可补"的事件，整张卡带一层极淡的警示底色 —— 扫一眼就能把它
    // 从待办堆里分出来。浓度压得很低（浅色 6% / 深色 12%）：底色只负责分堆，
    // 上面的正文仍读主/次文字色，不靠底色传达信息。
    val examCard = lerp(
        colors.surfaceCard,
        colors.warning,
        if (LocalDarkMode.current) 0.12f else 0.06f,
    )
    SoftCard(
        modifier = modifier.fillMaxWidth(),
        color = examCard,
        contentPadding = PaddingValues(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TodoCheck(
                checked = exam.completed,
                label = exam.courseName,
                onToggle = onToggle,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 考试标签用警示色而不是主色：考试与待办的区别是"错过无法补"，
                    // 主色会让它看起来只是另一条待办。
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.warning.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "考试",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.warning,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = exam.courseName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = PocketTypography.cardTitle,
                        fontWeight = FontWeight.Medium,
                        color = if (exam.completed) colors.textTertiary else colors.textPrimary,
                        textDecoration = if (exam.completed) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 倒计时放在课名右侧：考试最要紧的信息就是"还有多久"。
                    // 解析不出日期时不显示（而不是显示"未知"，那只是噪音）。
                    examCountdown(exam.date)?.let { cd ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = cd,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            // 3 天内用警示色 —— 临近考试值得被看见。
                            // ⚠️ 这个 3 现在来自 `util/ExamCountdown.EXAM_SOON_DAYS`：
                            // 主动气泡说「X 还有 N 天」用的是**同一个常量**，
                            // 两处必须同一天变（否则用户会觉得对不上）。
                            color = if (cd.startsWith("还有") &&
                                cd.filter { it.isDigit() }.toIntOrNull()?.let { it <= EXAM_SOON_DAYS } == true
                            ) colors.warning else colors.textTertiary,
                        )
                    }
                }
                Text(
                    text = exam.summary,
                    style = PocketTypography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (exam.seat.isNotBlank()) {
                    Text("座位 ${exam.seat}", style = PocketTypography.caption, color = colors.textTertiary)
                }
            }
            TodoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "删除考试",
                tint = colors.textSecondary,
                background = colors.textSecondary.copy(alpha = 0.08f),
                onClick = onDelete,
            )
        }
    }
}

/**
 * 首页待办区的「电费偏低」提醒行。
 *
 * ## 为什么它不是 [TodoRow]
 *
 * 它没有完成/删除语义 —— 一笔欠费不是勾一下就没了的事，唯一的动作是"去缴费"。
 * 所以整张卡可点、没有勾选圆环、也没有删除按钮；配色沿用考试行的警示底，
 * 因为两者都是"不处理会出事"的东西。
 *
 * ## 文案为什么说"上次查到"
 *
 * 余额来自本机记住的上一次查询（最多 24 小时，见 `ProactiveStore.BALANCE_MAX_AGE_MS`），
 * 不是实时值。说成"当前只剩"是在替系统撒谎；点进电费页会真查一次。
 */
@Composable
internal fun ElectricityReminderRow(
    alert: HomeElectricityAlert,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    // 欠费比"余额偏低"更急：用 danger 而不是 warning 区分这两种状态。
    val owed = alert.balance < 0.0
    val tint = if (owed) colors.danger else colors.warning
    val card = lerp(
        colors.surfaceCard,
        tint,
        if (LocalDarkMode.current) 0.12f else 0.06f,
    )
    val amount = "¥" + "%.2f".format(abs(alert.balance))
    SoftCard(
        modifier = modifier.fillMaxWidth(),
        color = card,
        onClick = onClick,
        contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FlashOn,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tint.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "电费",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = tint,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = alert.dormLabel,
                        style = PocketTypography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (owed) "宿舍电费已欠 $amount" else "宿舍电费只剩 $amount",
                    style = PocketTypography.cardTitle,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "上次查到的余额，点开可刷新",
                    style = PocketTypography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun EmptyTodoCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    SoftCard(modifier = Modifier.fillMaxWidth(), color = colors.surfaceCard) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accentMain.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = colors.accentMain,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "待办已清空 🎉",
                    style = PocketTypography.cardTitle,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    // 入口从"上方输入框"改成了右下角的按钮，这句话必须跟着改，
                    // 否则用户会去找一个已经不存在的东西。
                    text = "点右下角的 ＋ 添加一条待办",
                    style = PocketTypography.caption,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/**
 * 考试倒计时文案。解析不出日期时返回 null（界面就不显示倒计时）。
 *
 * 实现已搬到 `util/ExamCountdown`（那里还有主动气泡用的"还剩几天"）。
 * 这里保留这个名字与签名，只为不动既有调用点与它的单测（`ExamCountdownTest`）——
 * 与 `BoundLogin.maskStudentId` 保留为委托是同一个理由。
 *
 * ## 为什么不复用 [isOverdue]
 *
 * [isOverdue] 只认 ISO 的 `2026-01-15`。而考试通知里常见 **`1月15日`** 这种写法
 * （解析器刻意保留原文，因为通知里往往省略年份）—— `LocalDate.parse` 会直接抛异常。
 *
 * 所以 `util/ExamCountdown.parseExamDate` 额外支持「M月D日」，补**今年**的年份；
 * 若该日期已过去半年以上，则按**明年**算 —— 学期末的通知常跨年（12 月发的 1 月考试）。
 */
internal fun examCountdown(date: String): String? =
    examDaysLeft(date)?.let(::examCountdownText)

internal fun isOverdue(dueDate: String): Boolean {
    if (dueDate.isBlank()) return false
    return try {
        LocalDate.parse(dueDate).isBefore(LocalDate.now())
    } catch (_: Exception) {
        false
    }
}
