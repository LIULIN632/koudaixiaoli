package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * 把 [PocketTypography] 的 7 档映射成 MIUIX 的 **14 个文字槽**。
 *
 * ## ⚠️ 先说清楚：这条通道**当前没有任何消费者**
 *
 * 这不是猜测，是**实测**的：把 `MiuixTheme(textStyles = …)` 塞进一个荒谬的
 * **40sp** 值，`.\gradlew :app:testDebugUnitTest` 的 **70 张基线一张都没变**。
 * 原因是本 App 至今只用 MIUIX 的**主题包装**（`Colors` / `MiuixTheme`）与
 * **交互反馈**（`PressFeedbackType` / `SinkFeedback` / `TiltFeedback`），
 * **一个 MIUIX UI 组件都没有** —— 而 `LocalTextStyles` 只有 MIUIX 的文字组件才会读。
 *
 * 所以本文件与 [toMiuixColors] 是同一类东西：**为将来接入 MIUIX 组件提前接线**。
 * 那个先例的 KDoc 写得很清楚（"这样 MIUIX 组件一放进来就能读到正确配色，
 * 不必先做整体改造"）—— 文字这条走同一条路。
 *
 * **因此：本文件改动不会让任何基线变化，也永远不会被截图测试发现。**
 * 它的正确性靠同目录的 `MiuixTypographyMappingTest`（源码扫描）守着 —— 见那个文件的说明。
 *
 * ## 为什么是"映射"而不是"采用 MIUIX 默认字阶"
 *
 * MIUIX 的字阶是 **MIUI 尺寸**：以 17sp 为正文基准（`main` / `paragraph` / `button` 都是 17）。
 * 而本项目是 **12sp 基准的马卡龙极简**（`PocketTypography` 7 档：24/20/16/14/12/10 + mono13/numeric28）。
 * 直接采用 MIUI 字阶会让所有 MIUIX 组件比周围文字**大一圈**。
 *
 * **映射，不采用** —— 与配色那条完全同一个判断。
 *
 * ## 分流依据（语义对应，不是字号相等）
 *
 * | MIUIX 槽 | 本 App 档位 | 理由 |
 * |---|---|---|
 * | `title1` / `title2` | `screenTitle` | MIUIX 最大的两级标题 = 本 App 的页面大标题 |
 * | `title3` / `title4` | `sectionTitle` | 区块标题 |
 * | `headline1` / `headline2` | `cardTitle` | 卡片标题（本 App 的 16sp Bold）|
 * | `subtitle` | `cardTitle` | MIUI 的 subtitle 是 14 Bold，本 App 最接近的强调档就是它 |
 * | `button` | `cardTitle` | 按钮文字需要强调；本 App 没有单独的按钮档 |
 * | `main` / `body1` / `body2` / `paragraph` | `body` | MIUIX 的正文四槽在本 App 里对应同一档（14sp）|
 * | `footnote1` | `caption` | 注脚（本 App 12sp）|
 * | `footnote2` | `label` | 最小注脚（本 App 10sp）|
 *
 * ⚠️ `mono`（13sp 等宽）与 `numeric`（28sp 数字）**没有对应槽** ——
 * MIUIX 的 14 槽里没有等宽/数字专用槽。它们是本 App 的特种档，**刻意不硬塞**：
 * 塞进 `body2` 会让 MIUIX 组件的正文变成等宽字。
 *
 * ## ⚠️ 纪律：本文件**只搬运 `PocketTypography` 的值，不许新造字号**
 *
 * 新造一个"看着更合适"的字号，就会造出第二个字阶真源 ——
 * 与 `PocketTypography` 的 KDoc 开头点名要消灭的"18 处散落字号"是同一个病。
 * `MiuixTypographyMappingTest` 会扫描本文件、拦下任何 `fontSize =` 字面量。
 */
@Composable
fun miuixTextStyles(): TextStyles = defaultTextStyles(
    // ── 标题族 ──
    title1 = PocketTypography.screenTitle,
    title2 = PocketTypography.screenTitle,
    title3 = PocketTypography.sectionTitle,
    title4 = PocketTypography.sectionTitle,
    // ── 小标题 / 强调族 ──
    headline1 = PocketTypography.cardTitle,
    headline2 = PocketTypography.cardTitle,
    subtitle = PocketTypography.cardTitle,
    button = PocketTypography.cardTitle,
    // ── 正文族（MIUIX 四个正文槽在本 App 里是同一档）──
    main = PocketTypography.body,
    body1 = PocketTypography.body,
    body2 = PocketTypography.body,
    paragraph = PocketTypography.body,
    // ── 注脚族 ──
    footnote1 = PocketTypography.caption,
    footnote2 = PocketTypography.label,
)
