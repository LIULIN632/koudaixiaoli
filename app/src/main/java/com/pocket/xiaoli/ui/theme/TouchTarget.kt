package com.pocket.xiaoli.ui.theme

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 本项目**触控目标下限**：44dp。
 *
 * ## 为什么是 44 而不是 Material 的 48
 *
 * 两个数都是真实标准，本项目取 44：
 * - Material 3 的 `minimumInteractiveComponentSize()` 是 **48dp**；
 * - Apple HIG / WCAG 2.5.5（AAA）给的底线是 **44pt**，WCAG 2.5.8（AA）是 **24px**。
 *
 * 项目从 2026-09 起就把 `tools/lint_design.py` 的触控检查定在 44dp，并在基线注释里
 * 写明"低于 44dp 才算违规"。这里**沿用同一个数**，不另立一套 —— 否则 linter 与代码
 * 会各说各话（一个 44 一个 48，改了以后 linter 还是绿的，没人知道差在哪）。
 *
 * ⚠️ 若将来决定整体提到 48，**这两处必须一起改**，并重跑 linter 基线。
 */
val MinTouchTarget: Dp = 44.dp

/**
 * 把**可点击区域**撑到 [MinTouchTarget]，而不改变可见尺寸与对齐。
 *
 * ## 用在哪
 *
 * 给那些"视觉上必须小、但手指够不着"的控件（周次箭头、色块、拖拽柄、结果行）。
 *
 * ## 为什么不是 `Modifier.size(44.dp)`
 *
 * `size()` 会**钉死尺寸**，同时把父容器给的约束也改掉 —— 视觉跟着变大，
 * 排版跟着位移。它修的是"看起来小"，不是"够不着"。
 *
 * ## 为什么用 [widthIn]/[heightIn] 而不是自定义 Layout
 *
 * `widthIn(min = …)` / `heightIn(min = …)` 只是**抬高下界**：
 * 内容自然尺寸小于下限时被撑到下限，大于下限时保持不动。
 * 所以它既不会压缩大控件，也不会像 `DefaultMinSize` 那样在别处被忽略。
 *
 * ⚠️ 它会**占位**：一个 28dp 的箭头套上之后占 44dp 宽。这是刻意的 ——
 * 偷来的热区会与邻居重叠，越小的控件越容易点错隔壁那个。
 * 若某处确实没有 44dp 的排版预算，正确做法是**改布局**，不是把热区做小。
 */
fun Modifier.invisibleTouchTarget(
    size: Dp = MinTouchTarget,
): Modifier = widthIn(min = size).heightIn(min = size)

/**
 * [invisibleTouchTarget] 的 Composable 版本，便于在参数默认值里直接用。
 *
 * 存在的理由：Modifier 的默认参数不能读 CompositionLocal（本项目暂无相关 Local），
 * 而把 44dp 直接写进每个默认参数里，就是又造了十几处魔数 —— 正是这个文件要消灭的东西。
 */
@Composable
fun minTouchTarget(): Modifier = Modifier.invisibleTouchTarget()
