package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.miuixPress

/**
 * 通用卡片容器 —— 全 App 卡片的**唯一**渲染入口。
 *
 * ## 职责边界（重要）
 *
 * 三层各管各的，**不要越界**：
 *
 * | 层 | 管什么 | 不管什么 |
 * |---|---|---|
 * | `SkinTokens`（数据） | 圆角多少、有没有阴影、卡片什么底色 | 任何具体页面 |
 * | **`AppCard`（本文件）** | 把 skin 的字段组装成 `Modifier` | 硬编码尺寸 / 决定"该不该有阴影" |
 * | 页面（使用方） | `AppCard { … }` 声明"这里要一张卡" | 形状 / 底色 / 阴影 |
 *
 * ## 关键设计：**没有任何 `if (isMinimal)` / `if (isVivid)`**
 *
 * 两套风格的差异**全部由 `SkinTokens` 的数据驱动**：
 * - Minimal：`shadowBlur = 0.dp` + `shadowColor = Transparent` → 自动不画阴影
 * - Vivid：`shadowBlur = 16.dp` + `shadowColor = 10% 黑` → 自动画弥散阴影
 *
 * 这样加第三套主题时**不需要改本文件** —— 这正是"数据驱动"与"分支硬编码"的分界。
 *
 * ## 阴影为什么用 `dropShadow` 而不是 `shadow`
 *
 * 1. `Modifier.shadow` 只接受 elevation，**无法表达"零模糊的硬阴影"**
 * 2. 参考实现（岱宗盒子 `ExpressiveSurface`）实测记录：`Modifier.shadow` 在部分机型
 *    会带一圈不自然的灰边；`dropShadow`（ui 1.12.0+）可精确控制 radius / offset /
 *    spread / color
 *
 * ⚠️ 本项目的 Compose BOM `2026.08.00` 已满足该 API 要求（已用最小样例实测可编译）。
 *
 * ## 用法
 *
 * ```kotlin
 * AppCard(modifier = Modifier.fillMaxWidth()) {
 *     Text("下一节课", style = PocketTypography.cardTitle)
 * }
 *
 * // 可点击
 * AppCard(onClick = { open() }) { Text("去上课") }
 * ```
 *
 * ## 默认卡色：为什么必须走 `surfaceCard` 而不是 `skin.cardColor*`
 *
 * `skin.cardColorLight/Dark` 是皮肤的**目标裸色**（`#FFFFFF` / `#1E1E1E`），
 * 它必须先经 `SkinTokens.applySurfaces` 与配色主题 `lerp` 才是能上屏的颜色：
 * `surfaceCard = lerp(theme.surfaceCard, skin.cardColor, surfaceShift)`，
 * 其中 `surfaceShift` = 0.85（极简）/ 0.35（鲜活）。
 *
 * 所以**直接读裸色会丢掉 15%（极简）/ 65%（鲜活）的配色主题色相** ——
 * 选了「马卡龙粉」的用户，卡色会变成中性白。
 *
 * 本组件此前正是这么写的（2026-10 修）。改为读推导链的**下游**
 * （[LocalPocketXiaoliColors] 的 `surfaceCard`）后，整条链路才自洽：
 *
 * ```
 * skin.cardColor*  →  applySurfaces()  →  LocalPocketXiaoliColors.surfaceCard  →  AppCard
 * ```
 *
 * `skin.cardColor*` 因此**仍有消费者**（就是 `applySurfaces`），不是死字段。
 *
 * ## ⚠️ 当前接入状态（2026-10 实测）
 *
 * 本组件**已实现但远未替换存量页面**。实测存量 `Card(` 调用点 **74 处**，
 * 分布在 37 个文件（历史注释与 `docs/AppearanceGuide.md` 写的 76 / 77 是漂移值）。
 *
 * ⚠️ **关键：那 74 处全部传了显式 `shape=` 与 `colors=`。** 所以把 `Card(`
 * 机械替换成 `AppCard(` **一处都不会变** —— 显式 `shape` 仍压过
 * `skin.radiusCard`，显式 `colors` 仍提供底色。**必须逐点删掉这些实参**，
 * 皮肤才真正生效。
 *
 * 另外别漏掉一类不在 `Card(` 清单里的卡片壳：**22 处 `Surface(color = surfaceCard)`**
 *（如 `TodayCampusScreen.SurfaceCard`、`ErrorCard`、`ImportEntryCard`）——
 * 它们同样没有阴影路径。
 *
 * 皮肤阴影目前只覆盖首页的 7 张卡（经 `SoftCard` 间接使用）。按渐进原则分批迁移。
 *
 * ## 刻意**不**支持 `elevation`
 *
 * 存量有 19 处传 `elevation=`：10 处是 `0.dp`（迁移时直接删），9 处是非零的
 * 1–2dp，语义是"这张卡被选中 / 浮起"。
 *
 * 不给本组件加 `elevation` 参数，是因为那会引入**第二套阴影体系** ——
 * 皮肤的弥散阴影（blur + offset）与 M3 的硬投影语义不同，混用会打架。
 * 需要"浮起"提示的卡片应改用语义化**描边**（见 [border]）：项目已有这条纪律，
 * 异常/强调状态用 `surfaceCard` 底 + 描边表达，而不是靠投影深浅
 *（深色主题下黑色投影几乎不可见）。
 *
 * 那 9 处本轮**保持原样不迁移**，属已知例外。
 *
 * @param modifier 外部修饰符。**不会**被内部覆盖 —— 内部只用 `then()` 追加。
 * @param onClick 非 null 时卡片可点击，并带 MIUIX 按压反馈（Sink）。
 * @param shape 覆盖默认圆角。默认取 `skin.radiusCard`；只有"这不是普通卡片"时才传
 *   （例如对话框内嵌块，或确实要 `skin.radiusLarge` 的大卡片）。
 *   **不要**为了"好看一点"传自定义值 —— 那会破坏圆角体系。
 * @param containerColor 覆盖卡片底色。默认取 [LocalPocketXiaoliColors] 的
 *   `surfaceCard`；仅在"这张卡需要语义色"时传（如首页的深绿主视觉卡）。
 *   ⚠️ **不要**传 `skin.cardColorLight/Dark` —— 见上方「默认卡色」一节。
 * @param border 覆盖描边。默认走皮肤的 `borderWidth` / `borderColor*`
 *   （两套皮肤目前都是 `0.dp`，即无描边）。用于表达选中态 / 危险态等语义。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape? = null,
    containerColor: Color? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // ⚠️ 必须在任何早返回之前读取（历史上 NextCourseCard 踩过：早返回路径拿不到令牌）
    val skin = LocalSkin.current
    val isDark = LocalDarkMode.current
    val colors = LocalPocketXiaoliColors.current

    val resolvedShape = shape ?: RoundedCornerShape(skin.radiusCard)
    // ── 底色：读推导链的**下游**，不读皮肤的目标裸色 ──
    // 理由见 KDoc「默认卡色」一节：skin.cardColor* 未经 applySurfaces 与配色主题
    // 混合，直接用会丢掉 15%（极简）/ 65%（鲜活）的主题色相。
    val resolvedColor = containerColor ?: colors.surfaceCard

    // ── 阴影：由数据决定，不在这里判断风格 ──
    val shadowColor = if (isDark) skin.shadowColorDark else skin.shadowColorLight
    val hasShadow = skin.shadowBlur > 0.dp || shadowColor != Color.Transparent

    val shadowModifier = if (hasShadow) {
        Modifier.dropShadow(
            shape = resolvedShape,
            shadow = Shadow(
                radius = skin.shadowBlur,
                spread = 0.dp,
                color = shadowColor,
                offset = DpOffset(skin.shadowOffsetX, skin.shadowOffsetY),
            ),
        )
    } else {
        Modifier
    }

    // ── 描边：显式传入优先；否则同样由数据决定（borderWidth = 0 时自动省略）──
    val resolvedBorder = border ?: if (skin.borderWidth > 0.dp) {
        BorderStroke(
            width = skin.borderWidth,
            color = if (isDark) skin.borderColorDark else skin.borderColorLight,
        )
    } else {
        null
    }
    val borderModifier = if (resolvedBorder != null) {
        Modifier.border(resolvedBorder, resolvedShape)
    } else {
        Modifier
    }

    // 可点击时给 MIUIX 按压反馈（Sink），与首页胶囊、底栏同一套手感。
    // 为什么不是 ripple：卡片面积大，水波纹会铺满整张卡；而卡片此前用
    // `indication = null` 连回应都没有 —— 点「下一节课」卡只有跳转、没有触感，
    // 这是真实的交互缺陷。Sink 的轻微下沉在大卡片上更克制。
    val clickModifier = if (onClick != null) {
        Modifier.miuixPress(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(shadowModifier)
            .clip(resolvedShape)
            .background(resolvedColor)
            .then(borderModifier)
            .then(clickModifier),
        content = content,
    )
}
