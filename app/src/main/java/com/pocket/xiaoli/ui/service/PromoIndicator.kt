package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** 指示器的**静止**宽度：非当前页 8dp、当前页拉长到 24dp。 */
internal const val PROMO_INDICATOR_BASE_DP = 8f

/** 当前页拉长后的宽度（dp）。见 [PROMO_INDICATOR_BASE_DP]。 */
internal const val PROMO_INDICATOR_ACTIVE_DP = 24f

/** 指示条高度（dp）。比原来的 5/7dp 圆点更扁 —— 压在照片上时"细长条"比"圆点"更像进度。 */
internal const val PROMO_INDICATOR_HEIGHT_DP = 3f

/** 相邻两枚指示条的间距（dp）。 */
internal const val PROMO_INDICATOR_GAP_DP = 5f

/**
 * 每枚指示条的宽度（dp）。**纯函数**，由 `PromoIndicatorTest` 逐条钉住。
 *
 * @param pageCount 页数（≤0 返回空表）
 * @param position 连续位置 = `currentPage + currentPageOffsetFraction`，
 *   拖动过程中是 1.37 这样的小数 —— 指示条要跟着手指连续变宽变窄，
 *   而不是"松手后才跳到下一页"（那正是改造前圆点的观感问题）。
 *
 * 取值方式：**离当前位置越近越宽**，距离按 1 个页宽归一。
 * 静止时只有一页的距离为 0（拉到 24dp），其余为 8dp；
 * 拖到两页中间时两条都是 16dp —— 手指下的那条一直在长，读者能看出"正在往哪边翻"。
 */
internal fun promoIndicatorWidths(
    pageCount: Int,
    position: Float,
    base: Float = PROMO_INDICATOR_BASE_DP,
    active: Float = PROMO_INDICATOR_ACTIVE_DP,
): List<Float> {
    if (pageCount <= 0) return emptyList()
    val clamped = position.coerceIn(0f, (pageCount - 1).toFloat())
    return (0 until pageCount).map { index ->
        val distance = abs(index - clamped).coerceIn(0f, 1f)
        base + (active - base) * (1f - distance)
    }
}

/**
 * 轮播指示条：当前页拉长 + **按自动翻页倒计时填充**。
 *
 * 为什么不用圆点：圆点只说"有几页、在第几页"，说不出"还有多久翻"；
 * 而这块是每 4 秒自己走的内容，用户需要知道它会不会在读到一半时被抢走。
 * 填充进度与 `PromoBanner` 的自动翻页用同一个时长，只是提示，不参与任何曝光统计
 * （本项目对推广位的红线：无埋点、无用户行为追踪）。
 *
 * @param position 连续位置（见 [promoIndicatorWidths]）
 * @param progress 当前页已过去的比例 0..1；关闭自动播放 / 系统要求减少动画时传 1
 *   —— 那时"进度"没有意义，指示条应当是一根实心条。
 */
@Composable
internal fun PromoIndicator(
    pageCount: Int,
    position: Float,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val widths = promoIndicatorWidths(pageCount, position)
    if (widths.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PROMO_INDICATOR_GAP_DP.dp),
    ) {
        widths.forEachIndexed { index, widthDp ->
            // 靠近程度 0..1：填充只在"你正看着的那几条"上出现，翻页途中两边都有一点
            val closeness = (1f - abs(index - position.coerceIn(0f, (pageCount - 1).toFloat())))
                .coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(PROMO_INDICATOR_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(50))
                    // 0.45 与改造前那组小圆点的非当前页同档：明显退后，但压在浅色海报上还看得见
                    .background(Color.White.copy(alpha = 0.45f)),
            ) {
                val filled = (closeness * progress.coerceIn(0f, 1f))
                if (filled > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(filled)
                            .fillMaxHeight()
                            .background(Color.White),
                    )
                }
            }
        }
    }
}
