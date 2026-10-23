package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 16 个颜色实例（8 套配色 × 明暗）× 皮肤推移后的**文字对比度**测量与防退化闸门。
 *
 * ## 为什么需要它
 *
 * `docs/优化与美化清单-2026-10.md` 的 P3-2 把"深色模式对比度"列为待办，
 * 注明"**16 个实例从未系统过一遍**"；`docs/UI验收手册.md` 的盲区表里也照录了这条。
 * 这是个**从未被验证过的声称** —— 而它直接决定用户读不读得清字。
 *
 * 对比度是纯计算（WCAG 相对亮度），所以**不需要快照、不需要设备** ——
 * 比逐张看图更严格，也更快。
 *
 * ## 两级断言是刻意的（并且**不假装达标**）
 *
 * | 层级 | 对象 | 门限 | 依据 |
 * |---|---|---|---|
 * | 硬断言 | `textPrimary`（页底 / 卡底 / 高亮底） | **WCAG AA 4.5:1** | 实测最低 **12.21:1**，余量充足，没有理由不守 |
 * | **基线棘轮** | `textSecondary` / `textTertiary` / 四个语义色 | **不得低于登记基线** | 见下 |
 *
 * ## ⚠️ 2026-10 大改：**"品牌冲突"那段故事已经不成立了**
 *
 * 本节原文写：「`textSecondary` 达不到 4.5:1，因为它就是品牌色 `#8B95A8`，
 * 要达标等于改品牌色」。**那段话随外观收敛（Phase A）一起作废**：
 * 8 套色板已删、唯一真源是 `PocketSoft*`，而 `PocketSoftLight.textSecondary` 是
 * **`#4B5563`，实测 6.79–7.56:1 —— 早就达标了**；`#8B95A8` 已不在任何使用路径上。
 *
 * 翻案之后，真正的缺陷浮出来：**`textTertiary` 原来只有 2.28–2.54:1**（浅色），
 * 连 WCAG 的 **3:1 大字/图标**门槛都不到 —— 而它承载的是真实信息
 *（活动地点、时间戳、提示），且多用在小字号上。**这是真问题**，按本文件自己的纪律
 *「若令牌本身比基线还差 —— 去改令牌，不要下调基线」处理：
 * `PocketSoftLight.textTertiary` `#9CA3AF` → **`#636B7A`**（4.82–5.36:1）、
 * `PocketSoftDark.textTertiary` `#7A808A` → **`#8A9099`**（4.65–5.88:1），三底全达 AA。
 *
 * 下面的棘轮值**已同步提到新实测值**（棘轮的意义是"锁住已达成的水平不再退化"，
 * 而不是留住历史最差值）。
 *
 * ⚠️ **起约束作用的是「极简」皮肤**：它用 `surfaceShift = 0.85` 推移了底色， * 于是同一个令牌在极简下比在鲜活下的比值更低。上面表里给的是**裸令牌**的比值， * 而棘轮值取的是**经皮肤推移后**的实测最低值 —— 两者不同，别拿裸值去填棘轮。 *（踩过：第一版按裸值填 7.1/5.0/5.3，三条直接红。） * * 当前实测基线（2026-10 收敛后重测；改色后必须同步更新下表并说明理由）：
 *
 * | 探针 | 最小对比度 | 说明 |
 * |---|---|---|
 * | `textSecondary / 页底` | **7.11:1** | `#4B5563`，**已达 AA** |
 * | `textSecondary / 卡底` | **7.56:1** | 同上 |
 * | `textTertiary / 页底` | **5.05:1** | `#636B7A`，**已达 AA**（原 2.39）|
 * | `textTertiary / 卡底` | **5.36:1** | 同上（原 2.54）|
 * | `accentMain / 卡底` | 2.15:1 | 炒饭黄 · 浅 ｜ 极简 |
 * | `danger / 卡底` | 2.96:1 | 马卡龙蓝 · 浅 ｜ 鲜活 |
 * | `success / 卡底` | 2.76:1 | 马卡龙蓝 · 浅 ｜ 鲜活 |
 * | `warning / 卡底` | 2.14:1 | 马卡龙蓝 · 浅 ｜ 鲜活 |
 * | `info / 卡底` | 3.10:1 | 马卡龙蓝 · 浅 ｜ 鲜活 |
 *
 * 📌 **一条与皮肤直接相关的发现**：最糟组合大量落在 **「极简」** 上。
 * 因为 `MinimalSkin.surfaceShift = 0.85` 把页底推向 `#E9E9EE`（比卡色暗一档），
 * 而文字色不变 —— 于是**换到极简会让辅助文字对比度下降**（海盐蓝浅色：2.93 → 2.56）。
 * 这是"灰底白卡"分层方式的固有代价，不是缺陷；但改皮肤底色时要意识到它。
 * 快照测试抓不到这类问题（它只看像素是否与基准一致，不知道"够不够清"），两者互补。
 */
class ThemeContrastTest {

    /**
     * ⚠️ 2026-10：配色收敛为唯一真源 `POCKET_SOFT` 后，这里从 **16 个实例**（8 套 × 明暗）
     * 缩到 **2 个**。这是本测试**守备范围的真实缩小**，不是优化：
     *
     * - 收敛前它守 16 个实例各自的"读不读得清"；
     * - 收敛后只剩一套，"**跨主题不一致**"这一类问题在结构上不可能再出现 ——
     *   而那正是实测最严重的一类（`textSecondary` 在 8 套之间差到 1.19:1 ↔ 4.85:1）。
     *
     * 下面的 [probes] 基线值是从**收敛前 16 实例的最糟值**登记的，留在原处当作
     * "**不得比历史最糟更差**"的棘轮。若某条探针在 `POCKET_SOFT` 下直接低于基线，
     * 说明该令牌本身就比历史最糟还差 —— 那是真问题，**去改令牌，不要下调基线**。
     */
    private val instances: List<Triple<String, PocketXiaoliColors, Boolean>> = listOf(
        Triple("口袋小鲤 · 浅", PocketSoftLight, false),
        Triple("口袋小鲤 · 深", PocketSoftDark, true),
    )

    /** 两套皮肤 —— 会推移表面色，所以同一配色换皮肤后对比度会变（见类 KDoc 末段）。 */
    private val skins: List<Pair<String, SkinTokens>> = listOf(
        "极简" to MinimalSkin,
        "鲜活" to VividSkin,
    )

    /** WCAG 2.1 对比度。用 Compose 的 `luminance()`（它就是 WCAG 相对亮度）。 */
    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /**
     * 一组要检查的 (前台, 取后台, 用途名, 门限)。
     *
     * @param minRatio WCAG AA 硬门限（只给主要文字）；与 [baseline] 二选一。
     * @param baseline 登记基线 —— 只要**不变差**就通过。数值来源见类 KDoc 的表。
     */
    private data class Probe(
        val label: String,
        val foreground: (PocketXiaoliColors) -> Color,
        val background: (PocketXiaoliColors) -> Color,
        val minRatio: Double? = null,
        val baseline: Double? = null,
    ) {
        val floor: Double get() = minRatio ?: baseline ?: 0.0
        val tierName: String get() = if (minRatio != null) "AA 4.5:1" else "基线 $baseline:1"
    }

    private val probes = listOf(
        // ── 主要文字：硬断言 AA ──
        Probe("textPrimary / 页底", { it.textPrimary }, { it.surfaceBase }, minRatio = 4.5),
        Probe("textPrimary / 卡底", { it.textPrimary }, { it.surfaceCard }, minRatio = 4.5),
        Probe("textPrimary / 高亮底", { it.textPrimary }, { it.surfaceHighlight }, minRatio = 4.5),
        // ⚠️ 2026-10 补：代码块正文与"思考链 / 工具脚注"两种药丸都坐在 **surfaceAlt** 上，
        // 而此前 12 个探针只覆盖了 base / card / highlight —— **这个面一个都没测**。
        // 代码正文是可读性要求最高的一类（等宽、常含符号），所以同样硬断言 AA。
        Probe("textPrimary / 次级底", { it.textPrimary }, { it.surfaceAlt }, minRatio = 4.5),
        // ── 其余：锁登记基线防退化（达不到 AA 的原因见类 KDoc）──
        Probe("textSecondary / 页底", { it.textSecondary }, { it.surfaceBase }, baseline = 6.3),
        Probe("textSecondary / 卡底", { it.textSecondary }, { it.surfaceCard }, baseline = 7.5),
        Probe("textTertiary / 页底", { it.textTertiary }, { it.surfaceBase }, baseline = 4.5),
        Probe("textTertiary / 卡底", { it.textTertiary }, { it.surfaceCard }, baseline = 5.2),
        // 药丸上的文字（思考链耗时 / 工具脚注"查了 N 项"）—— 实测最小值
        Probe("textSecondary / 次级底", { it.textSecondary }, { it.surfaceAlt }, baseline = 6.7),
        // 代码块右上角的语言标签。⚠️ 实测 1.97:1 —— 已经改用它的是 textSecondary，
        // 这个探针留着是为了**盯住"不要把 textTertiary 当正文压在 surfaceAlt 上"**。
        Probe("textTertiary / 次级底", { it.textTertiary }, { it.surfaceAlt }, baseline = 4.8),
        // ⚠️ 工具**失败**时脚注曾用 warning 压在 surfaceAlt 上 —— 实测只有 **1.96:1**。
        // 根因不在用法而在令牌：`warning` 是构造函数的默认值(#FF9800)，
        // **8 套配色一套都没覆盖它**。2026-10 已把语义色改成按明暗分档，探针一并升级。
        Probe("warning / 次级底", { it.warning }, { it.surfaceAlt }, minRatio = 4.5),
        Probe("accentMain / 卡底", { it.accentMain }, { it.surfaceCard }, baseline = 2.15),
        // ── 四个语义状态色：2026-10 起**硬断言 AA** ──
        // 它们此前被当成"填充色"来取值（Material 300~500 号档），压在浅色面上只有 2~3:1。
        // 改法与理由见 DesignTokens.kt 的"语义状态色：按明暗分档"一节。
        Probe("danger / 卡底", { it.danger }, { it.surfaceCard }, minRatio = 4.5),
        Probe("success / 卡底", { it.success }, { it.surfaceCard }, minRatio = 4.5),
        Probe("warning / 卡底", { it.warning }, { it.surfaceCard }, minRatio = 4.5),
        // ⚠️ 2026-10 补：`我的`页身份卡的"未绑定"琥珀字坐在 `surfaceCard` 上（即上一行那个硬断言）。
        // 它**一度**打算坐在 **surfaceHighlight** 上 —— 此前 17 个探针里，
        // surfaceHighlight 只测过 textPrimary，"语义色压在它上面"这一格**从没人测**。
        // 补上后当场量出 **4.15:1**（紫藤紫·浅），8 套配色里 5 套低于 AA：
        //
        //     warning / 卡底    4.98:1  ✅      ← 同一个颜色
        //     warning / 高亮底  4.15:1  ❌      ← 只因为底换成了带色调的浅面
        //
        // 于是设计侧改掉了用法（琥珀字只落在卡底上）。本探针**保留为基线而不是删除**：
        // 它记录的是令牌本身的性质
        // （与 `textTertiary / 次级底` 那条同类，用于挡住"把语义色当正文压在带色调浅面上"）。
        Probe("warning / 高亮底", { it.warning }, { it.surfaceHighlight }, baseline = 4.15),
        Probe("info / 卡底", { it.info }, { it.surfaceCard }, minRatio = 4.5),
        // ── 金属金（2026-10，用户钉住的美术要求）──
        // 用户原话：「改成明亮的金属金色（Metallic Gold）……从 `#FFD700` 到 `#F9A825` 的
        // 线性渐变色……加一点白色的高光边缘或者轻微的 box-shadow……去掉任何降低透明度的属性。」
        //
        // ⚠️ 这两条因此从"3:1 硬门限"退成**登记基线**（棘轮）——这是**有意接受的例外**，
        // 不是悄悄放行：#FFD700 压白卡约 1.6:1、#F9A825 约 2.0:1，物理上不可能既"明亮的金属金"
        // 又达到 3:1（我上一版试过深金 `#85620A`，那正是用户说"太土黄"的那一版）。
        // 三条缓解措施（缺一条就该把这两条探针改回硬门限）：
        //   ① 金属条自带**不透明高光 + 阴影**，边界靠明暗层次而不是亮度差；
        //   ② 条旁边**始终有文字**（`已获 95.0 / 170`、`还差 X 学分`）⇒ 进度信息不是只靠颜色传达
        //      （WCAG 1.4.11 的本意）；
        //   ③ 基线是**棘轮**：谁再把这个金改得更淡，测试会红。
        // 1.66 = 实测最糟（浅色 · 极简 的页底）；卡底 1.97、深色档更高。
        Probe("progressMaster / 卡底", { it.progressMaster }, { it.surfaceCard }, baseline = 1.66),
        Probe("progressMaster / 页底", { it.progressMaster }, { it.surfaceBase }, baseline = 1.66),
    )

    @Test
    fun `全部实例的文字对比度不得低于各自门限`() {
        val regressions = mutableListOf<String>()
        val minByLabel = mutableMapOf<String, Pair<Double, String>>()
        var checks = 0

        println("=== 对比度实测（硬门限 AA 4.5:1；其余为登记基线）===")
        for ((themeName, theme, isDark) in instances) {
            for ((skinName, skin) in skins) {
                val applied = skin.applySurfaces(theme, isDark)
                val where = "$themeName ｜ $skinName"
                val parts = mutableListOf<String>()
                for (probe in probes) {
                    val ratio = contrast(probe.foreground(applied), probe.background(applied))
                    checks++
                    // 0.01 容差：对比度是确定性计算，容差只为浮点比较安全
                    if (ratio < probe.floor - 0.01) {
                        regressions += "%-22s %-22s %.2f:1（门限 %s）"
                            .format(where, probe.label, ratio, probe.tierName)
                    }
                    val prev = minByLabel[probe.label]
                    if (prev == null || ratio < prev.first) minByLabel[probe.label] = ratio to where
                    parts += "%s=%.2f".format(probe.label.substringBefore(" / "), ratio)
                }
                println("  %-22s %s".format(where, parts.joinToString("  ")))
            }
        }

        println()
        println("=== 每个探针的最小值（找出最糟的组合）===")
        minByLabel.entries.sortedBy { it.value.first }.forEach { (label, v) ->
            val tier = probes.first { it.label == label }.tierName
            println("  %-24s 最小 %.2f:1  @ %-22s [%s]".format(label, v.first, v.second, tier))
        }

        println()
        println("=== 共 $checks 项检查，${regressions.size} 项低于门限 ===")
        regressions.forEach { println("  ✗ $it") }

        assertTrue(
            "有 ${regressions.size} 项对比度低于各自门限。\n" +
                "若这是**有意**改色：请同步更新 ThemeContrastTest 的基线表与类 KDoc，并说明理由。\n" +
                "若这是**无意**退化：说明皮肤底色或文字色的改动压低了可读性。\n" +
                regressions.joinToString("\n"),
            regressions.isEmpty(),
        )
    }
}
