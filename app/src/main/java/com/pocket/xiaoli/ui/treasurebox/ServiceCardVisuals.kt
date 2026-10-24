package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 服务页「富卡片 / 常用服务格 / 金刚格」共用的**两层**绘制：类目色底 + 顶部高光。
 *
 * ## 为什么要抽出来
 *
 * 这三种格子原本各写一份渐变（0.42/0.16、0.30/0.10、0.32/0.12），
 * 于是同一页里"卡片"和"格子"的浓淡、方向、高光全不一样 —— 远看像三套设计拼在一页。
 * 抽成同一套之后，改一处就三处同时生效，也不会再出现"只修了卡片、格子还是旧的"。
 *
 * ## 两层各自解决什么
 *
 * 1. **类目色底（[serviceSurfaceBrush]）**：三档浓度代替两档平铺。
 *    两档渐变在中段直接线性过渡，颜色"摊"得很平，看着像一张平涂的色纸；
 *    三档在 55% 处留了一个中间色，卡面才有从上到下的"受光"层次。
 * 2. **顶部高光（[serviceTopGloss]）**：一条从顶部 22% 白逐渐消失的竖向渐变。
 *    只靠类目色加深，卡片会像"贴纸"；加一层高光后才像一块有厚度的面板。
 *    深色主题用约一半强度 —— 暗底上白高光过量会直接泛灰。
 *
 * ⚠️ 两层都**只在彩色态调用**。极简皮肤是"单色线性"的承诺，
 * 给它加高光等于偷偷反悔 —— 纯色卡上多一道白渐变怎么解释都不对。
 *
 * 另有 [serviceAccentRail]：富卡片专用的左缘类目色短竖条（只给彩色卡，理由见其 KDoc）。
 */

/**
 * 类目色底：上浓、中、下淡，模拟从左上打过来的光。
 *
 * @param topAlpha 顶端浓度。大卡用默认 0.46；小金刚格面积小，浓度高了会显脏，用 0.40。
 *   中间/底部按比例派生（0.44 / 0.26），保证任何浓度下层次比例一致。
 */
internal fun serviceSurfaceBrush(accent: Color, topAlpha: Float = 0.46f): Brush =
    Brush.linearGradient(
        0f to accent.copy(alpha = topAlpha),
        0.55f to accent.copy(alpha = topAlpha * 0.44f),
        1f to accent.copy(alpha = topAlpha * 0.26f),
    )

/**
 * 顶部白色高光。必须画在类目色底**之后**、文字**之前**（`drawBehind` 天然满足）。
 *
 * @param dark 深色主题下强度减半，否则暗底 + 22% 白 = 一层灰雾。
 * @param strength 相对强度，快捷格这类小面积用 0.7 左右即可。
 */
internal fun Modifier.serviceTopGloss(dark: Boolean, strength: Float = 1f): Modifier = drawBehind {
    val peak = (if (dark) 0.10f else 0.22f) * strength
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = peak),
            0.52f to Color.White.copy(alpha = peak * 0.25f),
            1f to Color.Transparent,
        )
    )
}

/**
 * 富卡片左侧的类目色竖条：**贴左缘、只覆盖两段圆角之间的直边**的一段圆头竖条。
 *
 * ## 长度为什么是"卡片高度 − 2×圆角"
 *
 * 竖条的长度试过三种，只有这一种同时满足"不会裁、不会挤、不会随卡片变高而变短"：
 *
 * - **贴边通高**：20dp 圆角会把上下两端切成月牙，深色竖条贴上去像一道没对齐的边框；
 * - **与图标块等高**：小卡上刚好，但 1.3 倍字号把卡片撑到 100dp 后，
 *   34dp 的竖条缩在中间，像一枚不知道属于谁的刻度；
 * - **内缩通高（x=5dp）**：横向内边距只有 10dp，竖条和图标块只剩 2dp 间距，两根竖线挤在一起。
 *
 * 取 `半径` 作上下内缩之后，竖条正好从中段直边的一端走到另一端：
 * 两端**永远碰不到弧线**（所以不会被裁），长度**跟着卡片高度走**（所以大字号下不缩水），
 * 而且横向只占 3dp，不碰图标块。
 *
 * ## 为什么只有富卡片有
 *
 * 常用服务格（54dp 宽）和金刚格（44dp 方块）本身就已经很小，
 * 左边再加一条 3dp 的竖条会直接吃掉布局宽度；它们靠图标衬底表达类目即可。
 *
 * 颜色用竖向渐变（0.92 → 0.58）而不是实色：实色的 `accentTint` 明度只有 0.6 出头，
 * 通高的实色条压在浅卡上会像"贴了一条深色胶带"；上浓下淡和卡片同一套受光方向。
 *
 * @param insetY 上下内缩量，传卡片的圆角半径即可。
 */
internal fun Modifier.serviceAccentRail(accent: Color, insetY: Dp): Modifier = drawBehind {
    val railWidth = 3.dp.toPx()
    // 与卡片左右边缘的"直段"等高：竖条只覆盖两段圆角之间的部分，两端永远碰不到弧线。
    val railHeight = (size.height - insetY.toPx() * 2f).coerceAtLeast(0f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to accent.copy(alpha = 0.92f),
            1f to accent.copy(alpha = 0.58f),
        ),
        topLeft = Offset(0f, (size.height - railHeight) / 2f),
        size = Size(railWidth, railHeight),
        cornerRadius = CornerRadius(railWidth / 2f),
    )
}

/**
 * 彩色卡上图标衬底的白色渐变。
 *
 * 纯白平涂在一块彩色卡上像"贴了一块白纸"，顶端留 1 点灰之后有了厚度；
 * 且白底能把类目色的 glyph 衬到最高对比 —— 彩色卡上继续用同色衬底的话，
 * 图标和卡面只差一个色阶，等于没有图标。
 */
internal val ServiceIconTileBrush: Brush = Brush.linearGradient(
    0f to Color.White,
    1f to Color(0xFFF2F3F7),
)
