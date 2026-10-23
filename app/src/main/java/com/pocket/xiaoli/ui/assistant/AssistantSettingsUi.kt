package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 鲤鱼设置页共用的小控件。
 *
 * 拆出来的理由只有一条：这些件同时被**首页摘要**、**四个二级页**与**快照测试**消费，
 * 留在任何一个具体页面里都会让另一个页面反向依赖它。它们全是纯状态驱动的，
 * 没有 store / container 依赖，所以放这里既不会把测试拖进 DI，也不会制造循环。
 */

/** 「回答字号」三个档位，与 `TabAppearance.assistantFontMultiplier` 的取值一一对应。 */
internal val FONT_SCALES = listOf("小" to 0, "标准" to 1, "大" to 2)

/**
 * 档位索引 → 中文名。
 *
 * ⚠️ 首页摘要与二级页 chip **必须**读同一个函数：两处各写一个 `when` 的话，
 * 将来加第四档时一定漏一处，用户会看到「首页写大、设置里选中的却是标准」。
 */
internal fun fontScaleLabel(value: Int): String =
    FONT_SCALES.firstOrNull { it.second == value }?.first ?: "标准"

/**
 * 分组卡片：与首页 / 设置页同一套皮肤卡 + 主色竖条组标题，避免整页一大列裸控件。
 *
 * 组标题此前是 12sp 的三级灰字，在白卡上方几乎看不见；现在用全站统一的
 * [SectionCaption]，与搜索页 / 资料站同一套层次。
 */
@Composable
internal fun SettingsCard(title: String, content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionCaption(
            text = title,
            modifier = Modifier.padding(start = skin.spaceXs, bottom = skin.spaceSm),
        )
        AppCard {
            Column(modifier = Modifier.padding(skin.spaceLg)) { content() }
        }
    }
}

/** 段落内的小标题（形状 / 颜色 / 回答字号）。 */
@Composable
internal fun GroupLabel(text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = colors.textPrimary,
        modifier = Modifier.padding(bottom = skin.spaceSm),
    )
}

/**
 * "这一段即时生效"的说明。
 *
 * 存在的理由：模型那一页偏偏要按保存 —— 不写清楚，用户点完形状会往下找保存按钮。
 */
@Composable
internal fun InstantHint(text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = text,
        fontSize = 11.sp,
        color = colors.textSecondary,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = skin.spaceSm),
    )
}

/** 一行「标题 + 说明 + 开关」。 */
@Composable
internal fun SwitchLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceMicro))
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(skin.spaceSm))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentMain),
        )
    }
}

/** 一行「说明」用的次级文字，让二级页底部的解释与卡片内容保持同一节奏。 */
@Composable
internal fun Footnote(text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = text,
        fontSize = 11.sp,
        color = colors.textSecondary,
        lineHeight = 17.sp,
        modifier = Modifier.padding(horizontal = skin.spaceXs),
    )
}
