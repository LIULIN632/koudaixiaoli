package com.pocket.xiaoli.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 鲤鱼消息区的**布局令牌** —— 这里只放"被多处共享、且是推导出来的"数值。
 *
 * ## 为什么值得单开一个文件
 *
 * 消息区有一个**结构性**的横向基准：回答正文、思考链、工具脚注、错误卡片
 * 都要和"头像右侧"对齐。改造前这个位置是**字面量 `36.dp`，在 4 个地方各写一遍**，
 * 而它其实是推导出来的：
 *
 * ```
 * 36dp = 头像 28dp + 头像与内容的间距 skin.spaceSm(8dp)
 * ```
 *
 * 四处各写一个 36 的代价不是"不好看"，是**改不动**：把头像从 28 调到 32，
 * 编译器不会报错、界面也不会崩 —— 只是四块内容悄悄错位 4dp，而且没有任何测试看得见。
 * 这正是"每个组件自己发明数字"最典型的一种后果。
 */

/**
 * 助手侧头像直径。改它，下方所有内容缩进会自动跟着走。
 *
 * 28dp → 40dp → **44dp**（2026-10，两轮按反馈调）：28 是当初"有个视觉锚点就行"的最小值，
 * 而头像里现在装的是一条**有五官的**吉祥物 —— 28dp 下眼睛只有 1dp 出头，
 * 鱼看起来是一团色块；40dp 时眼睛约 2dp，但用户仍觉得比参考软件小，于是定在 44dp
 * （= Material 的最小触摸尺寸，也是"再大就会压过正文"的上限：44+8=52dp 缩进）。
 *
 * ⚠️ 它的**副作用是设计好的**：`assistantContentGutter()` = 本值 + 头像间距，
 * 所以正文/思考链/脚注/错误卡片的左侧缩进会一起变（28→36、40→48、44→52dp）。
 * 四处各写一个 36 的旧写法做不到这件事（改一处会静默错位）。
 */
val AssistantAvatarSize: Dp = 44.dp

/** 图标按钮的**触摸**尺寸。Material 要求 ≥44dp（本项目 lint 也有这条）。 */
val AssistantIconTouchSize: Dp = 44.dp

/** 图标按钮里**字形**的尺寸（视觉尺寸，与触摸区无关）。 */
val AssistantIconGlyphSize: Dp = 15.dp

/**
 * 内容列（正文 / 思考链 / 脚注 / 错误卡片）的左侧缩进 = [AssistantAvatarSize] + 头像间距。
 *
 * ⚠️ 头像间距走 `skin.spaceSm`，所以**这个值是随皮肤变的**（当前两套皮肤恰好都是 8dp，
 * 但不要因此把它写死成 36 —— 那样一旦某套皮肤调了 `spaceSm`，这里就静默错位）。
 */
@Composable
fun assistantContentGutter(): Dp {
    val skin = LocalSkin.current
    return AssistantAvatarSize + skin.spaceSm
}

/**
 * 问答轮内外的垂直间距档位。
 *
 * ## 为什么不是"所有内容统一一个间距"
 *
 * 列表项按「一问一答」成组（见 `toRounds()`），于是有两种关系：
 *
 * ```
 * ┌ 第 1 轮 ──────────────┐
 * │ [用户提问]             │  ← WITHIN：同一段对话内部，贴紧
 * │ [助手回答]             │
 * └────────────────────────┘
 *        ← BETWEEN：换一段对话，拉开
 * ┌ 第 2 轮 ──────────────┐
 * ```
 *
 * 两者**共用同一个间距**（曾经的 `spaceMd`）会让"提问与回答"被推得和
 * "两段对话"一样远，一问一答读起来不像一组。
 *
 * 正确的关系是：**一问一答要贴紧，换一段对话才拉开**。
 * 这条差异只靠"统一加大间距"是修不出来的 —— 那只会让提问和回答离得更远。
 */
enum class TurnGap {
    /** 列表第一项，上方不加间距。 */
    NONE,

    /** 同一轮内部（提问与它的回答、折叠标题与展开内容）：贴紧，读起来是一组。 */
    WITHIN,

    /** 两段对话之间：拉开，形成"一轮一轮"的节奏。 */
    BETWEEN,
}

/** 两段问答之间固定拉开一档：折叠后一轮只剩一行标题，更需要间距来分界。 */
internal fun roundGapOf(index: Int): TurnGap =
    if (index == 0) TurnGap.NONE else TurnGap.BETWEEN

/** 把档位映射到具体间距。两处调用点共用，避免映射散开。 */
@Composable
fun TurnGap.toDp(): Dp {
    val skin = LocalSkin.current
    return when (this) {
        TurnGap.NONE -> 0.dp
        TurnGap.WITHIN -> skin.spaceSm
        // spaceXl：Minimal 18dp / Vivid 22dp —— 拉得开，又不至于"一屏只放一轮"
        TurnGap.BETWEEN -> skin.spaceXl
    }
}
