package com.pocket.xiaoli.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow

/**
 * 把一块顶栏变成**液态玻璃**（长条形状，与底栏分开实现）。
 *
 * ## 与底栏（[glassSurface]）的三处区别，都不是随手改的
 *
 * 1. **不画四周高光描边，只画底边一条发丝线** —— 顶栏的上边贴屏幕边，
 *    四周描边在顶上会变成一条悬空的亮线。底边这条线是必需的：
 *    没有它，滚上来的内容会直接贴到标题上，两者糊成一片。
 * 2. **底色更实（0.72 → 0.55 的竖向渐变）** —— 顶栏上永远压着标题和按钮，
 *    透明度必须比底栏高，否则内容滚过时标题读不清。
 * 3. **不做折射、不投影** —— 顶栏是贴边的，折射在直边上没有立足点；
 *    投影在贴边条上会变成一条"老 Android 的 elevation 分界线"。
 *
 * ## 采样源必须**不含顶栏自己**
 *
 * [backdrop] 要录的是"页面内容"，**不能**把顶栏也录进去 —— 采样源录到自己会成环，
 * 那是 RenderThread 级 SIGSEGV（原生崩溃，catch 不住）。所以调用方要把录制挂在
 * 顶栏的**兄弟节点**（Scaffold 的内容区）上，而不是外层容器上。见 `AppBackdrop.kt`。
 *
 * @param surfaceColor 顶栏底色（传**不透明**色，透明度由本函数按档位决定）
 * @param dividerColor 底边发丝线的颜色（通常是 `borderDivider`）
 */
fun Modifier.glassTopBarSurface(
    backdrop: Backdrop?,
    surfaceColor: Color,
    dividerColor: Color,
    blurRadius: Dp = 12.dp,
): Modifier {
    val capability = GlassCapability.current
    // 深浅由底色自己判：调用方传的是各主题的 surfaceBase，不另外引入一个 isDark 参数，
    // 否则迟早出现"参数说亮、底色是暗"的错配。
    val dark = surfaceColor.luminance() < 0.4f

    // 没有采样源时**不能**半透明：底下那层 drawBackdrop 不存在，半透明只会让滚过的内容
    // 生硬地透出来，读起来比实心还糟。所以"能不能磨砂"要连 backdrop 一起判，不能只看档位。
    val hasGlass = backdrop != null && capability.canBlur

    // ① 底色垫在最底下。
    //
    // 下面那层 drawBackdrop 采的是**内容区录下来的图层**；图层还没画出来（进页面第一帧）、
    // 或者背后根本空无一物（空态欢迎页、短列表、滚到顶）时，模糊没有任何像素可采。
    // 没有这层垫底，顶栏会变成一条半透明缝，看着就是"这次没开磨砂"。
    //
    // 但垫底只能薄薄一层：垫厚了玻璃就退化成一条不透明色带，背后内容糊得再动也透不上来。
    // 0.40 是"淡色页面上仍能兜住标题"与"模糊还看得见"之间的折中。
    val base = this.drawBehind {
        drawRect(surfaceColor.copy(alpha = if (hasGlass) 0.40f else 1f))
    }

    val body = if (backdrop == null || !capability.canBlur) {
        // 磨砂档 / 没有采样源：不描边、不加模糊，只靠底色与下面那道高光渐变表意
        base
    } else {
        base.drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                vibrancy()
                blur(radius = blurRadius.toPx())
            },
            highlight = null,
            shadow = null,
            onDrawSurface = {
                // 上浓下淡：顶部保证标题可读，往下逐步透出内容。
                // 与垫底叠加后顶部约 0.75、底部约 0.60 的不透明度 —— 再实就看不见模糊了。
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            surfaceColor.copy(alpha = 0.58f),
                            surfaceColor.copy(alpha = 0.34f),
                        )
                    )
                )
            },
        )
    }

    return body.drawBehind {
        // ② 上缘到中部的一道白色高光。
        //
        // 这是"不依赖背后内容"的那半边玻璃：即便背后是纯色、模糊看不出任何差别，
        // 顶亮下暗的落差也会让这块材质读成"一块有厚度的玻璃"，而不是一条平色带。
        // 深色主题下压到 0.10 —— 白高光在暗底上过于扎眼会显得脏。
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.10f else 0.26f),
                0.55f to Color.Transparent,
                1f to Color.Transparent,
            )
        )
        // ③ 底边发丝线：没有它，滚上来的内容会直接贴到标题上，两者糊成一片。
        val stroke = 1.dp.toPx()
        val y = size.height - stroke / 2f
        drawLine(
            color = dividerColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
        )
    }
}

/**
 * 顶栏上的**玻璃小件**（圆形按钮、徽标）—— 与 [glassTopBarSurface] 是同一块材质，只是尺寸小一号。
 *
 * ## 为什么不能再用实心浅底
 *
 * 磨砂顶栏是半透明的。压在上面的实心色块读起来是"贴在玻璃上的一张不干胶"，
 * 两种材质互相打架（用户的原话："那几个按钮跟磨砂材质不匹配"）。
 * 这里把它做成**同一块玻璃**：背后内容照糊，透出一层淡淡的主色系底，
 * 边上一条白色高光（与底栏玻璃同一套高光语法，见 [glassSurface]）。
 *
 * ## 与底栏 [glassSurface] 的两处区别
 *
 * - **不做折射（lens）**：折射是给长条形玻璃做"侧面厚度"的，在 34dp 的圆上没有立足点；
 * - **不投影**：投影会让按钮看着像浮在顶栏上的贴片，而它本来就该是顶栏的一部分。
 *
 * 边缘用 `innerShadow` 而不是白色描边 —— 与底栏那颗**选中胶囊**同一套做法
 * （见 `GlassBottomBar`：那条注释写着"内阴影给胶囊一圈边缘，否则一块淡色会糊在栏里没有形"）。
 * 白色高光描边是给**整条栏**的边框用的，小件上再镶一圈白会显得镶了金边。
 *
 * 降级与其它玻璃一致：不能模糊 / 没有采样源时退回**半透明底 + 白色高光描边**。
 * 这里刻意不用不透明底 —— 否则又变回"不干胶"，而半透明在纯色页面上也依然读得出来。
 *
 * @param surfaceColor 小件的底色（传**不透明**色，透明度由本函数按档位决定）
 */
fun Modifier.glassChipSurface(
    backdrop: Backdrop?,
    surfaceColor: Color,
    shape: Shape = CircleShape,
    blurRadius: Dp = 8.dp,
): Modifier {
    val capability = GlassCapability.current

    if (backdrop == null || !capability.canBlur) {
        return this
            .clip(shape)
            .background(surfaceColor.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.40f), shape)
    }

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 顺序与底栏一致，不能换：vibrancy 先挂 renderEffect，blur 再据此把采样外扩到自己的半径
                vibrancy()
                blur(radius = blurRadius.toPx())
            },
            highlight = null,
            shadow = null,
            // 内阴影给小件一圈边缘，否则一块淡色会"糊"在栏里没有形
            innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.30f) },
            // 主题色淡染。底栏胶囊的注释里量过：模糊会把底色一起虚化，
            // 太淡在浅色主题上根本看不出是一颗按钮
            onDrawSurface = { drawRect(surfaceColor.copy(alpha = 0.34f)) },
        )
}
