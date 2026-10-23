package com.pocket.xiaoli.ui.glass

import kotlin.math.roundToInt

/**
 * 底栏拖拽里**纯计算**的那部分（跟手位移夹取、松手落位）。
 *
 * ## 为什么要单独抽出来
 *
 * 这段逻辑原来写在 Composable 的手势回调里。Compose 的手势回调跑在**受限挂起作用域**
 * （`AwaitPointerEventScope`）中，单测里极难驱动 —— 于是**最容易写错的一段代码一行测试都没有**。
 * 它的失效表现是"松手后胶囊回不到正确那一格"或"拖到边上跑到栏外"，
 * 只在真机上偶发，靠肉眼在几个版本之间回归几乎不可能。
 *
 * 抽成纯函数之后，边界条件（两端夹取、只有一格、宽度为 0）都能像下面这样逐条钉住。
 */
internal object BottomBarDragMath {

    /**
     * 跟手拖动时的位移边界：胶囊从 [baseIndex] 那一格出发，
     * 左不能越过第 0 格、右不能越过最后一格。
     *
     * [tabWidthPx] 为 0（尚未测量出来）时返回 0：宁可"暂时拖不动"，
     * 也不要因为除零算出一个巨大的位移把胶囊甩出屏幕。
     */
    fun clampOffset(proposedPx: Float, baseIndex: Float, tabWidthPx: Float, maxIndex: Float): Float {
        if (tabWidthPx <= 0f) return 0f
        val min = -baseIndex * tabWidthPx
        val max = (maxIndex - baseIndex) * tabWidthPx
        // ⚠️ `-0.0f` 不等于 `0f`（浮点的符号零）。在左边界 `coerceIn` 会返回 `-0.0f`：
        // 显示上完全一样，但精确比较会挂、日志里也会冒出 "-0.0"。
        // 加 0f 把符号零归一（IEEE：-0 + +0 = +0），值不变。
        // 这个坑是被 BottomBarDragMathTest 的边界用例抓出来的。
        return proposedPx.coerceIn(min, max) + 0f
    }

    /**
     * 松手落位：离哪一格最近就落到哪一格。
     *
     * 拖动过程中判断"有没有越过一格"（用来触发轻触感）也走这个函数 ——
     * 两处用同一套取整规则，"手感"和"落位"才不会打架
     * （如果显示用一套、落位用另一套，就会出现"手感和落点差一格"）。
     */
    fun targetIndex(baseIndex: Float, dragOffsetPx: Float, tabWidthPx: Float, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        if (tabWidthPx <= 0f) return baseIndex.roundToInt().coerceIn(0, itemCount - 1)
        return (baseIndex + dragOffsetPx / tabWidthPx).roundToInt().coerceIn(0, itemCount - 1)
    }
}
