package com.pocket.xiaoli.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.theme.LocalBaseColors
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.SkinStyle
import com.pocket.xiaoli.ui.theme.applySurfaces
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer
import com.pocket.xiaoli.ui.theme.tokens

/**
 * 「界面风格」的可视化选择器 —— **用真实组件与真实令牌现场渲染**，不画静态图。
 *
 * ## 为什么不做静态预览图
 *
 * 改造前这一项用的是通用 `OptionsDialog`，两行纯文字：
 * "极简 · 紧凑 · 小圆角 · 灰底白卡" / "鲜活 · 宽松 · 大圆角 · 弥散阴影"。
 * 用户只能读文字，**看不到样子** —— 而"界面风格"恰恰是一个纯视觉的选择。
 *
 * 更关键的是：静态图**一定会漂移**。本项目已经踩过同类坑 ——
 * `DesignTokens.kt` 的 `LocalBaseColors` KDoc 的 KDoc 记录了旧实现手写 7 组预览色、
 * 最终"预览色和真实主题对不上"的历史。
 *
 * 所以这里换一条路：预览内容放进
 * `CompositionLocalProvider(LocalSkin provides 目标皮肤)`，
 * 于是它渲染的圆角、阴影、底色、图标线形/彩色、标签着色
 * **全部走和正式界面同一条代码路径**。物理上不可能与真实效果不一致。
 *
 * ## 三条依赖都是既有真源，没有新增任何"预览专用"数据
 *
 * | 需要 | 取自 |
 * |---|---|
 * | 目标皮肤的形状令牌（圆角 / 间距） | [SkinStyle.tokens] |
 * | 目标皮肤的表面基调（页底 / 卡底 / 次级底） | [LocalBaseColors] + [applySurfaces] |
 * | 目标皮肤下的图标与标签策略 | [currentIconPresentation]（读被 provide 的 `LocalSkin`） |
 *
 * 其中 [LocalBaseColors]（**未被皮肤推移过**的基色）正是为了这件事才暴露的 ——
 * [LocalPocketXiaoliColors] 里存的是已被当前皮肤混合过的颜色，而 `lerp` 不可逆，
 * 拿它推不出另一套皮肤的底色。
 *
 * ## 预览展示的是"你会得到什么"，不是"理论上最花的样子"
 *
 * 预览不覆盖用户的「用色强度」选择，所以：
 * - 用户选着「纯净」时，两套皮肤的预览都是单色的 —— 差异落在形状与底色上
 * - 用户选着「彩虹」时，极简预览仍然是单色（皮肤上限否决），鲜活预览是彩色
 *
 * 这恰好把"皮肤定上限"这条规则本身演示给了用户，而不是把两套皮肤都渲染成最鲜艳的样子。
 *
 * @param selected 当前生效的皮肤（用于单选态）
 */
@Composable
internal fun SkinPreviewDialog(
    selected: SkinStyle,
    onSelect: (SkinStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("界面风格") },
        text = {
            SkinPreviewContent(
                selected = selected,
                onSelect = {
                    onSelect(it)
                    onDismiss()
                },
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.textSecondary) }
        },
    )
}

/**
 * 弹窗的**正文**（两个皮肤选项 + 各自的实时预览）。
 *
 * ⚠️ 刻意从 [SkinPreviewDialog] 里抽出来并标 `internal`：`AlertDialog` 渲染在**独立窗口**里，
 * JVM 快照测试的 `captureRoboImage` 抓的是 Compose 根节点，抓不到那个窗口。
 * 抽出正文后，`SkinPreviewScreenshotTest` 能直接渲染**同一份**内容并截图 ——
 * 而不是在测试里另写一份仿制品（那又会变成"预览与实际漂移"的老问题）。
 */
@Composable
internal fun SkinPreviewContent(
    selected: SkinStyle,
    onSelect: (SkinStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkinStyle.entries.forEach { style ->
            SkinPreviewChoice(
                style = style,
                selected = style == selected,
                onSelect = { onSelect(style) },
            )
        }
    }
}

@Composable
private fun SkinPreviewChoice(
    style: SkinStyle,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val base = LocalBaseColors.current
    val isDark = LocalDarkMode.current
    val previewTokens = style.tokens()

    // 目标皮肤表面基调的**现场推导**：与 `Theme.kt` 对当前皮肤做的事完全一样，
    // 只是把皮肤换成用户正在预览的这一套。复用 `applySurfaces` 而不是重写推导，
    // 所以它受 `SkinThemeContractTest` 里那批"套用之后确实不同"的断言保护。
    val previewColors = previewTokens.applySurfaces(base, isDark)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accentMain),
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Text(
                text = style.description,
                fontSize = 11.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            // ── 关键：把目标皮肤与目标表面色 provide 下去，下面全是真实组件 ──
            CompositionLocalProvider(
                LocalSkin provides previewTokens,
                LocalPocketXiaoliColors provides previewColors,
            ) {
                SkinPreviewSample()
            }
        }
    }
}

/**
 * 预览样例：页底上的一张卡，卡内一行图标 + 标签。
 *
 * 三样东西分别对应皮肤的三组字段，一眼就能读出差异：
 * - **页底与卡底**：`backgroundColor*` / `cardColor*` + `surfaceShift` → 是"灰底白卡"还是"靠阴影分层"
 * - **卡片本身**：`radiusCard` / `shadowBlur` / `shadowColor*` → 形状与阴影
 * - **图标与标签**：`iconStyle`（线性 / 面性）+ `iconMode`（单色 / 彩色）+ `colorfulTags`
 */
@Composable
internal fun SkinPreviewSample() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val icon = currentIconPresentation()
    // 用 LIFE 而不是 ACADEMIC：ACADEMIC 的色相偏移是 0（与主题主色同色），
    // 拿它演示"多色"看不出区别；LIFE 偏移 150°，一眼能看出彩色与单色的差异。
    val accent = ServiceAccent.LIFE

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(skin.radiusCard))
            .background(colors.surfaceBase)
            .padding(skin.spaceSm),
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(
                    horizontal = skin.spaceSm,
                    vertical = skin.spaceSm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .background(
                            if (icon.showContainer) serviceAccentContainer(accent)
                            else colors.surfaceHighlight
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (icon.filled) Icons.Filled.School else Icons.Outlined.School,
                        contentDescription = null,
                        tint = if (icon.colorful) serviceAccentColor(accent) else colors.accentMain,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Text(
                    text = "示例卡片",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(skin.spaceXs))
                SampleTag(colorful = icon.colorfulTags, accent = accent)
            }
        }
    }
}

/** 标签样例：彩色态取语义色，中性态取弱化色 + 中性底。 */
@Composable
private fun SampleTag(colorful: Boolean, accent: ServiceAccent) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tint = if (colorful) serviceAccentColor(accent) else colors.textSecondary
    val shape = RoundedCornerShape(skin.radiusChip)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (colorful) tint.copy(alpha = 0.14f) else colors.surfaceHighlight)
            .then(
                if (colorful) Modifier.border(1.dp, tint.copy(alpha = 0.35f), shape) else Modifier
            )
            .padding(horizontal = skin.spaceXs, vertical = 1.dp),
    ) {
        Text(
            text = "标签",
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}
