package com.pocket.xiaoli.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * 把一块平面变成**液态玻璃**，并按 [GlassCapability] 三档自动降级。
 *
 * ## 参数全部对齐上游原件
 *
 * 这里的每个数值都不是我"看着调"的，而是逐条对应 `Kyant0/AndroidLiquidGlass` 的
 * `LiquidBottomTabs.kt`（Apache-2.0）。第一版我凭理解写了一遍，结果**四处偏差把效果全削弱了**，
 * 记在这里避免重犯：
 *
 * | 项 | 原件 | 我的错误版本 | 后果 |
 * |---|---|---|---|
 * | 表面色不透明度 | 0.40 | 0.62 | 玻璃被自己的底色盖住，折射看不出来 |
 * | `vibrancy()` | 调用 | 漏了 | 背后内容饱和度不提升，玻璃发灰发闷 |
 * | 采样外扩 `padding` | 不显式设 | 手写 62px | 采样区远大于栏体 → 采到栏外内容，画面被拉宽失真 |
 * | 投影 `Shadow` | `Shadow()` | 没加 | 栏不像浮起来，观感与旧版几乎一样 |
 *
 * ## 三档降级
 *
 * - **Android 13+**：`vibrancy` + `blur` + `lens` 折射 + 高光 + 投影
 * - **Android 12**：`vibrancy` + `blur` + 高光 + 投影（无折射）
 * - **Android 10–11**：不透明底 + 高光描边
 *
 * ## 为什么表面色由调用方传**不透明**色
 *
 * 透明度必须由能力档决定：能模糊时用 0.40 让背后内容透出来，不能模糊时若仍用 0.40，
 * 背后的内容会**清清楚楚**地穿过栏体，栏上的文字直接读不清。
 * 把决定权收在这里，调用方就不可能再犯"在低版本上也用 0.4"的错。
 *
 * @param surfaceColor **不透明**的表面色，透明度由本函数按能力档决定
 */
fun Modifier.glassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    surfaceColor: Color,
    blurRadius: Dp = 8.dp,
    refractionHeight: Dp = 24.dp,
    refractionAmount: Dp = 24.dp,
    dropShadow: Shadow? = Shadow.Default,
): Modifier {
    val capability = GlassCapability.current

    // ── 磨砂档 / 没有采样源：不透明底 + 高光描边 ──
    // 描边固定用白色：它模拟的是**高光**，不是主题语义色，所以不跟主题走。
    if (backdrop == null || !capability.canBlur) {
        return this
            .clip(shape)
            .background(surfaceColor)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    }

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // ⚠️ 顺序不能换：vibrancy 会先挂上 renderEffect，
                // 之后 blur 才会据此把 padding 抬到自己的半径（Blur.kt 里的条件分支）。
                // 少了这一步，采样外扩就只剩 lens 那点，边缘会留一条没虚化的硬缝。
                vibrancy()
                blur(radius = blurRadius.toPx())
                if (capability.canRefract) {
                    lens(refractionHeight.toPx(), refractionAmount.toPx())
                }
                // 刻意不写 `padding = ...`：由 blur / lens 自己累积。
                // 手上写死过一次 62px（原件实际约 20px），采样区远大于栏体，
                // 结果是"采到了栏外的内容"，画面反而失真。
            },
            highlight = { Highlight.Default },
            shadow = dropShadow?.let { shadow -> { shadow } },
            // 半透明底：不画它，栏上的字在浅色内容上会读不清。
            //
            // 用**竖向渐变**而不是纯色：上亮下暗能给出一点"实体厚度"，
            // 这样在背后是纯色、模糊等于没模糊的页面上，栏也不会读成一张平板白卡片 ——
            // 这是让它"不挑背景"的关键一步。
            onDrawSurface = {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            surfaceColor.copy(alpha = 0.46f),
                            surfaceColor.copy(alpha = 0.30f),
                        )
                    )
                )
            },
        )
        // 一圈「上亮下暗」的白色渐变描边。
        //
        // 这条边是玻璃**实体感**的来源，而且**不依赖背后有什么** ——
        // 在纯色背景上模糊等于没模糊，整块会读成"白色卡片"；有了这道边才立得住。
        // 只用白色、不用主题色：它模拟的是玻璃边缘的高光，不是语义色。
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.06f),
                )
            ),
            shape = shape,
        )
}
