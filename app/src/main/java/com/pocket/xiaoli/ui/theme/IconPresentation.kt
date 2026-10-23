package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/**
 * 图标的**笔画风格** —— 线性描边 / 面性填充。
 *
 * ## 为什么与 `IconMode` 分开（而不是合成一个枚举）
 *
 * 这是两个**正交**维度，合在一起会把"颜色"与"形状"两件事绑死：
 *
 * | | `IconMode` | `IconStyle` |
 * |---|---|---|
 * | 决定 | 图标**上不上色** | 图标**怎么画** |
 * | 归属 | 上限（皮肤只有否决权） | 纯风格特征 |
 * | 受「用色强度」影响 | ✅ 参与合成 | ❌ 恒定跟随皮肤 |
 *
 * 合成规则见 [resolveIcons]。
 *
 * ## 怎么落地（不需要任何新图片资源）
 *
 * 项目的 UI 图标**全部是可着色的 Material 矢量**（`res/drawable/` 里没有任何
 * 多色 UI 资源，只有 `empty_state_dog.webp` 与两个 xml），所以：
 * - "线性"用 `Icons.Outlined.*`
 * - "面性"用 `Icons.Filled.*`
 *
 * 两套都在已有依赖里。代价是每个图标要**成对登记** —— 见
 * `ServiceEntry.iconFilled`（默认值 = 原图标，所以未登记 Filled 变体的条目
 * 行为完全不变，可以渐进补齐）。
 */
enum class IconStyle {
    /** 线性：`Icons.Outlined.*` —— 描边、轻、克制（极简） */
    OUTLINED,

    /** 面性：`Icons.Filled.*` —— 填充、实、有重量（鲜活） */
    FILLED,
}

/**
 * 图标与角标的**最终呈现策略** —— `SkinTokens` 与 `AppearanceMode` 的合成结果。
 *
 * ## 这是页面唯一该读的东西
 *
 * 页面**不要**自己写 `if (skin.iconMode == ...)`，也不要直接读
 * `AppearanceMode`。两个输入轴 → 一个输出对象，判断只做一次、只做在一个地方：
 *
 * ```kotlin
 * val icon = currentIconPresentation()
 * Icon(
 *     imageVector = if (icon.filled) entry.iconFilled else entry.icon,
 *     tint = if (icon.colorful) serviceAccentColor(entry.accent) else colors.textSecondary,
 * )
 * ```
 *
 * 好处是"极简 = 单色线性图标"这条风格承诺由**数据**保证，
 * 而不是靠 43 个调用点各自记得判断 —— 后者一定会漏。
 *
 * ## 为什么不放进 `SkinTokens`
 *
 * 因为它**不是皮肤数据**：它是"皮肤 × 用户偏好"的**推导结果**。
 * 塞进 `SkinTokens` 会让同一个视觉结论有两个存放处，
 * 那正是本项目"换了皮肤没变化"那类问题的来源。
 */
@Immutable
data class IconPresentation(
    /** 图标是否按语义类别上色（`ServiceAccent` 六类色）。false = 统一中性前景色。 */
    val colorful: Boolean,

    /** 用面性（`Icons.Filled.*`）还是线性（`Icons.Outlined.*`）图标。 */
    val filled: Boolean,

    /**
     * 是否绘制图标背后的**彩色衬底**（"面性"的那层底）。
     *
     * 与 [colorful] 同生共死：单色态若还留一个彩色底，就不叫"单色"了。
     *
     * ⚠️ **关掉它不等于去掉那层底** —— 现状是改用**中性**衬底
     * （`LocalPocketXiaoliColors.surfaceHighlight`），以保住既有「纯净模式」的观感
     *（改造前 `isPure` 画的就是这个中性底，不能借这次改动把老用户的观感一起改了）。
     *
     * ⚠️ 消费方**不要**连带删掉那个 `Box`：它在布局上承担图标的尺寸与垂直居中锚点，
     * 删掉会让行高跳变。正确做法是只换它的 `background`。
     */
    val showContainer: Boolean,

    /** 角标 / 标签是否按语义上色。同样受皮肤的彩色上限约束。 */
    val colorfulTags: Boolean,
)

/**
 * 把"皮肤上限"与"用户偏好"合成为最终图标策略 —— **纯函数，便于单测**。
 *
 * ## 合成规则
 *
 * ```
 * colorful = (skin.iconMode == COLORFUL) && (appearance == RAINBOW)
 * ```
 *
 * | 皮肤 | 用色强度 | colorful | filled | showContainer | colorfulTags |
 * |---|---|---|---|---|---|
 * | 极简 | 彩虹 | ❌ | ❌ 线性 | ❌ | ❌ |
 * | 极简 | 纯净 | ❌ | ❌ 线性 | ❌ | ❌ |
 * | 鲜活 | 彩虹 | ✅ | ✅ 面性 | ✅ | ✅ |
 * | 鲜活 | 纯净 | ❌ | ✅ 面性 | ❌ | ❌ |
 *
 * ## 三条刻意的设计决定
 *
 * 1. **皮肤只否决、不做加法。** 极简声明 `MONOCHROME` 后，用户选彩虹也上不了色。
 *    如果不这样，"极简 = 单色线性"就只是默认值而非承诺，用户一改就打脸。
 * 2. **线形 / 面性与用色强度正交。** 于是「鲜活 + 纯净」得到**面性但单色**。
 *    好处是"用色强度"这个旋钮只改颜色，不会让图标的**几何形状**也跳变。
 * 3. **衬底与彩色同生共死。** 见 [IconPresentation.showContainer]。
 *
 * ## 为什么必须让 `AppearanceMode` 参与
 *
 * `AppearanceMode`（彩虹 / 纯净）**已经**在决定"图标是否按语义上色"，
 * 现成消费者 8 处。若皮肤再独立表态一次，"鲜活 + 纯净"下两个开关就会
 * 互相矛盾、谁也说不清该听谁。合成把话语权分清楚：**皮肤定上限、用户定偏好**。
 *
 * @param appearance 用户在「用色强度」里的选择。
 */
fun SkinTokens.resolveIcons(appearance: AppearanceMode): IconPresentation {
    // 上限语义：皮肤声明 MONOCHROME 时，任何用色强度都不能把它变成彩色。
    val colorful = iconMode == IconMode.COLORFUL && appearance == AppearanceMode.RAINBOW
    return IconPresentation(
        colorful = colorful,
        // 线形 / 面性正交于用色强度：极简=线性、鲜活=面性，恒定。
        filled = iconStyle == IconStyle.FILLED,
        // "面性"的彩色衬底与彩色同生共死。
        showContainer = colorful,
        // 角标同样受皮肤的彩色上限约束（colorful 已含该约束）。
        colorfulTags = colorfulTags && colorful,
    )
}

/**
 * 当前生效的图标策略 —— 页面取值的**唯一入口**。
 *
 * 用法见 [IconPresentation] 的 KDoc。已 `remember`，同一皮肤 + 同一用色强度下
 * 不会重复推导。
 *
 * ⚠️ 与 `LocalSkin.current` 一样，必须写在**任何早返回分支之前** ——
 * 否则早返回路径拿不到策略（历史上 `NextCourseCard` 踩过同类坑）。
 */
@Composable
fun currentIconPresentation(): IconPresentation {
    val skin = LocalSkin.current
    val appearance = LocalAppearanceMode.current
    return remember(skin, appearance) { skin.resolveIcons(appearance) }
}
