package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 核心颜色令牌。
 *
 * ## 设计要点
 *
 * **中性底 + 单一强调色 + 少量状态色** —— 这是"看起来高级"的用色纪律。
 * 反例是"课程=蓝、成绩=绿、校园卡=黄、通知=红、地图=紫、AI=粉"，
 * 颜色一多就变成功能大杂烩，用户记不住任何一个颜色的含义。
 *
 * 所以：
 * - 绝大多数界面用 [surfaceBase] / [surfaceCard] / [surfaceAlt]（中性）+
 *   [textPrimary] / [textSecondary] / [textTertiary]（中性文字）
 * - **只有主操作/选中态**用 [accentMain]
 * - **[success] / [warning] / [danger] / [info] 仅用于表达状态**，
 *   不用于"给每个功能配一个颜色"
 *
 * ## 后加的字段用默认值
 *
 * [success] / [warning] / [info] 是后加的，因此**用默认参数值**，
 * 这样已有的 16 个主题实例（8 套 × 明暗）不需要逐个修改就能编译通过。
 * 若某套主题需要专门的语义色，单独覆盖即可。
 */
@Immutable
data class PocketXiaoliColors(
    val surfaceBase: Color,        // 页面大背景
    val surfaceCard: Color,        // 卡片/顶栏背景
    val surfaceAlt: Color,         // 次级背景
    val surfaceHighlight: Color,   // 今日高亮 / accent 柔和背景
    val textPrimary: Color,        // 主标题文字
    val textSecondary: Color,      // 辅助说明文字
    val textTertiary: Color,       // 弱化文字/图标
    val borderDivider: Color,      // 分割线/卡片边框
    val danger: Color,             // 危险操作（红）
    val accentMain: Color,         // 动态主色
    /**
     * ── 语义状态色 ──
     *
     * ⚠️ **刻意不给默认值**。它们原先是默认参数（Material 500 号色），注释写着
     * "带默认值以免改动全部主题实例" —— 而这份便利的后果是：
     * **8 套配色一套都没覆盖它们**，于是同一个 `#FF9800`
     * 在暗色底上约 9:1（清楚）、在浅色底上只有 **1.96:1**（读不出）。
     * 而全 App 有 16 处拿它们当文字色用（实测见 `ThemeContrastTest`）。
     *
     * 去掉默认值之后，**新增配色必须显式声明这三个**，漏了编译器会拦住 ——
     * 这正是它该有的样子：一个"可以默默继承错值"的字段，迟早会继承一个错值。
     * 取值统一用下方 `*OnLight` / `*OnDark` 两套常量。
     */
    val success: Color,
    val warning: Color,
    val info: Color,
    /**
     * **总进度金**（培养方案总学分 / 总进度环）。
     *
     * ⚠️ 这不是"再给某个功能配一个颜色"——`DesignTokens` 开头那条纪律（中性底 + 单一强调色）
     * 依然成立。它的语义是**层级**：一页里同时存在"总进度"与"各类型进度"两种同形状的条，
     * 用户看不出哪条是总计（2026-10 用户原话：「主要进度条的颜色可以改成金色，正好方便区别」）。
     *
     * 取值 = 金属金的**暗端**（[MetallicGoldEnd]）。渐变亮端与高光见上面那三个常量 ——
     * 渐变要靠 `Brush` 画，而"需要一个单色金"的地方（如环的兜底色）用这个。
     * **明暗两档同值**：金属金是图形填充（不承载小字），压在深色底上反而更亮、更稳。
     */
    val progressMaster: Color = MetallicGoldEnd,
)

// ═══ 语义状态色：按明暗分档 ═══
//
// Material 的 500 号色（#4CAF50 / #FF9800 / #2196F3）是给**填充与大色块**设计的，
// 当**文字**压在浅色面上只有 2~3:1 —— 低于 WCAG AA 正文要求的 4.5:1。
// 浅色主题因此改用 700~800 号档；深色主题沿用 500 号档（亮色在暗底上对比度足够）。
//
// 具体数值由 `ThemeContrastTest` 定量验证（`* / 卡底`、`* / 次级底` 两组探针）。
//
// `danger` 不在下面三个常量里 —— 它本来就是逐实例指定的。但它踩了同一个坑：
// 7 套浅色主题用的是 `#E57373`（**Material 300**，填充色）当文字，实测 2.96:1；
// 现改为 `#C62828`（red 800）。`PocketSoft` 用的是 Tailwind 系，那套降到 `#B91C1C`
//（red 700）以保留它自己的色系，而不是套一个 Material 红。
//
// 一句话原则：**语义色当"填充/大色块"用可以很亮，当"文字/小图标"用必须够深。**
// 这两件事的取值不应该共用同一个常量 —— 这也是当初三个默认值出问题的根因。

/** 成功 / 通过 / 已完成 —— 浅色底上的文字与图标。 */
private val SuccessOnLight = Color(0xFF2E7D32)

/** 警告 / 即将截止 —— 浅色底上的文字与图标。 */
private val WarningOnLight = Color(0xFFB45309)

/** 提示 / 中性信息 —— 浅色底上的文字与图标。 */
private val InfoOnLight = Color(0xFF1565C0)

/** 成功 —— 深色底（沿用 Material 500）。 */
private val SuccessOnDark = Color(0xFF4CAF50)

/** 警告 —— 深色底（沿用 Material 500）。 */
private val WarningOnDark = Color(0xFFFF9800)

/** 提示 —— 深色底（沿用 Material 500）。 */
private val InfoOnDark = Color(0xFF2196F3)

/**
 * **金属金** —— 总进度条 / 总进度环专用（用户 2026-10 指定的品牌值）。
 *
 * 用户原话：「现在进度条的颜色太土黄了。请帮我改成明亮的金属金色（Metallic Gold）。
 * 背景颜色不要用单一的纯色，请使用从 `#FFD700` 到 `#F9A825` 的线性渐变色，
 * 并且加一点白色的高光边缘或者轻微的 box-shadow 来增加金属质感。去掉任何降低透明度的属性。」
 *
 * 所以这一组值是**钉住的美术要求**，不是我能"顺手改成更易读的深金"的东西：
 * - [MetallicGoldStart] `#FFD700` = 渐变亮端（也是"金"的本色）；
 * - [MetallicGoldEnd] `#F9A825` = 渐变暗端，提供体积感；
 * - [MetallicGoldHighlight] = 顶部那道**实心**高光（用户要求"白色的高光边缘" +
 *   "去掉任何降低透明度的属性" ⇒ 用不透明的近白色，而不是 `white.copy(alpha=…)`）。
 *
 * ⚠️ **代价必须记账**：`#FFD700` 压在白卡上约 **1.6:1**、`#F9A825` 约 **2.0:1**，
 * 都低于 WCAG 对"有意义的图形"要求的 3:1 —— 这是**刻意接受**的（美术要求优先），
 * 三条缓解措施：
 * ① 金属条自带高光 + 阴影，边界不靠亮度差、靠**明暗层次**辨认；
 * ② 条旁边**始终有文字**（`已获 95.0 / 170`、`还差 X 学分`）⇒ 进度这个信息
 *    **不是只靠颜色传达**的（这正是 WCAG 1.4.11 的本意）；
 * ③ `ThemeContrastTest` 里那两条探针**保留**并改成登记基线（棘轮），
 *    写明它是被美术要求钉住的例外 —— 不删除、不静默放行。
 */
val MetallicGoldStart = Color(0xFFFFD700)

/** 金属金渐变暗端（`#F9A825`），见 [MetallicGoldStart] 的说明。 */
val MetallicGoldEnd = Color(0xFFF9A825)

/** 金属条顶部的实心高光（不透明 —— 用户明确要求"去掉任何降低透明度的属性"）。 */
val MetallicGoldHighlight = Color(0xFFFFFDF0)

// ═══ 口袋小鲤（马卡龙极简 · Soft UI）═══
val PocketSoftLight = PocketXiaoliColors(
    surfaceBase = Color(0xFFF7F8FA),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F3F5),
    surfaceHighlight = Color(0xFFEEF2FF),
    textPrimary = Color(0xFF1F2937),
    textSecondary = Color(0xFF4B5563),
    // ⚠️ 2026-10 从 `#9CA3AF` 提到这里：实测 `#9CA3AF` 只有 **2.28–2.54:1**，
    // 连 WCAG 的 **3:1 大字/图标**门槛都不到 —— 而 `textTertiary` 承载的是
    // 真实信息（活动地点、时间戳、提示），且多用在小字号上，必须按正文门槛 4.5:1 要求。
    // `#636B7A` 实测 底 5.05 / 卡 5.36 / 次级底 4.82，三底全达标。
    textTertiary = Color(0xFF636B7A),
    borderDivider = Color(0xFFE5E7EB),
    danger = Color(0xFFB91C1C),     // red-700：与 accentMain(blue-500) 同属 Tailwind 系，但深一档才够当文字
    accentMain = Color(0xFF4D6BFE),
    success = SuccessOnLight,
    warning = WarningOnLight,
    info = InfoOnLight,
)

val PocketSoftDark = PocketXiaoliColors(
    surfaceBase = Color(0xFF0F1115),
    surfaceCard = Color(0xFF171A20),
    surfaceAlt = Color(0xFF24272E),
    surfaceHighlight = Color(0xFF26324F),
    textPrimary = Color(0xFFECEDEF),
    textSecondary = Color(0xFFB4B8C0),
    // 同上：`#7A808A` 在卡/次级底上只有 4.38 / 3.76（未达 4.5）。
    // `#8A9099` 实测 底 5.88 / 卡 5.42 / 次级底 4.65，三底全达标。
    textTertiary = Color(0xFF8A9099),
    borderDivider = Color(0xFF30343B),
    danger = Color(0xFFF87171),
    accentMain = Color(0xFF8AA4FF),
    success = SuccessOnDark,
    warning = WarningOnDark,
    info = InfoOnDark,
)
// ═══ CompositionLocal 环境注入 ═══
// ⚠️ 默认值 = 唯一配色真源「口袋小鲤」的浅色实例。
//
// 它只在「没有 Provider 包裹」的渲染路径上生效 —— 正常 App 与所有截图测试都经
// `PocketXiaoliTheme` 提供，所以这个默认值**不参与出图**（改它不会动任何基线）。
// 但它是「兜底兜到哪一套」的判据：必须与 `AppContainer` 的持久化默认
//（`"pocket_soft"`）。**该键现已不再被读取** —— 见 Theme.kt 顶部的墓碑注释。
val LocalPocketXiaoliColors = staticCompositionLocalOf { PocketSoftLight }

/**
 * **未被皮肤推移过**的配色主题令牌。
 *
 * ## 与 [LocalPocketXiaoliColors] 的关系
 *
 * | CompositionLocal | 内容 | 谁该读 |
 * |---|---|---|
 * | [LocalBaseColors] | `applySurfaces` **之前**的 `finalPocketColors`（已含自定义强调色与纯净模式） | 需要**重新推导**另一套皮肤底色的场景 |
 * | [LocalPocketXiaoliColors] | `applySurfaces` **之后**的最终令牌（当前皮肤已生效） | 绝大多数页面 —— 直接读它 |
 *
 * 两者是**同一条推导链的上下游**，不是两套真源：
 * `LocalBaseColors` → `SkinTokens.applySurfaces(skin, isDark)` → `LocalPocketXiaoliColors`。
 *
 * ## 为什么必须暴露基色
 *
 * 设置页的皮肤预览要现场渲染"**另一套**皮肤"的卡片。而
 * [LocalPocketXiaoliColors] 里存的是**已被当前皮肤混合过**的颜色 ——
 * 无法反推（`lerp` 不可逆）。
 *
 * 不暴露基色，预览就只剩两条路，两条都错：
 * - **复制一份 `applySurfaces` 的推导** → 两处真源，改一处必漏另一处
 * - **直接复用当前色** → 预览的底色与实际切换后的底色**不一致**，
 *   用户看到"预览是这样、切过去变那样"
 *
 * 本项目踩过同类坑：`DesignTokens.kt` 的 `LocalBaseColors` KDoc 记录了旧实现
 * 手写 7 组预览色、导致"预览色和真实主题对不上"的历史。
 *
 * ⚠️ 页面**不要**读它当自己的底色 —— 那是 [LocalPocketXiaoliColors] 的职责。
 * 它只服务"需要重新推导"这一种场景。
 */
val LocalBaseColors = staticCompositionLocalOf { PocketSoftLight }
