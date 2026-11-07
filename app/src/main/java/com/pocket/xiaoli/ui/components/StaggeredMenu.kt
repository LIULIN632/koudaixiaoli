package com.pocket.xiaoli.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.theme.reduceMotion
import kotlin.math.min

/** 相邻两项的出场间隔（毫秒）。0.04s：能看出先后，又不会让想点第 8 项的人干等。 */
internal const val MENU_ITEM_STAGGER_MS = 40

/** 单项从"无"到"完全出现"的时长（毫秒）。 */
internal const val MENU_ITEM_DURATION_MS = 160

/** 出场时每项上浮的距离（dp）。只给一点点：这是"出现"的暗示，不是入场演出。 */
internal const val MENU_ITEM_RISE_DP = 6f

/** 整个开合时长的分母（毫秒）：最后一项的窗口结束时刻。 */
private fun menuTimelineMs(count: Int): Int =
    MENU_ITEM_DURATION_MS + MENU_ITEM_STAGGER_MS * (count - 1).coerceAtLeast(0)

/**
 * 第 [index] 项在整条时间轴 [progress]（0..1）上的出现程度。
 *
 * **纯函数**，由 `StaggeredMenuTest` 钉住。第 i 项自己的窗口是
 * `[i*stagger, i*stagger+duration]` —— 把整条时间轴倒放，最后一项自然最先消失，
 * 不需要另写一套"关闭时反过来延迟"的时序（两套时序迟早对不齐）。
 *
 * @return 0 = 完全没出现，1 = 完全出现
 */
internal fun menuItemProgress(index: Int, progress: Float, itemCount: Int): Float {
    if (itemCount <= 1) return progress.coerceIn(0f, 1f)
    val timeline = menuTimelineMs(itemCount).toFloat()
    val start = (MENU_ITEM_STAGGER_MS * index).toFloat()
    val local = ((progress.coerceIn(0f, 1f) * timeline - start) / MENU_ITEM_DURATION_MS)
        .coerceIn(0f, 1f)
    // ease-out：起步快、收尾慢，像"落定"而不是匀速滑入
    return local * (2f - local)
}

/**
 * 菜单逐项出场的状态（[rememberStaggeredMenu] 的返回值）。
 *
 * 用法：
 * ```kotlin
 * val reveal = rememberStaggeredMenu(expanded = open, itemCount = items.size, onClosed = { open = false })
 * ExposedDropdownMenu(expanded = reveal.menuExpanded, onDismissRequest = reveal::close) {
 *     items.forEachIndexed { i, item ->
 *         Box(reveal.itemModifier(i)) { DropdownMenuItem(...) }
 *     }
 * }
 * ```
 * 关键点：**菜单的 `expanded` 用 [menuExpanded] 而不是调用方那个布尔** ——
 * 关闭时要等收回动画放完，菜单才真正消失，否则"逆序收回"根本看不见。
 */
internal class StaggeredMenuReveal(
    private val itemCount: Int,
    private val clock: () -> Float,
    /** 菜单容器是否还该留在组合里（关闭动画放完才置 false）。 */
    val menuExpanded: Boolean,
    /** 关闭时先播放收回，再让调用方把状态置 false。 */
    val close: () -> Unit,
) {
    /** 第 [index] 项的出场修饰符：透明度 + 上浮，读的是动画时钟（绘制阶段读，不触发重组）。 */
    fun itemModifier(index: Int): Modifier = Modifier.graphicsLayer {
        val p = menuItemProgress(index, clock(), itemCount)
        alpha = p
        translationY = (1f - p) * MENU_ITEM_RISE_DP.dp.toPx()
    }
}

/**
 * 菜单的逐项出场 / 逆序收回。
 *
 * 三条刻意的取舍：
 * 1. **不改菜单本身的锚点与定位** —— 仍然交给 `ExposedDropdownMenu` / `DropdownMenu`，
 *    这里只驱动内容的出场。自己画 Popup 会把"挂在输入框下、贴边翻转"这些细节全接管过来，
 *    风险远大于收益。
 * 2. **减少动画时直接开合**（系统设置或 `reduceMotion()`）：这一层是纯装饰，
 *    没有信息量必须在动效里传达，所以让路成本为零。
 * 3. **不做"动画期间锁死"**：状态驱动的时间轴本身不会叠加两套时钟，
 *    用户连点时最后一次意图生效 —— 锁住反而会让"点了没反应"。
 *
 * @param expanded 调用方想要的开合状态（点外面 → false，选中某项 → false）
 * @param itemCount 菜单里有几项（决定时间轴长度）
 * @param onClosed 收回动画放完后调用，调用方据此清掉自己的 `expanded`
 */
@Composable
internal fun rememberStaggeredMenu(
    expanded: Boolean,
    itemCount: Int,
    onClosed: () -> Unit,
): StaggeredMenuReveal {
    val reduce = reduceMotion()
    val count = itemCount.coerceAtLeast(1)
    // 初值：已经展开（比如旋转屏重建）就直接是完成态，不补放一次动画
    val clock = remember(count) { Animatable(if (expanded) 1f else 0f) }
    var menuExpanded by remember { mutableStateOf(expanded) }

    LaunchedEffect(expanded, count) {
        if (expanded) {
            menuExpanded = true
            if (reduce) {
                clock.snapTo(1f)
            } else {
                clock.snapTo(0f)
                clock.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(menuTimelineMs(count), easing = LinearEasing),
                )
            }
        } else if (menuExpanded) {
            if (reduce) {
                clock.snapTo(0f)
                menuExpanded = false
                onClosed()
            } else {
                clock.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(menuTimelineMs(count), easing = LinearEasing),
                )
                menuExpanded = false
                onClosed()
            }
        }
    }

    return remember(count, menuExpanded) {
        StaggeredMenuReveal(
            itemCount = count,
            clock = { clock.value.coerceIn(0f, 1f) },
            menuExpanded = menuExpanded,
            close = { if (menuExpanded) onClosed() },
        )
    }
}

/**
 * 包一项菜单内容，让它跟着 [reveal] 的时钟出场。
 *
 * 单独抽出来是为了调用点只多一层 `Box`；也可以直接用 `Modifier` 版本
 * （[StaggeredMenuReveal.itemModifier]）套在 `DropdownMenuItem` 自己的 modifier 上。
 */
@Composable
internal fun StaggeredMenuItem(
    reveal: StaggeredMenuReveal,
    index: Int,
    content: @Composable () -> Unit,
) {
    Box(modifier = reveal.itemModifier(index)) { content() }
}

/** 时间轴上"最后一项开始出场"的时刻（0..1），供测试与调试用。 */
internal fun lastItemStartProgress(itemCount: Int): Float {
    if (itemCount <= 1) return 0f
    return min(1f, MENU_ITEM_STAGGER_MS * (itemCount - 1).toFloat() / menuTimelineMs(itemCount))
}
