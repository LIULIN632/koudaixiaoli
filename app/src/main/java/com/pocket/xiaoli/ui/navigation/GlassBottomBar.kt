package com.pocket.xiaoli.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.pocket.xiaoli.ui.glass.glassSurface
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.tabContentAlpha

/** 栏体总高（含上下各 [BarInnerPadding]）。 */
private val BarHeight = 72.dp

/** 栏内边距：玻璃面板与选中椭圆都与栏边留这么点空。 */
private val BarInnerPadding = 4.dp

/** 格内上下留白（图标与文字之间只用 [LocalSkin.spaceTiny]，别再加一遍）。 */
private val TabVerticalPadding = 6.dp

/**
 * 选中椭圆的固定高度。
 *
 * 图标 20 + 间隙 + 文字 15 ≈ 39dp ⇒ 52dp 裹住内容后上下各剩 6.5dp。
 * 高度**写死不跟着格子走**：格子是约 65×64dp 的近方形，按比例缩出来就是圆的。
 */
private val SelectedPillHeight = 52.dp

/**
 * 选中椭圆两侧内缩。
 *
 * ⚠️ 这个值是「椭圆能不能看出来」的唯一旋钮：椭圆宽度 = 格宽 − 2×本值。
 * 5 格平分 393dp 屏（Pixel 5）时格宽约 65dp，取 1dp ⇒ 椭圆 63×52，横:竖 ≈ 1.21；
 * 取到 4dp 就只剩 57×52（≈1.1），看上去又会变成一个圆。
 */
private val SelectedPillInset = 1.dp

/** 选中椭圆的主题色浓度。栏底本来就是玻璃，浓度再高会糊成一块色斑。 */
private const val SelectedPillAlpha = 0.20f

/**
 * 液态玻璃底栏。
 *
 * ## 两层（自下而上）
 *
 * | 层 | 内容 | 可交互 |
 * |---|---|---|
 * | 1 | 玻璃面板：`vibrancy` + `blur` + 高光 + 投影 | ❌ |
 * | 2 | tab 行：图标 / 文字，**选中椭圆画在各自格子里** | ✅ 唯一可点 |
 *
 * ## 2026-10-23 第四次改：栏体放大、椭圆变宽、形象缩小
 *
 * 用户三条意见：「底栏稍微往上一点」「鲤鱼形象稍微做小一点、能放到椭圆里」「整体放大一点」。
 * 对应三处：外框下留白 8 → 14dp（栏体离手势区更远，不再贴着屏幕底边）、
 * 栏高 64 → 72dp、椭圆 60×50 → 63×52（左右各内缩 1dp，横:竖 ≈ 1.21，仍是椭圆而不是圆）、
 * `AssistantTabIcon` 的形象 40 → 34dp（旋转 ±8° 后的外接框约 37dp，稳稳落在 52dp 椭圆里）。
 *
 * ## 2026-10-22 第三次改：选中椭圆挪进格子里
 *
 * 前两版把选中态做成一个**跨格移动的玻璃胶囊**（跟手拖、松手弹到最近一格）。它有两个
 * 我在无真机时压不住的问题：① 位置靠 `index * tabWidth` 算，格子宽度一旦和算出来的
 * 不一致（栏内边距、`weight` 分配、按压缩放都会掺进来）就整体偏移；② 胶囊形状靠
 * `clip(RoundedCornerShape(50))`，去掉这个 clip 之后按下的水波纹就是一整块**正方形**。
 * 用户两条都报了：「选中状态偏移」「点击显示一个正方形的区域」。
 *
 * 现在改成：**选中椭圆由每个格子用自己 bounds 的 `drawBehind` 画**。没有跨格位移，
 * 也就没有对齐问题；形状由 `drawRoundRect(cornerRadius = 高度/2)` 直接保证，
 * 不依赖 clip，所以按下反馈不需要矩形水波纹 —— `indication = null`，
 * 按下时靠图标文字整体变淡（`tabContentAlpha`）回应。
 *
 * ## 沿用下来的取舍
 *
 * 上滑/横拖切换 tab（`BottomBarDragMath`）跟着胶囊一起下线了：没有可见的跟手物时，
 * 拖动切换比点击更容易误触，所以这里只保留点击。那套数学与它的单测仍在原处，
 * 等哪天真机上把胶囊做稳了再挂回来。
 */
@Composable
internal fun GlassBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val shape = RoundedCornerShape(50)

    val selectedIndex = items.indexOfFirst { item ->
        currentRoute == item.route || currentRoute?.startsWith("${item.route}/") == true
    }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        // ── 第 1 层：玻璃面板 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .padding(BarInnerPadding)
                .glassSurface(
                    backdrop = backdrop,
                    shape = shape,
                    surfaceColor = colors.surfaceCard,
                ),
        )

        // ── 第 2 层：tab 行（最上面，唯一可点）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .padding(BarInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                GlassBottomTab(
                    item = item,
                    selected = index == selectedIndex,
                    // 椭圆只是主题色半透明，不依赖 backdrop —— 所以三档玻璃能力下
                    // 选中态长得一样，不再出现"有玻璃的机器是胶囊、没玻璃的是方形底"。
                    pillTint = colors.accentMain,
                ) { onSelect(item) }
            }
        }
    }
}

/** 底栏上的一个 tab。 */
@Composable
private fun RowScope.GlassBottomTab(
    item: BottomNavItem,
    selected: Boolean,
    pillTint: Color,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val colors = LocalPocketXiaoliColors.current
    val isAssistantCell = item.route == "assistant"
    // 点按反馈的 source：和 clickable 共用，形象靠它做按压回弹
    val pressSource = remember { MutableInteractionSource() }
    // 按下即变淡（抄 MIUIX 底栏）：不依赖 ripple，手指压下去这一帧就有回应
    val pressed by pressSource.collectIsPressedAsState()
    val contentAlpha = tabContentAlpha(pressed, isAssistantCell)

    Column(
        modifier = Modifier
            .weight(1f)
            // 鲤鱼格撑满整格：只剩 40dp 形象时 Column 会缩到形象高，
            // 上下两截空白就没有点击响应（用户报过"点了没反应"）
            .then(if (isAssistantCell) Modifier.fillMaxHeight() else Modifier)
            // 选中椭圆：用**这一格自己的 bounds** 画，没有任何跨格位移数学。
            // drawBehind 的形状由 cornerRadius 决定，所以无需 clip 也不会出现方块。
            .drawBehind {
                if (!selected) return@drawBehind
                // 五格共用同一个椭圆尺寸：鲤鱼格的形象已经缩到 34dp（比椭圆 52dp 矮 18dp），
                // 不需要再给它单独垫高 —— 2026-10-23 之前那版给鲤鱼格 +2dp，结果两格选中
                // 时椭圆一大一小，看着像错位。
                val height = SelectedPillHeight.toPx().coerceAtMost(size.height)
                val inset = SelectedPillInset.toPx()
                val width = (size.width - inset * 2f).coerceAtLeast(height)
                drawRoundRect(
                    color = pillTint.copy(alpha = SelectedPillAlpha),
                    topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2f),
                )
            }
            .clickable(
                interactionSource = pressSource,
                // 水波纹是矩形的，会盖出"一个正方形的区域"（用户报的原话）——
                // 按下反馈只保留图标文字变淡，见 tabContentAlpha。
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = TabVerticalPadding),
        // 注意：间距由下面显式的 Spacer 提供，这里**不能再**用 spacedBy，
        // 否则同一段间距会被算两遍，内容被挤到超出栏高。
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val skin = LocalSkin.current
        val isAssistant = item.route == "assistant"

        if (isAssistant) {
            // 没有可见文字 → 语义自己补（见 AppNavigation 里同款注释）
            AssistantTabIcon(
                selected = selected,
                interactionSource = pressSource,
                modifier = Modifier.semantics { contentDescription = item.label },
            )
        } else {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(20.dp).alpha(contentAlpha),
                tint = if (selected) colors.accentMain else colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceTiny))
        }
        // 鲤鱼格只有形象、没有文字
        if (!isAssistant) {
            Text(
                modifier = Modifier.alpha(contentAlpha),
                text = item.label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.accentMain else colors.textTertiary,
            )
        }
    }
}
