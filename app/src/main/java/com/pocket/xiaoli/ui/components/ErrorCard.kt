package com.pocket.xiaoli.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.Motion
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 全项目统一的**错误卡片**。
 *
 * ## 为什么要有这个组件
 *
 * 项目里此前没有错误态规范：真正共享的只有 [EmptyStatePlaceholder]（**空态**，不处理失败），
 * 各页面各自内联一行 `Text(color = colors.danger)`。所以错误呈现是**每页一个样**，
 * 而且普遍缺两件事：**说清下一步能做什么**（重试），以及**能看到原始报错**（排查/反馈用）。
 *
 * 这个组件把三件事固定下来：
 * - **配色**：`danger` 的柔和底 + 25% 描边，而不是纯红字（纯红字在 8 套主题 × 明暗下
 *   对比度不稳，且看起来像"内容"而不是"状态"）
 * - **分层**：卡片上只放一句可读文案；HTTP 体 / 异常全文收进「详情」，默认不显示、限长
 * - **出路**：能重试的给「重试」，有原文的给「详情」
 *
 * 反面教材是岱宗盒子的助手实现：错误只丢一条普通气泡，无配色、不可点、无重试，
 * 还把原始异常直接往外扔且不限行数 —— 长报错会把整屏吃掉，而用户还是不知道能干什么。
 *
 * @param message 给用户看的一句话。**必须**是可读文案，不要直接塞异常 `toString()`。
 * @param detail 原始报错全文。为 null 时不显示「详情」入口。
 * @param onRetry 为 null 时不显示「重试」入口（不是所有失败都能原地重试）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "这次没成功",
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
    /** 详情原文的显示上限（行数），超出截断 —— 报错原文可能是几屏长。 */
    maxDetailLines: Int = 12,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var showDetail by remember { mutableStateOf(false) }
    val hasDetail = !detail.isNullOrBlank()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        // 卡片底 + danger 描边，而不是 danger 低透明度铺底。
        //
        // 为什么换：8% 的 danger 铺底在深色主题上**几乎不可见** ——
        // 深色主题的 danger 是 0xFFEF9A9A，8% 压在 0xFF1A1C1E 的页面上只偏移约 2 个色阶，
        // 等于"标了红但看不出来"；而浅色主题上那层淡粉又和页面的柔和底太接近。
        // 换成语义中立的卡片底（surfaceCard，明暗各有一套）+ 35% 的 danger 描边，
        // 既保住了"这是异常状态"的红，又在 16 套主题下都立得住。
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = skin.spaceCompact, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(skin.spaceSmall))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.height(skin.spaceSmall))
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.textSecondary,
            )

            if (onRetry != null || hasDetail) {
                Spacer(Modifier.height(skin.spaceSm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onRetry != null) {
                        ErrorAction(text = "重试", icon = Icons.Filled.Refresh, onClick = onRetry)
                    }
                    if (onRetry != null && hasDetail) Spacer(Modifier.width(skin.spaceSm))
                    if (hasDetail) {
                        ErrorAction(
                            text = if (showDetail) "收起详情" else "详情",
                            icon = if (showDetail) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            onClick = { showDetail = !showDetail },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showDetail,
                enter = fadeIn(tween(Motion.enter)) + expandVertically(tween(Motion.expand)),
                exit = fadeOut(tween(Motion.exit)) + shrinkVertically(tween(Motion.expand)),
            ) {
                Surface(
                    modifier = Modifier.padding(top = skin.spaceSm).fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    // 详情用 surfaceAlt（比卡片底再深/浅一层），在卡片上形成"凹槽"感
                    color = colors.surfaceAlt,
                ) {
                    Text(
                        text = detail.orEmpty().lines().take(maxDetailLines).joinToString("\n"),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * 错误卡片上的一个小胶囊动作（重试 / 详情），比 IconButton 更能表达"可以点"。
 *
 * 底色用 [com.pocket.xiaoli.ui.theme.PocketXiaoliColors.surfaceAlt] 而不是
 * danger 低透明度：12% 的 danger 在深色主题上同样几乎看不见（见 ErrorCard 的说明），
 * 而"这颗胶囊有没有边界"直接决定它看起来能不能点。
 */
@Composable
private fun ErrorAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.surfaceAlt,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = colors.danger, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(skin.spaceMicro))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.danger)
        }
    }
}

/**
 * 轻量错误提示：一行红字 + 可选重试。
 *
 * 给"页面里已经有一大块内容、只是某个区块失败了"的场景用 ——
 * 那里摆一张完整卡片太重，但也比"裸红字"多一点可操作性（至少能重试、并限行）。
 */
@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    maxLines: Int = 3,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colors.danger,
            maxLines = maxLines,
        )
        if (onRetry != null) {
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                text = "重试",
                modifier = Modifier
                    .clickable(onClick = onRetry)
                    .padding(horizontal = skin.spaceXs, vertical = 2.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accentMain,
            )
        }
    }
}
