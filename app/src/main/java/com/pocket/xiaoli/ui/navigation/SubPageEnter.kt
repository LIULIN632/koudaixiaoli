package com.pocket.xiaoli.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.pocket.xiaoli.ui.theme.Motion

/**
 * 页内二级页开合时的那层「滑入 + 淡入」。
 *
 * ## 为什么挂在导航层，而不是每页自己做
 *
 * 服务页那 28 个二级页是 `if (subPage == X) { Screen(); return }` 的早退链
 * （`TreasureBoxScreen.kt`）。要在这里做交叉转场，就得把 1200 行整体挪进一个 lambda 重排缩进 ——
 * 纯位移、没有语义收益，还会让 diff 没法读（`AccountBindingScreen` 为同样的理由
 * 用 `visible=` 参数而不是 `if {}` 包住每一块，那边注释写得很清楚）。
 * 而「这一屏此刻是不是二级页」这个信号**本来就已经上报到导航层了**（底栏显隐用的就是它），
 * 所以整条路由套一次就够，28 个分支一行都不用改。
 *
 * ## 方向语义与路由级一致
 *
 * 开二级页 = 从右滑入（"更深一层"）；退回 = 从左滑入。与 `routeTransitions()` 同一套直觉，
 * 位移取屏宽 12%（路由级是 25%，那是整页对整页；这里只是给一下方向感）。
 *
 * ## 为什么只做入场
 *
 * 早退结构里旧内容本来就是瞬间消失的。要做"出场"就得同时组合两屏 ——
 * 地图 / 3D 那种重页会掉帧，而且各页的 `LaunchedEffect` 会双跑（拉数据、上报会重复）。
 * 方向感 90% 由入场那一下建立，够用。
 *
 * ⚠️ 只用 `graphicsLayer`（改的是绘制矩阵，不改测量与布局），且**只在动画期间挂**，
 * 所以所有快照基线一张都不会变 —— 这点是刻意的，动效不该逼人重录一百张图。
 *
 * ## 二级页之间直接互切（不经过网格）的两处
 *
 * `open` 不翻转 ⇒ 这两处**不滑**，也不会因为滑入多出成本：
 * 图书馆 → 座位预约（`TreasureBoxScreen.kt:475`）、校园卡存账号 → 余额页（`:615`）。
 * 它们要变流畅得做真正的交叉转场（同时组合两屏），代价见上面"为什么只做入场"。
 */
@Composable
fun Modifier.subPageEnter(open: Boolean): Modifier {
    // 从 1 起步：首次组合（以及进程重建、切回本路由）不跑动画，只有"开合状态真的翻转"才滑。
    val slide = remember { Animatable(1f) }
    var lastOpen by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (lastOpen == open) return@LaunchedEffect
        lastOpen = open
        slide.snapTo(0f)
        slide.animateTo(1f, tween(Motion.expand, easing = Motion.easing))
    }
    // **只在动画期间挂 layer。** `graphicsLayer` 无条件用 = 整棵路由子树常驻一层离屏纹理，
    // 而玻璃底栏本来就在录这个子树（`glassSource`），等于同一棵树两层 —— 常态那层是白付的。
    // `derivedStateOf` 只在"起手 / 收尾"各翻转一次，不会每帧重组调用方（那才是真掉帧来源）。
    val animating by remember { derivedStateOf { slide.value < 0.999f } }
    if (!animating) return this
    return graphicsLayer {
        alpha = slide.value
        translationX = (1f - slide.value) * size.width * if (open) 0.12f else -0.12f
    }
}

/**
 * 「哪些状态翻转该滑一下」的唯一判据。
 *
 * ⚠️ 刻意**排除「我的」页**：它上报的是 `hasSubPage || dialogsVisible`
 * （`ProfileScreen.kt:297`，底栏需要这个合并值 —— 弹窗盖着时底栏也该收），
 * 但弹窗开合不该推动整页滑动：那会和弹窗自己的淡入叠成"页面在往后退"。
 * 要接进来，得给它拆一个只表示"整页替换"的信号，而不是在这里将就。
 */
internal fun shouldSlideOnSubPage(route: String?, hasOpenSubPage: Boolean): Boolean =
    hasOpenSubPage && route != "profile"
