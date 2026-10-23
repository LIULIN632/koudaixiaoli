package com.pocket.xiaoli.ui.components.infocard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 信息卡体系 —— 让「同类信息在全 App 长得一样」。
 *
 * ## 为什么需要它
 *
 * 项目里已经有不少**质量不错但各自实现**的信息卡：`GradeCard`（成绩）、
 * `BalanceCard`（电费/余额）、`NextCourseCard`（下一节课）……
 * 它们的信息层级其实高度一致：
 *
 * ```
 * 标签（小号弱色）
 * 主值（大号加粗）
 * 辅助明细（小号弱色）
 * ```
 *
 * 但每处都自己写一遍，导致：同样的层级用不同的字号（24/28/38）、
 * 不同的间距（10/12/16）、不同的次要文字色。**用户未必说得出哪里怪，
 * 但会觉得不精致** —— 这正是"设计系统"要解决的问题。
 *
 * 本文件提供**共享原语**，不强行改造已有实现：
 * 新页面直接用；老页面按需迁移（迁移时用本文件的组件替换自写结构）。
 *
 * ## 信息层级规范
 *
 * | 层级 | 典型字号 | 颜色 |
 * |---|---|---|
 * | 标签 | 12sp | textSecondary |
 * | 主值 | 28–38sp Bold | 语义色（见 [InfoValue]） |
 * | 明细行 | 12sp | textTertiary |
 * | 次要单元格 | 11sp 标签 + 15sp Bold 值 | 见 [InfoCell] |
 */

/** 信息卡的语义色调。决定主值的颜色，与具体业务无关，便于各处统一。 */
enum class InfoTone {
    /** 普通数值（余额、计数）—— 用主题强调色 */
    NORMAL,

    /** 正向（通过、达标） */
    POSITIVE,

    /** 警示（偏低、待办） */
    WARNING,

    /** 负向（欠费、不及格） */
    NEGATIVE,
}

/**
 * 信息卡容器：统一圆角、底色、内边距与纵向间距。
 *
 * @param title 可选的卡片标题（放在内容之上，弱色小字）
 * @param tone  决定主值颜色基调；子内容可用 [InfoValue] 单独覆盖
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    /** 卡片主体内容 */
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            if (title != null) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(skin.spaceSmall))
            }
            content()
        }
    }
}

/**
 * 主值 + 可选标签与辅助说明。信息卡的视觉核心。
 *
 * 标签在上、辅助在下 —— 因为用户扫视时先看数字，标签与说明是补充。
 *
 * @param value      主值文本（已格式化好，如 "128.50"）
 * @param label      上方标签（如 "可用余额"）
 * @param caption    下方辅助说明（如 "更新于 10:24 · 实时"）
 * @param tone       颜色基调
 * @param large      是否用超大号（余额这类"一眼要看清楚"的场景用 true）
 */
@Composable
fun InfoValue(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    caption: String? = null,
    tone: InfoTone = InfoTone.NORMAL,
    large: Boolean = false,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(modifier = modifier) {
        if (label != null) {
            Text(text = label, fontSize = 12.sp, color = colors.textSecondary)
            Spacer(Modifier.height(skin.spaceMicro))
        }
        Text(
            text = value,
            fontSize = if (large) 38.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            color = tone.color(colors),
        )
        if (caption != null) {
            Spacer(Modifier.height(skin.spaceMicro))
            Text(text = caption, fontSize = 12.sp, color = colors.textTertiary)
        }
    }
}

/** 一行「标签 —— 值」。用于卡片内的明细列表。 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = skin.spaceMicro),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.6f),
        )
    }
}

/**
 * 等分单元格：小号标签 + 加粗值。用于「平时 / 期末 / 期中」这类并列分项。
 */
@Composable
fun RowScope.InfoCell(
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 11.sp, color = colors.textTertiary)
        Spacer(Modifier.height(skin.spaceMicro))
        Text(
            text = value.ifBlank { "—" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: colors.textPrimary,
        )
    }
}

/**
 * 一行「关键词 · 关键词 · 关键词」的弱色元信息。
 *
 * 用来替代每处手写的 `buildString { append("绩点 ").append(...) }` ——
 * 那些实现的分隔符写法各不相同（有的两个空格、有的中点），这里统一。
 */

/** 卡片内的分隔线，统一粗细与颜色。 */
@Composable
fun InfoDivider(modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    HorizontalDivider(
        modifier = modifier.padding(vertical = skin.spaceSm),
        color = colors.borderDivider,
        thickness = 0.5.dp,
    )
}

private fun InfoTone.color(colors: com.pocket.xiaoli.ui.theme.PocketXiaoliColors): Color =
    when (this) {
        InfoTone.NORMAL -> colors.accentMain
        InfoTone.POSITIVE -> colors.success
        InfoTone.WARNING -> colors.warning
        InfoTone.NEGATIVE -> colors.danger
    }
