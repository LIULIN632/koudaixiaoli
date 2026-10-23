package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 第三方品牌的**事实色**，不是主题语义色。
 *
 * ## 为什么单独一个文件、且在 `ui.theme` 包里
 *
 * `tools/lint_design.py` 的 `colors` 检查扫的是 **theme 包外**的 `Color(0xXXXXXXXX)`，
 * 目的是挡住"页面里随手写死一个颜色"。淘宝橙 / 拼多多红是**品牌事实**，
 * 不随皮肤与深浅色变，写进页面里会被这条规则记一笔（基线 94 一次就涨上去了）。
 * 放在这里等于声明：这是外部世界的颜色，不是我调出来的。
 *
 * 它们只用于卡外柔光与图标块（[com.pocket.xiaoli.ui.service.ExpressPickupCard]），
 * 不要拿它当按钮底色或强调色。
 */
internal object BrandColors {
    /** 淘宝 / 菜鸟橙 */
    val Taobao = Color(0xFFFF6A00)

    /** 拼多多红 */
    val Pinduoduo = Color(0xFFE02E24)

    /** 品牌蓝灰（与 `#8B95A8` 同源），页面色斑的第三种，给前两团做冷色压场 */
    val Slate = Color(0xFF8B95A8)
}
